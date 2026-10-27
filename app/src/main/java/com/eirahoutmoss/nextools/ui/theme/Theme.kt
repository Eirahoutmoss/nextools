package com.eirahoutmoss.nextools.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eirahoutmoss.nextools.settings.ThemeMode

/**
 * Taslak "Görsel dil": koyu tema (Nex ailesi), vurgu camgöbeği / turkuaz #22D3EE — ölçüm aleti
 * ekranı hissi. Durum renkleri: yeşil = iyi, amber = dikkat, kırmızı = eşik aşıldı, gri = pasif.
 */
object NexColors {
    val Cyan = Color(0xFF22D3EE)
    val CyanDeep = Color(0xFF0891B2)
    val Green = Color(0xFF22C55E)
    val Amber = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
    val Gray = Color(0xFF6B7785)

    val Night = Color(0xFF0A0F14)
    val NightSurface = Color(0xFF111922)
    val NightSurfaceHigh = Color(0xFF18222D)
    val NightOutline = Color(0xFF2A3644)
    val LogoBlue = Color(0xFF1E7BF0)
}

/** Ölçüm göstergeleri için temaya göre değişen renkler. */
@Immutable
data class InstrumentColors(
    val accent: Color,
    val good: Color,
    val warn: Color,
    val alarm: Color,
    val passive: Color,
    val grid: Color,
    val scale: Color,
)

private val DarkInstrument = InstrumentColors(
    accent = NexColors.Cyan, good = NexColors.Green, warn = NexColors.Amber, alarm = NexColors.Red,
    passive = NexColors.Gray, grid = Color(0xFF1F2B37), scale = Color(0xFF8A97A6),
)
private val LightInstrument = InstrumentColors(
    accent = NexColors.CyanDeep, good = Color(0xFF16A34A), warn = Color(0xFFD97706), alarm = Color(0xFFDC2626),
    passive = Color(0xFF9AA4AE), grid = Color(0xFFE2E8EE), scale = Color(0xFF5F6B78),
)

val LocalInstrument = staticCompositionLocalOf { DarkInstrument }

private val Dark = darkColorScheme(
    primary = NexColors.Cyan,
    onPrimary = Color(0xFF00222A),
    primaryContainer = Color(0xFF0E3A45),
    onPrimaryContainer = Color(0xFFBFF3FC),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF00243A),
    secondaryContainer = NexColors.NightSurfaceHigh,
    onSecondaryContainer = Color(0xFFDDE6EE),
    tertiary = NexColors.LogoBlue,
    background = NexColors.Night,
    onBackground = Color(0xFFE6EDF3),
    surface = NexColors.NightSurface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = NexColors.NightSurfaceHigh,
    onSurfaceVariant = Color(0xFF9AA7B5),
    surfaceContainerLowest = Color(0xFF070B0F),
    surfaceContainerLow = Color(0xFF0E151C),
    surfaceContainer = NexColors.NightSurface,
    surfaceContainerHigh = NexColors.NightSurfaceHigh,
    surfaceContainerHighest = Color(0xFF202C38),
    outline = NexColors.NightOutline,
    outlineVariant = Color(0xFF1E2833),
    error = Color(0xFFFF8A80),
)

private val Light = lightColorScheme(
    primary = NexColors.CyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF4FB),
    onPrimaryContainer = Color(0xFF003640),
    secondary = Color(0xFF0369A1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EEF5),
    onSecondaryContainer = Color(0xFF0B2533),
    tertiary = NexColors.LogoBlue,
    background = Color(0xFFF4F7F9),
    onBackground = Color(0xFF111820),
    surface = Color.White,
    onSurface = Color(0xFF111820),
    surfaceVariant = Color(0xFFE7EDF2),
    onSurfaceVariant = Color(0xFF52606D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAFC),
    surfaceContainer = Color(0xFFEEF3F6),
    surfaceContainerHigh = Color(0xFFE8EEF2),
    surfaceContainerHighest = Color(0xFFE1E8EE),
    outline = Color(0xFFC3CDD6),
    outlineVariant = Color(0xFFDDE4EA),
    error = Color(0xFFB3261E),
)

private val NexTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Rakamlar eş aralıklı yazı tipiyle: değer değişirken titremez, alet ekranı gibi okunur. */
object NumberStyles {
    val Huge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 56.sp, letterSpacing = (-1).sp)
    val Big = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 34.sp)
    val Medium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
    val Small = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp)
}

@Composable
fun NexToolsTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalInstrument provides if (dark) DarkInstrument else LightInstrument) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = NexTypography, content = content)
    }
}
