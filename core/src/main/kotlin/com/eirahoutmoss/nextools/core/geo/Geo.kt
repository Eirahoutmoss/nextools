package com.eirahoutmoss.nextools.core.geo

import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Konum hesapları (WGS84 küresi yaklaşımı, R = 6 371 008,8 m). */
object Geo {
    const val R = 6_371_008.8
    const val M2_PER_DONUM = 1000.0
    const val M2_PER_HECTARE = 10_000.0
    const val M2_PER_ACRE = 4046.8564224

    private fun rad(d: Double) = d * PI / 180

    /** İki nokta arası büyük daire mesafesi (m). */
    fun distance(a: LatLon, b: LatLon): Double {
        val dLat = rad(b.lat - a.lat); val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * R * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Çokgen çevresi (kapalı; son nokta ilkine bağlanır). */
    fun perimeter(points: List<LatLon>): Double {
        if (points.size < 2) return 0.0
        var d = 0.0
        for (i in points.indices) d += distance(points[i], points[(i + 1) % points.size])
        return if (points.size == 2) d / 2 else d
    }

    /**
     * Küre üzerinde çokgen alanı (m²): Chamberlain & Duquette (2007) yöntemi.
     * Tarla ve arsa boyutlarında hata binde birden azdır; tapu ölçümü yerine geçmez.
     */
    fun area(points: List<LatLon>): Double {
        if (points.size < 3) return 0.0
        var s = 0.0
        for (i in points.indices) {
            val p1 = points[i]; val p2 = points[(i + 1) % points.size]
            s += rad(p2.lon - p1.lon) * (2 + sin(rad(p1.lat)) + sin(rad(p2.lat)))
        }
        return abs(s * R * R / 2)
    }

    /** Noktaları ilk noktaya göre yerel düzleme (x doğu, y kuzey; metre) çevirir. Çizim için. */
    fun toLocal(points: List<LatLon>): List<Pair<Double, Double>> {
        if (points.isEmpty()) return emptyList()
        val o = points.first()
        val k = cos(rad(o.lat))
        return points.map { Pair(rad(it.lon - o.lon) * R * k, rad(it.lat - o.lat) * R) }
    }
}

/** İz istatistikleri: mesafe, tırmanış (gürültüye karşı eşikli), süre, hız. */
class TrackStats(private val climbThresholdM: Double = 3.0) {
    var distanceM = 0.0; private set
    var ascentM = 0.0; private set
    var descentM = 0.0; private set
    var maxSpeed = 0.0; private set
    var points = 0; private set
    private var last: LatLon? = null
    private var altRef: Double? = null

    /** [accuracyM] kötüyse (>[maxAccuracyM]) nokta yok sayılır; false döner. */
    fun add(p: LatLon, altitudeM: Double?, speedMs: Double?, accuracyM: Double?, maxAccuracyM: Double = 30.0): Boolean {
        if (accuracyM != null && accuracyM > maxAccuracyM) return false
        last?.let { l ->
            val d = Geo.distance(l, p)
            // Durağan hâldeki GPS titremesi mesafeye eklenmesin
            if (d < (accuracyM ?: 5.0) * 0.5) return false
            distanceM += d
        }
        last = p
        points++
        speedMs?.let { if (it > maxSpeed) maxSpeed = it }
        if (altitudeM != null) {
            val ref = altRef
            if (ref == null) altRef = altitudeM
            else if (altitudeM - ref >= climbThresholdM) { ascentM += altitudeM - ref; altRef = altitudeM }
            else if (ref - altitudeM >= climbThresholdM) { descentM += ref - altitudeM; altRef = altitudeM }
        }
        return true
    }
}

/** GPX 1.1 yazıcı (iz). */
object Gpx {
    data class Point(val lat: Double, val lon: Double, val ele: Double?, val timeMs: Long?)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun write(name: String, points: List<Point>, out: Appendable) {
        out.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        out.append("""<gpx version="1.1" creator="NexTools" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        out.append("  <trk><name>").append(esc(name)).append("</name><trkseg>\n")
        for (p in points) {
            out.append("""    <trkpt lat="""").append(String.format(java.util.Locale.ROOT, "%.7f", p.lat))
                .append("""" lon="""").append(String.format(java.util.Locale.ROOT, "%.7f", p.lon)).append("\">")
            p.ele?.let { out.append("<ele>").append(String.format(java.util.Locale.ROOT, "%.1f", it)).append("</ele>") }
            p.timeMs?.let { out.append("<time>").append(Instant.ofEpochMilli(it).toString()).append("</time>") }
            out.append("</trkpt>\n")
        }
        out.append("  </trkseg></trk>\n</gpx>\n")
    }
}
