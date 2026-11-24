package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PostAttachment
import com.example.data.model.ThreadPost
import com.example.data.model.UserProfile
import com.example.data.model.formatCompactCount
import com.example.ui.components.CenteredThreadsContainer
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.ThreadPostRow
import com.example.ui.components.UserSearchDropdownField

@Composable
fun SearchScreen(
    profiles: List<UserProfile>,
    posts: List<ThreadPost>,
    currentUserId: String,
    followingIds: Set<String>,
    likedPostIds: Set<String>,
    repostedPostIds: Set<String>,
    savedPostIds: Set<String> = emptySet(),
    onProfileClick: (String) -> Unit,
    onFollowToggle: (String) -> Unit,
    onPostClick: (ThreadPost) -> Unit,
    onQuotedPostClick: (String) -> Unit = {},
    onLikeClick: (ThreadPost) -> Unit,
    onReplyClick: (ThreadPost) -> Unit,
    onRepostClick: (ThreadPost) -> Unit,
    onSaveClick: (ThreadPost) -> Unit = {},
    onEditClick: (ThreadPost) -> Unit = {},
    onDeleteClick: (ThreadPost) -> Unit,
    onImageClick: (String) -> Unit,
    onDownloadPhoto: (String) -> Unit = {},
    onDownloadAttachment: (PostAttachment) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val trimmedQuery = query.trim().lowercase()

    val matchingPosts = remember(posts, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            posts.filter { post ->
                post.isTopLevel && (
                    post.body.lowercase().contains(trimmedQuery) ||
                        post.topic.lowercase().contains(trimmedQuery) ||
                        post.authorUsername.lowercase().contains(trimmedQuery)
                    )
            }
        }
    }

    CenteredThreadsContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.tab_search),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // No usernames shown until typing; live dropdown suggests matching Orbit users
            UserSearchDropdownField(
                query = query,
                onQueryChange = { query = it },
                allUsers = profiles,
                excludedUserIds = setOf(currentUserId),
                placeholder = "Search people, topics, or threads",
                actionLabel = "View",
                testTag = "search_query_input",
                clearOnSelect = false,
                onSelectUser = { user -> onProfileClick(user.resolvedId) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (trimmedQuery.isEmpty()) {
                    item(key = "search_prompt_empty") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 56.dp)
                        ) {
                            Text(
                                text = "Start typing a name, @username, or keyword to discover people and threads on Orbit.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (matchingPosts.isNotEmpty()) {
                    item(key = "threads_section_header") {
                        Text(
                            text = "Threads",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    items(
                        items = matchingPosts,
                        key = { "search_post_${it.id}" }
                    ) { post ->
                        ThreadPostRow(
                            post = post,
                            isLiked = post.id in likedPostIds,
                            isReposted = post.id in repostedPostIds,
                            isSaved = post.id in savedPostIds,
                            isOwnPost = post.authorId == currentUserId,
                            isFollowingAuthor = post.authorId in followingIds,
                            onPostClick = { onPostClick(post) },
                            onQuotedPostClick = onQuotedPostClick,
                            onAuthorClick = { onProfileClick(post.authorId) },
                            onLikeClick = { onLikeClick(post) },
                            onReplyClick = { onReplyClick(post) },
                            onRepostClick = { onRepostClick(post) },
                            onSaveClick = { onSaveClick(post) },
                            onFollowAuthorClick = { onFollowToggle(post.authorId) },
                            onEditClick = { onEditClick(post) },
                            onDeleteClick = { onDeleteClick(post) },
                            onImageClick = onImageClick,
                            onDownloadPhoto = onDownloadPhoto,
                            onDownloadAttachment = onDownloadAttachment
                        )
                    }
                } else {
                    item(key = "empty_search_threads") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp)
                        ) {
                            Text(
                                text = "No matching threads for \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedAccountRow(
    profile: UserProfile,
    isFollowing: Boolean,
    onProfileClick: () -> Unit,
    onFollowClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onProfileClick)
            .testTag("search_profile_row_${profile.resolvedId}")
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            ThreadAvatar(
                avatarUrl = profile.avatarUrl,
                displayName = profile.displayName.ifBlank { profile.username },
                size = 42.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.username.ifBlank { profile.displayName },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.bio.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                val label = if (profile.followerCount == 1L) "follower" else "followers"
                Text(
                    text = "${formatCompactCount(profile.followerCount)} $label",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            if (isFollowing) {
                OutlinedButton(
                    onClick = onFollowClick,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("search_follow_btn_${profile.resolvedId}")
                ) {
                    Text(
                        text = stringResource(R.string.action_following),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Button(
                    onClick = onFollowClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("search_follow_btn_${profile.resolvedId}")
                ) {
                    Text(
                        text = stringResource(R.string.action_follow),
                        style = MaterialTheme.typography.labelMedium
                    )
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
