package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ActivityFilter
import com.example.data.model.ActivityItem
import com.example.data.model.formatRelativeTime
import com.example.ui.components.CenteredThreadsContainer
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.parseOrbitRichText
import com.example.ui.haptics.LocalOrbitHaptics
import com.example.ui.theme.OrbitHeartRed
import com.example.ui.theme.OrbitRepostGreen

@Composable
fun ActivityScreen(
    activities: List<ActivityItem>,
    onMarkRead: () -> Unit = {},
    onActorClick: (String) -> Unit,
    onPostClick: (String) -> Unit,
    onOpenSpace: (String) -> Unit = {},
    onAcceptSpaceInvite: (String) -> Unit = {},
    onOpenAppUpdates: () -> Unit = {}
) {
    val haptics = LocalOrbitHaptics.current
    var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }

    // Immediately mark activities read whenever user opens Activity (fixes stuck bell dot!)
    LaunchedEffect(activities.size) {
        onMarkRead()
    }

    val filtered = remember(activities, selectedFilter) {
        activities.filter { selectedFilter.matches(it.type) }
    }

    CenteredThreadsContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.tab_activity),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ActivityFilter.entries) { filter ->
                    val selected = filter == selectedFilter
                    FilterChip(
                        selected = selected,
                        onClick = {
                            haptics.performTap()
                            selectedFilter = filter
                        },
                        label = { Text(filter.label) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.testTag("activity_filter_${filter.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No activity yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Mentions, likes, replies, reposts, Space invites, join requests, and app updates will appear here in real time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filtered,
                        key = { it.id }
                    ) { item ->
                        ActivityRow(
                            item = item,
                            onClick = {
                                haptics.performTap()
                                when {
                                    item.type == "app_update" -> onOpenAppUpdates()
                                    item.spaceId.isNotBlank() -> onOpenSpace(item.spaceId)
                                    item.postId.isNotBlank() -> onPostClick(item.postId)
                                    else -> onActorClick(item.actorId)
                                }
                            },
                            onAvatarClick = { onActorClick(item.actorId) },
                            onAcceptSpaceInvite = if (item.type == "space_invite" && item.spaceId.isNotBlank()) {
                                {
                                    haptics.performMedium()
                                    onAcceptSpaceInvite(item.spaceId)
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    item: ActivityItem,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onAcceptSpaceInvite: (() -> Unit)? = null
) {
    val (badgeIcon, badgeColor, actionLabel) = when (item.type) {
        "like" -> Triple(Icons.Filled.Favorite, OrbitHeartRed, "liked your thread")
        "reply" -> Triple(Icons.Outlined.ChatBubble, MaterialTheme.colorScheme.primary, "replied to you")
        "repost" -> Triple(Icons.Filled.Repeat, OrbitRepostGreen, "reposted your thread")
        "quote" -> Triple(Icons.Outlined.FormatQuote, MaterialTheme.colorScheme.primary, "quoted your thread")
        "mention" -> Triple(
            Icons.Outlined.AlternateEmail,
            MaterialTheme.colorScheme.primary,
            if (item.spaceId.isNotBlank() && item.postId.isBlank()) {
                "mentioned you in a Space"
            } else {
                "mentioned you"
            }
        )
        "space_invite" -> Triple(Icons.Filled.Groups, MaterialTheme.colorScheme.primary, "invited you to join a Space")
        "space_join_request" -> Triple(Icons.Filled.Groups, MaterialTheme.colorScheme.tertiary, "requested to join your Space")
        "space_join_approved" -> Triple(Icons.Filled.Groups, OrbitRepostGreen, "approved your Space request")
        "space_member_joined" -> Triple(Icons.Filled.Groups, OrbitRepostGreen, "joined your Space")
        "app_update" -> Triple(Icons.Outlined.SystemUpdate, MaterialTheme.colorScheme.primary, "Orbit app update available")
        else -> Triple(Icons.Filled.PersonAdd, MaterialTheme.colorScheme.tertiary, "followed you")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("activity_item_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(modifier = Modifier.size(42.dp)) {
                ThreadAvatar(
                    avatarUrl = item.actorAvatarUrl,
                    displayName = item.actorUsername,
                    size = 40.dp,
                    onClick = onAvatarClick
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = item.type,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.actorUsername,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatRelativeTime(item.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.snippet.isNotBlank() && item.type != "follow") {
                    Spacer(Modifier.height(3.dp))
                    val accentColor = MaterialTheme.colorScheme.primary
                    val formattedSnippet = remember(item.snippet, accentColor) {
                        parseOrbitRichText(item.snippet, linkColor = accentColor)
                    }
                    Text(
                        text = formattedSnippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onAcceptSpaceInvite != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onAcceptSpaceInvite,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("activity_accept_invite_${item.id}")
                    ) {
                        Text("Confirm & join Space", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 70.dp)
        )
    }
}
