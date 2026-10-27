package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.LowPass
import com.eirahoutmoss.nextools.core.measure.Tachometer
import com.eirahoutmoss.nextools.records.Column as DataColumn
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

/** Dönüş Ölçer: dönen yüzeye düz konan telefonun z ekseni açısal hızından devir/dakika. */
@Composable
fun TachometerTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val gyro = remember { sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val maxRpm = remember { gyro?.let { Tachometer.maxRpm(it.maximumRange.toDouble()) } ?: 0.0 }
    val rec = rememberRecording(ToolId.TACHOMETER)
    val lp = remember { LowPass(0.3) }
    val flatLp = remember { LowPass(0.5) }
    var rpm by remember { mutableDoubleStateOf(0.0) }
    var signed by remember { mutableDoubleStateOf(0.0) }
    var flat by remember { mutableDoubleStateOf(1.0) }
    var frozen by remember { mutableStateOf(false) }
    val series = remember { RollingSeries(200) }
    val ui = remember { Throttle(80_000_000L) }
    val recT = remember { Throttle(100_000_000L) }

    ToolScaffold(
        tool = ToolId.TACHOMETER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.tach_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("rpm", "1/min", 1)),
                    headerComments = csvHeader(context, ToolId.TACHOMETER, gyro?.name.orEmpty(), note),
                    calibrated = false, calibrationNote = note, sensorName = gyro?.name.orEmpty(),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen }, onCalibrate = null,
    ) {
        if (gyro == null) { NoticeBanner(stringResource(R.string.drive_no_gyro), BadgeLevel.ALARM); return@ToolScaffold }
        CollectSensors(listOf(gyro), SensorManager.SENSOR_DELAY_GAME) { item ->
            if (item !is SensorEventItem.Sample) return@CollectSensors
            val s = item.sample
            val wz = lp.update(s.values[2].toDouble(), s.timestampNs)
            val f = flatLp.update(Tachometer.flatness(s.values[0].toDouble(), s.values[1].toDouble(), s.values[2].toDouble()), s.timestampNs)
            val r = Tachometer.rpm(wz)
            if (rec.isRecording && recT.ready(s.timestampNs)) rec.add(doubleArrayOf(r))
            if (!frozen && ui.ready(s.timestampNs)) { rpm = r; signed = wz; flat = f; series.add(r.toFloat()) }
        }
        BigReadout(
            value = Fmt.num(rpm, if (rpm < 100) 1 else 0), unit = "rpm",
            caption = when {
                rpm < 1 -> stringResource(R.string.tach_caption)
                signed > 0 -> stringResource(R.string.tach_ccw)
                else -> stringResource(R.string.tach_cw)
            },
            badge = stringResource(R.string.tach_badge), badgeLevel = BadgeLevel.APPROX,
        )
        ArcGauge(rpm, 0.0, if (maxRpm > 0) minOf(maxRpm, 600.0).coerceAtLeast(60.0) else 300.0, warnFrom = maxRpm * 0.85, majorStep = 50.0)
        StatRow(
            listOf(
                stringResource(R.string.tach_hz) to Fmt.num(rpm / 60, 2) + " Hz",
                stringResource(R.string.tach_limit) to (if (maxRpm > 0) Fmt.num(maxRpm, 0) + " rpm" else "—"),
            ),
        )
        if (rpm > 5 && flat < 0.9) NoticeBanner(stringResource(R.string.tach_not_flat))
        if (maxRpm > 0 && rpm > maxRpm * 0.95) NoticeBanner(stringResource(R.string.tach_over), BadgeLevel.ALARM)
        SectionCard(stringResource(R.string.tach_trend)) { LiveChart(listOf(series to LocalInstrument.current.accent), fixedMin = 0f, minSpan = 10f) }
        Text(stringResource(R.string.tach_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
