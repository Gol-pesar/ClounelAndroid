package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.model.AppThemeMode

private val SocksDarkColorScheme = darkColorScheme(
    primary = ElectricEmerald,
    onPrimary = DeepEmerald,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = ElectricEmerald,
    secondary = SignalCyan,
    onSecondary = Color(0xFF002438),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = SignalCyan,
    tertiary = AmberPulse,
    onTertiary = Color(0xFF3A2200),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = CarbonSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CarbonSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = CarbonSurfaceHigh,
    outline = OutlineObsidian,
    error = CrimsonAlert,
    onError = Color(0xFF470818),
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonAlert,
)

private val SocksLightColorScheme = lightColorScheme(
    primary = LightEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = LightEmeraldContainer,
    onPrimaryContainer = Color(0xFF00382C),
    secondary = LightCyanSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightCyanContainer,
    onSecondaryContainer = Color(0xFF00344E),
    tertiary = Color(0xFFD97706),
    background = LightCanvas,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerHigh = Color(0xFFDDE7EF),
    outline = LightOutline,
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
)

@Composable
fun SocksFlowTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) SocksDarkColorScheme else SocksLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SocksFlowTypography,
        content = content,
    )
}
