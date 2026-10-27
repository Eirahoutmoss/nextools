package com.eirahoutmoss.nextools.core.measure

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Duvardaki çivi, profil, demir boru gibi ferromanyetik cisimleri manyetometreyle arar
 * (NexYapı "Matkaptan Önce" modülündeki yöntemin sadeleştirilmiş hâli).
 *
 * Yöntem: boş sayılan bir noktada ortam alanının VEKTÖRÜ taban alınır. Tarama sırasında
 * ölçülen vektör ile taban arasındaki farkın büyüklüğü "sapma"dır. Cisim alanın yönünü de
 * bozduğu için yalnızca büyüklük değil vektör farkı kullanılır.
 *
 * Sınırlar: bakır / plastik boru ve akımsız kablo manyetik değildir, bulunamaz.
 * Telefon döndürülürse taban geçersizleşir.
 */
class MetalDetector(
    /** Yumuşatma katsayısı (0..1): küçük değer daha sakin, daha yavaş gösterge. */
    private val smoothing: Double = 0.25,
) {
    data class Baseline(val x: Double, val y: Double, val z: Double, val noise: Double) {
        val magnitude: Double get() = sqrt(x * x + y * y + z * z)
    }

    data class Reading(
        /** Yumuşatılmış sapma (µT). */
        val anomaly: Double,
        /** 0..1 gösterge ("sıcak-soğuk"). */
        val level: Double,
        val detected: Boolean,
        /** Bu taramadaki en yüksek sapma (µT). */
        val peak: Double,
    )

    /** Kalibrasyon sonucu. Hata metinleri uygulamada dil dosyasından seçilir. */
    sealed interface Calibration {
        data class Ok(val baseline: Baseline) : Calibration
        data object TooFewSamples : Calibration
        data object Unsteady : Calibration
        data class UnusualField(val magnitudeUt: Double) : Calibration
    }

    var baseline: Baseline? = null
        private set

    private var smoothed = 0.0
    private var peak = 0.0

    /** En az ~1 saniyelik örnekle taban alanı hesaplar. Telefon hareket ettiyse reddeder. */
    fun calibrate(samples: List<DoubleArray>): Calibration {
        if (samples.size < 20) return Calibration.TooFewSamples
        val mx = samples.sumOf { it[0] } / samples.size
        val my = samples.sumOf { it[1] } / samples.size
        val mz = samples.sumOf { it[2] } / samples.size
        var ss = 0.0
        for (s in samples) {
            val dx = s[0] - mx; val dy = s[1] - my; val dz = s[2] - mz
            ss += dx * dx + dy * dy + dz * dz
        }
        val noise = sqrt(ss / samples.size)
        val mag = sqrt(mx * mx + my * my + mz * mz)
        if (noise > 3.0) return Calibration.Unsteady
        if (mag < 15 || mag > 120) return Calibration.UnusualField(mag)
        val b = Baseline(mx, my, mz, noise)
        baseline = b
        resetPeak()
        smoothed = 0.0
        return Calibration.Ok(b)
    }

    fun clear() {
        baseline = null; smoothed = 0.0; peak = 0.0
    }

    fun resetPeak() { peak = 0.0 }

    /** [sensitivity] 0..1: yüksek değer küçük sapmaları da gösterir (tavan düşer). */
    var sensitivity: Double = 0.5

    /** Eşik: gürültünün 6 katı ama en az 2 µT. */
    val threshold: Double get() = max(2.0, (baseline?.noise ?: 0.5) * 6)

    /** Gösterge tavanı (µT): duyarlılığa göre 15..105 µT. */
    val ceiling: Double get() = 105.0 - 90.0 * sensitivity.coerceIn(0.0, 1.0)

    fun update(x: Double, y: Double, z: Double): Reading? {
        val b = baseline ?: return null
        val dx = x - b.x; val dy = y - b.y; val dz = z - b.z
        val raw = sqrt(dx * dx + dy * dy + dz * dz)
        smoothed += smoothing * (raw - smoothed)
        peak = max(peak, smoothed)
        val floor = threshold * 0.5
        val level = ((smoothed - floor) / (ceiling - floor)).coerceIn(0.0, 1.0)
        return Reading(smoothed, level, smoothed >= threshold, peak)
    }

    companion object {
        /** "Sıcak-soğuk" bip aralığı (ms): gösterge dolu iken 60 ms, boşken 900 ms. */
        fun beepIntervalMs(level: Double): Long = (900 - 840 * level.coerceIn(0.0, 1.0)).toLong()
    }
}
