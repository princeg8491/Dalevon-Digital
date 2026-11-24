package com.example.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

/**
 * Customizable Android haptic engine supporting user-controlled master toggle and strength slider
 * for buttons, long-press peeks, and subtle scroll tick feedback.
 */
class OrbitHapticController(
    context: Context?,
    var enabled: Boolean = true,
    var strength: Float = 0.65f
) {
    private val vibrator: Vibrator? = context?.let { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private var lastScrollTickMs: Long = 0L

    fun performTap() {
        pulse(durationMs = 14L, scale = 0.72f)
    }

    fun performMedium() {
        pulse(durationMs = 22L, scale = 0.90f)
    }

    fun performHeavy() {
        pulse(durationMs = 34L, scale = 1.0f)
    }

    fun performSelectionTick() {
        pulse(durationMs = 10L, scale = 0.55f)
    }

    fun performScrollTick() {
        val now = System.currentTimeMillis()
        if (now - lastScrollTickMs < 85L) return
        lastScrollTickMs = now
        pulse(durationMs = 8L, scale = 0.38f)
    }

    /**
     * Subtle, springy tactile feedback when liking or unliking a post.
     * Liking emits a crisp rising micro-double-tap; unliking emits a soft release tick.
     */
    fun performLike(isLiking: Boolean = true) {
        if (!enabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val s = strength.coerceIn(0.1f, 1.0f)
        runCatching {
            if (isLiking) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vib.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                        VibrationEffect.Composition.PRIMITIVE_CLICK
                    )
                ) {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (0.42f * s).coerceIn(0.12f, 1f))
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (0.72f * s).coerceIn(0.18f, 1f), 28)
                        .compose()
                    vib.vibrate(composition)
                } else {
                    waveformPulse(
                        firstMs = 9L, firstScale = 0.40f,
                        gapMs = 26L,
                        secondMs = 14L, secondScale = 0.68f
                    )
                }
            } else {
                subtleReleaseTick(vib, s, fallbackMs = 8L, fallbackScale = 0.30f)
            }
        }
    }

    /**
     * Subtle tactile bookmark "latch" feedback when saving or removing a saved post.
     */
    fun performSave(isSaving: Boolean = true) {
        if (!enabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val s = strength.coerceIn(0.1f, 1.0f)
        runCatching {
            if (isSaving) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vib.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_TICK
                    )
                ) {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (0.64f * s).coerceIn(0.16f, 1f))
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (0.40f * s).coerceIn(0.12f, 1f), 36)
                        .compose()
                    vib.vibrate(composition)
                } else {
                    waveformPulse(
                        firstMs = 12L, firstScale = 0.62f,
                        gapMs = 34L,
                        secondMs = 7L, secondScale = 0.34f
                    )
                }
            } else {
                subtleReleaseTick(vib, s, fallbackMs = 7L, fallbackScale = 0.28f)
            }
        }
    }

    /**
     * Subtle airy send-off feedback when opening the share sheet for a post or frame.
     */
    fun performShare() {
        if (!enabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val s = strength.coerceIn(0.1f, 1.0f)
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vib.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                        VibrationEffect.Composition.PRIMITIVE_TICK
                    ) -> {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, (0.50f * s).coerceIn(0.14f, 1f))
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (0.44f * s).coerceIn(0.12f, 1f), 20)
                        .compose()
                    vib.vibrate(composition)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vib.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                        VibrationEffect.Composition.PRIMITIVE_CLICK
                    ) -> {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (0.44f * s).coerceIn(0.12f, 1f))
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (0.54f * s).coerceIn(0.15f, 1f), 20)
                        .compose()
                    vib.vibrate(composition)
                }
                else -> {
                    waveformPulse(
                        firstMs = 10L, firstScale = 0.44f,
                        gapMs = 22L,
                        secondMs = 7L, secondScale = 0.34f
                    )
                }
            }
        }
    }

    /**
     * Crisp double-pulse confirmation haptic fired when a Firestore pull-to-refresh completes.
     */
    fun performRefreshComplete() {
        if (!enabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val clampedStrength = strength.coerceIn(0.1f, 1.0f)
        val firstDur = (14L * (0.7f + 0.45f * clampedStrength)).roundToInt().toLong().coerceAtLeast(8L)
        val secondDur = (22L * (0.7f + 0.50f * clampedStrength)).roundToInt().toLong().coerceAtLeast(10L)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val firstAmp = (255f * clampedStrength * 0.65f).roundToInt().coerceIn(20, 255)
                val secondAmp = (255f * clampedStrength * 0.95f).roundToInt().coerceIn(28, 255)
                val timings = longArrayOf(0L, firstDur, 42L, secondDur)
                val amplitudes = intArrayOf(0, firstAmp, 0, secondAmp)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(secondDur)
            }
        }
    }

    private fun subtleReleaseTick(vib: Vibrator, strength: Float, fallbackMs: Long, fallbackScale: Float) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) -> {
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, (0.42f * strength).coerceIn(0.12f, 1f))
                    .compose()
                vib.vibrate(composition)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) -> {
                val composition = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (0.36f * strength).coerceIn(0.12f, 1f))
                    .compose()
                vib.vibrate(composition)
            }
            else -> pulse(durationMs = fallbackMs, scale = fallbackScale)
        }
    }

    private fun waveformPulse(
        firstMs: Long,
        firstScale: Float,
        gapMs: Long,
        secondMs: Long,
        secondScale: Float
    ) {
        val vib = vibrator ?: return
        val s = strength.coerceIn(0.1f, 1.0f)
        val dur1 = (firstMs * (0.65f + 0.45f * s)).roundToInt().toLong().coerceAtLeast(6L)
        val dur2 = (secondMs * (0.65f + 0.45f * s)).roundToInt().toLong().coerceAtLeast(6L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp1 = (255f * s * firstScale).roundToInt().coerceIn(18, 255)
            val amp2 = (255f * s * secondScale).roundToInt().coerceIn(18, 255)
            val timings = longArrayOf(0L, dur1, gapMs, dur2)
            val amplitudes = intArrayOf(0, amp1, 0, amp2)
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(dur2)
        }
    }

    private fun pulse(durationMs: Long, scale: Float) {
        if (!enabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val clampedStrength = strength.coerceIn(0.1f, 1.0f)
        val scaledDuration = (durationMs * (0.65f + 0.55f * clampedStrength)).roundToInt().toLong().coerceAtLeast(6L)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (255f * clampedStrength * scale).roundToInt().coerceIn(18, 255)
                vib.vibrate(VibrationEffect.createOneShot(scaledDuration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(scaledDuration)
            }
        }
    }
}

val LocalOrbitHaptics = staticCompositionLocalOf { OrbitHapticController(null) }

@Composable
fun rememberOrbitHapticController(
    enabled: Boolean,
    strength: Float
): OrbitHapticController {
    val context = LocalContext.current.applicationContext
    val controller = remember(context) { OrbitHapticController(context, enabled, strength) }
    controller.enabled = enabled
    controller.strength = strength
    return controller
}

/**
 * Emits crisp, throttled scroll-tick haptic feedback as the user scrolls past items in feeds.
 */
@Composable
fun BindScrollHaptics(listState: LazyListState) {
    val haptics = LocalOrbitHaptics.current
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                if (listState.isScrollInProgress) {
                    haptics.performScrollTick()
                }
            }
    }
}
