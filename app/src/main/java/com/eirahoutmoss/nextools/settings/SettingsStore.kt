package com.eirahoutmoss.nextools.settings

import android.content.Context
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.units.PressureUnit
import com.eirahoutmoss.nextools.core.units.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode(val code: String) {
    DARK("dark"), LIGHT("light"), SYSTEM("system");

    companion object {
        /** Varsayılan koyu (Nex ailesi). */
        fun fromCode(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: DARK
    }
}

data class Settings(
    val theme: ThemeMode,
    val units: UnitSystem,
    val pressureUnit: PressureUnit,
    val favorites: Set<ToolId>,
)

/** Uygulama ayarları. Cihazda saklanır; hiçbir yere gönderilmez. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private fun load() = Settings(
        theme = ThemeMode.fromCode(prefs.getString(K_THEME, null)),
        units = UnitSystem.fromCode(prefs.getString(K_UNITS, null)),
        pressureUnit = PressureUnit.fromCode(prefs.getString(K_PRESSURE, null)),
        favorites = prefs.getStringSet(K_FAV, emptySet()).orEmpty().mapNotNull { ToolId.fromKey(it) }.toSet(),
    )

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(K_THEME, mode.code).apply()
        _state.update { it.copy(theme = mode) }
    }

    fun setUnits(system: UnitSystem) {
        prefs.edit().putString(K_UNITS, system.code).apply()
        _state.update { it.copy(units = system) }
    }

    fun setPressureUnit(unit: PressureUnit) {
        prefs.edit().putString(K_PRESSURE, unit.code).apply()
        _state.update { it.copy(pressureUnit = unit) }
    }

    fun toggleFavorite(id: ToolId) {
        val next = _state.value.favorites.let { if (id in it) it - id else it + id }
        prefs.edit().putStringSet(K_FAV, next.map { it.key }.toSet()).apply()
        _state.update { it.copy(favorites = next) }
    }

    private companion object {
        const val K_THEME = "theme"
        const val K_UNITS = "units"
        const val K_PRESSURE = "pressure_unit"
        const val K_FAV = "favorites"
    }
}
