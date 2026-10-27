package com.eirahoutmoss.nextools.core.measure

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Akan veride en küçük, en büyük, ortalama ve standart sapma (Welford). */
class RunningStats {
    var count: Long = 0
        private set
    var min: Double = Double.NaN
        private set
    var max: Double = Double.NaN
        private set
    private var mean = 0.0
    private var m2 = 0.0

    val average: Double get() = if (count == 0L) Double.NaN else mean
    val stdDev: Double get() = if (count < 2) Double.NaN else sqrt(m2 / (count - 1))

    fun add(v: Double) {
        if (v.isNaN() || v.isInfinite()) return
        count++
        min = if (count == 1L) v else min(min, v)
        max = if (count == 1L) v else max(max, v)
        val d = v - mean
        mean += d / count
        m2 += d * (v - mean)
    }

    fun reset() {
        count = 0; min = Double.NaN; max = Double.NaN; mean = 0.0; m2 = 0.0
    }
}

/**
 * Zaman sabitli birinci derece alçak geçiren süzgeç. Örnekleme aralığı değişse de
 * (Android sensörleri düzensiz gelir) aynı tepki süresini korur.
 */
class LowPass(private val timeConstantS: Double) {
    private var value = Double.NaN
    private var lastNs = 0L

    fun update(v: Double, timestampNs: Long): Double {
        if (value.isNaN() || timeConstantS <= 0) {
            value = v
        } else {
            val dt = ((timestampNs - lastNs) / 1e9).coerceIn(0.0, 1.0)
            val a = 1 - exp(-dt / timeConstantS)
            value += a * (v - value)
        }
        lastNs = timestampNs
        return value
    }

    fun reset() { value = Double.NaN }
}

/** Açılar için alçak geçiren süzgeç: 359° → 1° geçişinde ortalama 180° olmasın diye birim vektörle çalışır. */
class AngleLowPass(private val timeConstantS: Double) {
    private val c = LowPass(timeConstantS)
    private val s = LowPass(timeConstantS)

    fun update(deg: Double, timestampNs: Long): Double {
        val r = deg * PI / 180
        val x = c.update(kotlin.math.cos(r), timestampNs)
        val y = s.update(kotlin.math.sin(r), timestampNs)
        return Angles.normalize360(kotlin.math.atan2(y, x) * 180 / PI)
    }

    fun reset() { c.reset(); s.reset() }
}

object Angles {
    fun normalize360(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** -180..180 aralığına getirir. */
    fun normalize180(deg: Double): Double {
        val r = normalize360(deg)
        return if (r > 180) r - 360 else r
    }
}
