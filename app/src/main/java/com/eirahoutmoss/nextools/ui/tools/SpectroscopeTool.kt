package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.content.Context
import androidx.camera.core.Camera
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Spectro
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.CameraPreview
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.LineProfile
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.components.screenLineProfile
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.flow.MutableStateFlow

private class SpectroCalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("spectroscope", Context.MODE_PRIVATE)
    fun load(): Spectro.Calibration? =
        if (prefs.contains("a")) Spectro.Calibration(prefs.getFloat("a", 1f).toDouble(), prefs.getFloat("b", 0f).toDouble()) else null
    fun save(c: Spectro.Calibration?) {
        prefs.edit().apply { if (c == null) clear() else { putFloat("a", c.a.toFloat()); putFloat("b", c.b.toFloat()) } }.apply()
    }
}

/** Spektroskop: kamera + kırınım ağı (CD/DVD parçası) ile ışığın tayfı; iki çizgiyle dalga boyu kalibrasyonu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpectroscopeTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.SPECTROSCOPE)
    val store = remember { SpectroCalStore(context) }
    var cal by remember { mutableStateOf(store.load()) }
    val flow = remember { MutableStateFlow<LineProfile?>(null) }
    val live by flow.collectAsStateWithLifecycle()
    var frozen by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf<LineProfile?>(null) }
    val frozenRef = remember { booleanArrayOf(false) }
    frozenRef[0] = frozen
    var reference by remember { mutableStateOf<DoubleArray?>(null) }
    var calibrating by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var ev by remember { mutableIntStateOf(0) }
    val lastT = remember { longArrayOf(0L) }

    ToolScaffold(
        tool = ToolId.SPECTROSCOPE, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec, onStartRecording = null,
        frozen = frozen, onFreezeToggle = { frozen = !frozen; held = if (frozen) live else null },
        onCalibrate = { calibrating = !calibrating },
    ) {
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                CameraPreview(
                    Modifier.fillMaxWidth().height(160.dp),
                    onCamera = { camera = it },
                    onFrame = { img ->
                        val now = System.nanoTime()
                        if (now - lastT[0] < 120_000_000L || frozenRef[0]) return@CameraPreview
                        lastT[0] = now
                        flow.value = screenLineProfile(img)
                    },
                )
                Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                }
            }
            val p = if (frozen) held else live
            if (p == null) { Text(stringResource(R.string.waiting_sensor)); return@PermissionGate }
            val inten = p.intensity
            val norm = Spectro.normalize(inten)
            val peaks = Spectro.peaks(norm)
            val main = peaks.maxByOrNull { norm[it.toInt().coerceIn(0, norm.lastIndex)] }
            val c = cal
            BigReadout(
                value = main?.let { if (c != null) Fmt.num(c.nm(it), 0) else Fmt.num(it, 0) } ?: "—",
                unit = if (c != null) "nm" else "px",
                caption = stringResource(R.string.spec_main_peak),
                badge = stringResource(if (c != null) R.string.spec_calibrated else R.string.spec_uncalibrated),
                badgeLevel = if (c != null) BadgeLevel.APPROX else BadgeLevel.NEUTRAL,
            )
            if (p.max >= 250) NoticeBanner(stringResource(R.string.spec_saturated))
            else if (p.max < 40) NoticeBanner(stringResource(R.string.spec_dark), BadgeLevel.NEUTRAL)
            SectionCard(stringResource(R.string.spec_chart)) {
                SpectrumPlot(p, norm, c, peaks, reference)
                reference?.let { ref -> KeyValue(stringResource(R.string.spec_similarity), Fmt.num(Spectro.similarity(norm, ref) * 100, 0) + " %") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-2, -1, 0, 1, 2).forEach { e ->
                    FilterChip(ev == e, {
                        ev = e
                        camera?.let { cam ->
                            val st = cam.cameraInfo.exposureState
                            if (st.isExposureCompensationSupported) {
                                val step = st.exposureCompensationStep.toFloat()
                                val idx = (e / step).toInt().coerceIn(st.exposureCompensationRange.lower, st.exposureCompensationRange.upper)
                                cam.cameraControl.setExposureCompensationIndex(idx)
                            }
                        }
                    }, label = { Text((if (e > 0) "+" else "") + e + " EV") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { reference = if (reference == null) norm else null }) {
                    Text(stringResource(if (reference == null) R.string.spec_set_ref else R.string.spec_clear_ref))
                }
                OutlinedButton(onClick = { saveSpectrum(rec, context, p, c) }) { Text(stringResource(R.string.save_to_notebook)) }
            }
            if (calibrating) CalibrationCard(peaks, c, onSave = { n -> cal = n; store.save(n); calibrating = false })
            if (peaks.isNotEmpty()) {
                SectionCard(stringResource(R.string.spec_peaks)) {
                    peaks.forEach { px -> KeyValue(Fmt.num(px, 1) + " px", c?.let { Fmt.num(it.nm(px), 1) + " nm" } ?: "—") }
                }
            }
            SectionCard(stringResource(R.string.spec_setup_title)) {
                Text(stringResource(R.string.spec_setup), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalibrationCard(peaks: List<Double>, current: Spectro.Calibration?, onSave: (Spectro.Calibration?) -> Unit) {
    var p1 by remember { mutableStateOf<Double?>(null) }
    var p2 by remember { mutableStateOf<Double?>(null) }
    var n1 by remember { mutableStateOf(436.6) }
    var n2 by remember { mutableStateOf(546.5) }
    SectionCard(stringResource(R.string.spec_cal_title)) {
        Text(stringResource(R.string.spec_cal_help), style = MaterialTheme.typography.bodySmall)
        listOf(1, 2).forEach { k ->
            Text(stringResource(R.string.spec_cal_point, k), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                peaks.forEach { px ->
                    val sel = (if (k == 1) p1 else p2) == px
                    FilterChip(sel, { if (k == 1) p1 = px else p2 = px }, label = { Text(Fmt.num(px, 0) + " px") })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spectro.REFERENCE_LINES.forEach { nm ->
                    FilterChip((if (k == 1) n1 else n2) == nm, { if (k == 1) n1 = nm else n2 = nm }, label = { Text(Fmt.num(nm, 1) + " nm") })
                }
            }
        }
        val a = p1; val b = p2
        val candidate = if (a != null && b != null) Spectro.Calibration.fromTwo(a, n1, b, n2) else null
        if (a != null && b != null && candidate == null) NoticeBanner(stringResource(R.string.spec_cal_bad))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(candidate) }, enabled = candidate != null) { Text(stringResource(R.string.action_save)) }
            if (current != null) OutlinedButton(onClick = { onSave(null) }) { Text(stringResource(R.string.spec_cal_clear)) }
        }
    }
}

/** Görünür ışık rengi (yaklaşık; 380–700 nm). */
private fun nmColor(nm: Double): Color {
    val (r, g, b) = when {
        nm < 440 -> Triple((440 - nm) / 60, 0.0, 1.0)
        nm < 490 -> Triple(0.0, (nm - 440) / 50, 1.0)
        nm < 510 -> Triple(0.0, 1.0, (510 - nm) / 20)
        nm < 580 -> Triple((nm - 510) / 70, 1.0, 0.0)
        nm < 645 -> Triple(1.0, (645 - nm) / 65, 0.0)
        else -> Triple(1.0, 0.0, 0.0)
    }
    return Color(r.toFloat().coerceIn(0f, 1f), g.toFloat().coerceIn(0f, 1f), b.toFloat().coerceIn(0f, 1f))
}

@Composable
private fun SpectrumPlot(p: LineProfile, norm: DoubleArray, cal: Spectro.Calibration?, peaks: List<Double>, ref: DoubleArray?) {
    val inst = LocalInstrument.current
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 9.sp, color = inst.scale)
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.8f)) {
        val n = norm.size; if (n < 2) return@Canvas
        val bottom = size.height - 14.dp.toPx()
        fun x(i: Double) = (i / (n - 1) * size.width).toFloat()
        fun y(v: Double) = (bottom - v * bottom * 0.95).toFloat()
        // Sütun renkleri: kalibreliyse dalga boyundan, değilse ölçülen RGB'den
        val bar = size.width / n + 0.5f
        for (i in 0 until n) {
            val col = cal?.let { nmColor(it.nm(i.toDouble())) } ?: run {
                val m = maxOf(p.r[i], p.g[i], p.b[i], 1.0)
                Color((p.r[i] / m).toFloat(), (p.g[i] / m).toFloat(), (p.b[i] / m).toFloat())
            }
            drawLine(col.copy(alpha = 0.55f), Offset(x(i.toDouble()), bottom), Offset(x(i.toDouble()), y(norm[i])), bar)
        }
        val path = Path().apply { moveTo(x(0.0), y(norm[0])); for (i in 1 until n) lineTo(x(i.toDouble()), y(norm[i])) }
        drawPath(path, inst.accent, style = Stroke(1.5.dp.toPx()))
        ref?.let { r ->
            val m = minOf(n, r.size)
            val rp = Path().apply { moveTo(x(0.0), y(r[0])); for (i in 1 until m) lineTo(x(i.toDouble()), y(r[i])) }
            drawPath(rp, Color.White.copy(alpha = 0.7f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
        }
        peaks.forEach { px -> drawCircle(inst.warn, 3.dp.toPx(), Offset(x(px), y(norm[px.toInt().coerceIn(0, n - 1)]))) }
        // Eksen etiketleri
        if (cal != null) {
            var nm = (kotlin.math.ceil(minOf(cal.nm(0.0), cal.nm(n - 1.0)) / 50) * 50)
            val end = maxOf(cal.nm(0.0), cal.nm(n - 1.0))
            while (nm <= end) {
                val xx = x(cal.px(nm))
                drawLine(inst.grid, Offset(xx, 0f), Offset(xx, bottom), 1f)
                drawText(measurer, nm.toInt().toString(), Offset(xx - 8.dp.toPx(), bottom + 1.dp.toPx()), label)
                nm += 50
            }
        } else {
            listOf(0, n / 2, n - 1).forEach { i -> drawText(measurer, "$i px", Offset((x(i.toDouble()) - 10.dp.toPx()).coerceIn(0f, size.width - 30.dp.toPx()), bottom + 1.dp.toPx()), label) }
        }
    }
}

private fun saveSpectrum(rec: com.eirahoutmoss.nextools.ui.components.RecordingController, context: Context, p: LineProfile, cal: Spectro.Calibration?) {
    val note = context.getString(if (cal != null) R.string.spec_note_cal else R.string.spec_note_uncal)
    val inten = p.intensity
    rec.saveOneShot(
        RecordingSpec(
            columns = listOf(DataColumn(if (cal != null) "wavelength_nm" else "pixel", if (cal != null) "nm" else "px", 1),
                DataColumn("intensity", "", 1), DataColumn("r", "", 0), DataColumn("g", "", 0), DataColumn("b", "", 0)),
            headerComments = csvHeader(context, ToolId.SPECTROSCOPE, "camera (back)", note, listOf("one row per pixel along the line; t_ms has no meaning here") +
                (cal?.let { listOf("calibration nm = ${it.a} * px + ${it.b}") } ?: emptyList())),
            calibrated = cal != null, calibrationNote = note, sensorName = context.getString(R.string.hw_camera),
            summarize = { _, _ -> emptyList() },
        ),
        (0 until p.size).map { i -> doubleArrayOf(cal?.nm(i.toDouble()) ?: i.toDouble(), inten[i], p.r[i], p.g[i], p.b[i]) },
    )
}
