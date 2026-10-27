package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.PressureSpike
import com.eirahoutmoss.nextools.core.measure.RateMeter
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
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument

private class SealTest(val room: String, val peakPa: Double, val decayS: Double?, val atMs: Long)

/** Sızdırmazlık: kapı hızla kapanınca oluşan basınç sıçraması ve sönüm süresi; odalar arası göreli karşılaştırma. */
@Composable
fun AirtightnessTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val baro = remember { sm.getDefaultSensor(Sensor.TYPE_PRESSURE) }
    val rec = rememberRecording(ToolId.AIRTIGHTNESS)
    val rate = remember { RateMeter() }
    val detector = remember { arrayOfNulls<PressureSpike>(1) }
    val baseline = remember { doubleArrayOf(Double.NaN) }
    var rel by remember { mutableDoubleStateOf(0.0) }
    var fs by remember { mutableDoubleStateOf(0.0) }
    var room by rememberSaveable { mutableStateOf("") }
    val tests = remember { mutableStateListOf<SealTest>() }
    val series = remember { RollingSeries(250) }

    ToolScaffold(
        tool = ToolId.AIRTIGHTNESS, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.seal_note, room.ifBlank { "—" })
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("rel_pa", "Pa", 2)),
                    headerComments = csvHeader(context, ToolId.AIRTIGHTNESS, baro?.name.orEmpty(), note, listOf("rel_pa = pressure minus slow baseline (10 s)")),
                    calibrated = false, calibrationNote = note, sensorName = baro?.name.orEmpty(),
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        if (baro == null) { NoticeBanner(stringResource(R.string.waiting_sensor), BadgeLevel.ALARM); return@ToolScaffold }
        CollectSensors(listOf(baro), SensorManager.SENSOR_DELAY_FASTEST) { item ->
            if (item !is SensorEventItem.Sample) return@CollectSensors
            val s = item.sample
            val hpa = s.values[0].toDouble()
            rate.add(s.timestampNs)
            val hz = rate.hz ?: return@CollectSensors
            if (detector[0] == null) { detector[0] = PressureSpike(hz); fs = hz }
            val b = baseline[0]
            baseline[0] = if (b.isNaN()) hpa else b + (hpa - b) * (1.0 / (10 * hz)).coerceAtMost(1.0)
            rel = (hpa - baseline[0]) * 100
            series.add(rel.toFloat())
            if (rec.isRecording) rec.add(doubleArrayOf(rel))
            detector[0]?.update(hpa)?.let { e -> tests.add(0, SealTest(room.trim(), e.peakPa, e.decayS, System.currentTimeMillis())) }
        }
        val last = tests.firstOrNull()
        BigReadout(
            value = last?.let { Fmt.num(it.peakPa, 1) } ?: "—", unit = "Pa",
            caption = last?.let { stringResource(R.string.seal_last, it.room.ifBlank { "—" }) } ?: stringResource(R.string.seal_caption),
            badge = stringResource(R.string.seal_badge), badgeLevel = BadgeLevel.APPROX,
        )
        StatRow(
            listOf(
                stringResource(R.string.seal_now) to Fmt.signed(rel, 1) + " Pa",
                stringResource(R.string.seal_decay) to (last?.decayS?.let { Fmt.num(it, 2) + " s" } ?: "—"),
                stringResource(R.string.seal_rate) to (if (fs > 0) Fmt.num(fs, 0) + " Hz" else "—"),
            ),
        )
        if (fs in 0.1..9.0) NoticeBanner(stringResource(R.string.seal_slow_sensor, Fmt.num(fs, 0)))
        OutlinedTextField(room, { room = it.take(30) }, label = { Text(stringResource(R.string.seal_room)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SectionCard(stringResource(R.string.seal_live)) { LiveChart(listOf(series to LocalInstrument.current.accent), minSpan = 4f, threshold = 2f) }
        if (tests.isNotEmpty()) {
            SectionCard(stringResource(R.string.seal_tests, tests.size)) {
                tests.forEach { t ->
                    KeyValue((t.room.ifBlank { "—" }) + " · " + Fmt.dateTime(t.atMs), Fmt.num(t.peakPa, 1) + " Pa · " + (t.decayS?.let { Fmt.num(it, 2) + " s" } ?: "—"))
                }
                OutlinedButton(onClick = { tests.clear() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.seal_clear)) }
            }
        }
        Text(stringResource(R.string.seal_how), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
