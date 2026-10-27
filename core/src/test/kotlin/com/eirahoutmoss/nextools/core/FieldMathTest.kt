package com.eirahoutmoss.nextools.core

import com.eirahoutmoss.nextools.core.measure.Driving
import com.eirahoutmoss.nextools.core.measure.Flicker
import com.eirahoutmoss.nextools.core.measure.MachineHealth
import com.eirahoutmoss.nextools.core.measure.PressureSpike
import com.eirahoutmoss.nextools.core.measure.Pulse
import com.eirahoutmoss.nextools.core.measure.RoadRoughness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class FieldMathTest {
    @Test fun flickerPercentAndFrequency() {
        val rows = 1000
        val rowTime = 20e-6 // 20 µs/satır → 50 kHz satır hızı
        // 100 Hz, %20 modülasyon + yavaş vinyet
        val x = DoubleArray(rows) { i -> 150.0 * (1 + 0.2 * sin(2 * PI * 100 * i * rowTime)) * (1 - 0.2 * (i - 500.0) * (i - 500.0) / 250_000) }
        val r = Flicker.analyze(x, rowTime)
        assertEquals(20.0, r.percent, 4.0)
        assertEquals(100.0, r.frequencyHz!!, 5.0)
        val steady = Flicker.analyze(DoubleArray(rows) { 120.0 + Random(it).nextDouble(-0.5, 0.5) }, rowTime)
        assertTrue(steady.percent < 2)
        assertEquals(Flicker.Risk.LOW, Flicker.risk(steady.percent))
    }

    @Test fun roadVerticalAndGrades() {
        assertEquals(1.0, RoadRoughness.vertical(0.0, 0.0, 10.80665, 0.0, 0.0, 9.80665), 1e-9)
        assertEquals(RoadRoughness.Grade.SMOOTH, RoadRoughness.grade(0.3))
        assertEquals(RoadRoughness.Grade.VERY_ROUGH, RoadRoughness.grade(3.0))
    }

    @Test fun drivingEventsAndScore() {
        assertEquals(Driving.Event.HARSH_BRAKE, Driving.classify(-0.5, 0.0))
        assertEquals(Driving.Event.SHARP_TURN, Driving.classify(0.0, -0.45))
        assertNull(Driving.classify(0.1, 0.1))
        assertEquals(100, Driving.score(0, 10.0))
        assertEquals(75, Driving.score(10, 10.0))
        assertEquals(0.5, Driving.yawRate(0.0, 0.0, 0.5, 0.0, 0.0, 9.8), 1e-9)
    }

    @Test fun machineSignatureDetectsChange() {
        val fs = 400.0
        fun sig(f: Double, amp: Double, seed: Int) = MachineHealth.signature(DoubleArray(2048) { amp * sin(2 * PI * f * it / fs) + Random(seed + it).nextDouble(-0.02, 0.02) }, fs)
        val base = sig(50.0, 1.0, 1)
        assertEquals(MachineHealth.Verdict.SAME, MachineHealth.compare(base, sig(50.0, 1.02, 2)).verdict)
        assertEquals(MachineHealth.Verdict.CHANGED, MachineHealth.compare(base, sig(120.0, 1.0, 3)).verdict)
        assertEquals(MachineHealth.Verdict.CHANGED, MachineHealth.compare(base, sig(50.0, 2.5, 4)).verdict)
    }

    @Test fun pressureSpikeEvent() {
        val fs = 25.0
        val p = PressureSpike(fs)
        var ev: PressureSpike.Event? = null
        repeat(100) { p.update(1000.0) }
        // 6 Pa sıçrama, 0,4 sn zaman sabitiyle söner
        for (i in 0 until 200) {
            val v = 1000.0 + 0.06 * kotlin.math.exp(-i / (0.4 * fs))
            p.update(v)?.let { ev = it }
        }
        assertNotNull(ev)
        assertEquals(6.0, ev!!.peakPa, 0.3)
        assertEquals(0.4, ev!!.decayS!!, 0.1)
    }

    @Test fun pulseFromSyntheticPpg() {
        val t = DoubleArray(300) { it / 30.0 + Random(it).nextDouble(0.0, 0.005) }
        val v = DoubleArray(300) { 180 + 2 * sin(2 * PI * 1.2 * t[it]) + 0.01 * it }
        val r = Pulse.estimate(t, v)!!
        assertEquals(72.0, r.bpm, 2.0)
        assertTrue(r.quality > 0.3)
        assertTrue(Pulse.fingerPresent(200, 40, 30))
        assertNull(Pulse.estimate(t.take(60).toDoubleArray(), v.take(60).toDoubleArray()))
    }
}
