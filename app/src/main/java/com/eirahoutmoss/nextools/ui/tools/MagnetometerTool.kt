package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.CompassMath
import com.eirahoutmoss.nextools.core.measure.RunningStats
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.LiveChart
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.StatusDot
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument

/** Manyetik Alan Ölçer: µT cinsinden üç eksen + toplam, zaman grafiği. */
@Composable
fun MagnetometerTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    val rec = rememberRecording(ToolId.MAGNETOMETER)
    var v by remember { mutableStateOf<DoubleArray?>(null) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var frozen by remember { mutableStateOf(false) }
    var showAxes by rememberSaveable { mutableStateOf(false) }
    val stats = remember { RunningStats() }
    val total = remember { RollingSeries(200) }
    val sx = remember { RollingSeries(200) }
    val sy = remember { RollingSeries(200) }
    val sz = remember { RollingSeries(200) }
    val ui = remember { Throttle(50_000_000L) }
    val recT = remember { Throttle(50_000_000L) }

    CollectSensors(listOfNotNull(sensor), SensorManager.SENSOR_DELAY_GAME) { item ->
        when (item) {
            is SensorEventItem.Accuracy -> accuracy = item.accuracy
            is SensorEventItem.Sample -> {
                val s = item.sample
                val x = s.values[0].toDouble(); val y = s.values[1].toDouble(); val z = s.values[2].toDouble()
                val t = CompassMath.magnitude(x, y, z)
                if (rec.isRecording && recT.ready(s.timestampNs)) rec.add(doubleArrayOf(x, y, z, t))
                if (!frozen && ui.ready(s.timestampNs)) {
                    v = doubleArrayOf(x, y, z, t)
                    stats.add(t)
                    total.add(t.toFloat()); sx.add(x.toFloat()); sy.add(y.toFloat()); sz.add(z.toFloat())
                }
            }
        }
    }

    val inst = LocalInstrument.current
    val colX = Color(0xFFF87171); val colY = Color(0xFF4ADE80); val colZ = Color(0xFF60A5FA)
    ToolScaffold(
        tool = ToolId.MAGNETOMETER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.mag_cal_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("bx_ut", "µT", 2), DataColumn("by_ut", "µT", 2), DataColumn("bz_ut", "µT", 2), DataColumn("field_ut", "µT", 2)),
                    headerComments = csvHeader(context, ToolId.MAGNETOMETER, sensor?.name.orEmpty(), note),
                    calibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM, calibrationNote = note, sensorName = sensor?.name.orEmpty(),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen },
        onCalibrate = null,
        infoExtra = { SensorInfo(sensor) },
    ) {
        val r = v
        if (r == null) { Text(stringResource(R.string.waiting_sensor)); return@ToolScaffold }
        val low = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        BigReadout(
            value = Fmt.num(r[3], 1), unit = "µT",
            caption = stringResource(R.string.mag_total),
            badge = stringResource(if (low) R.string.mag_badge_low else R.string.mag_badge_ok),
            badgeLevel = if (low) BadgeLevel.APPROX else BadgeLevel.GOOD,
        )
        StatRow(listOf("X" to Fmt.num(r[0], 1), "Y" to Fmt.num(r[1], 1), "Z" to Fmt.num(r[2], 1)))
        SectionCard(null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showAxes, onClick = { showAxes = false }, label = { Text(stringResource(R.string.mag_total)) })
                FilterChip(selected = showAxes, onClick = { showAxes = true }, label = { Text(stringResource(R.string.mag_axes)) })
            }
            if (showAxes) {
                LiveChart(listOf(sx to colX, sy to colY, sz to colZ), minSpan = 10f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("X" to colX, "Y" to colY, "Z" to colZ).forEach { (n, col) ->
                        StatusDot(col); Spacer(Modifier.width(4.dp)); Text(n, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.width(14.dp))
                    }
                }
            } else {
                LiveChart(listOf(total to inst.accent), minSpan = 10f)
            }
        }
        StatRow(
            listOf(
                stringResource(R.string.stat_min) to Fmt.num(stats.min, 1),
                stringResource(R.string.stat_avg) to Fmt.num(stats.average, 1),
                stringResource(R.string.stat_max) to Fmt.num(stats.max, 1),
            ),
        )
        OutlinedButton(onClick = { stats.reset(); total.clear(); sx.clear(); sy.clear(); sz.clear() }) { Text(stringResource(R.string.action_reset_stats)) }
        if (r[3] > CompassMath.EARTH_MAX_UT * 1.5) NoticeBanner(stringResource(R.string.mag_strong))
        if (low) NoticeBanner(stringResource(R.string.compass_figure8))
    }
}
