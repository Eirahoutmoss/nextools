package com.eirahoutmoss.nextools.core.measure

import kotlin.math.roundToInt
import kotlin.math.sqrt

object CompassMath {

    /** 16 yön; dil dosyalarında aynı sırayla karşılıkları vardır (K, KKD, KD … / N, NNE, NE …). */
    const val POINTS = 16

    /** Yönün 16'lık pusula gülündeki sırası (0 = kuzey). */
    fun pointIndex(headingDeg: Double): Int =
        ((Angles.normalize360(headingDeg) / (360.0 / POINTS)).roundToInt()) % POINTS

    /** Gerçek kuzey = manyetik kuzey + sapma (doğu sapması pozitif). */
    fun trueHeading(magneticDeg: Double, declinationDeg: Double): Double =
        Angles.normalize360(magneticDeg + declinationDeg)

    /** Yeryüzündeki doğal alan yaklaşık 25–65 µT'dir; dışı yakında metal ya da mıknatıs demektir. */
    const val EARTH_MIN_UT = 25.0
    const val EARTH_MAX_UT = 65.0

    fun magnitude(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    enum class FieldQuality { NORMAL, DISTURBED }

    /**
     * [expectedUt] varsa (konumdan hesaplanan dünya alanı) sapma ona göre, yoksa genel aralığa göre değerlendirilir.
     */
    fun fieldQuality(magnitudeUt: Double, expectedUt: Double?): FieldQuality {
        if (expectedUt != null && expectedUt > 0) {
            val ratio = magnitudeUt / expectedUt
            return if (ratio in 0.8..1.2) FieldQuality.NORMAL else FieldQuality.DISTURBED
        }
        return if (magnitudeUt in EARTH_MIN_UT..EARTH_MAX_UT) FieldQuality.NORMAL else FieldQuality.DISTURBED
    }
}
