package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.DarkThemeMode
import com.example.data.preferences.OrbitFontPreset
import com.example.data.preferences.OrbitThemeConfig
import com.example.ui.haptics.LocalOrbitHaptics
import com.example.ui.theme.fontFamilyForPreset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val RGB_PRESET_COLORS = listOf(
    0xFF4F46E5L, // Indigo
    0xFF0EA5E9L, // Sky
    0xFF10B981L, // Emerald
    0xFFF59E0BL, // Amber
    0xFFF43F5EL, // Rose
    0xFF9333EAL, // Violet
    0xFFFF5722L  // Coral
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioSheet(
    themeConfig: OrbitThemeConfig,
    onDynamicWallpaperChanged: (Boolean) -> Unit,
    onDarkThemeModeChanged: (DarkThemeMode) -> Unit,
    onCustomSeedColorChanged: (Long?) -> Unit,
    onFontPresetChanged: (OrbitFontPreset) -> Unit = {},
    onFontScaleChanged: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.theme_studio_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            ThemeStudioContent(
                themeConfig = themeConfig,
                onDynamicWallpaperChanged = onDynamicWallpaperChanged,
                onDarkThemeModeChanged = onDarkThemeModeChanged,
                onCustomSeedColorChanged = onCustomSeedColorChanged,
                onFontPresetChanged = onFontPresetChanged,
                onFontScaleChanged = onFontScaleChanged
            )
        }
    }
}

/**
 * Reusable Theme Studio body embedded in both the Theme Studio modal sheet and App Settings.
 */
@Composable
fun ThemeStudioContent(
    themeConfig: OrbitThemeConfig,
    onDynamicWallpaperChanged: (Boolean) -> Unit,
    onDarkThemeModeChanged: (DarkThemeMode) -> Unit,
    onCustomSeedColorChanged: (Long?) -> Unit,
    onFontPresetChanged: (OrbitFontPreset) -> Unit = {},
    onFontScaleChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(modifier = modifier.fillMaxWidth()) {
        // Material You 3 Wallpaper Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Material You 3 Wallpaper",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (supportsDynamicColor) {
                            "Syncs Orbit’s palette with your Android wallpaper."
                        } else {
                            "Requires Android 12+. Use the RGB wheel below."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = themeConfig.dynamicWallpaperEnabled && supportsDynamicColor,
                    onCheckedChange = {
                        haptics.performTap()
                        onDynamicWallpaperChanged(it)
                    },
                    enabled = supportsDynamicColor,
                    modifier = Modifier.testTag("dynamic_wallpaper_switch")
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Live Palette Preview Strip
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StudioSwatchPill("Primary", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StudioSwatchPill("Secondary", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            StudioSwatchPill("Tertiary", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            StudioSwatchPill("Surface", MaterialTheme.colorScheme.surfaceContainerHighest, Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))

        // Light / Dark / System Mode
        Text(
            text = "Brightness Mode",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DarkThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = themeConfig.darkThemeMode == mode,
                    onClick = {
                        haptics.performTap()
                        onDarkThemeModeChanged(mode)
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = DarkThemeMode.entries.size
                    ),
                    modifier = Modifier.testTag("theme_mode_${mode.id}")
                ) {
                    Text(mode.label)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Collapsible App Fonts Dropdown (Twitter/X, Instagram, Facebook, Threads, Orbit Default + Text Size Slider)
        val fontPercent = (themeConfig.fontScale.coerceIn(0.85f, 1.35f) * 100f).toInt()
        CollapsibleSectionCard(
            title = "App Fonts",
            subtitle = "${themeConfig.fontPreset.label} · Text size $fontPercent%",
            icon = Icons.Outlined.FontDownload,
            initiallyExpanded = false,
            testTag = "theme_studio_fonts_dropdown"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Text Size Slider Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Text Size",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Drag the slider to increase or decrease text size across Orbit ($fontPercent%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (kotlin.math.abs(themeConfig.fontScale - 1.0f) > 0.01f) {
                                TextButton(
                                    onClick = {
                                        haptics.performTap()
                                        onFontScaleChanged(1.0f)
                                    },
                                    modifier = Modifier.testTag("theme_studio_font_size_reset")
                                ) {
                                    Text("Reset")
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "$fontPercent%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "A",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Slider(
                                value = themeConfig.fontScale.coerceIn(0.85f, 1.35f),
                                onValueChange = { newScale ->
                                    onFontScaleChanged(newScale.coerceIn(0.85f, 1.35f))
                                },
                                valueRange = 0.85f..1.35f,
                                steps = 9,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_studio_font_size_slider")
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "A",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OrbitFontPreset.entries.forEach { preset ->
                    val selected = themeConfig.fontPreset == preset
                    val presetFamily = remember(preset) { fontFamilyForPreset(preset) }
                    Surface(
                        onClick = {
                            haptics.performMedium()
                            onFontPresetChanged(preset)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 0.75.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("font_preset_${preset.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = presetFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${preset.styleSubtitle} — The quick brown fox jumps over Orbit",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = presetFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            if (selected) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected font",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Collapsible Interactive RGB Color Wheel Section
        CollapsibleSectionCard(
            title = "RGB Color Wheel",
            subtitle = "Drag anywhere on the wheel to craft a custom Orbit palette.",
            icon = Icons.Outlined.Palette,
            initiallyExpanded = false,
            testTag = "theme_studio_rgb_dropdown"
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (themeConfig.customSeedColorArgb != null) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                haptics.performTap()
                                onCustomSeedColorChanged(null)
                            },
                            modifier = Modifier.testTag("rgb_wheel_reset_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset custom color",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Reset custom color")
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                RgbColorWheelPicker(
                    selectedArgb = themeConfig.customSeedColorArgb
                        ?: MaterialTheme.colorScheme.primary.toArgb().toLong(),
                    onColorSelected = { argb ->
                        onCustomSeedColorChanged(argb)
                    }
                )
            }
        }
    }
}

@Composable
fun RgbColorWheelPicker(
    selectedArgb: Long,
    onColorSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val initialHsv = remember(selectedArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(selectedArgb.toInt(), it) }
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1].coerceIn(0.05f, 1f)) }
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceIn(0.35f, 1f)) }

    LaunchedEffect(selectedArgb) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(selectedArgb.toInt(), hsv)
        hue = hsv[0]
        saturation = hsv[1].coerceIn(0.05f, 1f)
        value = hsv[2].coerceIn(0.35f, 1f)
    }

    val currentColorInt = remember(hue, saturation, value) {
        AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    }
    val currentColor = Color(currentColorInt)
    val hexCode = remember(currentColorInt) {
        String.format("#%06X", 0xFFFFFF and currentColorInt)
    }

    fun emitCurrentColor() {
        val argb = (AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)).toLong() and 0xFFFFFFFFL) or 0xFF000000L
        onColorSelected(argb)
    }

    fun updateFromPosition(pos: Offset, width: Float, height: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = (min(width, height) / 2f).coerceAtLeast(1f)
        val dx = pos.x - cx
        val dy = pos.y - cy
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val sat = (dist / radius).coerceIn(0f, 1f)
        val angleDeg = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()) + 360f) % 360f
        hue = angleDeg
        saturation = sat
        emitCurrentColor()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Interactive Circular RGB Hue/Saturation Wheel
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(204.dp)
                .testTag("rgb_color_wheel_canvas")
        ) {
            Canvas(
                modifier = Modifier
                    .size(196.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            haptics.performTap()
                            updateFromPosition(offset, size.width.toFloat(), size.height.toFloat())
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                haptics.performTap()
                                updateFromPosition(offset, size.width.toFloat(), size.height.toFloat())
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                updateFromPosition(change.position, size.width.toFloat(), size.height.toFloat())
                            }
                        )
                    }
            ) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                val sweepBrush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFF0000),
                        Color(0xFFFFFF00),
                        Color(0xFF00FF00),
                        Color(0xFF00FFFF),
                        Color(0xFF0000FF),
                        Color(0xFFFF00FF),
                        Color(0xFFFF0000)
                    ),
                    center = center
                )
                drawCircle(brush = sweepBrush, radius = radius, center = center)

                val radialWhite = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = radius
                )
                drawCircle(brush = radialWhite, radius = radius, center = center)

                // Dim wheel gently to reflect brightness slider
                val dimAlpha = (1f - value).coerceIn(0f, 0.65f)
                if (dimAlpha > 0.01f) {
                    drawCircle(
                        color = Color.Black.copy(alpha = dimAlpha),
                        radius = radius,
                        center = center
                    )
                }

                // Crisp outer border
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draggable selector thumb
                val rad = hue * PI / 180.0
                val thumbX = center.x + (cos(rad) * saturation * radius).toFloat()
                val thumbY = center.y + (sin(rad) * saturation * radius).toFloat()
                val thumbOffset = Offset(thumbX, thumbY)

                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    radius = 12.dp.toPx(),
                    center = thumbOffset
                )
                drawCircle(
                    color = currentColor,
                    radius = 10.dp.toPx(),
                    center = thumbOffset
                )
                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = thumbOffset,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Live Color Chip + Hex Badge + Brightness Slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Text(
                text = hexCode,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("rgb_wheel_hex_label")
            )
            Slider(
                value = value,
                onValueChange = { nextVal ->
                    value = nextVal
                    emitCurrentColor()
                },
                valueRange = 0.35f..1.0f,
                modifier = Modifier
                    .weight(1f)
                    .testTag("rgb_wheel_brightness_slider")
            )
        }

        Spacer(Modifier.height(10.dp))

        // Quick Preset Swatches
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            RGB_PRESET_COLORS.forEachIndexed { idx, presetArgb ->
                val presetColor = Color(presetArgb.toInt())
                val isSelected = selectedArgb == presetArgb
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(presetColor)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable {
                            haptics.performTap()
                            onColorSelected(presetArgb)
                        }
                        .testTag("rgb_preset_$idx")
                )
            }
        }
    }
}

@Composable
private fun StudioSwatchPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color)
                .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
