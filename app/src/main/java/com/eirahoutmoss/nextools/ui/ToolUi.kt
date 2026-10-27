package com.eirahoutmoss.nextools.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Hardware
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.Category
import com.eirahoutmoss.nextools.core.catalog.Hardware
import com.eirahoutmoss.nextools.core.catalog.ToolId

/** Araç kataloğunun ekrandaki karşılıkları: ad, kısa açıklama, dürüst sınır, simge. */
class ToolText(@StringRes val name: Int, @StringRes val desc: Int, @StringRes val limits: Int)

object ToolUi {

    fun text(id: ToolId): ToolText = when (id) {
        ToolId.LEVEL -> ToolText(R.string.tool_level_name, R.string.tool_level_desc, R.string.tool_level_limits)
        ToolId.VIBRATION -> ToolText(R.string.tool_vibration_name, R.string.tool_vibration_desc, R.string.tool_vibration_limits)
        ToolId.SEISMOGRAPH -> ToolText(R.string.tool_seismograph_name, R.string.tool_seismograph_desc, R.string.tool_seismograph_limits)
        ToolId.MACHINE_HEALTH -> ToolText(R.string.tool_machine_health_name, R.string.tool_machine_health_desc, R.string.tool_machine_health_limits)
        ToolId.ROAD_QUALITY -> ToolText(R.string.tool_road_quality_name, R.string.tool_road_quality_desc, R.string.tool_road_quality_limits)
        ToolId.DRIVING_PROFILE -> ToolText(R.string.tool_driving_profile_name, R.string.tool_driving_profile_desc, R.string.tool_driving_profile_limits)
        ToolId.PROTRACTOR -> ToolText(R.string.tool_protractor_name, R.string.tool_protractor_desc, R.string.tool_protractor_limits)
        ToolId.GAIT -> ToolText(R.string.tool_gait_name, R.string.tool_gait_desc, R.string.tool_gait_limits)
        ToolId.TACHOMETER -> ToolText(R.string.tool_tachometer_name, R.string.tool_tachometer_desc, R.string.tool_tachometer_limits)
        ToolId.COMPASS -> ToolText(R.string.tool_compass_name, R.string.tool_compass_desc, R.string.tool_compass_limits)
        ToolId.MAGNETOMETER -> ToolText(R.string.tool_magnetometer_name, R.string.tool_magnetometer_desc, R.string.tool_magnetometer_limits)
        ToolId.METAL_FINDER -> ToolText(R.string.tool_metal_finder_name, R.string.tool_metal_finder_desc, R.string.tool_metal_finder_limits)
        ToolId.MAGNET_STRENGTH -> ToolText(R.string.tool_magnet_strength_name, R.string.tool_magnet_strength_desc, R.string.tool_magnet_strength_limits)
        ToolId.BAROMETER -> ToolText(R.string.tool_barometer_name, R.string.tool_barometer_desc, R.string.tool_barometer_limits)
        ToolId.ALTIMETER -> ToolText(R.string.tool_altimeter_name, R.string.tool_altimeter_desc, R.string.tool_altimeter_limits)
        ToolId.FLOOR_COUNTER -> ToolText(R.string.tool_floor_counter_name, R.string.tool_floor_counter_desc, R.string.tool_floor_counter_limits)
        ToolId.WEATHER_TREND -> ToolText(R.string.tool_weather_trend_name, R.string.tool_weather_trend_desc, R.string.tool_weather_trend_limits)
        ToolId.AIRTIGHTNESS -> ToolText(R.string.tool_airtightness_name, R.string.tool_airtightness_desc, R.string.tool_airtightness_limits)
        ToolId.LUX -> ToolText(R.string.tool_lux_name, R.string.tool_lux_desc, R.string.tool_lux_limits)
        ToolId.FLICKER -> ToolText(R.string.tool_flicker_name, R.string.tool_flicker_desc, R.string.tool_flicker_limits)
        ToolId.COLOR_PICKER -> ToolText(R.string.tool_color_picker_name, R.string.tool_color_picker_desc, R.string.tool_color_picker_limits)
        ToolId.COLOR_TEMPERATURE -> ToolText(R.string.tool_color_temperature_name, R.string.tool_color_temperature_desc, R.string.tool_color_temperature_limits)
        ToolId.SPECTROSCOPE -> ToolText(R.string.tool_spectroscope_name, R.string.tool_spectroscope_desc, R.string.tool_spectroscope_limits)
        ToolId.PH_READER -> ToolText(R.string.tool_ph_reader_name, R.string.tool_ph_reader_desc, R.string.tool_ph_reader_limits)
        ToolId.SOUND_LEVEL -> ToolText(R.string.tool_sound_level_name, R.string.tool_sound_level_desc, R.string.tool_sound_level_limits)
        ToolId.SPECTRUM -> ToolText(R.string.tool_spectrum_name, R.string.tool_spectrum_desc, R.string.tool_spectrum_limits)
        ToolId.TUNER -> ToolText(R.string.tool_tuner_name, R.string.tool_tuner_desc, R.string.tool_tuner_limits)
        ToolId.TONE_GENERATOR -> ToolText(R.string.tool_tone_generator_name, R.string.tool_tone_generator_desc, R.string.tool_tone_generator_limits)
        ToolId.RT60 -> ToolText(R.string.tool_rt60_name, R.string.tool_rt60_desc, R.string.tool_rt60_limits)
        ToolId.SONAR -> ToolText(R.string.tool_sonar_name, R.string.tool_sonar_desc, R.string.tool_sonar_limits)
        ToolId.METRONOME -> ToolText(R.string.tool_metronome_name, R.string.tool_metronome_desc, R.string.tool_metronome_limits)
        ToolId.RULER -> ToolText(R.string.tool_ruler_name, R.string.tool_ruler_desc, R.string.tool_ruler_limits)
        ToolId.MAGNIFIER -> ToolText(R.string.tool_magnifier_name, R.string.tool_magnifier_desc, R.string.tool_magnifier_limits)
        ToolId.PULSE -> ToolText(R.string.tool_pulse_name, R.string.tool_pulse_desc, R.string.tool_pulse_limits)
        ToolId.COSMIC_RAY -> ToolText(R.string.tool_cosmic_ray_name, R.string.tool_cosmic_ray_desc, R.string.tool_cosmic_ray_limits)
        ToolId.SPEED_VIDEO -> ToolText(R.string.tool_speed_video_name, R.string.tool_speed_video_desc, R.string.tool_speed_video_limits)
        ToolId.QR -> ToolText(R.string.tool_qr_name, R.string.tool_qr_desc, R.string.tool_qr_limits)
        ToolId.GPS_STATUS -> ToolText(R.string.tool_gps_status_name, R.string.tool_gps_status_desc, R.string.tool_gps_status_limits)
        ToolId.PRECISE_POSITION -> ToolText(R.string.tool_precise_position_name, R.string.tool_precise_position_desc, R.string.tool_precise_position_limits)
        ToolId.GPS_SPOOF -> ToolText(R.string.tool_gps_spoof_name, R.string.tool_gps_spoof_desc, R.string.tool_gps_spoof_limits)
        ToolId.AREA -> ToolText(R.string.tool_area_name, R.string.tool_area_desc, R.string.tool_area_limits)
        ToolId.TRACK -> ToolText(R.string.tool_track_name, R.string.tool_track_desc, R.string.tool_track_limits)
        ToolId.WIFI_ANALYZER -> ToolText(R.string.tool_wifi_analyzer_name, R.string.tool_wifi_analyzer_desc, R.string.tool_wifi_analyzer_limits)
        ToolId.WIFI_RTT_RANGE -> ToolText(R.string.tool_wifi_rtt_name, R.string.tool_wifi_rtt_desc, R.string.tool_wifi_rtt_limits)
        ToolId.BLE_SCANNER -> ToolText(R.string.tool_ble_scanner_name, R.string.tool_ble_scanner_desc, R.string.tool_ble_scanner_limits)
        ToolId.UWB_RANGE -> ToolText(R.string.tool_uwb_name, R.string.tool_uwb_desc, R.string.tool_uwb_limits)
        ToolId.NFC_READER -> ToolText(R.string.tool_nfc_reader_name, R.string.tool_nfc_reader_desc, R.string.tool_nfc_reader_limits)
        ToolId.ID_CARD -> ToolText(R.string.tool_id_card_name, R.string.tool_id_card_desc, R.string.tool_id_card_limits)
        ToolId.IR_REMOTE -> ToolText(R.string.tool_ir_remote_name, R.string.tool_ir_remote_desc, R.string.tool_ir_remote_limits)
        ToolId.DEVICE_REPORT -> ToolText(R.string.tool_device_report_name, R.string.tool_device_report_desc, R.string.tool_device_report_limits)
        ToolId.BATTERY -> ToolText(R.string.tool_battery_name, R.string.tool_battery_desc, R.string.tool_battery_limits)
        ToolId.SCREEN_TEST -> ToolText(R.string.tool_screen_test_name, R.string.tool_screen_test_desc, R.string.tool_screen_test_limits)
        ToolId.SENSOR_TEST -> ToolText(R.string.tool_sensor_test_name, R.string.tool_sensor_test_desc, R.string.tool_sensor_test_limits)
        ToolId.SPEAKER_MIC_TEST -> ToolText(R.string.tool_speaker_mic_test_name, R.string.tool_speaker_mic_test_desc, R.string.tool_speaker_mic_test_limits)
    }

    /** Yazılmış araçların "ne ölçer / ne ölçmez" bilgi metni. */
    @StringRes
    fun info(id: ToolId): Int? = when (id) {
        ToolId.LEVEL -> R.string.tool_level_info
        ToolId.COMPASS -> R.string.tool_compass_info
        ToolId.MAGNETOMETER -> R.string.tool_magnetometer_info
        ToolId.METAL_FINDER -> R.string.tool_metal_finder_info
        ToolId.BAROMETER -> R.string.tool_barometer_info
        ToolId.ALTIMETER -> R.string.tool_altimeter_info
        ToolId.LUX -> R.string.tool_lux_info
        ToolId.SOUND_LEVEL -> R.string.tool_sound_level_info
        ToolId.SENSOR_TEST -> R.string.tool_sensor_test_info
        ToolId.DEVICE_REPORT -> R.string.tool_device_report_info
        ToolId.VIBRATION -> R.string.tool_vibration_info
        ToolId.SEISMOGRAPH -> R.string.tool_seismograph_info
        ToolId.SPECTRUM -> R.string.tool_spectrum_info
        ToolId.TUNER -> R.string.tool_tuner_info
        ToolId.TONE_GENERATOR -> R.string.tool_tone_generator_info
        ToolId.FLOOR_COUNTER -> R.string.tool_floor_counter_info
        ToolId.COLOR_PICKER -> R.string.tool_color_picker_info
        ToolId.MAGNIFIER -> R.string.tool_magnifier_info
        ToolId.QR -> R.string.tool_qr_info
        ToolId.GPS_STATUS -> R.string.tool_gps_status_info
        ToolId.AREA -> R.string.tool_area_info
        ToolId.TRACK -> R.string.tool_track_info
        ToolId.WIFI_ANALYZER -> R.string.tool_wifi_analyzer_info
        ToolId.BLE_SCANNER -> R.string.tool_ble_scanner_info
        ToolId.NFC_READER -> R.string.tool_nfc_reader_info
        ToolId.BATTERY -> R.string.tool_battery_info
        ToolId.SCREEN_TEST -> R.string.tool_screen_test_info
        ToolId.RT60 -> R.string.tool_rt60_info
        ToolId.FLICKER -> R.string.tool_flicker_info
        ToolId.ROAD_QUALITY -> R.string.tool_road_quality_info
        ToolId.DRIVING_PROFILE -> R.string.tool_driving_profile_info
        ToolId.MACHINE_HEALTH -> R.string.tool_machine_health_info
        ToolId.AIRTIGHTNESS -> R.string.tool_airtightness_info
        ToolId.PULSE -> R.string.tool_pulse_info
        ToolId.PRECISE_POSITION -> R.string.tool_precise_position_info
        ToolId.GPS_SPOOF -> R.string.tool_gps_spoof_info
        ToolId.WIFI_RTT_RANGE -> R.string.tool_wifi_rtt_info
        ToolId.UWB_RANGE -> R.string.tool_uwb_info
        ToolId.ID_CARD -> R.string.tool_id_card_info
        ToolId.SPECTROSCOPE -> R.string.tool_spectroscope_info
        ToolId.COSMIC_RAY -> R.string.tool_cosmic_ray_info
        ToolId.MAGNET_STRENGTH -> R.string.tool_magnet_strength_info
        ToolId.TACHOMETER -> R.string.tool_tachometer_info
        ToolId.WEATHER_TREND -> R.string.tool_weather_trend_info
        ToolId.METRONOME -> R.string.tool_metronome_info
        ToolId.SPEAKER_MIC_TEST -> R.string.tool_speaker_mic_test_info
        ToolId.PROTRACTOR -> R.string.tool_protractor_info
        ToolId.RULER -> R.string.tool_ruler_info
        ToolId.GAIT -> R.string.tool_gait_info
        ToolId.COLOR_TEMPERATURE -> R.string.tool_color_temperature_info
        ToolId.PH_READER -> R.string.tool_ph_reader_info
        ToolId.SONAR -> R.string.tool_sonar_info
        ToolId.SPEED_VIDEO -> R.string.tool_speed_video_info
        ToolId.IR_REMOTE -> R.string.tool_ir_remote_info
        else -> null
    }

    fun icon(id: ToolId): ImageVector = when (id) {
        ToolId.LEVEL -> Icons.Outlined.Straighten
        ToolId.VIBRATION -> Icons.Outlined.Vibration
        ToolId.SEISMOGRAPH -> Icons.Outlined.Timeline
        ToolId.MACHINE_HEALTH -> Icons.Outlined.PrecisionManufacturing
        ToolId.ROAD_QUALITY -> Icons.Outlined.Route
        ToolId.DRIVING_PROFILE -> Icons.Outlined.DirectionsCar
        ToolId.PROTRACTOR -> Icons.Outlined.Architecture
        ToolId.GAIT -> Icons.AutoMirrored.Outlined.DirectionsWalk
        ToolId.TACHOMETER -> Icons.Outlined.Speed
        ToolId.COMPASS -> Icons.Outlined.Explore
        ToolId.MAGNETOMETER -> Icons.Outlined.Sensors
        ToolId.METAL_FINDER -> Icons.Outlined.Hardware
        ToolId.MAGNET_STRENGTH -> Icons.Outlined.Bolt
        ToolId.BAROMETER -> Icons.Outlined.Compress
        ToolId.ALTIMETER -> Icons.Outlined.Terrain
        ToolId.FLOOR_COUNTER -> Icons.Outlined.Stairs
        ToolId.WEATHER_TREND -> Icons.Outlined.Cloud
        ToolId.AIRTIGHTNESS -> Icons.Outlined.Air
        ToolId.LUX -> Icons.Outlined.LightMode
        ToolId.FLICKER -> Icons.Outlined.Lightbulb
        ToolId.COLOR_PICKER -> Icons.Outlined.Colorize
        ToolId.COLOR_TEMPERATURE -> Icons.Outlined.Thermostat
        ToolId.SPECTROSCOPE -> Icons.Outlined.Gradient
        ToolId.PH_READER -> Icons.Outlined.Science
        ToolId.SOUND_LEVEL -> Icons.Outlined.GraphicEq
        ToolId.SPECTRUM -> Icons.Outlined.Equalizer
        ToolId.TUNER -> Icons.Outlined.MusicNote
        ToolId.TONE_GENERATOR -> Icons.Outlined.Waves
        ToolId.RT60 -> Icons.Outlined.Hearing
        ToolId.SONAR -> Icons.Outlined.Radar
        ToolId.METRONOME -> Icons.Outlined.Timer
        ToolId.RULER -> Icons.Outlined.SquareFoot
        ToolId.MAGNIFIER -> Icons.Outlined.ZoomIn
        ToolId.PULSE -> Icons.Outlined.MonitorHeart
        ToolId.COSMIC_RAY -> Icons.Outlined.AutoAwesome
        ToolId.SPEED_VIDEO -> Icons.Outlined.Videocam
        ToolId.QR -> Icons.Outlined.QrCodeScanner
        ToolId.GPS_STATUS -> Icons.Outlined.SatelliteAlt
        ToolId.PRECISE_POSITION -> Icons.Outlined.GpsFixed
        ToolId.GPS_SPOOF -> Icons.Outlined.GpsOff
        ToolId.AREA -> Icons.Outlined.CropFree
        ToolId.TRACK -> Icons.Outlined.Map
        ToolId.WIFI_ANALYZER -> Icons.Outlined.Wifi
        ToolId.WIFI_RTT_RANGE -> Icons.Outlined.WifiTethering
        ToolId.BLE_SCANNER -> Icons.Outlined.Bluetooth
        ToolId.UWB_RANGE -> Icons.Outlined.NearMe
        ToolId.NFC_READER -> Icons.Outlined.Nfc
        ToolId.ID_CARD -> Icons.Outlined.Badge
        ToolId.IR_REMOTE -> Icons.Outlined.SettingsRemote
        ToolId.DEVICE_REPORT -> Icons.Outlined.PhoneAndroid
        ToolId.BATTERY -> Icons.Outlined.BatteryChargingFull
        ToolId.SCREEN_TEST -> Icons.Outlined.GridOn
        ToolId.SENSOR_TEST -> Icons.Outlined.DeveloperBoard
        ToolId.SPEAKER_MIC_TEST -> Icons.Outlined.SettingsVoice
    }

    @StringRes
    fun category(c: Category): Int = when (c) {
        Category.MOTION -> R.string.cat_motion
        Category.MAGNETIC -> R.string.cat_magnetic
        Category.PRESSURE -> R.string.cat_pressure
        Category.LIGHT -> R.string.cat_light
        Category.SOUND -> R.string.cat_sound
        Category.CAMERA -> R.string.cat_camera
        Category.LOCATION -> R.string.cat_location
        Category.WIRELESS -> R.string.cat_wireless
        Category.DEVICE -> R.string.cat_device
    }

    @StringRes
    fun hardware(h: Hardware): Int = when (h) {
        Hardware.ACCELEROMETER -> R.string.hw_accelerometer
        Hardware.GYROSCOPE -> R.string.hw_gyroscope
        Hardware.MAGNETOMETER -> R.string.hw_magnetometer
        Hardware.BAROMETER -> R.string.hw_barometer
        Hardware.LIGHT -> R.string.hw_light
        Hardware.PROXIMITY -> R.string.hw_proximity
        Hardware.AMBIENT_TEMPERATURE -> R.string.hw_ambient_temperature
        Hardware.HUMIDITY -> R.string.hw_humidity
        Hardware.STEP_COUNTER -> R.string.hw_step_counter
        Hardware.CAMERA -> R.string.hw_camera
        Hardware.FLASH -> R.string.hw_flash
        Hardware.MICROPHONE -> R.string.hw_microphone
        Hardware.SPEAKER -> R.string.hw_speaker
        Hardware.BLUETOOTH_LE -> R.string.hw_bluetooth_le
        Hardware.UWB -> R.string.hw_uwb
        Hardware.NFC -> R.string.hw_nfc
        Hardware.GNSS -> R.string.hw_gnss
        Hardware.GNSS_RAW -> R.string.hw_gnss_raw
        Hardware.WIFI -> R.string.hw_wifi
        Hardware.WIFI_RTT -> R.string.hw_wifi_rtt
        Hardware.IR_EMITTER -> R.string.hw_ir_emitter
        Hardware.VIBRATOR -> R.string.hw_vibrator
    }

    /** Özet satırlarının adı (RecordMeta.summary.kind). */
    @StringRes
    fun summaryKind(kind: String): Int = when (kind) {
        "min" -> R.string.sum_min
        "max" -> R.string.sum_max
        "avg" -> R.string.sum_avg
        "leq" -> R.string.sum_leq
        "lmax" -> R.string.sum_lmax
        "lmin" -> R.string.sum_lmin
        "last" -> R.string.sum_last
        "range" -> R.string.sum_range
        "peak" -> R.string.sum_peak
        "change" -> R.string.sum_change
        "total" -> R.string.sum_total
        "ascent" -> R.string.sum_ascent
        "descent" -> R.string.sum_descent
        "count" -> R.string.sum_count
        "rt60_mid" -> R.string.sum_rt60_mid
        "score" -> R.string.sum_score
        "rms" -> R.string.sum_rms
        "similarity" -> R.string.sum_similarity
        "rms_change" -> R.string.sum_rms_change
        "uncertainty" -> R.string.sum_uncertainty
        "variability" -> R.string.sum_variability
        "asymmetry" -> R.string.sum_asymmetry
        else -> R.string.sum_value
    }

    /** Sütun adı: bilinen adlar çevrilir, bilinmeyenler (ör. çoklu sensör kaydı) olduğu gibi yazılır. */
    @Composable
    fun columnText(name: String): String = columnText(name) { androidx.compose.ui.res.stringResource(it) }

    inline fun columnText(name: String, s: (Int) -> String): String {
        val r = column(name)
        return if (r == R.string.col_value && name != "value") name else s(r)
    }

    /** CSV sütun adlarının okunur karşılığı. */
    @StringRes
    fun column(name: String): Int = when (name) {
        "x_deg" -> R.string.col_x_deg
        "y_deg" -> R.string.col_y_deg
        "tilt_deg" -> R.string.col_tilt_deg
        "angle_deg" -> R.string.col_angle_deg
        "heading_mag_deg" -> R.string.col_heading_mag
        "heading_true_deg" -> R.string.col_heading_true
        "field_ut" -> R.string.col_field_total
        "bx_ut" -> R.string.col_bx
        "by_ut" -> R.string.col_by
        "bz_ut" -> R.string.col_bz
        "anomaly_ut" -> R.string.col_anomaly
        "pressure_hpa" -> R.string.col_pressure
        "slp_hpa" -> R.string.col_slp
        "altitude_m" -> R.string.col_altitude
        "lux" -> R.string.col_lux
        "level_db" -> R.string.col_level_db
        "leq_db" -> R.string.col_leq_db
        "rms_ms2" -> R.string.col_rms
        "peak_ms2" -> R.string.col_peak
        "dom_hz" -> R.string.col_dom_hz
        "accel_mg" -> R.string.col_accel_mg
        "peak_dbfs" -> R.string.col_peak_dbfs
        "total_dbfs" -> R.string.col_total_dbfs
        "freq_hz" -> R.string.col_freq
        "cents" -> R.string.col_cents
        "floor" -> R.string.col_floor
        "r" -> R.string.col_r
        "g" -> R.string.col_g
        "b" -> R.string.col_b
        "lab_l" -> R.string.col_lab_l
        "lab_a" -> R.string.col_lab_a
        "lab_b" -> R.string.col_lab_b
        "lat" -> R.string.col_lat
        "lon" -> R.string.col_lon
        "accuracy_m" -> R.string.col_accuracy
        "sats_used" -> R.string.col_sats_used
        "sats_visible" -> R.string.col_sats_visible
        "mean_cn0" -> R.string.col_mean_cn0
        "speed_kmh" -> R.string.col_speed
        "distance_m" -> R.string.col_distance
        "area_m2" -> R.string.col_area
        "area_donum" -> R.string.col_area_donum
        "perimeter_m" -> R.string.col_perimeter
        "corners" -> R.string.col_corners
        "connected_rssi" -> R.string.col_connected_rssi
        "ap_count" -> R.string.col_ap_count
        "strongest_rssi" -> R.string.col_strongest_rssi
        "device_count" -> R.string.col_device_count
        "level_pct" -> R.string.col_level_pct
        "temp_c" -> R.string.col_temp
        "voltage_v" -> R.string.col_voltage
        "current_ma" -> R.string.col_current
        "band_hz" -> R.string.col_band_hz
        "rt60_s" -> R.string.col_rt60
        "range_db" -> R.string.col_range_db
        "flicker_pct" -> R.string.col_flicker_pct
        "flicker_hz" -> R.string.col_flicker_hz
        "rough_ms2" -> R.string.col_rough
        "pothole" -> R.string.col_pothole
        "long_g" -> R.string.col_long_g
        "lat_g" -> R.string.col_lat_g
        "events" -> R.string.col_events
        "now_db" -> R.string.col_now_db
        "base_db" -> R.string.col_base_db
        "rel_pa" -> R.string.col_rel_pa
        "bpm" -> R.string.col_bpm
        "red" -> R.string.col_red
        "signs" -> R.string.col_signs
        "cn0_sd" -> R.string.col_cn0_sd
        "agc_db" -> R.string.col_agc
        "sd_m" -> R.string.col_sd_m
        "rssi_dbm" -> R.string.col_rssi
        "azimuth_deg" -> R.string.col_azimuth
        "elevation_deg" -> R.string.col_elevation
        "hits_per_min" -> R.string.col_hits_per_min
        "total" -> R.string.col_total
        "wavelength_nm" -> R.string.col_wavelength
        "pixel" -> R.string.col_pixel
        "intensity" -> R.string.col_intensity
        "peak_ut" -> R.string.col_peak_ut
        "rpm" -> R.string.col_rpm
        "change_3h_hpa" -> R.string.col_change_3h
        "step_s" -> R.string.col_step_s
        "cadence" -> R.string.col_cadence
        "cct_k" -> R.string.col_cct
        "cie_x" -> R.string.col_cie_x
        "cie_y" -> R.string.col_cie_y
        "ph" -> R.string.col_ph
        "delta_e" -> R.string.col_delta_e
        "confidence" -> R.string.col_confidence
        "time_s" -> R.string.col_time_s
        else -> R.string.col_value
    }
}
