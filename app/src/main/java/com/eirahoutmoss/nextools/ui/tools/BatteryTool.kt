package com.eirahoutmoss.nextools.ui.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.units.Units
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
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
import kotlin.math.abs

data class BatteryState(
    val levelPct: Double, val tempC: Double, val voltageV: Double, val currentMa: Double?,
    val status: Int, val plugged: Int, val health: Int, val technology: String?, val chargeCounterMah: Double?, val timeMs: Long,
)

fun readBattery(context: Context): BatteryState? {
    val i: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val now = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    // Üreticiye göre µA ya da mA, işaret de değişebilir: büyüklüğe göre birim tahmin edilir.
    val currentMa = if (now == Int.MIN_VALUE || now == 0) null else if (abs(now) > 20_000) now / 1000.0 else now.toDouble()
    val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).takeIf { it > 0 && it != Int.MIN_VALUE }?.let { it / 1000.0 }
    return BatteryState(
        levelPct = if (level >= 0) level * 100.0 / scale else Double.NaN,
        tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0,
        voltageV = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000.0,
        currentMa = currentMa,
        status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
        plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0),
        technology = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
        chargeCounterMah = counter,
        timeMs = System.currentTimeMillis(),
    )
}

/** Batarya Durumu: yüzde, sıcaklık, voltaj, anlık akım, şarj hızı grafiği. */
@Composable
fun BatteryTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val rec = rememberRecording(ToolId.BATTERY)
    var b by remember { mutableStateOf<BatteryState?>(null) }
    var first by remember { mutableStateOf<BatteryState?>(null) }
    var frozen by remember { mutableStateOf(false) }
    val current = remember { RollingSeries(300) }
    val temp = remember { RollingSeries(300) }
    val started = rememberIsStarted()

    LaunchedEffect(started) {
        while (started) {
            readBattery(context)?.let { s ->
                if (first == null || first!!.status != s.status) first = s
                if (rec.isRecording) rec.add(doubleArrayOf(s.levelPct, s.tempC, s.voltageV, s.currentMa ?: Double.NaN))
                if (!frozen) { b = s; s.currentMa?.let { current.add(it.toFloat()) }; temp.add(s.tempC.toFloat()) }
            }
            delay(1000)
        }
    }

    ToolScaffold(
        tool = ToolId.BATTERY, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.battery_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("level_pct", "%", 1), DataColumn("temp_c", "°C", 1), DataColumn("voltage_v", "V", 3), DataColumn("current_ma", "mA", 0)),
                    headerComments = csvHeader(context, ToolId.BATTERY, "BatteryManager", note, listOf("current sign and unit depend on the manufacturer")),
                    calibrated = false, calibrationNote = note, sensorName = "BatteryManager",
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen }, onCalibrate = null,
    ) {
        val s = b ?: run { Text(stringResource(R.string.waiting_sensor)); return@ToolScaffold }
        val inst = LocalInstrument.current
        val charging = s.status == BatteryManager.BATTERY_STATUS_CHARGING || s.status == BatteryManager.BATTERY_STATUS_FULL
        BigReadout(
            value = Fmt.num(s.levelPct, 0), unit = "%",
            caption = stringResource(statusText(s.status)) + plugText(s.plugged)?.let { " · " + stringResource(it) }.orEmpty(),
            badge = stringResource(R.string.battery_badge), badgeLevel = BadgeLevel.NEUTRAL,
            valueColor = if (s.levelPct < 15) inst.alarm else if (charging) inst.good else MaterialTheme.colorScheme.onBackground,
        )
        StatRow(
            listOf(
                stringResource(R.string.battery_temp) to Fmt.num(Units.celsiusTo(s.tempC, settings.units), 1) + Units.temperatureSymbol(settings.units),
                stringResource(R.string.battery_voltage) to Fmt.num(s.voltageV, 2) + " V",
                stringResource(R.string.battery_current) to (s.currentMa?.let { Fmt.num(it, 0) + " mA" } ?: "—"),
            ),
        )
        val f = first
        if (f != null && s.timeMs - f.timeMs > 120_000 && !s.levelPct.isNaN()) {
            val perHour = (s.levelPct - f.levelPct) / ((s.timeMs - f.timeMs) / 3_600_000.0)
            KeyValue(stringResource(if (charging) R.string.battery_charge_rate else R.string.battery_drain_rate), Fmt.signed(perHour, 1) + " %/h")
        }
        if (s.tempC >= 45) NoticeBanner(stringResource(R.string.battery_hot), BadgeLevel.ALARM)
        SectionCard(stringResource(R.string.battery_current_chart)) { LiveChart(listOf(current to inst.accent), minSpan = 50f) }
        SectionCard(stringResource(R.string.battery_temp_chart)) { LiveChart(listOf(temp to inst.warn), minSpan = 2f) }
        SectionCard(null) {
            KeyValue(stringResource(R.string.battery_health), stringResource(healthText(s.health)))
            s.technology?.let { KeyValue(stringResource(R.string.battery_tech), it) }
            s.chargeCounterMah?.let { KeyValue(stringResource(R.string.battery_counter), Fmt.num(it, 0) + " mAh") }
        }
        Text(stringResource(R.string.tool_battery_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun statusText(s: Int) = when (s) {
    BatteryManager.BATTERY_STATUS_CHARGING -> R.string.battery_charging
    BatteryManager.BATTERY_STATUS_FULL -> R.string.battery_full
    BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.battery_discharging
    else -> R.string.battery_unknown
}

private fun plugText(p: Int) = when (p) {
    BatteryManager.BATTERY_PLUGGED_AC -> R.string.battery_plug_ac
    BatteryManager.BATTERY_PLUGGED_USB -> R.string.battery_plug_usb
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.battery_plug_wireless
    else -> null
}

private fun healthText(h: Int) = when (h) {
    BatteryManager.BATTERY_HEALTH_GOOD -> R.string.battery_health_good
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.battery_health_overheat
    BatteryManager.BATTERY_HEALTH_DEAD -> R.string.battery_health_dead
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.battery_health_overvoltage
    BatteryManager.BATTERY_HEALTH_COLD -> R.string.battery_health_cold
    else -> R.string.battery_unknown
}
