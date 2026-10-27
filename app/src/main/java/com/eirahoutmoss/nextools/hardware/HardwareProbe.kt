package com.eirahoutmoss.nextools.hardware

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import com.eirahoutmoss.nextools.core.catalog.Availability
import com.eirahoutmoss.nextools.core.catalog.DeviceCapabilities
import com.eirahoutmoss.nextools.core.catalog.Hardware
import com.eirahoutmoss.nextools.core.catalog.Presence
import com.eirahoutmoss.nextools.core.catalog.ToolCatalog
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.catalog.ToolStatus

/** Cihaz Karnesi satırındaki sensör ayrıntısı. */
data class SensorDetail(
    val name: String,
    val vendor: String,
    val version: Int,
    val resolution: Float,
    val maxRange: Float,
    val powerMa: Float,
    /** En yüksek örnekleme hızı (Hz). Olay tabanlı sensörlerde (ışık, yakınlık) null. */
    val maxRateHz: Double?,
    val androidType: Int,
)

/** Donanım tanıma: Android'in bildirdiği özellik ve sensör listesinden telefonun yeteneklerini çıkarır. */
class HardwareProbe(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val sm: SensorManager? = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** Taslak bölüm 2'deki tabloya karşılık gelen Android sensör türleri. */
    fun sensorTypeFor(h: Hardware): Int? = when (h) {
        Hardware.ACCELEROMETER -> Sensor.TYPE_ACCELEROMETER
        Hardware.GYROSCOPE -> Sensor.TYPE_GYROSCOPE
        Hardware.MAGNETOMETER -> Sensor.TYPE_MAGNETIC_FIELD
        Hardware.BAROMETER -> Sensor.TYPE_PRESSURE
        Hardware.LIGHT -> Sensor.TYPE_LIGHT
        Hardware.PROXIMITY -> Sensor.TYPE_PROXIMITY
        Hardware.AMBIENT_TEMPERATURE -> Sensor.TYPE_AMBIENT_TEMPERATURE
        Hardware.HUMIDITY -> Sensor.TYPE_RELATIVE_HUMIDITY
        Hardware.STEP_COUNTER -> Sensor.TYPE_STEP_COUNTER
        else -> null
    }

    fun sensorFor(h: Hardware): Sensor? = sensorTypeFor(h)?.let { sm?.getDefaultSensor(it) }

    fun detail(sensor: Sensor): SensorDetail = SensorDetail(
        name = sensor.name,
        vendor = sensor.vendor,
        version = sensor.version,
        resolution = sensor.resolution,
        maxRange = sensor.maximumRange,
        powerMa = sensor.power,
        maxRateHz = sensor.minDelay.takeIf { it > 0 }?.let { 1_000_000.0 / it },
        androidType = sensor.type,
    )

    private fun feature(name: String) = pm.hasSystemFeature(name)

    private fun presence(h: Hardware): Presence {
        sensorTypeFor(h)?.let { return if (sm?.getDefaultSensor(it) != null) Presence.PRESENT else Presence.ABSENT }
        val present: Boolean? = when (h) {
            Hardware.CAMERA -> feature(PackageManager.FEATURE_CAMERA_ANY)
            Hardware.FLASH -> feature(PackageManager.FEATURE_CAMERA_FLASH)
            Hardware.MICROPHONE -> feature(PackageManager.FEATURE_MICROPHONE)
            Hardware.SPEAKER -> feature(PackageManager.FEATURE_AUDIO_OUTPUT)
            Hardware.BLUETOOTH_LE -> feature(PackageManager.FEATURE_BLUETOOTH_LE)
            Hardware.UWB -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && feature(PackageManager.FEATURE_UWB)
            Hardware.NFC -> feature(PackageManager.FEATURE_NFC)
            Hardware.GNSS -> feature(PackageManager.FEATURE_LOCATION_GPS)
            Hardware.GNSS_RAW -> gnssRaw()
            Hardware.WIFI -> feature(PackageManager.FEATURE_WIFI)
            Hardware.WIFI_RTT -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && feature(PackageManager.FEATURE_WIFI_RTT)
            Hardware.IR_EMITTER -> (context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager)?.hasIrEmitter() == true
            Hardware.VIBRATOR -> vibrator()?.hasVibrator() == true
            else -> false
        }
        return when (present) {
            true -> Presence.PRESENT
            false -> Presence.ABSENT
            null -> Presence.UNKNOWN
        }
    }

    /**
     * GNSS ham ölçüm desteği Android 12'den itibaren sorulabilir. Daha eskide ancak ölçüm
     * sırasında anlaşılır: bilinmiyor (null) döner, araç açık kalır.
     */
    private fun gnssRaw(): Boolean? {
        if (!feature(PackageManager.FEATURE_LOCATION_GPS)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching { lm.gnssCapabilities.hasMeasurements() }.getOrNull()
    }

    @Suppress("DEPRECATION")
    fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Tarama ucuzdur ama her ekranda tekrarlanmasın diye bir kez yapılır. */
    val capabilities: DeviceCapabilities by lazy {
        DeviceCapabilities(Hardware.entries.associateWith { presence(it) })
    }

    val statuses: List<ToolStatus> by lazy { Availability.evaluateAll(capabilities) }

    fun status(id: ToolId): ToolStatus = statuses.first { it.spec.id == id }

    val summary: Availability.Summary by lazy { Availability.summarize(statuses) }

    fun allSensors(): List<Sensor> = sm?.getSensorList(Sensor.TYPE_ALL).orEmpty()

    /** Cihaz Karnesi ekranının sıralı donanım listesi. */
    val reportOrder: List<Hardware> = listOf(
        Hardware.ACCELEROMETER, Hardware.GYROSCOPE, Hardware.MAGNETOMETER, Hardware.BAROMETER,
        Hardware.LIGHT, Hardware.PROXIMITY, Hardware.AMBIENT_TEMPERATURE, Hardware.HUMIDITY,
        Hardware.STEP_COUNTER, Hardware.CAMERA, Hardware.FLASH, Hardware.MICROPHONE, Hardware.SPEAKER,
        Hardware.BLUETOOTH_LE, Hardware.UWB, Hardware.NFC, Hardware.GNSS, Hardware.GNSS_RAW,
        Hardware.WIFI, Hardware.WIFI_RTT, Hardware.IR_EMITTER, Hardware.VIBRATOR,
    )

    init {
        check(reportOrder.toSet() == Hardware.entries.toSet()) { "reportOrder eksik" }
        check(ToolCatalog.all.isNotEmpty())
    }
}
