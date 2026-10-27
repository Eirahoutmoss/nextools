package com.eirahoutmoss.nextools.core.measure

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Yürüyüş analizi: ivme büyüklüğündeki tepelerden adımlar; adım süresi, ritim (dakikada adım),
 * düzenlilik (değişkenlik katsayısı) ve sağ/sol simetrisi (tek ve çift adımların süre farkı).
 */
class GaitAnalyzer(private val minStepS: Double = 0.28, private val minThreshold: Double = 0.8) {
    private val trend = LowPass(1.0)
    private val smooth = LowPass(0.04)
    private var p0 = 0.0; private var p1 = 0.0; private var t1 = 0L
    private var started = false
    private var lastStepNs = 0L
    private var peakAvg = 0.0
    val stepTimesNs = mutableListOf<Long>()
    private val sway = RunningStats()

    fun reset() { stepTimesNs.clear(); started = false; lastStepNs = 0; peakAvg = 0.0; sway.reset(); trend.reset(); smooth.reset() }

    /** [magnitude]: ivme büyüklüğü (m/s²). Adım bulunursa true. */
    fun add(magnitude: Double, timestampNs: Long): Boolean {
        val d = smooth.update(magnitude - trend.update(magnitude, timestampNs), timestampNs)
        var step = false
        if (started) {
            val thr = max(minThreshold, 0.45 * peakAvg)
            val isPeak = p1 > p0 && p1 >= d && p1 > thr
            if (isPeak && (lastStepNs == 0L || (t1 - lastStepNs) / 1e9 >= minStepS)) {
                stepTimesNs += t1; lastStepNs = t1
                peakAvg = if (peakAvg == 0.0) p1 else 0.8 * peakAvg + 0.2 * p1
                step = true
            }
        }
        p0 = p1; p1 = d; t1 = timestampNs; started = true
        return step
    }

    /** Jiroskop büyüklüğü (rad/s): gövde salınımı için. */
    fun addGyro(magnitude: Double) = sway.add(magnitude)

    data class Result(
        val steps: Int,
        val cadence: Double,
        val stepTimeS: Double,
        /** Adım süresinin değişkenlik katsayısı (%); küçük = düzenli. */
        val variabilityPct: Double,
        /** Tek/çift adım süresi farkı (%); küçük = simetrik. */
        val asymmetryPct: Double,
        /** Ortalama gövde dönüş hızı (°/s). */
        val swayDegS: Double?,
    )

    fun result(): Result? {
        if (stepTimesNs.size < 6) return null
        // Duraklamaları (> 2 s) dışarıda bırak
        val iv = stepTimesNs.zipWithNext { a, b -> (b - a) / 1e9 }.filter { it < 2.0 }
        if (iv.size < 5) return null
        val s = RunningStats(); iv.forEach { s.add(it) }
        val odd = iv.filterIndexed { i, _ -> i % 2 == 0 }.average(); val even = iv.filterIndexed { i, _ -> i % 2 == 1 }.average()
        return Result(
            stepTimesNs.size, 60 / s.average, s.average, s.stdDev / s.average * 100,
            abs(odd - even) / s.average * 100, sway.average.takeIf { sway.count > 10 }?.let { it * 180 / PI },
        )
    }
}

/** Işık kaynağının ilişkili renk sıcaklığı (CCT): sRGB → XYZ → xy → McCamy yaklaşımı. */
object Cct {
    private fun lin(c: Double): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }

    data class Result(val kelvin: Double, val x: Double, val y: Double)

    fun fromSrgb(r: Double, g: Double, b: Double): Result? {
        val rl = lin(r); val gl = lin(g); val bl = lin(b)
        val X = 0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl
        val Y = 0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl
        val Z = 0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl
        val sum = X + Y + Z
        if (sum <= 1e-6) return null
        val x = X / sum; val y = Y / sum
        val n = (x - 0.3320) / (0.1858 - y)
        val k = 449 * n * n * n + 3525 * n * n + 6823.3 * n + 5520.33
        return if (k in 1000.0..25000.0) Result(k, x, y) else null
    }
}

/** pH kâğıdı rengi: referans renklerden en yakın ikisinin ΔE ağırlıklı ara değeri. */
object PhScale {
    data class Ref(val ph: Double, val lab: ColorMath.Lab)
    data class Estimate(val ph: Double, val nearestDeltaE: Double)

    /** Genel evrensel indikatör skalası (yaklaşık; kâğıt markasına göre değişir). */
    val GENERIC: List<Ref> = listOf(
        1.0 to 0xEE1C25, 2.0 to 0xF26724, 3.0 to 0xF8C611, 4.0 to 0xF5ED1C, 5.0 to 0xB5D333, 6.0 to 0x84C341, 7.0 to 0x4DB848,
        8.0 to 0x33A94B, 9.0 to 0x22B46B, 10.0 to 0x0A8F8E, 11.0 to 0x4690C4, 12.0 to 0x3853A4, 13.0 to 0x5A51A2, 14.0 to 0x63459D,
    ).map { (p, c) -> Ref(p, ColorMath.lab((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)) }

    fun estimate(lab: ColorMath.Lab, refs: List<Ref>): Estimate? {
        if (refs.size < 2) return null
        val d = refs.map { it to ColorMath.deltaE2000(lab, it.lab) }.sortedBy { it.second }
        val (a, da) = d[0]; val (b, db) = d[1]
        if (da < 1e-6) return Estimate(a.ph, 0.0)
        val wa = 1 / da; val wb = 1 / db
        // Komşu olmayan iki referans arasında ara değer anlamsız: en yakını al
        val ph = if (abs(a.ph - b.ph) <= 2.01) (a.ph * wa + b.ph * wb) / (wa + wb) else a.ph
        return Estimate(ph, da)
    }
}

/** Su bulanıklığı: berrak su referansına göre renk farkı (ΔE00). NTU değildir. */
object Turbidity {
    enum class Level { CLEAR, SLIGHT, CLOUDY, VERY_CLOUDY }
    fun level(deltaE: Double): Level = when {
        deltaE < 2 -> Level.CLEAR
        deltaE < 5 -> Level.SLIGHT
        deltaE < 12 -> Level.CLOUDY
        else -> Level.VERY_CLOUDY
    }
}

/** Sonar: hoparlörden kısa tarama, mikrofonda doğrudan ses ve yankı arasındaki süre. */
object Sonar {
    fun soundSpeed(tempC: Double = 20.0) = 331.3 + 0.606 * tempC

    /** Doğrusal tarama, Hann pencereli. */
    fun chirp(fs: Int, f0: Double, f1: Double, durS: Double): DoubleArray {
        val n = (fs * durS).toInt()
        return DoubleArray(n) { i ->
            val t = i.toDouble() / fs
            val w = 0.5 - 0.5 * cos(2 * PI * i / (n - 1))
            w * sin(2 * PI * (f0 * t + (f1 - f0) / (2 * durS) * t * t))
        }
    }

    /** [x] içinde [ref]'in 0..[maxLag] kaydırmalarındaki ilişki büyüklüğü. */
    fun correlate(x: DoubleArray, ref: DoubleArray, maxLag: Int): DoubleArray {
        val lags = min(maxLag, x.size - ref.size).coerceAtLeast(0)
        return DoubleArray(lags) { lag -> var s = 0.0; for (i in ref.indices) s += x[lag + i] * ref[i]; abs(s) }
    }

    data class Echo(val distanceM: Double, val confidence: Double)

    /**
     * [corr]: ilişki dizisi. En güçlü tepe doğrudan sestir (hoparlör → mikrofon). Ondan sonra
     * [minDistM] karşılığı süre geçtikten sonraki en güçlü tepe yankıdır.
     */
    fun echo(corr: DoubleArray, fs: Int, c: Double = soundSpeed(), minDistM: Double = 0.3, maxDistM: Double = 5.0): Echo? {
        if (corr.size < 10) return null
        var direct = 0
        val firstPart = min(corr.size, fs / 50) // ilk 20 ms
        for (i in 0 until firstPart) if (corr[i] > corr[direct]) direct = i
        val from = direct + (2 * minDistM / c * fs).toInt()
        val to = min(corr.size - 1, direct + (2 * maxDistM / c * fs).toInt())
        if (from >= to) return null
        var e = from
        for (i in from..to) if (corr[i] > corr[e]) e = i
        val sorted = corr.copyOfRange(from, to + 1).sorted()
        val noise = sorted[sorted.size / 2].coerceAtLeast(1e-12)
        return Echo((e - direct).toDouble() / fs * c / 2, corr[e] / noise)
    }
}

/**
 * İki kapılı zamanlayıcı (video ile hız): her karede iki dikey şeridin parlaklığı verilir.
 * Şerit arka plandan [threshold] kadar saparsa kapı tetiklenir; iki kapı arası süre ölçülür.
 */
class GateTimer(private val threshold: Double = 12.0) {
    enum class State { ARMED, FIRST_HIT, DONE }
    var state = State.ARMED; private set
    private var baseA = Double.NaN; private var baseB = Double.NaN
    private var firstGate = 0
    private var firstNs = 0L
    var resultS: Double? = null; private set
    /** +1: A'dan B'ye, −1: B'den A'ya. */
    var direction = 0; private set

    fun arm() { state = State.ARMED; resultS = null; direction = 0; baseA = Double.NaN; baseB = Double.NaN }

    fun feed(a: Double, b: Double, timestampNs: Long) {
        if (baseA.isNaN()) { baseA = a; baseB = b; return }
        val hitA = abs(a - baseA) > threshold; val hitB = abs(b - baseB) > threshold
        when (state) {
            State.ARMED -> {
                if (hitA || hitB) { state = State.FIRST_HIT; firstGate = if (hitA) 1 else 2; firstNs = timestampNs }
            }
            State.FIRST_HIT -> {
                val other = if (firstGate == 1) hitB else hitA
                if (other) {
                    resultS = (timestampNs - firstNs) / 1e9; direction = if (firstGate == 1) 1 else -1; state = State.DONE
                } else if ((timestampNs - firstNs) / 1e9 > 30) arm()
            }
            State.DONE -> Unit
        }
        // Tetiklenmeyen kapının arka planı yavaşça izlenir (ışık değişimi)
        if (!hitA) baseA += (a - baseA) * 0.05
        if (!hitB) baseB += (b - baseB) * 0.05
    }
}

/** Kızılötesi kumanda kodları: (taşıyıcı Hz, açık/kapalı süre dizisi µs). */
object IrEncoder {
    data class Signal(val carrierHz: Int, val pattern: IntArray)

    private fun bitsLsb(v: Int, n: Int) = (0 until n).map { (v shr it) and 1 }
    private fun bitsMsb(v: Int, n: Int) = (n - 1 downTo 0).map { (v shr it) and 1 }

    /** NEC: 9 ms + 4,5 ms başlık, adres, ~adres, komut, ~komut (LSB önce). */
    fun nec(address: Int, command: Int): Signal {
        val p = mutableListOf(9000, 4500)
        val bits = bitsLsb(address and 0xFF, 8) + bitsLsb(address.inv() and 0xFF, 8) + bitsLsb(command and 0xFF, 8) + bitsLsb(command.inv() and 0xFF, 8)
        bits.forEach { p += 560; p += if (it == 1) 1690 else 560 }
        p += 560
        return Signal(38_000, p.toIntArray())
    }

    /** Samsung32: 4,5 + 4,5 ms başlık, adres, adres, komut, ~komut. */
    fun samsung32(address: Int, command: Int): Signal {
        val p = mutableListOf(4500, 4500)
        val bits = bitsLsb(address and 0xFF, 8) + bitsLsb(address and 0xFF, 8) + bitsLsb(command and 0xFF, 8) + bitsLsb(command.inv() and 0xFF, 8)
        bits.forEach { p += 560; p += if (it == 1) 1690 else 560 }
        p += 560
        return Signal(38_000, p.toIntArray())
    }

    /** Sony SIRC 12 bit: 7 bit komut + 5 bit adres (LSB önce), 3 kez, 45 ms aralıkla. */
    fun sirc12(address: Int, command: Int): Signal {
        val frame = mutableListOf(2400, 600)
        (bitsLsb(command and 0x7F, 7) + bitsLsb(address and 0x1F, 5)).forEach { frame += if (it == 1) 1200 else 600; frame += 600 }
        val len = frame.sum()
        val p = mutableListOf<Int>()
        repeat(3) { i -> p += frame; if (i < 2) p[p.lastIndex] = p.last() + (45_000 - len).coerceAtLeast(0) }
        return Signal(40_000, p.toIntArray())
    }

    /** Philips RC5: Manchester 889 µs; başlangıç 1, alan, geçiş biti, 5 bit adres, 6 bit komut (MSB önce). */
    fun rc5(address: Int, command: Int, toggle: Boolean): Signal {
        val bits = listOf(1, if (command < 64) 1 else 0, if (toggle) 1 else 0) + bitsMsb(address and 0x1F, 5) + bitsMsb(command and 0x3F, 6)
        // Her bit: 1 = kapalı, açık; 0 = açık, kapalı. Ardışık aynı düzeyler birleşir.
        val levels = mutableListOf<Pair<Boolean, Int>>()
        bits.forEach { b -> listOf(b == 0, b == 1).forEach { on -> if (levels.isNotEmpty() && levels.last().first == on) levels[levels.lastIndex] = on to levels.last().second + 889 else levels += on to 889 } }
        if (levels.first().first.not()) levels.removeAt(0)
        if (!levels.last().first) levels.removeAt(levels.lastIndex)
        return Signal(36_000, levels.map { it.second }.toIntArray())
    }
}

/** Ortam ışığı sensörü olmayan telefonda kameranın otomatik pozlamasından kaba aydınlık (lüks). */
object CameraLux {
    /** EV100 = log2(N²/t) − log2(ISO/100); düz alıcı için E ≈ 2,5 · 2^EV100. */
    fun fromExposure(aperture: Double, exposureS: Double, iso: Double): Double? {
        if (aperture <= 0 || exposureS <= 0 || iso <= 0) return null
        val ev100 = ln(aperture * aperture / exposureS) / ln(2.0) - ln(iso / 100) / ln(2.0)
        return 2.5 * 2.0.pow(ev100)
    }
}

/** Yardımcı: dizinin karekök ortalama karesi. */
internal fun rms(x: DoubleArray) = sqrt(x.sumOf { it * it } / max(1, x.size))
