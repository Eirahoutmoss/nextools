package com.eirahoutmoss.nextools.core

import com.eirahoutmoss.nextools.core.measure.ColorMath
import com.eirahoutmoss.nextools.core.measure.Fft
import com.eirahoutmoss.nextools.core.measure.Floors
import com.eirahoutmoss.nextools.core.measure.Notes
import com.eirahoutmoss.nextools.core.measure.Spectra
import com.eirahoutmoss.nextools.core.measure.StaLta
import com.eirahoutmoss.nextools.core.measure.ToneSynth
import com.eirahoutmoss.nextools.core.measure.VibrationMath
import com.eirahoutmoss.nextools.core.measure.Yin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class DspTest {
    @Test fun fftFindsSinePeakAndAmplitude() {
        val fs = 1000.0; val n = 1024
        val x = DoubleArray(n) { 2.0 * sin(2 * PI * 123.4 * it / fs) + 0.3 }
        val spec = Fft.amplitudeSpectrum(x)
        val p = Spectra.dominant(spec, fs, n)!!
        assertEquals(123.4, p.hz, 0.3)
        assertEquals(2.0, p.amplitude, 0.25)
    }

    @Test fun vibrationRmsAndFrequency() {
        val fs = 400.0
        val x = DoubleArray(512) { 1.5 * sin(2 * PI * 50 * it / fs) }
        val r = VibrationMath.analyze(x, fs)
        assertEquals(1.5 / sqrt(2.0), r.rms, 0.02)
        assertEquals(50.0, r.dominant!!.hz, 0.5)
    }

    @Test fun yinFindsPitch() {
        val fs = 44_100.0; val n = 4096
        for (f in listOf(82.41, 196.0, 440.0, 1046.5)) {
            val x = FloatArray(n) { (0.4 * sin(2 * PI * f * it / fs) + 0.2 * sin(4 * PI * f * it / fs)).toFloat() }
            val r = Yin(fs, n).detect(x)
            assertNotNull("f=$f", r)
            assertEquals(f, r!!.hz, f * 0.003)
        }
        assertNull(Yin(fs, n).detect(FloatArray(n)))
    }

    @Test fun noteNames() {
        val a = Notes.fromHz(440.0)
        assertEquals(9, a.pitchClass); assertEquals(4, a.octave); assertEquals(0.0, a.cents, 1e-9)
        val e2 = Notes.fromHz(82.41)
        assertEquals(4, e2.pitchClass); assertEquals(2, e2.octave)
        val sharp = Notes.fromHz(445.0)
        assertEquals(9, sharp.pitchClass); assertEquals(19.6, sharp.cents, 0.2)
        assertEquals(0, Notes.fromHz(432.0 * 2.0.let { Math.pow(2.0, 3.0 / 12) }, a4 = 432.0).pitchClass)
    }

    @Test fun staLtaTriggersOnEvent() {
        val fs = 100.0
        val t = StaLta(fs)
        var events = 0
        repeat(2000) { if (t.update(0.01 + (it % 3) * 0.001)) events++ }
        assertEquals(0, events)
        repeat(100) { if (t.update(0.5)) events++ }
        assertEquals(1, events)
        assertTrue(t.active)
        repeat(3000) { t.update(0.01) }
        assertFalse(t.active)
    }

    @Test fun floors() {
        assertEquals(0, Floors.floors(1.2))
        assertEquals(1, Floors.floors(2.9))
        assertEquals(-2, Floors.floors(-6.2))
        assertEquals(3, Floors.floors(10.6, 3.5))
    }

    @Test fun synthProducesBoundedSignal() {
        val s = ToneSynth(48_000)
        val out = FloatArray(48_000)
        for (w in ToneSynth.Wave.entries) {
            s.wave = w; s.volume = 1.0
            s.render(out)
            assertTrue(w.name, out.all { it in -1f..1f })
            assertTrue(w.name, out.drop(10_000).any { kotlin.math.abs(it) > 0.1f })
        }
        s.wave = ToneSynth.Wave.SWEEP; s.sweepFrom = 100.0; s.sweepTo = 1000.0; s.sweepSeconds = 2.0; s.restartSweep()
        s.render(FloatArray(48_000))
        assertEquals(316.2, s.currentHz, 2.0)
    }

    @Test fun ciede2000ReferenceValues() {
        // Sharma ve ark. 2005, tablo 1, çift 1 ve 7
        assertEquals(2.0425, ColorMath.deltaE2000(ColorMath.Lab(50.0, 2.6772, -79.7751), ColorMath.Lab(50.0, 0.0, -82.7485)), 1e-4)
        assertEquals(2.3669, ColorMath.deltaE2000(ColorMath.Lab(50.0, 0.0, 0.0), ColorMath.Lab(50.0, -1.0, 2.0)), 1e-4)
    }

    @Test fun labAndNearest() {
        val w = ColorMath.lab(255, 255, 255)
        assertEquals(100.0, w.l, 0.01); assertEquals(0.0, w.a, 0.01)
        assertEquals("#1E7BF0", ColorMath.hex(30, 123, 240))
        assertEquals("red", ColorMath.nearest(230, 20, 25).first.key)
        assertEquals("black", ColorMath.nearest(5, 5, 8).first.key)
    }
}
