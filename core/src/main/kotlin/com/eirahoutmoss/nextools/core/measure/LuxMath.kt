package com.eirahoutmoss.nextools.core.measure

/**
 * Lüksmetre. Telefonun ışık sensörü ekran parlaklığı için yapılmıştır; mutlak değeri ±%20 (bazen daha
 * fazla) sapabilir. Bilinen bir ölçerle karşılaştırılarak çarpan kalibrasyonu yapılabilir.
 */
object LuxMath {

    /** Referans ölçerin gösterdiği [referenceLux] ile telefonun ham değerinden çarpan. */
    fun calibrationFactor(referenceLux: Double, rawLux: Double): Double? {
        if (referenceLux <= 0 || rawLux <= 0.5) return null
        val f = referenceLux / rawLux
        return if (f in 0.1..10.0) f else null
    }

    const val LUX_PER_FOOT_CANDLE = 10.763910416709722

    /**
     * Önerilen aydınlatma düzeyleri (EN 12464-1 yaklaşık değerleri). Ad uygulamada dil dosyasından gelir.
     */
    enum class Recommendation(val lux: Int) {
        CORRIDOR(100),
        STAIRS(150),
        LIVING_ROOM(200),
        CLASSROOM(300),
        READING(500),
        OFFICE(500),
        TECHNICAL_DRAWING(750),
        PRECISION_WORK(1000),
    }

    enum class Verdict { LOW, OK, HIGH }

    /** Önerilen değerin %80'inden azı yetersiz, 3 katından fazlası çok parlak sayılır. */
    fun verdict(lux: Double, r: Recommendation): Verdict = when {
        lux < r.lux * 0.8 -> Verdict.LOW
        lux > r.lux * 3.0 -> Verdict.HIGH
        else -> Verdict.OK
    }
}
