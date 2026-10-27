package com.eirahoutmoss.nextools.ui.tools

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.radio.BleMath
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingController
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import kotlinx.coroutines.delay

data class BleDevice(val address: String, val name: String?, val rssi: Int, val txPower: Int?, val company: String?, val seenAt: Long, val connectable: Boolean)

private fun blePermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(android.Manifest.permission.BLUETOOTH_SCAN)
    else listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)

/** Bluetooth Tarayıcı: yakındaki BLE cihazları, sinyal gücü, kaba uzaklık, üretici. */
@Composable
fun BleScannerTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.BLE_SCANNER)
    ToolScaffold(
        tool = ToolId.BLE_SCANNER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.ble_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("device_count", "", 0), DataColumn("strongest_rssi", "dBm", 0)),
                    headerComments = csvHeader(context, ToolId.BLE_SCANNER, "Bluetooth LE", note, listOf("addresses are not stored")),
                    calibrated = false, calibrationNote = note, sensorName = "Bluetooth LE",
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        PermissionsGate(blePermissions(), stringResource(R.string.ble_rationale), required = blePermissions().take(1)) { BleContent(rec) }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun BleContent(rec: RecordingController) {
    val context = LocalContext.current
    val adapter = remember { (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }
    val devices = remember { mutableStateMapOf<String, BleDevice>() }
    var error by remember { mutableStateOf<Int?>(null) }
    val started = rememberIsStarted()

    DisposableEffect(started, adapter?.isEnabled) {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                val rec2 = r.scanRecord
                val company = rec2?.manufacturerSpecificData?.let { m -> if (m.size() > 0) BleMath.COMPANIES[m.keyAt(0)] ?: "0x%04X".format(m.keyAt(0)) else null }
                val tx = rec2?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
                devices[r.device.address] = BleDevice(
                    r.device.address, rec2?.deviceName, r.rssi, tx, company, SystemClock.elapsedRealtime(),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) r.isConnectable else true,
                )
            }
            override fun onScanFailed(errorCode: Int) { error = errorCode }
        }
        if (started && scanner != null) {
            runCatching { scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb) }
                .onFailure { error = -1 }
        }
        onDispose { runCatching { scanner?.stopScan(cb) } }
    }
    // 30 sn görülmeyen cihazlar listeden düşer
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val now = SystemClock.elapsedRealtime()
            devices.keys.filter { now - (devices[it]?.seenAt ?: 0) > 30_000 }.forEach { devices.remove(it) }
            if (rec.isRecording) rec.add(doubleArrayOf(devices.size.toDouble(), devices.values.maxOfOrNull { it.rssi }?.toDouble() ?: Double.NaN))
        }
    }

    if (adapter == null) { NoticeBanner(stringResource(R.string.passive_generic), BadgeLevel.ALARM); return }
    if (!adapter.isEnabled) NoticeBanner(stringResource(R.string.ble_off), BadgeLevel.ALARM)
    error?.let { NoticeBanner(stringResource(R.string.ble_error, it), BadgeLevel.ALARM) }
    val list = devices.values.sortedByDescending { it.rssi }
    BigReadout(
        value = list.size.toString(), unit = stringResource(R.string.ble_devices_unit),
        caption = stringResource(R.string.ble_caption), badge = stringResource(R.string.ble_badge), badgeLevel = BadgeLevel.NEUTRAL,
    )
    SectionCard(null) {
        if (list.isEmpty()) Text(stringResource(R.string.ble_searching), style = MaterialTheme.typography.bodySmall)
        list.take(60).forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name ?: d.company ?: stringResource(R.string.ble_unnamed), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(d.address, d.company?.takeIf { d.name != null }, if (d.connectable) null else stringResource(R.string.ble_beacon)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${d.rssi} dBm", style = MaterialTheme.typography.labelLarge, color = rssiColor(d.rssi))
                    Text("≈ " + Fmt.num(BleMath.distanceM(d.rssi, d.txPower), 1) + " m", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text(stringResource(R.string.ble_distance_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
