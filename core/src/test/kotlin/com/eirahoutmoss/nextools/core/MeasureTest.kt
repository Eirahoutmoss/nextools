package com.eirahoutmoss.nextools.core

import com.eirahoutmoss.nextools.core.measure.AngleLowPass
import com.eirahoutmoss.nextools.core.measure.Angles
import com.eirahoutmoss.nextools.core.measure.Barometric
import com.eirahoutmoss.nextools.core.measure.CompassMath
import com.eirahoutmoss.nextools.core.measure.LevelMath
import com.eirahoutmoss.nextools.core.measure.LuxMath
import com.eirahoutmoss.nextools.core.measure.MetalDetector
import com.eirahoutmoss.nextools.core.measure.RateMeter
import com.eirahoutmoss.nextools.core.measure.RunningStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MeasureTest {
    private val g = 9.80665

    @Test fun flatPhoneReadsZero() {
        val f = LevelMath.flat(0.0, 0.0, g)
        assertEquals(0.0, f.x, 1e-9); assertEquals(0.0, f.y, 1e-9); assertEquals(0.0, f.tilt, 1e-9)
    }

    @Test fun rightEdgeRaisedGivesPositiveX() {
        val a = 5.0 * PI / 180
        val f = LevelMath.flat(g * sin(a), 0.0, g * cos(a))
        assertEquals(5.0, f.x, 1e-6)
        assertEquals(5.0, f.tilt, 1e-6)
    }

    @Test fun edgeModeDeviationFromNearestRightAngle() {
        val a = 92.0 * PI / 180
        val e = LevelMath.edge(g * sin(a), g * cos(a))
        assertEquals(90, e.nearest)
        assertEquals(2.0, e.deviation, 1e-6)
        val up = LevelMath.edge(g * sin(-1.0 * PI / 180), g * cos(-1.0 * PI / 180))
        assertEquals(0, up.nearest)
        assertEquals(-1.0, up.deviation, 1e-6)
    }

    @Test fun modeHasHysteresis() {
        assertEquals(LevelMath.Mode.FLAT, LevelMath.chooseMode(LevelMath.Mode.FLAT, 45.0))
        assertEquals(LevelMath.Mode.EDGE, LevelMath.chooseMode(LevelMath.Mode.EDGE, 45.0))
        assertEquals(LevelMath.Mode.EDGE, LevelMath.chooseMode(LevelMath.Mode.FLAT, 60.0))
    }

    @Test fun percentSlope() {
        assertEquals(100.0, LevelMath.percent(45.0)!!, 1e-9)
        assertNull(LevelMath.percent(89.5))
    }

    @Test fun angleNormalization() {
        assertEquals(350.0, Angles.normalize360(-10.0), 1e-9)
        assertEquals(-170.0, Angles.normalize180(190.0), 1e-9)
    }

    @Test fun angleLowPassCrossesNorthCorrectly() {
        val f = AngleLowPass(0.5)
        f.update(359.0, 0)
        val v = f.update(1.0, 500_000_000)
        assertTrue("v=$v", v > 358.0 || v < 2.0)
    }

    @Test fun compassPoints() {
        assertEquals(0, CompassMath.pointIndex(359.0))
        assertEquals(4, CompassMath.pointIndex(90.0))
        assertEquals(8, CompassMath.pointIndex(181.0))
        assertEquals(10.0, CompassMath.trueHeading(5.0, 5.0), 1e-9)
        assertEquals(355.0, CompassMath.trueHeading(5.0, -10.0), 1e-9)
        assertEquals(CompassMath.FieldQuality.DISTURBED, CompassMath.fieldQuality(120.0, null))
        assertEquals(CompassMath.FieldQuality.NORMAL, CompassMath.fieldQuality(46.0, 45.0))
    }

    @Test fun barometricRoundTrip() {
        assertEquals(0.0, Barometric.altitude(Barometric.STANDARD_SEA_LEVEL_HPA), 1e-9)
        val p = 899.0
        val h = Barometric.altitude(p)
        assertEquals(1000.0, h, 15.0) // ~988 m
        val slp = Barometric.seaLevelPressure(p, h)
        assertEquals(Barometric.STANDARD_SEA_LEVEL_HPA, slp, 0.01)
    }

    @Test fun relativeAltitudeFromReference() {
        val ref = Barometric.Reference(pressureHpa = 1000.0, altitudeM = 100.0, setAtMs = 0)
        // ~0.12 hPa düşüş ≈ 1 m yükseliş
        val h = Barometric.altitudeFrom(ref, 1000.0 - 0.1185)
        assertEquals(101.0, h, 0.1)
    }

    @Test fun pressureTrend() {
        val now = 10 * Barometric.HOUR_MS
        val samples = (0..36).map { i -> (now - 3 * Barometric.HOUR_MS + i * 300_000L) to (1010.0 - i * (4.0 / 36)) }
        val d = Barometric.deltaPer3h(samples, now)
        assertNotNull(d)
        assertEquals(Barometric.Trend.FALLING_FAST, Barometric.trend(d!!))
        assertNull(Barometric.deltaPer3h(samples.takeLast(5), now))
    }

    @Test fun runningStats() {
        val s = RunningStats()
        listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0).forEach(s::add)
        assertEquals(5.0, s.average, 1e-9)
        assertEquals(2.0, s.min, 1e-9); assertEquals(9.0, s.max, 1e-9)
        assertEquals(2.138, s.stdDev, 1e-3)
        s.add(Double.NaN)
        assertEquals(8, s.count)
    }

    @Test fun luxCalibration() {
        assertEquals(1.25, LuxMath.calibrationFactor(500.0, 400.0)!!, 1e-9)
        assertNull(LuxMath.calibrationFactor(500.0, 0.0))
        assertNull(LuxMath.calibrationFactor(5000.0, 10.0))
        assertEquals(LuxMath.Verdict.LOW, LuxMath.verdict(200.0, LuxMath.Recommendation.OFFICE))
        assertEquals(LuxMath.Verdict.OK, LuxMath.verdict(450.0, LuxMath.Recommendation.OFFICE))
    }

    @Test fun metalDetectorFindsAnomalyAfterCalibration() {
        val d = MetalDetector()
        val base = List(40) { doubleArrayOf(20.0 + (it % 2) * 0.1, -5.0, -40.0) }
        val c = d.calibrate(base)
        assertTrue(c is MetalDetector.Calibration.Ok)
        var r: MetalDetector.Reading? = null
        repeat(30) { r = d.update(20.0, -5.0, -40.0) }
        assertTrue(!r!!.detected)
        repeat(30) { r = d.update(45.0, -5.0, -40.0) }
        assertTrue(r!!.detected)
        assertTrue(r!!.level > 0.2)
    }

    @Test fun metalDetectorRejectsMovingPhone() {
        val d = MetalDetector()
        val moving = List(40) { doubleArrayOf(20.0 + it, -5.0, -40.0) }
        assertEquals(MetalDetector.Calibration.Unsteady, d.calibrate(moving))
        assertEquals(MetalDetector.Calibration.TooFewSamples, d.calibrate(moving.take(5)))
    }

    @Test fun rateMeter() {
        val r = RateMeter()
        for (i in 0..200) r.add(i * 10_000_000L) // 100 Hz
        assertEquals(100.0, r.hz!!, 0.5)
    }
}
