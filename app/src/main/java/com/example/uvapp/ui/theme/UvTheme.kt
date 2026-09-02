package com.example.uvapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.uvapp.viewmodel.AccentColor
import com.example.uvapp.viewmodel.ThemeMode

/**
 * Typography per the high-fi spec (Segoe UI in the mockups, Roboto on device):
 * hero 62/800, countdown 44/700, section titles 16–20/700, body 15, captions 10–12.
 */
val UvTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 62.sp, lineHeight = 64.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp),
    titleSmall = TextStyle(fontSize = 11.sp),
)

/**
 * Custom design tokens. Values are derived/inlined here — no separate
 * constants, and near-duplicate hues are merged (e.g. indicator/ringBase use
 * outline, refreshIcon uses onBackground, warning uses error).
 */
data class UvColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,   // nav bar, locate button, reset pill
    val outline: Color,          // borders, radio rings, chart gridlines, timer ring base
    val accent: Color,
    val onBackground: Color,     // primary text + refresh icon
    val textSecondary: Color,    // secondary text and captions
    val activePill: Color,       // derived from accent
    val error: Color,            // error icon + timer warning
    val errorBanner: Color,      // derived from error
    val warningResetPill: Color, // derived from error
    val scrim: Color,
    val devCard: Color,
    val devStroke: Color,
    val devHeader: Color,
    val devSub: Color,
    val devButton: Color,
    val devButtonStroke: Color,
    val devToggleOn: Color,
    val devToggleOff: Color,
    val devExpandedBg: Color,
    val devSliderTrack: Color,
    val isDark: Boolean,
)

val LocalUvColors = staticCompositionLocalOf { lightUvColors(Color(0xFFE8590C)) }

private fun lightUvColors(accent: Color) = UvColors(
    background = Color(0xFFFFF6F0),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF9EFE7),
    outline = Color(0xFFE4D5C8),
    accent = accent,
    onBackground = Color(0xFF2B2018),
    textSecondary = Color(0xFF7A6A5C),
    activePill = lerp(accent, Color.White, 0.85f),
    error = Color(0xFFB3261E),
    errorBanner = lerp(Color(0xFFB3261E), Color.White, 0.93f),
    warningResetPill = lerp(Color(0xFFB3261E), Color.White, 0.85f),
    scrim = Color(0x8C1C120A),
    // Dev palette derived from two base hues.
    devCard = lerp(Color(0xFF6E1A12), Color.White, 0.90f),
    devStroke = Color(0xFF6E1A12).copy(alpha = 0.30f),
    devHeader = Color(0xFF6E1A12),
    devSub = Color(0xFF6E1A12).copy(alpha = 0.55f),
    devButton = lerp(Color(0xFF6E1A12), Color.White, 0.97f),
    devButtonStroke = Color(0xFF6E1A12).copy(alpha = 0.40f),
    devToggleOn = Color(0xFFC62828),
    devToggleOff = Color(0xFFC62828).copy(alpha = 0.35f),
    devExpandedBg = Color.White.copy(alpha = 0.55f),
    devSliderTrack = Color(0xFFC62828).copy(alpha = 0.20f),
    isDark = false,
)

private fun darkUvColors(accent: Color) = UvColors(
    background = Color(0xFF161210),
    surface = Color(0xFF211B17),
    surfaceVariant = Color(0xFF2A231E),
    outline = Color(0xFF453B33),
    accent = accent,
    onBackground = Color(0xFFF1E7DD),
    textSecondary = Color(0xFFB9A99B),
    activePill = lerp(accent, Color(0xFF211B17), 0.8f),
    error = Color(0xFFFFB4AB),
    errorBanner = Color(0xFFFFB4AB).copy(alpha = 0.22f),
    warningResetPill = Color(0xFF211B17),
    scrim = Color(0xB3000000),
    devCard = lerp(Color(0xFFFFB4A8), Color(0xFF211B17), 0.86f),
    devStroke = Color(0xFFFFB4A8).copy(alpha = 0.30f),
    devHeader = Color(0xFFFFB4A8),
    devSub = Color(0xFFFFB4A8).copy(alpha = 0.75f),
    devButton = lerp(Color(0xFFFFB4A8), Color(0xFF211B17), 0.92f),
    devButtonStroke = Color(0xFFFFB4A8).copy(alpha = 0.40f),
    devToggleOn = Color(0xFFFF8A80),
    devToggleOff = Color(0xFFFF8A80).copy(alpha = 0.30f),
    devExpandedBg = Color(0xFF211B17).copy(alpha = 0.12f),
    devSliderTrack = Color(0xFFFF8A80).copy(alpha = 0.40f),
    isDark = true,
)

/** Material scheme derived from the palette builders (single source of truth). */
private fun lightScheme(accent: Color): ColorScheme {
    val c = lightUvColors(accent)
    return lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        background = c.background,
        onBackground = c.onBackground,
        surface = c.surface,
        onSurface = c.onBackground,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.textSecondary,
        outline = c.outline,
        error = c.error,
    )
}

private fun darkScheme(accent: Color): ColorScheme {
    val c = darkUvColors(accent)
    return darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF3A1400),
        background = c.background,
        onBackground = c.onBackground,
        surface = c.surface,
        onSurface = c.onBackground,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.textSecondary,
        outline = c.outline,
        error = c.error,
    )
}

/** Applies the Solar palette with the user's theme mode and accent colour. */
@Composable
fun UvAppTheme(
    themeMode: ThemeMode,
    accent: AccentColor,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val (accentLight, accentDark) = accentPalette(accent)
    val colors = if (dark) darkUvColors(accentDark) else lightUvColors(accentLight)
    CompositionLocalProvider(LocalUvColors provides colors) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(accentDark) else lightScheme(accentLight),
            typography = UvTypography,
            content = content,
        )
    }
}

/** Accessor for the custom tokens. */
val UvTheme: UvColors
    @Composable get() = LocalUvColors.current
