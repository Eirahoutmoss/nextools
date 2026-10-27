package com.eirahoutmoss.nextools.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eirahoutmoss.nextools.core.record.Downsample
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Canlı grafik için sabit boyutlu kayan dizi. Her ekleme Compose'a yeniden çizim sinyali verir. */
@Stable
class RollingSeries(val capacity: Int) {
    private val buf = FloatArray(capacity)
    private var start = 0
    private var count = 0
    var version by mutableStateOf(0)
        private set

    fun add(v: Float) {
        if (count < capacity) {
            buf[(start + count) % capacity] = v; count++
        } else {
            buf[start] = v; start = (start + 1) % capacity
        }
        version++
    }

    fun clear() { start = 0; count = 0; version++ }

    fun snapshot(): FloatArray {
        @Suppress("UNUSED_VARIABLE") val v = version // okuma: yeniden çizimi tetikler
        return FloatArray(count) { buf[(start + it) % capacity] }
    }
}

private fun niceStep(span: Double, count: Int = 4): Double {
    if (span <= 0) return 1.0
    val raw = span / count
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    return (if (n < 1.5) 1.0 else if (n < 3) 2.0 else if (n < 7) 5.0 else 10.0) * mag
}

private fun decimalsFor(step: Double): Int = if (step >= 1) 0 else ceil(-log10(step)).toInt().coerceAtMost(4)

/**
 * Canlı çizgi grafik (ızgara arka planlı). [fixedMin]/[fixedMax] verilmezse ölçek kendini ayarlar.
 * [threshold] kesikli yatay çizgi olarak çizilir.
 */
@Composable
fun LiveChart(
    series: List<Pair<RollingSeries, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    fixedMin: Float? = null,
    fixedMax: Float? = null,
    minSpan: Float = 1f,
    threshold: Float? = null,
) {
    val inst = LocalInstrument.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = inst.scale)
    val data = series.map { it.first.snapshot() to it.second }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val all = data.flatMap { d -> d.first.asList() }
        var lo = fixedMin ?: (all.minOrNull() ?: 0f)
        var hi = fixedMax ?: (all.maxOrNull() ?: 1f)
        if (hi - lo < minSpan) { val mid = (hi + lo) / 2; lo = mid - minSpan / 2; hi = mid + minSpan / 2 }
        val step = niceStep((hi - lo).toDouble())
        if (fixedMin == null) lo = (floor(lo / step) * step).toFloat()
        if (fixedMax == null) hi = (ceil(hi / step) * step).toFloat()
        val left = 34.dp.toPx()
        val w = size.width - left
        val h = size.height
        fun yOf(v: Float) = h - (v - lo) / (hi - lo) * h
        // ızgara
        var t = lo.toDouble()
        val dec = decimalsFor(step)
        var guard = 0
        while (t <= hi + step * 0.01 && guard++ < 20) {
            val y = yOf(t.toFloat())
            drawLine(inst.grid, Offset(left, y), Offset(size.width, y), 1f)
            val lay = measurer.measure(Fmt.num(t, dec), labelStyle)
            drawText(lay, topLeft = Offset(left - lay.size.width - 4.dp.toPx(), (y - lay.size.height / 2).coerceIn(0f, h - lay.size.height)))
            t += step
        }
        for (i in 1..5) {
            val x = left + w * i / 6
            drawLine(inst.grid, Offset(x, 0f), Offset(x, h), 1f)
        }
        threshold?.let { th ->
            if (th in lo..hi) drawLine(inst.alarm, Offset(left, yOf(th)), Offset(size.width, yOf(th)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        data.forEach { (vals, color) ->
            if (vals.size < 2) return@forEach
            val cap = series.first().first.capacity
            val path = Path()
            vals.forEachIndexed { i, v ->
                val x = left + w * (i + (cap - vals.size)).toFloat() / (cap - 1)
                val y = yOf(v.coerceIn(lo, hi))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/**
 * Kayıt grafiği: x saniye, y değer. Birden fazla seri üst üste (karşılaştırma için).
 */
@Composable
fun RecordChart(
    series: List<Triple<DoubleArray, DoubleArray, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    xSuffix: String = " s",
) {
    val inst = LocalInstrument.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = inst.scale)
    val prepared = series.map { (x, y, c) -> Downsample.minMax(x, y, 1500).let { Triple(it.first, it.second, c) } }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val ys = prepared.flatMap { p -> p.second.filter { !it.isNaN() } }
        val xs = prepared.flatMap { it.first.asList() }
        if (ys.isEmpty() || xs.isEmpty()) return@Canvas
        var lo = ys.min(); var hi = ys.max()
        if (hi - lo < 1e-9) { lo -= 0.5; hi += 0.5 }
        val step = niceStep(hi - lo)
        lo = floor(lo / step) * step; hi = ceil(hi / step) * step
        val x0 = xs.min(); val x1 = max(xs.max(), x0 + 1e-6)
        val left = 40.dp.toPx(); val bottom = size.height - 16.dp.toPx()
        val w = size.width - left
        fun px(x: Double) = (left + (x - x0) / (x1 - x0) * w).toFloat()
        fun py(y: Double) = (bottom - (y - lo) / (hi - lo) * bottom).toFloat()
        val dec = decimalsFor(step)
        var t = lo; var g = 0
        while (t <= hi + step * 0.01 && g++ < 20) {
            val y = py(t)
            drawLine(inst.grid, Offset(left, y), Offset(size.width, y), 1f)
            val lay = measurer.measure(Fmt.num(t, dec), labelStyle)
            drawText(lay, topLeft = Offset(left - lay.size.width - 4.dp.toPx(), (y - lay.size.height / 2).coerceIn(0f, bottom - lay.size.height / 2)))
            t += step
        }
        val xStep = niceStep(x1 - x0, 5)
        var xt = ceil(x0 / xStep) * xStep; g = 0
        while (xt <= x1 && g++ < 20) {
            val x = px(xt)
            drawLine(inst.grid, Offset(x, 0f), Offset(x, bottom), 1f)
            val lay = measurer.measure(Fmt.num(xt, decimalsFor(xStep)) + xSuffix, labelStyle)
            drawText(lay, topLeft = Offset((x - lay.size.width / 2).coerceIn(left, size.width - lay.size.width), bottom + 2.dp.toPx()))
            xt += xStep
        }
        prepared.forEach { (sx, sy, color) ->
            val path = Path()
            var started = false
            for (i in sx.indices) {
                if (sy[i].isNaN()) { started = false; continue }
                if (!started) { path.moveTo(px(sx[i]), py(sy[i])); started = true } else path.lineTo(px(sx[i]), py(sy[i]))
            }
            drawPath(path, color, style = Stroke(width = 1.6.dp.toPx()))
        }
    }
}

/** Defter kartındaki küçük grafik. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val v = values.filter { !it.isNaN() }
        if (v.size < 2) return@Canvas
        val lo = v.min(); val hi = v.max().let { if (it - lo < 1e-9) lo + 1 else it }
        val path = Path()
        var i = 0
        values.forEachIndexed { idx, y ->
            if (y.isNaN()) return@forEachIndexed
            val x = size.width * idx / (values.size - 1)
            val py = (size.height - (y - lo) / (hi - lo) * size.height).toFloat()
            if (i++ == 0) path.moveTo(x, py) else path.lineTo(x, py)
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Ses ölçerdeki dalga şeridi (tepe genlikleri). */
@Composable
fun WaveStrip(series: RollingSeries, color: Color, modifier: Modifier = Modifier, height: Dp = 64.dp) {
    val vals = series.snapshot()
    Canvas(modifier.fillMaxWidth().height(height)) {
        val n = series.capacity
        val bw = size.width / n
        val mid = size.height / 2
        vals.forEachIndexed { i, v ->
            // logaritmik görünüm: sessiz ortamda da hareket görünsün
            val a = (1 + log10(max(v, 1e-3f).toDouble()) / 3).toFloat().coerceIn(0.02f, 1f)
            val x = (i + n - vals.size) * bw + bw / 2
            drawLine(color, Offset(x, mid - a * mid), Offset(x, mid + a * mid), strokeWidth = max(1f, bw * 0.6f))
        }
    }
}

/**
 * Alet kadranı (konsept görselindeki ses ölçer göstergesi gibi). 240° yay; [warnFrom] üstü amber,
 * [alarmFrom] üstü kırmızı.
 */
@Composable
fun ArcGauge(
    value: Double,
    min: Double,
    max: Double,
    modifier: Modifier = Modifier,
    warnFrom: Double? = null,
    alarmFrom: Double? = null,
    majorStep: Double = 10.0,
) {
    val inst = LocalInstrument.current
    val measurer = rememberTextMeasurer()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = inst.scale)
    Canvas(modifier) {
        val stroke = 14.dp.toPx()
        val d = kotlin.math.min(size.width, size.height * 1.18f) - stroke - 28.dp.toPx()
        val tl = Offset((size.width - d) / 2, 18.dp.toPx())
        val start = 150f; val sweep = 240f
        fun frac(v: Double) = ((v - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
        drawArc(track, start, sweep, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        val f = if (value.isNaN()) 0f else frac(value)
        val color = when {
            alarmFrom != null && value >= alarmFrom -> inst.alarm
            warnFrom != null && value >= warnFrom -> inst.warn
            else -> inst.accent
        }
        if (f > 0f) drawArc(color, start, sweep * f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        val c = Offset(tl.x + d / 2, tl.y + d / 2)
        val r = d / 2
        // ölçek çizgileri
        var v = ceil(min / majorStep) * majorStep
        var g = 0
        while (v <= max + 1e-9 && g++ < 40) {
            val a = Math.toRadians((start + sweep * frac(v)).toDouble())
            val p1 = Offset(c.x + (r - stroke) * cos(a).toFloat(), c.y + (r - stroke) * sin(a).toFloat())
            val p2 = Offset(c.x + (r - stroke * 1.6f) * cos(a).toFloat(), c.y + (r - stroke * 1.6f) * sin(a).toFloat())
            drawLine(inst.scale, p1, p2, 2f)
            val lay = measurer.measure(Fmt.num(v, 0), labelStyle)
            val lp = Offset(c.x + (r - stroke * 2.6f) * cos(a).toFloat() - lay.size.width / 2, c.y + (r - stroke * 2.6f) * sin(a).toFloat() - lay.size.height / 2)
            drawText(lay, topLeft = lp)
            v += majorStep
        }
        warnFrom?.let { mark(it, c, r, stroke, start, sweep, ::frac, inst.warn) }
        alarmFrom?.let { mark(it, c, r, stroke, start, sweep, ::frac, inst.alarm) }
    }
}

private fun DrawScope.mark(v: Double, c: Offset, r: Float, stroke: Float, start: Float, sweep: Float, frac: (Double) -> Float, color: Color) {
    val a = Math.toRadians((start + sweep * frac(v)).toDouble())
    val p1 = Offset(c.x + (r + stroke * 0.7f) * cos(a).toFloat(), c.y + (r + stroke * 0.7f) * sin(a).toFloat())
    val p2 = Offset(c.x + (r + stroke * 1.2f) * cos(a).toFloat(), c.y + (r + stroke * 1.2f) * sin(a).toFloat())
    drawLine(color, p1, p2, 3f, cap = StrokeCap.Round)
}

/** Yatay çubuk (lüksmetre önerilen değer karşılaştırması gibi). Logaritmik ölçek isteğe bağlı. */
@Composable
fun MeterBar(value: Double, target: Double, maxValue: Double, color: Color, modifier: Modifier = Modifier, log: Boolean = true) {
    val inst = LocalInstrument.current
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        fun f(v: Double): Float = if (log) (log10(1 + max(v, 0.0)) / log10(1 + maxValue)).toFloat().coerceIn(0f, 1f)
        else (v / maxValue).toFloat().coerceIn(0f, 1f)
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        drawRoundRect(color, size = Size(size.width * f(value), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        val tx = size.width * f(target)
        drawLine(inst.scale, Offset(tx, -2f), Offset(tx, size.height + 2f), 3f)
    }
}

internal fun angleDiff(a: Double, b: Double): Double {
    val d = abs(a - b) % 360
    return min(d, 360 - d)
}

internal const val DEG = PI / 180
