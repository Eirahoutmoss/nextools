package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.RateMeter
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import com.eirahoutmoss.nextools.ui.theme.NumberStyles

/** Çoklu kayıtta bir sensörün kaç bileşeni yazılır. */
private fun componentCount(type: Int): Int = when (type) {
    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GRAVITY,
    Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR,
    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> 3
    else -> 1
}

private fun unitOf(type: Int): String = when (type) {
    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "m/s²"
    Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
    Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "µT"
    Sensor.TYPE_PRESSURE -> "hPa"
    Sensor.TYPE_LIGHT -> "lx"
    Sensor.TYPE_PROXIMITY -> "cm"
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
    Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
    else -> ""
}

/** Sütun öneki: tür adı (ör. accelerometer), aynı türden ikinci sensörde sıra eklenir. */
private fun prefixes(list: List<Sensor>): List<String> {
    val seen = HashMap<String, Int>()
    return list.map { s ->
        val base = s.stringType.substringAfterLast('.').ifBlank { "type${s.type}" }
        val n = (seen[base] ?: 0) + 1; seen[base] = n
        if (n == 1) base else "${base}$n"
    }
}

private val MULTI_RATES = listOf(10, 50, 100)

/** Sensör Testi: bütün sensörlerin canlı ham değeri ve gerçek örnekleme hızı; seçilen sensörlerin birlikte kaydı. */
@Composable
fun SensorTestTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val sensors = remember { sm.getSensorList(Sensor.TYPE_ALL).sortedWith(compareBy({ it.type }, { it.name })) }
    var open by rememberSaveable { mutableIntStateOf(-1) }
    val chosen = remember { mutableStateListOf<Int>() }
    var rateHz by rememberSaveable { mutableIntStateOf(50) }
    val rec = rememberRecording(ToolId.SENSOR_TEST)
    // Kayıt anındaki seçim: sensör listesi, sütun başlangıçları ve son değerler (örnekle-tut)
    val active = remember { mutableStateOf<List<Sensor>>(emptyList()) }
    val latest = remember { mutableStateOf(DoubleArray(0)) }
    val offsets = remember { mutableStateOf(IntArray(0)) }

    ToolScaffold(
        tool = ToolId.SENSOR_TEST, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = if (chosen.isEmpty()) null else ({
            val list = chosen.sorted().map { sensors[it] }
            val pre = prefixes(list)
            val cols = mutableListOf<DataColumn>(); val off = IntArray(list.size)
            list.forEachIndexed { i, s ->
                off[i] = cols.size
                val n = componentCount(s.type)
                val suffix = if (n == 3) listOf("x", "y", "z") else listOf("")
                suffix.forEach { k -> cols += DataColumn(if (k.isEmpty()) pre[i] else "${pre[i]}_$k", unitOf(s.type), 4) }
            }
            active.value = list; offsets.value = off; latest.value = DoubleArray(cols.size) { Double.NaN }
            val note = context.getString(R.string.multi_note, rateHz)
            rec.start(
                RecordingSpec(
                    columns = cols,
                    headerComments = csvHeader(context, ToolId.SENSOR_TEST, list.joinToString(" + ") { it.name }, note,
                        listOf("resampled at $rateHz Hz (sample-and-hold)") + list.mapIndexed { i, s -> "${pre[i]} = ${s.name} (${s.vendor})" }),
                    calibrated = false, calibrationNote = note, sensorName = list.joinToString(", ") { it.name },
                ),
            )
        }),
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        Text(stringResource(R.string.sensor_test_count, sensors.size), style = MaterialTheme.typography.titleSmall)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) NoticeBanner(stringResource(R.string.sensor_test_rate_note), BadgeLevel.NEUTRAL)
        SectionCard(stringResource(R.string.multi_title)) {
            Text(
                if (chosen.isEmpty()) stringResource(R.string.multi_hint) else stringResource(R.string.multi_selected, chosen.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MULTI_RATES.forEach { r -> FilterChip(rateHz == r, { if (!rec.isRecording) rateHz = r }, label = { Text("$r Hz") }) }
            }
        }
        if (rec.isRecording && active.value.isNotEmpty()) {
            val list = active.value
            CollectSensors(list, SensorManager.SENSOR_DELAY_FASTEST, key = list) { item ->
                if (item !is SensorEventItem.Sample) return@CollectSensors
                val i = item.sample.sensor?.let { list.indexOf(it) }?.takeIf { it >= 0 } ?: list.indexOfFirst { it.type == item.sample.type }
                if (i < 0) return@CollectSensors
                val arr = latest.value; val o = offsets.value[i]
                val n = componentCount(list[i].type)
                for (k in 0 until n) if (k < item.sample.values.size) arr[o + k] = item.sample.values[k].toDouble()
            }
            LaunchedEffect(list, rateHz) {
                val period = 1000L / rateHz
                var next = System.currentTimeMillis()
                while (rec.isRecording) {
                    rec.add(latest.value.copyOf())
                    next += period
                    delay((next - System.currentTimeMillis()).coerceAtLeast(1))
                }
            }
        }
        sensors.forEachIndexed { i, s ->
            SensorRow(
                s, expanded = open == i, onToggle = { open = if (open == i) -1 else i },
                checked = i in chosen,
                onCheck = { if (!rec.isRecording) { if (i in chosen) chosen.remove(i) else chosen.add(i) } },
            )
        }
    }
}

@Composable
private fun SensorRow(sensor: Sensor, expanded: Boolean, onToggle: () -> Unit, checked: Boolean, onCheck: () -> Unit) {
    val inst = LocalInstrument.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, { onCheck() })
                Column(Modifier.weight(1f)) {
                    Text(sensor.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Text(
                        sensor.stringType.removePrefix("android.sensor.") + " · " + sensor.vendor,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
            }
            if (expanded) {
                val rate = remember(sensor) { RateMeter() }
                var values by remember(sensor) { mutableStateOf<FloatArray?>(null) }
                var hz by remember(sensor) { mutableStateOf<Double?>(null) }
                var accuracy by remember(sensor) { mutableIntStateOf(-1) }
                val ui = remember(sensor) { Throttle(80_000_000L) }
                CollectSensors(listOf(sensor), SensorManager.SENSOR_DELAY_FASTEST, key = sensor) { item ->
                    when (item) {
                        is SensorEventItem.Accuracy -> accuracy = item.accuracy
                        is SensorEventItem.Sample -> {
                            rate.add(item.sample.timestampNs)
                            if (ui.ready(item.sample.timestampNs)) { values = item.sample.values; hz = rate.hz; accuracy = item.sample.accuracy }
                        }
                    }
                }
                Column(Modifier.padding(top = 10.dp)) {
                    val v = values
                    if (v == null) {
                        Text(stringResource(R.string.sensor_test_waiting), style = MaterialTheme.typography.bodySmall)
                    } else {
                        v.take(6).forEachIndexed { k, x ->
                            Text("[$k]  " + Fmt.num(x.toDouble(), 4), style = NumberStyles.Small, color = inst.accent)
                        }
                    }
                    KeyValue(stringResource(R.string.sensor_test_measured_rate), hz?.let { Fmt.num(it, 1) + " Hz" } ?: "—")
                    KeyValue(stringResource(R.string.sensor_test_nominal_rate), if (sensor.minDelay > 0) Fmt.num(1e6 / sensor.minDelay, 0) + " Hz" else stringResource(R.string.sensor_test_on_change))
                    KeyValue(stringResource(R.string.info_resolution), sensor.resolution.toString())
                    KeyValue(stringResource(R.string.sensor_test_range), sensor.maximumRange.toString())
                    KeyValue(stringResource(R.string.sensor_test_power), Fmt.num(sensor.power.toDouble(), 2) + " mA")
                    KeyValue(stringResource(R.string.sensor_test_accuracy), accuracy.takeIf { it >= 0 }?.toString() ?: "—")
                }
            }
        }
    }
}
