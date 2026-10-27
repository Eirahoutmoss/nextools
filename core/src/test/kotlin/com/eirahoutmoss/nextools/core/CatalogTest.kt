package com.eirahoutmoss.nextools.core

import com.eirahoutmoss.nextools.core.catalog.Availability
import com.eirahoutmoss.nextools.core.catalog.DeviceCapabilities
import com.eirahoutmoss.nextools.core.catalog.Hardware
import com.eirahoutmoss.nextools.core.catalog.Presence
import com.eirahoutmoss.nextools.core.catalog.ToolCatalog
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.catalog.ToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {

    @Test fun everyToolIdHasExactlyOneSpec() {
        assertEquals(ToolId.entries.size, ToolCatalog.all.size)
        assertEquals(ToolId.entries.toSet(), ToolCatalog.all.map { it.id }.toSet())
    }

    @Test fun keysAreUnique() {
        assertEquals(ToolId.entries.size, ToolId.entries.map { it.key }.toSet().size)
        assertEquals(Hardware.entries.size, Hardware.entries.map { it.id }.toSet().size)
    }

    @Test fun implementedToolsMatchReleasePlan() {
        val done = ToolCatalog.all.filter { it.implemented }.map { it.id }.toSet()
        assertEquals(
            setOf(
                ToolId.DEVICE_REPORT, ToolId.LEVEL, ToolId.COMPASS, ToolId.MAGNETOMETER, ToolId.METAL_FINDER,
                ToolId.BAROMETER, ToolId.ALTIMETER, ToolId.LUX, ToolId.SOUND_LEVEL, ToolId.SENSOR_TEST,
                ToolId.VIBRATION, ToolId.SEISMOGRAPH, ToolId.SPECTRUM, ToolId.TUNER, ToolId.TONE_GENERATOR,
                ToolId.FLOOR_COUNTER, ToolId.COLOR_PICKER, ToolId.MAGNIFIER, ToolId.QR,
                ToolId.GPS_STATUS, ToolId.AREA, ToolId.TRACK, ToolId.WIFI_ANALYZER, ToolId.BLE_SCANNER,
                ToolId.NFC_READER, ToolId.BATTERY, ToolId.SCREEN_TEST,
                ToolId.RT60, ToolId.FLICKER, ToolId.ROAD_QUALITY, ToolId.DRIVING_PROFILE, ToolId.MACHINE_HEALTH,
                ToolId.AIRTIGHTNESS, ToolId.PULSE,
                ToolId.PRECISE_POSITION, ToolId.GPS_SPOOF, ToolId.UWB_RANGE, ToolId.WIFI_RTT_RANGE, ToolId.ID_CARD,
                ToolId.SPECTROSCOPE, ToolId.COSMIC_RAY,
                ToolId.MAGNET_STRENGTH, ToolId.TACHOMETER, ToolId.WEATHER_TREND, ToolId.METRONOME,
                ToolId.SPEAKER_MIC_TEST, ToolId.PROTRACTOR, ToolId.RULER,
                ToolId.GAIT, ToolId.COLOR_TEMPERATURE, ToolId.PH_READER, ToolId.SONAR, ToolId.SPEED_VIDEO, ToolId.IR_REMOTE,
            ),
            done,
        )
    }

    @Test fun missingBarometerMakesBarometerToolsPassive() {
        val caps = DeviceCapabilities.of(Hardware.ACCELEROMETER, Hardware.MAGNETOMETER, Hardware.LIGHT, Hardware.MICROPHONE)
        val s = Availability.evaluate(ToolCatalog[ToolId.ALTIMETER], caps)
        assertTrue(s is ToolStatus.MissingHardware)
        assertEquals(setOf(Hardware.BAROMETER), (s as ToolStatus.MissingHardware).missing)
        assertTrue(Availability.evaluate(ToolCatalog[ToolId.LEVEL], caps) is ToolStatus.Ready)
    }

    @Test fun luxWithoutLightSensorUsesCameraMode() {
        val caps = DeviceCapabilities.of(Hardware.CAMERA)
        val s = Availability.evaluate(ToolCatalog[ToolId.LUX], caps)
        assertTrue(s is ToolStatus.Ready)
        s as ToolStatus.Ready
        assertTrue(s.fallback)
        assertEquals("camera", s.requirement.mode)
    }

    @Test fun luxWithNeitherIsMissing() {
        val s = Availability.evaluate(ToolCatalog[ToolId.LUX], DeviceCapabilities.of())
        assertTrue(s is ToolStatus.MissingHardware)
    }

    @Test fun unknownPresenceKeepsToolOpen() {
        val presence = Hardware.entries.associateWith { Presence.ABSENT }.toMutableMap()
        presence[Hardware.GNSS] = Presence.PRESENT
        presence[Hardware.GNSS_RAW] = Presence.UNKNOWN
        val s = Availability.evaluate(ToolCatalog[ToolId.GPS_SPOOF], DeviceCapabilities(presence))
        assertTrue(s is ToolStatus.Ready && s.uncertain)
    }

    @Test fun summaryCountsHardwareNotImplementation() {
        val caps = DeviceCapabilities.of(*Hardware.entries.toTypedArray())
        val sum = Availability.summarize(Availability.evaluateAll(caps))
        assertEquals(sum.total, sum.hardwareOk)
        assertEquals(54, sum.usableNow)
    }

    @Test fun noHardwareStillAllowsDeviceTools() {
        val sum = Availability.summarize(Availability.evaluateAll(DeviceCapabilities.of()))
        // Cihaz Karnesi, Sensör Testi, Batarya ve Ekran Testi donanım istemez.
        assertEquals(4, sum.usableNow)
    }
}
