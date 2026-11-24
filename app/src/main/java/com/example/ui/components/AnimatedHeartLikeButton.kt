package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.OrbitHeartRed
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private data class FlyingHeartParticle(
    val xSpreadFactor: Float,
    val heightFactor: Float,
    val waveFreq: Float,
    val wavePhase: Float,
    val sizeFactor: Float,
    val delayFraction: Float,
    val tiltDegrees: Float,
    val color: Color
)

private val HEART_BURST_PARTICLES = listOf(
    FlyingHeartParticle(-0.95f, 1.05f, 1.6f, 0.0f, 0.56f, 0.00f, -18f, Color(0xFFFF2E5B)),
    FlyingHeartParticle(-0.55f, 1.30f, 2.0f, 0.9f, 0.72f, 0.04f, -10f, Color(0xFFFF5277)),
    FlyingHeartParticle(-0.18f, 1.48f, 1.4f, 1.8f, 0.64f, 0.02f, -4f, Color(0xFFFF2E5B)),
    FlyingHeartParticle(0.16f, 1.42f, 1.8f, 2.5f, 0.76f, 0.06f, 8f, Color(0xFFFF6B8B)),
    FlyingHeartParticle(0.54f, 1.26f, 2.1f, 0.4f, 0.62f, 0.03f, 14f, Color(0xFFFF2E5B)),
    FlyingHeartParticle(0.92f, 1.02f, 1.5f, 1.2f, 0.52f, 0.08f, 22f, Color(0xFFFF4772)),
    FlyingHeartParticle(-0.36f, 1.62f, 1.7f, 2.1f, 0.48f, 0.11f, -14f, Color(0xFFFF7A98)),
    FlyingHeartParticle(0.38f, 1.58f, 1.9f, 3.0f, 0.50f, 0.09f, 15f, Color(0xFFFF3864))
)

/**
 * Interactive Like chip for feed posts, replies, and Space discussions.
 * - On Like: launches a burst of smaller hearts that fly upward and fade away.
 * - On Unlike: splits the heart in half along a center fracture and drains all the red color
 *   downward out of both halves until it is completely empty.
 */
@Composable
fun AnimatedHeartActionChip(
    count: Int,
    isLiked: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    defaultTint: Color = MaterialTheme.colorScheme.onSurface
) {
    val scope = rememberCoroutineScope()
    val likeBurstProgress = remember { Animatable(0f) }
    val unlikeDrainProgress = remember { Animatable(0f) }
    val popScale = remember { Animatable(1f) }

    val triggerAnimationAndClick = {
        val willBeLiked = !isLiked
        onClick()
        scope.launch {
            if (willBeLiked) {
                unlikeDrainProgress.snapTo(0f)
                coroutineScope {
                    launch {
                        popScale.snapTo(0.84f)
                        popScale.animateTo(
                            targetValue = 1.28f,
                            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                        )
                        popScale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        likeBurstProgress.snapTo(0.01f)
                        likeBurstProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 780, easing = FastOutSlowInEasing)
                        )
                        likeBurstProgress.snapTo(0f)
                    }
                }
            } else {
                likeBurstProgress.snapTo(0f)
                popScale.snapTo(1f)
                unlikeDrainProgress.snapTo(0.01f)
                unlikeDrainProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 760, easing = FastOutSlowInEasing)
                )
                unlikeDrainProgress.snapTo(0f)
            }
        }
    }

    val activeTint = if (isLiked) OrbitHeartRed else defaultTint

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = { triggerAnimationAndClick() })
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics {
                contentDescription = if (count > 0) "Like ($count)" else "Like"
            }
            .testTag(testTag)
    ) {
        AnimatedHeartGraphic(
            isLiked = isLiked,
            iconSize = 20.dp,
            emptyOutlineColor = defaultTint,
            likeBurstProgress = likeBurstProgress.value,
            unlikeDrainProgress = unlikeDrainProgress.value,
            popScale = popScale.value
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = formatHeartCount(count),
                style = MaterialTheme.typography.labelMedium,
                color = activeTint
            )
        }
    }
}

/**
 * Icon-only animated heart button (used in the Frames action bar) with the same flying smaller
 * hearts burst on Like and split-heart red drain animation on Unlike.
 */
@Composable
fun AnimatedHeartIconButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 25.dp,
    defaultTint: Color = MaterialTheme.colorScheme.onSurface
) {
    val scope = rememberCoroutineScope()
    val likeBurstProgress = remember { Animatable(0f) }
    val unlikeDrainProgress = remember { Animatable(0f) }
    val popScale = remember { Animatable(1f) }

    val triggerAnimationAndClick = {
        val willBeLiked = !isLiked
        onClick()
        scope.launch {
            if (willBeLiked) {
                unlikeDrainProgress.snapTo(0f)
                coroutineScope {
                    launch {
                        popScale.snapTo(0.84f)
                        popScale.animateTo(
                            targetValue = 1.28f,
                            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                        )
                        popScale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        likeBurstProgress.snapTo(0.01f)
                        likeBurstProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 780, easing = FastOutSlowInEasing)
                        )
                        likeBurstProgress.snapTo(0f)
                    }
                }
            } else {
                likeBurstProgress.snapTo(0f)
                popScale.snapTo(1f)
                unlikeDrainProgress.snapTo(0.01f)
                unlikeDrainProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 760, easing = FastOutSlowInEasing)
                )
                unlikeDrainProgress.snapTo(0f)
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = { triggerAnimationAndClick() })
            .semantics {
                contentDescription = if (isLiked) "Unlike" else "Like"
            }
            .testTag(testTag)
    ) {
        AnimatedHeartGraphic(
            isLiked = isLiked,
            iconSize = iconSize,
            emptyOutlineColor = defaultTint,
            likeBurstProgress = likeBurstProgress.value,
            unlikeDrainProgress = unlikeDrainProgress.value,
            popScale = popScale.value
        )
    }
}

/**
 * Vertical rail heart button used in the full-screen Frames video feed with the same flying
 * hearts burst on Like and split-heart red drain animation on Unlike.
 */
@Composable
fun AnimatedFramesHeartButton(
    count: Int,
    isLiked: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val likeBurstProgress = remember { Animatable(0f) }
    val unlikeDrainProgress = remember { Animatable(0f) }
    val popScale = remember { Animatable(1f) }

    val triggerAnimationAndClick = {
        val willBeLiked = !isLiked
        onClick()
        scope.launch {
            if (willBeLiked) {
                unlikeDrainProgress.snapTo(0f)
                coroutineScope {
                    launch {
                        popScale.snapTo(0.84f)
                        popScale.animateTo(
                            targetValue = 1.28f,
                            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                        )
                        popScale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        likeBurstProgress.snapTo(0.01f)
                        likeBurstProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 780, easing = FastOutSlowInEasing)
                        )
                        likeBurstProgress.snapTo(0f)
                    }
                }
            } else {
                likeBurstProgress.snapTo(0f)
                popScale.snapTo(1f)
                unlikeDrainProgress.snapTo(0.01f)
                unlikeDrainProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 760, easing = FastOutSlowInEasing)
                )
                unlikeDrainProgress.snapTo(0f)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .sizeIn(minWidth = 52.dp, minHeight = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = { triggerAnimationAndClick() })
            .padding(vertical = 4.dp, horizontal = 6.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.38f), CircleShape)
        ) {
            AnimatedHeartGraphic(
                isLiked = isLiked,
                iconSize = 26.dp,
                emptyOutlineColor = Color.White,
                likeBurstProgress = likeBurstProgress.value,
                unlikeDrainProgress = unlikeDrainProgress.value,
                popScale = popScale.value
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (count > 0) count.toString() else "Like",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AnimatedHeartGraphic(
    isLiked: Boolean,
    iconSize: Dp,
    emptyOutlineColor: Color,
    likeBurstProgress: Float,
    unlikeDrainProgress: Float,
    popScale: Float,
    modifier: Modifier = Modifier
) {
    val isBreakingAndDraining = unlikeDrainProgress in 0.005f..0.995f
    val isBurstingHearts = likeBurstProgress in 0.005f..0.995f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(iconSize)
    ) {
        if (!isBreakingAndDraining) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = if (isLiked) OrbitHeartRed else emptyOutlineColor,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = popScale
                        scaleY = popScale
                    }
            )
        }

        if (isBurstingHearts || isBreakingAndDraining) {
            Canvas(modifier = Modifier.size(iconSize)) {
                if (isBurstingHearts) {
                    drawFlyingHeartsBurst(
                        progress = likeBurstProgress,
                        baseSize = size.minDimension
                    )
                }
                if (isBreakingAndDraining) {
                    drawBrokenDrainingHeart(
                        progress = unlikeDrainProgress,
                        baseSize = size.minDimension,
                        emptyOutlineColor = emptyOutlineColor,
                        heartRedColor = OrbitHeartRed
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawFlyingHeartsBurst(
    progress: Float,
    baseSize: Float
) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val maxTravelY = baseSize * 2.15f
    val maxSpreadX = baseSize * 1.15f

    HEART_BURST_PARTICLES.forEach { p ->
        val localT = ((progress - p.delayFraction) / (1f - p.delayFraction)).coerceIn(0f, 1f)
        if (localT > 0f && localT < 1f) {
            // Ease upward and fade out gracefully in the upper half of flight
            val rise = 1f - (1f - localT) * (1f - localT)
            val sway = sin((localT * p.waveFreq * PI).toFloat() + p.wavePhase) * (baseSize * 0.16f)
            val px = cx + (p.xSpreadFactor * maxSpreadX * rise) + sway
            val py = cy - (p.heightFactor * maxTravelY * rise)

            val scaleCurve = when {
                localT < 0.22f -> (localT / 0.22f)
                else -> 1f - ((localT - 0.22f) / 0.78f) * 0.45f
            }.coerceIn(0.15f, 1f)

            val alphaCurve = when {
                localT < 0.15f -> (localT / 0.15f)
                localT < 0.55f -> 1f
                else -> (1f - ((localT - 0.55f) / 0.45f))
            }.coerceIn(0f, 1f)

            val miniHeartSize = baseSize * p.sizeFactor * scaleCurve
            withTransform({
                translate(left = px, top = py)
                rotate(degrees = p.tiltDegrees * (0.5f + 0.8f * localT), pivot = Offset.Zero)
            }) {
                val heartPath = buildNormalizedHeartPath(
                    centerX = 0f,
                    centerY = 0f,
                    heartSize = miniHeartSize
                )
                drawPath(
                    path = heartPath,
                    color = p.color.copy(alpha = alphaCurve),
                    style = Fill
                )
            }
        }
    }
}

private fun DrawScope.drawBrokenDrainingHeart(
    progress: Float,
    baseSize: Float,
    emptyOutlineColor: Color,
    heartRedColor: Color
) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f

    // Phase 1 (0..0.28): Heart cracks open & halves tilt apart
    // Phase 2 (0.10..0.90): Red liquid drains from top to bottom out of both halves
    // Phase 3 (0.82..1.0): Empty halves settle back into the empty outline heart
    val splitFactor = when {
        progress < 0.26f -> (progress / 0.26f)
        progress < 0.78f -> 1f
        else -> (1f - ((progress - 0.78f) / 0.22f)).coerceIn(0f, 1f)
    }

    val drainFraction = ((progress - 0.08f) / 0.78f).coerceIn(0f, 1f)

    val heartPath = buildNormalizedHeartPath(
        centerX = cx,
        centerY = cy,
        heartSize = baseSize * 0.92f
    )

    // Jagged lightning crack dividing the left and right halves
    val topY = cy - baseSize * 0.55f
    val botY = cy + baseSize * 0.58f
    val h = botY - topY

    val leftHalfClip = Path().apply {
        moveTo(-baseSize, topY)
        lineTo(cx + baseSize * 0.02f, topY)
        lineTo(cx - baseSize * 0.07f, topY + h * 0.34f)
        lineTo(cx + baseSize * 0.06f, topY + h * 0.56f)
        lineTo(cx - baseSize * 0.05f, topY + h * 0.76f)
        lineTo(cx, botY)
        lineTo(-baseSize, botY)
        close()
    }

    val rightHalfClip = Path().apply {
        moveTo(cx + baseSize * 0.02f, topY)
        lineTo(size.width + baseSize, topY)
        lineTo(size.width + baseSize, botY)
        lineTo(cx, botY)
        lineTo(cx - baseSize * 0.05f, topY + h * 0.76f)
        lineTo(cx + baseSize * 0.06f, topY + h * 0.56f)
        lineTo(cx - baseSize * 0.07f, topY + h * 0.34f)
        close()
    }

    val shiftX = baseSize * 0.14f * splitFactor
    val tiltDeg = 15f * splitFactor
    val pivotPoint = Offset(cx, cy + baseSize * 0.36f)
    val strokeW = (baseSize * 0.095f).coerceAtLeast(2f)

    // Liquid top boundary moves downward from top of heart to bottom of heart
    val heartTop = cy - baseSize * 0.44f
    val heartBottom = cy + baseSize * 0.46f
    val liquidTopY = heartTop + (heartBottom - heartTop) * drainFraction

    // Draw Left Broken Half
    withTransform({
        translate(left = -shiftX, top = baseSize * 0.02f * splitFactor)
        rotate(degrees = -tiltDeg, pivot = pivotPoint)
    }) {
        clipPath(leftHalfClip, clipOp = ClipOp.Intersect) {
            // Empty outline shell
            drawPath(
                path = heartPath,
                color = emptyOutlineColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Draining red fill inside the left half
            if (drainFraction < 0.99f) {
                clipRect(
                    left = -baseSize,
                    top = liquidTopY,
                    right = size.width + baseSize,
                    bottom = heartBottom + baseSize * 0.2f
                ) {
                    drawPath(
                        path = heartPath,
                        color = heartRedColor,
                        style = Fill
                    )
                }
            }
        }
    }

    // Draw Right Broken Half
    withTransform({
        translate(left = shiftX, top = baseSize * 0.02f * splitFactor)
        rotate(degrees = tiltDeg, pivot = pivotPoint)
    }) {
        clipPath(rightHalfClip, clipOp = ClipOp.Intersect) {
            // Empty outline shell
            drawPath(
                path = heartPath,
                color = emptyOutlineColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Draining red fill inside the right half
            if (drainFraction < 0.99f) {
                clipRect(
                    left = -baseSize,
                    top = liquidTopY,
                    right = size.width + baseSize,
                    bottom = heartBottom + baseSize * 0.2f
                ) {
                    drawPath(
                        path = heartPath,
                        color = heartRedColor,
                        style = Fill
                    )
                }
            }
        }
    }

    // Small red droplets draining out the bottom crack while the red drains away
    if (drainFraction in 0.08f..0.92f) {
        val drop1T = ((drainFraction - 0.08f) / 0.55f).coerceIn(0f, 1f)
        if (drop1T in 0.01f..0.99f) {
            drawCircle(
                color = heartRedColor.copy(alpha = (1f - drop1T).coerceIn(0f, 0.9f)),
                radius = baseSize * 0.065f * (1f - drop1T * 0.35f),
                center = Offset(
                    x = cx - baseSize * 0.07f,
                    y = heartBottom + baseSize * 0.42f * drop1T
                )
            )
        }
        val drop2T = ((drainFraction - 0.28f) / 0.58f).coerceIn(0f, 1f)
        if (drop2T in 0.01f..0.99f) {
            drawCircle(
                color = heartRedColor.copy(alpha = (1f - drop2T).coerceIn(0f, 0.85f)),
                radius = baseSize * 0.055f * (1f - drop2T * 0.35f),
                center = Offset(
                    x = cx + baseSize * 0.08f,
                    y = heartBottom + baseSize * 0.46f * drop2T
                )
            )
        }
    }
}

private fun buildNormalizedHeartPath(
    centerX: Float,
    centerY: Float,
    heartSize: Float
): Path {
    val w = heartSize
    val h = heartSize
    val left = centerX - w * 0.5f
    val top = centerY - h * 0.48f
    return Path().apply {
        moveTo(centerX, top + h * 0.88f)
        cubicTo(
            left + w * 0.12f, top + h * 0.62f,
            left - w * 0.04f, top + h * 0.26f,
            left + w * 0.23f, top + h * 0.12f
        )
        cubicTo(
            left + w * 0.38f, top + h * 0.04f,
            centerX, top + h * 0.18f,
            centerX, top + h * 0.28f
        )
        cubicTo(
            centerX, top + h * 0.18f,
            left + w * 0.62f, top + h * 0.04f,
            left + w * 0.77f, top + h * 0.12f
        )
        cubicTo(
            left + w * 1.04f, top + h * 0.26f,
            left + w * 0.88f, top + h * 0.62f,
            centerX, top + h * 0.88f
        )
        close()
    }
}

private fun formatHeartCount(count: Int): String = when {
    count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000f)
    count >= 10_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000f)
    count >= 1_000 -> String.format(java.util.Locale.US, "%,d", count)
    else -> count.toString()
}
