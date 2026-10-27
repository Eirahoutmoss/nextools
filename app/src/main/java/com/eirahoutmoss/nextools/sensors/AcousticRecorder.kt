package com.eirahoutmoss.nextools.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.eirahoutmoss.nextools.core.measure.Sweep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Ses donanımı hatası; [code] uygulamada metne çevrilir. */
class AcousticException(val code: Int) : IOException("acoustic error $code")

/** Bir ölçüm turunun ham sonucu. */
class AcousticCapture(
    /** Her tarama için ayrı kayıt (tarama + sönüm süresi). */
    val sweeps: List<FloatArray>,
    /** Ölçümden önce kaydedilen 1 sn'lik ortam gürültüsü (dBFS, RMS). */
    val backgroundDbfs: Double,
    /** Taramalar sırasındaki en yüksek seviye (0..1). */
    val peakLevel: Double,
    val audioSource: String,
    val mediaVolumeRatio: Double,
)

/**
 * Tarama sesini çalar ve aynı anda mikrofondan kaydeder.
 * Mümkünse "işlenmemiş" (UNPROCESSED) ses kaynağı kullanılır: otomatik kazanç ve gürültü bastırma
 * yankıyı bozar. Desteklenmiyorsa ses tanıma kaynağı (en az işlenen standart kaynak) kullanılır.
 */
class AcousticRecorder(private val context: Context) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun mediaVolumeRatio(): Double {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toDouble() / max else 1.0
    }

    private fun source(): Pair<Int, String> {
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED"
        else MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION"
    }

    /** Mikrofon izni çağıran tarafından alınmış olmalıdır. */
    @SuppressLint("MissingPermission")
    suspend fun capture(
        sweep: Sweep.Signal,
        repeats: Int,
        tailS: Double,
        /** step 0 = ortam gürültüsü, 1.. = tarama numarası */
        onProgress: (step: Int, fraction: Float) -> Unit,
    ): AcousticCapture = withContext(Dispatchers.IO) {
        val fs = sweep.fs
        val (src, srcName) = source()
        val minBuf = AudioRecord.getMinBufferSize(fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) throw AcousticException(1)
        val rec = AudioRecord(src, fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf * 4, fs))
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw AcousticException(2)
        }
        val pcm = ShortArray(sweep.samples.size) { (sweep.samples[it] * Short.MAX_VALUE).toInt().toShort() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(fs).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            if (track.write(pcm, 0, pcm.size) != pcm.size) throw AcousticException(3)

            onProgress(0, 0f)
            rec.startRecording()
            val bg = read(rec, fs)
            val bgRms = sqrt(bg.fold(0.0) { a, v -> a + v * v } / bg.size)

            val takes = ArrayList<FloatArray>()
            var peak = 0.0
            val perTake = (fs * (sweep.durationS + tailS)).toInt()
            for (r in 0 until repeats) {
                coroutineContext.ensureActive()
                onProgress(r + 1, (r + 0.1f) / repeats)
                track.stop()
                track.reloadStaticData()
                track.play()
                val y = read(rec, perTake) { done -> onProgress(r + 1, (r + done) / repeats) }
                takes += y
                peak = max(peak, y.maxOf { abs(it).toDouble() })
            }
            rec.stop()
            AcousticCapture(
                sweeps = takes,
                backgroundDbfs = 20 * log10(max(bgRms, 1e-9)),
                peakLevel = peak,
                audioSource = srcName,
                mediaVolumeRatio = mediaVolumeRatio(),
            )
        } finally {
            runCatching { track.stop() }
            track.release()
            runCatching { rec.stop() }
            rec.release()
        }
    }

    private suspend fun read(rec: AudioRecord, count: Int, progress: ((Float) -> Unit)? = null): FloatArray {
        val out = FloatArray(count)
        val buf = ShortArray(4096)
        var n = 0
        while (n < count) {
            coroutineContext.ensureActive()
            val got = rec.read(buf, 0, minOf(buf.size, count - n))
            if (got < 0) throw AcousticException(4)
            for (i in 0 until got) out[n + i] = buf[i] / 32768f
            n += got
            progress?.invoke(n.toFloat() / count)
        }
        return out
    }
}
