package com.eirahoutmoss.nextools.core.measure

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** Frekans ağırlıklandırma (IEC 61672): A insan kulağına yakın, C düz ama uçları kırpılmış, Z düz. */
enum class Weighting(val code: String) { A("A"), C("C"), Z("Z") }

/** Zaman ağırlıklandırma: Hızlı 125 ms, Yavaş 1 s. */
enum class TimeWeighting(val tauS: Double, val code: String) { FAST(0.125, "F"), SLOW(1.0, "S") }

/**
 * IEC 61672 ağırlık süzgeçlerinin sayısal (iki doğrusal dönüşüm, ön bükmeli) karşılığı.
 * Birinci dereceden bölümlerin art arda bağlanmasıyla kurulur ve 1 kHz'de 0 dB'e normalize edilir.
 *
 * Dürüst sınır: 48 kHz örneklemede 10 kHz üstünde standart eğriden birkaç dB ayrılabilir;
 * telefon mikrofonu da zaten 16 kHz üstünde zayıftır.
 */
class WeightingFilter(weighting: Weighting, private val fs: Double) {

    private class Section(val b0: Double, val b1: Double, val a1: Double) {
        var x1 = 0.0
        var y1 = 0.0
        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 - a1 * y1
            x1 = x; y1 = y
            return y
        }
        fun reset() { x1 = 0.0; y1 = 0.0 }
    }

    private val sections: List<Section>
    private val gain: Double

    init {
        val k = 2 * fs
        fun warp(fHz: Double) = k * tan(PI * fHz / fs)
        fun hp(fHz: Double): Section {
            val w = warp(fHz)
            return Section(b0 = k / (k + w), b1 = -k / (k + w), a1 = (w - k) / (k + w))
        }
        fun lp(fHz: Double): Section {
            val w = warp(fHz)
            return Section(b0 = 1 / (k + w), b1 = 1 / (k + w), a1 = (w - k) / (k + w))
        }
        sections = when (weighting) {
            Weighting.A -> listOf(hp(F1), hp(F1), hp(F2), hp(F3), lp(F4), lp(F4))
            Weighting.C -> listOf(hp(F1), hp(F1), lp(F4), lp(F4))
            Weighting.Z -> emptyList()
        }
        gain = if (sections.isEmpty()) 1.0 else 1.0 / magnitudeAt(1000.0, raw = true)
    }

    fun process(x: Double): Double {
        var v = x
        for (s in sections) v = s.process(v)
        return v * gain
    }

    fun reset() = sections.forEach { it.reset() }

    /** Süzgecin [fHz] frekansındaki kazancı (doğrusal). Testler ve grafik için. */
    fun magnitudeAt(fHz: Double, raw: Boolean = false): Double {
        val w = 2 * PI * fHz / fs
        // z^-1 = e^{-jw}
        val zr = cos(w); val zi = -sin(w)
        var re = 1.0; var im = 0.0
        for (s in sections) {
            // H = (b0 + b1 z^-1) / (1 + a1 z^-1)
            val nr = s.b0 + s.b1 * zr; val ni = s.b1 * zi
            val dr = 1 + s.a1 * zr; val di = s.a1 * zi
            val den = dr * dr + di * di
            val hr = (nr * dr + ni * di) / den
            val hi = (ni * dr - nr * di) / den
            val r2 = re * hr - im * hi
            val i2 = re * hi + im * hr
            re = r2; im = i2
        }
        val m = hypot(re, im)
        return if (raw) m else m * gain
    }

    companion object {
        const val F1 = 20.598997
        const val F2 = 107.65265
        const val F3 = 737.86223
        const val F4 = 12194.217
    }
}

/**
 * Ses seviyesi ölçer. Ham PCM örneklerini (−1..1) alır; ağırlıklandırılmış, zaman ağırlıklı seviyeyi,
 * eşdeğer sürekli seviyeyi (Leq) ve en büyük / en küçük değerleri tutar.
 *
 * dB SPL = dBFS + [offsetDb]. [offsetDb] kalibrasyonla belirlenir; kalibre edilmemiş telefonda
 * değer ±5 dB (bazen daha fazla) sapabilir. Ses KAYDEDİLMEZ, yalnızca seviyeler hesaplanır.
 */
class SoundLevelMeter(
    val fs: Double,
    weighting: Weighting,
    val timeWeighting: TimeWeighting,
    var offsetDb: Double = DEFAULT_OFFSET_DB,
) {
    val weighting: Weighting = weighting
    private val filter = WeightingFilter(weighting, fs)
    private val alpha = 1 - exp(-1.0 / (fs * timeWeighting.tauS))
    private var ms = 0.0
    private var energySum = 0.0
    private var sampleCount = 0L
    private var settleSamples = (fs * timeWeighting.tauS * 3).toLong()
    private var maxMs = 0.0
    private var minMs = Double.MAX_VALUE
    private var peakAbs = 0.0

    /** Bir blok örneği işler. Blok sonunda güncel seviye okunabilir. */
    fun process(samples: FloatArray, count: Int = samples.size) {
        for (i in 0 until count) {
            val y = filter.process(samples[i].toDouble())
            val sq = y * y
            ms += alpha * (sq - ms)
            energySum += sq
            sampleCount++
            val a = kotlin.math.abs(samples[i].toDouble())
            if (a > peakAbs) peakAbs = a
        }
        if (sampleCount > settleSamples) {
            maxMs = max(maxMs, ms)
            minMs = min(minMs, ms)
        }
    }

    private fun toDb(meanSquare: Double): Double = 10 * log10(max(meanSquare, 1e-14)) + offsetDb

    /** Zaman ağırlıklı anlık seviye (dB). */
    val level: Double get() = toDb(ms)

    /** Başlangıçtan bu yana eşdeğer sürekli seviye (dB). */
    val leq: Double get() = if (sampleCount == 0L) Double.NaN else toDb(energySum / sampleCount)

    val lmax: Double get() = if (maxMs == 0.0) Double.NaN else toDb(maxMs)
    val lmin: Double get() = if (minMs == Double.MAX_VALUE) Double.NaN else toDb(minMs)

    /** Ham sinyalin tepe değeri (dBFS). 0'a yakınsa mikrofon doymuştur, ölçüm güvenilmez. */
    val peakDbfs: Double get() = 20 * log10(max(peakAbs, 1e-9))
    val clipping: Boolean get() = peakAbs >= 0.99

    val elapsedS: Double get() = sampleCount / fs

    /** Min / maks / Leq istatistiklerini sıfırlar (süzgeç durumu korunur). */
    fun resetStats() {
        energySum = 0.0; sampleCount = 0; maxMs = 0.0; minMs = Double.MAX_VALUE; peakAbs = 0.0
        settleSamples = 0
    }

    companion object {
        /**
         * Kalibre edilmemiş telefon için başlangıç farkı: MEMS mikrofonların tam ölçeği ≈ 120 dB SPL.
         * Yaklaşıktır; uygulama bu yüzden "kalibre değil" rozeti gösterir.
         */
        const val DEFAULT_OFFSET_DB = 120.0
    }
}

/** Seviyeyi gündelik bir karşılıkla eşleştirir (konsept görselindeki "Normal konuşma" gibi). */
object SoundScale {
    enum class Band(val fromDb: Double) {
        VERY_QUIET(Double.NEGATIVE_INFINITY),
        QUIET_ROOM(30.0),
        QUIET_OFFICE(45.0),
        CONVERSATION(55.0),
        BUSY_STREET(70.0),
        HARMFUL_LONG(85.0),
        DANGEROUS(100.0),
    }

    fun band(db: Double): Band = Band.entries.last { db >= it.fromDb }

    /** 85 dB üstü uzun süreli maruziyette işitmeye zararlı kabul edilir. */
    fun isWarning(db: Double): Boolean = db >= Band.HARMFUL_LONG.fromDb
    fun isDanger(db: Double): Boolean = db >= Band.DANGEROUS.fromDb
}
