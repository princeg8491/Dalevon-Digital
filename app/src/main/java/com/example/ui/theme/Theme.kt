package com.example.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.preferences.OrbitThemeConfig

val OrbitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalOrbitThemeConfig = staticCompositionLocalOf { OrbitThemeConfig() }

private fun hsvColor(hue: Float, sat: Float, value: Float): Color {
    val wrappedHue = ((hue % 360f) + 360f) % 360f
    return Color(AndroidColor.HSVToColor(floatArrayOf(wrappedHue, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))))
}

private fun contrastingOnColor(bg: Color): Color =
    if (bg.luminance() > 0.45f) Color(0xFF101114) else Color(0xFFFFFFFF)

/**
 * Generates a cohesive Material 3 Light or Dark ColorScheme derived from any user-picked RGB wheel color.
 */
fun buildCustomOrbitColorScheme(seedArgb: Long, darkTheme: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seedArgb.toInt(), hsv)
    val hue = hsv[0]
    val sat = hsv[1].coerceAtLeast(0.28f)

    return if (darkTheme) {
        val primary = hsvColor(hue, (sat * 0.72f).coerceIn(0.32f, 0.78f), 0.92f)
        val primaryContainer = hsvColor(hue, (sat * 0.85f).coerceIn(0.35f, 0.82f), 0.30f)
        val secondary = hsvColor(hue + 24f, (sat * 0.45f).coerceIn(0.20f, 0.55f), 0.84f)
        val secondaryContainer = hsvColor(hue + 24f, (sat * 0.42f).coerceIn(0.18f, 0.52f), 0.24f)
        val tertiary = hsvColor(hue + 56f, (sat * 0.65f).coerceIn(0.28f, 0.70f), 0.88f)
        val tertiaryContainer = hsvColor(hue + 56f, (sat * 0.65f).coerceIn(0.28f, 0.70f), 0.28f)
        darkColorScheme(
            primary = primary,
            onPrimary = contrastingOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = hsvColor(hue, 0.24f, 0.96f),
            secondary = secondary,
            onSecondary = contrastingOnColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = hsvColor(hue + 24f, 0.18f, 0.94f),
            tertiary = tertiary,
            onTertiary = contrastingOnColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = hsvColor(hue + 56f, 0.22f, 0.96f),
            background = hsvColor(hue, 0.08f, 0.07f),
            onBackground = Color(0xFFF3F4F8),
            surface = hsvColor(hue, 0.08f, 0.07f),
            onSurface = Color(0xFFF3F4F8),
            surfaceVariant = hsvColor(hue, 0.12f, 0.14f),
            onSurfaceVariant = Color(0xFFA0A5B4),
            surfaceContainerLowest = hsvColor(hue, 0.06f, 0.05f),
            surfaceContainerLow = hsvColor(hue, 0.09f, 0.10f),
            surfaceContainer = hsvColor(hue, 0.10f, 0.13f),
            surfaceContainerHigh = hsvColor(hue, 0.12f, 0.16f),
            surfaceContainerHighest = hsvColor(hue, 0.14f, 0.20f),
            outline = hsvColor(hue, 0.14f, 0.44f),
            outlineVariant = hsvColor(hue, 0.14f, 0.20f),
            error = Color(0xFFFF6B81),
            onError = Color(0xFF490011),
            errorContainer = Color(0xFF6E0820),
            onErrorContainer = Color(0xFFFFDADF)
        )
    } else {
        val primary = hsvColor(hue, sat.coerceIn(0.45f, 0.92f), 0.62f)
        val primaryContainer = hsvColor(hue, (sat * 0.30f).coerceIn(0.14f, 0.36f), 0.96f)
        val secondary = hsvColor(hue + 24f, (sat * 0.48f).coerceIn(0.22f, 0.56f), 0.50f)
        val secondaryContainer = hsvColor(hue + 24f, (sat * 0.22f).coerceIn(0.10f, 0.26f), 0.95f)
        val tertiary = hsvColor(hue + 56f, (sat * 0.62f).coerceIn(0.32f, 0.72f), 0.56f)
        val tertiaryContainer = hsvColor(hue + 56f, (sat * 0.26f).coerceIn(0.12f, 0.32f), 0.95f)
        lightColorScheme(
            primary = primary,
            onPrimary = contrastingOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = hsvColor(hue, 0.85f, 0.22f),
            secondary = secondary,
            onSecondary = contrastingOnColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = hsvColor(hue + 24f, 0.70f, 0.22f),
            tertiary = tertiary,
            onTertiary = contrastingOnColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = hsvColor(hue + 56f, 0.75f, 0.22f),
            background = hsvColor(hue, 0.02f, 0.99f),
            onBackground = Color(0xFF101114),
            surface = hsvColor(hue, 0.02f, 0.99f),
            onSurface = Color(0xFF101114),
            surfaceVariant = hsvColor(hue, 0.06f, 0.94f),
            onSurfaceVariant = Color(0xFF5A5F6E),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = hsvColor(hue, 0.04f, 0.97f),
            surfaceContainer = hsvColor(hue, 0.06f, 0.95f),
            surfaceContainerHigh = hsvColor(hue, 0.08f, 0.92f),
            surfaceContainerHighest = hsvColor(hue, 0.10f, 0.89f),
            outline = hsvColor(hue, 0.12f, 0.64f),
            outlineVariant = hsvColor(hue, 0.10f, 0.86f),
            error = Color(0xFFD92D4B),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDADF),
            onErrorContainer = Color(0xFF41000D)
        )
    }
}

@Composable
fun OrbitTheme(
    themeConfig: OrbitThemeConfig = OrbitThemeConfig(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = themeConfig.resolveDarkTheme(systemDark)
    val context = LocalContext.current

    val colorScheme = remember(
        themeConfig.dynamicWallpaperEnabled,
        themeConfig.customSeedColorArgb,
        useDarkTheme
    ) {
        when {
            themeConfig.dynamicWallpaperEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            themeConfig.customSeedColorArgb != null -> {
                buildCustomOrbitColorScheme(themeConfig.customSeedColorArgb, useDarkTheme)
            }
            useDarkTheme -> OrbitDarkColorScheme
            else -> OrbitLightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    val typography = remember(themeConfig.fontPreset) {
        buildOrbitTypography(themeConfig.fontPreset)
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, themeConfig.fontScale) {
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * themeConfig.fontScale.coerceIn(0.85f, 1.35f)
        )
    }

    CompositionLocalProvider(
        LocalOrbitThemeConfig provides themeConfig,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = OrbitShapes,
            content = content
        )
    }
}

enum class OrbitPixelBadgeTone {
    SKY_BLUE,
    PERIWINKLE,
    PINK,
    AMBER,
    PEACH_ORANGE,
    MINT
}

data class OrbitPixelBadgeColors(
    val container: Color,
    val content: Color
)

@Composable
fun rememberOrbitPixelBadgeColors(tone: OrbitPixelBadgeTone): OrbitPixelBadgeColors {
    val isDark = LocalOrbitThemeConfig.current.resolveDarkTheme(isSystemInDarkTheme())
    return remember(tone, isDark) {
        if (isDark) {
            when (tone) {
                OrbitPixelBadgeTone.SKY_BLUE -> OrbitPixelBadgeColors(Color(0xFF7FD2FA), Color(0xFF00344A))
                OrbitPixelBadgeTone.PERIWINKLE -> OrbitPixelBadgeColors(Color(0xFFC7D6FF), Color(0xFF1A2C5B))
                OrbitPixelBadgeTone.PINK -> OrbitPixelBadgeColors(Color(0xFFFF9BD3), Color(0xFF5A0F3B))
                OrbitPixelBadgeTone.AMBER -> OrbitPixelBadgeColors(Color(0xFFE5BA72), Color(0xFF422C00))
                OrbitPixelBadgeTone.PEACH_ORANGE -> OrbitPixelBadgeColors(Color(0xFFFFBB78), Color(0xFF4B2800))
                OrbitPixelBadgeTone.MINT -> OrbitPixelBadgeColors(Color(0xFF91ECC6), Color(0xFF003824))
            }
        } else {
            when (tone) {
                OrbitPixelBadgeTone.SKY_BLUE -> OrbitPixelBadgeColors(Color(0xFFBDE9FF), Color(0xFF004C6A))
                OrbitPixelBadgeTone.PERIWINKLE -> OrbitPixelBadgeColors(Color(0xFFDCE4FF), Color(0xFF233970))
                OrbitPixelBadgeTone.PINK -> OrbitPixelBadgeColors(Color(0xFFFFD8EC), Color(0xFF6B1547))
                OrbitPixelBadgeTone.AMBER -> OrbitPixelBadgeColors(Color(0xFFFCE2B0), Color(0xFF523800))
                OrbitPixelBadgeTone.PEACH_ORANGE -> OrbitPixelBadgeColors(Color(0xFFFFDDBB), Color(0xFF593100))
                OrbitPixelBadgeTone.MINT -> OrbitPixelBadgeColors(Color(0xFFBDF6DD), Color(0xFF004D32))
            }
        }
    }
}

