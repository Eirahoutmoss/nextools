package com.eirahoutmoss.nextools.core

import com.eirahoutmoss.nextools.core.geo.Geo
import com.eirahoutmoss.nextools.core.geo.Gpx
import com.eirahoutmoss.nextools.core.geo.LatLon
import com.eirahoutmoss.nextools.core.geo.TrackStats
import com.eirahoutmoss.nextools.core.radio.Band
import com.eirahoutmoss.nextools.core.radio.BleMath
import com.eirahoutmoss.nextools.core.radio.WifiChannels
import com.eirahoutmoss.nextools.core.radio.toHexId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoRadioTest {
    @Test fun distanceKnownPair() {
        // Ankara Kızılay – İstanbul Taksim ≈ 351 km
        val d = Geo.distance(LatLon(39.9208, 32.8541), LatLon(41.0369, 28.9850))
        assertEquals(351_000.0, d, 3_000.0)
        assertEquals(111_195.0, Geo.distance(LatLon(0.0, 0.0), LatLon(1.0, 0.0)), 5.0)
    }

    @Test fun areaOf100mSquare() {
        val lat = 38.0; val lon = 35.0
        val dLat = 100.0 / 111_195.0
        val dLon = dLat / kotlin.math.cos(Math.toRadians(lat))
        val sq = listOf(LatLon(lat, lon), LatLon(lat, lon + dLon), LatLon(lat + dLat, lon + dLon), LatLon(lat + dLat, lon))
        assertEquals(10_000.0, Geo.area(sq), 30.0)
        assertEquals(400.0, Geo.perimeter(sq), 1.0)
        assertEquals(10.0, Geo.area(sq) / Geo.M2_PER_DONUM, 0.05)
        assertEquals(0.0, Geo.area(sq.take(2)), 0.0)
    }

    @Test fun localProjection() {
        val pts = Geo.toLocal(listOf(LatLon(38.0, 35.0), LatLon(38.0, 35.001)))
        assertEquals(87.6, pts[1].first, 0.5)
        assertEquals(0.0, pts[1].second, 1e-6)
    }

    @Test fun trackStatsIgnoresJitterAndCountsClimb() {
        val t = TrackStats()
        assertTrue(t.add(LatLon(38.0, 35.0), 1000.0, 1.0, 5.0))
        assertTrue(!t.add(LatLon(38.00001, 35.0), 1000.5, 0.0, 5.0)) // 1 m titreme
        assertTrue(!t.add(LatLon(38.01, 35.0), 1000.0, 1.0, 80.0)) // kötü doğruluk
        for (i in 1..10) t.add(LatLon(38.0 + i * 0.001, 35.0), 1000.0 + i * 2, 1.5, 5.0)
        assertEquals(1112.0, t.distanceM, 5.0)
        assertEquals(20.0, t.ascentM, 0.01)
    }

    @Test fun gpxIsWellFormed() {
        val sb = StringBuilder()
        Gpx.write("Yürüyüş <1>", listOf(Gpx.Point(38.1234567, 35.7654321, 1001.0, 0L)), sb)
        val s = sb.toString()
        assertTrue(s.contains("<trkpt lat=\"38.1234567\" lon=\"35.7654321\"><ele>1001.0</ele><time>1970-01-01T00:00:00Z</time></trkpt>"))
        assertTrue(s.contains("Yürüyüş &lt;1&gt;"))
    }

    @Test fun wifiChannels() {
        assertEquals(1, WifiChannels.channel(2412)); assertEquals(6, WifiChannels.channel(2437)); assertEquals(14, WifiChannels.channel(2484))
        assertEquals(36, WifiChannels.channel(5180)); assertEquals(149, WifiChannels.channel(5745))
        assertEquals(Band.GHZ_6, WifiChannels.band(5975)); assertEquals(5, WifiChannels.channel(5975))
    }

    @Test fun recommendsEmptiestNonOverlappingChannel() {
        val aps = listOf(WifiChannels.Ap(2412, 20, -40), WifiChannels.Ap(2417, 20, -50), WifiChannels.Ap(2437, 20, -55), WifiChannels.Ap(2462, 20, -85))
        assertEquals(11, WifiChannels.recommend(Band.GHZ_2_4, aps)!!.first)
        // Kanal 2 (2417) kanal 1'i de kanal 6'yı da kısmen bozar
        assertTrue(WifiChannels.congestion(2412, aps) > WifiChannels.congestion(2462, aps))
    }

    @Test fun bleDistanceAndHex() {
        assertEquals(1.0, BleMath.distanceM(-59, null), 1e-9)
        assertEquals(10.0, BleMath.distanceM(-84, -59, n = 2.5), 0.01)
        assertEquals("04:A2:FF", byteArrayOf(4, 0xA2.toByte(), -1).toHexId())
    }
}
