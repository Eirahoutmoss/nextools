package com.eirahoutmoss.nextools.ui.tools

import android.content.Intent
import android.location.GnssStatus
import android.location.Location
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.GnssStream
import com.eirahoutmoss.nextools.sensors.Satellite
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.StatusDot
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Takım yıldızı adı ve rengi (adlar özel isim; dile bağlı değil). */
fun constellation(type: Int): Pair<String, Color> = when (type) {
    GnssStatus.CONSTELLATION_GPS -> "GPS" to Color(0xFF22D3EE)
    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS" to Color(0xFFF87171)
    GnssStatus.CONSTELLATION_GALILEO -> "Galileo" to Color(0xFF60A5FA)
    GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou" to Color(0xFFFBBF24)
    GnssStatus.CONSTELLATION_QZSS -> "QZSS" to Color(0xFFA78BFA)
    GnssStatus.CONSTELLATION_SBAS -> "SBAS" to Color(0xFF9CA3AF)
    GnssStatus.CONSTELLATION_IRNSS -> "NavIC" to Color(0xFF4ADE80)
    else -> "?" to Color(0xFF6B7280)
}

/** L5 / E5a / B2a bandı (~1176 MHz): çift frekanslı alıcı işareti. */
fun Satellite.isL5(): Boolean = carrierHz?.let { abs(it - 1_176_450_000f) < 5_000_000f } == true

/** Konum + uydu akışlarını ekran görünürken toplar. */
@Composable
fun CollectGnss(onLocation: (Location) -> Unit, onSatellites: (List<Satellite>) -> Unit = {}, withSatellites: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val loc = androidx.compose.runtime.rememberUpdatedState(onLocation)
    val sats = androidx.compose.runtime.rememberUpdatedState(onSatellites)
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coroutineScope {
                launch { GnssStream.locations(context).collect { loc.value(it) } }
                if (withSatellites) launch { GnssStream.satellites(context).collect { sats.value(it) } }
            }
        }
    }
}

@Composable
fun GpsOffBanner() {
    val context = LocalContext.current
    if (!GnssStream.isGpsEnabled(context)) {
        NoticeBanner(stringResource(R.string.gps_off), BadgeLevel.ALARM)
        OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }) {
            Text(stringResource(R.string.gps_open_settings))
        }
    }
}

/** GPS Durumu: uydular (GPS, GLONASS, Galileo, BeiDou…), sinyal gücü, gökyüzü haritası, konum. */
@Composable
fun GpsStatusTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.GPS_STATUS)
    var sats by remember { mutableStateOf<List<Satellite>>(emptyList()) }
    var fix by remember { mutableStateOf<Location?>(null) }
    var frozen by remember { mutableStateOf(false) }

    ToolScaffold(
        tool = ToolId.GPS_STATUS, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.gps_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("lat", "°", 7), DataColumn("lon", "°", 7), DataColumn("accuracy_m", "m", 1),
                        DataColumn("sats_used", "", 0), DataColumn("sats_visible", "", 0), DataColumn("mean_cn0", "dB-Hz", 1)),
                    headerComments = csvHeader(context, ToolId.GPS_STATUS, "GNSS", note),
                    calibrated = false, calibrationNote = note, sensorName = "GNSS",
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen }, onCalibrate = null,
    ) {
        PermissionsGate(LOCATION_PERMISSIONS, stringResource(R.string.gps_rationale), required = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            CollectGnss(
                onLocation = { l ->
                    if (!frozen) fix = l
                    if (rec.isRecording) {
                        val used = sats.count { it.usedInFix }
                        rec.add(doubleArrayOf(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy.toDouble() else Double.NaN,
                            used.toDouble(), sats.size.toDouble(), sats.filter { it.cn0 > 0 }.map { it.cn0.toDouble() }.average()))
                    }
                },
                onSatellites = { if (!frozen) sats = it },
            )
            GpsOffBanner()
            val used = sats.count { it.usedInFix }
            val l = fix
            BigReadout(
                value = "$used / ${sats.size}", unit = "",
                caption = stringResource(R.string.gps_used_visible),
                badge = l?.let { if (it.hasAccuracy()) stringResource(R.string.gps_accuracy, Fmt.num(it.accuracy.toDouble(), 0)) else null } ?: stringResource(R.string.gps_no_fix),
                badgeLevel = when {
                    l == null -> BadgeLevel.NEUTRAL
                    l.accuracy <= 5 -> BadgeLevel.GOOD
                    else -> BadgeLevel.APPROX
                },
            )
            if (sats.any { it.isL5() }) NoticeBanner(stringResource(R.string.gps_dual_freq), BadgeLevel.GOOD)
            SkyPlot(sats)
            Legend(sats)
            SectionCard(stringResource(R.string.gps_signal)) { Cn0Bars(sats) }
            l?.let {
                SectionCard(stringResource(R.string.gps_position)) {
                    KeyValue(stringResource(R.string.gps_lat_lon), "%.6f, %.6f".format(java.util.Locale.ROOT, it.latitude, it.longitude))
                    if (it.hasAltitude()) KeyValue(stringResource(R.string.col_altitude), Fmt.num(it.altitude, 1) + " m")
                    if (it.hasSpeed()) KeyValue(stringResource(R.string.gps_speed), Fmt.num(it.speed * 3.6, 1) + " km/h")
                    if (it.hasBearing()) KeyValue(stringResource(R.string.gps_bearing), Fmt.num(it.bearing.toDouble(), 0) + "°")
                    KeyValue(stringResource(R.string.gps_fix_time), Fmt.dateTime(it.time))
                }
            }
            StatRow(
                listOf(
                    stringResource(R.string.gps_mean_cn0) to (sats.filter { it.usedInFix }.takeIf { s -> s.isNotEmpty() }?.let { s -> Fmt.num(s.map { x -> x.cn0.toDouble() }.average(), 1) } ?: "—"),
                    stringResource(R.string.gps_constellations) to sats.map { it.constellation }.distinct().size.toString(),
                ),
            )
        }
    }
}

/** Gökyüzü haritası: merkez başucu (90°), kenar ufuk (0°); kuzey yukarıda. */
@Composable
private fun SkyPlot(sats: List<Satellite>) {
    val inst = LocalInstrument.current
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 10.sp, color = inst.scale)
    Canvas(Modifier.fillMaxWidth(0.85f).aspectRatio(1f).padding(8.dp)) {
        val r = min(size.width, size.height) / 2 - 10.dp.toPx()
        val c = center
        listOf(0f, 30f, 60f).forEach { el -> drawCircle(inst.grid, r * (90 - el) / 90, c, style = Stroke(1.dp.toPx())) }
        drawLine(inst.grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y)); drawLine(inst.grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r))
        listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (t, a) ->
            val lay = measurer.measure(t, label)
            val rad = Math.toRadians(a - 90)
            drawText(lay, topLeft = Offset(c.x + ((r + 6.dp.toPx()) * cos(rad)).toFloat() - lay.size.width / 2, c.y + ((r + 6.dp.toPx()) * sin(rad)).toFloat() - lay.size.height / 2))
        }
        sats.filter { it.elevation > 0 || it.azimuth > 0 }.forEach { s ->
            val rr = r * (90 - s.elevation.coerceIn(0f, 90f)) / 90
            val a = Math.toRadians(s.azimuth - 90.0)
            val p = Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat())
            val col = constellation(s.constellation).second
            if (s.usedInFix) drawCircle(col, 6.dp.toPx(), p) else drawCircle(col, 6.dp.toPx(), p, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun Legend(sats: List<Satellite>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        sats.map { it.constellation }.distinct().sorted().forEach { t ->
            val (name, col) = constellation(t)
            Row { StatusDot(col); Spacer(Modifier.width(4.dp)); Text("$name ${sats.count { it.constellation == t && it.usedInFix }}/${sats.count { it.constellation == t }}", style = MaterialTheme.typography.labelSmall) }
        }
    }
    Text(stringResource(R.string.gps_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Uydu başına sinyal gücü (C/N0) çubukları. */
@Composable
private fun Cn0Bars(sats: List<Satellite>) {
    val inst = LocalInstrument.current
    val list = sats.sortedWith(compareBy({ it.constellation }, { it.svid })).take(40)
    if (list.isEmpty()) { Text(stringResource(R.string.gps_searching)); return }
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val bw = size.width / list.size
        list.forEachIndexed { i, s ->
            val h = (s.cn0 / 50f).coerceIn(0f, 1f) * size.height
            val col = constellation(s.constellation).second
            drawRect(if (s.usedInFix) col else col.copy(alpha = 0.35f), topLeft = Offset(i * bw + bw * 0.15f, size.height - h), size = androidx.compose.ui.geometry.Size(bw * 0.7f, h))
        }
        drawLine(inst.good.copy(alpha = 0.6f), Offset(0f, size.height * (1 - 30f / 50f)), Offset(size.width, size.height * (1 - 30f / 50f)), 1.dp.toPx())
    }
    Text(stringResource(R.string.gps_cn0_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
