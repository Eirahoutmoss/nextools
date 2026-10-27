package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.AcousticsNote
import com.eirahoutmoss.nextools.core.measure.AcousticsResult
import com.eirahoutmoss.nextools.core.measure.Quality
import com.eirahoutmoss.nextools.core.measure.RoomAcoustics
import com.eirahoutmoss.nextools.core.measure.RoomVerdict
import com.eirahoutmoss.nextools.core.measure.Sweep
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.sensors.AcousticException
import com.eirahoutmoss.nextools.sensors.AcousticRecorder
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.MeterBar
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingController
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatusDot
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tek seferlik ölçümü Defter'e alır: satırlar yazılır, kayıt kapanır, kaydetme penceresi açılır.
 * (Oda Yankısı ve Makine Sağlığı gibi "ölç → sonuç" araçlar için.)
 */
fun RecordingController.saveOneShot(spec: RecordingSpec, rows: List<DoubleArray>) {
    start(spec); rows.forEach { add(it) }; stop()
}

private fun noteText(n: AcousticsNote) = when (n) {
    AcousticsNote.MID_UNMEASURED -> R.string.rt_note_mid
    AcousticsNote.LOW_RANGE -> R.string.rt_note_range
    AcousticsNote.SPEAKER_BASS_WEAK -> R.string.rt_note_bass
    AcousticsNote.BASS_NEEDS_EXTERNAL -> R.string.rt_note_external
}

private fun verdictText(v: RoomVerdict.Verdict) = when (v) {
    RoomVerdict.Verdict.DEAD -> R.string.rt_dead
    RoomVerdict.Verdict.BALANCED -> R.string.rt_balanced
    RoomVerdict.Verdict.SLIGHTLY_LIVE -> R.string.rt_slightly_live
    RoomVerdict.Verdict.LIVE -> R.string.rt_live
}

/** Oda Yankısı (RT60): üstel tarama çalınır ve kaydedilir; dürtü yanıtından bant bant yankı süresi. */
@Composable
fun RoomEchoTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.RT60)
    val scope = rememberCoroutineScope()
    var external by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf<Job?>(null) }
    var step by remember { mutableStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<AcousticsResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var volume by remember { mutableStateOf(1.0) }

    fun measure() {
        error = null; result = null
        running = scope.launch {
            try {
                val sweep = withContext(Dispatchers.Default) { Sweep.create(fs = 48_000, durationS = 3.0) }
                val recorder = AcousticRecorder(context)
                val cap = recorder.capture(sweep, repeats = 3, tailS = 2.0) { s, f -> step = s; progress = f }
                volume = cap.mediaVolumeRatio
                result = withContext(Dispatchers.Default) {
                    val irs = cap.sweeps.map { RoomAcoustics.impulseResponse(it, sweep) }
                    RoomAcoustics.analyze(RoomAcoustics.average(irs), sweep.fs, external)
                }
            } catch (e: AcousticException) {
                error = context.getString(R.string.sound_mic_failed, e.code)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally { running = null }
        }
    }

    ToolScaffold(
        tool = ToolId.RT60, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec, onStartRecording = null, frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        PermissionGate(Manifest.permission.RECORD_AUDIO, stringResource(R.string.mic_rationale_generic)) {
            val r = result
            BigReadout(
                value = r?.rt60MidS?.let { Fmt.num(it, 2) } ?: "—", unit = "s",
                caption = r?.rt60MidS?.let { stringResource(verdictText(RoomVerdict.of(it))) } ?: stringResource(R.string.rt_caption),
                badge = r?.let { stringResource(qualityText(it.quality)) }, badgeLevel = when (r?.quality) {
                    Quality.OK -> BadgeLevel.GOOD; Quality.LOW -> BadgeLevel.APPROX; else -> BadgeLevel.NEUTRAL
                },
            )
            val job = running
            if (job != null) {
                Text(stringResource(if (step == 0) R.string.rt_step_noise else R.string.rt_step_sweep, step, 3), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { job.cancel() }) { Text(stringResource(R.string.action_cancel)) }
            } else {
                Button(onClick = { measure() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (r == null) R.string.rt_start else R.string.rt_again)) }
            }
            error?.let { NoticeBanner(it, BadgeLevel.ALARM) }
            if (volume < 0.6 && r != null) NoticeBanner(stringResource(R.string.rt_volume_low))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rt_external), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(external, { external = it })
            }
            r?.let { res ->
                SectionCard(stringResource(R.string.rt_bands)) {
                    val inst = LocalInstrument.current
                    res.bands.forEach { b ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(when (b.quality) { Quality.OK -> inst.good; Quality.LOW -> inst.warn; Quality.UNRELIABLE -> inst.passive })
                                Spacer(Modifier.width(8.dp))
                                Text(if (b.centerHz >= 1000) Fmt.num(b.centerHz / 1000, 0) + " kHz" else Fmt.num(b.centerHz, 0) + " Hz", modifier = Modifier.weight(1f))
                                Text(b.rt60S?.let { Fmt.num(it, 2) + " s" } ?: "—", style = MaterialTheme.typography.labelLarge)
                            }
                            MeterBar(b.rt60S ?: 0.0, 0.6, 2.0, inst.accent, log = false)
                        }
                    }
                    Text(stringResource(R.string.rt_bands_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                res.notes.forEach { NoticeBanner(stringResource(noteText(it)), BadgeLevel.NEUTRAL) }
                OutlinedButton(onClick = { saveRt(rec, res, external, context) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_to_notebook)) }
            }
            Text(stringResource(R.string.rt_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun qualityText(q: Quality) = when (q) {
    Quality.OK -> R.string.rt_quality_ok
    Quality.LOW -> R.string.rt_quality_low
    Quality.UNRELIABLE -> R.string.rt_quality_bad
}

private fun saveRt(rec: RecordingController, res: AcousticsResult, external: Boolean, context: android.content.Context) {
    val note = context.getString(if (external) R.string.rt_note_ext_speaker else R.string.rt_note_phone_speaker)
    rec.saveOneShot(
        RecordingSpec(
            columns = listOf(DataColumn("band_hz", "Hz", 0), DataColumn("rt60_s", "s", 2), DataColumn("range_db", "dB", 1)),
            headerComments = csvHeader(context, ToolId.RT60, "speaker + microphone", note, listOf("one row per octave band; t_ms has no meaning here")),
            calibrated = false, calibrationNote = note, sensorName = context.getString(R.string.hw_microphone),
            summarize = { _, _ -> listOf(SummaryItem("rt60_mid", "rt60_s", res.rt60MidS ?: Double.NaN, "s", 2)) },
        ),
        res.bands.map { doubleArrayOf(it.centerHz, it.rt60S ?: Double.NaN, it.dynamicRangeDb) },
    )
}
