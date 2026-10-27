package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.LevelMath
import com.eirahoutmoss.nextools.core.measure.LowPass
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.settings.LevelCalibration
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private class LevelReading(
    val mode: LevelMath.Mode,
    /** Düzeltilmiş (kalibrasyon uygulanmış) değerler. */
    val x: Double, val y: Double, val tilt: Double,
    val edgeDev: Double, val edgeAngle: Double,
    /** Kalibrasyon için ham değerler. */
    val rawX: Double, val rawY: Double, val rawEdge: Double,
)

/** Su Terazisi / Eğim Ölçer: yüzeyde iki eksenli kabarcık, kenarda tek açı. */
@Composable
fun LevelTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val gravity = remember { sm.getDefaultSensor(Sensor.TYPE_GRAVITY) }
    val accel = remember { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val sensor = gravity ?: accel
    val rec = rememberRecording(ToolId.LEVEL)
    var reading by remember { mutableStateOf<LevelReading?>(null) }
    var frozen by remember { mutableStateOf(false) }
    var calibrating by remember { mutableStateOf(false) }
    val filters = remember { Triple(LowPass(0.12), LowPass(0.12), LowPass(0.12)) }
    var mode by remember { mutableStateOf(LevelMath.Mode.FLAT) }
    val uiThrottle = remember { Throttle(40_000_000L) }
    val recThrottle = remember { Throttle(50_000_000L) }

    CollectSensors(listOfNotNull(sensor), SensorManager.SENSOR_DELAY_GAME) { item ->
        if (item !is SensorEventItem.Sample) return@CollectSensors
        val s = item.sample
        // Yerçekimi sensörü zaten süzülmüştür; ham ivmeölçer hafifçe süzülür.
        val gx = if (gravity != null) s.values[0].toDouble() else filters.first.update(s.values[0].toDouble(), s.timestampNs)
        val gy = if (gravity != null) s.values[1].toDouble() else filters.second.update(s.values[1].toDouble(), s.timestampNs)
        val gz = if (gravity != null) s.values[2].toDouble() else filters.third.update(s.values[2].toDouble(), s.timestampNs)
        mode = LevelMath.chooseMode(mode, LevelMath.tiltFromFlat(gx, gy, gz))
        val f = LevelMath.flat(gx, gy, gz)
        val e = LevelMath.edge(gx, gy)
        val lc = cal.level
        val x = f.x - (lc?.flatX ?: 0.0)
        val y = f.y - (lc?.flatY ?: 0.0)
        val r = LevelReading(
            mode = mode, x = x, y = y, tilt = kotlin.math.sqrt(x * x + y * y),
            edgeDev = e.deviation - (lc?.edge ?: 0.0), edgeAngle = e.angle,
            rawX = f.x, rawY = f.y, rawEdge = e.deviation,
        )
        if (rec.isRecording && recThrottle.ready(s.timestampNs)) rec.add(doubleArrayOf(r.x, r.y, r.tilt, r.edgeDev))
        if (!frozen && uiThrottle.ready(s.timestampNs)) reading = r
    }

    val calibrated = cal.level != null
    val inst = LocalInstrument.current
    ToolScaffold(
        tool = ToolId.LEVEL, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = cal.level?.let { context.getString(R.string.level_cal_note, Fmt.dateTime(it.setAtMs)) } ?: context.getString(R.string.pdf_not_calibrated)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("x_deg", "°", 2), DataColumn("y_deg", "°", 2), DataColumn("tilt_deg", "°", 2), DataColumn("angle_deg", "°", 2)),
                    headerComments = csvHeader(context, ToolId.LEVEL, sensor?.name.orEmpty(), note, listOf("x/y/tilt: surface mode; angle: edge mode deviation")),
                    calibrated = calibrated, calibrationNote = note, sensorName = sensor?.name.orEmpty(),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen },
        onCalibrate = { calibrating = true },
        infoExtra = { SensorInfo(sensor) },
    ) {
        val r = reading
        if (r == null) {
            Text(stringResource(R.string.waiting_sensor))
            return@ToolScaffold
        }
        val badge = stringResource(if (calibrated) R.string.level_badge_cal else R.string.level_badge_uncal)
        if (r.mode == LevelMath.Mode.FLAT) {
            val isLevel = r.tilt < LevelMath.LEVEL_THRESHOLD
            BigReadout(
                value = Fmt.num(r.tilt, 1), unit = "°", badge = badge,
                badgeLevel = if (calibrated) BadgeLevel.GOOD else BadgeLevel.APPROX,
                valueColor = if (isLevel) inst.good else MaterialTheme.colorScheme.onBackground,
                caption = stringResource(if (isLevel) R.string.level_is_level else R.string.level_mode_flat),
            )
            Bubble(r.x, r.y)
            StatRow(
                listOf(
                    stringResource(R.string.level_axis_x) to Fmt.signed(r.x, 1) + "°",
                    stringResource(R.string.level_axis_y) to Fmt.signed(r.y, 1) + "°",
                    stringResource(R.string.level_slope) to (LevelMath.percent(r.tilt)?.let { "%" + Fmt.num(it, 1) } ?: "—"),
                ),
            )
        } else {
            val isLevel = abs(r.edgeDev) < LevelMath.LEVEL_THRESHOLD
            BigReadout(
                value = Fmt.num(abs(r.edgeDev), 1), unit = "°", badge = badge,
                badgeLevel = if (calibrated) BadgeLevel.GOOD else BadgeLevel.APPROX,
                valueColor = if (isLevel) inst.good else MaterialTheme.colorScheme.onBackground,
                caption = stringResource(if (isLevel) R.string.level_is_level else R.string.level_mode_edge),
            )
            EdgeGauge(r.edgeDev)
            StatRow(
                listOf(
                    stringResource(R.string.level_full_angle) to Fmt.num(r.edgeAngle, 1) + "°",
                    stringResource(R.string.level_slope) to (LevelMath.percent(r.edgeDev)?.let { "%" + Fmt.num(abs(it), 1) } ?: "—"),
                ),
            )
        }
        SectionCard(null) {
            Text(stringResource(R.string.level_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (calibrating) {
        val r = reading
        AlertDialog(
            onDismissRequest = { calibrating = false },
            title = { Text(stringResource(R.string.action_calibrate)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.level_cal_explain))
                    Button(enabled = r != null, onClick = {
                        if (r != null) {
                            val old = cal.level
                            c.calibrations.setLevel(
                                if (r.mode == LevelMath.Mode.FLAT) LevelCalibration(r.rawX, r.rawY, old?.edge ?: 0.0, System.currentTimeMillis())
                                else LevelCalibration(old?.flatX ?: 0.0, old?.flatY ?: 0.0, r.rawEdge, System.currentTimeMillis()),
                            )
                        }
                        calibrating = false
                    }) { Text(stringResource(R.string.level_cal_set_zero)) }
                    if (calibrated) OutlinedButton(onClick = { c.calibrations.reset(ToolId.LEVEL); calibrating = false }) { Text(stringResource(R.string.cal_reset_title)) }
                }
            },
            confirmButton = { TextButton(onClick = { calibrating = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

/** İki eksenli kabarcık: halkalar 1°, 2°, 5°; kenar 10°. */
@Composable
private fun Bubble(x: Double, y: Double) {
    val inst = LocalInstrument.current
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 9.sp, color = inst.scale)
    Canvas(Modifier.fillMaxWidth(0.82f).aspectRatio(1f).padding(8.dp)) {
        val r = min(size.width, size.height) / 2
        val c = center
        val full = 10.0
        drawCircle(inst.grid, r, c)
        listOf(1.0, 2.0, 5.0).forEach { deg ->
            val rr = (r * deg / full).toFloat()
            drawCircle(inst.scale.copy(alpha = 0.5f), rr, c, style = Stroke(1.dp.toPx()))
            val lay = measurer.measure("${deg.toInt()}°", label)
            drawText(lay, topLeft = Offset(c.x + rr + 2.dp.toPx(), c.y - lay.size.height))
        }
        drawCircle(inst.scale, r, c, style = Stroke(2.dp.toPx()))
        drawLine(inst.scale.copy(alpha = 0.6f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        drawLine(inst.scale.copy(alpha = 0.6f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
        // Kabarcık yüksek tarafa gider: x > 0 sağ yüksek → sağa; y > 0 üst yüksek → yukarı.
        val bx = (x / full).coerceIn(-1.0, 1.0)
        val by = (y / full).coerceIn(-1.0, 1.0)
        val br = r * 0.14f
        val pos = Offset(c.x + (bx * (r - br)).toFloat(), c.y - (by * (r - br)).toFloat())
        val tilt = kotlin.math.sqrt(x * x + y * y)
        val color = if (tilt < LevelMath.LEVEL_THRESHOLD) inst.good else if (tilt < 2) inst.accent else inst.warn
        drawCircle(color.copy(alpha = 0.35f), br, pos)
        drawCircle(color, br, pos, style = Stroke(3.dp.toPx()))
    }
}

/** Kenar kipinde dönen çizgi + ölçek. */
@Composable
private fun EdgeGauge(dev: Double) {
    val inst = LocalInstrument.current
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.8f).padding(8.dp)) {
        val r = min(size.width / 2, size.height) * 0.92f
        val pivot = Offset(size.width / 2, size.height * 0.95f)
        for (d in -45..45 step 5) {
            val a = Math.toRadians(d.toDouble() - 90)
            val len = if (d % 15 == 0) 18.dp.toPx() else 9.dp.toPx()
            val p1 = Offset(pivot.x + (r * cos(a)).toFloat(), pivot.y + (r * sin(a)).toFloat())
            val p2 = Offset(pivot.x + ((r - len) * cos(a)).toFloat(), pivot.y + ((r - len) * sin(a)).toFloat())
            drawLine(if (d == 0) inst.good else inst.scale, p1, p2, if (d == 0) 3.dp.toPx() else 1.5.dp.toPx())
        }
        val color = if (abs(dev) < LevelMath.LEVEL_THRESHOLD) inst.good else inst.accent
        // İbre: telefonun alt kenarı yatayken dik durur; sapma kadar döner.
        rotate(dev.toFloat().coerceIn(-45f, 45f), pivot = pivot) {
            drawLine(color, pivot, Offset(pivot.x, pivot.y - r * 0.9f), 4.dp.toPx())
        }
        drawCircle(color, 7.dp.toPx(), pivot)
        drawLine(inst.scale.copy(alpha = 0.5f), Offset(pivot.x - r, pivot.y), Offset(pivot.x + r, pivot.y), 1.dp.toPx())
    }
}
