package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.LuxMath
import com.eirahoutmoss.nextools.core.measure.RunningStats
import com.eirahoutmoss.nextools.core.units.Units
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.LiveChart
import com.eirahoutmoss.nextools.ui.components.MeterBar
import com.eirahoutmoss.nextools.ui.components.NumberInputDialog
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument

private fun LuxMath.Recommendation.label(): Int = when (this) {
    LuxMath.Recommendation.CORRIDOR -> R.string.lux_rec_corridor
    LuxMath.Recommendation.STAIRS -> R.string.lux_rec_stairs
    LuxMath.Recommendation.LIVING_ROOM -> R.string.lux_rec_living
    LuxMath.Recommendation.CLASSROOM -> R.string.lux_rec_classroom
    LuxMath.Recommendation.READING -> R.string.lux_rec_reading
    LuxMath.Recommendation.OFFICE -> R.string.lux_rec_office
    LuxMath.Recommendation.TECHNICAL_DRAWING -> R.string.lux_rec_drawing
    LuxMath.Recommendation.PRECISION_WORK -> R.string.lux_rec_precision
}

/** Lüksmetre: ortam ışığı sensörü; önerilen değerlerle karşılaştırma; referans ölçerle kalibrasyon. */
@Composable
fun LuxTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_LIGHT) }
    val rec = rememberRecording(ToolId.LUX)
    var raw by remember { mutableDoubleStateOf(Double.NaN) }
    var frozen by remember { mutableStateOf(false) }
    var calibrating by remember { mutableStateOf(false) }
    val stats = remember { RunningStats() }
    val series = remember { RollingSeries(150) }
    val ui = remember { Throttle(150_000_000L) }
    val recT = remember { Throttle(200_000_000L) }
    val factor = cal.luxFactor ?: 1.0
    val units = settings.units

    CollectSensors(listOfNotNull(sensor), SensorManager.SENSOR_DELAY_UI) { item ->
        if (item !is SensorEventItem.Sample) return@CollectSensors
        val s = item.sample
        val lux = s.values[0] * factor
        if (rec.isRecording && recT.ready(s.timestampNs)) rec.add(doubleArrayOf(lux))
        if (!frozen && ui.ready(s.timestampNs)) {
            raw = s.values[0].toDouble()
            stats.add(lux)
            series.add(Units.illuminance(lux, units).toFloat())
        }
    }

    val calibrated = cal.luxFactor != null
    ToolScaffold(
        tool = ToolId.LUX, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = cal.luxFactor?.let { context.getString(R.string.lux_cal_note, Fmt.num(it, 3)) } ?: context.getString(R.string.lux_uncal_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("lux", "lx", 1)),
                    headerComments = csvHeader(context, ToolId.LUX, sensor?.name.orEmpty(), note),
                    calibrated = calibrated, calibrationNote = note, sensorName = sensor?.name.orEmpty(),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen },
        onCalibrate = { calibrating = true },
        infoExtra = { SensorInfo(sensor) },
    ) {
        if (raw.isNaN()) { Text(stringResource(R.string.waiting_sensor)); return@ToolScaffold }
        val lux = raw * factor
        BigReadout(
            value = Fmt.num(Units.illuminance(lux, units), if (lux < 10) 1 else 0), unit = Units.illuminanceSymbol(units),
            badge = stringResource(if (calibrated) R.string.badge_calibrated else R.string.lux_badge_uncal),
            badgeLevel = if (calibrated) BadgeLevel.GOOD else BadgeLevel.APPROX,
        )
        StatRow(
            listOf(
                stringResource(R.string.stat_min) to Fmt.num(Units.illuminance(stats.min, units), 0),
                stringResource(R.string.stat_avg) to Fmt.num(Units.illuminance(stats.average, units), 0),
                stringResource(R.string.stat_max) to Fmt.num(Units.illuminance(stats.max, units), 0),
            ),
        )
        SectionCard(null) { LiveChart(listOf(series to LocalInstrument.current.accent), minSpan = 10f, fixedMin = 0f) }
        SectionCard(stringResource(R.string.lux_compare_title)) {
            val inst = LocalInstrument.current
            LuxMath.Recommendation.entries.forEach { r ->
                val verdict = LuxMath.verdict(lux, r)
                val color = when (verdict) { LuxMath.Verdict.OK -> inst.good; LuxMath.Verdict.LOW -> inst.warn; LuxMath.Verdict.HIGH -> inst.accent }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(r.label()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            Fmt.num(Units.illuminance(r.lux.toDouble(), units), 0) + " " + Units.illuminanceSymbol(units) + " · " + stringResource(
                                when (verdict) { LuxMath.Verdict.OK -> R.string.lux_ok; LuxMath.Verdict.LOW -> R.string.lux_low; LuxMath.Verdict.HIGH -> R.string.lux_high },
                            ),
                            style = MaterialTheme.typography.labelMedium, color = color,
                        )
                    }
                    MeterBar(lux, r.lux.toDouble(), 3000.0, color)
                    Spacer(Modifier.height(4.dp))
                }
            }
            Text(stringResource(R.string.lux_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { stats.reset(); series.clear() }) { Text(stringResource(R.string.action_reset_stats)) }
        Text(stringResource(R.string.lux_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    }

    if (calibrating) {
        NumberInputDialog(
            title = stringResource(R.string.lux_cal_title),
            message = stringResource(R.string.lux_cal_message, Fmt.num(raw * factor, 0)),
            label = stringResource(R.string.lux_cal_label),
            initial = null,
            onConfirm = { v ->
                val f = LuxMath.calibrationFactor(v, raw)
                if (f == null) context.getString(R.string.lux_cal_error) else { c.calibrations.setLuxFactor(f); calibrating = false; null }
            },
            onDismiss = { calibrating = false },
            extra = {
                if (calibrated) OutlinedButton(onClick = { c.calibrations.reset(ToolId.LUX); calibrating = false }) { Text(stringResource(R.string.cal_reset_title)) }
            },
        )
    }
}
