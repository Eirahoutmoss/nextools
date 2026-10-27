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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.geo.Geo
import com.eirahoutmoss.nextools.core.geo.LatLon
import com.eirahoutmoss.nextools.core.measure.LowPass
import com.eirahoutmoss.nextools.core.measure.RoadRoughness
import com.eirahoutmoss.nextools.core.measure.RunningStats
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.LiveChart
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private class RoadPoint(val p: LatLon, val grade: RoadRoughness.Grade)

@Composable
fun gradeColor(g: RoadRoughness.Grade): Color {
    val i = LocalInstrument.current
    return when (g) {
        RoadRoughness.Grade.SMOOTH -> i.good
        RoadRoughness.Grade.FAIR -> i.accent
        RoadRoughness.Grade.ROUGH -> i.warn
        RoadRoughness.Grade.VERY_ROUGH -> i.alarm
    }
}

fun gradeText(g: RoadRoughness.Grade) = when (g) {
    RoadRoughness.Grade.SMOOTH -> R.string.road_smooth
    RoadRoughness.Grade.FAIR -> R.string.road_fair
    RoadRoughness.Grade.ROUGH -> R.string.road_rough
    RoadRoughness.Grade.VERY_ROUGH -> R.string.road_very_rough
}

/** Yol Kalitesi: araçta sabit telefonla düşey sarsıntı; 1 sn'lik pencereler GPS noktasıyla renklendirilir. */
@Composable
fun RoadQualityTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val accel = remember { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val rec = rememberRecording(ToolId.ROAD_QUALITY)
    val gravity = remember { Triple(LowPass(1.5), LowPass(1.5), LowPass(1.5)) }
    val window = remember { RunningStats() }
    val windowStart = remember { longArrayOf(0L) }
    val lastPothole = remember { longArrayOf(0L) }
    var rms by remember { mutableDoubleStateOf(Double.NaN) }
    var potholes by remember { mutableIntStateOf(0) }
    var fix by remember { mutableStateOf<Location?>(null) }
    val path = remember { mutableStateListOf<RoadPoint>() }
    val series = remember { RollingSeries(180) }
    val potholeFlag = remember { booleanArrayOf(false) }

    ToolScaffold(
        tool = ToolId.ROAD_QUALITY, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            path.clear(); potholes = 0
            val note = context.getString(R.string.road_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("lat", "°", 7), DataColumn("lon", "°", 7), DataColumn("rough_ms2", "m/s²", 2),
                        DataColumn("speed_kmh", "km/h", 1), DataColumn("pothole", "", 0)),
                    headerComments = csvHeader(context, ToolId.ROAD_QUALITY, accel?.name.orEmpty(), note, listOf("rough_ms2 = std of vertical acceleration per 1 s")),
                    calibrated = false, calibrationNote = note, sensorName = accel?.name.orEmpty(),
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        PermissionsGate(LOCATION_PERMISSIONS, stringResource(R.string.gps_rationale), required = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            CollectGnss(onLocation = { fix = it }, withSatellites = false)
            CollectSensors(listOfNotNull(accel), SensorManager.SENSOR_DELAY_GAME) { item ->
                if (item !is SensorEventItem.Sample) return@CollectSensors
                val s = item.sample
                val ax = s.values[0].toDouble(); val ay = s.values[1].toDouble(); val az = s.values[2].toDouble()
                val gx = gravity.first.update(ax, s.timestampNs); val gy = gravity.second.update(ay, s.timestampNs); val gz = gravity.third.update(az, s.timestampNs)
                val v = RoadRoughness.vertical(ax, ay, az, gx, gy, gz)
                window.add(v)
                if (abs(v) >= RoadRoughness.POTHOLE_MS2 && s.timestampNs - lastPothole[0] > 1_500_000_000L) {
                    lastPothole[0] = s.timestampNs; potholes++; potholeFlag[0] = true
                }
                if (windowStart[0] == 0L) windowStart[0] = s.timestampNs
                if (s.timestampNs - windowStart[0] >= 1_000_000_000L) {
                    val r = window.stdDev
                    window.reset(); windowStart[0] = s.timestampNs
                    if (r.isNaN()) return@CollectSensors
                    rms = r; series.add(r.toFloat())
                    val l = fix
                    val moving = l != null && l.hasSpeed() && l.speed > 2f
                    if (moving) {
                        val p = LatLon(l.latitude, l.longitude)
                        if (path.isEmpty() || Geo.distance(path.last().p, p) > 3) path += RoadPoint(p, RoadRoughness.grade(r))
                        if (rec.isRecording) rec.add(doubleArrayOf(p.lat, p.lon, r, l.speed * 3.6, if (potholeFlag[0]) 1.0 else 0.0))
                    }
                    potholeFlag[0] = false
                }
            }
            GpsOffBanner()
            val g = if (rms.isNaN()) null else RoadRoughness.grade(rms)
            BigReadout(
                value = if (rms.isNaN()) "—" else Fmt.num(rms, 2), unit = "m/s²",
                caption = g?.let { stringResource(gradeText(it)) } ?: stringResource(R.string.waiting_sensor),
                badge = stringResource(R.string.road_badge), badgeLevel = BadgeLevel.APPROX,
                valueColor = g?.let { gradeColor(it) } ?: MaterialTheme.colorScheme.onBackground,
            )
            StatRow(
                listOf(
                    stringResource(R.string.road_potholes) to potholes.toString(),
                    stringResource(R.string.gps_speed) to (fix?.takeIf { it.hasSpeed() }?.let { Fmt.num(it.speed * 3.6, 0) } ?: "—"),
                    stringResource(R.string.track_points) to path.size.toString(),
                ),
            )
            SectionCard(stringResource(R.string.road_map)) { RoadCanvas(path.toList()) }
            SectionCard(stringResource(R.string.road_trend)) { LiveChart(listOf(series to LocalInstrument.current.accent), fixedMin = 0f, minSpan = 1f) }
            if ((fix?.speed ?: 0f) < 2f) NoticeBanner(stringResource(R.string.road_moving_only), BadgeLevel.NEUTRAL)
            Text(stringResource(R.string.road_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoadCanvas(points: List<RoadPoint>) {
    val inst = LocalInstrument.current
    val colors = RoadRoughness.Grade.entries.associateWith { gradeColor(it) }
    val local = remember(points.size) { Geo.toLocal(points.map { it.p }) }
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.2f)) {
        drawRect(inst.grid, style = Stroke(1.dp.toPx()))
        if (local.size < 2) return@Canvas
        val minX = local.minOf { it.first }; val maxX = local.maxOf { it.first }
        val minY = local.minOf { it.second }; val maxY = local.maxOf { it.second }
        val span = max(max(maxX - minX, maxY - minY), 20.0)
        val pad = 16.dp.toPx()
        val s = (min(size.width, size.height) - 2 * pad) / span
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        fun p(i: Int) = Offset(size.width / 2 + ((local[i].first - cx) * s).toFloat(), size.height / 2 - ((local[i].second - cy) * s).toFloat())
        for (i in 1 until local.size) drawLine(colors.getValue(points[i].grade), p(i - 1), p(i), 5.dp.toPx(), cap = StrokeCap.Round)
    }
}
