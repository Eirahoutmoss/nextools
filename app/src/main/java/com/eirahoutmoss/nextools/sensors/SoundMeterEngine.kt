package com.eirahoutmoss.nextools.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.eirahoutmoss.nextools.core.measure.SoundLevelMeter
import com.eirahoutmoss.nextools.core.measure.TimeWeighting
import com.eirahoutmoss.nextools.core.measure.Weighting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** Ekrana giden bir ölçüm anı (her ~100 ms). */
data class SoundReading(
    val level: Double,
    val leq: Double,
    val lmax: Double,
    val lmin: Double,
    val clipping: Boolean,
    val elapsedS: Double,
    /** Son blok için 20 tepe genliği (0..1): ekrandaki dalga şeridi. */
    val wave: FloatArray,
    val elapsedRealtimeMs: Long,
)

sealed interface SoundState {
    data object Idle : SoundState
    data class Running(val reading: SoundReading?, val sampleRate: Int, val source: String) : SoundState
    /** Mikrofon açılamadı (başka uygulama kullanıyor vb.). */
    data class Failed(val code: Int) : SoundState
}

/**
 * Mikrofonu okur ve [SoundLevelMeter] ile seviyeleri hesaplar. Ses verisi bellekte işlenip atılır;
 * hiçbir yere yazılmaz.
 *
 * Mümkünse "işlenmemiş" (UNPROCESSED) kaynak kullanılır; desteklenmiyorsa otomatik kazancı
 * genelde kapalı olan ses tanıma (VOICE_RECOGNITION) kaynağı.
 */
class SoundMeterEngine(context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow<SoundState>(SoundState.Idle)
    val state: StateFlow<SoundState> = _state.asStateFlow()

    @Volatile var offsetDb: Double = SoundLevelMeter.DEFAULT_OFFSET_DB
    @Volatile private var resetRequested = false
    private var job: Job? = null

    fun resetStats() { resetRequested = true }

    private fun source(): Pair<Int, String> {
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED"
        else MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION"
    }

    /** Mikrofon izni çağıran tarafından alınmış olmalıdır. */
    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, weighting: Weighting, timing: TimeWeighting) {
        // Önceki oturumun mikrofonu tamamen bırakması beklenir; yoksa yeni kayıt açılamayabilir.
        val previous = job
        job = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            val (src, srcName) = source()
            val fs = listOf(48_000, 44_100).firstOrNull {
                AudioRecord.getMinBufferSize(it, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
            }
            if (fs == null) { _state.value = SoundState.Failed(1); return@launch }
            val minBuf = AudioRecord.getMinBufferSize(fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(src, fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf * 4, fs / 2))
            } catch (e: Exception) {
                _state.value = SoundState.Failed(2); return@launch
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release(); _state.value = SoundState.Failed(2); return@launch
            }
            val meter = SoundLevelMeter(fs.toDouble(), weighting, timing, offsetDb)
            val block = fs / 10
            val pcm = ShortArray(block)
            val f = FloatArray(block)
            _state.value = SoundState.Running(null, fs, srcName)
            try {
                rec.startRecording()
                if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    _state.value = SoundState.Failed(3); return@launch
                }
                while (isActive) {
                    var n = 0
                    while (n < block && isActive) {
                        val got = rec.read(pcm, n, block - n)
                        if (got < 0) { _state.value = SoundState.Failed(4); return@launch }
                        n += got
                    }
                    for (i in 0 until n) f[i] = pcm[i] / 32768f
                    if (resetRequested) { meter.resetStats(); resetRequested = false }
                    meter.offsetDb = offsetDb
                    meter.process(f, n)
                    val wave = FloatArray(20)
                    val per = max(1, n / 20)
                    for (k in 0 until 20) {
                        var p = 0f
                        val from = k * per
                        val to = minOf(n, from + per)
                        for (i in from until to) p = max(p, abs(f[i]))
                        wave[k] = p
                    }
                    _state.value = SoundState.Running(
                        SoundReading(meter.level, meter.leq, meter.lmax, meter.lmin, meter.clipping, meter.elapsedS, wave, SystemClock.elapsedRealtime()),
                        fs, srcName,
                    )
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }
    }

    /** Durdurur. İş nesnesi tutulur: sonraki [start] mikrofonun bırakılmasını bekleyebilsin. */
    fun stop() {
        job?.cancel()
        _state.value = SoundState.Idle
    }
}
