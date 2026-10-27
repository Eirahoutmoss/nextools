package com.eirahoutmoss.nextools.core

import com.eirahoutmoss.nextools.core.measure.ImageGeometry
import com.eirahoutmoss.nextools.core.measure.MagnetPeak
import com.eirahoutmoss.nextools.core.measure.Meter
import com.eirahoutmoss.nextools.core.measure.MetronomeSynth
import com.eirahoutmoss.nextools.core.measure.Tachometer
import com.eirahoutmoss.nextools.core.measure.WeatherTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ExtraMathTest {
    @Test fun meterAccentsAndParse() {
        val aksak = Meter.PRESETS.first { it.key == "aksak" }
        assertEquals("9/8", aksak.signature)
        assertEquals(listOf(2, 0, 1, 0, 1, 0, 1, 0, 0), (0 until 9).map { aksak.accent(it) })
        assertEquals(listOf(2, 2, 3), Meter.parse("2+2+3")!!.groups)
        assertNull(Meter.parse("2+x"))
        assertNull(Meter.parse(""))
    }

    @Test fun metronomeClickTiming() {
        val fs = 8000
        val m = MetronomeSynth(fs).apply { meter = Meter.PRESETS.first { it.key == "turk_aksagi" }; pulsesPerMinute = 240.0 }
        val pulses = mutableListOf<Int>()
        m.onPulse = { pulses += it }
        val out = FloatArray(fs) // 1 sn → 240/dk = 4 darbe
        m.render(out)
        assertEquals(listOf(0, 1, 2, 3), pulses)
        // Darbeler 2000 örnek arayla başlar
        assertTrue(kotlin.math.abs(out[2000 + 3]) > 0.01 || kotlin.math.abs(out[2000 + 5]) > 0.01)
        assertEquals(0f, out[1500], 1e-6f)
    }

    @Test fun tachometerAndMagnet() {
        assertEquals(60.0, Tachometer.rpm(2 * PI), 1e-9)
        assertEquals(1.0, Tachometer.flatness(0.0, 0.0, 3.0), 1e-9)
        val m = MagnetPeak()
        m.tare(20.0, -5.0, -40.0)
        m.update(20.0, -5.0, 260.0, 2000.0)
        assertEquals(300.0, m.peak, 1e-9)
        m.update(20.0, -5.0, -40.0, 2000.0)
        assertEquals(300.0, m.peak, 1e-9); assertEquals(0.0, m.current, 1e-9)
        assertFalse(m.saturated)
        m.update(1990.0, 0.0, 0.0, 2000.0)
        assertTrue(m.saturated)
    }

    @Test fun weatherTrendFromLog() {
        val now = 10 * 3_600_000L
        // 3 saatte 4,2 hPa düşüş
        val pts = (0..12).map { i -> (now - (12 - i) * 15 * 60_000L) to (1015.0 - 4.2 * i / 12) }
        val r = WeatherTrend.analyze(pts, now)!!
        assertEquals(-4.2, r.change3hHpa, 0.05)
        assertEquals(WeatherTrend.Tendency.QUICK, r.tendency)
        assertEquals(WeatherTrend.Outlook.STORM_POSSIBLE, r.outlook)
        assertNull(WeatherTrend.analyze(pts.takeLast(3), now))
        assertEquals(WeatherTrend.Tendency.STEADY, WeatherTrend.tendency(0.05))
    }

    @Test fun imageGeometry() {
        assertEquals(90.0, ImageGeometry.angle(0.0, 0.0, 10.0, 0.0, 0.0, 5.0), 1e-9)
        assertEquals(45.0, ImageGeometry.angle(0.0, 0.0, 10.0, 0.0, 3.0, 3.0), 1e-9)
        assertEquals(171.2, ImageGeometry.length(100.0, 85.6, 200.0)!!, 1e-9)
        assertNull(ImageGeometry.length(2.0, 85.6, 200.0))
    }
}
