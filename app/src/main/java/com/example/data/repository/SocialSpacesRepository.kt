package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.cache.OrbitCacheManager
import com.example.data.model.Community
import com.example.data.model.CommunityComment
import com.example.data.model.CommunityPost
import com.example.data.model.Conversation
import com.example.data.model.DirectMessage
import com.example.data.model.PostAttachment
import com.example.data.model.UserProfile
import com.example.data.model.buildConversationId
import com.example.data.model.isGifUrl
import com.example.data.model.sanitizeUsername
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SocialSpacesRepository(context: Context) {

    private val db: FirebaseFirestore = OrbitCacheManager.getFirestore(context.applicationContext)

    private val communitiesCollection = db.collection("communities")
    private val communityPostsCollection = db.collection("communityPosts")
    private val conversationsCollection = db.collection("conversations")
    private val activitiesCollection = db.collection("activities")

    private fun requireUserId(): String =
        Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in to continue.")

    private fun unwrapFirestoreException(e: Exception): FirebaseFirestoreException? {
        if (e is FirebaseFirestoreException) return e
        if (e is CancellationException) return e.cause as? FirebaseFirestoreException
        return e.cause as? FirebaseFirestoreException
    }

    private suspend fun sendSpaceNotificationBestEffort(
        recipientIds: List<String>,
        actor: UserProfile,
        type: String,
        spaceId: String,
        snippet: String
    ) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val safeUsername = actor.username.ifBlank { sanitizeUsername(actor.displayName, uid) }.take(30)
        for (recipientId in recipientIds.distinct()) {
            if (recipientId.isBlank() || recipientId == uid) continue
            val actId = "spc_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val payload = mapOf(
                "recipientId" to recipientId,
                "actorId" to uid,
                "actorUsername" to safeUsername,
                "actorAvatarUrl" to actor.avatarUrl.take(350_000),
                "type" to type,
                "postId" to "",
                "spaceId" to spaceId.take(128),
                "snippet" to snippet.take(240),
                "createdAt" to FieldValue.serverTimestamp()
            )
            runCatching {
                activitiesCollection.document(actId).set(payload).await()
            }.onFailure { err ->
                Log.w("SocialSpacesRepo", "Space notification failed: ${err.message}")
            }
        }
    }

    // ==================== ORBIT SPACES ====================

    fun observeCommunities(limit: Long = 80L): Flow<List<Community>> = callbackFlow {
        val registration = communitiesCollection
            .whereEqualTo("visibility", "public")
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.LIST, "communities") }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull {
                        runCatching {
                            it.toObject(Community::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull()
                    }
                    ?.sortedByDescending { it.memberCount }
                    .orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getCommunityById(communityId: String): Result<Community?> = runCatching {
        requireUserId()
        val path = "communities/$communityId"
        val snap = try {
            communitiesCollection.document(communityId).get().await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.GET, path)
        }
        if (snap.exists()) {
            runCatching {
                snap.toObject(Community::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.getOrNull()
        } else {
            null
        }
    }

    fun observeCommunityPosts(communityId: String, limit: Long = 120L): Flow<List<CommunityPost>> = callbackFlow {
        val registration = communityPostsCollection
            .whereEqualTo("visibility", "public")
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.LIST, "communityPosts") }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val posts = snapshot?.documents
                    ?.mapNotNull {
                        runCatching {
                            it.toObject(CommunityPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull()
                    }
                    ?.filter { it.communityId == communityId }
                    ?.sortedByDescending { it.createdAtMillis }
                    .orEmpty()
                trySend(posts)
            }
        awaitClose { registration.remove() }
    }

    fun observeCommunityComments(postId: String, limit: Long = 80L): Flow<List<CommunityComment>> = callbackFlow {
        val path = "communityPosts/$postId/comments"
        val registration = communityPostsCollection
            .document(postId)
            .collection("comments")
            .whereEqualTo("visibility", "public")
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.LIST, path) }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val comments = snapshot?.documents
                    ?.mapNotNull {
                        runCatching {
                            it.toObject(CommunityComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull()
                    }
                    ?.sortedBy { it.createdAtMillis }
                    .orEmpty()
                trySend(comments)
            }
        awaitClose { registration.remove() }
    }

    suspend fun createCommunity(
        owner: UserProfile,
        name: String,
        description: String,
        category: String,
        coverUrl: String,
        accentIndex: Long,
        privacyMode: String = "public"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val safeName = name.trim().take(60)
        require(safeName.length >= 2) { "Space name must be at least 2 characters." }
        val safePrivacy = if (privacyMode.equals("private", ignoreCase = true)) "private" else "public"
        val communityId = "comm_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "communities/$communityId"
        val payload = mapOf(
            "name" to safeName,
            "description" to description.trim().take(500),
            "category" to category.trim().ifBlank { "Tech" }.take(30),
            "coverUrl" to coverUrl.take(450_000),
            "accentIndex" to accentIndex.coerceIn(0L, 10L),
            "ownerId" to uid,
            "ownerUsername" to owner.username.ifBlank { sanitizeUsername(owner.displayName, uid) }.take(30),
            "privacyMode" to safePrivacy,
            "moderatorIds" to emptyList<String>(),
            "bannedUserIds" to emptyList<String>(),
            "mutedUntilMap" to emptyMap<String, Long>(),
            "pendingRequestIds" to emptyList<String>(),
            "invitedUserIds" to emptyList<String>(),
            "memberIds" to listOf(uid),
            "memberCount" to 1L,
            "postCount" to 0L,
            "visibility" to "public",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            communitiesCollection.document(communityId).set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.CREATE, path)
        }
        communityId
    }

    /**
     * Ensures any legacy Space document (created before v7 schema added `visibility == "public"`
     * or `ownerUsername` / `accentIndex` / `memberIds`) includes all required schema fields
     * whenever an update or transaction runs, preventing Firestore rule rejections.
     */
    private fun buildSpaceUpdateMap(
        snap: DocumentSnapshot?,
        fallbackSpace: Community? = null,
        changes: Map<String, Any>
    ): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        val hasVisibility = snap?.getString("visibility") == "public"
        val hasOwnerUsername = !snap?.getString("ownerUsername").isNullOrBlank()
        val hasAccentIndex = snap?.getLong("accentIndex") != null
        val hasPostCount = snap?.getLong("postCount") != null
        val hasCreatedAt = runCatching { snap?.getTimestamp("createdAt") }.getOrNull() != null
        if (!hasVisibility) result["visibility"] = "public"
        if (!hasOwnerUsername) {
            val ownerId = snap?.getString("ownerId") ?: fallbackSpace?.ownerId.orEmpty()
            val candidate = fallbackSpace?.ownerUsername.orEmpty().ifBlank { sanitizeUsername("space_admin", ownerId) }
            result["ownerUsername"] = candidate.take(30)
        }
        if (!hasAccentIndex) {
            result["accentIndex"] = (fallbackSpace?.accentIndex ?: 0L).coerceIn(0L, 10L)
        }
        if (!hasPostCount) {
            result["postCount"] = (fallbackSpace?.postCount ?: 0L).coerceAtLeast(0L)
        }
        if (!hasCreatedAt) {
            result["createdAt"] = FieldValue.serverTimestamp()
        }
        result.putAll(changes)
        return result
    }

    suspend fun updateSpaceAdminSettings(
        spaceId: String,
        name: String,
        description: String,
        coverUrl: String,
        privacyMode: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val safeName = name.trim().take(60)
        require(safeName.length >= 2) { "Space name must be at least 2 characters." }
        val safePrivacy = if (privacyMode.equals("private", ignoreCase = true)) "private" else "public"
        val path = "communities/$spaceId"
        val docRef = communitiesCollection.document(spaceId)
        val snap = runCatching { docRef.get().await() }.getOrNull()
        try {
            docRef.update(
                buildSpaceUpdateMap(
                    snap = snap,
                    changes = mapOf(
                        "name" to safeName,
                        "description" to description.trim().take(500),
                        "coverUrl" to coverUrl.take(450_000),
                        "privacyMode" to safePrivacy,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun deleteSpace(spaceId: String): Result<Unit> = runCatching {
        requireUserId()
        val path = "communities/$spaceId"
        try {
            communitiesCollection.document(spaceId).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    suspend fun toggleJoinCommunity(communityId: String): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val docRef = communitiesCollection.document(communityId)
        val path = "communities/$communityId"
        var joinedResult = false
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) {
                    throw IllegalStateException("This Space no longer exists.")
                }
                @Suppress("UNCHECKED_CAST")
                val banned = (snap.get("bannedUserIds") as? List<String>).orEmpty()
                if (uid in banned) {
                    throw IllegalStateException("You are banned from this Space.")
                }
                @Suppress("UNCHECKED_CAST")
                val currentMembers = (snap.get("memberIds") as? List<String>)?.toMutableList() ?: mutableListOf()
                val isMember = uid in currentMembers
                if (isMember) {
                    currentMembers.remove(uid)
                    joinedResult = false
                } else if (currentMembers.size < 500) {
                    currentMembers.add(uid)
                    joinedResult = true
                }
                tx.update(
                    docRef,
                    buildSpaceUpdateMap(
                        snap = snap,
                        changes = mapOf(
                            "memberIds" to currentMembers,
                            "memberCount" to currentMembers.size.toLong(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                )
            }.await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            if (e is IllegalStateException) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
        joinedResult
    }

    suspend fun requestToJoinSpace(space: Community, me: UserProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!space.isBanned(uid)) { "You are banned from this Space." }
        require(uid !in space.memberIds) { "You are already a member of this Space." }
        val updatedRequests = (space.pendingRequestIds + uid).distinct().take(500)
        val path = "communities/${space.id}"
        try {
            communitiesCollection.document(space.id).update(
                mapOf(
                    "pendingRequestIds" to updatedRequests,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
        val staffRecipients = listOf(space.ownerId) + space.moderatorIds
        sendSpaceNotificationBestEffort(
            recipientIds = staffRecipients,
            actor = me,
            type = "space_join_request",
            spaceId = space.id,
            snippet = "Requested to join ${space.name}"
        )
    }

    suspend fun approveJoinRequest(space: Community, targetUid: String, actor: UserProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(space.canModerate(uid)) { "Only Admins or Moderators can approve join requests." }
        val updatedRequests = space.pendingRequestIds.filterNot { it == targetUid }
        val updatedInvites = space.invitedUserIds.filterNot { it == targetUid }
        val updatedMembers = (space.memberIds + targetUid).distinct().take(500)
        val path = "communities/${space.id}"
        try {
            communitiesCollection.document(space.id).update(
                mapOf(
                    "pendingRequestIds" to updatedRequests,
                    "invitedUserIds" to updatedInvites,
                    "memberIds" to updatedMembers,
                    "memberCount" to updatedMembers.size.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
        sendSpaceNotificationBestEffort(
            recipientIds = listOf(targetUid),
            actor = actor,
            type = "space_join_approved",
            spaceId = space.id,
            snippet = "You were accepted into ${space.name}"
        )
    }

    suspend fun declineJoinRequest(space: Community, targetUid: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(space.canModerate(uid)) { "Only Admins or Moderators can manage join requests." }
        val updatedRequests = space.pendingRequestIds.filterNot { it == targetUid }
        val path = "communities/${space.id}"
        try {
            communitiesCollection.document(space.id).update(
                mapOf(
                    "pendingRequestIds" to updatedRequests,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun inviteUserToSpace(space: Community, targetUid: String, actor: UserProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(space.canModerate(uid)) { "Only Admins or Moderators can invite users." }
        require(targetUid !in space.memberIds) { "User is already in this Space." }
        require(!space.isBanned(targetUid)) { "Unban this user before inviting them." }
        val updatedInvites = (space.invitedUserIds + targetUid).distinct().take(500)
        val path = "communities/${space.id}"
        try {
            communitiesCollection.document(space.id).update(
                mapOf(
                    "invitedUserIds" to updatedInvites,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
        sendSpaceNotificationBestEffort(
            recipientIds = listOf(targetUid),
            actor = actor,
            type = "space_invite",
            spaceId = space.id,
            snippet = "Invited you to join ${space.name}"
        )
    }

    suspend fun acceptSpaceInvite(space: Community, me: UserProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!space.isBanned(uid)) { "You are banned from this Space." }
        val updatedInvites = space.invitedUserIds.filterNot { it == uid }
        val updatedRequests = space.pendingRequestIds.filterNot { it == uid }
        val updatedMembers = (space.memberIds + uid).distinct().take(500)
        val path = "communities/${space.id}"
        try {
            communitiesCollection.document(space.id).update(
                mapOf(
                    "invitedUserIds" to updatedInvites,
                    "pendingRequestIds" to updatedRequests,
                    "memberIds" to updatedMembers,
                    "memberCount" to updatedMembers.size.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
        val staffRecipients = listOf(space.ownerId) + space.moderatorIds
        sendSpaceNotificationBestEffort(
            recipientIds = staffRecipients,
            actor = me,
            type = "space_member_joined",
            spaceId = space.id,
            snippet = "Accepted invite and joined ${space.name}"
        )
    }

    suspend fun toggleModerator(space: Community, targetUid: String): Result<Boolean> = runCatching {
        val uid = requireUserId()
        require(space.isAdmin(uid)) { "Only the Space Admin can assign moderators." }
        require(targetUid != space.ownerId) { "Space Admin already has full permissions." }
        val currentlyMod = targetUid in space.moderatorIds
        val updatedMods = if (currentlyMod) {
            space.moderatorIds.filterNot { it == targetUid }
        } else {
            (space.moderatorIds + targetUid).distinct().take(100)
        }
        val path = "communities/${space.id}"
        try {
            communitiesCollection.document(space.id).update(
                mapOf(
                    "moderatorIds" to updatedMods,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
        !currentlyMod
    }

    suspend fun muteMember(space: Community, targetUid: String, durationHours: Int): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(space.canModerate(uid)) { "Only Admins or Moderators can pause posting." }
        require(targetUid != space.ownerId) { "Cannot pause the Space Admin." }
        val nextMap = space.mutedUntilMap.toMutableMap()
        if (durationHours <= 0) {
            nextMap.remove(targetUid)
        } else {
            nextMap[targetUid] = System.currentTimeMillis() + durationHours * 3_600_000L
        }
        val path = "communities/${space.id}"
        try {
            communitiesCollection.document(space.id).update(
                mapOf(
                    "mutedUntilMap" to nextMap,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun kickMember(space: Community, targetUid: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(space.canModerate(uid)) { "Only Admins or Moderators can kick members." }
        require(targetUid != space.ownerId) { "Cannot kick the Space Admin." }
        val updatedMembers = space.memberIds.filterNot { it == targetUid }
        val path = "communities/${space.id}"
        val updates = mutableMapOf<String, Any>(
            "memberIds" to updatedMembers,
            "memberCount" to updatedMembers.size.toLong(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (space.isAdmin(uid) && targetUid in space.moderatorIds) {
            updates["moderatorIds"] = space.moderatorIds.filterNot { it == targetUid }
        }
        try {
            communitiesCollection.document(space.id).update(updates).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun banMember(space: Community, targetUid: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(space.canModerate(uid)) { "Only Admins or Moderators can ban members." }
        require(targetUid != space.ownerId) { "Cannot ban the Space Admin." }
        val updatedBanned = (space.bannedUserIds + targetUid).distinct().take(500)
        val updatedMembers = space.memberIds.filterNot { it == targetUid }
        val updatedRequests = space.pendingRequestIds.filterNot { it == targetUid }
        val updatedInvites = space.invitedUserIds.filterNot { it == targetUid }
        val path = "communities/${space.id}"
        val updates = mutableMapOf<String, Any>(
            "bannedUserIds" to updatedBanned,
            "memberIds" to updatedMembers,
            "memberCount" to updatedMembers.size.toLong(),
            "pendingRequestIds" to updatedRequests,
            "invitedUserIds" to updatedInvites,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (space.isAdmin(uid) && targetUid in space.moderatorIds) {
            updates["moderatorIds"] = space.moderatorIds.filterNot { it == targetUid }
        }
        try {
            communitiesCollection.document(space.id).update(updates).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun unbanMember(space: Community, targetUid: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(space.canModerate(uid)) { "Only Admins or Moderators can unban users." }
        val updatedBanned = space.bannedUserIds.filterNot { it == targetUid }
        val path = "communities/${space.id}"
        try {
            communitiesCollection.document(space.id).update(
                mapOf(
                    "bannedUserIds" to updatedBanned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun createCommunityPost(
        space: Community,
        author: UserProfile,
        streamCategory: String,
        body: String,
        imageUrls: List<String>,
        attachments: List<PostAttachment> = emptyList(),
        mentionedUserIds: List<String> = emptyList()
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(!space.isBanned(uid)) { "You are banned from posting in this Space." }
        val remainingMuteMs = space.muteRemainingMillis(uid)
        if (remainingMuteMs > 0L) {
            val mins = ((remainingMuteMs / 60_000L) + 1L)
            val label = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
            throw IllegalStateException("You are paused from posting in this Space for $label.")
        }
        val trimmedBody = body.trim().take(1000)
        val safeImages = imageUrls.filter { it.isNotBlank() }.take(4)
        val safeAttachments = attachments.filter { it.dataUri.isNotBlank() }.take(3).map { att ->
            mapOf(
                "fileName" to att.fileName.take(160).ifBlank { "attachment" },
                "mimeType" to att.mimeType.take(120).ifBlank { "application/octet-stream" },
                "sizeBytes" to att.sizeBytes.coerceAtLeast(0L),
                "dataUri" to att.dataUri.take(450_000)
            )
        }
        require(trimmedBody.isNotEmpty() || safeImages.isNotEmpty() || safeAttachments.isNotEmpty()) {
            "Write a post or attach a photo or file."
        }
        val postId = "cpost_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "communityPosts/$postId"
        val payload = mapOf(
            "communityId" to space.id,
            "authorId" to uid,
            "authorUsername" to author.username.ifBlank { sanitizeUsername(author.displayName, uid) }.take(30),
            "authorDisplayName" to author.displayName.ifBlank { "Orbit Member" }.take(50),
            "authorAvatarUrl" to author.avatarUrl.take(350_000),
            "streamCategory" to streamCategory.trim().ifBlank { "Discussion" }.take(30),
            "body" to trimmedBody,
            "imageUrls" to safeImages,
            "attachments" to safeAttachments,
            "edited" to false,
            "plusOneCount" to 0L,
            "plusOneUserIds" to emptyList<String>(),
            "commentCount" to 0L,
            "visibility" to "public",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            communityPostsCollection.document(postId).set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.CREATE, path)
        }

        val commRef = communitiesCollection.document(space.id)
        runCatching {
            db.runTransaction { tx ->
                val snap = tx.get(commRef)
                if (snap.exists()) {
                    val next = ((snap.getLong("postCount") ?: 0L) + 1L).coerceAtLeast(0L)
                    tx.update(
                        commRef,
                        buildSpaceUpdateMap(
                            snap = snap,
                            fallbackSpace = space,
                            changes = mapOf("postCount" to next, "updatedAt" to FieldValue.serverTimestamp())
                        )
                    )
                }
            }.await()
        }
        if (mentionedUserIds.isNotEmpty()) {
            sendSpaceNotificationBestEffort(
                recipientIds = mentionedUserIds.distinct().take(10),
                actor = author,
                type = "mention",
                spaceId = space.id,
                snippet = trimmedBody.take(180).ifBlank { "Mentioned you in ${space.name}" }
            )
        }
        postId
    }

    suspend fun editCommunityPost(post: CommunityPost, newBody: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(post.authorId == uid) { "Only the author can edit this post." }
        val trimmed = newBody.trim().take(1000)
        require(trimmed.isNotEmpty() || post.imageUrls.isNotEmpty() || post.attachments.isNotEmpty()) {
            "Post text cannot be empty."
        }
        val path = "communityPosts/${post.id}"
        try {
            communityPostsCollection.document(post.id).update(
                mapOf(
                    "body" to trimmed,
                    "edited" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun togglePlusOne(postId: String): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val docRef = communityPostsCollection.document(postId)
        val path = "communityPosts/$postId"
        var plusOned = false
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val ids = (snap.get("plusOneUserIds") as? List<String>)?.toMutableList() ?: mutableListOf()
                    if (uid in ids) {
                        ids.remove(uid)
                        plusOned = false
                    } else if (ids.size < 500) {
                        ids.add(uid)
                        plusOned = true
                    }
                    tx.update(
                        docRef,
                        mapOf(
                            "plusOneUserIds" to ids,
                            "plusOneCount" to ids.size.toLong(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
        plusOned
    }

    suspend fun addCommunityComment(
        postId: String,
        author: UserProfile,
        body: String,
        spaceId: String = "",
        mentionedUserIds: List<String> = emptyList()
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val trimmed = body.trim().take(500)
        require(trimmed.isNotEmpty()) { "Comment cannot be empty." }
        val commentId = "ccmt_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "communityPosts/$postId/comments/$commentId"
        val payload = mapOf(
            "authorId" to uid,
            "authorUsername" to author.username.ifBlank { sanitizeUsername(author.displayName, uid) }.take(30),
            "authorAvatarUrl" to author.avatarUrl.take(350_000),
            "body" to trimmed,
            "visibility" to "public",
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            communityPostsCollection.document(postId)
                .collection("comments")
                .document(commentId)
                .set(payload)
                .await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.CREATE, path)
        }

        val postRef = communityPostsCollection.document(postId)
        runCatching {
            db.runTransaction { tx ->
                val snap = tx.get(postRef)
                if (snap.exists()) {
                    val next = ((snap.getLong("commentCount") ?: 0L) + 1L).coerceAtLeast(0L)
                    tx.update(
                        postRef,
                        mapOf("commentCount" to next, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }.await()
        }
        if (mentionedUserIds.isNotEmpty()) {
            sendSpaceNotificationBestEffort(
                recipientIds = mentionedUserIds.distinct().take(10),
                actor = author,
                type = "mention",
                spaceId = spaceId,
                snippet = trimmed.take(180)
            )
        }
        commentId
    }

    suspend fun deleteCommunityPost(post: CommunityPost, space: Community?): Result<Unit> = runCatching {
        val uid = requireUserId()
        val canDelete = post.authorId == uid || (space != null && space.canModerate(uid))
        require(canDelete) { "Only the author, Admin, or Moderator can delete this post." }
        val path = "communityPosts/${post.id}"
        try {
            communityPostsCollection.document(post.id).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.DELETE, path)
        }
        val commRef = communitiesCollection.document(post.communityId)
        runCatching {
            db.runTransaction { tx ->
                val snap = tx.get(commRef)
                if (snap.exists()) {
                    val next = ((snap.getLong("postCount") ?: 0L) - 1L).coerceAtLeast(0L)
                    tx.update(
                        commRef,
                        mapOf("postCount" to next, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }.await()
        }
    }

    // ==================== TWITTER/X STYLE DIRECT MESSAGES ====================

    fun observeConversations(uid: String, limit: Long = 60L): Flow<List<Conversation>> = callbackFlow {
        val registration = conversationsCollection
            .whereArrayContains("participantIds", uid)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.LIST, "conversations") }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull {
                        runCatching {
                            it.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull()
                    }
                    ?.sortedByDescending { it.lastMessageAtMillis }
                    .orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    fun observeDirectMessages(conversationId: String, limit: Long = 150L): Flow<List<DirectMessage>> = callbackFlow {
        val path = "conversations/$conversationId/messages"
        val registration = conversationsCollection
            .document(conversationId)
            .collection("messages")
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.LIST, path) }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents
                    ?.mapNotNull {
                        runCatching {
                            it.toObject(DirectMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull()
                    }
                    ?.sortedBy { it.createdAtMillis }
                    .orEmpty()
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    suspend fun ensureConversation(me: UserProfile, peer: UserProfile): Result<String> = runCatching {
        val uid = requireUserId()
        val peerId = peer.resolvedId
        require(peerId.isNotBlank() && peerId != uid) { "Select another user to message." }

        val convId = buildConversationId(uid, peerId)
        val docRef = conversationsCollection.document(convId)
        val path = "conversations/$convId"

        val existing = runCatching { docRef.get().await() }.getOrNull()
        if (existing != null && existing.exists()) {
            return@runCatching convId
        }

        val participants = listOf(uid, peerId).sorted()
        val myUsername = me.username.ifBlank { sanitizeUsername(me.displayName, uid) }
        val peerUsername = peer.username.ifBlank { sanitizeUsername(peer.displayName, peerId) }

        val payload = mapOf(
            "participantIds" to participants,
            "participantNames" to mapOf(
                uid to me.displayName.ifBlank { myUsername }.take(50),
                peerId to peer.displayName.ifBlank { peerUsername }.take(50)
            ),
            "participantUsernames" to mapOf(
                uid to myUsername.take(30),
                peerId to peerUsername.take(30)
            ),
            "participantAvatars" to mapOf(
                uid to me.avatarUrl.take(350_000),
                peerId to peer.avatarUrl.take(350_000)
            ),
            "lastReadAtByUser" to mapOf(
                uid to FieldValue.serverTimestamp()
            ),
            "lastMessageText" to "",
            "lastMessageSenderId" to "",
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.CREATE, path)
        }
        convId
    }

    suspend fun ensureGroupConversation(
        me: UserProfile,
        peers: List<UserProfile>,
        groupTitle: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val distinctPeers = peers
            .filter { it.resolvedId.isNotBlank() && it.resolvedId != uid }
            .distinctBy { it.resolvedId }
            .take(11)
        require(distinctPeers.isNotEmpty()) { "Select at least one person to message." }

        if (distinctPeers.size == 1) {
            return@runCatching ensureConversation(me, distinctPeers.first()).getOrThrow()
        }

        val convId = com.example.data.model.buildGroupConversationId()
        val docRef = conversationsCollection.document(convId)
        val path = "conversations/$convId"

        val allParticipants = listOf(me.copy(id = uid, userId = uid)) + distinctPeers
        val participantIds = allParticipants.map { it.resolvedId }.distinct().sorted()

        val participantNames = LinkedHashMap<String, String>(allParticipants.size)
        val participantUsernames = LinkedHashMap<String, String>(allParticipants.size)
        val participantAvatars = LinkedHashMap<String, String>(allParticipants.size)
        allParticipants.forEach { profile ->
            val pid = profile.resolvedId
            val handle = profile.username.ifBlank { sanitizeUsername(profile.displayName, pid) }.take(30)
            participantNames[pid] = profile.displayName.ifBlank { handle }.take(50)
            participantUsernames[pid] = handle
            participantAvatars[pid] = profile.avatarUrl.take(350_000)
        }

        val payload = linkedMapOf<String, Any>(
            "participantIds" to participantIds,
            "participantNames" to participantNames,
            "participantUsernames" to participantUsernames,
            "participantAvatars" to participantAvatars,
            "lastReadAtByUser" to mapOf(uid to FieldValue.serverTimestamp()),
            "lastMessageText" to "",
            "lastMessageSenderId" to "",
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val trimmedTitle = groupTitle.trim().take(60)
        if (trimmedTitle.isNotEmpty()) {
            payload["groupTitle"] = trimmedTitle
        }

        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.CREATE, path)
        }
        convId
    }

    suspend fun sendDirectMessage(
        conversationId: String,
        me: UserProfile,
        peer: UserProfile?,
        body: String,
        imageUrl: String = "",
        attachments: List<PostAttachment> = emptyList()
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val trimmed = body.trim().take(1000)
        val safeImage = imageUrl.take(350_000)
        val safeAttachments = attachments.filter { it.dataUri.isNotBlank() }.take(3).map { att ->
            mapOf(
                "fileName" to att.fileName.take(160).ifBlank { "attachment" },
                "mimeType" to att.mimeType.take(120).ifBlank { "application/octet-stream" },
                "sizeBytes" to att.sizeBytes.coerceAtLeast(0L),
                "dataUri" to att.dataUri.take(450_000)
            )
        }
        require(trimmed.isNotEmpty() || safeImage.isNotEmpty() || safeAttachments.isNotEmpty()) {
            "Type a message or attach a photo or file."
        }

        val convRef = conversationsCollection.document(conversationId)
        val convPath = "conversations/$conversationId"
        val myUsername = me.username.ifBlank { sanitizeUsername(me.displayName, uid) }.take(30)

        val convSnap = runCatching { convRef.get().await() }.getOrNull()
        if (convSnap == null || !convSnap.exists()) {
            val resolvedPeer = requireNotNull(peer) { "Conversation does not exist." }
            ensureConversation(me, resolvedPeer).getOrThrow()
        }

        val messageId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val msgPath = "conversations/$conversationId/messages/$messageId"
        val messagePayload = mapOf(
            "senderId" to uid,
            "senderUsername" to myUsername,
            "body" to trimmed,
            "imageUrl" to safeImage,
            "attachments" to safeAttachments,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            convRef.collection("messages").document(messageId).set(messagePayload).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.CREATE, msgPath)
        }

        val preview = when {
            trimmed.isNotBlank() -> trimmed
            safeImage.isNotBlank() && isGifUrl(safeImage) -> "Sent a GIF"
            safeImage.isNotBlank() -> "Sent a photo"
            else -> "Sent ${attachments.firstOrNull()?.fileName ?: "a file"}"
        }.take(500)
        try {
            convRef.update(
                FieldPath.of("lastMessageText"), preview,
                FieldPath.of("lastMessageSenderId"), uid,
                FieldPath.of("lastMessageAt"), FieldValue.serverTimestamp(),
                FieldPath.of("lastReadAtByUser", uid), FieldValue.serverTimestamp(),
                FieldPath.of("updatedAt"), FieldValue.serverTimestamp()
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, convPath)
        }

        messageId
    }

    suspend fun markConversationRead(conversationId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (conversationId.isBlank()) return@runCatching
        val convRef = conversationsCollection.document(conversationId)
        val convPath = "conversations/$conversationId"
        try {
            convRef.update(
                FieldPath.of("lastReadAtByUser", uid), FieldValue.serverTimestamp(),
                FieldPath.of("updatedAt"), FieldValue.serverTimestamp()
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            val fsError = unwrapFirestoreException(e)
            // Ignore NOT_FOUND if the conversation hasn't been created yet
            if (fsError?.code == FirebaseFirestoreException.Code.NOT_FOUND) return@runCatching
            handleFirestoreError(e, OperationType.UPDATE, convPath)
        }
    }

    suspend fun toggleDirectMessageReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
        currentReaction: String?
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(conversationId.isNotBlank() && messageId.isNotBlank()) { "Invalid message reference." }
        val cleanEmoji = emoji.trim().take(16)
        require(cleanEmoji.isNotEmpty()) { "Select an emoji reaction." }
        val msgRef = conversationsCollection
            .document(conversationId)
            .collection("messages")
            .document(messageId)
        val msgPath = "conversations/$conversationId/messages/$messageId"
        val valueToWrite: Any = if (currentReaction == cleanEmoji) {
            FieldValue.delete()
        } else {
            cleanEmoji
        }
        try {
            msgRef.update(FieldPath.of("reactions", uid), valueToWrite).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, msgPath)
        }
    }

    suspend fun deleteDirectMessage(conversationId: String, messageId: String): Result<Unit> = runCatching {
        requireUserId()
        val msgPath = "conversations/$conversationId/messages/$messageId"
        try {
            conversationsCollection
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.DELETE, msgPath)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        requireUserId()
        val convPath = "conversations/$conversationId"
        try {
            conversationsCollection.document(conversationId).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.DELETE, convPath)
        }
    }
}
