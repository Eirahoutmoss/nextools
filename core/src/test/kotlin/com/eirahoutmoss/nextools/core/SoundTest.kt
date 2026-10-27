package com.eirahoutmoss.nextools.core

import com.eirahoutmoss.nextools.core.measure.SoundLevelMeter
import com.eirahoutmoss.nextools.core.measure.SoundScale
import com.eirahoutmoss.nextools.core.measure.TimeWeighting
import com.eirahoutmoss.nextools.core.measure.Weighting
import com.eirahoutmoss.nextools.core.measure.WeightingFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class SoundTest {
    private fun db(x: Double) = 20 * log10(x)

    /** IEC 61672 tablo değerleri (dB) ile karşılaştırma. Telefonun kullandığı aralıkta sıkı tolerans. */
    @Test fun aWeightingMatchesStandard() {
        val f = WeightingFilter(Weighting.A, 48_000.0)
        assertEquals(0.0, db(f.magnitudeAt(1000.0)), 0.01)
        assertEquals(-19.1, db(f.magnitudeAt(100.0)), 0.3)
        assertEquals(-8.6, db(f.magnitudeAt(250.0)), 0.3)
        assertEquals(1.2, db(f.magnitudeAt(2000.0)), 0.3)
        assertEquals(1.0, db(f.magnitudeAt(4000.0)), 0.5)
        assertEquals(-30.2, db(f.magnitudeAt(50.0)), 0.5)
    }

    @Test fun cWeightingIsFlatInMidBand() {
        val f = WeightingFilter(Weighting.C, 44_100.0)
        assertEquals(0.0, db(f.magnitudeAt(1000.0)), 0.01)
        assertEquals(-0.3, db(f.magnitudeAt(100.0)), 0.3)
        assertEquals(-3.0, db(f.magnitudeAt(31.5)), 0.4)
    }

    @Test fun zWeightingIsFlat() {
        val f = WeightingFilter(Weighting.Z, 48_000.0)
        assertEquals(1.0, f.magnitudeAt(50.0), 1e-12)
    }

    @Test fun meterReadsSineLevel() {
        val fs = 48_000.0
        val m = SoundLevelMeter(fs, Weighting.A, TimeWeighting.FAST, offsetDb = 0.0)
        val amp = 0.1
        val buf = FloatArray(4800)
        var n = 0
        repeat(20) {
            for (i in buf.indices) { buf[i] = (amp * sin(2 * PI * 1000 * n / fs)).toFloat(); n++ }
            m.process(buf)
        }
        // RMS = amp/√2 → 20log10(0.0707) = −23,0 dB
        assertEquals(-23.0, m.level, 0.2)
        assertEquals(-23.0, m.leq, 0.3)
        assertTrue(!m.clipping)
    }

    @Test fun offsetShiftsLevel() {
        val fs = 48_000.0
        val m = SoundLevelMeter(fs, Weighting.Z, TimeWeighting.SLOW, offsetDb = 100.0)
        val buf = FloatArray(48_000) { (0.5 * sin(2 * PI * 440 * it / fs)).toFloat() }
        m.process(buf); m.process(buf); m.process(buf)
        assertEquals(100.0 + 20 * log10(0.5 / kotlin.math.sqrt(2.0)), m.level, 0.3)
    }

    @Test fun scaleBands() {
        assertEquals(SoundScale.Band.CONVERSATION, SoundScale.band(68.4))
        assertEquals(SoundScale.Band.VERY_QUIET, SoundScale.band(10.0))
        assertTrue(SoundScale.isWarning(90.0))
    }
}
