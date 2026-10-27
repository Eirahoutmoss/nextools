package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.ColorMath
import com.eirahoutmoss.nextools.core.measure.PhScale
import com.eirahoutmoss.nextools.core.measure.Turbidity
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.CameraPreview
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.centerRgb
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import kotlinx.coroutines.flow.MutableStateFlow

/** Kullanıcının kendi pH kâğıdı skalası ve berrak su referansı (SharedPreferences, Lab olarak). */
private class PhStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ph_reader", Context.MODE_PRIVATE)
    fun scale(): List<PhScale.Ref> = (1..14).mapNotNull { p -> prefs.getString("ph$p", null)?.let { parse(it) }?.let { PhScale.Ref(p.toDouble(), it) } }
    fun setPh(ph: Int, lab: ColorMath.Lab?) = prefs.edit().apply { if (lab == null) remove("ph$ph") else putString("ph$ph", fmt(lab)) }.apply()
    fun clearScale() = prefs.edit().apply { (1..14).forEach { remove("ph$it") } }.apply()
    fun clearWater(): ColorMath.Lab? = prefs.getString("water", null)?.let { parse(it) }
    fun setWater(lab: ColorMath.Lab) = prefs.edit().putString("water", fmt(lab)).apply()
    private fun fmt(l: ColorMath.Lab) = "${l.l};${l.a};${l.b}"
    private fun parse(s: String) = s.split(';').mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 3 }?.let { ColorMath.Lab(it[0], it[1], it[2]) }
}

private fun turbidityText(l: Turbidity.Level) = when (l) {
    Turbidity.Level.CLEAR -> R.string.ph_turb_clear
    Turbidity.Level.SLIGHT -> R.string.ph_turb_slight
    Turbidity.Level.CLOUDY -> R.string.ph_turb_cloudy
    Turbidity.Level.VERY_CLOUDY -> R.string.ph_turb_very
}

/** pH / Bulanıklık: pH kâğıdının rengini skalayla karşılaştırır; suyun berrak su referansına göre bulanıklığı. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhReaderTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.PH_READER)
    val store = remember { PhStore(context) }
    val rgbFlow = remember { MutableStateFlow<IntArray?>(null) }
    val rgb by rgbFlow.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var scale by remember { mutableStateOf(store.scale()) }
    var water by remember { mutableStateOf(store.clearWater()) }
    var held by remember { mutableStateOf<IntArray?>(null) }
    var calPh by rememberSaveable { mutableIntStateOf(7) }
    var calibrating by remember { mutableStateOf(false) }
    val useOwn = scale.size >= 3

    ToolScaffold(
        tool = ToolId.PH_READER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec, onStartRecording = null,
        frozen = held != null, onFreezeToggle = { held = if (held == null) rgb else null },
        onCalibrate = { calibrating = !calibrating; mode = 0 },
    ) {
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(mode == 0, { mode = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.ph_mode_ph)) }
                SegmentedButton(mode == 1, { mode = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.ph_mode_turb)) }
            }
            Box(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                CameraPreview(Modifier.fillMaxWidth().aspectRatio(1.3f), onFrame = { img -> rgbFlow.value = centerRgb(img, box = 32) })
                Box(Modifier.size(36.dp).border(2.dp, Color.White, CircleShape))
            }
            val c = held ?: rgb
            if (c == null) { Text(stringResource(R.string.waiting_sensor)); return@PermissionGate }
            val lab = ColorMath.lab(c[0], c[1], c[2])
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(28.dp).background(Color(c[0], c[1], c[2]), RoundedCornerShape(6.dp)))
                Text(ColorMath.hex(c[0], c[1], c[2]) + " · L* " + Fmt.num(lab.l, 0), style = MaterialTheme.typography.bodyMedium)
            }
            if (mode == 0) {
                val refs = if (useOwn) scale else PhScale.GENERIC
                val e = PhScale.estimate(lab, refs)
                BigReadout(
                    value = e?.let { Fmt.num(it.ph, 1) } ?: "—", unit = "pH",
                    caption = stringResource(if (useOwn) R.string.ph_own_scale else R.string.ph_generic_scale),
                    badge = stringResource(R.string.ph_badge), badgeLevel = BadgeLevel.APPROX,
                )
                e?.let { if (it.nearestDeltaE > 15) NoticeBanner(stringResource(R.string.ph_far, Fmt.num(it.nearestDeltaE, 0))) }
                OutlinedButton(onClick = {
                    e?.let { est ->
                        rec.saveOneShot(
                            RecordingSpec(
                                columns = listOf(DataColumn("ph", "", 1), DataColumn("r", "", 0), DataColumn("g", "", 0), DataColumn("b", "", 0), DataColumn("delta_e", "", 1)),
                                headerComments = csvHeader(context, ToolId.PH_READER, "camera (back)", context.getString(if (useOwn) R.string.ph_own_scale else R.string.ph_generic_scale)),
                                calibrated = useOwn, calibrationNote = context.getString(if (useOwn) R.string.ph_own_scale else R.string.ph_generic_scale),
                                sensorName = context.getString(R.string.hw_camera),
                                summarize = { _, _ -> listOf(SummaryItem("last", "ph", est.ph, "", 1)) },
                            ),
                            listOf(doubleArrayOf(est.ph, c[0].toDouble(), c[1].toDouble(), c[2].toDouble(), est.nearestDeltaE)),
                        )
                    }
                }, enabled = e != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_to_notebook)) }
                if (calibrating) {
                    SectionCard(stringResource(R.string.ph_cal_title)) {
                        Text(stringResource(R.string.ph_cal_help), style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..14).forEach { p ->
                                val has = scale.any { it.ph == p.toDouble() }
                                FilterChip(calPh == p, { calPh = p }, label = { Text((if (has) "✓ " else "") + p) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { store.setPh(calPh, lab); scale = store.scale() }) { Text(stringResource(R.string.ph_cal_save, calPh)) }
                            OutlinedButton(onClick = { store.clearScale(); scale = store.scale() }) { Text(stringResource(R.string.ph_cal_clear)) }
                        }
                        Text(stringResource(R.string.ph_cal_count, scale.size), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(stringResource(R.string.ph_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val ref = water
                val de = ref?.let { ColorMath.deltaE2000(lab, it) }
                BigReadout(
                    value = de?.let { stringResource(turbidityText(Turbidity.level(it))) } ?: "—", unit = "",
                    caption = if (ref == null) stringResource(R.string.ph_turb_need_ref) else stringResource(R.string.ph_turb_caption, Fmt.num(de ?: 0.0, 1)),
                    badge = stringResource(R.string.ph_turb_badge), badgeLevel = BadgeLevel.NEUTRAL,
                )
                OutlinedButton(onClick = { store.setWater(lab); water = lab }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ph_turb_set_ref)) }
                SectionCard(stringResource(R.string.ph_turb_scale)) {
                    listOf("< 2" to Turbidity.Level.CLEAR, "2–5" to Turbidity.Level.SLIGHT, "5–12" to Turbidity.Level.CLOUDY, "> 12" to Turbidity.Level.VERY_CLOUDY)
                        .forEach { (k, l) -> KeyValue("ΔE $k", stringResource(turbidityText(l))) }
                }
                Text(stringResource(R.string.ph_turb_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
