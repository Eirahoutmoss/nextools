package com.eirahoutmoss.nextools.ui.tools

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.radio.Band
import com.eirahoutmoss.nextools.core.radio.WifiChannels
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatusDot
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.delay

private val PALETTE = listOf(0xFF22D3EE, 0xFFF87171, 0xFF4ADE80, 0xFFFBBF24, 0xFFA78BFA, 0xFF60A5FA, 0xFFF472B6, 0xFF34D399, 0xFFFB923C, 0xFF94A3B8).map { Color(it) }

data class WifiAp(val ssid: String, val bssid: String, val rssi: Int, val freq: Int, val width: Int, val security: String)

@Suppress("DEPRECATION")
private fun ScanResult.toAp(): WifiAp {
    val ssidText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wifiSsid?.toString()?.trim('"').orEmpty() else SSID.orEmpty()
    val width = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) when (channelWidth) {
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40; ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
        else -> 20
    } else 20
    val sec = when {
        capabilities.contains("SAE") -> "WPA3"
        capabilities.contains("WPA2") || capabilities.contains("RSN") -> "WPA2"
        capabilities.contains("WPA") -> "WPA"
        capabilities.contains("WEP") -> "WEP"
        else -> "—"
    }
    return WifiAp(ssidText, BSSID.orEmpty(), level, frequency, width, sec)
}

fun wifiPermissions(): List<String> = buildList {
    add(android.Manifest.permission.ACCESS_FINE_LOCATION); add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
}

/** Wi-Fi Analizörü: ağlar, kanal doluluğu grafiği, en boş kanal önerisi. */
@Composable
fun WifiAnalyzerTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.WIFI_ANALYZER)
    ToolScaffold(
        tool = ToolId.WIFI_ANALYZER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.wifi_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("connected_rssi", "dBm", 0), DataColumn("ap_count", "", 0), DataColumn("strongest_rssi", "dBm", 0)),
                    headerComments = csvHeader(context, ToolId.WIFI_ANALYZER, "Wi-Fi", note),
                    calibrated = false, calibrationNote = note, sensorName = "Wi-Fi",
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        PermissionsGate(wifiPermissions(), stringResource(R.string.wifi_rationale), required = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            WifiContent(rec)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun WifiContent(rec: com.eirahoutmoss.nextools.ui.components.RecordingController) {
    val context = LocalContext.current
    val wm = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    var aps by remember { mutableStateOf<List<WifiAp>>(emptyList()) }
    var lastScan by remember { mutableLongStateOf(0L) }
    var throttled by remember { mutableStateOf(false) }
    var band by rememberSaveable { mutableStateOf(Band.GHZ_2_4) }
    val started = rememberIsStarted()

    fun read() { aps = runCatching { wm.scanResults.map { it.toAp() } }.getOrDefault(emptyList()).sortedByDescending { it.rssi }; lastScan = System.currentTimeMillis() }
    @Suppress("DEPRECATION")
    fun scan() { throttled = !runCatching { wm.startScan() }.getOrDefault(false); read() }

    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { read() } }
        val f = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else context.registerReceiver(r, f)
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }
    // Android 9+ dakikada ~4 taramaya izin verir: 30 sn'de bir dener, arada sonuçları yeniler.
    LaunchedEffect(started) { while (started) { scan(); delay(30_000) } }
    @Suppress("DEPRECATION") val conn = wm.connectionInfo
    val connectedRssi = conn?.takeIf { it.networkId != -1 }?.rssi
    LaunchedEffect(aps, connectedRssi) {
        if (rec.isRecording) rec.add(doubleArrayOf(connectedRssi?.toDouble() ?: Double.NaN, aps.size.toDouble(), aps.maxOfOrNull { it.rssi }?.toDouble() ?: Double.NaN))
    }

    if (!wm.isWifiEnabled) NoticeBanner(stringResource(R.string.wifi_off), BadgeLevel.ALARM)
    @Suppress("DEPRECATION")
    val connSsid = conn?.ssid?.trim('"')?.takeIf { connectedRssi != null && it != "<unknown ssid>" }
    BigReadout(
        value = connectedRssi?.toString() ?: "—", unit = "dBm",
        caption = connSsid?.let { stringResource(R.string.wifi_connected_to, it, conn.frequency) } ?: stringResource(R.string.wifi_not_connected),
        badge = stringResource(R.string.wifi_count, aps.size), badgeLevel = BadgeLevel.NEUTRAL,
    )
    if (throttled) NoticeBanner(stringResource(R.string.wifi_throttled), BadgeLevel.NEUTRAL)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(band == Band.GHZ_2_4, { band = Band.GHZ_2_4 }, label = { Text("2,4 GHz") })
        FilterChip(band == Band.GHZ_5, { band = Band.GHZ_5 }, label = { Text("5 GHz") })
        FilterChip(band == Band.GHZ_6, { band = Band.GHZ_6 }, label = { Text("6 GHz") })
        OutlinedButton(onClick = { scan() }) { Text(stringResource(R.string.wifi_rescan)) }
    }
    val inBand = aps.filter { WifiChannels.band(it.freq) == band }
    val rec2 = WifiChannels.recommend(band, inBand.map { WifiChannels.Ap(it.freq, it.width, it.rssi) })
    rec2?.let { (ch, _) -> NoticeBanner(stringResource(R.string.wifi_best_channel, ch), BadgeLevel.GOOD) }
    SectionCard(stringResource(R.string.wifi_channel_graph)) { ChannelGraph(inBand, band) }
    SectionCard(stringResource(R.string.wifi_networks)) {
        if (inBand.isEmpty()) Text(stringResource(R.string.wifi_none), style = MaterialTheme.typography.bodySmall)
        inBand.forEachIndexed { i, ap ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(PALETTE[i % PALETTE.size])
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ap.ssid.ifBlank { stringResource(R.string.wifi_hidden) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.wifi_ap_line, WifiChannels.channel(ap.freq), ap.width, ap.security, ap.bssid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${ap.rssi} dBm", style = MaterialTheme.typography.labelLarge, color = rssiColor(ap.rssi))
            }
        }
    }
    Text(stringResource(R.string.wifi_last_scan, Fmt.dateTime(lastScan)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun rssiColor(rssi: Int): Color {
    val inst = LocalInstrument.current
    return when { rssi >= -60 -> inst.good; rssi >= -75 -> inst.warn; else -> inst.alarm }
}

/** Her erişim noktası kanal genişliği boyunca bir tümsek: yükseklik = sinyal gücü. */
@Composable
private fun ChannelGraph(aps: List<WifiAp>, band: Band) {
    val inst = LocalInstrument.current
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 9.sp, color = inst.scale)
    val (fMin, fMax, ticks) = when (band) {
        Band.GHZ_2_4 -> Triple(2397, 2487, (1..13).map { it to 2407 + it * 5 })
        Band.GHZ_5 -> Triple(5160, 5900, listOf(36, 52, 100, 116, 132, 149, 165).map { it to 5000 + it * 5 })
        else -> Triple(5940, 7130, listOf(1, 33, 65, 97, 129, 161, 193, 225).map { it to 5950 + it * 5 })
    }
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val bottom = size.height - 14.dp.toPx()
        fun x(f: Int) = (f - fMin).toFloat() / (fMax - fMin) * size.width
        fun y(rssi: Int) = bottom - ((rssi + 100) / 70f).coerceIn(0f, 1f) * bottom
        listOf(-40, -60, -80).forEach { r -> drawLine(inst.grid, Offset(0f, y(r)), Offset(size.width, y(r))) }
        ticks.forEach { (ch, f) ->
            val l = measurer.measure("$ch", label)
            drawText(l, topLeft = Offset(x(f) - l.size.width / 2, bottom + 2.dp.toPx()))
        }
        aps.forEachIndexed { i, ap ->
            val col = PALETTE[i % PALETTE.size]
            val x0 = x(ap.freq - ap.width / 2); val x1 = x(ap.freq + ap.width / 2); val top = y(ap.rssi)
            val p = Path().apply { moveTo(x0, bottom); cubicTo(x0 + (x1 - x0) * 0.15f, top, x1 - (x1 - x0) * 0.15f, top, x1, bottom) }
            drawPath(p, col.copy(alpha = 0.12f)); drawPath(p, col, style = Stroke(1.5.dp.toPx()))
        }
    }
}
