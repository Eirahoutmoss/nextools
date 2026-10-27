package com.eirahoutmoss.nextools.core.measure

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Metronom ölçüsü: vuruşların gruplanması (ör. 9/8 = 2+2+2+3). */
data class Meter(val key: String, val groups: List<Int>, val unit: Int) {
    val pulses: Int get() = groups.sum()
    val signature: String get() = "$pulses/$unit"
    val grouping: String get() = groups.joinToString("+")

    /** Darbe türü: 2 = ölçü başı, 1 = grup başı, 0 = ara. */
    fun accent(pulse: Int): Int {
        val p = pulse % pulses
        if (p == 0) return 2
        var s = 0
        for (g in groups) { if (p == s) return 1; s += g }
        return 0
    }

    companion object {
        /** Yaygın ölçüler ve aksak usuller. [key] metin dosyasındaki adı seçer. */
        val PRESETS = listOf(
            Meter("m2_4", listOf(1, 1), 4),
            Meter("m3_4", listOf(1, 1, 1), 4),
            Meter("m4_4", listOf(1, 1, 1, 1), 4),
            Meter("m6_8", listOf(3, 3), 8),
            Meter("turk_aksagi", listOf(2, 3), 8),
            Meter("devri_hindi", listOf(3, 2, 2), 8),
            Meter("devri_turan", listOf(2, 2, 3), 8),
            Meter("aksak", listOf(2, 2, 2, 3), 8),
            Meter("aksak_semai", listOf(3, 2, 2, 3), 8),
        )

        /** "2+2+3" gibi gruplamayı çözer (1..9 arası, en fazla 12 grup, toplam ≤ 32). */
        fun parse(text: String, unit: Int = 8): Meter? {
            val g = text.split('+', ' ', ',').filter { it.isNotBlank() }.map { it.trim().toIntOrNull() ?: return null }
            if (g.isEmpty() || g.size > 12 || g.any { it !in 1..9 } || g.sum() > 32) return null
            return Meter("custom", g, unit)
        }
    }
}

/**
 * Metronom sesi: her darbede kısa, sönümlü bir "tık". Vurgu yüksekliği ve şiddeti darbe türüne göre değişir.
 * [render] ses iş parçacığından çağrılır; [onPulse] yeni darbenin sırasını bildirir.
 */
class MetronomeSynth(private val fs: Int) {
    @Volatile var meter: Meter = Meter.PRESETS[2]
    /** Darbe hızı (dakikada darbe). Aksak ölçülerde sekizlik başına. */
    @Volatile var pulsesPerMinute: Double = 120.0
    @Volatile var accentOn: Boolean = true
    var onPulse: (Int) -> Unit = {}

    private var sampleInPulse = 0L
    private var pulse = -1
    private var clickPhase = 0.0
    private var clickFreq = 0.0
    private var clickAmp = 0.0
    private var clickT = Int.MAX_VALUE

    fun reset() { sampleInPulse = 0; pulse = -1; clickT = Int.MAX_VALUE }

    fun render(out: FloatArray) {
        val period = (fs * 60.0 / pulsesPerMinute.coerceIn(20.0, 600.0)).toLong()
        for (i in out.indices) {
            if (pulse < 0 || sampleInPulse >= period) {
                pulse = (pulse + 1) % meter.pulses
                sampleInPulse = 0
                val a = if (accentOn) meter.accent(pulse) else 0
                clickFreq = when (a) { 2 -> 1760.0; 1 -> 1320.0; else -> 880.0 }
                clickAmp = when (a) { 2 -> 0.9; 1 -> 0.7; else -> 0.45 }
                clickT = 0; clickPhase = 0.0
                onPulse(pulse)
            }
            var v = 0.0
            if (clickT < fs / 25) {
                v = clickAmp * sin(clickPhase) * exp(-clickT / (fs * 0.008))
                clickPhase += 2 * PI * clickFreq / fs
                clickT++
            }
            out[i] = v.toFloat()
            sampleInPulse++
        }
    }
}

/** Dönüş ölçer: telefon dönen yüzeyde düz yatarken z eksenindeki açısal hız → devir/dakika. */
object Tachometer {
    fun rpm(omegaRadS: Double): Double = abs(omegaRadS) * 60 / (2 * PI)

    /** Jiroskobun ölçebildiği en yüksek devir (ölçüm aralığı rad/s'den). */
    fun maxRpm(maxRangeRadS: Double): Double = rpm(maxRangeRadS)

    /** Telefon düz değilse dönüş z ekseninde değil: toplam vektörün z payı (0..1). */
    fun flatness(wx: Double, wy: Double, wz: Double): Double {
        val n = sqrt(wx * wx + wy * wy + wz * wz)
        return if (n < 1e-6) 1.0 else abs(wz) / n
    }
}

/** Mıknatıs gücü: dünya alanı "sıfırlanır", mıknatıs yaklaştıkça artış ve en yüksek değer tutulur. */
class MagnetPeak {
    private var bx = 0.0; private var by = 0.0; private var bz = 0.0
    var tared = false; private set
    var current = 0.0; private set
    var peak = 0.0; private set
    var saturated = false; private set

    fun tare(x: Double, y: Double, z: Double) { bx = x; by = y; bz = z; tared = true; peak = 0.0; saturated = false }
    fun resetPeak() { peak = 0.0; saturated = false }

    /** [maxRange]: sensörün bildirdiği ölçüm sınırı (µT). Bir eksen sınıra %98 yaklaşırsa doymuş sayılır. */
    fun update(x: Double, y: Double, z: Double, maxRange: Double): Double {
        val dx = x - bx; val dy = y - by; val dz = z - bz
        current = sqrt(dx * dx + dy * dy + dz * dz)
        if (current > peak) peak = current
        if (maxRange > 0 && (abs(x) >= 0.98 * maxRange || abs(y) >= 0.98 * maxRange || abs(z) >= 0.98 * maxRange)) saturated = true
        return current
    }
}

/**
 * Basınç eğilimi (WMO barometrik eğilim sınıfları, 3 saatlik değişim):
 * sabit < 0,1 · yavaş 0,1–1,5 · değişiyor 1,6–3,5 · hızlı 3,6–6,0 · çok hızlı > 6 hPa.
 */
object WeatherTrend {
    enum class Tendency { STEADY, SLOW, CHANGING, QUICK, VERY_QUICK }
    enum class Outlook { IMPROVING, STABLE, WORSENING, STORM_POSSIBLE }

    data class Result(val change3hHpa: Double, val tendency: Tendency, val outlook: Outlook, val spanH: Double)

    fun tendency(change: Double): Tendency {
        val a = abs(change)
        return when {
            a < 0.1 -> Tendency.STEADY
            a <= 1.5 -> Tendency.SLOW
            a <= 3.5 -> Tendency.CHANGING
            a <= 6.0 -> Tendency.QUICK
            else -> Tendency.VERY_QUICK
        }
    }

    fun outlook(change: Double): Outlook = when {
        change <= -3.6 -> Outlook.STORM_POSSIBLE
        change <= -1.6 -> Outlook.WORSENING
        change >= 1.6 -> Outlook.IMPROVING
        else -> Outlook.STABLE
    }

    /** Son [windowH] saatin (zaman ms, hPa) noktalarına doğru uydurup 3 saatlik değişimi verir. En az 1 saat ve 3 nokta gerekir. */
    fun analyze(points: List<Pair<Long, Double>>, nowMs: Long, windowH: Double = 3.0): Result? {
        val from = nowMs - (windowH * 3_600_000).toLong()
        val p = points.filter { it.first >= from }.sortedBy { it.first }
        if (p.size < 3) return null
        val spanH = (p.last().first - p.first().first) / 3_600_000.0
        if (spanH < 1.0) return null
        val t0 = p.first().first
        val xs = p.map { (it.first - t0) / 3_600_000.0 }; val ys = p.map { it.second }
        val mx = xs.average(); val my = ys.average()
        var num = 0.0; var den = 0.0
        for (i in xs.indices) { num += (xs[i] - mx) * (ys[i] - my); den += (xs[i] - mx) * (xs[i] - mx) }
        if (den <= 0) return null
        val change = num / den * 3.0
        return Result(change, tendency(change), outlook(change), spanH)
    }
}

/** Görüntü üzerinde açı ve uzunluk. */
object ImageGeometry {
    /** Tepe [v] ile iki kol ucu [a], [b] arasındaki açı (derece, 0..180). */
    fun angle(vx: Double, vy: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val ux = ax - vx; val uy = ay - vy; val wx = bx - vx; val wy = by - vy
        val nu = hypot(ux, uy); val nw = hypot(wx, wy)
        if (nu < 1e-9 || nw < 1e-9) return 0.0
        return acos(((ux * wx + uy * wy) / (nu * nw)).coerceIn(-1.0, 1.0)) * 180 / PI
    }

    /** Referans uzunluğuyla ölçek: nesne uzunluğu = nesne pikseli / referans pikseli × referans mm. */
    fun length(refPx: Double, refMm: Double, objPx: Double): Double? = if (refPx < 5) null else objPx / refPx * refMm

    fun dist(ax: Double, ay: Double, bx: Double, by: Double) = hypot(bx - ax, by - ay)

    /** Kredi / banka kartı (ISO/IEC 7810 ID-1): 85,60 × 53,98 mm. */
    const val CARD_LONG_MM = 85.60
    const val CARD_SHORT_MM = 53.98
}
