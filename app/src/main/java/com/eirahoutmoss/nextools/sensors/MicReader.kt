package com.eirahoutmoss.nextools.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Mikrofondan sabit uzunlukta, [hop] kadar kayan pencereler üretir (Frekans Analizörü, Akort).
 * Ses bellekte işlenip atılır; hiçbir yere yazılmaz.
 */
class MicReader(context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var job: Job? = null

    /** 0 = çalışıyor ya da boşta; >0 hata kodu. */
    private val _error = MutableStateFlow(0)
    val error: StateFlow<Int> = _error.asStateFlow()
    @Volatile var sampleRate: Int = 0
        private set
    @Volatile var sourceName: String = ""
        private set

    /** [onWindow] arka plan iş parçacığında çağrılır; dizi yeniden kullanılır, kopyalanmalıdır. */
    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, window: Int, hop: Int, onWindow: (FloatArray, Int) -> Unit) {
        val previous = job
        job = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            _error.value = 0
            val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val src = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
            sourceName = if (unprocessed) "UNPROCESSED" else "VOICE_RECOGNITION"
            val fs = listOf(48_000, 44_100).firstOrNull {
                AudioRecord.getMinBufferSize(it, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
            } ?: run { _error.value = 1; return@launch }
            sampleRate = fs
            val minBuf = AudioRecord.getMinBufferSize(fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(src, fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf * 4, window * 4))
            } catch (e: Exception) { _error.value = 2; return@launch }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); _error.value = 2; return@launch }
            val win = FloatArray(window)
            val pcm = ShortArray(hop)
            var filled = 0
            try {
                rec.startRecording()
                while (isActive) {
                    var n = 0
                    while (n < hop && isActive) {
                        val got = rec.read(pcm, n, hop - n)
                        if (got < 0) { _error.value = 4; return@launch }
                        n += got
                    }
                    // pencereyi kaydır, yeni örnekleri sona ekle
                    System.arraycopy(win, hop, win, 0, window - hop)
                    for (i in 0 until hop) win[window - hop + i] = pcm[i] / 32768f
                    filled = minOf(window, filled + hop)
                    if (filled >= window && isActive) onWindow(win, fs)
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }
    }

    fun stop() { job?.cancel() }
}
