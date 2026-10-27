package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.LowPass
import com.eirahoutmoss.nextools.core.measure.WeatherTrend
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordChart
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun tendencyText(t: WeatherTrend.Tendency, falling: Boolean) = when (t) {
    WeatherTrend.Tendency.STEADY -> R.string.wt_steady
    WeatherTrend.Tendency.SLOW -> if (falling) R.string.wt_falling_slow else R.string.wt_rising_slow
    WeatherTrend.Tendency.CHANGING -> if (falling) R.string.wt_falling else R.string.wt_rising
    WeatherTrend.Tendency.QUICK -> if (falling) R.string.wt_falling_quick else R.string.wt_rising_quick
    WeatherTrend.Tendency.VERY_QUICK -> if (falling) R.string.wt_falling_very else R.string.wt_rising_very
}

private fun outlookText(o: WeatherTrend.Outlook) = when (o) {
    WeatherTrend.Outlook.IMPROVING -> R.string.wt_improving
    WeatherTrend.Outlook.STABLE -> R.string.wt_stable
    WeatherTrend.Outlook.WORSENING -> R.string.wt_worsening
    WeatherTrend.Outlook.STORM_POSSIBLE -> R.string.wt_storm
}

/** Hava Eğilimi: son 3 saatin basınç değişimi (WMO eğilim sınıfları) ve basit bozulma uyarısı. */
@Composable
fun WeatherTrendTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val logOn by c.pressureLog.enabled.collectAsStateWithLifecycle()
    val logVersion by c.pressureLog.version.collectAsStateWithLifecycle()
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_PRESSURE) }
    val rec = rememberRecording(ToolId.WEATHER_TREND)
    val filter = remember { LowPass(2.0) }
    var hpa by remember { mutableDoubleStateOf(Double.NaN) }
    // Ekran açıkken dakikada bir nokta (günlük kapalıysa da kısa süreli eğilim görülebilsin)
    val session = remember { mutableStateListOf<Pair<Long, Double>>() }
    val minuteT = remember { Throttle(60_000_000_000L) }

    val log by produceState(emptyList<Pair<Long, Double>>(), logVersion, logOn) {
        value = withContext(Dispatchers.IO) { c.pressureLog.read() }
    }

    ToolScaffold(
        tool = ToolId.WEATHER_TREND, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.wt_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("pressure_hpa", "hPa", 2), DataColumn("change_3h_hpa", "hPa", 2)),
                    headerComments = csvHeader(context, ToolId.WEATHER_TREND, sensor?.name.orEmpty(), note, listOf("one row per minute")),
                    calibrated = false, calibrationNote = note, sensorName = sensor?.name.orEmpty(),
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        if (sensor == null) { NoticeBanner(stringResource(R.string.waiting_sensor), BadgeLevel.ALARM); return@ToolScaffold }
        val now = System.currentTimeMillis()
        val points = (log + session).sortedBy { it.first }
        val r = WeatherTrend.analyze(points, now)
        CollectSensors(listOf(sensor), SensorManager.SENSOR_DELAY_UI) { item ->
            if (item !is SensorEventItem.Sample) return@CollectSensors
            val p = filter.update(item.sample.values[0].toDouble(), item.sample.timestampNs)
            hpa = p
            if (minuteT.ready(item.sample.timestampNs)) {
                session += System.currentTimeMillis() to p
                if (session.size > 720) session.removeAt(0)
                if (rec.isRecording) rec.add(doubleArrayOf(p, WeatherTrend.analyze((log + session).sortedBy { it.first }, System.currentTimeMillis())?.change3hHpa ?: Double.NaN))
            }
        }
        val inst = LocalInstrument.current
        BigReadout(
            value = r?.let { stringResource(outlookText(it.outlook)) } ?: "—", unit = "",
            caption = r?.let { stringResource(tendencyText(it.tendency, it.change3hHpa < 0)) } ?: stringResource(R.string.wt_need_data),
            badge = stringResource(R.string.wt_badge), badgeLevel = BadgeLevel.NEUTRAL,
            valueColor = when (r?.outlook) {
                WeatherTrend.Outlook.STORM_POSSIBLE -> inst.alarm
                WeatherTrend.Outlook.WORSENING -> inst.warn
                WeatherTrend.Outlook.IMPROVING -> inst.good
                else -> MaterialTheme.colorScheme.onBackground
            },
        )
        StatRow(
            listOf(
                stringResource(R.string.wt_change) to (r?.let { Fmt.signed(it.change3hHpa, 1) + " hPa" } ?: "—"),
                stringResource(R.string.wt_now) to (if (hpa.isNaN()) "—" else Fmt.num(hpa, 1) + " hPa"),
                stringResource(R.string.wt_span) to (r?.let { Fmt.num(it.spanH, 1) + " " + stringResource(R.string.unit_hour_short) } ?: "—"),
            ),
        )
        SectionCard(stringResource(R.string.baro_trend_title)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.baro_log_switch), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(logOn, { c.pressureLog.setEnabled(it) })
            }
            if (!logOn) Text(stringResource(R.string.wt_log_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val recent = points.filter { it.first >= now - 12 * 3_600_000L }
            if (recent.size >= 2) {
                val xs = DoubleArray(recent.size) { (recent[it].first - now) / 3_600_000.0 }
                val ys = DoubleArray(recent.size) { recent[it].second }
                RecordChart(listOf(Triple(xs, ys, inst.accent)), xSuffix = " " + stringResource(R.string.unit_hour_short))
            }
        }
        SectionCard(stringResource(R.string.wt_rules_title)) {
            fun n(v: Double) = Fmt.num(v, 1)
            KeyValue("< ${n(0.1)} hPa", stringResource(R.string.wt_steady))
            KeyValue("${n(0.1)}–${n(1.5)} hPa", stringResource(R.string.wt_rule_slow))
            KeyValue("${n(1.6)}–${n(3.5)} hPa", stringResource(R.string.wt_rule_changing))
            KeyValue("${n(3.6)}–${n(6.0)} hPa", stringResource(R.string.wt_rule_quick))
            KeyValue("> ${n(6.0)} hPa", stringResource(R.string.wt_rule_very))
        }
        Text(stringResource(R.string.wt_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
