package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.LowPass
import com.eirahoutmoss.nextools.core.measure.RateMeter
import com.eirahoutmoss.nextools.core.measure.StaLta
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.LiveChart
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private class SeismoEvent(val atMs: Long, var peakMg: Double)

/** Sismograf: masadaki titreşimi sürekli çizer, STA/LTA eşiği aşılınca olay işaretler. */
@Composable
fun SeismographTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val rec = rememberRecording(ToolId.SEISMOGRAPH)
    val rate = remember { RateMeter() }
    val gravity = remember { LowPass(2.0) }
    val trace = remember { RollingSeries(600) }
    val events = remember { mutableStateListOf<SeismoEvent>() }
    val trigger = remember { arrayOfNulls<StaLta>(1) }
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    var level by remember { mutableDoubleStateOf(0.0) }
    var ratio by remember { mutableDoubleStateOf(0.0) }
    var frozen by remember { mutableStateOf(false) }
    val chunkMax = remember { doubleArrayOf(0.0) }
    val ui = remember { Throttle(50_000_000L) }
    val recT = remember { Throttle(20_000_000L) }

    CollectSensors(listOfNotNull(sensor), SensorManager.SENSOR_DELAY_GAME) { item ->
        if (item !is SensorEventItem.Sample) return@CollectSensors
        val s = item.sample
        rate.add(s.timestampNs)
        val mag = sqrt((s.values[0] * s.values[0] + s.values[1] * s.values[1] + s.values[2] * s.values[2]).toDouble())
        val mg = (mag - gravity.update(mag, s.timestampNs)) / SensorManager.STANDARD_GRAVITY * 1000
        val fs = rate.hz
        if (fs != null && trigger[0] == null) trigger[0] = StaLta(fs)
        trigger[0]?.let { t ->
            t.onRatio = 6.0 - 4.0 * sensitivity
            if (t.update(abs(mg))) events.add(0, SeismoEvent(System.currentTimeMillis(), abs(mg)))
            if (t.active) events.firstOrNull()?.let { it.peakMg = max(it.peakMg, abs(mg)) }
        }
        if (rec.isRecording && recT.ready(s.timestampNs)) rec.add(doubleArrayOf(mg))
        chunkMax[0] = if (abs(mg) > abs(chunkMax[0])) mg else chunkMax[0]
        if (!frozen && ui.ready(s.timestampNs)) {
            trace.add(chunkMax[0].toFloat()); chunkMax[0] = 0.0
            level = abs(mg); ratio = trigger[0]?.ratio ?: 0.0
        }
    }

    ToolScaffold(
        tool = ToolId.SEISMOGRAPH, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.seismo_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("accel_mg", "mg", 3)),
                    headerComments = csvHeader(context, ToolId.SEISMOGRAPH, sensor?.name.orEmpty(), note, listOf("accel_mg = |a| minus gravity, milli-g")),
                    calibrated = false, calibrationNote = note, sensorName = sensor?.name.orEmpty(),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen }, onCalibrate = null,
        infoExtra = { SensorInfo(sensor) },
    ) {
        val inst = LocalInstrument.current
        val active = trigger[0]?.active == true
        BigReadout(
            value = Fmt.num(level, 2), unit = "mg",
            caption = stringResource(if (active) R.string.seismo_event_now else R.string.seismo_quiet),
            badge = stringResource(R.string.seismo_badge), badgeLevel = BadgeLevel.NEUTRAL,
            valueColor = if (active) inst.warn else MaterialTheme.colorScheme.onBackground,
        )
        SectionCard(null) {
            LiveChart(listOf(trace to inst.accent), minSpan = 2f)
            Text(stringResource(R.string.seismo_ratio, Fmt.num(ratio, 1)), style = MaterialTheme.typography.bodySmall, color = inst.scale)
        }
        SectionCard(stringResource(R.string.seismo_sensitivity)) {
            Slider(value = sensitivity, onValueChange = { sensitivity = it })
            Text(stringResource(R.string.seismo_sensitivity_hint), style = MaterialTheme.typography.bodySmall)
        }
        SectionCard(stringResource(R.string.seismo_events, events.size)) {
            if (events.isEmpty()) Text(stringResource(R.string.seismo_no_events), style = MaterialTheme.typography.bodySmall)
            events.take(20).forEach { e -> KeyValue(Fmt.dateTime(e.atMs), stringResource(R.string.seismo_event_peak, Fmt.num(e.peakMg, 2))) }
            if (events.isNotEmpty()) Row { OutlinedButton(onClick = { events.clear() }) { Text(stringResource(R.string.action_clear)) } }
        }
        NoticeBanner(stringResource(R.string.seismo_disclaimer), BadgeLevel.NEUTRAL)
    }
}
