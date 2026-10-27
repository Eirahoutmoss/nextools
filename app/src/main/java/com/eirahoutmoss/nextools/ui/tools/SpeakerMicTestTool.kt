package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private const val TEST_FS = 48_000
private const val REC_SECONDS = 5

private enum class Channel { LEFT, RIGHT, BOTH, SWEEP }

private data class MicState(
    val recording: Boolean = false,
    val playing: Boolean = false,
    val progress: Float = 0f,
    val levelDbfs: Double = -120.0,
    val peakDbfs: Double = -120.0,
    val rmsDbfs: Double = -120.0,
    val hasClip: Boolean = false,
)

/** Kısa test sesi: sol / sağ / iki kanal 1 kHz ya da 100 Hz–10 kHz tarama (stereo, PCM float). */
private fun playTest(ch: Channel, done: () -> Unit) = thread(name = "nextools-spk") {
    val seconds = if (ch == Channel.SWEEP) 4.0 else 1.5
    val n = (TEST_FS * seconds).toInt()
    val data = FloatArray(n * 2)
    var phase = 0.0
    for (i in 0 until n) {
        val t = i.toDouble() / TEST_FS
        val f = if (ch == Channel.SWEEP) 100.0 * exp(t / seconds * ln(10_000.0 / 100.0)) else 1000.0
        phase += 2 * PI * f / TEST_FS
        val env = minOf(1.0, i / 2400.0, (n - i) / 2400.0)
        val v = (0.5 * env * sin(phase)).toFloat()
        data[2 * i] = if (ch == Channel.RIGHT) 0f else v
        data[2 * i + 1] = if (ch == Channel.LEFT) 0f else v
    }
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(TEST_FS).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(data.size * 4)
        .build()
    try {
        track.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        Thread.sleep((seconds * 1000).toLong() + 150)
    } catch (_: Exception) {
    } finally { runCatching { track.stop() }; track.release(); done() }
}

private fun dbfs(v: Double) = if (v <= 1e-9) -120.0 else 20 * log10(v)

/** Mikrofondan [REC_SECONDS] saniye kayıt (bellekte), ardından hoparlörden dinletme. */
@SuppressLint("MissingPermission")
private fun recordMic(source: Int, state: MutableStateFlow<MicState>, sink: (ShortArray) -> Unit) = thread(name = "nextools-mic") {
    val total = TEST_FS * REC_SECONDS
    val buf = ShortArray(total)
    val minBuf = AudioRecord.getMinBufferSize(TEST_FS, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val rec = try { AudioRecord(source, TEST_FS, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 9600)) } catch (_: Exception) { null }
    if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { rec?.release(); state.value = MicState(); return@thread }
    state.value = MicState(recording = true)
    var pos = 0; var peak = 0; var sumSq = 0.0; var clip = false
    try {
        rec.startRecording()
        while (pos < total) {
            val r = rec.read(buf, pos, minOf(2400, total - pos))
            if (r <= 0) break
            var blockPeak = 0
            for (i in pos until pos + r) { val a = abs(buf[i].toInt()); if (a > blockPeak) blockPeak = a; sumSq += buf[i].toDouble() * buf[i]; if (a >= 32700) clip = true }
            peak = max(peak, blockPeak)
            pos += r
            state.value = state.value.copy(progress = pos / total.toFloat(), levelDbfs = dbfs(blockPeak / 32768.0), peakDbfs = dbfs(peak / 32768.0), hasClip = clip)
        }
    } catch (_: Exception) {
    } finally { runCatching { rec.stop() }; rec.release() }
    state.value = state.value.copy(recording = false, progress = 1f, rmsDbfs = dbfs(sqrt(sumSq / max(1, pos)) / 32768.0))
    sink(buf.copyOf(pos))
}

private fun playBack(pcm: ShortArray, state: MutableStateFlow<MicState>) = thread(name = "nextools-play") {
    if (pcm.isEmpty()) return@thread
    state.value = state.value.copy(playing = true)
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(TEST_FS).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(pcm.size * 2)
        .build()
    try {
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep(pcm.size * 1000L / TEST_FS + 150)
    } catch (_: Exception) {
    } finally { runCatching { track.stop() }; track.release(); state.value = state.value.copy(playing = false) }
}

private val SOURCES = listOf(
    MediaRecorder.AudioSource.MIC to R.string.smt_src_mic,
    MediaRecorder.AudioSource.CAMCORDER to R.string.smt_src_camcorder,
    MediaRecorder.AudioSource.VOICE_COMMUNICATION to R.string.smt_src_voice,
    MediaRecorder.AudioSource.UNPROCESSED to R.string.smt_src_raw,
)

/** Hoparlör / Mikrofon Testi: kanal testi, frekans taraması, kayıt ve dinleme, mikrofon listesi. Kayıt yalnızca bellekte tutulur. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakerMicTestTool(onBack: () -> Unit) {
    val context = LocalContext.current
    var playingCh by remember { mutableStateOf<Channel?>(null) }
    val mic = remember { MutableStateFlow(MicState()) }
    val ms by mic.collectAsStateWithLifecycle()
    val pcm = remember { arrayOfNulls<ShortArray>(1) }
    var hasRec by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(MediaRecorder.AudioSource.MIC) }
    DisposableEffect(Unit) { onDispose { pcm[0] = null } }

    ToolScaffold(ToolId.SPEAKER_MIC_TEST, onBack, {}, null, null, false, null, null) {
        SectionCard(stringResource(R.string.smt_speaker)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Channel.LEFT to R.string.smt_left, Channel.RIGHT to R.string.smt_right, Channel.BOTH to R.string.smt_both, Channel.SWEEP to R.string.smt_sweep).forEach { (ch, label) ->
                    OutlinedButton(onClick = { playingCh = ch; playTest(ch) { playingCh = null } }, enabled = playingCh == null && !ms.recording && !ms.playing) {
                        Text(stringResource(label))
                    }
                }
            }
            Text(stringResource(R.string.smt_speaker_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PermissionGate(Manifest.permission.RECORD_AUDIO, stringResource(R.string.mic_rationale_generic)) {
            SectionCard(stringResource(R.string.smt_mic)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SOURCES.forEach { (src, label) -> FilterChip(source == src, { if (!ms.recording) source = src }, label = { Text(stringResource(label)) }) }
                }
                Button(
                    onClick = { hasRec = false; recordMic(source, mic) { pcm[0] = it; hasRec = it.isNotEmpty() } },
                    enabled = !ms.recording && !ms.playing && playingCh == null, modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.smt_record, REC_SECONDS)) }
                if (ms.recording) LinearProgressIndicator(progress = { ms.progress }, modifier = Modifier.fillMaxWidth())
                if (ms.recording || hasRec) {
                    LinearProgressIndicator(progress = { ((ms.levelDbfs + 60) / 60).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    KeyValue(stringResource(R.string.smt_peak), Fmt.num(ms.peakDbfs, 1) + " dBFS")
                    if (!ms.recording) KeyValue(stringResource(R.string.smt_rms), Fmt.num(ms.rmsDbfs, 1) + " dBFS")
                }
                if (ms.hasClip) NoticeBanner(stringResource(R.string.smt_clip))
                if (hasRec && !ms.recording && ms.peakDbfs < -60) NoticeBanner(stringResource(R.string.smt_silent), BadgeLevel.ALARM)
                OutlinedButton(onClick = { pcm[0]?.let { playBack(it, mic) } }, enabled = hasRec && !ms.recording && !ms.playing, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (ms.playing) R.string.smt_playing else R.string.smt_play))
                }
                Text(stringResource(R.string.smt_mic_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MicList(context)
        }
    }
}

@Composable
private fun MicList(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val mics = remember { runCatching { am.microphones }.getOrDefault(emptyList()) }
    if (mics.isEmpty()) return
    SectionCard(stringResource(R.string.smt_mic_list, mics.size)) {
        mics.forEach { m ->
            val loc = when (m.location) {
                android.media.MicrophoneInfo.LOCATION_MAINBODY -> stringResource(R.string.smt_loc_body)
                android.media.MicrophoneInfo.LOCATION_MAINBODY_MOVABLE -> stringResource(R.string.smt_loc_movable)
                android.media.MicrophoneInfo.LOCATION_PERIPHERAL -> stringResource(R.string.smt_loc_peripheral)
                else -> "—"
            }
            val pos = m.position.takeIf { it != android.media.MicrophoneInfo.POSITION_UNKNOWN }?.let { "x ${Fmt.num(it.x.toDouble(), 3)}, y ${Fmt.num(it.y.toDouble(), 3)} m" }
            KeyValue(m.description.ifBlank { "#${m.id}" }, loc + (pos?.let { " · $it" } ?: ""))
        }
    }
}
