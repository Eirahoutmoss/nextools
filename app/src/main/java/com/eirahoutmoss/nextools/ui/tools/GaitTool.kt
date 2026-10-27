package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.GaitAnalyzer
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeepScreenOn
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
import kotlin.math.sqrt

/** Yürüyüş Analizi: adım süresi, ritim, düzenlilik, sağ/sol simetrisi ve gövde salınımı. Tıbbi cihaz değildir. */
@Composable
fun GaitTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val accel = remember { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val gyro = remember { sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val rec = rememberRecording(ToolId.GAIT)
    val gait = remember { GaitAnalyzer() }
    var running by remember { mutableStateOf(false) }
    var steps by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<GaitAnalyzer.Result?>(null) }
    val series = remember { RollingSeries(60) }

    ToolScaffold(
        tool = ToolId.GAIT, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.gait_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("step_s", "s", 3), DataColumn("cadence", "1/min", 1)),
                    headerComments = csvHeader(context, ToolId.GAIT, accel?.name.orEmpty(), note, listOf("one row per step; not a medical device")),
                    calibrated = false, calibrationNote = note, sensorName = accel?.name.orEmpty(),
                    summarize = { _, _ ->
                        val r = gait.result()
                        listOfNotNull(
                            r?.let { SummaryItem("avg", "cadence", it.cadence, "1/min", 0) },
                            r?.let { SummaryItem("variability", "step_s", it.variabilityPct, "%", 1) },
                            r?.let { SummaryItem("asymmetry", "step_s", it.asymmetryPct, "%", 1) },
                        )
                    },
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        KeepScreenOn(running)
        if (accel == null) { NoticeBanner(stringResource(R.string.waiting_sensor), BadgeLevel.ALARM); return@ToolScaffold }
        if (running) {
            CollectSensors(listOfNotNull(accel, gyro), SensorManager.SENSOR_DELAY_GAME) { item ->
                if (item !is SensorEventItem.Sample) return@CollectSensors
                val s = item.sample; val v = s.values
                val m = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
                if (s.type == Sensor.TYPE_GYROSCOPE) { gait.addGyro(m); return@CollectSensors }
                if (gait.add(m, s.timestampNs)) {
                    steps = gait.stepTimesNs.size
                    val t = gait.stepTimesNs
                    if (t.size >= 2) {
                        val dt = (t[t.size - 1] - t[t.size - 2]) / 1e9
                        if (dt < 2) { series.add(dt.toFloat()); if (rec.isRecording) rec.add(doubleArrayOf(dt, 60 / dt)) }
                    }
                    if (steps % 4 == 0) result = gait.result()
                }
            }
        }
        val r = result
        BigReadout(
            value = r?.let { Fmt.num(it.cadence, 0) } ?: "—", unit = stringResource(R.string.gait_unit),
            caption = if (running) stringResource(R.string.gait_walking, steps) else stringResource(R.string.gait_caption),
            badge = stringResource(R.string.pulse_badge), badgeLevel = BadgeLevel.APPROX,
        )
        if (!running) {
            Button(onClick = { gait.reset(); steps = 0; result = null; series.clear(); running = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.gait_start))
            }
        } else {
            OutlinedButton(onClick = { running = false; result = gait.result() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gait_stop)) }
        }
        r?.let {
            StatRow(
                listOf(
                    stringResource(R.string.gait_step_time) to Fmt.num(it.stepTimeS, 2) + " s",
                    stringResource(R.string.gait_variability) to Fmt.num(it.variabilityPct, 1) + " %",
                    stringResource(R.string.gait_asymmetry) to Fmt.num(it.asymmetryPct, 1) + " %",
                ),
            )
            SectionCard(stringResource(R.string.gait_details)) {
                KeyValue(stringResource(R.string.gait_steps), it.steps.toString())
                it.swayDegS?.let { s -> KeyValue(stringResource(R.string.gait_sway), Fmt.num(s, 0) + " °/s") }
                KeyValue(stringResource(R.string.gait_regularity), stringResource(when {
                    it.variabilityPct < 3 -> R.string.gait_reg_very
                    it.variabilityPct < 6 -> R.string.gait_reg_good
                    else -> R.string.gait_reg_irregular
                }))
            }
        }
        if (steps > 1) SectionCard(stringResource(R.string.gait_intervals)) { LiveChart(listOf(series to LocalInstrument.current.accent), minSpan = 0.2f) }
        Text(stringResource(R.string.gait_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
