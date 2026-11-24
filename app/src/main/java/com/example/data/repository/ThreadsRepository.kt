package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.cache.OrbitCacheManager
import com.example.BuildConfig
import com.example.data.model.ActivityItem
import com.example.data.model.AlbumMediaItem
import com.example.data.model.AppRelease
import com.example.data.model.PostAttachment
import com.example.data.model.PrivateUserState
import com.example.data.model.ProfileAlbum
import com.example.data.model.ProfileLink
import com.example.data.model.ThreadPost
import com.example.data.model.UserProfile
import com.example.data.model.buildLinkThumbnailUrl
import com.example.data.model.normalizeWebUrl
import com.example.data.model.sanitizeUsername
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ThreadsRepository(context: Context) {

    private val db: FirebaseFirestore = OrbitCacheManager.getFirestore(context.applicationContext)

    private val profilesCollection = db.collection("profiles")
    private val postsCollection = db.collection("posts")
    private val activitiesCollection = db.collection("activities")
    private val communityPostsCollection = db.collection("communityPosts")
    private val appReleasesCollection = db.collection("appReleases")

    private fun privateStateDoc(uid: String) =
        db.collection("users").document(uid).collection("private").document("state")

    private fun requireUserId(): String =
        Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in to access Orbit.")

    private fun unwrapFirestoreException(e: Exception): FirebaseFirestoreException? {
        if (e is FirebaseFirestoreException) return e
        if (e is CancellationException) return e.cause as? FirebaseFirestoreException
        return e.cause as? FirebaseFirestoreException
    }

    private fun DocumentSnapshot.safeString(field: String): String? =
        runCatching { getString(field) }.getOrNull()

    private fun DocumentSnapshot.safeLong(field: String): Long? =
        runCatching { getLong(field) }.getOrNull()

    private fun isCurrentSchemaProfile(snapshot: DocumentSnapshot, uid: String): Boolean {
        if (!snapshot.exists()) return false
        return snapshot.safeString("visibility") == "public" &&
            snapshot.safeString("userId") == uid &&
            runCatching { snapshot.getTimestamp("createdAt") }.getOrNull() != null &&
            snapshot.safeLong("followerCount") != null &&
            snapshot.safeLong("followingCount") != null
    }

    suspend fun ensureUserInitialized(
        uid: String,
        displayName: String?,
        email: String?,
        photoUrl: String?
    ): Result<UserProfile> = runCatching {
        val profileRef = profilesCollection.document(uid)
        val profilePath = "profiles/$uid"
        val snapshot = try {
            profileRef.get().await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.GET, profilePath)
        }

        val resolvedProfile = if (isCurrentSchemaProfile(snapshot, uid)) {
            runCatching {
                snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.getOrNull() ?: UserProfile(id = uid, userId = uid)
        } else {
            val existingDisplayName = snapshot.safeString("displayName")?.trim()?.take(50)?.ifBlank { null }
            val existingUsername = (snapshot.safeString("username") ?: snapshot.safeString("handle"))
                ?.trim()?.ifBlank { null }
            val existingBio = snapshot.safeString("bio")?.trim()?.take(240).orEmpty()
            val existingAvatar = snapshot.safeString("avatarUrl")?.take(350_000)?.ifBlank { null }
            val existingBanner = snapshot.safeString("bannerUrl")?.take(450_000).orEmpty()
            val existingLink = snapshot.safeString("linkUrl")?.trim()?.take(300).orEmpty()

            val safeDisplayName = existingDisplayName
                ?: displayName?.trim()?.take(50)?.ifBlank { null }
                ?: email?.substringBefore("@")?.take(30)?.ifBlank { null }
                ?: "Orbit Member"
            val seed = existingUsername ?: email?.substringBefore("@") ?: safeDisplayName
            val safeUsername = sanitizeUsername(seed, uid)
            val safeAvatar = existingAvatar ?: (photoUrl ?: "").take(350_000)

            val existingBadges = com.example.data.model.OrbitProfileBadge.normalizeIds(
                (snapshot.get("profileBadges") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            )
            val existingPrivateAccount = runCatching { snapshot.getBoolean("privateAccount") }.getOrNull() ?: false

            val data = mapOf(
                "userId" to uid,
                "username" to safeUsername,
                "displayName" to safeDisplayName,
                "bio" to existingBio,
                "avatarUrl" to safeAvatar,
                "bannerUrl" to existingBanner,
                "linkUrl" to existingLink,
                "extraLinks" to emptyList<String>(),
                "savedLinks" to emptyList<Map<String, Any>>(),
                "profileBadges" to existingBadges,
                "privateAccount" to existingPrivateAccount,
                "visibility" to "public",
                "followerCount" to (snapshot.safeLong("followerCount") ?: 0L).coerceAtLeast(0L),
                "followingCount" to (snapshot.safeLong("followingCount") ?: 0L).coerceAtLeast(0L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                profileRef.set(data).await()
            } catch (e: Exception) {
                if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
                handleFirestoreError(e, if (snapshot.exists()) OperationType.UPDATE else OperationType.CREATE, profilePath)
            }
            UserProfile(
                id = uid,
                userId = uid,
                username = safeUsername,
                displayName = safeDisplayName,
                bio = existingBio,
                avatarUrl = safeAvatar,
                bannerUrl = existingBanner,
                linkUrl = existingLink,
                visibility = "public"
            )
        }

        val privateRef = privateStateDoc(uid)
        val privatePath = "users/$uid/private/state"
        val privateSnap = try {
            privateRef.get().await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.GET, privatePath)
        }
        if (!privateSnap.exists() || privateSnap.safeString("userId") != uid) {
            val privateData = mapOf(
                "userId" to uid,
                "email" to (email ?: "").take(200),
                "followingIds" to emptyList<String>(),
                "likedPostIds" to emptyList<String>(),
                "repostedPostIds" to emptyList<String>(),
                "savedPostIds" to emptyList<String>(),
                "blockedUserIds" to emptyList<String>(),
                "mutedUserIds" to emptyList<String>(),
                "mutedWords" to emptyList<String>(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                privateRef.set(privateData).await()
            } catch (e: Exception) {
                if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
                handleFirestoreError(e, OperationType.CREATE, privatePath)
            }
        }

        OrbitCacheManager.cacheProfiles(listOf(resolvedProfile))
        resolvedProfile
    }

    fun observePublicPosts(limit: Long = 60L): Flow<List<ThreadPost>> = callbackFlow {
        val registration = postsCollection
            .whereEqualTo("visibility", "public")
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.LIST, "posts") }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val posts = snapshot?.documents
                    ?.mapNotNull { doc ->
                        runCatching {
                            doc.toObject(ThreadPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull()
                    }
                    ?.sortedByDescending { it.createdAtMillis }
                    .orEmpty()
                OrbitCacheManager.cachePosts(posts)
                trySend(posts)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Explicitly pulls the latest public feed posts from Firestore server (falling back to cache
     * only if the device is offline) for pull-to-refresh updates.
     */
    suspend fun fetchFreshPublicPosts(limit: Long = 60L): Result<List<ThreadPost>> = runCatching {
        requireUserId()
        val query = postsCollection
            .whereEqualTo("visibility", "public")
            .limit(limit)
        val snapshot = try {
            query.get(Source.SERVER).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            val fsError = unwrapFirestoreException(e)
            if (fsError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                query.get(Source.DEFAULT).await()
            } else {
                handleFirestoreError(e, OperationType.LIST, "posts")
            }
        }
        val posts = snapshot.documents
            .mapNotNull { doc ->
                runCatching {
                    doc.toObject(ThreadPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.getOrNull()
            }
            .sortedByDescending { it.createdAtMillis }
        OrbitCacheManager.cachePosts(posts)
        posts
    }

    /**
     * Explicitly pulls the latest public profiles from Firestore server for pull-to-refresh sync.
     */
    suspend fun fetchFreshPublicProfiles(limit: Long = 100L): Result<List<UserProfile>> = runCatching {
        requireUserId()
        val query = profilesCollection
            .whereEqualTo("visibility", "public")
            .limit(limit)
        val snapshot = try {
            query.get(Source.SERVER).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            val fsError = unwrapFirestoreException(e)
            if (fsError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                query.get(Source.DEFAULT).await()
            } else {
                handleFirestoreError(e, OperationType.LIST, "profiles")
            }
        }
        val profiles = snapshot.documents
            .mapNotNull { doc ->
                runCatching {
                    doc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.getOrNull()
            }
            .sortedByDescending { it.followerCount }
        OrbitCacheManager.cacheProfiles(profiles)
        profiles
    }

    fun observePublicProfiles(limit: Long = 100L): Flow<List<UserProfile>> = callbackFlow {
        val registration = profilesCollection
            .whereEqualTo("visibility", "public")
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.LIST, "profiles") }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val profiles = snapshot?.documents
                    ?.mapNotNull { doc ->
                        runCatching {
                            doc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull()
                    }
                    ?.sortedByDescending { it.followerCount }
                    .orEmpty()
                OrbitCacheManager.cacheProfiles(profiles)
                trySend(profiles)
            }
        awaitClose { registration.remove() }
    }

    fun observePrivateState(uid: String): Flow<PrivateUserState> = callbackFlow {
        val path = "users/$uid/private/state"
        val registration = privateStateDoc(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.GET, path) }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val state = runCatching {
                    snapshot?.toObject(
                        PrivateUserState::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }.getOrNull() ?: PrivateUserState(id = "state", userId = uid)
                trySend(state)
            }
        awaitClose { registration.remove() }
    }

    fun observeActivities(uid: String, limit: Long = 80L): Flow<List<ActivityItem>> = callbackFlow {
        val registration = activitiesCollection
            .whereEqualTo("recipientId", uid)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.LIST, "activities") }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    ?.mapNotNull { doc ->
                        runCatching {
                            doc.toObject(ActivityItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull()
                    }
                    ?.sortedByDescending { it.createdAtMillis }
                    .orEmpty()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    fun observeLatestAppRelease(): Flow<AppRelease?> = callbackFlow {
        val registration = appReleasesCollection
            .document("latest")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val release = if (snapshot != null && snapshot.exists()) {
                    runCatching {
                        snapshot.toObject(AppRelease::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                } else {
                    null
                }
                trySend(release)
            }
        awaitClose { registration.remove() }
    }

    suspend fun fetchLatestAppRelease(): Result<AppRelease?> = runCatching {
        requireUserId()
        val ref = appReleasesCollection.document("latest")
        val snapshot = try {
            ref.get(Source.SERVER).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            val fsError = unwrapFirestoreException(e)
            if (fsError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                ref.get(Source.DEFAULT).await()
            } else {
                handleFirestoreError(e, OperationType.GET, "appReleases/latest")
            }
        }
        if (snapshot.exists()) {
            runCatching {
                snapshot.toObject(AppRelease::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.getOrNull()
        } else {
            null
        }
    }

    suspend fun getPostById(postId: String): Result<ThreadPost?> = runCatching {
        requireUserId()
        val snap = postsCollection.document(postId).get().await()
        if (snap.exists()) {
            runCatching {
                snap.toObject(ThreadPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.getOrNull()
        } else {
            null
        }
    }

    suspend fun createPost(
        author: UserProfile,
        body: String,
        topic: String = "",
        imageUrls: List<String> = emptyList(),
        attachments: List<PostAttachment> = emptyList(),
        parentPost: ThreadPost? = null,
        quotedPost: ThreadPost? = null,
        audienceMode: String = "everyone",
        replyMode: String = "everyone",
        allowedReplyUserIds: List<String> = emptyList(),
        pollOptions: List<String> = emptyList(),
        pollDurationHours: Int = 24,
        mentionedUserIds: List<String> = emptyList()
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val trimmedBody = body.trim().take(500)
        val safeImages = imageUrls.filter { it.isNotBlank() }.take(4)
        val safeAttachments = attachments.filter { it.dataUri.isNotBlank() }.take(3).map { att ->
            mapOf(
                "fileName" to att.fileName.take(160).ifBlank { "attachment" },
                "mimeType" to att.mimeType.take(120).ifBlank { "application/octet-stream" },
                "sizeBytes" to att.sizeBytes.coerceAtLeast(0L),
                "dataUri" to att.dataUri.take(450_000)
            )
        }
        val cleanedPollOptions = pollOptions
            .map { it.trim().take(60) }
            .filter { it.isNotEmpty() }
            .take(4)
        val safePollOptions = if (cleanedPollOptions.size >= 2) cleanedPollOptions else emptyList()
        val pollEndsAtMillis = if (safePollOptions.size >= 2) {
            System.currentTimeMillis() + pollDurationHours.coerceIn(1, 168) * 3_600_000L
        } else {
            0L
        }

        require(
            trimmedBody.isNotEmpty() ||
                safeImages.isNotEmpty() ||
                safeAttachments.isNotEmpty() ||
                quotedPost != null ||
                safePollOptions.size >= 2
        ) {
            "Write something or attach a photo, GIF, poll, video, or file to post."
        }

        val postId = "post_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val postRef = postsCollection.document(postId)
        val parentPostId = parentPost?.id.orEmpty()
        val rootPostId = when {
            parentPost == null -> ""
            parentPost.rootPostId.isNotBlank() -> parentPost.rootPostId
            else -> parentPost.id
        }
        val safeAudience = if (audienceMode == "followers_only") "followers_only" else "everyone"
        val safeReplyMode = when (replyMode) {
            "followers_only", "selected_users" -> replyMode
            else -> "everyone"
        }
        val safeAllowedReplyUsers = allowedReplyUserIds.filter { it.isNotBlank() }.distinct().take(100)

        val payload = mapOf(
            "authorId" to uid,
            "authorUsername" to author.username.ifBlank { sanitizeUsername(author.displayName, uid) }.take(30),
            "authorDisplayName" to author.displayName.ifBlank { "Orbit Member" }.take(50),
            "authorAvatarUrl" to author.avatarUrl.take(350_000),
            "body" to trimmedBody,
            "topic" to topic.trim().take(40),
            "imageUrls" to safeImages,
            "attachments" to safeAttachments,
            "parentPostId" to parentPostId,
            "rootPostId" to rootPostId,
            "replyToAuthorUsername" to (parentPost?.authorUsername.orEmpty().take(30)),
            "replyToBody" to (parentPost?.body.orEmpty().trim().take(240)),
            "repostOfPostId" to "",
            "repostAuthorUsername" to "",
            "quotedPostId" to (quotedPost?.id.orEmpty().take(128)),
            "quotedAuthorUsername" to (quotedPost?.authorUsername.orEmpty().take(30)),
            "quotedAuthorDisplayName" to (quotedPost?.authorDisplayName.orEmpty().take(50)),
            "quotedAuthorAvatarUrl" to (quotedPost?.authorAvatarUrl.orEmpty().take(350_000)),
            "quotedBody" to (quotedPost?.body.orEmpty().take(500)),
            "quotedImageUrl" to (quotedPost?.imageUrls?.firstOrNull().orEmpty().take(350_000)),
            "audienceMode" to safeAudience,
            "replyMode" to safeReplyMode,
            "allowedReplyUserIds" to safeAllowedReplyUsers,
            "pollOptions" to safePollOptions,
            "pollVotes" to emptyMap<String, Long>(),
            "pollEndsAtMillis" to pollEndsAtMillis,
            "edited" to false,
            "visibility" to "public",
            "likeCount" to 0L,
            "replyCount" to 0L,
            "repostCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            postRef.set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.CREATE, "posts/$postId")
        }

        if (parentPost != null && parentPost.id.isNotBlank()) {
            incrementPostCounter(parentPost.id, "replyCount", 1L)
            if (parentPost.authorId.isNotBlank() && parentPost.authorId != uid) {
                logActivityBestEffort(
                    recipientId = parentPost.authorId,
                    actor = author,
                    type = "reply",
                    postId = parentPost.id,
                    snippet = trimmedBody.ifBlank { "Replied with media" }
                )
            }
        }

        if (quotedPost != null && quotedPost.id.isNotBlank()) {
            runCatching { incrementPostCounter(quotedPost.id, "repostCount", 1L) }
            if (quotedPost.authorId.isNotBlank() && quotedPost.authorId != uid) {
                logActivityBestEffort(
                    recipientId = quotedPost.authorId,
                    actor = author,
                    type = "quote",
                    postId = postId,
                    snippet = trimmedBody.ifBlank { "Quoted your thread" }
                )
            }
        }

        val alreadyNotified = buildSet {
            add(uid)
            parentPost?.authorId?.takeIf { it.isNotBlank() }?.let { add(it) }
            quotedPost?.authorId?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        val targetPostId = parentPost?.id?.ifBlank { postId } ?: postId
        mentionedUserIds
            .asSequence()
            .filter { it.isNotBlank() && it !in alreadyNotified }
            .distinct()
            .take(10)
            .forEach { recipientId ->
                logActivityBestEffort(
                    recipientId = recipientId,
                    actor = author,
                    type = "mention",
                    postId = targetPostId,
                    snippet = trimmedBody.take(180).ifBlank { "Mentioned you in a thread" }
                )
            }
        postId
    }

    suspend fun editPost(post: ThreadPost, newBody: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(post.authorId == uid) { "Only the author can edit this post." }
        val trimmed = newBody.trim().take(500)
        require(
            trimmed.isNotEmpty() ||
                post.imageUrls.isNotEmpty() ||
                post.attachments.isNotEmpty() ||
                post.hasQuotedPost ||
                post.hasPoll
        ) {
            "Post text cannot be empty."
        }
        val path = "posts/${post.id}"
        try {
            postsCollection.document(post.id).update(
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

    suspend fun voteOnPoll(postId: String, optionIndex: Int): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(postId.isNotBlank()) { "Invalid poll post." }
        require(optionIndex in 0..3) { "Invalid poll option." }
        val path = "posts/$postId"
        try {
            postsCollection.document(postId).update(
                FieldPath.of("pollVotes", uid), optionIndex.toLong(),
                FieldPath.of("updatedAt"), FieldValue.serverTimestamp()
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun deletePost(post: ThreadPost): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(post.authorId == uid) { "Only the author can delete this thread." }
        val path = "posts/${post.id}"
        try {
            postsCollection.document(post.id).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.DELETE, path)
        }
        if (post.parentPostId.isNotBlank()) {
            runCatching { incrementPostCounter(post.parentPostId, "replyCount", -1L) }
        }
        OrbitCacheManager.postCache.remove(post.id)
    }

    suspend fun deleteAllMyPosts(): Result<Int> = runCatching {
        val uid = requireUserId()
        var deleted = 0

        val postSnaps = postsCollection
            .whereEqualTo("visibility", "public")
            .get()
            .await()
        for (doc in postSnaps.documents) {
            if (doc.getString("authorId") == uid) {
                runCatching { doc.reference.delete().await() }.onSuccess { deleted++ }
                OrbitCacheManager.postCache.remove(doc.id)
            }
        }

        val spacePostSnaps = communityPostsCollection
            .whereEqualTo("visibility", "public")
            .get()
            .await()
        for (doc in spacePostSnaps.documents) {
            if (doc.getString("authorId") == uid) {
                runCatching { doc.reference.delete().await() }.onSuccess { deleted++ }
            }
        }
        deleted
    }

    suspend fun deleteMyAccount(): Result<Unit> = runCatching {
        val uid = requireUserId()
        runCatching { deleteAllMyPosts() }
        runCatching { privateStateDoc(uid).delete().await() }
        runCatching { profilesCollection.document(uid).delete().await() }
        OrbitCacheManager.clearMemoryCaches()
        runCatching { Firebase.auth.currentUser?.delete()?.await() }
    }

    suspend fun toggleLike(
        post: ThreadPost,
        actor: UserProfile,
        currentlyLiked: Boolean
    ): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val nowLiked = !currentlyLiked
        val delta = if (nowLiked) 1L else -1L
        val privateRef = privateStateDoc(uid)
        val privatePath = "users/$uid/private/state"

        try {
            privateRef.update(
                mapOf(
                    "likedPostIds" to if (nowLiked) FieldValue.arrayUnion(post.id) else FieldValue.arrayRemove(post.id),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, privatePath)
        }

        incrementPostCounter(post.id, "likeCount", delta)

        if (nowLiked && post.authorId.isNotBlank() && post.authorId != uid) {
            logActivityBestEffort(
                recipientId = post.authorId,
                actor = actor,
                type = "like",
                postId = post.id,
                snippet = post.body.take(120)
            )
        }
        nowLiked
    }

    suspend fun toggleRepost(
        post: ThreadPost,
        actor: UserProfile,
        currentlyReposted: Boolean
    ): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val nowReposted = !currentlyReposted
        val delta = if (nowReposted) 1L else -1L
        val privateRef = privateStateDoc(uid)
        val privatePath = "users/$uid/private/state"

        try {
            privateRef.update(
                mapOf(
                    "repostedPostIds" to if (nowReposted) FieldValue.arrayUnion(post.id) else FieldValue.arrayRemove(post.id),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, privatePath)
        }

        incrementPostCounter(post.id, "repostCount", delta)

        if (nowReposted && post.authorId.isNotBlank() && post.authorId != uid) {
            logActivityBestEffort(
                recipientId = post.authorId,
                actor = actor,
                type = "repost",
                postId = post.id,
                snippet = post.body.take(120)
            )
        }
        nowReposted
    }

    suspend fun toggleFollow(
        targetUserId: String,
        actor: UserProfile,
        currentlyFollowing: Boolean
    ): Result<Boolean> = runCatching {
        val uid = requireUserId()
        require(targetUserId.isNotBlank() && targetUserId != uid) { "Cannot follow yourself." }
        val nowFollowing = !currentlyFollowing
        val delta = if (nowFollowing) 1L else -1L

        val privateRef = privateStateDoc(uid)
        val privatePath = "users/$uid/private/state"
        try {
            privateRef.update(
                mapOf(
                    "followingIds" to if (nowFollowing) FieldValue.arrayUnion(targetUserId) else FieldValue.arrayRemove(targetUserId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, privatePath)
        }

        incrementProfileCounter(uid, "followingCount", delta)
        incrementProfileCounter(targetUserId, "followerCount", delta)

        if (nowFollowing) {
            logActivityBestEffort(
                recipientId = targetUserId,
                actor = actor,
                type = "follow",
                postId = "",
                snippet = "Started following you"
            )
        }
        nowFollowing
    }

    suspend fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        linkUrl: String,
        extraLinks: List<String> = emptyList(),
        avatarUrl: String,
        bannerUrl: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeDisplayName = displayName.trim().take(50).ifBlank { "Orbit Member" }
        val safeUsername = sanitizeUsername(username, uid).take(30)
        val safeBio = bio.trim().take(240)
        val safeLink = linkUrl.trim().take(300)
        val safeExtraLinks = extraLinks
            .map { it.trim().take(300) }
            .filter { it.isNotBlank() }
            .take(3)
        val safeAvatar = avatarUrl.take(350_000)
        val safeBanner = bannerUrl.take(450_000)
        val profileRef = profilesCollection.document(uid)
        val path = "profiles/$uid"

        val existingSnap = try {
            profileRef.get().await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.GET, path)
        }

        try {
            if (isCurrentSchemaProfile(existingSnap, uid)) {
                profileRef.update(
                    mapOf(
                        "displayName" to safeDisplayName,
                        "username" to safeUsername,
                        "bio" to safeBio,
                        "linkUrl" to safeLink,
                        "extraLinks" to safeExtraLinks,
                        "avatarUrl" to safeAvatar,
                        "bannerUrl" to safeBanner,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val existingBadges = com.example.data.model.OrbitProfileBadge.normalizeIds(
                    (existingSnap.get("profileBadges") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                )
                val existingPrivate = runCatching { existingSnap.getBoolean("privateAccount") }.getOrNull() ?: false
                profileRef.set(
                    mapOf(
                        "userId" to uid,
                        "username" to safeUsername,
                        "displayName" to safeDisplayName,
                        "bio" to safeBio,
                        "avatarUrl" to safeAvatar,
                        "bannerUrl" to safeBanner,
                        "linkUrl" to safeLink,
                        "extraLinks" to safeExtraLinks,
                        "savedLinks" to emptyList<Map<String, Any>>(),
                        "profileBadges" to existingBadges,
                        "privateAccount" to existingPrivate,
                        "visibility" to "public",
                        "followerCount" to (existingSnap.safeLong("followerCount") ?: 0L).coerceAtLeast(0L),
                        "followingCount" to (existingSnap.safeLong("followingCount") ?: 0L).coerceAtLeast(0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun updateProfileBadges(badges: List<String>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val path = "profiles/$uid"
        val normalized = com.example.data.model.OrbitProfileBadge.normalizeIds(badges)
        try {
            profilesCollection.document(uid).update(
                mapOf(
                    "profileBadges" to normalized,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    private fun serializeProfileLinks(links: List<ProfileLink>): List<Map<String, Any>> {
        return links.take(25).mapIndexed { index, link ->
            mapOf(
                "id" to link.id.ifBlank { "lnk_${UUID.randomUUID().toString().take(8)}" }.take(64),
                "title" to link.title.ifBlank { "Link" }.take(80),
                "url" to link.url.take(500),
                "thumbnailUrl" to link.resolvedThumbnailUrl.take(500),
                "createdAtMillis" to link.createdAtMillis,
                "pinned" to link.pinned,
                "sortOrder" to index
            )
        }
    }

    suspend fun updateProfileLinks(updatedLinks: List<ProfileLink>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val path = "profiles/$uid"
        val serialized = serializeProfileLinks(updatedLinks)
        try {
            profilesCollection.document(uid).update(
                mapOf(
                    "savedLinks" to serialized,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun addProfileLink(
        currentLinks: List<ProfileLink>,
        title: String,
        rawUrl: String
    ): Result<Unit> = runCatching {
        val normalizedUrl = normalizeWebUrl(rawUrl).take(500)
        require(normalizedUrl.isNotBlank()) { "Please enter a valid link URL." }
        val safeTitle = title.trim().take(80).ifBlank { normalizedUrl.removePrefix("https://").take(40) }
        val newLink = ProfileLink(
            id = "lnk_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            title = safeTitle,
            url = normalizedUrl,
            thumbnailUrl = buildLinkThumbnailUrl(normalizedUrl).take(500),
            createdAtMillis = System.currentTimeMillis(),
            pinned = false,
            sortOrder = currentLinks.size
        )
        val pinned = currentLinks.filter { it.pinned }
        val unpinned = currentLinks.filterNot { it.pinned }
        updateProfileLinks(pinned + listOf(newLink) + unpinned).getOrThrow()
    }

    suspend fun removeProfileLink(
        currentLinks: List<ProfileLink>,
        linkId: String
    ): Result<Unit> = updateProfileLinks(currentLinks.filterNot { it.id == linkId })

    // ==================== SAVED POSTS & PRIVACY CONTROLS ====================

    suspend fun toggleSavedPost(postId: String, currentlySaved: Boolean): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val nowSaved = !currentlySaved
        val path = "users/$uid/private/state"
        try {
            privateStateDoc(uid).update(
                mapOf(
                    "savedPostIds" to if (nowSaved) FieldValue.arrayUnion(postId) else FieldValue.arrayRemove(postId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
        nowSaved
    }

    suspend fun setPrivateAccount(enabled: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val path = "profiles/$uid"
        try {
            profilesCollection.document(uid).update(
                mapOf(
                    "privateAccount" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun toggleBlockUser(targetUserId: String, currentlyBlocked: Boolean): Result<Boolean> = runCatching {
        val uid = requireUserId()
        require(targetUserId.isNotBlank() && targetUserId != uid) { "Cannot block yourself." }
        val nowBlocked = !currentlyBlocked
        val path = "users/$uid/private/state"
        try {
            privateStateDoc(uid).update(
                mapOf(
                    "blockedUserIds" to if (nowBlocked) FieldValue.arrayUnion(targetUserId) else FieldValue.arrayRemove(targetUserId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
        nowBlocked
    }

    suspend fun toggleMuteUser(targetUserId: String, currentlyMuted: Boolean): Result<Boolean> = runCatching {
        val uid = requireUserId()
        require(targetUserId.isNotBlank() && targetUserId != uid) { "Cannot mute yourself." }
        val nowMuted = !currentlyMuted
        val path = "users/$uid/private/state"
        try {
            privateStateDoc(uid).update(
                mapOf(
                    "mutedUserIds" to if (nowMuted) FieldValue.arrayUnion(targetUserId) else FieldValue.arrayRemove(targetUserId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
        nowMuted
    }

    suspend fun updateMutedWords(words: List<String>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleaned = words.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().take(200)
        val path = "users/$uid/private/state"
        try {
            privateStateDoc(uid).update(
                mapOf(
                    "mutedWords" to cleaned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    // ==================== PROFILE MEDIA ALBUMS ====================

    fun observeUserAlbums(userId: String): Flow<List<ProfileAlbum>> = callbackFlow {
        val path = "profiles/$userId/albums"
        val registration = profilesCollection
            .document(userId)
            .collection("albums")
            .whereEqualTo("visibility", "public")
            .limit(50L)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.LIST, path) }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { doc ->
                        runCatching {
                            doc.toObject(ProfileAlbum::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull()
                    }
                    ?.sortedByDescending { it.createdAtMillis }
                    .orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    fun observeAlbumItems(userId: String, albumId: String): Flow<List<AlbumMediaItem>> = callbackFlow {
        val path = "profiles/$userId/albums/$albumId/items"
        val registration = profilesCollection
            .document(userId)
            .collection("albums")
            .document(albumId)
            .collection("items")
            .whereEqualTo("visibility", "public")
            .limit(200L)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.LIST, path) }
                        .onFailure { close(it) }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { doc ->
                        runCatching {
                            doc.toObject(AlbumMediaItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull()
                    }
                    ?.sortedWith(compareBy<AlbumMediaItem> { it.sortOrder }.thenBy { it.createdAtMillis })
                    .orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun createAlbum(
        owner: UserProfile,
        title: String,
        coverUrl: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val safeTitle = title.trim().take(80)
        require(safeTitle.isNotEmpty()) { "Enter an album name." }
        val albumId = "alb_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "profiles/$uid/albums/$albumId"
        val payload = mapOf(
            "ownerId" to uid,
            "ownerUsername" to owner.username.ifBlank { sanitizeUsername(owner.displayName, uid) }.take(30),
            "title" to safeTitle,
            "coverUrl" to coverUrl.take(450_000),
            "itemCount" to 0L,
            "visibility" to "public",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            profilesCollection.document(uid).collection("albums").document(albumId).set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.CREATE, path)
        }
        albumId
    }

    suspend fun addItemsToAlbum(
        albumId: String,
        existingCount: Int,
        existingCoverUrl: String,
        items: List<Triple<String, String, String>> // (mediaUrl, mimeType, fileName)
    ): Result<Int> = runCatching {
        val uid = requireUserId()
        val albumRef = profilesCollection.document(uid).collection("albums").document(albumId)
        val itemsCol = albumRef.collection("items")
        var added = 0
        var firstImageCover = existingCoverUrl

        for ((idx, triple) in items.withIndex()) {
            val (mediaUrl, mimeType, fileName) = triple
            if (mediaUrl.isBlank()) continue
            val isVideo = mimeType.startsWith("video/", ignoreCase = true)
            val mediaType = if (isVideo) "video" else "image"
            val itemId = "itm_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val itemPayload = mapOf(
                "ownerId" to uid,
                "mediaUrl" to mediaUrl.take(450_000),
                "mimeType" to mimeType.take(120).ifBlank { if (isVideo) "video/mp4" else "image/jpeg" },
                "mediaType" to mediaType,
                "fileName" to fileName.take(160),
                "sortOrder" to (existingCount + idx).coerceIn(0, 100_000),
                "visibility" to "public",
                "createdAt" to FieldValue.serverTimestamp()
            )
            try {
                itemsCol.document(itemId).set(itemPayload).await()
                added++
                if (firstImageCover.isBlank() && !isVideo) {
                    firstImageCover = mediaUrl
                }
            } catch (e: Exception) {
                if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
                handleFirestoreError(e, OperationType.CREATE, "profiles/$uid/albums/$albumId/items/$itemId")
            }
        }

        if (added > 0) {
            runCatching {
                albumRef.update(
                    mapOf(
                        "itemCount" to (existingCount + added).toLong().coerceIn(0L, 500L),
                        "coverUrl" to firstImageCover.take(450_000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        added
    }

    suspend fun removeAlbumItems(
        albumId: String,
        itemIdsToRemove: Set<String>,
        remainingItems: List<AlbumMediaItem>,
        currentCoverUrl: String
    ): Result<Int> = runCatching {
        val uid = requireUserId()
        val albumRef = profilesCollection.document(uid).collection("albums").document(albumId)
        val itemsCol = albumRef.collection("items")
        var removed = 0
        for (itemId in itemIdsToRemove) {
            runCatching { itemsCol.document(itemId).delete().await() }.onSuccess { removed++ }
        }
        val nextCover = if (remainingItems.any { it.mediaUrl == currentCoverUrl }) {
            currentCoverUrl
        } else {
            remainingItems.firstOrNull { !it.isVideo }?.mediaUrl.orEmpty()
        }
        runCatching {
            albumRef.update(
                mapOf(
                    "itemCount" to remainingItems.size.toLong().coerceIn(0L, 500L),
                    "coverUrl" to nextCover.take(450_000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        removed
    }

    suspend fun setAlbumCover(albumId: String, coverUrl: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val path = "profiles/$uid/albums/$albumId"
        try {
            profilesCollection.document(uid).collection("albums").document(albumId).update(
                mapOf(
                    "coverUrl" to coverUrl.take(450_000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun reorderAlbumItems(albumId: String, orderedItems: List<AlbumMediaItem>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val itemsCol = profilesCollection.document(uid).collection("albums").document(albumId).collection("items")
        val batch = db.batch()
        orderedItems.forEachIndexed { idx, item ->
            if (item.id.isNotBlank()) {
                batch.update(itemsCol.document(item.id), mapOf("sortOrder" to idx))
            }
        }
        try {
            batch.commit().await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, "profiles/$uid/albums/$albumId/items")
        }
    }

    suspend fun deleteAlbum(albumId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val albumRef = profilesCollection.document(uid).collection("albums").document(albumId)
        val itemSnaps = runCatching { albumRef.collection("items").get().await() }.getOrNull()
        itemSnaps?.documents?.forEach { doc ->
            runCatching { doc.reference.delete().await() }
        }
        try {
            albumRef.delete().await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.DELETE, "profiles/$uid/albums/$albumId")
        }
    }

    // ==================== APP RELEASES & DRIVE LINK ====================

    suspend fun saveDriveDownloadUrl(
        driveDownloadUrl: String,
        versionCode: Long,
        versionName: String,
        releaseNotes: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeUrl = normalizeWebUrl(driveDownloadUrl).take(500)
        val payload = mapOf(
            "versionCode" to versionCode.coerceIn(1L, 100_000L),
            "versionName" to versionName.trim().take(30).ifBlank { BuildConfig.VERSION_NAME },
            "releaseNotes" to releaseNotes.trim().take(500),
            "driveDownloadUrl" to safeUrl,
            "publishedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            appReleasesCollection.document("latest").set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, "appReleases/latest")
        }
    }

    suspend fun publishAppUpdateAnnouncement(
        actor: UserProfile,
        versionCode: Long,
        versionName: String,
        releaseNotes: String,
        driveDownloadUrl: String = "",
        recipientUserIds: List<String>
    ): Result<Int> = runCatching {
        val uid = requireUserId()
        val safeVersion = versionName.trim().take(30).ifBlank { BuildConfig.VERSION_NAME }
        val safeNotes = releaseNotes.trim().take(500)
        val safeDriveUrl = normalizeWebUrl(driveDownloadUrl).take(500)
        val payload = mapOf(
            "versionCode" to versionCode.coerceIn(1L, 100_000L),
            "versionName" to safeVersion,
            "releaseNotes" to safeNotes,
            "driveDownloadUrl" to safeDriveUrl,
            "publishedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            appReleasesCollection.document("latest").set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, "appReleases/latest")
        }

        var notified = 0
        val snippet = buildString {
            append("Orbit v$safeVersion is now available! ")
            if (safeNotes.isNotBlank()) append(safeNotes) else append("Tap to update the app.")
        }.take(240)

        for (recipientId in recipientUserIds.distinct().take(100)) {
            if (recipientId.isBlank()) continue
            val activityId = "upd_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val actPayload = mapOf(
                "recipientId" to recipientId,
                "actorId" to uid,
                "actorUsername" to actor.username.ifBlank { "orbit" }.take(30),
                "actorAvatarUrl" to actor.avatarUrl.take(350_000),
                "type" to "app_update",
                "postId" to "",
                "spaceId" to "",
                "snippet" to snippet,
                "createdAt" to FieldValue.serverTimestamp()
            )
            runCatching {
                activitiesCollection.document(activityId).set(actPayload).await()
            }.onSuccess { notified++ }
        }
        notified
    }

    private suspend fun incrementPostCounter(postId: String, field: String, delta: Long) {
        val docRef = postsCollection.document(postId)
        val path = "posts/$postId"
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    val current = snap.getLong(field) ?: 0L
                    val next = (current + delta).coerceAtLeast(0L)
                    tx.update(
                        docRef,
                        mapOf(
                            field to next,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    private suspend fun incrementProfileCounter(userId: String, field: String, delta: Long) {
        val docRef = profilesCollection.document(userId)
        val path = "profiles/$userId"
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists() && snap.getString("visibility") == "public") {
                    val current = snap.getLong(field) ?: 0L
                    val next = (current + delta).coerceAtLeast(0L)
                    tx.update(
                        docRef,
                        mapOf(
                            field to next,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        } catch (e: Exception) {
            if (e is CancellationException && unwrapFirestoreException(e) == null) throw e
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    private suspend fun logActivityBestEffort(
        recipientId: String,
        actor: UserProfile,
        type: String,
        postId: String,
        snippet: String,
        spaceId: String = ""
    ) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        if (recipientId.isBlank() || recipientId == uid) return
        val activityId = "act_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val payload = mapOf(
            "recipientId" to recipientId,
            "actorId" to uid,
            "actorUsername" to actor.username.ifBlank { sanitizeUsername(actor.displayName, uid) }.take(30),
            "actorAvatarUrl" to actor.avatarUrl.take(350_000),
            "type" to type,
            "postId" to postId.take(128),
            "spaceId" to spaceId.take(128),
            "snippet" to snippet.take(240),
            "createdAt" to FieldValue.serverTimestamp()
        )
        runCatching {
            activitiesCollection.document(activityId).set(payload).await()
        }.onFailure { err ->
            Log.w("ThreadsRepository", "Activity write failed: ${err.message}")
        }
    }
}
