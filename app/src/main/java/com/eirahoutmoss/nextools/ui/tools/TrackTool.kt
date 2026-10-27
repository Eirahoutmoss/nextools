package com.eirahoutmoss.nextools.ui.tools

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.geo.Geo
import com.eirahoutmoss.nextools.core.geo.LatLon
import com.eirahoutmoss.nextools.core.geo.TrackStats
import com.eirahoutmoss.nextools.core.units.Units
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
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
import kotlin.math.max
import kotlin.math.min

/** Yerel düzlemdeki noktaları kutuya sığdırıp çizer (harita altlığı yok; çevrimdışı). */
@Composable
fun ShapeCanvas(points: List<LatLon>, closed: Boolean, modifier: Modifier = Modifier) {
    val inst = LocalInstrument.current
    val local = remember(points.size, points.lastOrNull()) { Geo.toLocal(points) }
    Canvas(modifier.fillMaxWidth().aspectRatio(1.2f)) {
        drawRect(inst.grid, style = Stroke(1.dp.toPx()))
        if (local.isEmpty()) return@Canvas
        val minX = local.minOf { it.first }; val maxX = local.maxOf { it.first }
        val minY = local.minOf { it.second }; val maxY = local.maxOf { it.second }
        val span = max(max(maxX - minX, maxY - minY), 10.0)
        val pad = 16.dp.toPx()
        val s = (min(size.width, size.height) - 2 * pad) / span
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        fun p(x: Double, y: Double) = Offset(size.width / 2 + ((x - cx) * s).toFloat(), size.height / 2 - ((y - cy) * s).toFloat())
        val path = Path()
        local.forEachIndexed { i, (x, y) -> val o = p(x, y); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        if (closed && local.size > 2) path.close()
        if (closed && local.size > 2) drawPath(path, inst.accent.copy(alpha = 0.15f))
        drawPath(path, inst.accent, style = Stroke(2.5.dp.toPx()))
        drawCircle(inst.good, 5.dp.toPx(), p(local.first().first, local.first().second))
        drawCircle(inst.warn, 5.dp.toPx(), p(local.last().first, local.last().second))
    }
}

/** İz Kaydedici: GPS izi, mesafe, hız, tırmanış; Defter'den GPX olarak dışa aktarılır. */
@Composable
fun TrackTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val rec = rememberRecording(ToolId.TRACK)
    val points = remember { mutableStateListOf<LatLon>() }
    var stats by remember { mutableStateOf(TrackStats()) }
    var fix by remember { mutableStateOf<Location?>(null) }
    val ele = remember { RollingSeries(300) }
    val units = settings.units

    ToolScaffold(
        tool = ToolId.TRACK, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            points.clear(); stats = TrackStats(); ele.clear()
            val note = context.getString(R.string.track_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("lat", "°", 7), DataColumn("lon", "°", 7), DataColumn("altitude_m", "m", 1),
                        DataColumn("speed_kmh", "km/h", 1), DataColumn("accuracy_m", "m", 1), DataColumn("distance_m", "m", 1)),
                    headerComments = csvHeader(context, ToolId.TRACK, "GNSS", note),
                    calibrated = false, calibrationNote = note, sensorName = "GNSS",
                    summarize = { st, _ ->
                        listOf(
                            SummaryItem("total", "distance_m", stats.distanceM, "m", 0),
                            SummaryItem("ascent", "altitude_m", stats.ascentM, "m", 0),
                            SummaryItem("descent", "altitude_m", stats.descentM, "m", 0),
                            SummaryItem("max", "speed_kmh", st["speed_kmh"]?.max ?: Double.NaN, "km/h", 1),
                            SummaryItem("avg", "speed_kmh", st["speed_kmh"]?.average ?: Double.NaN, "km/h", 1),
                        )
                    },
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        PermissionsGate(LOCATION_PERMISSIONS, stringResource(R.string.gps_rationale), required = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            CollectGnss(onLocation = { l ->
                fix = l
                if (rec.isRecording) {
                    val p = LatLon(l.latitude, l.longitude)
                    val acc = if (l.hasAccuracy()) l.accuracy.toDouble() else null
                    if (stats.add(p, if (l.hasAltitude()) l.altitude else null, if (l.hasSpeed()) l.speed.toDouble() else null, acc)) {
                        points += p
                        if (l.hasAltitude()) ele.add(Units.length(l.altitude, units).toFloat())
                        rec.add(doubleArrayOf(l.latitude, l.longitude, if (l.hasAltitude()) l.altitude else Double.NaN,
                            if (l.hasSpeed()) l.speed * 3.6 else Double.NaN, acc ?: Double.NaN, stats.distanceM))
                    }
                }
            }, withSatellites = false)
            GpsOffBanner()
            val km = units == com.eirahoutmoss.nextools.core.units.UnitSystem.METRIC
            val dist = if (km) stats.distanceM / 1000 else stats.distanceM / 1609.344
            BigReadout(
                value = Fmt.num(dist, 2), unit = if (km) "km" else "mi",
                caption = stringResource(if (rec.isRecording) R.string.track_recording else R.string.track_press_record),
                badge = fix?.let { if (it.hasAccuracy()) stringResource(R.string.gps_accuracy, Fmt.num(it.accuracy.toDouble(), 0)) else null } ?: stringResource(R.string.gps_searching),
                badgeLevel = if ((fix?.accuracy ?: 99f) <= 10f) BadgeLevel.GOOD else BadgeLevel.APPROX,
            )
            StatRow(
                listOf(
                    stringResource(R.string.gps_speed) to (fix?.takeIf { it.hasSpeed() }?.let { Fmt.num(it.speed * 3.6, 1) } ?: "—"),
                    stringResource(R.string.track_ascent) to Fmt.num(Units.length(stats.ascentM, units), 0) + " " + Units.lengthSymbol(units),
                    stringResource(R.string.track_points) to stats.points.toString(),
                ),
            )
            SectionCard(stringResource(R.string.track_shape)) { ShapeCanvas(points.toList(), closed = false) }
            SectionCard(stringResource(R.string.track_elevation)) { LiveChart(listOf(ele to LocalInstrument.current.accent), minSpan = 5f) }
            NoticeBanner(stringResource(R.string.track_screen_on), BadgeLevel.NEUTRAL)
            Text(stringResource(R.string.track_gpx_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
