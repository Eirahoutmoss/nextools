package com.eirahoutmoss.nextools.core.catalog

/**
 * Araçların ihtiyaç duyduğu donanım. Uygulama açılışta telefonu tarar ve bu kümelerden
 * hangisinin bulunduğunu çıkarır (bkz. app/hardware/HardwareProbe).
 *
 * [id] kalıcıdır: kayıtlarda ve dışa aktarılan dosyalarda geçer, değiştirilmez.
 */
enum class Hardware(val id: String) {
    ACCELEROMETER("accelerometer"),
    GYROSCOPE("gyroscope"),
    MAGNETOMETER("magnetometer"),
    BAROMETER("barometer"),
    LIGHT("light"),
    PROXIMITY("proximity"),
    AMBIENT_TEMPERATURE("ambient_temperature"),
    HUMIDITY("humidity"),
    STEP_COUNTER("step_counter"),
    CAMERA("camera"),
    FLASH("flash"),
    MICROPHONE("microphone"),
    SPEAKER("speaker"),
    BLUETOOTH_LE("bluetooth_le"),
    UWB("uwb"),
    NFC("nfc"),
    GNSS("gnss"),
    GNSS_RAW("gnss_raw"),
    WIFI("wifi"),
    WIFI_RTT("wifi_rtt"),
    IR_EMITTER("ir_emitter"),
    VIBRATOR("vibrator"),
    ;

    companion object {
        fun fromId(id: String): Hardware? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Bir donanımın bu cihazdaki durumu. Bazı yetenekler (ör. GNSS ham ölçüm Android 11 öncesinde)
 * ancak ölçüm sırasında anlaşılabilir; bunlar [UNKNOWN] olur ve araç açık bırakılır.
 */
enum class Presence { PRESENT, ABSENT, UNKNOWN }

/** Telefonun yetenek tablosu. */
data class DeviceCapabilities(val presence: Map<Hardware, Presence>) {
    fun has(h: Hardware): Boolean = presence[h] == Presence.PRESENT
    fun isAbsent(h: Hardware): Boolean = (presence[h] ?: Presence.ABSENT) == Presence.ABSENT
    fun presenceOf(h: Hardware): Presence = presence[h] ?: Presence.ABSENT

    companion object {
        fun of(vararg present: Hardware): DeviceCapabilities =
            DeviceCapabilities(Hardware.entries.associateWith { if (it in present) Presence.PRESENT else Presence.ABSENT })
    }
}
