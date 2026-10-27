package com.eirahoutmoss.nextools.core.measure

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ton üreteci: sinüs, kare, üçgen, beyaz ve pembe gürültü, logaritmik tarama.
 * Ses parçalar hâlinde üretilir; frekans/biçim değişimi tıklama yapmasın diye faz sürekli tutulur.
 */
class ToneSynth(val fs: Int) {
    enum class Wave { SINE, SQUARE, TRIANGLE, WHITE, PINK, SWEEP }

    @Volatile var wave: Wave = Wave.SINE
    @Volatile var frequency: Double = 440.0
    /** 0..1 */
    @Volatile var volume: Double = 0.5
    @Volatile var sweepFrom: Double = 20.0
    @Volatile var sweepTo: Double = 20_000.0
    @Volatile var sweepSeconds: Double = 10.0

    private var phase = 0.0
    private var sweepT = 0.0
    private var gain = 0.0
    private val rnd = Random(42)
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var b3 = 0.0; private var b4 = 0.0; private var b5 = 0.0; private var b6 = 0.0

    /** Şu an çalan frekans (tarama kipinde değişir). */
    val currentHz: Double get() = if (wave == Wave.SWEEP) sweepHz(sweepT) else frequency

    private fun sweepHz(t: Double): Double {
        val k = ln(sweepTo / sweepFrom) / sweepSeconds
        return sweepFrom * exp(k * (t % sweepSeconds))
    }

    fun restartSweep() { sweepT = 0.0 }

    /** [out]'u −1..1 örneklerle doldurur. Ses seviyesi yumuşak geçişle değişir. */
    fun render(out: FloatArray) {
        val w = wave
        for (i in out.indices) {
            gain += (volume - gain) * 0.002
            val f = when (w) {
                Wave.SWEEP -> sweepHz(sweepT).also { sweepT += 1.0 / fs }
                else -> frequency
            }
            phase += f / fs
            if (phase >= 1) phase -= kotlin.math.floor(phase)
            val v = when (w) {
                Wave.SINE, Wave.SWEEP -> sin(2 * PI * phase)
                Wave.SQUARE -> if (phase < 0.5) 0.8 else -0.8
                Wave.TRIANGLE -> 4 * kotlin.math.abs(phase - 0.5) - 1
                Wave.WHITE -> rnd.nextDouble(-1.0, 1.0) * 0.5
                Wave.PINK -> pink(rnd.nextDouble(-1.0, 1.0))
            }
            out[i] = (v * gain).toFloat()
        }
    }

    /** Paul Kellet pembe gürültü süzgeci. */
    private fun pink(white: Double): Double {
        b0 = 0.99886 * b0 + white * 0.0555179
        b1 = 0.99332 * b1 + white * 0.0750759
        b2 = 0.96900 * b2 + white * 0.1538520
        b3 = 0.86650 * b3 + white * 0.3104856
        b4 = 0.55000 * b4 + white * 0.5329522
        b5 = -0.7616 * b5 - white * 0.0168980
        val p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
        b6 = white * 0.115926
        return p * 0.11
    }
}
