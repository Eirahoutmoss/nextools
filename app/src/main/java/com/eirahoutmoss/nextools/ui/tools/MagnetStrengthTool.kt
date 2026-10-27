package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.MagnetPeak
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.ArcGauge
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

/** Mıknatıs Gücü: dünya alanı sıfırlanır, mıknatıs yaklaştıkça alan artışı ve en yüksek değer (µT / mT). */
@Composable
fun MagnetStrengthTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    val maxRange = remember { sensor?.maximumRange?.toDouble() ?: 0.0 }
    val rec = rememberRecording(ToolId.MAGNET_STRENGTH)
    val peak = remember { MagnetPeak() }
    val last = remember { DoubleArray(3) }
    var current by remember { mutableDoubleStateOf(0.0) }
    var top by remember { mutableDoubleStateOf(0.0) }
    var saturated by remember { mutableStateOf(false) }
    val series = remember { RollingSeries(200) }
    val ui = remember { Throttle(50_000_000L) }

    fun fmtField(ut: Double) = if (ut >= 1000) Fmt.num(ut / 1000, 2) + " mT" else Fmt.num(ut, 0) + " µT"

    ToolScaffold(
        tool = ToolId.MAGNET_STRENGTH, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.mag_peak_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("field_ut", "µT", 1), DataColumn("peak_ut", "µT", 1)),
                    headerComments = csvHeader(context, ToolId.MAGNET_STRENGTH, sensor?.name.orEmpty(), note, listOf("field_ut = change from tared background")),
                    calibrated = false, calibrationNote = note, sensorName = sensor?.name.orEmpty(),
                    summarize = { _, _ -> listOf(SummaryItem("peak", "peak_ut", peak.peak, "µT", 1)) },
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        if (sensor == null) { NoticeBanner(stringResource(R.string.waiting_sensor), BadgeLevel.ALARM); return@ToolScaffold }
        CollectSensors(listOf(sensor), SensorManager.SENSOR_DELAY_GAME) { item ->
            if (item !is SensorEventItem.Sample) return@CollectSensors
            val v = item.sample.values
            last[0] = v[0].toDouble(); last[1] = v[1].toDouble(); last[2] = v[2].toDouble()
            if (!peak.tared) peak.tare(last[0], last[1], last[2])
            val c = peak.update(last[0], last[1], last[2], maxRange)
            if (rec.isRecording) rec.add(doubleArrayOf(c, peak.peak))
            if (ui.ready(item.sample.timestampNs)) { current = c; top = peak.peak; saturated = peak.saturated; series.add(c.toFloat()) }
        }
        BigReadout(
            value = if (top >= 1000) Fmt.num(top / 1000, 2) else Fmt.num(top, 0), unit = if (top >= 1000) "mT" else "µT",
            caption = stringResource(R.string.mag_peak_caption),
            badge = stringResource(R.string.mag_peak_badge), badgeLevel = BadgeLevel.APPROX,
        )
        ArcGauge(current, 0.0, maxOf(maxRange, 100.0), warnFrom = maxRange * 0.8, alarmFrom = maxRange * 0.98, majorStep = if (maxRange > 2000) 1000.0 else 500.0)
        StatRow(
            listOf(
                stringResource(R.string.mag_peak_now) to fmtField(current),
                stringResource(R.string.mag_peak_limit) to (if (maxRange > 0) fmtField(maxRange) else "—"),
            ),
        )
        if (saturated) NoticeBanner(stringResource(R.string.mag_peak_saturated), BadgeLevel.ALARM)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { peak.tare(last[0], last[1], last[2]); top = 0.0; saturated = false; series.clear() }) { Text(stringResource(R.string.mag_peak_tare)) }
            OutlinedButton(onClick = { peak.resetPeak(); top = 0.0; saturated = false }) { Text(stringResource(R.string.mag_peak_reset)) }
        }
        SectionCard(stringResource(R.string.mag_peak_trend)) { LiveChart(listOf(series to LocalInstrument.current.accent), fixedMin = 0f, minSpan = 50f) }
        Text(stringResource(R.string.mag_peak_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
