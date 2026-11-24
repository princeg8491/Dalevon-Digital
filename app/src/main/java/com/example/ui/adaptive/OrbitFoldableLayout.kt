package com.example.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

enum class FoldablePosture {
    /** Standard flat display (cover screen, tablet, or fully unfolded foldable). */
    FLAT,
    /** Half-opened with a vertical hinge (book / dual-page posture). */
    BOOK,
    /** Half-opened with a horizontal hinge (tabletop / flex posture). */
    TABLETOP
}

enum class FoldScreenProfile {
    /** Tall phone / folded Pixel Pro Fold outer cover screen (< 600dp wide). */
    COVER_COMPACT,
    /** Unfolded Pixel 11 Pro Fold near-square inner display (~1:1 aspect ratio, 600dp–960dp). */
    PIXEL_FOLD_SQUARE,
    /** Wide landscape foldable or tablet (>= 900dp wide with wider aspect ratio). */
    EXPANDED_WIDE
}

@Immutable
data class FoldableLayoutInfo(
    val windowWidthDp: Dp = 412.dp,
    val windowHeightDp: Dp = 915.dp,
    val aspectRatio: Float = 0.45f,
    val screenProfile: FoldScreenProfile = FoldScreenProfile.COVER_COMPACT,
    val posture: FoldablePosture = FoldablePosture.FLAT,
    val isHingeSeparating: Boolean = false,
    val hingeWidthDp: Dp = 0.dp,
    val hingeHeightDp: Dp = 0.dp,
    val hingeLeftDp: Dp = 0.dp,
    val hingeTopDp: Dp = 0.dp
) {
    /** Pixel 11 Pro Fold inner unfolded display has a distinctive near-square (~0.82..1.25) aspect ratio. */
    val isPixelFoldInnerSquare: Boolean
        get() = screenProfile == FoldScreenProfile.PIXEL_FOLD_SQUARE

    /** Use left-edge NavigationRail when unfolded (>= 600dp) to maximize vertical reading height on near-square displays. */
    val useNavigationRail: Boolean
        get() = windowWidthDp >= 600.dp

    /** Show canonical dual-pane (List-Detail / Supporting Pane) layouts on unfolded foldables and tablets. */
    val supportsTwoPane: Boolean
        get() = posture == FoldablePosture.BOOK || windowWidthDp >= 680.dp

    /** True when the device is resting in hands-free Tabletop / Flex posture with a horizontal crease. */
    val isTabletop: Boolean
        get() = posture == FoldablePosture.TABLETOP

    /** True when the device is half-open vertically like an open book. */
    val isBookPosture: Boolean
        get() = posture == FoldablePosture.BOOK

    /** Optimal horizontal max width for single-pane readable columns when not in two-pane mode. */
    val singlePaneMaxWidth: Dp
        get() = when (screenProfile) {
            FoldScreenProfile.COVER_COMPACT -> Dp.Unspecified
            FoldScreenProfile.PIXEL_FOLD_SQUARE -> 680.dp
            FoldScreenProfile.EXPANDED_WIDE -> 720.dp
        }
}

val LocalFoldableLayoutInfo = compositionLocalOf { FoldableLayoutInfo() }

/**
 * Observes live Jetpack WindowManager [WindowLayoutInfo] and computes fold-aware layout metrics
 * specifically tuned for the Pixel 11 Pro Fold cover screen (~20:9) and inner square display (~1:1).
 */
@Composable
fun rememberFoldableLayoutInfo(
    maxWidth: Dp,
    maxHeight: Dp
): FoldableLayoutInfo {
    val context = LocalContext.current
    val density = LocalDensity.current
    val activity = remember(context) { context.findActivity() }

    val layoutInfo by produceState<WindowLayoutInfo?>(initialValue = null, key1 = activity) {
        if (activity != null) {
            runCatching {
                WindowInfoTracker.getOrCreate(activity)
                    .windowLayoutInfo(activity)
                    .collect { info -> value = info }
            }
        }
    }

    val foldingFeature = remember(layoutInfo) {
        layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
    }

    return remember(maxWidth, maxHeight, foldingFeature, density) {
        val posture = when {
            foldingFeature == null -> FoldablePosture.FLAT
            foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL -> FoldablePosture.TABLETOP
            foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL -> FoldablePosture.BOOK
            else -> FoldablePosture.FLAT
        }

        val bounds = foldingFeature?.bounds
        val hingeWidthDp = if (bounds != null) with(density) { bounds.width().toDp() } else 0.dp
        val hingeHeightDp = if (bounds != null) with(density) { bounds.height().toDp() } else 0.dp
        val hingeLeftDp = if (bounds != null) with(density) { bounds.left.toDp() } else 0.dp
        val hingeTopDp = if (bounds != null) with(density) { bounds.top.toDp() } else 0.dp

        classifyFoldableLayout(
            windowWidthDp = maxWidth,
            windowHeightDp = maxHeight,
            posture = posture,
            isHingeSeparating = foldingFeature?.isSeparating == true,
            hingeWidthDp = hingeWidthDp,
            hingeHeightDp = hingeHeightDp,
            hingeLeftDp = hingeLeftDp,
            hingeTopDp = hingeTopDp
        )
    }
}

/**
 * Pure layout classifier tuned for the Pixel 11 Pro Fold cover screen (~20:9),
 * inner near-square display (~1:1, ~800-880dp), and expanded tablets.
 */
fun classifyFoldableLayout(
    windowWidthDp: Dp,
    windowHeightDp: Dp,
    posture: FoldablePosture = FoldablePosture.FLAT,
    isHingeSeparating: Boolean = false,
    hingeWidthDp: Dp = 0.dp,
    hingeHeightDp: Dp = 0.dp,
    hingeLeftDp: Dp = 0.dp,
    hingeTopDp: Dp = 0.dp
): FoldableLayoutInfo {
    val w = windowWidthDp.value.coerceAtLeast(1f)
    val h = windowHeightDp.value.coerceAtLeast(1f)
    val aspect = w / h

    // Pixel 11 Pro Fold inner screen is ~8" near-square (~800-880dp x ~800-880dp, ratio ~0.78..1.32)
    val screenProfile = when {
        windowWidthDp < 600.dp -> FoldScreenProfile.COVER_COMPACT
        aspect in 0.78f..1.32f && windowWidthDp < 1024.dp -> FoldScreenProfile.PIXEL_FOLD_SQUARE
        else -> FoldScreenProfile.EXPANDED_WIDE
    }

    return FoldableLayoutInfo(
        windowWidthDp = windowWidthDp,
        windowHeightDp = windowHeightDp,
        aspectRatio = aspect,
        screenProfile = screenProfile,
        posture = posture,
        isHingeSeparating = isHingeSeparating,
        hingeWidthDp = hingeWidthDp,
        hingeHeightDp = hingeHeightDp,
        hingeLeftDp = hingeLeftDp,
        hingeTopDp = hingeTopDp
    )
}

/**
 * Hinge-aware horizontal two-pane layout.
 * - In Book Posture (or when a vertical hinge separates the screen), aligns both panes strictly
 *   to the left and right of the physical fold so text/buttons never straddle the crease.
 * - On the Pixel 11 Pro Fold's flat ~1:1 inner display, uses balanced ergonomic weights with a
 *   clean Material 3 vertical divider.
 */
@Composable
fun FoldableTwoPaneLayout(
    info: FoldableLayoutInfo,
    leftWeight: Float = 0.54f,
    modifier: Modifier = Modifier,
    leftPane: @Composable () -> Unit,
    rightPane: @Composable () -> Unit
) {
    val useHingeSplit = info.isBookPosture || (info.isHingeSeparating && info.hingeWidthDp >= 0.dp && info.hingeLeftDp > 120.dp)
    val safeLeftWeight = if (useHingeSplit) 0.5f else leftWeight.coerceIn(0.35f, 0.65f)
    val gapWidth = if (useHingeSplit) info.hingeWidthDp.coerceAtLeast(16.dp) else 0.dp

    Row(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(safeLeftWeight)
                .fillMaxHeight()
        ) {
            leftPane()
        }

        if (gapWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .width(gapWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            )
        } else {
            VerticalDivider(
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxHeight()
            )
        }

        Box(
            modifier = Modifier
                .weight(1f - safeLeftWeight)
                .fillMaxHeight()
        ) {
            rightPane()
        }
    }
}

/**
 * Hinge-aware vertical split layout for Tabletop / Flex posture.
 * Places primary media/visual content strictly on the upright top half above the crease
 * and interactive controls/inputs on the flat lower half below the crease.
 */
@Composable
fun FoldableTabletopSplitLayout(
    info: FoldableLayoutInfo,
    modifier: Modifier = Modifier,
    topPane: @Composable () -> Unit,
    bottomPane: @Composable () -> Unit
) {
    val topRatio = if (info.hingeTopDp > 120.dp && info.windowHeightDp > 240.dp) {
        (info.hingeTopDp.value / info.windowHeightDp.value).coerceIn(0.38f, 0.62f)
    } else {
        0.5f
    }
    val hingeGap = info.hingeHeightDp.coerceAtLeast(14.dp)

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(topRatio)
                .fillMaxWidth()
        ) {
            topPane()
        }

        Spacer(modifier = Modifier.height(hingeGap))

        Box(
            modifier = Modifier
                .weight(1f - topRatio)
                .fillMaxWidth()
        ) {
            bottomPane()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
