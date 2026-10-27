package com.eirahoutmoss.nextools.core.measure

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Renk ölçer hesapları: sRGB → CIE Lab (D65), CIEDE2000 renk farkı, en yakın adlandırılmış renk. */
object ColorMath {
    data class Lab(val l: Double, val a: Double, val b: Double)

    fun hex(r: Int, g: Int, b: Int): String = "#%02X%02X%02X".format(r, g, b)

    private fun lin(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun lab(r: Int, g: Int, b: Int): Lab {
        val rl = lin(r); val gl = lin(g); val bl = lin(b)
        val x = (0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl) / 0.95047
        val y = (0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl) / 1.0
        val z = (0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
        val fx = f(x); val fy = f(y); val fz = f(z)
        return Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    /** CIEDE2000 (Sharma, Wu, Dalal 2005). ~1: zor fark edilir; 2–10: fark edilir; >10: belirgin. */
    fun deltaE2000(p: Lab, q: Lab): Double {
        val c1 = sqrt(p.a * p.a + p.b * p.b); val c2 = sqrt(q.a * q.a + q.b * q.b)
        val cm = (c1 + c2) / 2
        val g = 0.5 * (1 - sqrt(cm.pow(7) / (cm.pow(7) + 25.0.pow(7))))
        val a1 = (1 + g) * p.a; val a2 = (1 + g) * q.a
        val c1p = sqrt(a1 * a1 + p.b * p.b); val c2p = sqrt(a2 * a2 + q.b * q.b)
        fun hue(b: Double, a: Double): Double { if (a == 0.0 && b == 0.0) return 0.0; val h = Math.toDegrees(atan2(b, a)); return if (h < 0) h + 360 else h }
        val h1 = hue(p.b, a1); val h2 = hue(q.b, a2)
        val dl = q.l - p.l
        val dc = c2p - c1p
        var dh = if (c1p * c2p == 0.0) 0.0 else h2 - h1
        if (dh > 180) dh -= 360 else if (dh < -180) dh += 360
        val dH = 2 * sqrt(c1p * c2p) * sin(Math.toRadians(dh / 2))
        val lm = (p.l + q.l) / 2
        val cmp = (c1p + c2p) / 2
        val hm = when {
            c1p * c2p == 0.0 -> h1 + h2
            abs(h1 - h2) <= 180 -> (h1 + h2) / 2
            h1 + h2 < 360 -> (h1 + h2 + 360) / 2
            else -> (h1 + h2 - 360) / 2
        }
        val t = 1 - 0.17 * cos(Math.toRadians(hm - 30)) + 0.24 * cos(Math.toRadians(2 * hm)) +
            0.32 * cos(Math.toRadians(3 * hm + 6)) - 0.20 * cos(Math.toRadians(4 * hm - 63))
        val dTheta = 30 * exp(-((hm - 275) / 25).pow(2))
        val rc = 2 * sqrt(cmp.pow(7) / (cmp.pow(7) + 25.0.pow(7)))
        val sl = 1 + 0.015 * (lm - 50).pow(2) / sqrt(20 + (lm - 50).pow(2))
        val sc = 1 + 0.045 * cmp
        val sh = 1 + 0.015 * cmp * t
        val rt = -sin(Math.toRadians(2 * dTheta)) * rc
        return sqrt((dl / sl).pow(2) + (dc / sc).pow(2) + (dH / sh).pow(2) + rt * (dc / sc) * (dH / sh))
    }

    /** Adlandırılmış renk paleti; adlar uygulamada dil dosyasından ([key]) gelir. */
    data class Named(val key: String, val r: Int, val g: Int, val b: Int)

    val PALETTE = listOf(
        Named("black", 0, 0, 0), Named("white", 255, 255, 255), Named("gray", 128, 128, 128),
        Named("light_gray", 200, 200, 200), Named("dark_gray", 64, 64, 64), Named("red", 220, 30, 30),
        Named("dark_red", 128, 0, 0), Named("pink", 255, 170, 190), Named("orange", 255, 140, 0),
        Named("brown", 120, 70, 30), Named("beige", 225, 205, 170), Named("yellow", 250, 220, 30),
        Named("olive", 128, 128, 0), Named("lime", 160, 220, 40), Named("green", 30, 150, 50),
        Named("dark_green", 0, 90, 30), Named("turquoise", 40, 200, 200), Named("cyan", 0, 220, 240),
        Named("sky_blue", 120, 190, 240), Named("blue", 30, 70, 210), Named("navy", 10, 25, 90),
        Named("purple", 120, 40, 160), Named("lilac", 190, 150, 220), Named("magenta", 210, 40, 160),
        Named("cream", 250, 245, 220), Named("gold", 210, 170, 50),
    )

    fun nearest(r: Int, g: Int, b: Int): Pair<Named, Double> {
        val l = lab(r, g, b)
        return PALETTE.map { it to deltaE2000(l, lab(it.r, it.g, it.b)) }.minBy { it.second }
    }
}
