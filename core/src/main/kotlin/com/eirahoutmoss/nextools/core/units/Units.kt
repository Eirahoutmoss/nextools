package com.eirahoutmoss.nextools.core.units

import com.eirahoutmoss.nextools.core.measure.LuxMath

/** Ayarlar > Birimler. */
enum class UnitSystem(val code: String) {
    METRIC("metric"), IMPERIAL("imperial");

    companion object {
        fun fromCode(code: String?): UnitSystem = entries.firstOrNull { it.code == code } ?: METRIC
    }
}

/** Basınç birimi metrik sistemde bile seçilebilir (hPa = mbar, mmHg). */
enum class PressureUnit(val code: String, val symbol: String) {
    HPA("hpa", "hPa"), MMHG("mmhg", "mmHg"), INHG("inhg", "inHg");

    fun fromHpa(hpa: Double): Double = when (this) {
        HPA -> hpa
        MMHG -> hpa * 0.750061683
        INHG -> hpa * 0.0295299830714
    }

    companion object {
        fun fromCode(code: String?): PressureUnit = entries.firstOrNull { it.code == code } ?: HPA
    }
}

object Units {
    const val FEET_PER_METER = 3.280839895

    fun length(m: Double, system: UnitSystem): Double = if (system == UnitSystem.IMPERIAL) m * FEET_PER_METER else m
    fun lengthToMeters(v: Double, system: UnitSystem): Double = if (system == UnitSystem.IMPERIAL) v / FEET_PER_METER else v
    fun lengthSymbol(system: UnitSystem): String = if (system == UnitSystem.IMPERIAL) "ft" else "m"

    fun illuminance(lux: Double, system: UnitSystem): Double =
        if (system == UnitSystem.IMPERIAL) lux / LuxMath.LUX_PER_FOOT_CANDLE else lux
    fun illuminanceSymbol(system: UnitSystem): String = if (system == UnitSystem.IMPERIAL) "fc" else "lx"

    fun celsiusTo(c: Double, system: UnitSystem): Double = if (system == UnitSystem.IMPERIAL) c * 9 / 5 + 32 else c
    fun temperatureSymbol(system: UnitSystem): String = if (system == UnitSystem.IMPERIAL) "°F" else "°C"
}
