package com.example.data.cache

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.PostAttachment
import com.example.data.model.ThreadPost
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Date

private val Context.orbitFeedCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "orbit_feed_cache_datastore"
)

/**
 * Identifies which layer of the multi-layer feed cache supplied the active feed snapshot.
 */
enum class FeedDataSource {
    NONE,
    MEMORY_L1,
    DATASTORE_L2,
    NETWORK_L3
}

data class CachedFeedSnapshot(
    val posts: List<ThreadPost>,
    val cachedAtMillis: Long,
    val ownerUid: String,
    val source: FeedDataSource,
    val approxSizeBytes: Long = 0L
) {
    fun isStale(nowMillis: Long = System.currentTimeMillis(), softTtlMs: Long = FeedCacheRepository.SOFT_TTL_MS): Boolean {
        if (cachedAtMillis <= 0L) return true
        return (nowMillis - cachedAtMillis) >= softTtlMs
    }
}

data class FeedCacheMetadata(
    val cachedPostCount: Int = 0,
    val cachedAtMillis: Long = 0L,
    val ownerUid: String = "",
    val approxSizeBytes: Long = 0L
)

data class FeedCacheStatus(
    val source: FeedDataSource = FeedDataSource.NONE,
    val cachedPostCount: Int = 0,
    val lastSyncedAtMillis: Long = 0L,
    val isOffline: Boolean = false,
    val approxSizeBytes: Long = 0L
)

/**
 * Multi-layer feed caching repository backing Orbit's home feed for instant cold starts and
 * full offline viewing:
 *
 * - **Layer 1 (L1 — Hot Memory)**: Ordered in-memory feed snapshot + `OrbitCacheManager.postCache`
 *   LRU cache for sub-millisecond reads, tab switches, and zero-flicker screen restores.
 * - **Layer 2 (L2 — Persistent Jetpack DataStore)**: Survives process death and offline launches,
 *   persisting recent post snapshots with account isolation, schema versioning, TTL expiry, and
 *   payload budgeting so inline base64 payloads never bloat disk storage.
 * - **Layer 3 (L3 — Firestore Remote)**: Live remote source of truth that writes through to L1 & L2.
 */
class FeedCacheRepository internal constructor(
    private val dataStore: DataStore<Preferences>
) {

    constructor(context: Context) : this(context.applicationContext.orbitFeedCacheDataStore)

    private object Keys {
        val POSTS_JSON = stringPreferencesKey("cached_feed_posts_json")
        val CACHED_AT_MILLIS = longPreferencesKey("cached_feed_at_millis")
        val OWNER_UID = stringPreferencesKey("cached_feed_owner_uid")
        val SCHEMA_VERSION = intPreferencesKey("cached_feed_schema_version")
        val POST_COUNT = intPreferencesKey("cached_feed_post_count")
        val PAYLOAD_BYTES = longPreferencesKey("cached_feed_payload_bytes")
    }

    private val writeMutex = Mutex()

    @Volatile
    private var l1MemorySnapshot: CachedFeedSnapshot? = null

    /**
     * Observes persisted L2 DataStore cache metadata (count, last sync timestamp, size) for UI & Settings.
     */
    fun observeCacheMetadata(ownerUid: String): Flow<FeedCacheMetadata> = dataStore.data
        .catch { err ->
            if (err is IOException) emit(emptyPreferences()) else throw err
        }
        .map { prefs ->
            val storedUid = prefs[Keys.OWNER_UID].orEmpty()
            val schema = prefs[Keys.SCHEMA_VERSION] ?: 0
            if (schema != SCHEMA_VERSION || (ownerUid.isNotBlank() && storedUid.isNotBlank() && storedUid != ownerUid)) {
                FeedCacheMetadata()
            } else {
                FeedCacheMetadata(
                    cachedPostCount = prefs[Keys.POST_COUNT] ?: 0,
                    cachedAtMillis = prefs[Keys.CACHED_AT_MILLIS] ?: 0L,
                    ownerUid = storedUid,
                    approxSizeBytes = prefs[Keys.PAYLOAD_BYTES] ?: 0L
                )
            }
        }

    /**
     * Synchronously peeks at the L1 hot memory snapshot if it belongs to [ownerUid] and hasn't expired.
     */
    fun peekMemoryFeed(
        ownerUid: String,
        nowMillis: Long = System.currentTimeMillis()
    ): CachedFeedSnapshot? {
        val snap = l1MemorySnapshot ?: return null
        if (ownerUid.isNotBlank() && snap.ownerUid.isNotBlank() && snap.ownerUid != ownerUid) {
            return null
        }
        if (snap.posts.isEmpty()) return null
        if (snap.cachedAtMillis > 0L && (nowMillis - snap.cachedAtMillis) > HARD_TTL_MS) {
            l1MemorySnapshot = null
            return null
        }
        return snap.copy(source = FeedDataSource.MEMORY_L1)
    }

    /**
     * Loads the best available cached feed snapshot using the L1 -> L2 hierarchy:
     * 1. Checks L1 hot in-memory snapshot first.
     * 2. On L1 miss, reads L2 DataStore from disk, validates owner UID + schema + TTL,
     *    promotes the posts into L1 + `OrbitCacheManager.postCache`, and returns the snapshot.
     */
    suspend fun loadCachedFeed(
        ownerUid: String,
        nowMillis: Long = System.currentTimeMillis()
    ): CachedFeedSnapshot? {
        peekMemoryFeed(ownerUid, nowMillis)?.let { return it }

        return withContext(Dispatchers.IO) {
            val prefs = dataStore.data
                .catch { err ->
                    if (err is IOException) emit(emptyPreferences()) else throw err
                }
                .first()

            val schema = prefs[Keys.SCHEMA_VERSION] ?: 0
            val storedUid = prefs[Keys.OWNER_UID].orEmpty()
            val cachedAt = prefs[Keys.CACHED_AT_MILLIS] ?: 0L
            val rawJson = prefs[Keys.POSTS_JSON].orEmpty()

            if (schema != SCHEMA_VERSION || rawJson.isBlank()) {
                return@withContext null
            }
            if (ownerUid.isNotBlank() && storedUid.isNotBlank() && storedUid != ownerUid) {
                return@withContext null
            }
            if (cachedAt > 0L && (nowMillis - cachedAt) > HARD_TTL_MS) {
                clearDataStoreOnly()
                return@withContext null
            }

            val decodedPosts = ThreadPostCacheSerializer.decodeList(rawJson)
            if (decodedPosts.isEmpty()) return@withContext null

            val payloadBytes = prefs[Keys.PAYLOAD_BYTES] ?: rawJson.toByteArray(Charsets.UTF_8).size.toLong()
            val promoted = CachedFeedSnapshot(
                posts = decodedPosts,
                cachedAtMillis = cachedAt,
                ownerUid = storedUid.ifBlank { ownerUid },
                source = FeedDataSource.DATASTORE_L2,
                approxSizeBytes = payloadBytes
            )
            l1MemorySnapshot = promoted.copy(source = FeedDataSource.MEMORY_L1)
            OrbitCacheManager.cachePosts(decodedPosts)
            promoted
        }
    }

    /**
     * Write-through update from L3 (Firestore): immediately refreshes L1 hot memory and persists
     * a budgeted snapshot of up to [MAX_PERSISTED_POSTS] recent posts into L2 DataStore.
     */
    suspend fun saveFeedSnapshot(
        ownerUid: String,
        posts: List<ThreadPost>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val sorted = posts
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAtMillis }

        // Layer 1: Always update hot memory immediately
        OrbitCacheManager.cachePosts(sorted)
        val persistedSubset = sorted.take(MAX_PERSISTED_POSTS)

        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val encodedJson = ThreadPostCacheSerializer.encodeList(
                    posts = persistedSubset,
                    maxTotalBytes = MAX_DATASTORE_BYTES
                )
                val sizeBytes = encodedJson.toByteArray(Charsets.UTF_8).size.toLong()
                l1MemorySnapshot = CachedFeedSnapshot(
                    posts = sorted,
                    cachedAtMillis = nowMillis,
                    ownerUid = ownerUid,
                    source = FeedDataSource.MEMORY_L1,
                    approxSizeBytes = sizeBytes
                )
                runCatching {
                    dataStore.edit { prefs ->
                        prefs[Keys.SCHEMA_VERSION] = SCHEMA_VERSION
                        prefs[Keys.OWNER_UID] = ownerUid
                        prefs[Keys.CACHED_AT_MILLIS] = nowMillis
                        prefs[Keys.POSTS_JSON] = encodedJson
                        prefs[Keys.POST_COUNT] = persistedSubset.size
                        prefs[Keys.PAYLOAD_BYTES] = sizeBytes
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Failed to persist feed cache to DataStore: ${err.message}")
                }
            }
        }
    }

    /**
     * Upserts a single post across L1 and L2 so newly created or edited posts appear immediately
     * even if the user loses connectivity before the next full feed sync.
     */
    suspend fun upsertPost(
        ownerUid: String,
        post: ThreadPost,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (post.id.isBlank()) return
        val existing = loadCachedFeed(ownerUid, nowMillis)?.posts.orEmpty()
        val updated = (listOf(post) + existing.filterNot { it.id == post.id })
            .sortedByDescending { it.createdAtMillis }
        saveFeedSnapshot(ownerUid = ownerUid, posts = updated, nowMillis = nowMillis)
    }

    /**
     * Removes a deleted post from L1 and L2 so deleted content never reappears offline.
     */
    suspend fun removePost(
        ownerUid: String,
        postId: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (postId.isBlank()) return
        OrbitCacheManager.postCache.remove(postId)
        val existing = loadCachedFeed(ownerUid, nowMillis)?.posts ?: return
        val filtered = existing.filterNot { it.id == postId }
        if (filtered.size != existing.size) {
            saveFeedSnapshot(ownerUid = ownerUid, posts = filtered, nowMillis = nowMillis)
        }
    }

    /**
     * Clears both L1 memory cache and L2 DataStore persisted feed snapshot.
     */
    suspend fun clearAll() {
        l1MemorySnapshot = null
        OrbitCacheManager.postCache.evictAll()
        clearDataStoreOnly()
    }

    /**
     * Clears only the in-memory L1 snapshot (useful for testing L2 DataStore cold-start hydration).
     */
    fun clearMemoryLayerOnly() {
        l1MemorySnapshot = null
        OrbitCacheManager.postCache.evictAll()
    }

    private suspend fun clearDataStoreOnly() {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                runCatching {
                    dataStore.edit { prefs ->
                        prefs.remove(Keys.POSTS_JSON)
                        prefs.remove(Keys.CACHED_AT_MILLIS)
                        prefs.remove(Keys.OWNER_UID)
                        prefs.remove(Keys.SCHEMA_VERSION)
                        prefs.remove(Keys.POST_COUNT)
                        prefs.remove(Keys.PAYLOAD_BYTES)
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Failed to clear feed cache DataStore: ${err.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "FeedCacheRepository"
        const val SCHEMA_VERSION = 1
        const val MAX_PERSISTED_POSTS = 60
        const val MAX_DATASTORE_BYTES = 1_600_000 // ~1.6 MB cap to keep DataStore fast & lightweight
        const val MAX_INLINE_DATA_URI_CHARS = 90_000
        const val SOFT_TTL_MS = 10L * 60L * 1000L // 10 minutes
        const val HARD_TTL_MS = 7L * 24L * 60L * 60L * 1000L // 7 days
    }
}

/**
 * Dependency-free, schema-resilient JSON serializer for [ThreadPost] snapshots persisted in DataStore.
 * Budgets oversized inline base64 blobs while preserving remote URLs, compact preview thumbnails,
 * polls, quoted posts, replies, attachments metadata, and timestamps.
 */
object ThreadPostCacheSerializer {

    fun encodeList(
        posts: List<ThreadPost>,
        maxTotalBytes: Int = FeedCacheRepository.MAX_DATASTORE_BYTES
    ): String {
        val array = JSONArray()
        var runningBytes = 2 // "[]"
        for (post in posts) {
            val obj = toJson(post)
            val entryString = obj.toString()
            val entryBytes = entryString.toByteArray(Charsets.UTF_8).size + 1
            if (array.length() > 0 && runningBytes + entryBytes > maxTotalBytes) {
                // Retry a lightweight text-only copy of the post so recent posts still fit offline
                val compactObj = toJson(post, stripInlineMediaBlobs = true)
                val compactBytes = compactObj.toString().toByteArray(Charsets.UTF_8).size + 1
                if (runningBytes + compactBytes > maxTotalBytes) {
                    break
                }
                array.put(compactObj)
                runningBytes += compactBytes
            } else {
                array.put(obj)
                runningBytes += entryBytes
            }
        }
        return array.toString()
    }

    fun decodeList(rawJson: String): List<ThreadPost> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val post = fromJson(obj)
                    if (post.id.isNotBlank()) {
                        add(post)
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun sanitizePersistableUri(uri: String, stripInlineMediaBlobs: Boolean): String {
        if (uri.isBlank()) return ""
        if (uri.startsWith("data:", ignoreCase = true)) {
            if (stripInlineMediaBlobs) return ""
            // Strip #orbit_hd2k= only if oversized, otherwise keep compact preview + hd2k link
            return if (uri.length <= FeedCacheRepository.MAX_INLINE_DATA_URI_CHARS) {
                uri
            } else {
                val hd2k = OrbitCacheManager.extractHd2kUrl(uri)
                hd2k.orEmpty()
            }
        }
        return uri
    }

    private fun toJson(post: ThreadPost, stripInlineMediaBlobs: Boolean = false): JSONObject {
        val obj = JSONObject()
        obj.put("id", post.id)
        obj.put("authorId", post.authorId)
        obj.put("authorUsername", post.authorUsername)
        obj.put("authorDisplayName", post.authorDisplayName)
        obj.put("authorAvatarUrl", sanitizePersistableUri(post.authorAvatarUrl, stripInlineMediaBlobs))
        obj.put("body", post.body)
        obj.put("topic", post.topic)

        val imagesArray = JSONArray()
        post.imageUrls.forEach { raw ->
            val safe = sanitizePersistableUri(raw, stripInlineMediaBlobs)
            if (safe.isNotBlank()) imagesArray.put(safe)
        }
        obj.put("imageUrls", imagesArray)

        val attachmentsArray = JSONArray()
        post.attachments.forEach { att ->
            val attObj = JSONObject()
            attObj.put("fileName", att.fileName)
            attObj.put("mimeType", att.mimeType)
            attObj.put("sizeBytes", att.sizeBytes)
            attObj.put("dataUri", sanitizePersistableUri(att.dataUri, stripInlineMediaBlobs))
            attachmentsArray.put(attObj)
        }
        obj.put("attachments", attachmentsArray)

        obj.put("parentPostId", post.parentPostId)
        obj.put("rootPostId", post.rootPostId)
        obj.put("replyToAuthorUsername", post.replyToAuthorUsername)
        obj.put("replyToBody", post.replyToBody)
        obj.put("repostOfPostId", post.repostOfPostId)
        obj.put("repostAuthorUsername", post.repostAuthorUsername)
        obj.put("quotedPostId", post.quotedPostId)
        obj.put("quotedAuthorUsername", post.quotedAuthorUsername)
        obj.put("quotedAuthorDisplayName", post.quotedAuthorDisplayName)
        obj.put("quotedAuthorAvatarUrl", sanitizePersistableUri(post.quotedAuthorAvatarUrl, stripInlineMediaBlobs))
        obj.put("quotedBody", post.quotedBody)
        obj.put("quotedImageUrl", sanitizePersistableUri(post.quotedImageUrl, stripInlineMediaBlobs))
        obj.put("audienceMode", post.audienceMode)
        obj.put("replyMode", post.replyMode)

        val allowedReplyArr = JSONArray()
        post.allowedReplyUserIds.forEach { allowedReplyArr.put(it) }
        obj.put("allowedReplyUserIds", allowedReplyArr)

        val pollOptsArr = JSONArray()
        post.pollOptions.forEach { pollOptsArr.put(it) }
        obj.put("pollOptions", pollOptsArr)

        val pollVotesObj = JSONObject()
        post.pollVotes.forEach { (uid, choice) -> pollVotesObj.put(uid, choice) }
        obj.put("pollVotes", pollVotesObj)

        obj.put("pollEndsAtMillis", post.pollEndsAtMillis)
        obj.put("edited", post.edited)
        obj.put("visibility", post.visibility)
        obj.put("likeCount", post.likeCount)
        obj.put("replyCount", post.replyCount)
        obj.put("repostCount", post.repostCount)
        obj.put("createdAtMillis", post.createdAt?.toDate()?.time ?: 0L)
        obj.put("updatedAtMillis", post.updatedAt?.toDate()?.time ?: 0L)
        return obj
    }

    private fun fromJson(obj: JSONObject): ThreadPost {
        val imageUrls = mutableListOf<String>()
        val imgArr = obj.optJSONArray("imageUrls")
        if (imgArr != null) {
            for (i in 0 until imgArr.length()) {
                val u = imgArr.optString(i, "")
                if (u.isNotBlank()) imageUrls.add(u)
            }
        }

        val attachments = mutableListOf<PostAttachment>()
        val attArr = obj.optJSONArray("attachments")
        if (attArr != null) {
            for (i in 0 until attArr.length()) {
                val a = attArr.optJSONObject(i) ?: continue
                attachments.add(
                    PostAttachment(
                        fileName = a.optString("fileName", ""),
                        mimeType = a.optString("mimeType", "application/octet-stream"),
                        sizeBytes = a.optLong("sizeBytes", 0L),
                        dataUri = a.optString("dataUri", "")
                    )
                )
            }
        }

        val allowedReplyUserIds = mutableListOf<String>()
        val allowedArr = obj.optJSONArray("allowedReplyUserIds")
        if (allowedArr != null) {
            for (i in 0 until allowedArr.length()) {
                val id = allowedArr.optString(i, "")
                if (id.isNotBlank()) allowedReplyUserIds.add(id)
            }
        }

        val pollOptions = mutableListOf<String>()
        val pollArr = obj.optJSONArray("pollOptions")
        if (pollArr != null) {
            for (i in 0 until pollArr.length()) {
                pollOptions.add(pollArr.optString(i, ""))
            }
        }

        val pollVotes = mutableMapOf<String, Long>()
        val votesObj = obj.optJSONObject("pollVotes")
        if (votesObj != null) {
            val keys = votesObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                pollVotes[k] = votesObj.optLong(k, 0L)
            }
        }

        val createdMs = obj.optLong("createdAtMillis", 0L)
        val updatedMs = obj.optLong("updatedAtMillis", 0L)

        return ThreadPost(
            id = obj.optString("id", ""),
            authorId = obj.optString("authorId", ""),
            authorUsername = obj.optString("authorUsername", ""),
            authorDisplayName = obj.optString("authorDisplayName", ""),
            authorAvatarUrl = obj.optString("authorAvatarUrl", ""),
            body = obj.optString("body", ""),
            topic = obj.optString("topic", ""),
            imageUrls = imageUrls,
            attachments = attachments,
            parentPostId = obj.optString("parentPostId", ""),
            rootPostId = obj.optString("rootPostId", ""),
            replyToAuthorUsername = obj.optString("replyToAuthorUsername", ""),
            replyToBody = obj.optString("replyToBody", ""),
            repostOfPostId = obj.optString("repostOfPostId", ""),
            repostAuthorUsername = obj.optString("repostAuthorUsername", ""),
            quotedPostId = obj.optString("quotedPostId", ""),
            quotedAuthorUsername = obj.optString("quotedAuthorUsername", ""),
            quotedAuthorDisplayName = obj.optString("quotedAuthorDisplayName", ""),
            quotedAuthorAvatarUrl = obj.optString("quotedAuthorAvatarUrl", ""),
            quotedBody = obj.optString("quotedBody", ""),
            quotedImageUrl = obj.optString("quotedImageUrl", ""),
            audienceMode = obj.optString("audienceMode", "everyone"),
            replyMode = obj.optString("replyMode", "everyone"),
            allowedReplyUserIds = allowedReplyUserIds,
            pollOptions = pollOptions,
            pollVotes = pollVotes,
            pollEndsAtMillis = obj.optLong("pollEndsAtMillis", 0L),
            edited = obj.optBoolean("edited", false),
            visibility = obj.optString("visibility", "public"),
            likeCount = obj.optLong("likeCount", 0L),
            replyCount = obj.optLong("replyCount", 0L),
            repostCount = obj.optLong("repostCount", 0L),
            createdAt = if (createdMs > 0L) Timestamp(Date(createdMs)) else null,
            updatedAt = if (updatedMs > 0L) Timestamp(Date(updatedMs)) else null
        )
    }
}
