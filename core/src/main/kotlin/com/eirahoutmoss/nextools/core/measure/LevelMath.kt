package com.eirahoutmoss.nextools.core.measure

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Su terazisi / eğim ölçer hesapları. Girdi Android eksenlerinde yerçekimi vektörüdür
 * (telefon ekranı yukarı bakarak düz yatarken ≈ (0, 0, +9,81)).
 *
 * İki kip:
 *  - Yüzey (FLAT): telefon yüzeyin üstünde yatar; iki eksenli kabarcık.
 *  - Kenar (EDGE): telefon kenarı üstünde dik durur; ekran düzleminde tek açı.
 */
object LevelMath {

    enum class Mode { FLAT, EDGE }

    /** Yüzey kipinde iki eksen (°). x > 0: sağ kenar yüksek; y > 0: üst kenar yüksek. */
    data class Flat(val x: Double, val y: Double, val tilt: Double)

    /** Kenar kipinde: [angle] ekran düzlemindeki tam açı, [deviation] en yakın 0/90/180/270'ten sapma. */
    data class Edge(val angle: Double, val deviation: Double, val nearest: Int)

    private const val RAD = 180.0 / PI

    fun flat(gx: Double, gy: Double, gz: Double): Flat {
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        val tilt = if (g > 0) acos((gz / g).coerceIn(-1.0, 1.0)) * RAD else 0.0
        return Flat(
            x = atan2(gx, gz) * RAD,
            y = atan2(gy, gz) * RAD,
            tilt = tilt,
        )
    }

    fun edge(gx: Double, gy: Double): Edge {
        val angle = Angles.normalize360(atan2(gx, gy) * RAD)
        val nearest = ((angle / 90.0).roundToInt() % 4) * 90
        val dev = Angles.normalize180(angle - nearest)
        return Edge(angle = angle, deviation = dev, nearest = nearest)
    }

    /** Telefonun düz yataydan ne kadar eğik olduğu (°). Kip seçimi için. */
    fun tiltFromFlat(gx: Double, gy: Double, gz: Double): Double {
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        if (g <= 0) return 0.0
        return acos((abs(gz) / g).coerceIn(-1.0, 1.0)) * RAD
    }

    /** Kip seçimi, ara bölgede titreşmemesi için gecikmeli (histerezis). */
    fun chooseMode(current: Mode, tiltFromFlat: Double): Mode = when (current) {
        Mode.FLAT -> if (tiltFromFlat > 50) Mode.EDGE else Mode.FLAT
        Mode.EDGE -> if (tiltFromFlat < 40) Mode.FLAT else Mode.EDGE
    }

    /** Açıdan eğim yüzdesi (tan × 100). 90°'ye yakın değerlerde anlamsız büyür; null döner. */
    fun percent(deg: Double): Double? {
        if (abs(deg) >= 89.0) return null
        return tan(deg / RAD) * 100
    }

    /** Ekranda "düz" sayılacak eşik (°). */
    const val LEVEL_THRESHOLD = 0.3
}
