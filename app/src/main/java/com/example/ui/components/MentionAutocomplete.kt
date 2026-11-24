package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ThreadPost
import com.example.data.model.UserProfile
import com.example.ui.haptics.LocalOrbitHaptics

/**
 * Provides the list of profiles the current user follows (excluding self and blocked users).
 */
val LocalFollowedProfiles = staticCompositionLocalOf<List<UserProfile>> { emptyList() }

/**
 * Provides all public mentionable Orbit profiles (excluding self and blocked users),
 * ordered with followed users first so replying to anyone lets you `@mention` them.
 */
val LocalMentionableProfiles = staticCompositionLocalOf<List<UserProfile>> { emptyList() }

/**
 * Builds a prioritized mention candidate list for replies:
 * 1. The author being replied to (even if not followed yet)
 * 2. Other participants in the thread
 * 3. People the current user follows
 * 4. Any remaining public Orbit members
 */
fun buildReplyMentionCandidates(
    replyTarget: ThreadPost?,
    threadPosts: List<ThreadPost> = emptyList(),
    followedProfiles: List<UserProfile>,
    allMentionableProfiles: List<UserProfile>,
    currentUserId: String = ""
): List<UserProfile> {
    val byId = LinkedHashMap<String, UserProfile>()

    fun addCandidate(profile: UserProfile) {
        val uid = profile.resolvedId
        if (uid.isBlank() || uid == currentUserId || profile.username.isBlank()) return
        byId.putIfAbsent(uid, profile)
    }

    val allById = allMentionableProfiles.associateBy { it.resolvedId }

    fun addAuthorFromPost(post: ThreadPost?) {
        if (post == null || post.authorId.isBlank() || post.authorId == currentUserId) return
        val existing = allById[post.authorId]
        if (existing != null) {
            addCandidate(existing)
        } else if (post.authorUsername.isNotBlank()) {
            addCandidate(
                UserProfile(
                    id = post.authorId,
                    userId = post.authorId,
                    username = post.authorUsername,
                    displayName = post.authorDisplayName.ifBlank { post.authorUsername },
                    avatarUrl = post.authorAvatarUrl
                )
            )
        }
    }

    addAuthorFromPost(replyTarget)
    threadPosts.forEach { addAuthorFromPost(it) }
    followedProfiles.forEach { addCandidate(it) }
    allMentionableProfiles.forEach { addCandidate(it) }
    return byId.values.toList()
}

/**
 * Represents an active `@query` token currently being typed at the cursor.
 */
data class ActiveMentionQuery(
    val atIndex: Int,
    val cursorIndex: Int,
    val query: String
)

private val MENTION_EXTRACT_REGEX = Regex("(?<![a-zA-Z0-9_.])@([a-zA-Z0-9_.]{2,30})")

private fun isMentionHandleChar(ch: Char): Boolean {
    return ch.isLetterOrDigit() || ch == '_' || ch == '.'
}

private fun isMentionBoundaryChar(ch: Char): Boolean {
    return ch.isWhitespace() || ch in charArrayOf('(', '[', '{', '"', '\'', '\u2022', '-', '/', ':', ',', ';', '!', '?')
}

/**
 * Inspects [value] around the current cursor position and returns an [ActiveMentionQuery]
 * when the user is actively typing `@` or `@prefix` (ignoring emails like `name@domain`).
 */
fun detectActiveMentionQuery(value: TextFieldValue): ActiveMentionQuery? {
    return detectActiveMentionQuery(value.text, value.selection)
}

fun detectActiveMentionQuery(text: String, selection: TextRange): ActiveMentionQuery? {
    if (!selection.collapsed) return null
    val cursor = selection.start.coerceIn(0, text.length)
    if (cursor == 0) return null

    var i = cursor - 1
    var scanned = 0
    while (i >= 0 && scanned <= 32) {
        val ch = text[i]
        if (ch == '@') {
            val validBoundary = i == 0 || isMentionBoundaryChar(text[i - 1])
            if (!validBoundary) return null
            val query = text.substring(i + 1, cursor)
            return ActiveMentionQuery(atIndex = i, cursorIndex = cursor, query = query)
        }
        if (!isMentionHandleChar(ch)) {
            return null
        }
        i--
        scanned++
    }
    return null
}

/**
 * Replaces the active `@query` span with `@username ` and positions the cursor right after
 * the inserted tag.
 */
fun insertMention(
    value: TextFieldValue,
    active: ActiveMentionQuery,
    username: String,
    maxChars: Int = 1000
): TextFieldValue {
    val cleanHandle = username.trim().removePrefix("@")
    if (cleanHandle.isEmpty()) return value
    val safeStart = active.atIndex.coerceIn(0, value.text.length)
    val safeEnd = active.cursorIndex.coerceIn(safeStart, value.text.length)

    val before = value.text.substring(0, safeStart)
    val after = value.text.substring(safeEnd)
    val needsTrailingSpace = after.isEmpty() || !after[0].isWhitespace()
    val inserted = if (needsTrailingSpace) "@$cleanHandle " else "@$cleanHandle"
    val nextText = (before + inserted + after).take(maxChars)
    val nextCursor = (before.length + inserted.length).coerceAtMost(nextText.length)
    return TextFieldValue(
        text = nextText,
        selection = TextRange(nextCursor)
    )
}

/**
 * Extracts distinct lowercase usernames tagged with `@username` from [text].
 */
fun extractMentionedUsernames(text: String, maxMentions: Int = 10): List<String> {
    if (text.isBlank() || '@' !in text) return emptyList()
    return MENTION_EXTRACT_REGEX.findAll(text)
        .map { it.groupValues[1].trimEnd('.').lowercase() }
        .filter { it.length >= 2 }
        .distinct()
        .take(maxMentions)
        .toList()
}

/**
 * Resolves `@username` tags in [text] to matching recipient user IDs using [allProfiles],
 * automatically excluding [excludeUserIds] (such as the author or already-notified parent author).
 */
fun resolveMentionedUserIds(
    text: String,
    allProfiles: List<UserProfile>,
    excludeUserIds: Set<String> = emptySet()
): List<String> {
    val handles = extractMentionedUsernames(text)
    if (handles.isEmpty()) return emptyList()
    val profilesByHandle = allProfiles
        .filter { it.username.isNotBlank() && it.resolvedId.isNotBlank() }
        .associateBy { it.username.trim().lowercase() }
    return handles.mapNotNull { handle ->
        val profile = profilesByHandle[handle] ?: return@mapNotNull null
        val uid = profile.resolvedId
        if (uid.isBlank() || uid in excludeUserIds) null else uid
    }.distinct()
}

/**
 * Material 3 dropdown triggered whenever the user types `@` in a post or comment composer,
 * displaying followed users filtered by the typed prefix.
 */
@Composable
fun MentionSuggestionsDropdown(
    activeQuery: ActiveMentionQuery?,
    followedProfiles: List<UserProfile>,
    onSelectUser: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
    followedIds: Set<String>? = null
) {
    val haptics = LocalOrbitHaptics.current
    val resolvedFollowedIds = followedIds ?: remember(followedProfiles) {
        followedProfiles.map { it.resolvedId }.toSet()
    }

    val matches = remember(activeQuery?.query, followedProfiles) {
        if (activeQuery == null) {
            emptyList()
        } else {
            val q = activeQuery.query.trim().lowercase()
            if (q.isEmpty()) {
                followedProfiles.take(8)
            } else {
                followedProfiles
                    .filter { user ->
                        user.username.lowercase().contains(q) ||
                            user.displayName.lowercase().contains(q)
                    }
                    .sortedWith(
                        compareByDescending<UserProfile> {
                            it.username.lowercase().startsWith(q)
                        }.thenByDescending {
                            it.displayName.lowercase().startsWith(q)
                        }.thenBy {
                            it.username.lowercase()
                        }
                    )
                    .take(8)
            }
        }
    }

    AnimatedVisibility(
        visible = activeQuery != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mention_dropdown")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AlternateEmail,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.mention_dropdown_header),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (matches.isNotEmpty()) {
                        Text(
                            text = "${matches.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.6.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )

                if (followedProfiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.mention_dropdown_no_following),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                            .testTag("mention_empty_no_following")
                    )
                } else if (matches.isEmpty()) {
                    val typed = activeQuery?.query.orEmpty()
                    Text(
                        text = stringResource(R.string.mention_dropdown_no_matches, typed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                            .testTag("mention_empty_no_matches")
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 216.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        matches.forEachIndexed { index, user ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable {
                                        haptics.performTap()
                                        onSelectUser(user)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("mention_suggestion_${user.username}")
                            ) {
                                ThreadAvatar(
                                    avatarUrl = user.avatarUrl,
                                    displayName = user.displayName.ifBlank { user.username },
                                    size = 34.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName.ifBlank { "@${user.username}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "@${user.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OrbitMutedMentionColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (user.resolvedId in resolvedFollowedIds) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_following),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            if (index < matches.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
