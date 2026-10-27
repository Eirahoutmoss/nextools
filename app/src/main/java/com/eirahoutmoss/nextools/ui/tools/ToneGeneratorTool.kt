package com.eirahoutmoss.nextools.ui.tools

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.ToneSynth
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import kotlinx.coroutines.delay
import kotlin.concurrent.thread
import kotlin.math.ln
import kotlin.math.exp

private const val FS = 48_000

/** Hoparlörden ses akışı: ayrı iş parçacığı, AudioTrack (float). */
private class TonePlayer(val synth: ToneSynth) {
    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "nextools-tone", priority = Thread.MAX_PRIORITY) {
            val minBuf = AudioTrack.getMinBufferSize(FS, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(FS).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBuf, 4096 * 4))
                .build()
            val block = FloatArray(1024)
            try {
                track.play()
                while (running) {
                    synth.render(block)
                    track.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING)
                }
            } catch (_: Exception) {
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
    }
}

private fun sliderToHz(v: Float): Double = 20.0 * exp(v * ln(20_000.0 / 20.0))
private fun hzToSlider(hz: Double): Float = (ln(hz / 20.0) / ln(20_000.0 / 20.0)).toFloat().coerceIn(0f, 1f)

/** Ton Üreteci: sinüs, kare, üçgen, beyaz/pembe gürültü, logaritmik tarama. */
@Composable
fun ToneGeneratorTool(onBack: () -> Unit) {
    val synth = remember { ToneSynth(FS) }
    val player = remember { TonePlayer(synth) }
    var playing by remember { mutableStateOf(false) }
    var wave by rememberSaveable { mutableStateOf(ToneSynth.Wave.SINE) }
    var freq by rememberSaveable { mutableDoubleStateOf(440.0) }
    var volume by rememberSaveable { mutableStateOf(0.3f) }
    var sweepSec by rememberSaveable { mutableStateOf(10f) }
    var current by remember { mutableDoubleStateOf(440.0) }
    val started = rememberIsStarted()

    synth.wave = wave; synth.frequency = freq; synth.volume = volume.toDouble()
    synth.sweepFrom = 20.0; synth.sweepTo = 20_000.0; synth.sweepSeconds = sweepSec.toDouble()
    LaunchedEffect(playing, started) { if (playing && started) player.start() else player.stop() }
    LaunchedEffect(playing, wave) { while (playing) { current = synth.currentHz; delay(100) } }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    ToolScaffold(ToolId.TONE_GENERATOR, onBack, {}, null, null, false, null, null) {
        val noise = wave == ToneSynth.Wave.WHITE || wave == ToneSynth.Wave.PINK
        BigReadout(
            value = if (noise) "—" else Fmt.num(if (wave == ToneSynth.Wave.SWEEP) current else freq, if (freq < 100) 1 else 0), unit = if (noise) "" else "Hz",
            caption = stringResource(waveName(wave)), badge = stringResource(R.string.tone_badge), badgeLevel = BadgeLevel.NEUTRAL,
        )
        Button(onClick = { playing = !playing; if (playing) synth.restartSweep() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (playing) R.string.action_stop else R.string.tone_play))
        }
        SectionCard(stringResource(R.string.tone_wave)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ToneSynth.Wave.entries.toList()) { w ->
                    FilterChip(wave == w, { wave = w; if (w == ToneSynth.Wave.SWEEP) synth.restartSweep() }, label = { Text(stringResource(waveName(w))) })
                }
            }
        }
        if (!noise && wave != ToneSynth.Wave.SWEEP) {
            SectionCard(stringResource(R.string.tone_frequency)) {
                Slider(value = hzToSlider(freq), onValueChange = { freq = Math.round(sliderToHz(it) * 10) / 10.0 })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50.0, 100.0, 440.0, 1000.0, 10_000.0).forEach { f ->
                        OutlinedButton(onClick = { freq = f }) { Text(if (f >= 1000) Fmt.num(f / 1000, 0) + "k" else Fmt.num(f, 0)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { freq = (freq / 2.0).coerceAtLeast(20.0) }) { Text(stringResource(R.string.tone_octave_down)) }
                    OutlinedButton(onClick = { freq = (freq * 2.0).coerceAtMost(20_000.0) }) { Text(stringResource(R.string.tone_octave_up)) }
                    OutlinedButton(onClick = { freq = (freq - 1).coerceAtLeast(20.0) }) { Text("−1") }
                    OutlinedButton(onClick = { freq = (freq + 1).coerceAtMost(20_000.0) }) { Text("+1") }
                }
            }
        }
        if (wave == ToneSynth.Wave.SWEEP) {
            SectionCard(stringResource(R.string.tone_sweep_time, Fmt.num(sweepSec.toDouble(), 0))) {
                Slider(value = sweepSec, onValueChange = { sweepSec = it }, valueRange = 2f..60f)
            }
        }
        SectionCard(stringResource(R.string.tone_volume, (volume * 100).toInt())) {
            Slider(value = volume, onValueChange = { volume = it })
        }
        NoticeBanner(stringResource(R.string.tone_hearing))
        Text(stringResource(R.string.tool_tone_generator_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun waveName(w: ToneSynth.Wave): Int = when (w) {
    ToneSynth.Wave.SINE -> R.string.tone_sine
    ToneSynth.Wave.SQUARE -> R.string.tone_square
    ToneSynth.Wave.TRIANGLE -> R.string.tone_triangle
    ToneSynth.Wave.WHITE -> R.string.tone_white
    ToneSynth.Wave.PINK -> R.string.tone_pink
    ToneSynth.Wave.SWEEP -> R.string.tone_sweep
}
