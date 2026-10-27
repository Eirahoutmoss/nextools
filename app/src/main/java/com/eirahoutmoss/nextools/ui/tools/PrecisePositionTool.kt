package com.eirahoutmoss.nextools.ui.tools

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.geo.Geo
import com.eirahoutmoss.nextools.core.geo.LatLon
import com.eirahoutmoss.nextools.core.measure.PositionAverager
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.sensors.Satellite
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeepScreenOn
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlin.math.max

private const val MAX_ACCURACY_M = 25.0

/** Hassas Konum: aynı noktada beklerken konumların ağırlıklı ortalaması ve %95 dağılım elipsi. */
@Composable
fun PrecisePositionTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.PRECISE_POSITION)
    val clipboard = LocalClipboardManager.current
    val avg = remember { PositionAverager() }
    var running by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var fix by remember { mutableStateOf<Location?>(null) }
    var sats by remember { mutableStateOf<List<Satellite>>(emptyList()) }
    var skipped by remember { mutableIntStateOf(0) }
    val points = remember { mutableStateListOf<LatLon>() }

    ToolScaffold(
        tool = ToolId.PRECISE_POSITION, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.pp_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("lat", "°", 8), DataColumn("lon", "°", 8), DataColumn("accuracy_m", "m", 1), DataColumn("altitude_m", "m", 1)),
                    headerComments = csvHeader(context, ToolId.PRECISE_POSITION, "GNSS", note),
                    calibrated = false, calibrationNote = note, sensorName = "GNSS",
                    summarize = { _, _ ->
                        val r = avg.result()
                        listOfNotNull(
                            r?.let { SummaryItem("avg", "lat", it.lat, "°", 8) },
                            r?.let { SummaryItem("avg", "lon", it.lon, "°", 8) },
                            r?.meanUncertaintyM?.takeUnless { it.isNaN() }?.let { SummaryItem("uncertainty", "accuracy_m", it, "m", 2) },
                            r?.let { SummaryItem("count", "lat", it.count.toDouble(), "", 0) },
                        )
                    },
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        KeepScreenOn(running)
        PermissionsGate(LOCATION_PERMISSIONS, stringResource(R.string.gps_rationale), required = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            CollectGnss(
                onLocation = { l ->
                    fix = l
                    if (!running) return@CollectGnss
                    val acc = if (l.hasAccuracy()) l.accuracy.toDouble() else Double.NaN
                    if (acc.isNaN() || acc > MAX_ACCURACY_M) { skipped++; return@CollectGnss }
                    avg.add(l.latitude, l.longitude, if (l.hasAltitude()) l.altitude else null, acc, l.elapsedRealtimeNanos / 1e9)
                    points += LatLon(l.latitude, l.longitude); if (points.size > 1200) points.removeAt(0)
                    if (rec.isRecording) rec.add(doubleArrayOf(l.latitude, l.longitude, acc, if (l.hasAltitude()) l.altitude else Double.NaN))
                    version++
                },
                onSatellites = { sats = it },
            )
            GpsOffBanner()
            val r = remember(version) { avg.result() }
            val e95 = r?.scatter?.scaled()
            BigReadout(
                value = r?.meanUncertaintyM?.takeUnless { it.isNaN() }?.let { "±" + Fmt.num(it, if (it < 10) 1 else 0) } ?: "—", unit = "m",
                caption = if (r == null) stringResource(if (running) R.string.pp_collecting else R.string.pp_caption) else stringResource(R.string.pp_mean_unc),
                badge = stringResource(R.string.pp_badge), badgeLevel = BadgeLevel.APPROX,
            )
            val dual = sats.any { it.usedInFix && it.isL5() }
            StatRow(
                listOf(
                    stringResource(R.string.pp_count) to (r?.count ?: 0).toString(),
                    stringResource(R.string.pp_duration) to Fmt.duration(((r?.durationS ?: 0.0) * 1000).toLong()),
                    stringResource(R.string.pp_dual) to stringResource(if (dual) R.string.yes else R.string.no),
                ),
            )
            if (!running) {
                Button(onClick = { avg.reset(); points.clear(); skipped = 0; running = true; version++ }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (r == null) R.string.pp_start else R.string.pp_restart))
                }
            } else {
                OutlinedButton(onClick = { running = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pp_stop)) }
            }
            if (r != null) {
                SectionCard(stringResource(R.string.pp_result)) {
                    KeyValue(stringResource(R.string.col_lat), Fmt.num(r.lat, 8) + "°")
                    KeyValue(stringResource(R.string.col_lon), Fmt.num(r.lon, 8) + "°")
                    r.altitudeM?.let { KeyValue(stringResource(R.string.col_altitude), Fmt.num(it, 1) + " m" + (r.altitudeSdM?.let { sd -> " (σ " + Fmt.num(sd, 1) + ")" } ?: "")) }
                    e95?.let { KeyValue(stringResource(R.string.pp_ellipse), Fmt.num(it.majorM, 1) + " × " + Fmt.num(it.minorM, 1) + " m · " + Fmt.num(it.orientationDeg, 0) + "°") }
                    if (skipped > 0) KeyValue(stringResource(R.string.pp_skipped), skipped.toString())
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(String.format(java.util.Locale.ROOT, "%.8f, %.8f", r.lat, r.lon))) }) {
                        Text(stringResource(R.string.action_copy))
                    }
                }
                SectionCard(stringResource(R.string.pp_scatter)) { ScatterPlot(points.toList(), LatLon(r.lat, r.lon), e95) }
            }
            if (!dual && sats.isNotEmpty()) NoticeBanner(stringResource(R.string.pp_single_freq), BadgeLevel.NEUTRAL)
            Text(stringResource(R.string.pp_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScatterPlot(points: List<LatLon>, mean: LatLon, e95: PositionAverager.Ellipse?) {
    val inst = LocalInstrument.current
    val local = remember(points.size) { Geo.toLocal(listOf(mean) + points) }
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val c = center
        val extent = max(5.0, max(local.drop(1).maxOfOrNull { kotlin.math.abs(it.first - local[0].first) } ?: 0.0,
            max(local.drop(1).maxOfOrNull { kotlin.math.abs(it.second - local[0].second) } ?: 0.0, e95?.majorM ?: 0.0)))
        val k = (size.minDimension / 2 * 0.9f) / extent.toFloat()
        // 1 m'lik ızgara halkaları
        val ring = when { extent > 40 -> 10.0; extent > 15 -> 5.0; else -> 1.0 }
        var rr = ring
        while (rr <= extent) { drawCircle(inst.grid, (rr * k).toFloat(), c, style = Stroke(1f)); rr += ring }
        local.drop(1).forEach { (x, y) ->
            drawCircle(inst.accent.copy(alpha = 0.45f), 2.5.dp.toPx(), Offset(c.x + ((x - local[0].first) * k).toFloat(), c.y - ((y - local[0].second) * k).toFloat()))
        }
        e95?.let { e ->
            val w = (e.minorM * 2 * k).toFloat(); val h = (e.majorM * 2 * k).toFloat()
            rotate(e.orientationDeg.toFloat(), c) {
                drawOval(inst.warn, Offset(c.x - w / 2, c.y - h / 2), Size(w, h), style = Stroke(2.dp.toPx()))
            }
        }
        drawCircle(inst.alarm, 4.dp.toPx(), c)
    }
}
