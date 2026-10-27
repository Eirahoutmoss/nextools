package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.AngleLowPass
import com.eirahoutmoss.nextools.core.measure.Angles
import com.eirahoutmoss.nextools.core.measure.CompassMath
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.LocationHelper
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.NumberInputDialog
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun CompassTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val rotation = remember { sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val accel = remember { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val mag = remember { sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    val sensors = remember { listOfNotNull(rotation, accel.takeIf { rotation == null }, mag) }
    val rec = rememberRecording(ToolId.COMPASS)
    val points = stringArrayResource(R.array.compass_points)

    var heading by remember { mutableStateOf<Double?>(null) }
    var field by remember { mutableStateOf<Double?>(null) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var frozen by remember { mutableStateOf(false) }
    var showTrue by rememberSaveable { mutableStateOf(true) }
    var calibrating by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf<Location?>(null) }
    var locationGranted by remember { mutableStateOf(LocationHelper.hasPermission(context)) }
    val filter = remember { AngleLowPass(0.15) }
    val g = remember { FloatArray(3) }
    val m = remember { FloatArray(3) }
    var haveG by remember { mutableStateOf(false) }
    var haveM by remember { mutableStateOf(false) }
    val uiThrottle = remember { Throttle(50_000_000L) }
    val recThrottle = remember { Throttle(100_000_000L) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        locationGranted = r.values.any { it }
    }
    LaunchedEffect(locationGranted) {
        if (locationGranted) location = LocationHelper.lastKnown(context) ?: LocationHelper.current(context, timeoutMs = 10_000)
    }
    val model = location?.let { LocationHelper.magneticModel(it) }
    val declination: Double? = cal.manualDeclination ?: model?.declinationDeg

    fun updateHeading(azimuthRad: Float, tNs: Long) {
        val h = filter.update(Angles.normalize360(Math.toDegrees(azimuthRad.toDouble())), tNs)
        if (rec.isRecording && recThrottle.ready(tNs)) {
            rec.add(doubleArrayOf(h, declination?.let { CompassMath.trueHeading(h, it) } ?: Double.NaN, field ?: Double.NaN))
        }
        if (!frozen && uiThrottle.ready(tNs)) heading = h
    }

    CollectSensors(sensors, SensorManager.SENSOR_DELAY_GAME) { item ->
        when (item) {
            is SensorEventItem.Accuracy -> if (item.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy = item.accuracy
            is SensorEventItem.Sample -> {
                val s = item.sample
                when (s.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val r = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(r, s.values)
                        val o = FloatArray(3); SensorManager.getOrientation(r, o)
                        updateHeading(o[0], s.timestampNs)
                    }
                    Sensor.TYPE_ACCELEROMETER -> { s.values.copyInto(g, endIndex = 3); haveG = true }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        s.values.copyInto(m, endIndex = 3); haveM = true
                        if (!frozen) field = sqrt((m[0] * m[0] + m[1] * m[1] + m[2] * m[2]).toDouble())
                        accuracy = s.accuracy
                        if (rotation == null && haveG) {
                            val r = FloatArray(9)
                            if (SensorManager.getRotationMatrix(r, null, g, m)) {
                                val o = FloatArray(3); SensorManager.getOrientation(r, o)
                                updateHeading(o[0], s.timestampNs)
                            }
                        }
                    }
                }
            }
        }
    }

    val inst = LocalInstrument.current
    val quality = field?.let { CompassMath.fieldQuality(it, model?.fieldUt) }
    val needsCal = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
    val sensorName = (rotation ?: mag)?.name.orEmpty()

    ToolScaffold(
        tool = ToolId.COMPASS, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = declination?.let { context.getString(R.string.compass_decl_note, Fmt.signed(it, 1)) } ?: context.getString(R.string.compass_no_decl)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("heading_mag_deg", "°", 1), DataColumn("heading_true_deg", "°", 1), DataColumn("field_ut", "µT", 1)),
                    headerComments = csvHeader(context, ToolId.COMPASS, sensorName, note),
                    calibrated = !needsCal, calibrationNote = note, sensorName = sensorName,
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen },
        onCalibrate = { calibrating = true },
        infoExtra = { SensorInfo(mag) },
    ) {
        val h = heading
        if (h == null) {
            Text(stringResource(R.string.waiting_sensor))
            return@ToolScaffold
        }
        val trueH = declination?.let { CompassMath.trueHeading(h, it) }
        val main = if (showTrue && trueH != null) trueH else h
        val badge = when {
            needsCal -> stringResource(R.string.compass_badge_needs_cal)
            quality == CompassMath.FieldQuality.DISTURBED -> stringResource(R.string.compass_badge_disturbed)
            else -> stringResource(R.string.compass_badge_ok)
        }
        BigReadout(
            value = Fmt.num(main, 0), unit = "°",
            caption = points[CompassMath.pointIndex(main)] + " · " + stringResource(if (showTrue && trueH != null) R.string.compass_true else R.string.compass_magnetic),
            badge = badge,
            badgeLevel = if (needsCal || quality == CompassMath.FieldQuality.DISTURBED) BadgeLevel.APPROX else BadgeLevel.GOOD,
        )
        if (trueH != null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(showTrue, { showTrue = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.compass_true)) }
                SegmentedButton(!showTrue, { showTrue = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.compass_magnetic)) }
            }
        }
        CompassRose(heading = h, declination = declination, points = points)
        StatRow(
            listOf(
                stringResource(R.string.compass_magnetic) to Fmt.num(h, 0) + "°",
                stringResource(R.string.compass_true) to (trueH?.let { Fmt.num(it, 0) + "°" } ?: "—"),
                stringResource(R.string.compass_field) to (field?.let { Fmt.num(it, 0) + " µT" } ?: "—"),
            ),
        )
        if (needsCal) NoticeBanner(stringResource(R.string.compass_figure8))
        if (quality == CompassMath.FieldQuality.DISTURBED) NoticeBanner(stringResource(R.string.compass_disturbed))
        if (declination == null) {
            NoticeBanner(stringResource(R.string.compass_no_decl_hint), BadgeLevel.NEUTRAL)
            if (!locationGranted) {
                OutlinedButton(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                    Text(stringResource(R.string.compass_allow_location))
                }
            }
        } else {
            Text(
                stringResource(
                    if (cal.manualDeclination != null) R.string.compass_decl_manual else R.string.compass_decl_auto,
                    Fmt.signed(declination, 1),
                ),
                style = MaterialTheme.typography.bodySmall, color = inst.scale,
            )
        }
    }

    if (calibrating) {
        var manual by remember { mutableStateOf(false) }
        if (manual) {
            NumberInputDialog(
                title = stringResource(R.string.compass_decl_title),
                message = stringResource(R.string.compass_decl_message),
                label = stringResource(R.string.compass_decl_label),
                initial = cal.manualDeclination ?: model?.declinationDeg,
                onConfirm = { v ->
                    if (v < -90 || v > 90) context.getString(R.string.error_range, "-90", "90")
                    else { c.calibrations.setManualDeclination(v); calibrating = false; null }
                },
                onDismiss = { calibrating = false },
            )
        } else {
            AlertDialog(
                onDismissRequest = { calibrating = false },
                title = { Text(stringResource(R.string.action_calibrate)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.compass_figure8))
                        OutlinedButton(onClick = { manual = true }) { Text(stringResource(R.string.compass_decl_set)) }
                        if (cal.manualDeclination != null) {
                            OutlinedButton(onClick = { c.calibrations.setManualDeclination(null); calibrating = false }) { Text(stringResource(R.string.compass_decl_auto_btn)) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { calibrating = false }) { Text(stringResource(R.string.action_close)) } },
            )
        }
    }
}

/** Pusula gülü: telefonun baktığı yön üstte; kırmızı uç manyetik kuzey, turkuaz üçgen gerçek kuzey. */
@Composable
private fun CompassRose(heading: Double, declination: Double?, points: Array<String>) {
    val inst = LocalInstrument.current
    val measurer = rememberTextMeasurer()
    val onBg = MaterialTheme.colorScheme.onBackground
    val cardinal = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onBg)
    val minor = TextStyle(fontSize = 11.sp, color = inst.scale)
    Canvas(Modifier.fillMaxWidth(0.88f).aspectRatio(1f).padding(6.dp)) {
        val r = min(size.width, size.height) / 2
        val c = center
        // sabit üst işaret (telefonun yönü)
        val top = Path().apply { moveTo(c.x, c.y - r - 2f); lineTo(c.x - 9.dp.toPx(), c.y - r - 16.dp.toPx()); lineTo(c.x + 9.dp.toPx(), c.y - r - 16.dp.toPx()); close() }
        drawCircle(inst.grid, r, c)
        rotate(-heading.toFloat(), pivot = c) {
            for (d in 0 until 360 step 5) {
                val a = Math.toRadians(d - 90.0)
                val len = when { d % 90 == 0 -> 22.dp.toPx(); d % 30 == 0 -> 14.dp.toPx(); else -> 7.dp.toPx() }
                drawLine(
                    if (d == 0) inst.alarm else inst.scale,
                    Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()),
                    Offset(c.x + ((r - len) * cos(a)).toFloat(), c.y + ((r - len) * sin(a)).toFloat()),
                    if (d % 30 == 0) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
            for (k in 0 until 8) {
                val d = k * 45
                val a = Math.toRadians(d - 90.0)
                val label = points[k * 2]
                val lay = measurer.measure(label, if (k % 2 == 0) cardinal else minor)
                val rr = r - 38.dp.toPx()
                val p = Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat())
                rotate(d.toFloat(), pivot = p) {
                    drawText(lay, color = if (k == 0) inst.alarm else onBg.copy(alpha = if (k % 2 == 0) 1f else 0.7f), topLeft = Offset(p.x - lay.size.width / 2, p.y - lay.size.height / 2))
                }
            }
            if (declination != null) {
                val a = Math.toRadians(declination - 90.0)
                val tip = Offset(c.x + ((r + 4.dp.toPx()) * cos(a)).toFloat(), c.y + ((r + 4.dp.toPx()) * sin(a)).toFloat())
                drawCircle(inst.accent, 5.dp.toPx(), tip)
            }
        }
        drawPath(top, inst.accent)
        drawCircle(inst.scale, r, c, style = Stroke(2.dp.toPx()))
        drawLine(inst.accent.copy(alpha = 0.6f), Offset(c.x, c.y - r * 0.5f), Offset(c.x, c.y + r * 0.5f), 1.dp.toPx())
        drawCircle(inst.accent, 4.dp.toPx(), c)
    }
}
