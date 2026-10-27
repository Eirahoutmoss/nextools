package com.eirahoutmoss.nextools.ui.tools

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.RangeFilter
import com.eirahoutmoss.nextools.records.Column as DataColumn
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class RttPeer(val bssid: String, val ssid: String, val freq: Int) {
    val filter = RangeFilter(10)
    var rssi = 0
    var ok = 0
    var tried = 0
    var lastStatus = -1
}

/** Wi-Fi Mesafe (RTT): 802.11mc destekleyen erişim noktalarına uçuş süresinden mesafe. */
@Composable
fun WifiRttTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val rec = rememberRecording(ToolId.WIFI_RTT_RANGE)
    val context = LocalContext.current
    ToolScaffold(
        tool = ToolId.WIFI_RTT_RANGE, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.rtt_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("distance_m", "m", 2), DataColumn("sd_m", "m", 2), DataColumn("rssi_dbm", "dBm", 0)),
                    headerComments = csvHeader(context, ToolId.WIFI_RTT_RANGE, "Wi-Fi RTT (IEEE 802.11mc)", note),
                    calibrated = false, calibrationNote = note, sensorName = "Wi-Fi RTT",
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { NoticeBanner(stringResource(R.string.rtt_old_android), BadgeLevel.ALARM); return@ToolScaffold }
        PermissionsGate(wifiPermissions(), stringResource(R.string.wifi_rationale), required = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            RttContent(rec)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@SuppressLint("MissingPermission")
private suspend fun rangeOnce(rtt: WifiRttManager, aps: List<ScanResult>, executor: java.util.concurrent.Executor): List<RangingResult>? =
    suspendCancellableCoroutine { cont ->
        val req = RangingRequest.Builder().apply { aps.take(RangingRequest.getMaxPeers()).forEach { addAccessPoint(it) } }.build()
        runCatching {
            rtt.startRanging(req, executor, object : RangingResultCallback() {
                override fun onRangingResults(results: List<RangingResult>) { if (cont.isActive) cont.resume(results) }
                override fun onRangingFailure(code: Int) { if (cont.isActive) cont.resume(null) }
            })
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun RttContent(rec: com.eirahoutmoss.nextools.ui.components.RecordingController) {
    val context = LocalContext.current
    val wm = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val rtt = remember { context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as? WifiRttManager }
    val started = rememberIsStarted()
    var responders by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }
    val peers = remember { HashMap<String, RttPeer>() }
    var tick by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val series = remember { RollingSeries(120) }
    var rttAvailable by remember { mutableStateOf(rtt?.isAvailable == true) }

    fun read() {
        responders = runCatching { wm.scanResults.filter { it.is80211mcResponder } }.getOrDefault(emptyList()).sortedByDescending { it.level }
        scanned = true
    }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == WifiRttManager.ACTION_WIFI_RTT_STATE_CHANGED) rttAvailable = rtt?.isAvailable == true else read()
            }
        }
        val f = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION).apply { addAction(WifiRttManager.ACTION_WIFI_RTT_STATE_CHANGED) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else context.registerReceiver(r, f)
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }
    LaunchedEffect(started) { while (started) { @Suppress("DEPRECATION") runCatching { wm.startScan() }; read(); delay(30_000) } }
    LaunchedEffect(started, responders, rttAvailable) {
        if (rtt == null || !rttAvailable) return@LaunchedEffect
        while (started && responders.isNotEmpty()) {
            val sel = selected
            val targets = if (sel != null) responders.filter { it.BSSID == sel }.ifEmpty { responders } else responders
            val res = rangeOnce(rtt, targets, context.mainExecutor)
            res?.forEach { r ->
                val bssid = r.macAddress?.toString() ?: return@forEach
                val sr = targets.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }
                @Suppress("DEPRECATION")
                val p = peers.getOrPut(bssid.lowercase()) { RttPeer(bssid.lowercase(), sr?.SSID.orEmpty(), sr?.frequency ?: 0) }
                p.tried++; p.lastStatus = r.status
                if (r.status == RangingResult.STATUS_SUCCESS) {
                    p.ok++
                    p.filter.add(r.distanceMm / 1000.0)
                    p.rssi = r.rssi
                    if (bssid.equals(sel, ignoreCase = true) || (sel == null && p == peers.values.maxByOrNull { it.rssi })) {
                        series.add((r.distanceMm / 1000f))
                        if (rec.isRecording) rec.add(doubleArrayOf(r.distanceMm / 1000.0, r.distanceStdDevMm / 1000.0, r.rssi.toDouble()))
                    }
                }
            }
            tick++
            delay(if (sel != null) 250 else 1000)
        }
    }

    @Suppress("UNUSED_VARIABLE") val t = tick
    val focus = selected?.let { peers[it.lowercase()] } ?: peers.values.filter { it.ok > 0 }.maxByOrNull { it.rssi }
    BigReadout(
        value = focus?.filter?.median?.let { Fmt.num(it, if (it < 10) 2 else 1) } ?: "—", unit = "m",
        caption = focus?.let { it.ssid.ifBlank { it.bssid } } ?: stringResource(R.string.rtt_caption),
        badge = stringResource(R.string.rtt_badge), badgeLevel = BadgeLevel.APPROX,
    )
    focus?.let { p ->
        StatRow(
            listOf(
                stringResource(R.string.rtt_spread) to (p.filter.sd?.let { "±" + Fmt.num(it, 2) + " m" } ?: "—"),
                stringResource(R.string.rtt_success) to "${p.ok}/${p.tried}",
                "RSSI" to "${p.rssi} dBm",
            ),
        )
        SectionCard(stringResource(R.string.rtt_trend)) { LiveChart(listOf(series to LocalInstrument.current.accent), fixedMin = 0f, minSpan = 2f) }
    }
    when {
        rtt == null || !rttAvailable -> NoticeBanner(stringResource(R.string.rtt_unavailable), BadgeLevel.ALARM)
        scanned && responders.isEmpty() -> NoticeBanner(stringResource(R.string.rtt_no_responders))
    }
    if (responders.isNotEmpty()) {
        SectionCard(stringResource(R.string.rtt_list, responders.size)) {
            responders.forEach { sr ->
                val p = peers[sr.BSSID.lowercase()]
                @Suppress("DEPRECATION") val name = sr.SSID.orEmpty().ifBlank { sr.BSSID }
                val isSel = selected.equals(sr.BSSID, ignoreCase = true)
                Row(
                    Modifier.fillMaxWidth().clickable { selected = if (isSel) null else sr.BSSID; series.clear() }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text((if (isSel) "● " else "") + name, style = MaterialTheme.typography.bodyMedium)
                        Text("${sr.BSSID} · ${sr.frequency} MHz · ${sr.level} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(p?.filter?.median?.let { Fmt.num(it, 2) + " m" } ?: "—", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    Text(stringResource(R.string.rtt_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
