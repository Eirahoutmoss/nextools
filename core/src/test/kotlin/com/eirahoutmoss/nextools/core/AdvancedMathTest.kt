package com.eirahoutmoss.nextools.core

import com.eirahoutmoss.nextools.core.measure.CosmicDetector
import com.eirahoutmoss.nextools.core.measure.PositionAverager
import com.eirahoutmoss.nextools.core.measure.RangeFilter
import com.eirahoutmoss.nextools.core.measure.SpoofCheck
import com.eirahoutmoss.nextools.core.measure.Spectro
import com.eirahoutmoss.nextools.core.measure.UwbPairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.random.Random

class AdvancedMathTest {
    @Test fun averagerEllipseAlongEast() {
        val a = PositionAverager()
        val r = Random(1)
        val lat = 39.0; val lon = 32.0
        val mPerDegLon = PI / 180 * 6_371_008.8 * kotlin.math.cos(lat * PI / 180)
        repeat(600) { i ->
            val e = r.nextDouble(-1.0, 1.0) * 6.0   // doğu-batı yayılımı geniş
            val n = r.nextDouble(-1.0, 1.0) * 1.0
            a.add(lat + n / 111_195.0, lon + e / mPerDegLon, 900.0 + r.nextDouble(-2.0, 2.0), 4.0, i.toDouble())
        }
        val res = a.result()!!
        assertEquals(lat, res.lat, 1e-5); assertEquals(lon, res.lon, 1e-5)
        assertTrue(res.scatter.majorM > 3 * res.scatter.minorM)
        assertEquals(90.0, res.scatter.orientationDeg, 10.0)
        // 600 sn / 60 sn ≈ 11 bağımsız örnek → belirsizlik dağılımın ~1/3'ü
        val radial = kotlin.math.sqrt(res.scatter.majorM * res.scatter.majorM + res.scatter.minorM * res.scatter.minorM)
        assertEquals(radial / kotlin.math.sqrt(11.0), res.meanUncertaintyM, 0.3)
        assertEquals(900.0, res.altitudeM!!, 0.5)
    }

    @Test fun spoofSigns() {
        assertTrue(SpoofCheck.Sign.UNIFORM_CN0 in SpoofCheck.cn0Signs(List(8) { 40.0 + it * 0.1 }))
        assertTrue(SpoofCheck.cn0Signs(listOf(22.0, 28.0, 35.0, 41.0, 44.0, 31.0, 38.0)).isEmpty())
        assertTrue(SpoofCheck.Sign.HIGH_CN0 in SpoofCheck.cn0Signs(listOf(53.0, 55.0, 54.0, 53.0, 40.0, 30.0)))
        assertTrue(SpoofCheck.isJump(5000.0, 1.0, 5.0))
        assertFalse(SpoofCheck.isJump(30.0, 1.0, 5.0))
        assertTrue(SpoofCheck.isTimeMismatch(0, 60_000))
        assertEquals(SpoofCheck.Level.WARNING, SpoofCheck.level(listOf(SpoofCheck.Sign.MOCK_LOCATION)))
        assertEquals(SpoofCheck.Level.NOTICE, SpoofCheck.level(listOf(SpoofCheck.Sign.HIGH_CN0)))
    }

    @Test fun spectroPeaksAndCalibration() {
        // 400 piksel; 436 nm → 100. piksel, 546 nm → 300. piksel
        val cal = Spectro.Calibration.fromTwo(100.0, 436.6, 300.0, 546.5)!!
        val x = DoubleArray(450) { i -> 5 + 100 * exp(-((i - 100.3) * (i - 100.3)) / 8) + 60 * exp(-((i - 300.0) * (i - 300.0)) / 8) + 40 * exp(-((i - 418.5) * (i - 418.5)) / 8) }
        val p = Spectro.peaks(x)
        assertEquals(3, p.size)
        assertEquals(100.3, p[0], 0.2)
        assertEquals(611.6, cal.nm(Spectro.peakNear(x, 420)!!), 2.0)
        assertNull(Spectro.Calibration.fromTwo(100.0, 436.0, 102.0, 546.0))
        assertEquals(1.0, Spectro.similarity(Spectro.normalize(x), Spectro.normalize(x)), 1e-9)
    }

    @Test fun cosmicMasksHotPixelsAndCountsHits() {
        val w = 64; val h = 48
        val d = CosmicDetector(w, h, learnFrames = 5, threshold = 40)
        fun frame(extra: (ByteArray) -> Unit = {}): ByteArray = ByteArray(w * h) { 3 }.also { it[10 * w + 10] = 200.toByte(); extra(it) }
        repeat(5) { assertTrue(d.process(frame(), w).isEmpty()) }
        assertEquals(1, d.maskedPixels)
        assertTrue(d.process(frame(), w).isEmpty())
        val hits = d.process(frame { f -> f[30 * w + 40] = 180.toByte(); f[30 * w + 41] = 120.toByte(); f[5 * w + 50] = 90.toByte() }, w)
        assertEquals(2, hits.size)
        assertEquals(2, hits.first { it.x == 40 }.size)
    }

    @Test fun rangeFilterAndPairing() {
        val f = RangeFilter(5)
        listOf(1.0, 1.2, 9.0, 1.1, 1.05).forEach { f.add(it) }
        assertEquals(1.1, f.median!!, 1e-9)
        val p = UwbPairing(byteArrayOf(0x12, 0x34), 9, 10, 4242, ByteArray(8) { it.toByte() })
        assertEquals(p, UwbPairing.decode(p.encode()))
        assertNull(UwbPairing.decode("NXUWB1;adr=12;ch=9"))
        assertNotNull(UwbPairing.decode(p.encode().lowercase().replace("nxuwb1", "NXUWB1")))
    }
}
