package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Conversation

/**
 * Renders either a standard single-user avatar for 1-on-1 DMs or a clustered participant-based
 * [GroupConversationAvatar] for group DM threads.
 */
@Composable
fun ConversationAvatar(
    conversation: Conversation,
    currentUserId: String,
    size: Dp = 48.dp,
    borderColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier
) {
    if (conversation.isGroupChat) {
        val participants = conversation.otherParticipantAvatarPairs(currentUserId)
        GroupConversationAvatar(
            participants = participants,
            totalOtherCount = participants.size,
            size = size,
            borderColor = borderColor,
            modifier = modifier
        )
    } else {
        ThreadAvatar(
            avatarUrl = conversation.peerAvatarUrl(currentUserId),
            displayName = conversation.peerName(currentUserId),
            size = size,
            modifier = modifier
        )
    }
}

/**
 * Clustered participant-based Group Avatar for multi-user DM threads.
 * - 2 other participants: diagonal overlapping dual avatars with crisp surface ring borders.
 * - 3 other participants: balanced triangular 3-avatar cluster.
 * - 4+ other participants: 2 participant avatars + a Material 3 primary-container "+N" badge.
 */
@Composable
fun GroupConversationAvatar(
    participants: List<Pair<String, String>>,
    totalOtherCount: Int = participants.size,
    size: Dp = 48.dp,
    borderColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier
) {
    val safeList = participants.ifEmpty { listOf("" to "Group") }
    val ringWidth = if (size >= 44.dp) 1.8.dp else 1.2.dp

    if (safeList.size <= 1) {
        val first = safeList.first()
        ThreadAvatar(
            avatarUrl = first.first,
            displayName = first.second,
            size = size,
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Group conversation avatar" }
    ) {
        when {
            safeList.size == 2 && totalOtherCount <= 2 -> {
                val backSize = size * 0.64f
                val frontSize = size * 0.68f
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(backSize)
                        .clip(CircleShape)
                        .border(ringWidth, borderColor, CircleShape)
                ) {
                    ThreadAvatar(
                        avatarUrl = safeList[1].first,
                        displayName = safeList[1].second,
                        size = backSize
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(frontSize)
                        .clip(CircleShape)
                        .background(borderColor, CircleShape)
                        .border(ringWidth, borderColor, CircleShape)
                ) {
                    ThreadAvatar(
                        avatarUrl = safeList[0].first,
                        displayName = safeList[0].second,
                        size = frontSize
                    )
                }
            }

            totalOtherCount == 3 && safeList.size >= 3 -> {
                val miniSize = size * 0.56f
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(miniSize)
                        .clip(CircleShape)
                        .border(ringWidth, borderColor, CircleShape)
                ) {
                    ThreadAvatar(
                        avatarUrl = safeList[0].first,
                        displayName = safeList[0].second,
                        size = miniSize
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(miniSize)
                        .clip(CircleShape)
                        .background(borderColor, CircleShape)
                        .border(ringWidth, borderColor, CircleShape)
                ) {
                    ThreadAvatar(
                        avatarUrl = safeList[1].first,
                        displayName = safeList[1].second,
                        size = miniSize
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(miniSize)
                        .clip(CircleShape)
                        .background(borderColor, CircleShape)
                        .border(ringWidth, borderColor, CircleShape)
                ) {
                    ThreadAvatar(
                        avatarUrl = safeList[2].first,
                        displayName = safeList[2].second,
                        size = miniSize
                    )
                }
            }

            else -> {
                val miniSize = size * 0.56f
                val overflowCount = (totalOtherCount - 2).coerceAtLeast(1)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(miniSize)
                        .clip(CircleShape)
                        .border(ringWidth, borderColor, CircleShape)
                ) {
                    ThreadAvatar(
                        avatarUrl = safeList[0].first,
                        displayName = safeList[0].second,
                        size = miniSize
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(miniSize)
                        .clip(CircleShape)
                        .background(borderColor, CircleShape)
                        .border(ringWidth, borderColor, CircleShape)
                ) {
                    ThreadAvatar(
                        avatarUrl = safeList[1].first,
                        displayName = safeList[1].second,
                        size = miniSize
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(miniSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .border(ringWidth, borderColor, CircleShape)
                ) {
                    Text(
                        text = "+$overflowCount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = (size.value * 0.21f).coerceIn(8.5f, 13f).sp,
                            lineHeight = (size.value * 0.23f).coerceIn(9.5f, 14f).sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
