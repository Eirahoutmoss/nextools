package com.eirahoutmoss.nextools.ui.tools

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Meter
import com.eirahoutmoss.nextools.core.measure.MetronomeSynth
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeepScreenOn
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.thread

private const val METRO_FS = 48_000

private fun meterName(key: String) = when (key) {
    "m2_4" -> R.string.metro_m2_4
    "m3_4" -> R.string.metro_m3_4
    "m4_4" -> R.string.metro_m4_4
    "m6_8" -> R.string.metro_m6_8
    "turk_aksagi" -> R.string.metro_turk_aksagi
    "devri_hindi" -> R.string.metro_devri_hindi
    "devri_turan" -> R.string.metro_devri_turan
    "aksak" -> R.string.metro_aksak
    "aksak_semai" -> R.string.metro_aksak_semai
    else -> R.string.metro_custom
}

private class MetronomePlayer(val synth: MetronomeSynth) {
    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        synth.reset()
        worker = thread(name = "nextools-metronome", priority = Thread.MAX_PRIORITY) {
            val minBuf = AudioTrack.getMinBufferSize(METRO_FS, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(METRO_FS).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBuf, 1024 * 4))
                .build()
            // Küçük blok: darbe bildirimi ile duyulan ses arasındaki gecikme az olsun
            val block = FloatArray(256)
            try {
                track.play()
                while (running) { synth.render(block); track.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING) }
            } catch (_: Exception) {
            } finally { runCatching { track.stop() }; track.release() }
        }
    }

    fun stop() { running = false; worker?.join(500); worker = null }
}

private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

/** Metronom: düz ölçüler ve aksak usuller (5/8, 7/8, 9/8, 10/8…), vurgulu darbe, titreşim. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetronomeTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val synth = remember { MetronomeSynth(METRO_FS) }
    val player = remember { MetronomePlayer(synth) }
    val vib = remember { vibrator(context) }
    val pulseFlow = remember { MutableStateFlow(-1) }
    val pulse by pulseFlow.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf(false) }
    var bpm by rememberSaveable { mutableDoubleStateOf(100.0) }
    var meterKey by rememberSaveable { mutableStateOf("m4_4") }
    var custom by rememberSaveable { mutableStateOf("2+2+3") }
    var accent by rememberSaveable { mutableStateOf(true) }
    var vibrate by rememberSaveable { mutableStateOf(false) }
    val taps = remember { ArrayDeque<Long>() }
    val vibrateRef = remember { booleanArrayOf(false) }
    vibrateRef[0] = vibrate

    val meter = if (meterKey == "custom") Meter.parse(custom) else Meter.PRESETS.firstOrNull { it.key == meterKey }
    synth.pulsesPerMinute = bpm
    synth.accentOn = accent
    meter?.let { synth.meter = it }
    synth.onPulse = { p ->
        pulseFlow.value = p
        if (vibrateRef[0] && synth.meter.accent(p) == 2) runCatching { vib?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    KeepScreenOn(playing)

    ToolScaffold(ToolId.METRONOME, onBack, {}, null, null, false, null, null) {
        val m = synth.meter
        BigReadout(
            value = Fmt.num(bpm, 0), unit = stringResource(R.string.metro_unit),
            caption = stringResource(meterName(m.key)) + " · " + m.signature + (if (m.groups.any { it > 1 }) " (" + m.grouping + ")" else ""),
            badge = null, badgeLevel = BadgeLevel.NEUTRAL,
        )
        // Darbe göstergesi: gruplar arasında boşluk
        val inst = LocalInstrument.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            var idx = 0
            m.groups.forEachIndexed { gi, g ->
                if (gi > 0) Spacer(Modifier.width(10.dp))
                repeat(g) {
                    val i = idx++
                    val a = m.accent(i)
                    val on = playing && pulse == i
                    Box(Modifier.size(if (a == 2) 22.dp else if (a == 1) 18.dp else 14.dp).background(if (on) inst.accent else inst.grid, CircleShape))
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
        Slider(bpm.toFloat(), { bpm = it.toDouble().coerceIn(30.0, 300.0) }, valueRange = 30f..300f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { bpm = (bpm - 1).coerceAtLeast(30.0) }) { Text("−1") }
            OutlinedButton(onClick = { bpm = (bpm + 1).coerceAtMost(300.0) }) { Text("+1") }
            OutlinedButton(onClick = {
                val now = System.currentTimeMillis()
                if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
                taps.addLast(now); while (taps.size > 6) taps.removeFirst()
                if (taps.size >= 2) bpm = (60_000.0 * (taps.size - 1) / (taps.last() - taps.first())).coerceIn(30.0, 300.0)
            }) { Text(stringResource(R.string.metro_tap)) }
        }
        Button(
            onClick = { if (playing) { player.stop(); playing = false } else { player.start(); playing = true } },
            enabled = meter != null, modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (playing) R.string.metro_stop else R.string.metro_start)) }
        SectionCard(stringResource(R.string.metro_meter)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Meter.PRESETS.forEach { p ->
                    FilterChip(meterKey == p.key, { meterKey = p.key }, label = { Text(stringResource(meterName(p.key)) + " " + p.signature) })
                }
                FilterChip(meterKey == "custom", { meterKey = "custom" }, label = { Text(stringResource(R.string.metro_custom)) })
            }
            if (meterKey == "custom") {
                OutlinedTextField(custom, { custom = it.take(40) }, label = { Text(stringResource(R.string.metro_custom_label)) },
                    singleLine = true, isError = Meter.parse(custom) == null, modifier = Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.metro_pulse_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.metro_accent), modifier = Modifier.weight(1f)); Switch(accent, { accent = it })
        }
        if (vib?.hasVibrator() == true) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.metro_vibrate), modifier = Modifier.weight(1f)); Switch(vibrate, { vibrate = it })
        }
    }
}
