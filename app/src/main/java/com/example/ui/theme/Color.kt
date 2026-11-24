package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Clean editorial Material 3 fallback palettes (used on Android < 12 or when Dynamic Wallpaper is toggled off).
// On Android 12+, Material You 3 dynamicLightColorScheme / dynamicDarkColorScheme takes precedence by default.

val OrbitLightColorScheme = lightColorScheme(
    primary = Color(0xFF141416),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E8EE),
    onPrimaryContainer = Color(0xFF141416),
    secondary = Color(0xFF4F5462),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEEF3),
    onSecondaryContainer = Color(0xFF1B1E26),
    tertiary = Color(0xFF5A4FCF),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5E2FF),
    onTertiaryContainer = Color(0xFF190E66),
    background = Color(0xFFFCFCFD),
    onBackground = Color(0xFF101114),
    surface = Color(0xFFFCFCFD),
    onSurface = Color(0xFF101114),
    surfaceVariant = Color(0xFFF1F2F6),
    onSurfaceVariant = Color(0xFF5F6472),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFF2F3F7),
    surfaceContainerHigh = Color(0xFFECEEF3),
    surfaceContainerHighest = Color(0xFFE4E6ED),
    outline = Color(0xFF9EA3B0),
    outlineVariant = Color(0xFFE2E4EC),
    error = Color(0xFFD92D4B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF41000D)
)

val OrbitDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF4F5F8),
    onPrimary = Color(0xFF101114),
    primaryContainer = Color(0xFF282A31),
    onPrimaryContainer = Color(0xFFF4F5F8),
    secondary = Color(0xFFBDC2D0),
    onSecondary = Color(0xFF1F222A),
    secondaryContainer = Color(0xFF23262E),
    onSecondaryContainer = Color(0xFFE1E4EE),
    tertiary = Color(0xFFC4BDFF),
    onTertiary = Color(0xFF281C7A),
    tertiaryContainer = Color(0xFF3E3497),
    onTertiaryContainer = Color(0xFFE6E3FF),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFF2F3F7),
    surface = Color(0xFF0E0F12),
    onSurface = Color(0xFFF2F3F7),
    surfaceVariant = Color(0xFF1B1D23),
    onSurfaceVariant = Color(0xFF969BA8),
    surfaceContainerLowest = Color(0xFF090A0C),
    surfaceContainerLow = Color(0xFF14161A),
    surfaceContainer = Color(0xFF191B20),
    surfaceContainerHigh = Color(0xFF20232A),
    surfaceContainerHighest = Color(0xFF292C35),
    outline = Color(0xFF636877),
    outlineVariant = Color(0xFF262932),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF490011),
    errorContainer = Color(0xFF6E0820),
    onErrorContainer = Color(0xFFFFDADF)
)

// Semantic engagement accents harmonized for both light & dark themes
val OrbitHeartRed = Color(0xFFFF3040)
val OrbitRepostGreen = Color(0xFF00BA7C)
