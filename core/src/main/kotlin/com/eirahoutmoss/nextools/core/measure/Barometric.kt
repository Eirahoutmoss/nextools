package com.eirahoutmoss.nextools.core.measure

import kotlin.math.pow

/**
 * Basınç ve yükseklik. Uluslararası standart atmosfer (ISA) formülü:
 *   h = 44330 × (1 − (p / p0)^(1/5,255))
 * Telefon barometresi mutlak değerde birkaç hPa sapabilir, ama KISA süreli DEĞİŞİMİ çok iyi ölçer
 * (≈0,1 hPa ≈ 1 m). Bu yüzden altimetre bir referansa göre bağıl çalışır.
 */
object Barometric {

    const val STANDARD_SEA_LEVEL_HPA = 1013.25
    private const val EXP = 1 / 5.255

    /** [p0] deniz seviyesi basıncına göre yükseklik (m). */
    fun altitude(pressureHpa: Double, p0: Double = STANDARD_SEA_LEVEL_HPA): Double =
        44330.0 * (1 - (pressureHpa / p0).pow(EXP))

    /** Bilinen yükseklikteki ölçümden deniz seviyesine indirgenmiş basınç (QNH benzeri). */
    fun seaLevelPressure(pressureHpa: Double, altitudeM: Double): Double =
        pressureHpa / (1 - altitudeM / 44330.0).pow(5.255)

    /**
     * Referans noktası: [pressureHpa] ölçülürken yükseklik [altitudeM] idi.
     * Şimdiki yükseklik, iki basıncın standart atmosferdeki farkı kadar değişmiştir.
     */
    data class Reference(val pressureHpa: Double, val altitudeM: Double, val setAtMs: Long)

    fun altitudeFrom(ref: Reference, pressureHpa: Double): Double =
        ref.altitudeM + (altitude(pressureHpa) - altitude(ref.pressureHpa))

    /** Hava basıncı eğilimi (3 saatte hPa değişimi) — basit kural, hava tahmini değildir. */
    enum class Trend { RISING_FAST, RISING, STEADY, FALLING, FALLING_FAST }

    fun trend(deltaHpaPer3h: Double): Trend = when {
        deltaHpaPer3h >= 3.0 -> Trend.RISING_FAST
        deltaHpaPer3h >= 1.0 -> Trend.RISING
        deltaHpaPer3h > -1.0 -> Trend.STEADY
        deltaHpaPer3h > -3.0 -> Trend.FALLING
        else -> Trend.FALLING_FAST
    }

    /**
     * Zaman damgalı basınç dizisinden son 3 saatteki değişim (hPa/3 sa). En az 1 saatlik veri gerekir;
     * daha kısa ise null. Veri 3 saatten kısaysa orantılanır.
     */
    fun deltaPer3h(samples: List<Pair<Long, Double>>, nowMs: Long): Double? {
        val window = samples.filter { it.first >= nowMs - 3 * HOUR_MS && it.first <= nowMs }.sortedBy { it.first }
        if (window.size < 2) return null
        val span = window.last().first - window.first().first
        if (span < HOUR_MS) return null
        // Uçlardaki gürültüyü azaltmak için baştan ve sondan birkaç örneğin ortalaması.
        val k = maxOf(1, window.size / 10)
        val first = window.take(k).map { it.second }.average()
        val last = window.takeLast(k).map { it.second }.average()
        return (last - first) * (3.0 * HOUR_MS / span)
    }

    const val HOUR_MS = 3_600_000L
}
