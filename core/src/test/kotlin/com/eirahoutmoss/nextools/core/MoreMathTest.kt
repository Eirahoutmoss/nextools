package com.eirahoutmoss.nextools.core

import com.eirahoutmoss.nextools.core.measure.CameraLux
import com.eirahoutmoss.nextools.core.measure.Cct
import com.eirahoutmoss.nextools.core.measure.ColorMath
import com.eirahoutmoss.nextools.core.measure.GaitAnalyzer
import com.eirahoutmoss.nextools.core.measure.GateTimer
import com.eirahoutmoss.nextools.core.measure.IrEncoder
import com.eirahoutmoss.nextools.core.measure.PhScale
import com.eirahoutmoss.nextools.core.measure.Sonar
import com.eirahoutmoss.nextools.core.measure.Turbidity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MoreMathTest {
    @Test fun gaitStepsCadenceAsymmetry() {
        val g = GaitAnalyzer()
        val fs = 100.0
        // Adımlar sırayla 0,50 ve 0,56 sn: ortalama 0,53 sn → ~113 adım/dk, asimetri ~%11
        var t = 0.0; var k = 0
        val stepStarts = mutableListOf<Double>()
        while (t < 30) { stepStarts += t; t += if (k++ % 2 == 0) 0.50 else 0.56 }
        for (i in 0 until (30 * fs).toInt()) {
            val ts = i / fs
            val s = stepStarts.lastOrNull { it <= ts } ?: 0.0
            val phase = (ts - s) / 0.25
            val bump = if (phase < 1) 4 * sin(PI * phase) else 0.0
            g.add(9.81 + bump, (ts * 1e9).toLong())
        }
        val r = g.result()!!
        assertEquals(113.2, r.cadence, 3.0)
        assertEquals(11.3, r.asymmetryPct, 2.5)
        assertTrue(r.steps in 50..60)
    }

    @Test fun cctOfWhiteAndWarm() {
        assertEquals(6504.0, Cct.fromSrgb(255.0, 255.0, 255.0)!!.kelvin, 60.0)
        assertTrue(Cct.fromSrgb(255.0, 180.0, 100.0)!!.kelvin < 3500)
    }

    @Test fun phInterpolation() {
        val seven = PhScale.GENERIC.first { it.ph == 7.0 }.lab
        assertEquals(7.0, PhScale.estimate(seven, PhScale.GENERIC)!!.ph, 0.01)
        val mid = ColorMath.lab(0x5F, 0xBE, 0x45) // 6 ile 7 arası
        val e = PhScale.estimate(mid, PhScale.GENERIC)!!
        assertTrue(e.ph in 6.0..7.0)
        assertEquals(Turbidity.Level.CLOUDY, Turbidity.level(8.0))
    }

    @Test fun sonarFindsEcho() {
        val fs = 48_000
        val ch = Sonar.chirp(fs, 2000.0, 10_000.0, 0.005)
        val c = Sonar.soundSpeed(20.0)
        val delay = (2 * 1.5 / c * fs).toInt() // 1,5 m
        val x = DoubleArray(6000)
        for (i in ch.indices) { x[500 + i] += ch[i]; x[500 + delay + i] += 0.25 * ch[i] }
        val r = Sonar.echo(Sonar.correlate(x, ch, 4000), fs, c)!!
        assertEquals(1.5, r.distanceM, 0.02)
        assertTrue(r.confidence > 5)
    }

    @Test fun gateTimer() {
        val g = GateTimer(10.0)
        g.feed(100.0, 100.0, 0)
        g.feed(101.0, 99.0, 33_000_000)
        g.feed(140.0, 100.0, 66_000_000)
        assertEquals(GateTimer.State.FIRST_HIT, g.state)
        g.feed(100.0, 100.0, 1_000_000_000)
        g.feed(100.0, 60.0, 1_566_000_000)
        assertEquals(1.5, g.resultS!!, 1e-9)
        assertEquals(1, g.direction)
    }

    @Test fun irPatterns() {
        val nec = IrEncoder.nec(0x04, 0x08)
        assertEquals(67, nec.pattern.size)
        assertEquals(38_000, nec.carrierHz)
        val sony = IrEncoder.sirc12(1, 21)
        assertEquals(3 * 26, sony.pattern.size)
        val rc5 = IrEncoder.rc5(0, 12, false)
        assertTrue(rc5.pattern.all { it == 889 || it == 1778 })
        assertNotNull(rc5.pattern.firstOrNull())
    }

    @Test fun cameraLux() {
        assertEquals(1000.0, CameraLux.fromExposure(2.0, 0.01, 100.0)!!, 1e-6)
    }
}
