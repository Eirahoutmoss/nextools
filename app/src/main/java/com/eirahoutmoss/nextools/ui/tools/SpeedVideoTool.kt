package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.GateTimer
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.MeasureOverlay
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.NumberInputDialog
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.components.screenColumnLuma
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.flow.MutableStateFlow

private data class GateState(val state: GateTimer.State = GateTimer.State.ARMED, val seconds: Double? = null, val direction: Int = 0, val fps: Double = 0.0)

/**
 * Hız Ölçer (video): görüntüde iki dikey kapı; aralarındaki gerçek mesafe girilir. Nesne birinci kapıdan
 * geçince sayaç başlar, ikinciden geçince durur. Kare süresi (ör. 33 ms) belirsizliği belirler.
 */
@Composable
fun SpeedVideoTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.SPEED_VIDEO)
    val points = remember { mutableStateListOf(Offset(0.25f, 0.5f), Offset(0.75f, 0.5f)) }
    val gates = remember { floatArrayOf(0.25f, 0.75f) }
    gates[0] = points[0].x; gates[1] = points[1].x
    val timer = remember { GateTimer(14.0) }
    val armRequest = remember { booleanArrayOf(true) }
    val stateFlow = remember { MutableStateFlow(GateState()) }
    val st by stateFlow.collectAsStateWithLifecycle()
    val lastTs = remember { longArrayOf(0L) }
    var distanceM by rememberSaveable { mutableDoubleStateOf(10.0) }
    var editDist by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<Double>() }
    val inst = LocalInstrument.current

    ToolScaffold(ToolId.SPEED_VIDEO, onBack, onOpenRecord, rec, null, false, null, { editDist = true }) {
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            MeasureOverlay(
                frozen = null, points = points, onView = {},
                onFrame = { img ->
                    val ts = img.imageInfo.timestamp
                    val fps = if (lastTs[0] > 0) 1e9 / (ts - lastTs[0]) else 0.0
                    lastTs[0] = ts
                    if (armRequest[0]) { timer.arm(); armRequest[0] = false }
                    val prof = screenColumnLuma(img)
                    fun gate(f: Float): Double {
                        val c = (f * (prof.size - 1)).toInt(); val r = (prof.size / 100).coerceAtLeast(1)
                        var s = 0.0; var n = 0
                        for (i in (c - r).coerceAtLeast(0)..(c + r).coerceAtMost(prof.size - 1)) { s += prof[i]; n++ }
                        return s / n
                    }
                    timer.feed(gate(gates[0]), gate(gates[1]), ts)
                    stateFlow.value = GateState(timer.state, timer.resultS, timer.direction, if (fps > 0) (fps * 0.1 + stateFlow.value.fps * 0.9).let { if (stateFlow.value.fps == 0.0) fps else it } else stateFlow.value.fps)
                },
            ) { p ->
                p.forEachIndexed { i, o ->
                    drawLine(if (i == 0) inst.good else inst.alarm, Offset(o.x, 0f), Offset(o.x, size.height), 3.dp.toPx())
                    drawCircle(if (i == 0) inst.good else inst.alarm, 12.dp.toPx(), o)
                }
            }
            val secs = st.seconds
            val speed = secs?.let { if (it > 0) distanceM / it else null }
            BigReadout(
                value = speed?.let { Fmt.num(it * 3.6, 1) } ?: "—", unit = "km/h",
                caption = when (st.state) {
                    GateTimer.State.ARMED -> stringResource(R.string.speed_armed)
                    GateTimer.State.FIRST_HIT -> stringResource(R.string.speed_timing)
                    GateTimer.State.DONE -> stringResource(if (st.direction > 0) R.string.speed_dir_ab else R.string.speed_dir_ba)
                },
                badge = stringResource(R.string.ruler_badge), badgeLevel = BadgeLevel.APPROX,
            )
            StatRow(
                listOf(
                    stringResource(R.string.speed_time) to (secs?.let { Fmt.num(it, 3) + " s" } ?: "—"),
                    "m/s" to (speed?.let { Fmt.num(it, 2) } ?: "—"),
                    stringResource(R.string.speed_distance) to Fmt.num(distanceM, 2) + " m",
                ),
            )
            if (st.fps > 0) {
                val frame = 1 / st.fps
                KeyValue(stringResource(R.string.speed_fps), Fmt.num(st.fps, 0) + " fps")
                if (secs != null && secs > 0) KeyValue(stringResource(R.string.speed_uncertainty), "± " + Fmt.num(frame / secs * 100, 0) + " %")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (st.state == GateTimer.State.DONE && speed != null) {
                        history.add(0, speed * 3.6); while (history.size > 10) history.removeAt(history.lastIndex)
                        if (rec.isRecording) rec.add(doubleArrayOf(speed * 3.6, secs!!))
                    }
                    armRequest[0] = true
                }) { Text(stringResource(R.string.speed_rearm)) }
                OutlinedButton(onClick = { editDist = true }) { Text(stringResource(R.string.speed_set_distance)) }
            }
            if (speed != null) OutlinedButton(onClick = {
                rec.saveOneShot(
                    RecordingSpec(
                        columns = listOf(DataColumn("speed_kmh", "km/h", 1), DataColumn("time_s", "s", 3)),
                        headerComments = csvHeader(context, ToolId.SPEED_VIDEO, "camera (back)", context.getString(R.string.speed_note, Fmt.num(distanceM, 2))),
                        calibrated = false, calibrationNote = context.getString(R.string.speed_note, Fmt.num(distanceM, 2)), sensorName = context.getString(R.string.hw_camera),
                        summarize = { _, _ -> listOf(SummaryItem("last", "speed_kmh", speed * 3.6, "km/h", 1)) },
                    ),
                    listOf(doubleArrayOf(speed * 3.6, secs ?: Double.NaN)),
                )
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_to_notebook)) }
            if (history.isNotEmpty()) SectionCard(stringResource(R.string.sonar_history)) { history.forEachIndexed { i, v -> KeyValue("#${history.size - i}", Fmt.num(v, 1) + " km/h") } }
            NoticeBanner(stringResource(R.string.speed_hold_still), BadgeLevel.NEUTRAL)
            Text(stringResource(R.string.speed_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editDist) {
        val bad = stringResource(R.string.error_number)
        NumberInputDialog(
            title = stringResource(R.string.speed_set_distance), message = stringResource(R.string.speed_distance_msg), label = "m", initial = distanceM,
            onConfirm = { v -> if (v > 0.1 && v < 1000) { distanceM = v; editDist = false; null } else bad }, onDismiss = { editDist = false },
        )
    }
}
