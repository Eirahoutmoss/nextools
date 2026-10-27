package com.eirahoutmoss.nextools.core.catalog

import com.eirahoutmoss.nextools.core.catalog.Hardware.ACCELEROMETER
import com.eirahoutmoss.nextools.core.catalog.Hardware.BAROMETER
import com.eirahoutmoss.nextools.core.catalog.Hardware.BLUETOOTH_LE
import com.eirahoutmoss.nextools.core.catalog.Hardware.CAMERA
import com.eirahoutmoss.nextools.core.catalog.Hardware.FLASH
import com.eirahoutmoss.nextools.core.catalog.Hardware.GNSS
import com.eirahoutmoss.nextools.core.catalog.Hardware.GNSS_RAW
import com.eirahoutmoss.nextools.core.catalog.Hardware.GYROSCOPE
import com.eirahoutmoss.nextools.core.catalog.Hardware.IR_EMITTER
import com.eirahoutmoss.nextools.core.catalog.Hardware.LIGHT
import com.eirahoutmoss.nextools.core.catalog.Hardware.MAGNETOMETER
import com.eirahoutmoss.nextools.core.catalog.Hardware.MICROPHONE
import com.eirahoutmoss.nextools.core.catalog.Hardware.NFC
import com.eirahoutmoss.nextools.core.catalog.Hardware.SPEAKER
import com.eirahoutmoss.nextools.core.catalog.Hardware.UWB
import com.eirahoutmoss.nextools.core.catalog.Hardware.WIFI
import com.eirahoutmoss.nextools.core.catalog.Hardware.WIFI_RTT

/** Ana ekrandaki kategori sekmeleri (taslak bölüm 3 ve 5). Sıra sekme sırasıdır. */
enum class Category(val id: String) {
    MOTION("motion"),
    MAGNETIC("magnetic"),
    PRESSURE("pressure"),
    LIGHT("light"),
    SOUND("sound"),
    CAMERA("camera"),
    LOCATION("location"),
    WIRELESS("wireless"),
    DEVICE("device"),
}

/** Taslaktaki rozetler: 🟢 her telefonda · 🟡 çoğu telefonda · 🔴 az telefonda. */
enum class Rarity { EVERY_PHONE, MOST_PHONES, FEW_PHONES }

/**
 * Bir aracın çalışma yolu: [hardware] kümesinin TAMAMI gerekir.
 * [implemented] = bu yol uygulamada yazıldı mı (ör. lüksmetrenin kamera yedek modu sonraki sürümde).
 */
data class Requirement(val hardware: Set<Hardware>, val mode: String = "default", val implemented: Boolean = true)

/**
 * Bir aracın değişmez tanımı. Ad ve açıklamalar uygulamada dil dosyalarından gelir;
 * burada yalnızca davranışı belirleyen bilgiler durur.
 *
 * @param requirements Sıralı çalışma yolları; ilki asıl yoldur. Boş liste = donanım gerekmez.
 */
data class ToolSpec(
    val id: ToolId,
    val category: Category,
    val rarity: Rarity,
    val requirements: List<Requirement>,
    val implemented: Boolean,
    val calibrationRecommended: Boolean = false,
    /** Kaydı Defter'e alınabilir mi. */
    val recordable: Boolean = true,
)

/** Kalıcı araç kimlikleri. [key] kayıt dosyalarında ve ayarlarda geçer; değiştirilmez. */
enum class ToolId(val key: String) {
    // 3.1 Hareket ve Titreşim
    LEVEL("level"), VIBRATION("vibration"), SEISMOGRAPH("seismograph"), MACHINE_HEALTH("machine_health"),
    ROAD_QUALITY("road_quality"), DRIVING_PROFILE("driving_profile"), PROTRACTOR("protractor"),
    GAIT("gait"), TACHOMETER("tachometer"),

    // 3.2 Manyetik Alan
    COMPASS("compass"), MAGNETOMETER("magnetometer"), METAL_FINDER("metal_finder"), MAGNET_STRENGTH("magnet_strength"),

    // 3.3 Basınç ve Yükseklik
    BAROMETER("barometer"), ALTIMETER("altimeter"), FLOOR_COUNTER("floor_counter"),
    WEATHER_TREND("weather_trend"), AIRTIGHTNESS("airtightness"),

    // 3.4 Işık ve Renk
    LUX("lux"), FLICKER("flicker"), COLOR_PICKER("color_picker"), COLOR_TEMPERATURE("color_temperature"),
    SPECTROSCOPE("spectroscope"), PH_READER("ph_reader"),

    // 3.5 Ses ve Akustik
    SOUND_LEVEL("sound_level"), SPECTRUM("spectrum"), TUNER("tuner"), TONE_GENERATOR("tone_generator"),
    RT60("rt60"), SONAR("sonar"), METRONOME("metronome"),

    // 3.6 Kamera ile Ölçüm
    RULER("ruler"), MAGNIFIER("magnifier"), PULSE("pulse"), COSMIC_RAY("cosmic_ray"),
    SPEED_VIDEO("speed_video"), QR("qr"),

    // 3.7 Konum ve Uydu
    GPS_STATUS("gps_status"), PRECISE_POSITION("precise_position"), GPS_SPOOF("gps_spoof"),
    AREA("area"), TRACK("track"),

    // 3.8 Kablosuz ve Yakın Alan
    WIFI_ANALYZER("wifi_analyzer"), WIFI_RTT_RANGE("wifi_rtt"), BLE_SCANNER("ble_scanner"),
    UWB_RANGE("uwb"), NFC_READER("nfc_reader"), ID_CARD("id_card"), IR_REMOTE("ir_remote"),

    // 3.9 Cihaz ve Sistem
    DEVICE_REPORT("device_report"), BATTERY("battery"), SCREEN_TEST("screen_test"),
    SENSOR_TEST("sensor_test"), SPEAKER_MIC_TEST("speaker_mic_test"),
    ;

    companion object {
        fun fromKey(key: String): ToolId? = entries.firstOrNull { it.key == key }
    }
}

object ToolCatalog {

    private fun req(vararg h: Hardware) = listOf(Requirement(h.toSet()))
    private val NONE = emptyList<Requirement>()

    private fun t(
        id: ToolId, cat: Category, rarity: Rarity, reqs: List<Requirement>, done: Boolean,
        calibration: Boolean = false, recordable: Boolean = true,
    ) = ToolSpec(
        id = id, category = cat, rarity = rarity, requirements = reqs,
        implemented = done, calibrationRecommended = calibration, recordable = recordable,
    )

    /** Yazılmış araç / henüz yazılmamış ("Yakında" kartı). */
    private const val DONE = true
    private const val SOON = false

    val all: List<ToolSpec> = listOf(
        // 3.1
        t(ToolId.LEVEL, Category.MOTION, Rarity.EVERY_PHONE, req(ACCELEROMETER), DONE, calibration = true),
        t(ToolId.VIBRATION, Category.MOTION, Rarity.EVERY_PHONE, req(ACCELEROMETER), DONE),
        t(ToolId.SEISMOGRAPH, Category.MOTION, Rarity.EVERY_PHONE, req(ACCELEROMETER), DONE),
        t(ToolId.MACHINE_HEALTH, Category.MOTION, Rarity.EVERY_PHONE, req(ACCELEROMETER, MICROPHONE), DONE),
        t(ToolId.ROAD_QUALITY, Category.MOTION, Rarity.EVERY_PHONE, req(ACCELEROMETER, GNSS), DONE),
        t(ToolId.DRIVING_PROFILE, Category.MOTION, Rarity.EVERY_PHONE, req(ACCELEROMETER, GYROSCOPE, GNSS), DONE),
        t(ToolId.PROTRACTOR, Category.MOTION, Rarity.EVERY_PHONE, req(ACCELEROMETER, CAMERA), DONE),
        t(ToolId.GAIT, Category.MOTION, Rarity.EVERY_PHONE, req(ACCELEROMETER, GYROSCOPE), SOON),
        t(ToolId.TACHOMETER, Category.MOTION, Rarity.MOST_PHONES, req(GYROSCOPE), DONE),
        // 3.2 (pusula eğim dengelemesi için ivmeölçere de ihtiyaç duyar)
        t(ToolId.COMPASS, Category.MAGNETIC, Rarity.EVERY_PHONE, req(MAGNETOMETER, ACCELEROMETER), DONE),
        t(ToolId.MAGNETOMETER, Category.MAGNETIC, Rarity.EVERY_PHONE, req(MAGNETOMETER), DONE),
        t(ToolId.METAL_FINDER, Category.MAGNETIC, Rarity.EVERY_PHONE, req(MAGNETOMETER), DONE),
        t(ToolId.MAGNET_STRENGTH, Category.MAGNETIC, Rarity.EVERY_PHONE, req(MAGNETOMETER), DONE),
        // 3.3
        t(ToolId.BAROMETER, Category.PRESSURE, Rarity.MOST_PHONES, req(BAROMETER), DONE),
        t(ToolId.ALTIMETER, Category.PRESSURE, Rarity.MOST_PHONES, req(BAROMETER), DONE, calibration = true),
        t(ToolId.FLOOR_COUNTER, Category.PRESSURE, Rarity.MOST_PHONES, req(BAROMETER), DONE),
        t(ToolId.WEATHER_TREND, Category.PRESSURE, Rarity.MOST_PHONES, req(BAROMETER), DONE),
        t(ToolId.AIRTIGHTNESS, Category.PRESSURE, Rarity.MOST_PHONES, req(BAROMETER), DONE),
        // 3.4 — lüksmetre ortam ışığı sensörü yoksa kamerayla çalışacak (o yol henüz yazılmadı)
        t(
            ToolId.LUX, Category.LIGHT, Rarity.EVERY_PHONE,
            listOf(Requirement(setOf(LIGHT)), Requirement(setOf(CAMERA), mode = "camera", implemented = false)),
            DONE, calibration = true,
        ),
        t(ToolId.FLICKER, Category.LIGHT, Rarity.EVERY_PHONE, req(CAMERA), DONE),
        t(ToolId.COLOR_PICKER, Category.LIGHT, Rarity.EVERY_PHONE, req(CAMERA), DONE),
        t(ToolId.COLOR_TEMPERATURE, Category.LIGHT, Rarity.EVERY_PHONE, req(CAMERA), SOON),
        t(ToolId.SPECTROSCOPE, Category.LIGHT, Rarity.EVERY_PHONE, req(CAMERA), DONE),
        t(ToolId.PH_READER, Category.LIGHT, Rarity.EVERY_PHONE, req(CAMERA), SOON),
        // 3.5
        t(ToolId.SOUND_LEVEL, Category.SOUND, Rarity.EVERY_PHONE, req(MICROPHONE), DONE, calibration = true),
        t(ToolId.SPECTRUM, Category.SOUND, Rarity.EVERY_PHONE, req(MICROPHONE), DONE),
        t(ToolId.TUNER, Category.SOUND, Rarity.EVERY_PHONE, req(MICROPHONE), DONE),
        t(ToolId.TONE_GENERATOR, Category.SOUND, Rarity.EVERY_PHONE, req(SPEAKER), DONE, recordable = false),
        t(ToolId.RT60, Category.SOUND, Rarity.EVERY_PHONE, req(SPEAKER, MICROPHONE), DONE),
        t(ToolId.SONAR, Category.SOUND, Rarity.MOST_PHONES, req(SPEAKER, MICROPHONE), SOON),
        t(ToolId.METRONOME, Category.SOUND, Rarity.EVERY_PHONE, req(SPEAKER), DONE, recordable = false),
        // 3.6
        t(ToolId.RULER, Category.CAMERA, Rarity.EVERY_PHONE, req(CAMERA), DONE),
        t(ToolId.MAGNIFIER, Category.CAMERA, Rarity.EVERY_PHONE, req(CAMERA), DONE, recordable = false),
        t(ToolId.PULSE, Category.CAMERA, Rarity.EVERY_PHONE, req(CAMERA, FLASH), DONE),
        t(ToolId.COSMIC_RAY, Category.CAMERA, Rarity.EVERY_PHONE, req(CAMERA), DONE),
        t(ToolId.SPEED_VIDEO, Category.CAMERA, Rarity.EVERY_PHONE, req(CAMERA), SOON),
        t(ToolId.QR, Category.CAMERA, Rarity.EVERY_PHONE, req(CAMERA), DONE, recordable = false),
        // 3.7
        t(ToolId.GPS_STATUS, Category.LOCATION, Rarity.EVERY_PHONE, req(GNSS), DONE),
        t(ToolId.PRECISE_POSITION, Category.LOCATION, Rarity.FEW_PHONES, req(GNSS, GNSS_RAW), DONE),
        t(ToolId.GPS_SPOOF, Category.LOCATION, Rarity.MOST_PHONES, req(GNSS, GNSS_RAW), DONE),
        t(ToolId.AREA, Category.LOCATION, Rarity.EVERY_PHONE, req(GNSS), DONE),
        t(ToolId.TRACK, Category.LOCATION, Rarity.EVERY_PHONE, req(GNSS), DONE),
        // 3.8
        t(ToolId.WIFI_ANALYZER, Category.WIRELESS, Rarity.EVERY_PHONE, req(WIFI), DONE),
        t(ToolId.WIFI_RTT_RANGE, Category.WIRELESS, Rarity.FEW_PHONES, req(WIFI_RTT), DONE),
        t(ToolId.BLE_SCANNER, Category.WIRELESS, Rarity.EVERY_PHONE, req(BLUETOOTH_LE), DONE),
        t(ToolId.UWB_RANGE, Category.WIRELESS, Rarity.FEW_PHONES, req(UWB), DONE),
        t(ToolId.NFC_READER, Category.WIRELESS, Rarity.MOST_PHONES, req(NFC), DONE, recordable = false),
        t(ToolId.ID_CARD, Category.WIRELESS, Rarity.MOST_PHONES, req(NFC), DONE, recordable = false),
        t(ToolId.IR_REMOTE, Category.WIRELESS, Rarity.FEW_PHONES, req(IR_EMITTER), SOON, recordable = false),
        // 3.9
        t(ToolId.DEVICE_REPORT, Category.DEVICE, Rarity.EVERY_PHONE, NONE, DONE, recordable = false),
        t(ToolId.BATTERY, Category.DEVICE, Rarity.EVERY_PHONE, NONE, DONE),
        t(ToolId.SCREEN_TEST, Category.DEVICE, Rarity.EVERY_PHONE, NONE, DONE, recordable = false),
        t(ToolId.SENSOR_TEST, Category.DEVICE, Rarity.EVERY_PHONE, NONE, DONE),
        t(
            ToolId.SPEAKER_MIC_TEST, Category.DEVICE, Rarity.EVERY_PHONE,
            listOf(Requirement(setOf(MICROPHONE)), Requirement(setOf(SPEAKER), mode = "speaker")),
            DONE, recordable = false,
        ),
    )

    private val byId: Map<ToolId, ToolSpec> = all.associateBy { it.id }

    operator fun get(id: ToolId): ToolSpec = byId.getValue(id)

    fun inCategory(c: Category): List<ToolSpec> = all.filter { it.category == c }
}
