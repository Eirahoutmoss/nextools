package com.eirahoutmoss.nextools.core.measure

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Işık titreşimi (flicker): kısa pozlamalı kamera karesinde satır satır parlaklık (rolling shutter)
 * titreşen ışıkta yatay bantlar oluşturur. Satır ortalamalarından modülasyon derinliği ve —
 * satır okuma süresi biliniyorsa — titreşim frekansı bulunur.
 */
object Flicker {
    data class Result(
        /** Titreşim yüzdesi (IES): (maks − min) / (maks + min) × 100. */
        val percent: Double,
        /** Satır okuma süresinden hesaplanan frekans (Hz); bilinmiyorsa null. */
        val frequencyHz: Double?,
    )

    /**
     * [rows]: her satırın ortalama parlaklığı (0..255). [rowTimeS]: bir satırın okunma süresi
     * (rolling shutter skew / satır sayısı). Kare içindeki yavaş değişim (vinyet, sahne) bant
     * geçiren süzgeçle ayıklanır.
     */
    fun analyze(rows: DoubleArray, rowTimeS: Double?): Result {
        val n = rows.size
        if (n < 64) return Result(0.0, null)
        val from = n / 10; val to = n - n / 10
        val mean = rows.copyOfRange(from, to).average()
        // Satır süresi biliniyorsa sahne eğimi (2. derece) ile sinüs birlikte uydurulur; karede çoğu zaman
        // yalnızca 1–3 dalga olduğundan ayrı ayrı çıkarmak dalganın bir kısmını eğriye yedirir.
        var freq: Double? = null
        var smooth = quadraticFit(rows)
        if (rowTimeS != null && rowTimeS > 0) {
            val frame = n * rowTimeS
            val cMin = max(1.2, 40.0 * frame); val cMax = min(n / 6.0, 5000.0 * frame)
            if (cMax > cMin) {
                fun score(c: Double) = sineFit(rows, c)
                var best = cMin; var bestE = -1.0
                var c = cMin
                while (c <= cMax) { val e = score(c).first; if (e > bestE) { bestE = e; best = c }; c += 0.2 }
                var step = 0.05
                repeat(3) {
                    var cc = best - 4 * step
                    val end = best + 4 * step
                    while (cc <= end) { if (cc in cMin..cMax) { val e = score(cc).first; if (e > bestE) { bestE = e; best = cc } }; cc += step }
                    step /= 5
                }
                val fit = score(best)
                if (mean > 1 && fit.second / mean * 100 >= 1.0) { freq = best / frame; smooth = fit.third }
            }
        }
        val detail = DoubleArray(n) { rows[it] - smooth[it] }
        var hi = Double.NEGATIVE_INFINITY; var lo = Double.POSITIVE_INFINITY
        val fine = movingAverage(detail, max(1, n / 200))
        for (i in from until to) { hi = max(hi, fine[i]); lo = min(lo, fine[i]) }
        val amplitude = (hi - lo) / 2
        val percent = if (mean <= 1) 0.0 else (amplitude / mean * 100).coerceIn(0.0, 100.0)
        return Result(percent, freq)
    }

    /**
     * y ≈ a + b·x + c·x² + p·sin(2π·cycles·i/n) + q·cos(…) en küçük kareler.
     * Döner: (sinüsün açıkladığı enerji, sinüs genliği, yalnızca polinom kısmı).
     */
    private fun sineFit(y: DoubleArray, cycles: Double): Triple<Double, Double, DoubleArray> {
        val n = y.size
        val m = Array(5) { DoubleArray(6) }
        val p = DoubleArray(5)
        val w = 2 * PI * cycles / n
        for (i in 0 until n) {
            val x = 2.0 * i / (n - 1) - 1
            p[0] = 1.0; p[1] = x; p[2] = x * x; p[3] = sin(w * i); p[4] = cos(w * i)
            for (r in 0..4) { for (c in 0..4) m[r][c] += p[r] * p[c]; m[r][5] += p[r] * y[i] }
        }
        for (col in 0..4) {
            val piv = (col..4).maxBy { abs(m[it][col]) }
            val t = m[col]; m[col] = m[piv]; m[piv] = t
            if (abs(m[col][col]) < 1e-12) return Triple(0.0, 0.0, quadraticFit(y))
            for (r in 0..4) if (r != col) {
                val f = m[r][col] / m[col][col]
                for (c in col..5) m[r][c] -= f * m[col][c]
            }
        }
        val a = DoubleArray(5) { m[it][5] / m[it][it] }
        var e = 0.0
        for (i in 0 until n) { val s = a[3] * sin(w * i) + a[4] * cos(w * i); e += s * s }
        val poly = DoubleArray(n) { i -> val x = 2.0 * i / (n - 1) - 1; a[0] + a[1] * x + a[2] * x * x }
        return Triple(e, sqrt(a[3] * a[3] + a[4] * a[4]), poly)
    }

    /** En küçük kareler ikinci derece polinom uydurması (x = satır numarası, normalize). */
    fun quadraticFit(y: DoubleArray): DoubleArray {
        val n = y.size
        val m = Array(3) { DoubleArray(4) }
        for (i in 0 until n) {
            val x = 2.0 * i / (n - 1) - 1
            val p = doubleArrayOf(1.0, x, x * x)
            for (r in 0..2) { for (c in 0..2) m[r][c] += p[r] * p[c]; m[r][3] += p[r] * y[i] }
        }
        for (col in 0..2) {
            val piv = (col..2).maxBy { abs(m[it][col]) }
            val t = m[col]; m[col] = m[piv]; m[piv] = t
            for (r in 0..2) if (r != col) {
                val f = m[r][col] / m[col][col]
                for (c in col..3) m[r][c] -= f * m[col][c]
            }
        }
        val a = DoubleArray(3) { m[it][3] / m[it][it] }
        return DoubleArray(n) { i -> val x = 2.0 * i / (n - 1) - 1; a[0] + a[1] * x + a[2] * x * x }
    }

    fun movingAverage(x: DoubleArray, w: Int): DoubleArray {
        val out = DoubleArray(x.size)
        var s = 0.0
        val half = w / 2
        // önek toplamıyla
        val pre = DoubleArray(x.size + 1)
        for (i in x.indices) { s += x[i]; pre[i + 1] = s }
        for (i in x.indices) {
            val a = max(0, i - half); val b = min(x.size, i + half + 1)
            out[i] = (pre[b] - pre[a]) / (b - a)
        }
        return out
    }

    /** IEEE 1789 kaba sınıfı: %8'in altı düşük risk. */
    enum class Risk { LOW, MODERATE, HIGH }

    fun risk(percent: Double): Risk = when {
        percent < 8 -> Risk.LOW
        percent < 30 -> Risk.MODERATE
        else -> Risk.HIGH
    }
}

/**
 * Yol pürüzlülüğü: telefon araca sabitken düşey ivmenin (yerçekimi yönüne izdüşüm)
 * pencere başına standart sapması. Çukur: tek bir düşey sarsıntının eşiği aşması.
 */
object RoadRoughness {
    enum class Grade { SMOOTH, FAIR, ROUGH, VERY_ROUGH }

    fun grade(rmsMs2: Double): Grade = when {
        rmsMs2 < 0.7 -> Grade.SMOOTH
        rmsMs2 < 1.4 -> Grade.FAIR
        rmsMs2 < 2.5 -> Grade.ROUGH
        else -> Grade.VERY_ROUGH
    }

    /** Düşey bileşen: ivmenin yerçekimi yönündeki izdüşümü (yerçekimi çıkarılmış), m/s². */
    fun vertical(ax: Double, ay: Double, az: Double, gx: Double, gy: Double, gz: Double): Double {
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        if (g < 1e-6) return 0.0
        return (ax * gx + ay * gy + az * gz) / g - g
    }

    const val POTHOLE_MS2 = 6.0
}

/** Sürüş olayları (g biriminde). Boyuna ivme GPS hızından, yanal ivme hız × sapma hızından. */
object Driving {
    enum class Event { HARSH_BRAKE, HARSH_ACCEL, SHARP_TURN }

    const val BRAKE_G = -0.35
    const val ACCEL_G = 0.30
    const val TURN_G = 0.40
    const val G = 9.80665

    fun classify(longG: Double, latG: Double): Event? = when {
        longG <= BRAKE_G -> Event.HARSH_BRAKE
        longG >= ACCEL_G -> Event.HARSH_ACCEL
        abs(latG) >= TURN_G -> Event.SHARP_TURN
        else -> null
    }

    /** Sapma hızı (rad/s): jiroskobun yerçekimi eksenine izdüşümü. */
    fun yawRate(wx: Double, wy: Double, wz: Double, gx: Double, gy: Double, gz: Double): Double {
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        return if (g < 1e-6) 0.0 else (wx * gx + wy * gy + wz * gz) / g
    }

    /** 100 üzerinden puan: her olay puan düşürür, km başına normalize edilir. */
    fun score(events: Int, distanceKm: Double): Int {
        if (distanceKm < 0.5) return 100
        val perKm = events / distanceKm
        return (100 - perKm * 25).toInt().coerceIn(0, 100)
    }
}

/**
 * Makine sağlığı imzası: titreşim spektrumunun logaritmik bantlardaki enerjisi + RMS + baskın frekans.
 * İki imza karşılaştırılır; teşhis değildir, yalnızca "değişim var / yok".
 */
data class MachineSignature(val bands: DoubleArray, val rms: Double, val dominantHz: Double?, val fs: Double)

object MachineHealth {
    /** Bant sınırları (Hz): 2'den Nyquist'e logaritmik 16 bant. */
    fun signature(x: DoubleArray, fs: Double): MachineSignature {
        val n = Integer.highestOneBit(x.size)
        val seg = x.copyOfRange(x.size - n, x.size)
        val spec = Fft.amplitudeSpectrum(seg)
        val df = fs / n
        val edges = DoubleArray(17) { 2.0 * exp(it * ln((fs / 2) / 2.0) / 16) }
        val bands = DoubleArray(16) { b ->
            var e = 0.0
            for (k in (edges[b] / df).toInt().coerceAtLeast(1)..(edges[b + 1] / df).toInt().coerceAtMost(spec.size - 1)) e += spec[k] * spec[k]
            e
        }
        val v = VibrationMath.analyze(seg, fs)
        return MachineSignature(bands, v.rms, v.dominant?.hz, fs)
    }

    data class Comparison(
        /** RMS değişimi (%). */
        val rmsChangePct: Double,
        /** Spektral benzerlik 0..1 (1 = aynı biçim). */
        val similarity: Double,
        val dominantShiftHz: Double?,
        val verdict: Verdict,
    )

    enum class Verdict { SAME, SLIGHT_CHANGE, CHANGED }

    fun compare(base: MachineSignature, now: MachineSignature): Comparison {
        val a = base.bands.map { ln(1e-12 + it) }; val b = now.bands.map { ln(1e-12 + it) }
        val ma = a.average(); val mb = b.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in a.indices) { num += (a[i] - ma) * (b[i] - mb); da += (a[i] - ma) * (a[i] - ma); db += (b[i] - mb) * (b[i] - mb) }
        val sim = if (da <= 0 || db <= 0) 0.0 else (num / sqrt(da * db)).coerceIn(0.0, 1.0)
        val rmsPct = if (base.rms <= 0) 0.0 else (now.rms - base.rms) / base.rms * 100
        val shift = if (base.dominantHz != null && now.dominantHz != null) now.dominantHz - base.dominantHz else null
        val verdict = when {
            sim < 0.8 || abs(rmsPct) > 50 -> Verdict.CHANGED
            sim < 0.92 || abs(rmsPct) > 20 || (shift != null && abs(shift) > 2) -> Verdict.SLIGHT_CHANGE
            else -> Verdict.SAME
        }
        return Comparison(rmsPct, sim, shift, verdict)
    }
}

/**
 * Sızdırmazlık testi: kapı hızla kapanınca odanın iç basıncı kısa bir an yükselir (Pa).
 * Sıkı oda: büyük sıçrama, yavaş sönüm. Sızdıran oda: küçük sıçrama, hızlı sönüm.
 * Göreli karşılaştırmadır, standart blower-door testi değildir.
 */
class PressureSpike(private val fs: Double) {
    data class Event(val peakPa: Double, val decayS: Double?)

    private val history = ArrayDeque<Double>()
    private val baseLen = (2 * fs).toInt().coerceAtLeast(5)
    private var inEvent = false
    private var peak = 0.0
    private var peakIndex = 0
    private var sinceStart = 0
    private var base = 0.0
    private var decayAt: Int? = null

    /** [hpa] örneği; olay bitince sonucu döndürür. [thresholdPa] eşiği aşan değişim olay başlatır. */
    fun update(hpa: Double, thresholdPa: Double = 2.0): Event? {
        val pa = hpa * 100
        if (!inEvent) {
            history.addLast(pa); while (history.size > baseLen) history.removeFirst()
            if (history.size < baseLen) return null
            base = history.sorted()[history.size / 2]
            if (abs(pa - base) >= thresholdPa) {
                inEvent = true; peak = pa - base; peakIndex = 0; sinceStart = 0; decayAt = null
            }
            return null
        }
        sinceStart++
        val d = pa - base
        if (abs(d) > abs(peak)) { peak = d; peakIndex = sinceStart; decayAt = null }
        if (decayAt == null && abs(d) <= abs(peak) / Math.E) decayAt = sinceStart
        if (sinceStart > 4 * fs || (decayAt != null && sinceStart - decayAt!! > fs)) {
            inEvent = false
            history.clear()
            return Event(abs(peak), decayAt?.let { (it - peakIndex) / fs })
        }
        return null
    }
}

/**
 * Nabız (PPG): parmak kamera + flaşın üstündeyken kırmızı kanal ortalaması kalp atışıyla dalgalanır.
 * Düzgün aralıklı örneğe çevrilip 0,7–3,5 Hz (42–210 vuruş/dk) aralığında baskın frekans aranır.
 * Tıbbi cihaz değildir.
 */
object Pulse {
    data class Result(val bpm: Double, val quality: Double)

    /** [t] saniye, [v] kırmızı ortalaması. En az 6 sn veri gerekir. */
    fun estimate(t: DoubleArray, v: DoubleArray, fs: Double = 30.0): Result? {
        if (t.size < 20) return null
        val span = t.last() - t.first()
        if (span < 6) return null
        val n0 = (span * fs).toInt()
        var n = 1; while (n * 2 <= n0) n *= 2
        val start = t.last() - n / fs
        val y = DoubleArray(n)
        var j = 0
        for (i in 0 until n) {
            val ti = start + i / fs
            while (j < t.size - 2 && t[j + 1] < ti) j++
            val f = ((ti - t[j]) / (t[j + 1] - t[j])).coerceIn(0.0, 1.0)
            y[i] = v[j] + (v[j + 1] - v[j]) * f
        }
        // Yavaş eğilimi çıkar (1,5 sn kayan ortalama)
        val trend = Flicker.movingAverage(y, (1.5 * fs).toInt())
        val d = DoubleArray(n) { y[it] - trend[it] }
        val spec = Fft.amplitudeSpectrum(d)
        val p = Spectra.dominant(spec, fs, n, minHz = 0.7, maxHz = 3.5) ?: return null
        val df = fs / n
        val band = ((0.7 / df).toInt()..(3.5 / df).toInt().coerceAtMost(spec.size - 1))
        val mean = band.map { spec[it] }.average()
        val quality = if (mean <= 0) 0.0 else ((p.amplitude / mean - 1) / 4).coerceIn(0.0, 1.0)
        return Result(p.hz * 60, quality)
    }

    /** Parmak lens üzerinde mi: kırmızı baskın ve parlak, yeşil/mavi düşük. */
    fun fingerPresent(r: Int, g: Int, b: Int): Boolean = r > 90 && r > g * 2 && r > b * 2
}
