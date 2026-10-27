package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.SoundLevelMeter
import com.eirahoutmoss.nextools.core.measure.SoundScale
import com.eirahoutmoss.nextools.core.measure.TimeWeighting
import com.eirahoutmoss.nextools.core.measure.Weighting
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.sensors.SoundMeterEngine
import com.eirahoutmoss.nextools.sensors.SoundState
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.ArcGauge
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.LiveChart
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.NumberInputDialog
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.WaveStrip
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument

private fun bandText(b: SoundScale.Band): Int = when (b) {
    SoundScale.Band.VERY_QUIET -> R.string.sound_band_very_quiet
    SoundScale.Band.QUIET_ROOM -> R.string.sound_band_quiet_room
    SoundScale.Band.QUIET_OFFICE -> R.string.sound_band_quiet_office
    SoundScale.Band.CONVERSATION -> R.string.sound_band_conversation
    SoundScale.Band.BUSY_STREET -> R.string.sound_band_busy_street
    SoundScale.Band.HARMFUL_LONG -> R.string.sound_band_harmful
    SoundScale.Band.DANGEROUS -> R.string.sound_band_dangerous
}

/** Ses Seviyesi (dB): A/C/Z ağırlıklı, Hızlı/Yavaş zaman ağırlıklı, Leq / Lmax; kalibre edilebilir. */
@Composable
fun SoundLevelTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    PermissionGateScreen(onBack, onOpenRecord, granted = context.hasPermission(Manifest.permission.RECORD_AUDIO))
}

@Composable
private fun PermissionGateScreen(onBack: () -> Unit, onOpenRecord: (String) -> Unit, granted: Boolean) {
    var ok by remember { mutableStateOf(granted) }
    if (ok) {
        SoundLevelContent(onBack, onOpenRecord)
    } else {
        ToolScaffold(
            tool = ToolId.SOUND_LEVEL, onBack = onBack, onOpenRecord = onOpenRecord,
            recording = null, onStartRecording = null, frozen = false, onFreezeToggle = null, onCalibrate = null,
        ) {
            PermissionGate(Manifest.permission.RECORD_AUDIO, stringResource(R.string.sound_mic_rationale)) {
                LaunchedEffect(Unit) { ok = true }
            }
        }
    }
}

@Composable
private fun SoundLevelContent(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val engine = remember { SoundMeterEngine(context) }
    val state by engine.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val started = rememberIsStarted()
    val rec = rememberRecording(ToolId.SOUND_LEVEL)
    var frozen by remember { mutableStateOf(false) }
    var calibrating by remember { mutableStateOf(false) }
    val series = remember { RollingSeries(300) }
    val wave = remember { RollingSeries(200) }
    var lastLeq by remember { mutableDoubleStateOf(Double.NaN) }
    var shown by remember { mutableStateOf<com.eirahoutmoss.nextools.sensors.SoundReading?>(null) }
    val weighting = cal.soundWeighting
    val timing = cal.soundTiming
    engine.offsetDb = cal.soundOffset ?: SoundLevelMeter.DEFAULT_OFFSET_DB

    // Mikrofon yalnızca ekran görünürken açık.
    LaunchedEffect(started, weighting, timing) {
        if (started) engine.start(scope, weighting, timing) else engine.stop()
    }
    DisposableEffect(Unit) { onDispose { engine.stop() } }

    val reading = (state as? SoundState.Running)?.reading
    LaunchedEffect(reading) {
        val r = reading ?: return@LaunchedEffect
        if (rec.isRecording) { rec.add(doubleArrayOf(r.level, r.leq)); lastLeq = r.leq }
        if (!frozen) {
            shown = r
            series.add(r.level.toFloat())
            r.wave.forEachIndexed { i, v -> if (i % 2 == 0) wave.add(v) }
        }
    }

    val calibrated = cal.soundOffset != null
    val unitLabel = "dB(${weighting.code})"
    ToolScaffold(
        tool = ToolId.SOUND_LEVEL, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            engine.resetStats()
            val note = (cal.soundOffset?.let { context.getString(R.string.sound_cal_note, Fmt.num(it, 1)) } ?: context.getString(R.string.sound_uncal_note)) +
                " · ${weighting.code}/${timing.code}"
            val src = (state as? SoundState.Running)?.let { "${it.source} ${it.sampleRate} Hz" }.orEmpty()
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("level_db", unitLabel, 1), DataColumn("leq_db", unitLabel, 1)),
                    headerComments = csvHeader(context, ToolId.SOUND_LEVEL, "microphone $src", note, listOf("weighting=${weighting.code}", "time_weighting=${timing.code}", "audio is not stored")),
                    calibrated = calibrated, calibrationNote = note, sensorName = context.getString(R.string.hw_microphone) + " · " + src,
                    summarize = { stats, _ ->
                        val s = stats["level_db"]
                        listOfNotNull(
                            SummaryItem("leq", "level_db", lastLeq, unitLabel, 1),
                            s?.let { SummaryItem("lmax", "level_db", it.max, unitLabel, 1) },
                            s?.let { SummaryItem("lmin", "level_db", it.min, unitLabel, 1) },
                        )
                    },
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen },
        onCalibrate = { calibrating = true },
        infoExtra = {
            (state as? SoundState.Running)?.let { Text(stringResource(R.string.sound_source, it.source, it.sampleRate), style = MaterialTheme.typography.bodySmall) }
        },
    ) {
        when (val st = state) {
            is SoundState.Failed -> { NoticeBanner(stringResource(R.string.sound_mic_failed, st.code), BadgeLevel.ALARM); return@ToolScaffold }
            else -> Unit
        }
        val r = shown
        val inst = LocalInstrument.current
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ArcGauge(
                value = r?.level ?: Double.NaN, min = 20.0, max = 120.0, warnFrom = 85.0, alarmFrom = 100.0,
                modifier = Modifier.fillMaxWidth(0.92f).aspectRatio(1.35f),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 36.dp)) {
                BigReadout(
                    value = r?.let { Fmt.num(it.level, 1) } ?: "—", unit = unitLabel,
                    badge = null, badgeLevel = BadgeLevel.NEUTRAL,
                    valueColor = when {
                        r == null -> MaterialTheme.colorScheme.onBackground
                        SoundScale.isDanger(r.level) -> inst.alarm
                        SoundScale.isWarning(r.level) -> inst.warn
                        else -> MaterialTheme.colorScheme.onBackground
                    },
                )
                if (r != null) Text(stringResource(bandText(SoundScale.band(r.level))), style = MaterialTheme.typography.labelLarge, color = inst.accent, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            com.eirahoutmoss.nextools.ui.components.AccuracyBadge(
                stringResource(if (calibrated) R.string.badge_calibrated else R.string.sound_badge_uncal),
                if (calibrated) BadgeLevel.GOOD else BadgeLevel.APPROX,
            )
        }
        StatRow(
            listOf(
                stringResource(R.string.stat_min) to (r?.let { Fmt.num(it.lmin, 1) } ?: "—"),
                stringResource(R.string.sum_leq) to (r?.let { Fmt.num(it.leq, 1) } ?: "—"),
                stringResource(R.string.stat_max) to (r?.let { Fmt.num(it.lmax, 1) } ?: "—"),
            ),
        )
        SectionCard(null) {
            WaveStrip(wave, inst.accent)
            LiveChart(listOf(series to inst.accent), fixedMin = 20f, fixedMax = 120f, threshold = 85f, height = 120.dp)
        }
        if (r?.clipping == true) NoticeBanner(stringResource(R.string.sound_clipping), BadgeLevel.ALARM)
        SectionCard(null) {
            Text(stringResource(R.string.sound_weighting), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Weighting.entries.forEach { w ->
                    FilterChip(selected = weighting == w, enabled = !rec.isRecording, onClick = { c.calibrations.setSoundWeighting(w) }, label = { Text(stringResource(when (w) { Weighting.A -> R.string.sound_w_a; Weighting.C -> R.string.sound_w_c; Weighting.Z -> R.string.sound_w_z })) })
                }
            }
            Text(stringResource(R.string.sound_timing), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeWeighting.entries.forEach { t ->
                    FilterChip(selected = timing == t, enabled = !rec.isRecording, onClick = { c.calibrations.setSoundTiming(t) }, label = { Text(stringResource(if (t == TimeWeighting.FAST) R.string.sound_t_fast else R.string.sound_t_slow)) })
                }
            }
        }
        OutlinedButton(onClick = { engine.resetStats(); series.clear() }) { Text(stringResource(R.string.action_reset_stats)) }
        Text(stringResource(R.string.sound_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (calibrating) {
        val current = shown?.level
        NumberInputDialog(
            title = stringResource(R.string.sound_cal_title),
            message = stringResource(R.string.sound_cal_message, current?.let { Fmt.num(it, 1) } ?: "—"),
            label = stringResource(R.string.sound_cal_label),
            initial = null,
            onConfirm = { v ->
                if (current == null || current.isNaN()) context.getString(R.string.waiting_sensor)
                else if (v < 20 || v > 130) context.getString(R.string.error_range, "20", "130")
                else {
                    val newOffset = (cal.soundOffset ?: SoundLevelMeter.DEFAULT_OFFSET_DB) + (v - current)
                    c.calibrations.setSoundOffset(newOffset); calibrating = false; null
                }
            },
            onDismiss = { calibrating = false },
            extra = {
                Text(stringResource(R.string.sound_cal_tip), style = MaterialTheme.typography.bodySmall)
                if (calibrated) OutlinedButton(onClick = { c.calibrations.reset(ToolId.SOUND_LEVEL); calibrating = false }) { Text(stringResource(R.string.cal_reset_title)) }
            },
        )
    }
}
