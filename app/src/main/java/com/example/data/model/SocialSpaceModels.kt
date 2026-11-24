package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.UUID

val COMMUNITY_CATEGORIES = listOf(
    "All",
    "Photography",
    "Tech",
    "Design",
    "Gaming",
    "Music",
    "Lifestyle"
)

val COMMUNITY_STREAM_CATEGORIES = listOf(
    "All posts",
    "Discussion",
    "Showcase",
    "Q&A",
    "News"
)

@Keep
@IgnoreExtraProperties
data class Community(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "Tech",
    val coverUrl: String = "",
    val accentIndex: Long = 0L,
    val ownerId: String = "",
    val ownerUsername: String = "",
    val privacyMode: String = "public", // "public" or "private"
    val moderatorIds: List<String> = emptyList(),
    val bannedUserIds: List<String> = emptyList(),
    val mutedUntilMap: Map<String, Long> = emptyMap(),
    val pendingRequestIds: List<String> = emptyList(),
    val invitedUserIds: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val memberCount: Long = 0L,
    val postCount: Long = 0L,
    val visibility: String = "public",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val createdAtMillis: Long
        get() = createdAt?.toDate()?.time ?: 0L

    val isPrivateSpace: Boolean
        get() = privacyMode.equals("private", ignoreCase = true)

    fun isAdmin(uid: String): Boolean = uid.isNotBlank() && ownerId == uid

    fun isModerator(uid: String): Boolean = uid.isNotBlank() && uid in moderatorIds

    fun canModerate(uid: String): Boolean = isAdmin(uid) || isModerator(uid)

    fun isBanned(uid: String): Boolean = uid.isNotBlank() && uid in bannedUserIds

    fun isInvited(uid: String): Boolean = uid.isNotBlank() && uid in invitedUserIds

    fun hasPendingRequest(uid: String): Boolean = uid.isNotBlank() && uid in pendingRequestIds

    fun muteRemainingMillis(uid: String, nowMillis: Long = System.currentTimeMillis()): Long {
        val until = mutedUntilMap[uid] ?: return 0L
        return (until - nowMillis).coerceAtLeast(0L)
    }

    fun isMuted(uid: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        muteRemainingMillis(uid, nowMillis) > 0L
}

@Keep
@IgnoreExtraProperties
data class CommunityPost(
    @DocumentId val id: String = "",
    val communityId: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorAvatarUrl: String = "",
    val streamCategory: String = "Discussion",
    val body: String = "",
    val imageUrls: List<String> = emptyList(),
    val attachments: List<PostAttachment> = emptyList(),
    val edited: Boolean = false,
    val plusOneCount: Long = 0L,
    val plusOneUserIds: List<String> = emptyList(),
    val commentCount: Long = 0L,
    val visibility: String = "public",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val createdAtMillis: Long
        get() = createdAt?.toDate()?.time ?: 0L
}

@Keep
@IgnoreExtraProperties
data class CommunityComment(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val body: String = "",
    val visibility: String = "public",
    val createdAt: Timestamp? = null
) {
    val createdAtMillis: Long
        get() = createdAt?.toDate()?.time ?: 0L
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantUsernames: Map<String, String> = emptyMap(),
    val participantAvatars: Map<String, String> = emptyMap(),
    val groupTitle: String = "",
    val lastReadAtByUser: Map<String, Timestamp> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val lastMessageAtMillis: Long
        get() = lastMessageAt?.toDate()?.time ?: updatedAt?.toDate()?.time ?: 0L

    @get:Exclude
    val isGroupChat: Boolean
        get() = participantIds.size > 2

    fun otherParticipantIds(myUid: String): List<String> =
        participantIds.filter { it.isNotBlank() && it != myUid }

    fun participantDisplayName(uid: String): String =
        participantNames[uid]?.ifBlank { null }
            ?: participantUsernames[uid]?.ifBlank { null }
            ?: "Orbit Member"

    fun participantUsername(uid: String): String =
        participantUsernames[uid]?.ifBlank { null } ?: "member"

    fun participantAvatarUrl(uid: String): String =
        participantAvatars[uid].orEmpty()

    fun peerId(myUid: String): String =
        participantIds.firstOrNull { it != myUid }.orEmpty()

    fun peerName(myUid: String): String {
        val pid = peerId(myUid)
        return participantDisplayName(pid)
    }

    fun peerUsername(myUid: String): String {
        val pid = peerId(myUid)
        return participantUsername(pid)
    }

    fun peerAvatarUrl(myUid: String): String {
        val pid = peerId(myUid)
        return participantAvatarUrl(pid)
    }

    fun displayTitle(myUid: String): String {
        if (!isGroupChat) return peerName(myUid)
        val trimmedTitle = groupTitle.trim()
        if (trimmedTitle.isNotBlank()) return trimmedTitle
        val names = otherParticipantIds(myUid).map { participantDisplayName(it) }
        return when {
            names.isEmpty() -> "Group chat"
            names.size <= 3 -> names.joinToString(", ")
            else -> "${names.take(2).joinToString(", ")} +${names.size - 2}"
        }
    }

    fun displaySubtitle(myUid: String): String =
        if (isGroupChat) "${participantIds.size} members" else "@${peerUsername(myUid)}"

    fun otherParticipantAvatarPairs(myUid: String): List<Pair<String, String>> {
        val others = otherParticipantIds(myUid)
        val source = if (others.isNotEmpty()) others else participantIds
        return source.map { uid -> participantAvatarUrl(uid) to participantDisplayName(uid) }
    }

    fun lastMessageSenderLabel(myUid: String): String = when {
        lastMessageSenderId.isBlank() -> ""
        lastMessageSenderId == myUid -> "You"
        else -> participantDisplayName(lastMessageSenderId)
    }

    fun myLastReadAtMillis(myUid: String): Long =
        lastReadAtByUser[myUid]?.toDate()?.time ?: 0L

    fun peerLastReadAtMillis(myUid: String): Long {
        val others = otherParticipantIds(myUid)
        if (others.isEmpty()) return 0L
        return others.mapNotNull { lastReadAtByUser[it]?.toDate()?.time }.maxOrNull() ?: 0L
    }

    fun seenByNames(myUid: String, messageCreatedAtMillis: Long): List<String> =
        seenByUsers(myUid, messageCreatedAtMillis).map { it.displayName }

    fun seenByUsers(myUid: String, messageCreatedAtMillis: Long): List<SeenReceiptInfo> {
        if (messageCreatedAtMillis <= 0L) return emptyList()
        return otherParticipantIds(myUid).mapNotNull { uid ->
            val readAt = lastReadAtByUser[uid]?.toDate()?.time ?: 0L
            if (readAt > 0L && readAt >= messageCreatedAtMillis) {
                SeenReceiptInfo(
                    uid = uid,
                    displayName = participantDisplayName(uid),
                    username = participantUsername(uid),
                    avatarUrl = participantAvatarUrl(uid),
                    readAtMillis = readAt
                )
            } else {
                null
            }
        }.sortedByDescending { it.readAtMillis }
    }

    fun latestSeenAtMillis(myUid: String, messageCreatedAtMillis: Long): Long {
        return seenByUsers(myUid, messageCreatedAtMillis).maxOfOrNull { it.readAtMillis } ?: 0L
    }

    fun isMessageSeenByPeer(myUid: String, messageCreatedAtMillis: Long): Boolean {
        val peerReadAt = peerLastReadAtMillis(myUid)
        return messageCreatedAtMillis > 0L && peerReadAt > 0L && peerReadAt >= messageCreatedAtMillis
    }

    fun isLastMessageSeenByPeer(myUid: String): Boolean {
        if (lastMessageSenderId != myUid || lastMessageText.isBlank()) return false
        return isMessageSeenByPeer(myUid, lastMessageAtMillis)
    }
}

data class SeenReceiptInfo(
    val uid: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String,
    val readAtMillis: Long
)

val DM_REACTION_EMOJIS = listOf(
    "❤️",
    "😂",
    "😮",
    "😢",
    "🔥",
    "👍",
    "🎉",
    "🙏"
)

data class DmReactionChipSummary(
    val emoji: String,
    val userIds: List<String>,
    val reactedByMe: Boolean
) {
    val count: Int
        get() = userIds.size
}

@Keep
@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val attachments: List<PostAttachment> = emptyList(),
    val reactions: Map<String, String> = emptyMap(),
    val createdAt: Timestamp? = null
) {
    @get:Exclude
    val createdAtMillis: Long
        get() = createdAt?.toDate()?.time ?: 0L

    fun myReaction(myUid: String): String? =
        reactions[myUid]?.trim()?.takeIf { it.isNotEmpty() }

    fun reactionSummaries(myUid: String): List<DmReactionChipSummary> {
        if (reactions.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<String, MutableList<String>>()
        reactions.forEach { (uid, rawEmoji) ->
            val emoji = rawEmoji.trim()
            if (uid.isNotBlank() && emoji.isNotEmpty()) {
                grouped.getOrPut(emoji) { mutableListOf() }.add(uid)
            }
        }
        return grouped.entries.map { (emoji, uids) ->
            DmReactionChipSummary(
                emoji = emoji,
                userIds = uids.distinct(),
                reactedByMe = myUid.isNotBlank() && myUid in uids
            )
        }
    }
}

fun buildConversationId(uidA: String, uidB: String): String {
    val sorted = listOf(uidA.trim(), uidB.trim()).sorted()
    val cleanA = sorted[0].replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(48)
    val cleanB = sorted[1].replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(48)
    return "dm_${cleanA}_${cleanB}"
}

fun buildGroupConversationId(): String =
    "grp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
