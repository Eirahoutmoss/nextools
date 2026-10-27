package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.geo.Geo
import com.eirahoutmoss.nextools.core.geo.LatLon
import com.eirahoutmoss.nextools.core.measure.Driving
import com.eirahoutmoss.nextools.core.measure.LowPass
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlin.math.abs
import kotlin.math.min

private class DriveEvent(val type: Driving.Event, val atMs: Long, val g: Double)

private fun eventText(e: Driving.Event) = when (e) {
    Driving.Event.HARSH_BRAKE -> R.string.drive_brake
    Driving.Event.HARSH_ACCEL -> R.string.drive_accel
    Driving.Event.SHARP_TURN -> R.string.drive_turn
}

/** Sürüş Profili: GPS hız değişiminden boyuna, jiroskop sapma hızından yanal ivme; sert olaylar ve puan. */
@Composable
fun DrivingProfileTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val gyro = remember { sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val accel = remember { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val rec = rememberRecording(ToolId.DRIVING_PROFILE)
    val grav = remember { Triple(LowPass(2.0), LowPass(2.0), LowPass(2.0)) }
    val g = remember { DoubleArray(3) }
    val yawLp = remember { LowPass(0.5) }
    var yaw by remember { mutableDoubleStateOf(0.0) }
    var longG by remember { mutableDoubleStateOf(0.0) }
    var latG by remember { mutableDoubleStateOf(0.0) }
    var fix by remember { mutableStateOf<Location?>(null) }
    var prev by remember { mutableStateOf<Location?>(null) }
    var distance by remember { mutableDoubleStateOf(0.0) }
    val events = remember { mutableStateListOf<DriveEvent>() }
    val lastEvent = remember { longArrayOf(0L) }
    val trail = remember { ArrayDeque<Pair<Float, Float>>() }

    fun onFix(l: Location) {
        val p = prev
        if (p != null && l.hasSpeed() && p.hasSpeed()) {
            val dt = (l.elapsedRealtimeNanos - p.elapsedRealtimeNanos) / 1e9
            if (dt in 0.5..3.0) longG = 0.5 * longG + 0.5 * ((l.speed - p.speed) / dt / Driving.G)
            distance += Geo.distance(LatLon(p.latitude, p.longitude), LatLon(l.latitude, l.longitude)).takeIf { l.speed > 1 } ?: 0.0
        }
        val v = if (l.hasSpeed()) l.speed.toDouble() else 0.0
        latG = if (v > 3) v * yaw / Driving.G else 0.0
        val now = System.currentTimeMillis()
        Driving.classify(longG, latG)?.let { e ->
            if (now - lastEvent[0] > 3000 && v > 2) { lastEvent[0] = now; events.add(0, DriveEvent(e, now, if (e == Driving.Event.SHARP_TURN) latG else longG)) }
        }
        trail.addLast(latG.toFloat() to longG.toFloat()); while (trail.size > 60) trail.removeFirst()
        if (rec.isRecording) rec.add(doubleArrayOf(v * 3.6, longG, latG, events.size.toDouble()))
        prev = l; fix = l
    }

    ToolScaffold(
        tool = ToolId.DRIVING_PROFILE, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            events.clear(); distance = 0.0
            val note = context.getString(R.string.drive_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("speed_kmh", "km/h", 1), DataColumn("long_g", "g", 3), DataColumn("lat_g", "g", 3), DataColumn("events", "", 0)),
                    headerComments = csvHeader(context, ToolId.DRIVING_PROFILE, "GNSS + gyroscope", note),
                    calibrated = false, calibrationNote = note, sensorName = gyro?.name.orEmpty(),
                    summarize = { st, _ ->
                        listOf(
                            SummaryItem("total", "distance_m", distance, "m", 0),
                            SummaryItem("count", "events", events.size.toDouble(), "", 0),
                            SummaryItem("score", "events", Driving.score(events.size, distance / 1000).toDouble(), "/100", 0),
                            SummaryItem("max", "speed_kmh", st["speed_kmh"]?.max ?: Double.NaN, "km/h", 0),
                        )
                    },
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        PermissionsGate(LOCATION_PERMISSIONS, stringResource(R.string.gps_rationale), required = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            CollectGnss(onLocation = { onFix(it) }, withSatellites = false)
            CollectSensors(listOfNotNull(gyro, accel), SensorManager.SENSOR_DELAY_GAME) { item ->
                if (item !is SensorEventItem.Sample) return@CollectSensors
                val s = item.sample
                if (s.type == Sensor.TYPE_ACCELEROMETER) {
                    g[0] = grav.first.update(s.values[0].toDouble(), s.timestampNs); g[1] = grav.second.update(s.values[1].toDouble(), s.timestampNs); g[2] = grav.third.update(s.values[2].toDouble(), s.timestampNs)
                } else {
                    yaw = yawLp.update(Driving.yawRate(s.values[0].toDouble(), s.values[1].toDouble(), s.values[2].toDouble(), g[0], g[1], g[2]), s.timestampNs)
                }
            }
            GpsOffBanner()
            if (gyro == null) NoticeBanner(stringResource(R.string.drive_no_gyro))
            val score = Driving.score(events.size, distance / 1000)
            BigReadout(
                value = score.toString(), unit = "/100", caption = stringResource(R.string.drive_score),
                badge = stringResource(R.string.drive_badge), badgeLevel = BadgeLevel.APPROX,
            )
            StatRow(
                listOf(
                    stringResource(R.string.gps_speed) to (fix?.takeIf { it.hasSpeed() }?.let { Fmt.num(it.speed * 3.6, 0) } ?: "—"),
                    stringResource(R.string.drive_long) to Fmt.signed(longG, 2) + " g",
                    stringResource(R.string.drive_lat) to Fmt.signed(latG, 2) + " g",
                ),
            )
            SectionCard(stringResource(R.string.drive_gg)) { GgPlot(longG, latG, trail.toList()) }
            SectionCard(stringResource(R.string.drive_events, events.size)) {
                KeyValue(stringResource(R.string.col_distance), Fmt.num(distance / 1000, 2) + " km")
                events.take(20).forEach { e -> KeyValue(Fmt.dateTime(e.atMs), stringResource(eventText(e.type)) + " · " + Fmt.signed(e.g, 2) + " g") }
            }
            Text(stringResource(R.string.drive_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** g-g diyagramı: yatay yanal, dikey boyuna ivme; daireler 0,2 / 0,4 g. */
@Composable
private fun GgPlot(longG: Double, latG: Double, trail: List<Pair<Float, Float>>) {
    val inst = LocalInstrument.current
    Canvas(Modifier.fillMaxWidth(0.7f).aspectRatio(1f)) {
        val c = center; val r = min(size.width, size.height) / 2
        val k = r / 0.6f
        listOf(0.2f, 0.4f).forEach { drawCircle(if (it == 0.4f) inst.warn.copy(alpha = 0.6f) else inst.grid, it * k, c, style = Stroke(1.dp.toPx())) }
        drawLine(inst.grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y)); drawLine(inst.grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r))
        trail.forEachIndexed { i, (la, lo) -> drawCircle(inst.accent.copy(alpha = 0.1f + 0.5f * i / trail.size), 3.dp.toPx(), Offset(c.x + la * k, c.y - lo * k)) }
        val hot = abs(longG) > 0.3 || abs(latG) > 0.4
        drawCircle(if (hot) inst.alarm else inst.accent, 8.dp.toPx(), Offset(c.x + (latG * k).toFloat().coerceIn(-r, r), c.y - (longG * k).toFloat().coerceIn(-r, r)))
    }
}
