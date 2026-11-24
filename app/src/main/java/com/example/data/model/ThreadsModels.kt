package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.net.URI
import java.util.concurrent.TimeUnit

@Keep
@IgnoreExtraProperties
data class PostAttachment(
    val fileName: String = "",
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val dataUri: String = ""
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video/", ignoreCase = true)

    val isImage: Boolean
        get() = mimeType.startsWith("image/", ignoreCase = true)
}

@Keep
@IgnoreExtraProperties
data class ProfileLink(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val thumbnailUrl: String = "",
    val createdAtMillis: Long = 0L,
    val pinned: Boolean = false,
    val sortOrder: Int = 0
) {
    val resolvedHost: String
        get() = extractHostFromUrl(url)

    val resolvedThumbnailUrl: String
        get() = thumbnailUrl.ifBlank { buildLinkThumbnailUrl(url) }
}

@Keep
@IgnoreExtraProperties
data class ProfileAlbum(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerUsername: String = "",
    val title: String = "",
    val coverUrl: String = "",
    val itemCount: Long = 0L,
    val visibility: String = "public",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val createdAtMillis: Long
        get() = createdAt?.toDate()?.time ?: 0L
}

@Keep
@IgnoreExtraProperties
data class AlbumMediaItem(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val mediaUrl: String = "",
    val mimeType: String = "image/jpeg",
    val mediaType: String = "image", // "image" or "video"
    val fileName: String = "",
    val sortOrder: Int = 0,
    val visibility: String = "public",
    val createdAt: Timestamp? = null
) {
    val isVideo: Boolean
        get() = mediaType.equals("video", ignoreCase = true) || mimeType.startsWith("video/", ignoreCase = true)

    val createdAtMillis: Long
        get() = createdAt?.toDate()?.time ?: 0L
}

enum class OrbitProfileBadge(
    val id: String,
    val label: String,
    val description: String
) {
    CREATOR(
        id = "creator",
        label = "Creator",
        description = "Show Creator status beside your @handle"
    ),
    YOUTUBER(
        id = "youtuber",
        label = "YouTuber",
        description = "Show YouTuber status beside your @handle"
    ),
    INFLUENCER(
        id = "influencer",
        label = "Influencer",
        description = "Show Influencer status beside your @handle"
    ),
    DEVELOPER(
        id = "developer",
        label = "Developer",
        description = "Show Developer status beside your @handle"
    );

    companion object {
        fun fromId(raw: String): OrbitProfileBadge? {
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull { it.id == normalized }
        }

        fun normalizeIds(raw: Collection<String>): List<String> {
            val selected = raw.mapNotNull { fromId(it) }.toSet()
            return entries.filter { it in selected }.map { it.id }
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val bannerUrl: String = "",
    val linkUrl: String = "",
    val extraLinks: List<String> = emptyList(),
    val savedLinks: List<ProfileLink> = emptyList(),
    val profileBadges: List<String> = emptyList(),
    val privateAccount: Boolean = false,
    val visibility: String = "public",
    val followerCount: Long = 0L,
    val followingCount: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val resolvedId: String
        get() = userId.ifBlank { id }

    @get:Exclude
    val resolvedProfileBadges: List<OrbitProfileBadge>
        get() = OrbitProfileBadge.normalizeIds(profileBadges).mapNotNull { OrbitProfileBadge.fromId(it) }
}

@Keep
@IgnoreExtraProperties
data class PrivateUserState(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val followingIds: List<String> = emptyList(),
    val likedPostIds: List<String> = emptyList(),
    val repostedPostIds: List<String> = emptyList(),
    val savedPostIds: List<String> = emptyList(),
    val blockedUserIds: List<String> = emptyList(),
    val mutedUserIds: List<String> = emptyList(),
    val mutedWords: List<String> = emptyList(),
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ThreadPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorAvatarUrl: String = "",
    val body: String = "",
    val topic: String = "",
    val imageUrls: List<String> = emptyList(),
    val attachments: List<PostAttachment> = emptyList(),
    val parentPostId: String = "",
    val rootPostId: String = "",
    val replyToAuthorUsername: String = "",
    val replyToBody: String = "",
    val repostOfPostId: String = "",
    val repostAuthorUsername: String = "",
    val quotedPostId: String = "",
    val quotedAuthorUsername: String = "",
    val quotedAuthorDisplayName: String = "",
    val quotedAuthorAvatarUrl: String = "",
    val quotedBody: String = "",
    val quotedImageUrl: String = "",
    val audienceMode: String = "everyone", // "everyone" | "followers_only"
    val replyMode: String = "everyone", // "everyone" | "followers_only" | "selected_users"
    val allowedReplyUserIds: List<String> = emptyList(),
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, Long> = emptyMap(),
    val pollEndsAtMillis: Long = 0L,
    val edited: Boolean = false,
    val visibility: String = "public",
    val likeCount: Long = 0L,
    val replyCount: Long = 0L,
    val repostCount: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val isTopLevel: Boolean
        get() = parentPostId.isBlank()

    @get:Exclude
    val hasQuotedPost: Boolean
        get() = quotedPostId.isNotBlank()

    @get:Exclude
    val hasPoll: Boolean
        get() = pollOptions.size >= 2

    @get:Exclude
    val hasVisualMedia: Boolean
        get() = imageUrls.isNotEmpty() || attachments.any { it.isImage || it.isVideo }

    @get:Exclude
    val createdAtMillis: Long
        get() = createdAt?.toDate()?.time ?: 0L

    fun isPollEnded(nowMillis: Long = System.currentTimeMillis()): Boolean =
        hasPoll && pollEndsAtMillis > 0L && nowMillis >= pollEndsAtMillis
}

@Keep
@IgnoreExtraProperties
data class ActivityItem(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val actorId: String = "",
    val actorUsername: String = "",
    val actorAvatarUrl: String = "",
    val type: String = "like", // like, reply, repost, quote, follow, space_invite, space_join_request, space_join_approved, space_member_joined, app_update
    val postId: String = "",
    val spaceId: String = "",
    val snippet: String = "",
    val createdAt: Timestamp? = null
) {
    val createdAtMillis: Long
        get() = createdAt?.toDate()?.time ?: 0L
}

@Keep
@IgnoreExtraProperties
data class AppRelease(
    @DocumentId val id: String = "",
    val versionCode: Long = 0L,
    val versionName: String = "",
    val releaseNotes: String = "",
    val driveDownloadUrl: String = "",
    val publishedBy: String = "",
    val updatedAt: Timestamp? = null
)

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

enum class FeedTab(val label: String) {
    FOR_YOU("For you"),
    FOLLOWING("Following"),
    FRAMES("Frames")
}

enum class ProfileTab(val label: String) {
    THREADS("Threads"),
    REPLIES("Replies"),
    REPOSTS("Reposts"),
    MEDIA("Media"),
    LINKS("Links")
}

enum class ActivityFilter(val label: String) {
    ALL("All"),
    MENTIONS("Mentions"),
    REPLIES("Replies"),
    LIKES("Likes"),
    REPOSTS("Reposts"),
    FOLLOWS("Follows"),
    SPACES("Spaces"),
    UPDATES("Updates");

    fun matches(type: String): Boolean = when (this) {
        ALL -> true
        MENTIONS -> type == "mention"
        REPLIES -> type == "reply"
        LIKES -> type == "like"
        REPOSTS -> type == "repost" || type == "quote"
        FOLLOWS -> type == "follow"
        SPACES -> type in setOf("space_invite", "space_join_request", "space_join_approved", "space_member_joined")
        UPDATES -> type == "app_update"
    }
}

fun formatRelativeTime(timestamp: Timestamp?, nowMillis: Long = System.currentTimeMillis()): String {
    val millis = timestamp?.toDate()?.time ?: return "now"
    val diffMs = (nowMillis - millis).coerceAtLeast(0L)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMs)
    return when {
        seconds < 60 -> "${seconds.coerceAtLeast(1)}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        seconds < 604800 -> "${seconds / 86400}d"
        else -> "${seconds / 604800}w"
    }
}

fun formatCompactCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
            .replace(".0M", "M")
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
            .replace(".0K", "K")
        else -> count.toString()
    }
}

fun sanitizeUsername(raw: String, fallbackSeed: String): String {
    val cleaned = raw.lowercase()
        .replace(Regex("[^a-z0-9._]"), "")
        .trim('.')
        .take(24)
    return if (cleaned.length >= 2) {
        cleaned
    } else {
        "orbit_${fallbackSeed.lowercase().replace(Regex("[^a-z0-9]"), "").take(6).ifEmpty { "user" }}"
    }
}

fun normalizeWebUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

fun extractHostFromUrl(url: String): String {
    val normalized = normalizeWebUrl(url)
    return runCatching {
        URI(normalized).host?.removePrefix("www.").orEmpty()
    }.getOrDefault("").ifBlank {
        normalized.removePrefix("https://").removePrefix("http://").substringBefore("/")
    }
}

fun buildLinkThumbnailUrl(url: String): String {
    val host = extractHostFromUrl(url)
    return if (host.isNotBlank()) {
        "https://www.google.com/s2/favicons?sz=128&domain=$host"
    } else {
        ""
    }
}

private val URL_EXTRACT_REGEX = Regex("""(https?://[^\s<>()]+|www\.[^\s<>()]+)""", RegexOption.IGNORE_CASE)

fun extractUrlsFromText(text: String): List<String> =
    URL_EXTRACT_REGEX.findAll(text)
        .map { it.value.trimEnd('.', ',', ';', '!', '?', ')', ']') }
        .distinct()
        .toList()

fun isGifUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    return lower.contains("giphy.com/") ||
        lower.contains("tenor.com/") ||
        lower.contains("kb_gif_") ||
        lower.endsWith(".gif") ||
        lower.contains(".gif?") ||
        lower.startsWith("data:image/gif")
}
