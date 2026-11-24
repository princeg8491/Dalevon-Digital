package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ThreadPost
import com.example.ui.haptics.LocalOrbitHaptics
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Root-provided signed-in user ID so ThreadPollCard can highlight the current user's vote anywhere.
 */
val LocalOrbitCurrentUserId = staticCompositionLocalOf { "" }

/**
 * Root-provided handler for voting on interactive Polls anywhere a post is rendered.
 */
val LocalPollVoteHandler = staticCompositionLocalOf<(ThreadPost, Int) -> Unit> { { _, _ -> } }

/**
 * Optimistic in-flight poll votes keyed by postId -> optionIndex so voting responds in 0ms.
 */
val LocalOptimisticPollVotes = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

/**
 * Interactive Material 3 Poll Card rendered inside Thread posts.
 * Supports one-tap voting, instant vote switching while open, animated percentage result bars,
 * winning-option highlight, and expiration countdown.
 */
@Composable
fun ThreadPollCard(
    post: ThreadPost,
    currentUserId: String = LocalOrbitCurrentUserId.current,
    onVote: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!post.hasPoll) return

    val haptics = LocalOrbitHaptics.current
    val rootVoteHandler = LocalPollVoteHandler.current
    val optimisticVotes = LocalOptimisticPollVotes.current

    val effectiveVotes = if (post.id in optimisticVotes && currentUserId.isNotBlank()) {
        post.pollVotes + (currentUserId to optimisticVotes.getValue(post.id).toLong())
    } else {
        post.pollVotes
    }

    val myVoteIndex = effectiveVotes[currentUserId]?.toInt()?.takeIf { it in post.pollOptions.indices }
    val nowMillis = System.currentTimeMillis()
    val pollEnded = post.isPollEnded(nowMillis)
    val showResults = myVoteIndex != null || pollEnded
    val totalVotes = effectiveVotes.size

    val optionCounts = post.pollOptions.indices.map { idx ->
        effectiveVotes.values.count { it.toInt() == idx }
    }
    val maxVotes = optionCounts.maxOrNull() ?: 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("poll_card_${post.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            post.pollOptions.forEachIndexed { index, label ->
                val votes = optionCounts[index]
                val fraction = if (totalVotes > 0) votes.toFloat() / totalVotes.toFloat() else 0f
                val animatedFraction by animateFloatAsState(
                    targetValue = if (showResults) fraction else 0f,
                    animationSpec = tween(durationMillis = 360),
                    label = "poll_bar_$index"
                )
                val isSelected = myVoteIndex == index
                val isLeading = showResults && maxVotes > 0 && votes == maxVotes
                val percentage = (fraction * 100f).roundToInt()

                val borderColor = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isLeading -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                val barColor = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    isLeading -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (isSelected) 1.5.dp else 0.85.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !pollEnded) {
                            haptics.performMedium()
                            if (onVote != null) {
                                onVote(index)
                            } else {
                                rootVoteHandler(post, index)
                            }
                        }
                        .testTag("poll_option_${post.id}_$index")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        if (showResults && animatedFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedFraction.coerceIn(0f, 1f))
                                        .background(barColor)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Your vote",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            } else if (!showResults) {
                                Icon(
                                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }

                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected || isLeading) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (showResults) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "$percentage%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected || isLeading) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                val voteLabel = if (totalVotes == 1) "1 vote" else "$totalVotes votes"
                val statusLabel = formatPollTimeRemaining(post.pollEndsAtMillis, nowMillis)
                Text(
                    text = "$voteLabel · $statusLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("poll_footer_${post.id}")
                )
                if (!pollEnded && myVoteIndex != null) {
                    Text(
                        text = "Tap to change vote",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun formatPollTimeRemaining(endsAtMillis: Long, nowMillis: Long): String {
    if (endsAtMillis <= 0L) return "Open poll"
    val diff = endsAtMillis - nowMillis
    if (diff <= 0L) return "Final results"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        days >= 1 -> "${days}d left"
        hours >= 1 -> "${hours}h left"
        else -> "${minutes.coerceAtLeast(1)}m left"
    }
}
