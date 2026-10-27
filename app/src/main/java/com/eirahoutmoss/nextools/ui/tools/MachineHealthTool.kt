package com.eirahoutmoss.nextools.ui.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.MachineHealth
import com.eirahoutmoss.nextools.core.measure.MachineSignature
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeepScreenOn
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.MeterBar
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Makine taban imzaları: files/machines.json (ad → imza). */
class MachineStore(context: Context) {
    private val file = File(context.filesDir, "machines.json")

    @Synchronized fun load(): Map<String, MachineSignature> = try {
        if (!file.exists()) emptyMap() else {
            val o = JSONObject(file.readText())
            o.keys().asSequence().associateWith { k ->
                val m = o.getJSONObject(k)
                val b = m.getJSONArray("bands")
                MachineSignature(DoubleArray(b.length()) { b.getDouble(it) }, m.getDouble("rms"), m.optDouble("dominant_hz").takeUnless { it.isNaN() }, m.getDouble("fs"))
            }
        }
    } catch (_: Exception) { emptyMap() }

    @Synchronized fun put(name: String, s: MachineSignature?) {
        val o = try { if (file.exists()) JSONObject(file.readText()) else JSONObject() } catch (_: Exception) { JSONObject() }
        if (s == null) o.remove(name) else o.put(name, JSONObject().apply {
            put("bands", JSONArray(s.bands.toList())); put("rms", s.rms); s.dominantHz?.let { put("dominant_hz", it) }; put("fs", s.fs)
            put("saved_at", System.currentTimeMillis())
        })
        val tmp = File(file.parentFile, "machines.json.tmp"); tmp.writeText(o.toString()); tmp.renameTo(file)
    }
}

private const val CAPTURE_S = 10.0

private fun verdictText(v: MachineHealth.Verdict) = when (v) {
    MachineHealth.Verdict.SAME -> R.string.mach_same
    MachineHealth.Verdict.SLIGHT_CHANGE -> R.string.mach_slight
    MachineHealth.Verdict.CHANGED -> R.string.mach_changed
}

/** Makine Sağlığı: telefon makinenin üstüne konur, 10 sn titreşim imzası alınır ve kayıtlı tabanla karşılaştırılır. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MachineHealthTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accel = remember { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val store = remember { MachineStore(context) }
    val rec = rememberRecording(ToolId.MACHINE_HEALTH)
    val scope = rememberCoroutineScope()
    var machines by remember { mutableStateOf(store.load()) }
    var name by rememberSaveable { mutableStateOf("") }
    var capturing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var current by remember { mutableStateOf<MachineSignature?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val buf = remember { ArrayList<Double>(8192) }
    val t0 = remember { longArrayOf(0L, 0L) }

    fun finish() {
        capturing = false
        val x = buf.toDoubleArray(); val span = (t0[1] - t0[0]) / 1e9
        buf.clear()
        if (x.size < 256 || span <= 0) { error = context.getString(R.string.mach_too_few); return }
        val fs = (x.size - 1) / span
        scope.launch { current = withContext(Dispatchers.Default) { MachineHealth.signature(x, fs) } }
    }

    ToolScaffold(
        tool = ToolId.MACHINE_HEALTH, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec, onStartRecording = null, frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        KeepScreenOn(capturing)
        if (accel == null) { NoticeBanner(stringResource(R.string.waiting_sensor), BadgeLevel.ALARM); return@ToolScaffold }
        if (capturing) {
            CollectSensors(listOf(accel), 0) { item ->
                if (item !is SensorEventItem.Sample || !capturing) return@CollectSensors
                val s = item.sample
                if (t0[0] == 0L) t0[0] = s.timestampNs
                t0[1] = s.timestampNs
                val v = s.values
                buf += sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
                progress = ((s.timestampNs - t0[0]) / 1e9 / CAPTURE_S).toFloat()
                if (progress >= 1f) finish()
            }
        }
        val base = machines[name.trim()]
        val cmp = if (base != null && current != null) MachineHealth.compare(base, current!!) else null
        BigReadout(
            value = cmp?.let { stringResource(verdictText(it.verdict)) } ?: current?.let { Fmt.num(it.rms * 1000, 0) } ?: "—",
            unit = if (cmp == null && current != null) "mm/s²" else "",
            caption = when {
                cmp != null -> stringResource(R.string.mach_vs_base, name.trim())
                current != null -> stringResource(R.string.mach_rms)
                else -> stringResource(R.string.mach_caption)
            },
            badge = stringResource(R.string.mach_badge), badgeLevel = BadgeLevel.NEUTRAL,
            valueColor = when (cmp?.verdict) {
                MachineHealth.Verdict.SAME -> LocalInstrument.current.good
                MachineHealth.Verdict.SLIGHT_CHANGE -> LocalInstrument.current.warn
                MachineHealth.Verdict.CHANGED -> LocalInstrument.current.alarm
                null -> MaterialTheme.colorScheme.onBackground
            },
        )
        OutlinedTextField(name, { name = it.take(40); current = null }, label = { Text(stringResource(R.string.mach_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (machines.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                machines.keys.sorted().forEach { k -> FilterChip(k == name.trim(), { name = k; current = null }, label = { Text(k) }) }
            }
        }
        if (capturing) {
            Text(stringResource(R.string.mach_capturing), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        } else {
            Button(
                onClick = { error = null; current = null; buf.clear(); t0[0] = 0L; t0[1] = 0L; progress = 0f; capturing = true },
                enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.mach_measure, CAPTURE_S.toInt())) }
        }
        error?.let { NoticeBanner(it, BadgeLevel.ALARM) }
        val cur = current
        if (cur != null && !capturing) {
            if (cmp != null && base != null) {
                SectionCard(stringResource(R.string.mach_compare)) {
                    KeyValue(stringResource(R.string.mach_rms_change), Fmt.signed(cmp.rmsChangePct, 0) + " %")
                    KeyValue(stringResource(R.string.mach_similarity), Fmt.num(cmp.similarity * 100, 0) + " %")
                    KeyValue(stringResource(R.string.mach_dominant), (base.dominantHz?.let { Fmt.num(it, 1) } ?: "—") + " → " + (cur.dominantHz?.let { Fmt.num(it, 1) } ?: "—") + " Hz")
                }
            }
            SectionCard(stringResource(R.string.mach_bands)) {
                val inst = LocalInstrument.current
                val ref = base?.bands
                val top = max(cur.bands.maxOrNull() ?: 1e-12, ref?.maxOrNull() ?: 1e-12)
                fun db(e: Double) = 10 * log10(max(e, 1e-12) / top)
                bandEdges(cur.fs).forEachIndexed { i, (lo, hi) ->
                    KeyValue(Fmt.num(lo, 0) + "–" + Fmt.num(hi, 0) + " Hz", Fmt.num(db(cur.bands[i]), 0) + " dB" + (ref?.let { "  (" + Fmt.num(db(it[i]), 0) + ")" } ?: ""))
                    MeterBar(db(cur.bands[i]) + 60, ref?.let { db(it[i]) + 60 } ?: 0.0, 60.0, inst.accent, log = false)
                }
                if (ref != null) Text(stringResource(R.string.mach_bands_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { store.put(name.trim(), cur); machines = store.load() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (base == null) R.string.mach_save_base else R.string.mach_replace_base))
            }
            OutlinedButton(onClick = { saveMachine(rec, context, name.trim(), cur, base, cmp) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_to_notebook)) }
        }
        if (base != null && !capturing) {
            OutlinedButton(onClick = { store.put(name.trim(), null); machines = store.load(); current = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mach_delete_base)) }
        }
        NoticeBanner(stringResource(R.string.mach_no_mic), BadgeLevel.NEUTRAL)
        Text(stringResource(R.string.mach_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun bandEdges(fs: Double): List<Pair<Double, Double>> {
    val e = DoubleArray(17) { 2.0 * kotlin.math.exp(it * kotlin.math.ln((fs / 2) / 2.0) / 16) }
    return (0 until 16).map { e[it] to e[it + 1] }
}

private fun saveMachine(rec: com.eirahoutmoss.nextools.ui.components.RecordingController, context: Context, name: String, cur: MachineSignature, base: MachineSignature?, cmp: MachineHealth.Comparison?) {
    val note = context.getString(R.string.mach_note, name)
    val top = max(cur.bands.maxOrNull() ?: 1e-12, base?.bands?.maxOrNull() ?: 1e-12)
    fun db(e: Double) = 10 * log10(max(e, 1e-12) / top)
    val edges = bandEdges(cur.fs)
    rec.saveOneShot(
        RecordingSpec(
            columns = listOf(DataColumn("band_hz", "Hz", 1), DataColumn("now_db", "dB", 1), DataColumn("base_db", "dB", 1)),
            headerComments = csvHeader(context, ToolId.MACHINE_HEALTH, "accelerometer", note, listOf("machine = $name", "one row per log band (centre frequency); t_ms has no meaning here")),
            calibrated = false, calibrationNote = note, sensorName = context.getString(R.string.hw_accelerometer),
            summarize = { _, _ ->
                listOfNotNull(
                    SummaryItem("rms", "now_db", cur.rms * 1000, "mm/s²", 0),
                    cmp?.let { SummaryItem("similarity", "now_db", it.similarity * 100, "%", 0) },
                    cmp?.let { SummaryItem("rms_change", "now_db", it.rmsChangePct, "%", 0) },
                )
            },
        ),
        edges.mapIndexed { i, (lo, hi) -> doubleArrayOf(sqrt(lo * hi), db(cur.bands[i]), base?.let { db(it.bands[i]) } ?: Double.NaN) },
    )
}
