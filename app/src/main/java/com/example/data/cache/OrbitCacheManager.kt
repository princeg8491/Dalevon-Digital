package com.example.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.example.R
import com.example.data.model.ThreadPost
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

val LocalOrbitImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

/**
 * Threads-grade multi-layer caching engine:
 * 1. L1 Hot Memory: In-memory `LruCache` for posts & user profiles + ordered feed snapshot in `FeedCacheRepository`.
 * 2. L2 Persistent DataStore: `FeedCacheRepository` persists recent feed snapshots to Jetpack DataStore for instant offline cold starts.
 * 3. Media Cache: Coil 25% RAM memory cache + 100 MB disk cache + scroll-ahead prefetching for zero-jank scrolling.
 * 4. L3 Remote & Disk Sync: Firestore persistent local disk cache (100 MB) backed by live snapshot listeners.
 */
object OrbitCacheManager {

    private const val FIRESTORE_CACHE_BYTES = 100L * 1024L * 1024L // 100 MB
    private const val COIL_DISK_CACHE_BYTES = 100L * 1024L * 1024L // 100 MB
    private const val MAX_INLINE_IMAGE_DIM = 960
    private const val MAX_AVATAR_DIM = 320
    const val MAX_2K_IMAGE_DIM = 2560
    const val HD_2K_FRAGMENT_DELIMITER = "#orbit_hd2k="

    private val firestoreConfigured = AtomicBoolean(false)
    @Volatile
    private var cachedImageLoader: ImageLoader? = null

    val postCache: LruCache<String, ThreadPost> = LruCache(250)
    val profileCache: LruCache<String, UserProfile> = LruCache(200)

    fun getFirestore(context: Context): FirebaseFirestore {
        val databaseId = context.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        if (firestoreConfigured.compareAndSet(false, true)) {
            runCatching {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FIRESTORE_CACHE_BYTES)
                            .build()
                    )
                    .build()
                firestore.firestoreSettings = settings
            }.onFailure { err ->
                Log.w("OrbitCacheManager", "Firestore settings already initialized: ${err.message}")
            }
        }
        return firestore
    }

    fun getImageLoader(context: Context): ImageLoader {
        cachedImageLoader?.let { return it }
        return synchronized(this) {
            cachedImageLoader?.let { return it }
            val appContext = context.applicationContext
            val loader = ImageLoader.Builder(appContext)
                .components {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .memoryCache {
                    MemoryCache.Builder(appContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(appContext.cacheDir, "orbit_image_cache"))
                        .maxSizeBytes(COIL_DISK_CACHE_BYTES)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(180)
                .build()
            cachedImageLoader = loader
            loader
        }
    }

    fun cachePosts(posts: List<ThreadPost>) {
        posts.forEach { post ->
            if (post.id.isNotBlank()) {
                postCache.put(post.id, post)
            }
        }
    }

    fun cacheProfiles(profiles: List<UserProfile>) {
        profiles.forEach { profile ->
            val key = profile.resolvedId
            if (key.isNotBlank()) {
                profileCache.put(key, profile)
            }
        }
    }

    fun clearMemoryCaches() {
        postCache.evictAll()
        profileCache.evictAll()
    }

    /**
     * Converts an HTTP(S) URL, local `content://` / `file://` URI, video `#poster=` URI,
     * or inline `data:image/...;base64,...` URI (stripping any `#orbit_hd2k=` fragment so feeds
     * never download 2K assets until the user taps to open an image) into a Coil-compatible model.
     */
    fun toCoilModel(urlOrDataUri: String?): Any? {
        if (urlOrDataUri.isNullOrBlank()) return null
        val posterIdx = urlOrDataUri.indexOf("#poster=")
        if (posterIdx >= 0) {
            val posterDataUri = urlOrDataUri.substring(posterIdx + "#poster=".length)
            if (posterDataUri.startsWith("data:image/")) {
                return toCoilModel(posterDataUri)
            }
        }
        val cleanPreview = stripHd2kFragment(urlOrDataUri)
        if (cleanPreview.startsWith("data:image/")) {
            val commaIndex = cleanPreview.indexOf(',')
            if (commaIndex in 1 until cleanPreview.lastIndex) {
                return runCatching {
                    Base64.decode(cleanPreview.substring(commaIndex + 1), Base64.DEFAULT)
                }.getOrNull()
            }
            return null
        }
        if (cleanPreview.startsWith("content://")) {
            return Uri.parse(cleanPreview)
        }
        if (cleanPreview.startsWith("file://")) {
            val clean = cleanPreview.substringBefore('#').removePrefix("file://")
            return File(clean)
        }
        return cleanPreview
    }

    /**
     * Removes any `#orbit_hd2k=...` suffix so feed thumbnails stay lightweight.
     */
    fun stripHd2kFragment(urlOrDataUri: String): String {
        val idx = urlOrDataUri.indexOf(HD_2K_FRAGMENT_DELIMITER)
        return if (idx >= 0) urlOrDataUri.substring(0, idx) else urlOrDataUri
    }

    /**
     * Extracts the hosted 2K HTTPS URL embedded in `#orbit_hd2k=...`, or returns null if none.
     */
    fun extractHd2kUrl(urlOrDataUri: String?): String? {
        if (urlOrDataUri.isNullOrBlank()) return null
        val idx = urlOrDataUri.indexOf(HD_2K_FRAGMENT_DELIMITER)
        if (idx < 0) return null
        val candidate = urlOrDataUri.substring(idx + HD_2K_FRAGMENT_DELIMITER.length).trim()
        return candidate.takeIf {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
    }

    /**
     * Upgrades known resizable remote image URLs (e.g., Unsplash, Pexels, Google Photos)
     * to 2K (2560px) resolution exclusively when the user clicks to open an image.
     */
    fun upgradeRemoteUrlTo2k(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true) && !trimmed.startsWith("http://", ignoreCase = true)) {
            return trimmed
        }
        var upgraded = trimmed
        if (upgraded.contains("images.unsplash.com", ignoreCase = true) ||
            upgraded.contains("images.pexels.com", ignoreCase = true)
        ) {
            upgraded = if (Regex("""([?&])w=\d+""").containsMatchIn(upgraded)) {
                upgraded.replace(Regex("""([?&])w=\d+"""), "$1w=$MAX_2K_IMAGE_DIM")
            } else {
                val sep = if (upgraded.contains('?')) '&' else '?'
                "${upgraded}${sep}w=$MAX_2K_IMAGE_DIM"
            }
            if (Regex("""([?&])q=\d+""").containsMatchIn(upgraded)) {
                upgraded = upgraded.replace(Regex("""([?&])q=\d+"""), "$1q=92")
            }
        } else if (upgraded.contains("googleusercontent.com", ignoreCase = true) &&
            Regex("""=[sw]\d+""").containsMatchIn(upgraded)
        ) {
            upgraded = upgraded.replace(Regex("""=([sw])\d+"""), "=$1$MAX_2K_IMAGE_DIM")
        }
        return upgraded
    }

    /**
     * Resolves the highest-resolution 2K Coil model for fullscreen viewing ONLY when an image is opened.
     */
    fun resolveFullScreen2kModel(urlOrDataUri: String?): Any? {
        if (urlOrDataUri.isNullOrBlank()) return null
        val hd2kUrl = extractHd2kUrl(urlOrDataUri)
        if (!hd2kUrl.isNullOrBlank()) {
            return hd2kUrl
        }
        val clean = stripHd2kFragment(urlOrDataUri)
        if (clean.startsWith("https://", ignoreCase = true) || clean.startsWith("http://", ignoreCase = true)) {
            return upgradeRemoteUrlTo2k(clean)
        }
        return toCoilModel(clean)
    }

    /**
     * Returns true when opening [urlOrDataUri] in the lightbox will stream or decode a dedicated 2K source.
     */
    fun hasDedicated2kSource(urlOrDataUri: String?): Boolean {
        if (urlOrDataUri.isNullOrBlank()) return false
        if (!extractHd2kUrl(urlOrDataUri).isNullOrBlank()) return true
        val clean = stripHd2kFragment(urlOrDataUri)
        return clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("content://", ignoreCase = true) ||
            clean.startsWith("file://", ignoreCase = true)
    }

    /**
     * Builds an explicit 2K (2560px) Coil [ImageRequest] used ONLY inside [FullScreenImageLightbox].
     */
    fun buildFullScreen2kImageRequest(context: Context, imageUrl: String): ImageRequest {
        val data2k = resolveFullScreen2kModel(imageUrl)
        return ImageRequest.Builder(context)
            .data(data2k)
            .size(MAX_2K_IMAGE_DIM, MAX_2K_IMAGE_DIM)
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }

    /**
     * Scroll-ahead media prefetcher: pre-warms avatars & post photos for the next [prefetchCount] posts.
     * Feed prefetches stay capped at standard resolution so 2K images are only loaded when clicked.
     */
    fun prefetchFeedMedia(
        context: Context,
        imageLoader: ImageLoader,
        posts: List<ThreadPost>,
        firstVisibleIndex: Int,
        prefetchCount: Int = 6
    ) {
        if (posts.isEmpty()) return
        val start = firstVisibleIndex.coerceAtLeast(0)
        val end = (start + prefetchCount).coerceAtMost(posts.lastIndex)
        for (i in start..end) {
            val post = posts[i]
            enqueuePrefetch(context, imageLoader, post.authorAvatarUrl)
            post.imageUrls.forEach { url ->
                enqueuePrefetch(context, imageLoader, url)
            }
        }
    }

    private fun enqueuePrefetch(context: Context, imageLoader: ImageLoader, urlOrDataUri: String) {
        val clean = stripHd2kFragment(urlOrDataUri)
        if (clean.isBlank() ||
            clean.startsWith("data:") ||
            clean.startsWith("file://") ||
            clean.startsWith("content://") ||
            clean.contains("#poster=")
        ) {
            return
        }
        val request = ImageRequest.Builder(context)
            .data(clean)
            .size(1080, 1080)
            .scale(Scale.FIT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
        imageLoader.enqueue(request)
    }

    /**
     * Encodes a zero-permission Photo Picker or keyboard-inserted media URI for Firestore storage.
     * Preserves animated GIFs (`image/gif` or `GIF87a`/`GIF89a`) as inline base64 GIFs (<= 240 KB)
     * or hosted HTTPS GIF URLs (> 240 KB), and compresses standard photos into compact JPEGs.
     */
    suspend fun encodeUriToDataUri(
        context: Context,
        uri: Uri,
        isAvatar: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!isAvatar) {
                val rawBytes = readUriBytesUpToLimit(context, uri, maxBytes = 12 * 1024 * 1024)
                val mime = OrbitMediaFiles.queryMimeType(context, uri).lowercase(java.util.Locale.US)
                if (rawBytes != null &&
                    (mime.contains("gif") ||
                        uri.toString().lowercase(java.util.Locale.US).endsWith(".gif") ||
                        OrbitMediaFiles.hasGifMagicHeader(rawBytes))
                ) {
                    if (rawBytes.size <= 240_000) {
                        val b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
                        return@runCatching "data:image/gif;base64,$b64"
                    }
                    val hostedGifUrl = OrbitMediaFiles.uploadHostedGifBytes(rawBytes)
                    if (!hostedGifUrl.isNullOrBlank()) {
                        return@runCatching hostedGifUrl
                    }
                }
            }

            val maxDim = if (isAvatar) MAX_AVATAR_DIM else MAX_INLINE_IMAGE_DIM
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return@runCatching null

            // Decode high enough for 2K (2560px) when uploading non-avatar photos, then derive
            // both the crisp 2K hosted version (loaded ONLY when opened) and the compact feed preview.
            val decodeTargetDim = if (isAvatar) MAX_AVATAR_DIM else MAX_2K_IMAGE_DIM
            var inSampleSize = 1
            while ((boundsOptions.outWidth / inSampleSize) > decodeTargetDim * 2 ||
                (boundsOptions.outHeight / inSampleSize) > decodeTargetDim * 2
            ) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@runCatching null

            var hosted2kUrl: String? = null
            if (!isAvatar) {
                val maxEdge = maxOf(decoded.width, decoded.height).coerceAtLeast(1)
                val scale2k = minOf(1f, MAX_2K_IMAGE_DIM.toFloat() / maxEdge)
                val bitmap2k = if (scale2k < 1f) {
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * scale2k).toInt().coerceAtLeast(1),
                        (decoded.height * scale2k).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    decoded
                }
                val hd2kOut = ByteArrayOutputStream()
                var q2k = 92
                do {
                    hd2kOut.reset()
                    bitmap2k.compress(Bitmap.CompressFormat.JPEG, q2k, hd2kOut)
                    q2k -= 6
                } while (hd2kOut.size() > 4 * 1024 * 1024 && q2k >= 72)
                if (bitmap2k !== decoded) bitmap2k.recycle()
                val hd2kBytes = hd2kOut.toByteArray()
                if (hd2kBytes.isNotEmpty()) {
                    hosted2kUrl = OrbitMediaFiles.uploadHostedImage2kBytes(hd2kBytes)
                }
            }

            val scale = minOf(
                1f,
                maxDim.toFloat() / maxOf(decoded.width, decoded.height).coerceAtLeast(1)
            )
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it != decoded) decoded.recycle() }
            } else {
                decoded
            }

            val maxBytes = if (isAvatar) 80_000 else 150_000
            var quality = if (isAvatar) 78 else 74
            val baos = ByteArrayOutputStream()
            do {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                quality -= 10
            } while (baos.size() > maxBytes && quality >= 35)
            scaled.recycle()

            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val previewDataUri = "data:image/jpeg;base64,$encoded"
            if (!hosted2kUrl.isNullOrBlank()) {
                "$previewDataUri$HD_2K_FRAGMENT_DELIMITER$hosted2kUrl"
            } else {
                previewDataUri
            }
        }.getOrNull()
    }

    private fun readUriBytesUpToLimit(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
        return runCatching {
            val stream = if (uri.scheme == "file") {
                uri.path?.let { File(it) }?.takeIf { it.exists() }?.inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            } ?: return@runCatching null
            stream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return@runCatching null
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            }
        }.getOrNull()
    }
}
