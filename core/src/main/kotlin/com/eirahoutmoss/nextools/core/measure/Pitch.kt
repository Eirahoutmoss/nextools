package com.eirahoutmoss.nextools.core.measure

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Perde (temel frekans) bulma: YIN algoritması (de Cheveigné & Kawahara, 2002).
 * Gürültüde ya da sessizlikte null döner.
 */
class Yin(private val fs: Double, private val size: Int, private val threshold: Double = 0.15) {
    private val buf = DoubleArray(size / 2)

    data class Result(val hz: Double, val probability: Double)

    fun detect(x: FloatArray, minHz: Double = 40.0, maxHz: Double = 2000.0): Result? {
        require(x.size >= size)
        val half = size / 2
        val tauMin = maxOf(2, (fs / maxHz).toInt())
        val tauMax = minOf(half - 1, (fs / minHz).toInt())
        var rms = 0.0
        for (i in 0 until size) rms += x[i] * x[i]
        if (sqrt(rms / size) < 1e-3) return null
        buf[0] = 1.0
        for (tau in 1 until half) {
            var s = 0.0
            for (i in 0 until half) { val d = x[i] - x[i + tau]; s += d * d }
            buf[tau] = s
        }
        var running = 0.0
        for (tau in 1 until half) {
            running += buf[tau]
            buf[tau] = if (running == 0.0) 1.0 else buf[tau] * tau / running
        }
        var tau = tauMin
        var found = -1
        while (tau <= tauMax) {
            if (buf[tau] < threshold) {
                while (tau + 1 <= tauMax && buf[tau + 1] < buf[tau]) tau++
                found = tau; break
            }
            tau++
        }
        if (found < 0) return null
        val t = found
        val better = if (t in 1 until half - 1) {
            val s0 = buf[t - 1]; val s1 = buf[t]; val s2 = buf[t + 1]
            val d = 2 * s1 - s2 - s0
            if (d == 0.0) t.toDouble() else t + (s2 - s0) / (2 * d)
        } else t.toDouble()
        return Result(fs / better, 1 - buf[t])
    }
}

/** Eşit aralıklı (12-TET) nota adlandırma; A4 ayarlanabilir. Makam komaları NexTone'dadır. */
object Notes {
    data class Note(
        /** 0 = Do (C), 9 = La (A). */
        val pitchClass: Int,
        val octave: Int,
        /** En yakın notadan sapma (sent, -50..50). */
        val cents: Double,
        val targetHz: Double,
    )

    fun fromHz(hz: Double, a4: Double = 440.0): Note {
        val midiExact = 69 + 12 * log2(hz / a4)
        val midi = midiExact.roundToInt()
        val cents = (midiExact - midi) * 100
        val target = a4 * 2.0.pow((midi - 69) / 12.0)
        return Note(((midi % 12) + 12) % 12, midi / 12 - 1, cents, target)
    }

    /** Akort için "tam" sayılan sapma. */
    const val IN_TUNE_CENTS = 5.0
}
