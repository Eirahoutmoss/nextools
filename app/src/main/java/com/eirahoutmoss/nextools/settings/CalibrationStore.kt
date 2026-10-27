package com.eirahoutmoss.nextools.settings

import android.content.Context
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Barometric
import com.eirahoutmoss.nextools.core.measure.TimeWeighting
import com.eirahoutmoss.nextools.core.measure.Weighting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Su terazisi sıfır noktaları (°). */
data class LevelCalibration(val flatX: Double, val flatY: Double, val edge: Double, val setAtMs: Long)

data class Calibrations(
    val level: LevelCalibration?,
    /** Elle girilen manyetik sapma (°, doğu +). null = konumdan otomatik. */
    val manualDeclination: Double?,
    /** Lüksmetre çarpanı. null = kalibre değil. */
    val luxFactor: Double?,
    val luxSetAtMs: Long?,
    /** Ses ölçer dBFS → dB SPL farkı. null = varsayılan (kalibre değil). */
    val soundOffset: Double?,
    val soundSetAtMs: Long?,
    val soundWeighting: Weighting,
    val soundTiming: TimeWeighting,
    val altimeterRef: Barometric.Reference?,
    /** Barometrede deniz seviyesi basıncı için bulunulan yerin yüksekliği (m). */
    val barometerAltitude: Double?,
    val metalSensitivity: Double,
    /** Akort aletinde La (A4) frekansı. */
    val tunerA4: Double,
    /** Kat sayacında bir katın yüksekliği (m). */
    val floorHeight: Double,
) {
    fun isCalibrated(tool: ToolId): Boolean = when (tool) {
        ToolId.LEVEL -> level != null
        ToolId.LUX -> luxFactor != null
        ToolId.SOUND_LEVEL -> soundOffset != null
        ToolId.ALTIMETER -> altimeterRef != null
        ToolId.COMPASS -> manualDeclination != null
        ToolId.BAROMETER -> barometerAltitude != null
        ToolId.TUNER -> tunerA4 != 440.0
        ToolId.FLOOR_COUNTER -> floorHeight != 3.0
        else -> false
    }
}

/**
 * Kalibrasyonlar bu cihaza özeldir (başka telefona taşınmaz; yedeklenmez).
 * Ayarlar > Kalibrasyonlar ekranından tek tek sıfırlanabilir.
 */
class CalibrationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("calibration", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Calibrations> = _state.asStateFlow()

    private fun dbl(key: String): Double? =
        if (prefs.contains(key)) java.lang.Double.longBitsToDouble(prefs.getLong(key, 0)) else null

    private fun lng(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null

    private fun load(): Calibrations {
        val level = dbl("level.x")?.let { x ->
            LevelCalibration(x, dbl("level.y") ?: 0.0, dbl("level.edge") ?: 0.0, lng("level.at") ?: 0)
        }
        val alt = dbl("alt.p")?.let { p -> Barometric.Reference(p, dbl("alt.h") ?: 0.0, lng("alt.at") ?: 0) }
        return Calibrations(
            level = level,
            manualDeclination = dbl("compass.decl"),
            luxFactor = dbl("lux.f"),
            luxSetAtMs = lng("lux.at"),
            soundOffset = dbl("sound.off"),
            soundSetAtMs = lng("sound.at"),
            soundWeighting = Weighting.entries.firstOrNull { it.code == prefs.getString("sound.w", null) } ?: Weighting.A,
            soundTiming = TimeWeighting.entries.firstOrNull { it.code == prefs.getString("sound.t", null) } ?: TimeWeighting.FAST,
            altimeterRef = alt,
            barometerAltitude = dbl("baro.h"),
            metalSensitivity = dbl("metal.sens") ?: 0.5,
            tunerA4 = dbl("tuner.a4") ?: 440.0,
            floorHeight = dbl("floor.h") ?: 3.0,
        )
    }

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = load()
    }

    private fun android.content.SharedPreferences.Editor.putD(key: String, v: Double) =
        putLong(key, java.lang.Double.doubleToRawLongBits(v))

    fun setLevel(c: LevelCalibration) = edit {
        putD("level.x", c.flatX); putD("level.y", c.flatY); putD("level.edge", c.edge); putLong("level.at", c.setAtMs)
    }

    fun setManualDeclination(deg: Double?) = edit { if (deg == null) remove("compass.decl") else putD("compass.decl", deg) }

    fun setLuxFactor(f: Double) = edit { putD("lux.f", f); putLong("lux.at", System.currentTimeMillis()) }

    fun setSoundOffset(off: Double) = edit { putD("sound.off", off); putLong("sound.at", System.currentTimeMillis()) }

    fun setSoundWeighting(w: Weighting) = edit { putString("sound.w", w.code) }
    fun setSoundTiming(t: TimeWeighting) = edit { putString("sound.t", t.code) }

    fun setAltimeterRef(ref: Barometric.Reference) = edit {
        putD("alt.p", ref.pressureHpa); putD("alt.h", ref.altitudeM); putLong("alt.at", ref.setAtMs)
    }

    fun setBarometerAltitude(h: Double?) = edit { if (h == null) remove("baro.h") else putD("baro.h", h) }

    fun setTunerA4(hz: Double) = edit { putD("tuner.a4", hz) }

    fun setFloorHeight(m: Double) = edit { putD("floor.h", m) }

    fun setMetalSensitivity(s: Double) = edit { putD("metal.sens", s.coerceIn(0.0, 1.0)) }

    /** Bir aracın kalibrasyonunu siler (görüntü ayarları — ör. ağırlıklandırma — korunur). */
    fun reset(tool: ToolId) = edit {
        when (tool) {
            ToolId.LEVEL -> { remove("level.x"); remove("level.y"); remove("level.edge"); remove("level.at") }
            ToolId.LUX -> { remove("lux.f"); remove("lux.at") }
            ToolId.SOUND_LEVEL -> { remove("sound.off"); remove("sound.at") }
            ToolId.ALTIMETER -> { remove("alt.p"); remove("alt.h"); remove("alt.at") }
            ToolId.COMPASS -> remove("compass.decl")
            ToolId.BAROMETER -> remove("baro.h")
            ToolId.TUNER -> remove("tuner.a4")
            ToolId.FLOOR_COUNTER -> remove("floor.h")
            else -> Unit
        }
    }

    companion object {
        /** Ayarlar ekranında listelenen, kalibrasyonu olan araçlar. */
        val CALIBRATABLE = listOf(ToolId.LEVEL, ToolId.COMPASS, ToolId.BAROMETER, ToolId.ALTIMETER, ToolId.LUX, ToolId.SOUND_LEVEL, ToolId.TUNER, ToolId.FLOOR_COUNTER)
    }
}
