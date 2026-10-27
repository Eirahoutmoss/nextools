package com.eirahoutmoss.nextools.core.measure

/** Sensör olaylarının zaman damgalarından gerçek örnekleme hızını (Hz) ölçer. */
class RateMeter(private val windowNs: Long = 2_000_000_000L) {
    private val stamps = ArrayDeque<Long>()

    fun add(timestampNs: Long) {
        stamps.addLast(timestampNs)
        while (stamps.size > 2 && stamps.last() - stamps.first() > windowNs) stamps.removeFirst()
    }

    /** En az 0,5 s veri yoksa null. */
    val hz: Double?
        get() {
            if (stamps.size < 3) return null
            val span = stamps.last() - stamps.first()
            if (span < windowNs / 4) return null
            return (stamps.size - 1) * 1e9 / span
        }

    fun reset() = stamps.clear()
}

/**
 * Sensör gürültüsü: telefon kıpırdatılmadan toplanan örneklerin her eksendeki standart sapmasının
 * karelerinin toplamının karekökü (birim sensörün birimi).
 */
object Noise {
    fun rms(samples: List<DoubleArray>): Double? {
        if (samples.size < 10) return null
        val dims = samples.first().size
        var total = 0.0
        for (d in 0 until dims) {
            val s = RunningStats()
            samples.forEach { s.add(it[d]) }
            val sd = s.stdDev
            if (!sd.isNaN()) total += sd * sd
        }
        return kotlin.math.sqrt(total)
    }
}
