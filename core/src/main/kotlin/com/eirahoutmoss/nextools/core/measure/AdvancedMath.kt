package com.eirahoutmoss.nextools.core.measure

import com.eirahoutmoss.nextools.core.geo.Geo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Hassas Konum: aynı noktada beklerken gelen konumların ortalaması ve dağılım elipsi.
 * GNSS hataları dakikalar boyunca ilişkilidir; bu yüzden ortalamanın belirsizliği σ/√n değil,
 * "bağımsız örnek" sayısı süre / [correlationS] alınarak hesaplanır.
 */
class PositionAverager(private val correlationS: Double = 60.0) {
    data class Ellipse(
        /** Büyük ve küçük yarı eksen (m, 1σ). */
        val majorM: Double,
        val minorM: Double,
        /** Büyük eksenin kuzeyden saat yönünde açısı (°). */
        val orientationDeg: Double,
    ) {
        /** %95 için 2B ölçek: √(χ²₂,0.95) ≈ 2,448. */
        fun scaled(k: Double = 2.4477) = Ellipse(majorM * k, minorM * k, orientationDeg)
    }

    data class Result(
        val lat: Double,
        val lon: Double,
        val altitudeM: Double?,
        val count: Int,
        val durationS: Double,
        /** Tek konumların dağılımı (1σ). */
        val scatter: Ellipse,
        /** Ortalama konumun tahmini belirsizliği (1σ yarıçap, m). */
        val meanUncertaintyM: Double,
        /** Yükseklik dağılımı (1σ, m). */
        val altitudeSdM: Double?,
    )

    private var lat0 = Double.NaN
    private var lon0 = Double.NaN
    private var t0 = 0.0
    private var tLast = 0.0
    private var sw = 0.0
    private var sx = 0.0; private var sy = 0.0
    private var sxx = 0.0; private var syy = 0.0; private var sxy = 0.0
    private val alt = RunningStats()
    var count = 0; private set

    fun reset() { lat0 = Double.NaN; sw = 0.0; sx = 0.0; sy = 0.0; sxx = 0.0; syy = 0.0; sxy = 0.0; alt.reset(); count = 0 }

    /** [accuracyM]: Android'in bildirdiği yatay doğruluk; ağırlık 1/σ². [timeS]: saniye. */
    fun add(lat: Double, lon: Double, altitudeM: Double?, accuracyM: Double, timeS: Double) {
        if (lat0.isNaN()) { lat0 = lat; lon0 = lon; t0 = timeS }
        tLast = timeS
        val k = cos(lat0 * PI / 180)
        val x = (lon - lon0) * PI / 180 * Geo.R * k
        val y = (lat - lat0) * PI / 180 * Geo.R
        val w = 1.0 / max(accuracyM, 0.5).let { it * it }
        sw += w; sx += w * x; sy += w * y; sxx += w * x * x; syy += w * y * y; sxy += w * x * y
        altitudeM?.let { alt.add(it) }
        count++
    }

    fun result(): Result? {
        if (count < 2 || sw <= 0) return if (count == 1) Result(lat0, lon0, alt.average.takeIf { alt.count > 0 }, 1, 0.0, Ellipse(0.0, 0.0, 0.0), Double.NaN, null) else null
        val mx = sx / sw; val my = sy / sw
        val cxx = max(0.0, sxx / sw - mx * mx); val cyy = max(0.0, syy / sw - my * my); val cxy = sxy / sw - mx * my
        // Kovaryans matrisinin özdeğerleri
        val tr = cxx + cyy; val det = cxx * cyy - cxy * cxy
        val disc = sqrt(max(0.0, tr * tr / 4 - det))
        val l1 = tr / 2 + disc; val l2 = max(0.0, tr / 2 - disc)
        // Büyük eksen yönü (x doğu, y kuzey) → kuzeyden saat yönünde
        val angEast = 0.5 * atan2(2 * cxy, cxx - cyy)
        val bearing = ((90 - angEast * 180 / PI) % 180 + 180) % 180
        val k = cos(lat0 * PI / 180)
        val lat = lat0 + my / Geo.R * 180 / PI
        val lon = lon0 + mx / (Geo.R * k) * 180 / PI
        val duration = tLast - t0
        val nEff = max(1.0, min(count.toDouble(), 1 + duration / correlationS))
        val radial = sqrt(l1 + l2)
        return Result(
            lat, lon, alt.average.takeIf { alt.count > 0 }, count, duration,
            Ellipse(sqrt(l1), sqrt(l2), bearing), radial / sqrt(nEff),
            alt.stdDev.takeIf { alt.count > 1 },
        )
    }
}

/**
 * Sahte GPS / parazit işaretleri. Her işaret tek başına kanıt değildir; birkaç işaret birlikte
 * görülürse şüphe artar.
 */
object SpoofCheck {
    enum class Sign {
        /** Android konumu "sahte konum uygulaması" işaretiyle bildiriyor. */
        MOCK_LOCATION,
        /** Uyduların sinyal gücü olağandışı benzer (simülatör tek antenden yayınlar). */
        UNIFORM_CN0,
        /** Sinyaller açık gökyüzünde bile görülmeyecek kadar güçlü. */
        HIGH_CN0,
        /** Konum, hızla açıklanamayacak bir sıçrama yaptı. */
        POSITION_JUMP,
        /** GNSS saati telefon saatinden çok farklı. */
        TIME_MISMATCH,
        /** Alıcı saatinde süreksizlik (saat sıfırlandı / sıçradı). */
        CLOCK_JUMP,
        /** Otomatik kazanç (AGC) belirgin düştü: güçlü bir verici yakında olabilir (karıştırma). */
        AGC_DROP,
        /** Yalnızca tek bir uydu sisteminden sinyal geliyor (çok sistemli alıcıda olağandışı). */
        SINGLE_CONSTELLATION,
    }

    enum class Level { OK, NOTICE, WARNING }

    /** C/N0 listesinden (dB-Hz) benzerlik ve güç işaretleri. En az 6 uydu gerekir. */
    fun cn0Signs(cn0: List<Double>): List<Sign> {
        if (cn0.size < 6) return emptyList()
        val s = RunningStats(); cn0.forEach { s.add(it) }
        val out = mutableListOf<Sign>()
        if (s.stdDev < 1.5 && s.average > 30) out += Sign.UNIFORM_CN0
        if (s.average > 50 || cn0.count { it > 52 } >= 4) out += Sign.HIGH_CN0
        return out
    }

    /** İki konum arası hız (m/s) [maxSpeed] üstündeyse sıçrama. */
    fun isJump(distanceM: Double, dtS: Double, accuracyM: Double, maxSpeed: Double = 150.0): Boolean =
        dtS > 0 && distanceM > 3 * accuracyM + 50 && distanceM / dtS > maxSpeed

    fun isTimeMismatch(gnssMs: Long, systemMs: Long, toleranceMs: Long = 10_000): Boolean = abs(gnssMs - systemMs) > toleranceMs

    /** AGC taban değerine göre [dropDb] dB'den fazla düşüş. */
    fun isAgcDrop(baselineDb: Double, nowDb: Double, dropDb: Double = 6.0): Boolean = baselineDb - nowDb > dropDb

    fun level(signs: Collection<Sign>): Level = when {
        Sign.MOCK_LOCATION in signs -> Level.WARNING
        signs.size >= 2 -> Level.WARNING
        signs.size == 1 -> Level.NOTICE
        else -> Level.OK
    }
}

/**
 * Spektroskop: görüntüden çıkarılan parlaklık profili, tepe bulma ve iki noktalı dalga boyu kalibrasyonu.
 */
object Spectro {
    /** Doğrusal kalibrasyon: nm = a·piksel + b. */
    data class Calibration(val a: Double, val b: Double) {
        fun nm(px: Double) = a * px + b
        fun px(nm: Double) = (nm - b) / a

        companion object {
            fun fromTwo(px1: Double, nm1: Double, px2: Double, nm2: Double): Calibration? {
                if (abs(px2 - px1) < 5 || abs(nm2 - nm1) < 20) return null
                val a = (nm2 - nm1) / (px2 - px1)
                return Calibration(a, nm1 - a * px1)
            }
        }
    }

    /** Kompakt floresan / cıva çizgileri (nm): kalibrasyon için bilinen tepeler. */
    val REFERENCE_LINES = listOf(405.4, 436.6, 487.7, 546.5, 611.6)

    /** [x] içinde [around] çevresinde ±[radius] aralıktaki en yüksek noktanın alt-piksel konumu (parabol). */
    fun peakNear(x: DoubleArray, around: Int, radius: Int = 15): Double? {
        if (x.size < 3) return null
        val from = max(1, around - radius); val to = min(x.size - 2, around + radius)
        if (from > to) return null
        var best = from
        for (i in from..to) if (x[i] > x[best]) best = i
        return subPixel(x, best)
    }

    private fun subPixel(x: DoubleArray, i: Int): Double {
        if (i <= 0 || i >= x.size - 1) return i.toDouble()
        val a = x[i - 1]; val b = x[i]; val c = x[i + 1]
        val d = a - 2 * b + c
        return if (d >= 0) i.toDouble() else i + 0.5 * (a - c) / d
    }

    /** Belirgin tepeler: komşularından büyük ve en yükseğin [minRel] katından yüksek; en fazla [max] tane. */
    fun peaks(x: DoubleArray, minRel: Double = 0.15, max: Int = 8, minGap: Int = 6): List<Double> {
        if (x.size < 5) return emptyList()
        val top = x.max(); val bottom = x.min()
        val thr = bottom + (top - bottom) * minRel
        val cand = (2 until x.size - 2).filter { i -> x[i] >= thr && x[i] >= x[i - 1] && x[i] > x[i + 1] && x[i] >= x[i - 2] && x[i] > x[i + 2] }
            .sortedByDescending { x[it] }
        val kept = mutableListOf<Int>()
        for (c in cand) { if (kept.none { abs(it - c) < minGap }) kept += c; if (kept.size >= max) break }
        return kept.sorted().map { subPixel(x, it) }
    }

    /** Profili 0..1 aralığına getirir. */
    fun normalize(x: DoubleArray): DoubleArray {
        val lo = x.min(); val hi = x.max()
        return if (hi - lo <= 0) DoubleArray(x.size) else DoubleArray(x.size) { (x[it] - lo) / (hi - lo) }
    }

    /** İki normalize profilin benzerliği (Pearson, 0..1). */
    fun similarity(a: DoubleArray, b: DoubleArray): Double {
        val n = min(a.size, b.size); if (n < 3) return 0.0
        val ma = a.take(n).average(); val mb = b.take(n).average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in 0 until n) { num += (a[i] - ma) * (b[i] - mb); da += (a[i] - ma) * (a[i] - ma); db += (b[i] - mb) * (b[i] - mb) }
        return if (da <= 0 || db <= 0) 0.0 else (num / sqrt(da * db)).coerceIn(0.0, 1.0)
    }
}

/**
 * Kozmik ışın sayacı: objektifi kapalı kamerada tek tük parlak pikseller.
 * İlk karelerde sürekli parlayan "sıcak pikseller" öğrenilir ve maskelenir.
 */
class CosmicDetector(
    private val width: Int,
    private val height: Int,
    /** Isınma: ilk kaç kare maske öğrenmeye ayrılır. */
    private val learnFrames: Int = 30,
    /** Arka plan üstü eşik (0..255 parlaklık). */
    private val threshold: Int = 40,
) {
    private val hotCount = IntArray(width * height)
    private val mask = BooleanArray(width * height)
    var frames = 0; private set
    var maskedPixels = 0; private set
    val learning get() = frames < learnFrames

    data class Hit(val x: Int, val y: Int, val peak: Int, val size: Int)

    /** Bir parlaklık karesini işler; öğrenme bittiyse olayları döndürür. Arka plan = karenin medyan yaklaşımı. */
    fun process(luma: ByteArray, rowStride: Int, pixelStride: Int = 1): List<Hit> {
        frames++
        // Arka plan: seyrek örnekle ortalama
        var s = 0L; var n = 0
        var j = 0
        while (j < height) { var i = 0; while (i < width) { s += luma[j * rowStride + i * pixelStride].toInt() and 0xFF; n++; i += 16 }; j += 16 }
        val bg = if (n == 0) 0 else (s / n).toInt()
        val thr = bg + threshold
        val hits = mutableListOf<Hit>()
        val seen = HashSet<Int>()
        for (y in 0 until height) {
            val row = y * rowStride
            for (x in 0 until width) {
                val v = luma[row + x * pixelStride].toInt() and 0xFF
                if (v <= thr) continue
                val idx = y * width + x
                if (frames <= learnFrames) { if (++hotCount[idx] >= 2 && !mask[idx]) { mask[idx] = true; maskedPixels++ }; continue }
                if (mask[idx] || idx in seen) continue
                // Küçük bölge büyütme (en fazla 7×7): aynı izin piksellerini tek olay say
                var peak = v; var size = 0
                for (dy in -3..3) for (dx in -3..3) {
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until width || yy !in 0 until height) continue
                    val k = yy * width + xx
                    val vv = luma[yy * rowStride + xx * pixelStride].toInt() and 0xFF
                    if (vv > thr && !mask[k]) { seen += k; size++; if (vv > peak) peak = vv }
                }
                hits += Hit(x, y, peak, size)
            }
        }
        if (frames == learnFrames) { /* öğrenme bitti */ }
        return hits
    }

    /** Ortalama parlaklık bu değeri aşarsa objektif tam kapanmamıştır. */
    fun isLightLeak(meanLuma: Int) = meanLuma > 25
}

/** Mesafe ölçümleri (RTT, UWB) için kayan medyan ve dağılım. */
class RangeFilter(private val size: Int = 10) {
    private val buf = ArrayDeque<Double>()
    fun add(v: Double) { buf.addLast(v); while (buf.size > size) buf.removeFirst() }
    fun clear() = buf.clear()
    val median: Double? get() = if (buf.isEmpty()) null else buf.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
    val sd: Double? get() = if (buf.size < 2) null else RunningStats().also { s -> buf.forEach { s.add(it) } }.stdDev
}

/**
 * UWB eşleşme bilgisi: ana cihaz (controller) QR'da bunu gösterir, karşı cihaz okur.
 * Biçim: "NXUWB1;adr=hex;ch=9;pi=10;sid=1234;key=hex16"
 */
data class UwbPairing(val address: ByteArray, val channel: Int, val preamble: Int, val sessionId: Int, val sessionKey: ByteArray) {
    fun encode(): String = "NXUWB1;adr=${address.hex()};ch=$channel;pi=$preamble;sid=$sessionId;key=${sessionKey.hex()}"

    override fun equals(other: Any?) = other is UwbPairing && address.contentEquals(other.address) && channel == other.channel &&
        preamble == other.preamble && sessionId == other.sessionId && sessionKey.contentEquals(other.sessionKey)
    override fun hashCode() = sessionId

    companion object {
        fun decode(s: String): UwbPairing? {
            val parts = s.trim().split(";")
            if (parts.firstOrNull() != "NXUWB1") return null
            val m = parts.drop(1).mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return try {
                UwbPairing(
                    unhex(m.getValue("adr")), m.getValue("ch").toInt(), m.getValue("pi").toInt(),
                    m.getValue("sid").toInt(), unhex(m.getValue("key")),
                ).takeIf { it.address.size in 2..8 && it.sessionKey.size == 8 }
            } catch (_: Exception) { null }
        }

        fun ByteArray.hex() = joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        fun unhex(s: String): ByteArray {
            val t = s.trim().uppercase()
            require(t.length % 2 == 0 && t.all { it in "0123456789ABCDEF" })
            return ByteArray(t.length / 2) { t.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }
}
