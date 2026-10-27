package com.eirahoutmoss.nextools.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlin.math.log10

/**
 * Spektrum grafiği: x frekans (logaritmik ya da doğrusal), y dB. [db] kutu başına değer, kutu genişliği [binHz].
 * [hold] verilirse tepe tutma çizgisi soluk çizilir; [markHz] dikey işaret.
 */
@Composable
fun SpectrumChart(
    db: FloatArray,
    binHz: Double,
    minHz: Double,
    maxHz: Double,
    minDb: Float,
    maxDb: Float,
    modifier: Modifier = Modifier,
    logX: Boolean = true,
    hold: FloatArray? = null,
    markHz: Double? = null,
    height: Dp = 180.dp,
) {
    val inst = LocalInstrument.current
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 9.sp, color = inst.scale)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val left = 30.dp.toPx(); val bottom = size.height - 14.dp.toPx()
        val w = size.width - left
        fun x(hz: Double): Float = if (logX) (left + (log10(hz / minHz) / log10(maxHz / minHz)) * w).toFloat()
        else (left + (hz - minHz) / (maxHz - minHz) * w).toFloat()
        fun y(v: Float): Float = bottom - ((v - minDb) / (maxDb - minDb)).coerceIn(0f, 1f) * bottom
        var d = maxDb
        while (d >= minDb) {
            drawLine(inst.grid, Offset(left, y(d)), Offset(size.width, y(d)), 1f)
            val l = measurer.measure(Fmt.num(d.toDouble(), 0), label)
            drawText(l, topLeft = Offset(left - l.size.width - 3.dp.toPx(), y(d) - l.size.height / 2))
            d -= 20f
        }
        val ticks = if (logX) listOf(20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 0.5, 1.0, 2.0, 5.0, 10.0)
        else (0..10).map { minHz + (maxHz - minHz) * it / 10 }
        ticks.filter { it in minHz..maxHz }.forEach { hz ->
            val px = x(hz)
            drawLine(inst.grid, Offset(px, 0f), Offset(px, bottom), 1f)
            val txt = if (hz >= 1000) Fmt.num(hz / 1000, 0) + "k" else Fmt.num(hz, if (hz < 10) 1 else 0)
            val l = measurer.measure(txt, label)
            drawText(l, topLeft = Offset((px - l.size.width / 2).coerceIn(left, size.width - l.size.width), bottom + 2.dp.toPx()))
        }
        fun path(values: FloatArray): Path {
            val p = Path(); var started = false
            for (k in 1 until values.size) {
                val hz = k * binHz
                if (hz < minHz || hz > maxHz) continue
                val px = x(hz); val py = y(values[k])
                if (!started) { p.moveTo(px, py); started = true } else p.lineTo(px, py)
            }
            return p
        }
        hold?.let { drawPath(path(it), inst.warn.copy(alpha = 0.5f), style = Stroke(1.dp.toPx())) }
        drawPath(path(db), inst.accent, style = Stroke(1.5.dp.toPx()))
        markHz?.let { if (it in minHz..maxHz) drawLine(inst.scale, Offset(x(it), 0f), Offset(x(it), bottom), 1.5f) }
    }
}
