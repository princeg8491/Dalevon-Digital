package com.example.data.cache

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.AppRelease
import com.example.data.model.PostAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipFile

object OrbitMediaFiles {

    const val MAX_VIDEO_UPLOAD_BYTES: Long = 500L * 1024L * 1024L // 500 MB max video size
    private const val MAX_INLINE_FILE_BYTES = 300 * 1024 // ~300 KB binary -> ~400 KB base64 (fits safely in Firestore 1 MB doc)
    private const val PREFS_VERSION_META = "orbit_version_meta"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"

    /**
     * Runs once after an app update (e.g., v4 -> v5) to clear stale in-memory/image caches
     * so schema or resource updates never leave broken cached state.
     */
    fun runAppUpgradeHousekeepingIfNeeded(context: Context) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_VERSION_META, Context.MODE_PRIVATE)
            val lastVersion = prefs.getInt(KEY_LAST_VERSION_CODE, -1)
            val currentVersion = BuildConfig.VERSION_CODE
            if (lastVersion != currentVersion) {
                OrbitCacheManager.clearMemoryCaches()
                File(context.applicationContext.cacheDir, "orbit_videos").deleteRecursively()
                prefs.edit().putInt(KEY_LAST_VERSION_CODE, currentVersion).apply()
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1_024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    fun queryDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "attachment"
    }

    fun queryFileSize(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        val size = cursor.getLong(idx)
                        if (size > 0L) return size
                    }
                }
            }
        }
        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) return afd.length
            }
        }
        return -1L
    }

    fun queryMimeType(context: Context, uri: Uri): String {
        val resolved = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (!resolved.isNullOrBlank()) return resolved
        val name = queryDisplayName(context, uri).lowercase(java.util.Locale.US)
        return when {
            name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".mkv") -> "video/mp4"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".gif") -> "image/gif"
            else -> "application/octet-stream"
        }
    }

    /**
     * Checks if [bytes] begins with the GIF87a or GIF89a animated image magic header.
     */
    fun hasGifMagicHeader(bytes: ByteArray, length: Int = bytes.size): Boolean {
        return length >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte() &&
            (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
            bytes[5] == 'a'.code.toByte()
    }

    /**
     * Synchronously persists a media URI committed by the Android keyboard (Gboard, Samsung
     * Keyboard, SwiftKey, etc.) into app cache while the IME's temporary URI read grant is active.
     */
    fun persistKeyboardMediaUriSync(context: Context, sourceUri: Uri): Uri? {
        return runCatching {
            val mime = queryMimeType(context, sourceUri).lowercase(java.util.Locale.US)
            val cacheDir = File(context.cacheDir, "orbit_kb_media").apply { mkdirs() }
            val tempFile = File(cacheDir, "tmp_${UUID.randomUUID()}")
            val header = ByteArray(12)
            var headerLen = 0
            var totalBytes = 0L
            val maxKeyboardMediaBytes = 15L * 1024L * 1024L // 15 MB safety cap
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        if (headerLen < header.size) {
                            val toCopy = minOf(read, header.size - headerLen)
                            System.arraycopy(buf, 0, header, headerLen, toCopy)
                            headerLen += toCopy
                        }
                        totalBytes += read
                        if (totalBytes > maxKeyboardMediaBytes) {
                            error("Keyboard GIF exceeds 15 MB limit.")
                        }
                        out.write(buf, 0, read)
                    }
                    out.flush()
                }
            } ?: return@runCatching null

            if (totalBytes <= 0L) {
                runCatching { tempFile.delete() }
                return@runCatching null
            }

            val isGif = mime.contains("gif") || hasGifMagicHeader(header, headerLen)
            val ext = when {
                isGif -> "gif"
                mime.contains("webp") -> "webp"
                mime.contains("png") -> "png"
                else -> "jpg"
            }
            val prefix = if (isGif) "kb_gif_" else "kb_img_"
            val finalFile = File(cacheDir, "$prefix${UUID.randomUUID()}.$ext")
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            Uri.fromFile(finalFile)
        }.getOrNull()
    }

    /**
     * Uploads an animated GIF (> 240 KB) to a public HTTPS host so it stays animated across all
     * devices without exceeding Firestore's 1 MB document limit.
     */
    suspend fun uploadHostedGifBytes(
        bytes: ByteArray,
        fileName: String = "orbit_gif_${UUID.randomUUID().toString().take(8)}.gif"
    ): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty() || bytes.size > 15 * 1024 * 1024) return@withContext null
        val safeName = if (fileName.endsWith(".gif", ignoreCase = true)) fileName else "$fileName.gif"
        val tempFile = runCatching {
            File.createTempFile("orbit_gif_up_", ".gif").apply { writeBytes(bytes) }
        }.getOrNull() ?: return@withContext null
        try {
            tryUploadBinaryToCatbox(tempFile, safeName, "image/gif") {}
                ?: tryUploadBinaryToLitterbox(tempFile, safeName, "image/gif") {}
                ?: tryUploadBinaryToTmpfiles(tempFile, safeName, "image/gif") {}
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    /**
     * Uploads a 2K-resolution JPEG image (up to 2560px longest edge) to a public HTTPS host so
     * feeds stay fast with compact inline previews while tapping an image loads crisp 2K quality.
     */
    suspend fun uploadHostedImage2kBytes(
        bytes: ByteArray,
        fileName: String = "orbit_2k_${UUID.randomUUID().toString().take(8)}.jpg"
    ): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty() || bytes.size > 15 * 1024 * 1024) return@withContext null
        val safeName = if (fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true)) {
            fileName
        } else {
            "$fileName.jpg"
        }
        val tempFile = runCatching {
            File.createTempFile("orbit_2k_up_", ".jpg").apply { writeBytes(bytes) }
        }.getOrNull() ?: return@withContext null
        try {
            tryUploadBinaryToCatbox(tempFile, safeName, "image/jpeg") {}
                ?: tryUploadBinaryToLitterbox(tempFile, safeName, "image/jpeg") {}
                ?: tryUploadBinaryToTmpfiles(tempFile, safeName, "image/jpeg") {}
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    /**
     * Extracts a representative poster frame Bitmap from a local video [uri] or [file].
     */
    fun extractVideoFrameBitmap(
        context: Context,
        uri: Uri? = null,
        file: File? = null
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            when {
                file != null && file.exists() -> retriever.setDataSource(file.absolutePath)
                uri != null -> retriever.setDataSource(context, uri)
                else -> return null
            }
            retriever.getFrameAtTime(250_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Extracts a compact JPEG data URI (`data:image/jpeg;base64,...`) from a video file or URI
     * so videos up to 500 MB can store a lightweight poster thumbnail inside Firestore documents.
     */
    fun extractVideoFrameDataUri(
        context: Context,
        uri: Uri? = null,
        file: File? = null
    ): String? {
        val frame = extractVideoFrameBitmap(context, uri = uri, file = file) ?: return null
        return try {
            val maxDim = 480
            val scale = minOf(
                1f,
                maxDim.toFloat() / maxOf(frame.width, frame.height).coerceAtLeast(1)
            )
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                frame
            }
            val baos = ByteArrayOutputStream()
            var quality = 72
            do {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                quality -= 12
            } while (baos.size() > 45_000 && quality >= 30)
            if (scaled !== frame) scaled.recycle()
            frame.recycle()
            "data:image/jpeg;base64,${Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)}"
        } catch (_: Throwable) {
            runCatching { frame.recycle() }
            null
        }
    }

    /**
     * Extracts the `#poster=data:image/jpeg;base64,...` preview payload embedded in a 500 MB video dataUri.
     */
    fun extractPosterFromVideoDataUri(dataUri: String): String? {
        val marker = "#poster="
        val idx = dataUri.indexOf(marker)
        if (idx < 0) return null
        val sub = dataUri.substring(idx + marker.length)
        return sub.takeIf { it.startsWith("data:image/") }
    }

    /**
     * Reads a file or video (up to 500 MB) picked via system Files (OpenDocument) or Photo Picker
     * into a Firestore-ready PostAttachment.
     */
    suspend fun encodeUriToAttachment(context: Context, uri: Uri): Result<PostAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            val mimeType = queryMimeType(context, uri)
            val fileName = queryDisplayName(context, uri).take(150)

            // If user picked an image through system Files, compress it cleanly as JPEG
            if (mimeType.startsWith("image/", ignoreCase = true)) {
                val compressed = OrbitCacheManager.encodeUriToDataUri(context, uri, isAvatar = false)
                    ?: error("Could not read selected image.")
                val approxBytes = (compressed.substringAfter(',').length * 3L) / 4L
                return@runCatching PostAttachment(
                    fileName = fileName,
                    mimeType = "image/jpeg",
                    sizeBytes = approxBytes,
                    dataUri = compressed
                )
            }

            // Video attachments: support up to 500 MB with streaming disk persistence & poster extraction
            if (mimeType.startsWith("video/", ignoreCase = true)) {
                val declaredSize = queryFileSize(context, uri)
                if (declaredSize > MAX_VIDEO_UPLOAD_BYTES) {
                    error(
                        "Video \"$fileName\" is ${formatFileSize(declaredSize)}, which exceeds the 500 MB limit."
                    )
                }

                val persistentDir = File(context.filesDir, "orbit_videos").apply { mkdirs() }
                val destFile = File(persistentDir, "vid_${UUID.randomUUID()}.mp4")
                var totalBytes = 0L
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                totalBytes += read
                                if (totalBytes > MAX_VIDEO_UPLOAD_BYTES) {
                                    error(
                                        "Video \"$fileName\" exceeds the 500 MB limit."
                                    )
                                }
                                out.write(buffer, 0, read)
                            }
                            out.flush()
                        }
                    } ?: error("Unable to open selected video.")
                } catch (e: Throwable) {
                    runCatching { destFile.delete() }
                    throw e
                }

                require(totalBytes > 0L) { "Selected video is empty." }

                // Small clips (<= 300 KB) can still be stored inline as base64 data URIs
                if (totalBytes <= MAX_INLINE_FILE_BYTES) {
                    val bytes = destFile.readBytes()
                    runCatching { destFile.delete() }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    return@runCatching PostAttachment(
                        fileName = fileName,
                        mimeType = mimeType.take(120),
                        sizeBytes = totalBytes,
                        dataUri = "data:$mimeType;base64,$b64"
                    )
                }

                // Videos from 300 KB up to 500 MB are persisted locally & carry an inline poster frame
                val posterDataUri = extractVideoFrameDataUri(context, file = destFile)
                val storedDataUri = if (!posterDataUri.isNullOrBlank()) {
                    "file://${destFile.absolutePath}#poster=$posterDataUri"
                } else {
                    "file://${destFile.absolutePath}"
                }
                return@runCatching PostAttachment(
                    fileName = fileName,
                    mimeType = mimeType.take(120),
                    sizeBytes = totalBytes,
                    dataUri = storedDataUri
                )
            }

            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_INLINE_FILE_BYTES) {
                        error("File \"$fileName\" is larger than 300 KB. For videos, up to 500 MB is supported.")
                    }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            } ?: error("Unable to open selected file.")

            require(bytes.isNotEmpty()) { "Selected file is empty." }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            PostAttachment(
                fileName = fileName,
                mimeType = mimeType.take(120),
                sizeBytes = bytes.size.toLong(),
                dataUri = "data:$mimeType;base64,$b64"
            )
        }
    }

    /**
     * Encodes a cropped Bitmap (avatar or banner) into a compact base64 JPEG data URI.
     */
    suspend fun encodeCroppedBitmapToDataUri(
        bitmap: Bitmap,
        isBanner: Boolean
    ): String = withContext(Dispatchers.IO) {
        val targetMaxWidth = if (isBanner) 1080 else 420
        val scale = minOf(1f, targetMaxWidth.toFloat() / bitmap.width.coerceAtLeast(1))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        val maxBytes = if (isBanner) 150_000 else 80_000
        var quality = if (isBanner) 82 else 84
        val baos = ByteArrayOutputStream()
        do {
            baos.reset()
            resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            quality -= 8
        } while (baos.size() > maxBytes && quality >= 35)

        if (resized !== bitmap) {
            resized.recycle()
        }
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    }

    /**
     * Resolves or materializes a video attachment's data URI into a playable local file for VideoView.
     * Supports 500 MB `file://` persisted videos in O(1) time as well as `content://` and inline `data:` clips.
     */
    suspend fun materializeVideoToTempFile(
        context: Context,
        dataUri: String,
        cacheKey: String
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUri = dataUri.substringBefore('#')
            if (cleanUri.startsWith("file://")) {
                val localFile = File(cleanUri.removePrefix("file://"))
                if (localFile.exists() && localFile.length() > 0L) {
                    return@runCatching localFile
                }
            }

            val dir = File(context.cacheDir, "orbit_videos").apply { mkdirs() }
            val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(48)
            val target = File(dir, "$safeKey.mp4")
            if (target.exists() && target.length() > 0L) return@runCatching target

            if (cleanUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(cleanUri))?.use { input ->
                    target.outputStream().use { out ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                } ?: return@runCatching null
                return@runCatching target.takeIf { it.length() > 0L }
            }

            val bytes = decodeBytesFromUrlOrDataUri(cleanUri) ?: return@runCatching null
            target.writeBytes(bytes)
            target
        }.getOrNull()
    }

    /**
     * Zero-permission download of a photo (data URI or HTTPS URL) to Pictures/Orbit (API 29+)
     * or app-scoped Pictures directory (API 24-28).
     */
    suspend fun downloadPhotoToDevice(
        context: Context,
        urlOrDataUri: String,
        suggestedName: String = "orbit_photo_${System.currentTimeMillis()}.jpg",
        saveToDownloads: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = decodeBytesFromUrlOrDataUri(urlOrDataUri)
                ?: error("Could not read photo data.")
            val safeName = sanitizeFileName(suggestedName, "jpg")
            saveBytesZeroPermission(
                context = context,
                bytes = bytes,
                fileName = safeName,
                mimeType = "image/jpeg",
                kind = if (saveToDownloads) DownloadKind.DOCUMENT else DownloadKind.IMAGE
            )
        }
    }

    /**
     * Zero-permission download of any file or video attachment (up to 500 MB) to Downloads/Orbit or Movies/Orbit.
     */
    suspend fun downloadAttachmentToDevice(
        context: Context,
        attachment: PostAttachment
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val kind = when {
                attachment.mimeType.startsWith("video/") -> DownloadKind.VIDEO
                attachment.mimeType.startsWith("image/") -> DownloadKind.IMAGE
                else -> DownloadKind.DOCUMENT
            }
            val safeName = sanitizeFileName(attachment.fileName.ifBlank { "orbit_file" }, null)
            val cleanUri = attachment.dataUri.substringBefore('#')
            if (cleanUri.startsWith("file://")) {
                val localFile = File(cleanUri.removePrefix("file://"))
                if (localFile.exists() && localFile.length() > 0L) {
                    return@runCatching localFile.inputStream().use { stream ->
                        saveStreamZeroPermission(
                            context = context,
                            input = stream,
                            fileName = safeName,
                            mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                            kind = kind
                        )
                    }
                }
            }
            val bytes = decodeBytesFromUrlOrDataUri(cleanUri)
                ?: error("Could not read attachment data.")
            saveBytesZeroPermission(
                context = context,
                bytes = bytes,
                fileName = safeName,
                mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                kind = kind
            )
        }
    }

    private const val KEY_HOSTED_APK_URL = "hosted_apk_download_url"
    private const val KEY_HOSTED_APK_VERSION = "hosted_apk_version_code"

    /**
     * Returns any previously hosted direct APK download link cached on this device for the current build.
     */
    fun getCachedHostedApkDownloadUrl(context: Context): String {
        return runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_VERSION_META, Context.MODE_PRIVATE)
            val cachedVer = prefs.getInt(KEY_HOSTED_APK_VERSION, -1)
            val cachedUrl = prefs.getString(KEY_HOSTED_APK_URL, "").orEmpty().trim()
            if (cachedVer == BuildConfig.VERSION_CODE && isValidApkDownloadUrl(cachedUrl)) {
                cachedUrl
            } else {
                ""
            }
        }.getOrDefault("")
    }

    private fun saveCachedHostedApkDownloadUrl(context: Context, url: String) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_VERSION_META, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_HOSTED_APK_VERSION, BuildConfig.VERSION_CODE)
                .putString(KEY_HOSTED_APK_URL, url.trim())
                .apply()
        }
    }

    data class ApkIntegrityReport(
        val isShareable: Boolean,
        val sizeBytes: Long,
        val hasSplitApks: Boolean,
        val reason: String
    )

    private fun clearCachedHostedApkDownloadUrl(context: Context) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_VERSION_META, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(KEY_HOSTED_APK_URL)
                .remove(KEY_HOSTED_APK_VERSION)
                .apply()
        }
    }

    /**
     * Checks if [bytes] begins with the ZIP / Android APK magic header (`PK\x03\x04`).
     */
    fun hasApkZipMagicHeader(bytes: ByteArray, length: Int = bytes.size): Boolean {
        return length >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }

    private fun isHtmlOrTextErrorPayload(bytes: ByteArray, length: Int): Boolean {
        if (length <= 0) return false
        val preview = String(bytes, 0, minOf(length, 256), Charsets.UTF_8)
            .trimStart()
            .lowercase(java.util.Locale.US)
        return preview.startsWith("<!doctype") ||
            preview.startsWith("<html") ||
            preview.startsWith("<head") ||
            preview.startsWith("<body") ||
            preview.startsWith("<script") ||
            preview.startsWith("{")
    }

    /**
     * Returns true when a Firestore release entry matches or is newer than the current app build
     * and carries a valid download URL (preventing Android version-downgrade install failures).
     */
    fun isReleaseUrlCurrentOrNewer(
        release: AppRelease?,
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): Boolean {
        if (release == null) return false
        return release.versionCode >= currentVersionCode.toLong() &&
            isValidApkDownloadUrl(release.driveDownloadUrl)
    }

    /**
     * Normalizes tmpfiles.org URLs to their single `/dl/` direct-binary endpoint without ever
     * duplicating `/dl/dl/`.
     */
    fun normalizeTmpfilesDownloadUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val match = Regex("""^https?://(?:www\.)?tmpfiles\.org/+(.+)$""", RegexOption.IGNORE_CASE)
            .find(trimmed) ?: return trimmed
        var path = match.groupValues[1].trimStart('/')
        while (path.startsWith("dl/", ignoreCase = true)) {
            path = path.substring(3).trimStart('/')
        }
        return if (path.isNotBlank()) "https://tmpfiles.org/dl/$path" else ""
    }

    /**
     * Checks whether [url] is a valid HTTPS download link (and specifically rejects the
     * AI Studio web proxy preview URL that returns HTTP 404 for Android projects).
     */
    fun isValidApkDownloadUrl(url: String?): Boolean {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        if (!trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return false
        }
        if (trimmed.contains(".run.app", ignoreCase = true)) {
            return false
        }
        return true
    }

    /**
     * Normalizes any Google Drive or direct APK download URL so it works cleanly in mobile browsers.
     */
    fun resolveOpenShareableUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (!isValidApkDownloadUrl(trimmed)) return ""
        if (trimmed.contains("tmpfiles.org/", ignoreCase = true)) {
            return normalizeTmpfilesDownloadUrl(trimmed)
        }
        val filePathMatch = Regex("""drive\.google\.com/file/d/([a-zA-Z0-9_\-]+)""").find(trimmed)
        if (filePathMatch != null) {
            val fileId = filePathMatch.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }
        val queryIdMatch = Regex("""[?&]id=([a-zA-Z0-9_\-]+)""").find(trimmed)
        if (trimmed.contains("drive.google.com", ignoreCase = true) && queryIdMatch != null) {
            val fileId = queryIdMatch.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }
        return trimmed
    }

    /**
     * Verifies that the installed Orbit APK has a valid AndroidManifest.xml, compiled DEX payload,
     * and verified package signature metadata so it can be shared and installed by invited users.
     */
    fun inspectInstalledApkIntegrity(context: Context): ApkIntegrityReport {
        val appInfo = context.applicationInfo
            ?: return ApkIntegrityReport(false, 0L, false, "Could not access installed app info.")
        val apkPath = appInfo.sourceDir.orEmpty()
        val apkFile = File(apkPath)
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return ApkIntegrityReport(false, 0L, false, "Could not locate installed Orbit APK package.")
        }
        val splitDirs = appInfo.splitSourceDirs?.filter { it.isNotBlank() }.orEmpty()
        val hasSplitConfigs = splitDirs.isNotEmpty()
        val hasRequiredZipEntries = runCatching {
            ZipFile(apkFile).use { zip ->
                val hasManifest = zip.getEntry("AndroidManifest.xml") != null
                val entries = zip.entries()
                var hasDex = false
                while (entries.hasMoreElements()) {
                    if (entries.nextElement().name.endsWith(".dex", ignoreCase = true)) {
                        hasDex = true
                        break
                    }
                }
                hasManifest && hasDex
            }
        }.getOrDefault(false)
        if (!hasRequiredZipEntries) {
            return ApkIntegrityReport(
                isShareable = false,
                sizeBytes = apkFile.length(),
                hasSplitApks = hasSplitConfigs,
                reason = "Installed Orbit APK archive is missing AndroidManifest.xml or compiled DEX code."
            )
        }
        @Suppress("DEPRECATION")
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        }.getOrNull()
        if (pkgInfo == null || pkgInfo.packageName != context.packageName) {
            return ApkIntegrityReport(
                isShareable = false,
                sizeBytes = apkFile.length(),
                hasSplitApks = hasSplitConfigs,
                reason = "Installed APK metadata failed package verification."
            )
        }
        if (hasSplitConfigs) {
            return ApkIntegrityReport(
                isShareable = false,
                sizeBytes = apkFile.length(),
                hasSplitApks = true,
                reason = "Orbit is installed via Google Play App Bundle. Share or update directly from Google Play Store."
            )
        }
        return ApkIntegrityReport(
            isShareable = true,
            sizeBytes = apkFile.length(),
            hasSplitApks = false,
            reason = ""
        )
    }

    data class ApkLinkCheckResult(
        val isValid: Boolean,
        val httpStatus: Int = 0,
        val contentLengthBytes: Long = -1L,
        val hostLabel: String = "",
        val message: String = ""
    )

    /**
     * Probes a download URL over HTTP (`Range: bytes=0-3`) and returns detailed verification
     * diagnostics confirming it serves a real binary APK/ZIP stream (`PK\x03\x04`) rather than
     * an expired 404 or HTML error page.
     */
    suspend fun inspectDirectApkDownloadUrl(rawUrl: String): ApkLinkCheckResult = withContext(Dispatchers.IO) {
        val startUrl = resolveOpenShareableUrl(rawUrl)
        if (startUrl.isBlank()) {
            return@withContext ApkLinkCheckResult(
                isValid = false,
                message = "No direct APK link is configured yet."
            )
        }
        val hostLabel = runCatching { URL(startUrl).host.orEmpty() }.getOrDefault("")
        runCatching {
            var currentUrl = startUrl
            var connection: HttpURLConnection? = null
            var redirects = 0
            while (redirects < 6) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 12_000
                    setRequestProperty("Range", "bytes=0-3")
                    setRequestProperty("User-Agent", "Orbit-Android/${BuildConfig.VERSION_NAME}")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        return@runCatching ApkLinkCheckResult(
                            isValid = false,
                            httpStatus = code,
                            hostLabel = hostLabel,
                            message = "Redirect response missing Location header (HTTP $code)."
                        )
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                } else {
                    connection = conn
                    break
                }
            }
            val conn = connection ?: return@runCatching ApkLinkCheckResult(
                isValid = false,
                hostLabel = hostLabel,
                message = "Too many redirects while checking link."
            )
            try {
                val status = conn.responseCode
                if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) {
                    return@runCatching ApkLinkCheckResult(
                        isValid = false,
                        httpStatus = status,
                        hostLabel = hostLabel,
                        message = "Host returned HTTP $status (link expired or unavailable)."
                    )
                }
                val contentType = conn.contentType.orEmpty().lowercase(java.util.Locale.US)
                if (contentType.contains("text/html") ||
                    contentType.contains("application/json") ||
                    contentType.contains("text/plain")
                ) {
                    return@runCatching ApkLinkCheckResult(
                        isValid = false,
                        httpStatus = status,
                        hostLabel = hostLabel,
                        message = "Link returned a web page ($contentType) instead of an APK binary."
                    )
                }
                val totalLength = conn.getHeaderField("Content-Range")
                    ?.substringAfter('/', "")
                    ?.trim()
                    ?.toLongOrNull()
                    ?: conn.contentLengthLong
                val header = ByteArray(4)
                var totalRead = 0
                conn.inputStream.use { input ->
                    while (totalRead < 4) {
                        val r = input.read(header, totalRead, 4 - totalRead)
                        if (r <= 0) break
                        totalRead += r
                    }
                }
                val isZip = hasApkZipMagicHeader(header, totalRead)
                if (!isZip) {
                    ApkLinkCheckResult(
                        isValid = false,
                        httpStatus = status,
                        contentLengthBytes = totalLength,
                        hostLabel = hostLabel,
                        message = "Downloaded header is not a valid Android APK package."
                    )
                } else {
                    val sizeHint = if (totalLength > 0L) " (${formatFileSize(totalLength)})" else ""
                    ApkLinkCheckResult(
                        isValid = true,
                        httpStatus = status,
                        contentLengthBytes = totalLength,
                        hostLabel = hostLabel,
                        message = "Verified live APK binary on ${hostLabel.ifBlank { "host" }}$sizeHint"
                    )
                }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { err ->
            ApkLinkCheckResult(
                isValid = false,
                hostLabel = hostLabel,
                message = err.message ?: "Could not reach download host."
            )
        }
    }

    /**
     * Probes a download URL over HTTP (`Range: bytes=0-3`) to confirm it resolves to an actual
     * binary APK/ZIP stream (`PK\x03\x04`) rather than an expired 404 or HTML error page.
     */
    suspend fun verifyDirectApkDownloadUrl(rawUrl: String): Boolean {
        return inspectDirectApkDownloadUrl(rawUrl).isValid
    }

    /**
     * Resolves the best active direct APK download URL from Firestore release metadata or local cache.
     */
    fun resolveActiveApkDownloadUrl(
        context: Context,
        releaseDownloadUrl: String?,
        releaseVersionCode: Long? = null
    ): String {
        val fromRelease = releaseDownloadUrl?.trim().orEmpty()
        val versionAcceptable = releaseVersionCode == null || releaseVersionCode >= BuildConfig.VERSION_CODE.toLong()
        if (versionAcceptable && isValidApkDownloadUrl(fromRelease)) {
            return resolveOpenShareableUrl(fromRelease)
        }
        val fromCache = getCachedHostedApkDownloadUrl(context)
        if (isValidApkDownloadUrl(fromCache)) {
            return resolveOpenShareableUrl(fromCache)
        }
        return ""
    }

    /**
     * Uploads the currently installed Orbit APK (`context.applicationInfo.sourceDir`) to a public
     * HTTPS direct-download host so invited friends can click the link in Chrome/Messages and
     * immediately download `Orbit-v<version>.apk` without any 404 or corrupt HTML error.
     */
    suspend fun ensureHostedApkDownloadUrl(
        context: Context,
        existingReleaseUrl: String? = null,
        existingReleaseVersionCode: Long? = null,
        forceRefresh: Boolean = false,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!forceRefresh) {
                val active = resolveActiveApkDownloadUrl(
                    context = context,
                    releaseDownloadUrl = existingReleaseUrl,
                    releaseVersionCode = existingReleaseVersionCode
                )
                if (active.isNotBlank() && verifyDirectApkDownloadUrl(active)) {
                    saveCachedHostedApkDownloadUrl(context, active)
                    onProgress(1f, "Direct APK download link is ready")
                    return@runCatching active
                }
                if (active.isNotBlank()) {
                    clearCachedHostedApkDownloadUrl(context)
                }
            }

            val integrity = inspectInstalledApkIntegrity(context)
            require(integrity.isShareable) { integrity.reason }

            val apkFile = File(context.applicationInfo.sourceDir)
            val safeVer = BuildConfig.VERSION_NAME.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            val fileName = "Orbit-v$safeVer.apk"

            onProgress(0.06f, "Preparing $fileName (${formatFileSize(apkFile.length())}) for direct download link…")

            // Try Catbox (permanent direct link), Litterbox (72h direct link), and Tmpfiles (fixed /dl/ direct link); verify each binary URL
            val uploadedUrl = tryUploadApkToCatbox(apkFile, fileName) { pct ->
                onProgress((0.08f + pct * 0.85f).coerceIn(0.08f, 0.94f), "Generating permanent APK download link… ${(pct * 100).toInt()}%")
            } ?: tryUploadApkToLitterbox(apkFile, fileName) { pct ->
                onProgress((0.08f + pct * 0.85f).coerceIn(0.08f, 0.94f), "Generating direct APK download link… ${(pct * 100).toInt()}%")
            } ?: tryUploadApkToTmpfiles(apkFile, fileName) { pct ->
                onProgress((0.08f + pct * 0.85f).coerceIn(0.08f, 0.94f), "Generating direct APK download link… ${(pct * 100).toInt()}%")
            } ?: error("Could not generate a verified APK download link right now. Use 'Send APK File' to share directly.")

            saveCachedHostedApkDownloadUrl(context, uploadedUrl)
            onProgress(1f, "Direct APK download link ready!")
            uploadedUrl
        }
    }

    private suspend fun tryUploadApkToCatbox(
        apkFile: File,
        fileName: String,
        onUploadProgress: (Float) -> Unit
    ): String? {
        val candidate = tryUploadBinaryToCatbox(
            file = apkFile,
            fileName = fileName,
            contentType = "application/vnd.android.package-archive",
            onUploadProgress = onUploadProgress
        ) ?: return null
        return if (verifyDirectApkDownloadUrl(candidate)) candidate else null
    }

    private fun tryUploadBinaryToCatbox(
        file: File,
        fileName: String,
        contentType: String,
        onUploadProgress: (Float) -> Unit
    ): String? {
        return runCatching {
            val boundary = "----OrbitApkBoundary${UUID.randomUUID().toString().replace("-", "")}"
            val header = buildString {
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n")
                append("fileupload\r\n")
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"$fileName\"\r\n")
                append("Content-Type: $contentType\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)
            val footer = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            val payloadLength = header.size.toLong() + file.length() + footer.size.toLong()

            val conn = (URL("https://catbox.moe/user/api.php").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                connectTimeout = 20_000
                readTimeout = 90_000
                setFixedLengthStreamingMode(payloadLength)
                setRequestProperty("User-Agent", "Orbit-Android/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            try {
                val totalBytes = file.length().coerceAtLeast(1L)
                var sentBytes = 0L
                conn.outputStream.use { out ->
                    out.write(header)
                    file.inputStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            sentBytes += read
                            onUploadProgress((sentBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    out.write(footer)
                    out.flush()
                }
                if (conn.responseCode in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                    if (resp.startsWith("https://", ignoreCase = true) && !resp.contains(' ')) resp else null
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun tryUploadApkToLitterbox(
        apkFile: File,
        fileName: String,
        onUploadProgress: (Float) -> Unit
    ): String? {
        val candidate = tryUploadBinaryToLitterbox(
            file = apkFile,
            fileName = fileName,
            contentType = "application/vnd.android.package-archive",
            onUploadProgress = onUploadProgress
        ) ?: return null
        return if (verifyDirectApkDownloadUrl(candidate)) candidate else null
    }

    private fun tryUploadBinaryToLitterbox(
        file: File,
        fileName: String,
        contentType: String,
        onUploadProgress: (Float) -> Unit
    ): String? {
        return runCatching {
            val boundary = "----OrbitLitterBoundary${UUID.randomUUID().toString().replace("-", "")}"
            val header = buildString {
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n")
                append("fileupload\r\n")
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"time\"\r\n\r\n")
                append("72h\r\n")
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"$fileName\"\r\n")
                append("Content-Type: $contentType\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)
            val footer = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            val payloadLength = header.size.toLong() + file.length() + footer.size.toLong()

            val conn = (URL("https://litterbox.catbox.moe/resources/internals/api.php").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                connectTimeout = 20_000
                readTimeout = 90_000
                setFixedLengthStreamingMode(payloadLength)
                setRequestProperty("User-Agent", "Orbit-Android/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            try {
                val totalBytes = file.length().coerceAtLeast(1L)
                var sentBytes = 0L
                conn.outputStream.use { out ->
                    out.write(header)
                    file.inputStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            sentBytes += read
                            onUploadProgress((sentBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    out.write(footer)
                    out.flush()
                }
                if (conn.responseCode in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                    if (resp.startsWith("https://", ignoreCase = true) && !resp.contains(' ')) resp else null
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun tryUploadApkToTmpfiles(
        apkFile: File,
        fileName: String,
        onUploadProgress: (Float) -> Unit
    ): String? {
        val candidate = tryUploadBinaryToTmpfiles(
            file = apkFile,
            fileName = fileName,
            contentType = "application/vnd.android.package-archive",
            onUploadProgress = onUploadProgress
        ) ?: return null
        return if (verifyDirectApkDownloadUrl(candidate)) candidate else null
    }

    private fun tryUploadBinaryToTmpfiles(
        file: File,
        fileName: String,
        contentType: String,
        onUploadProgress: (Float) -> Unit
    ): String? {
        return runCatching {
            val boundary = "----OrbitTmpBoundary${UUID.randomUUID().toString().replace("-", "")}"
            val header = buildString {
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                append("Content-Type: $contentType\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)
            val footer = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            val payloadLength = header.size.toLong() + file.length() + footer.size.toLong()

            val conn = (URL("https://tmpfiles.org/api/v1/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                connectTimeout = 20_000
                readTimeout = 90_000
                setFixedLengthStreamingMode(payloadLength)
                setRequestProperty("User-Agent", "Orbit-Android/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            try {
                val totalBytes = file.length().coerceAtLeast(1L)
                var sentBytes = 0L
                conn.outputStream.use { out ->
                    out.write(header)
                    file.inputStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            sentBytes += read
                            onUploadProgress((sentBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    out.write(footer)
                    out.flush()
                }
                if (conn.responseCode in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val rawUrl = Regex(""""url"\s*:\s*"([^"]+)"""").find(resp)?.groupValues?.getOrNull(1)
                        ?.replace("\\/", "/")
                        ?.trim()
                        .orEmpty()
                    if (rawUrl.isNotBlank()) {
                        normalizeTmpfilesDownloadUrl(rawUrl).takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * Copies the currently installed Orbit APK into `cacheDir/shared/Orbit-v<version>.apk` and
     * returns a `content://` URI via `FileProvider` so it can be shared directly through
     * WhatsApp, Messages, Telegram, Gmail, or Quick Share. Rejects split/corrupt APKs cleanly.
     */
    suspend fun prepareShareableInstalledApkUri(context: Context): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val integrity = inspectInstalledApkIntegrity(context)
            if (!integrity.isShareable) return@runCatching null

            val sourcePath = context.applicationInfo?.sourceDir.orEmpty()
            val sourceFile = File(sourcePath)
            val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
            val safeVer = BuildConfig.VERSION_NAME.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            val targetFile = File(sharedDir, "Orbit-v$safeVer.apk")
            val needsRefresh = !targetFile.exists() ||
                targetFile.length() != sourceFile.length() ||
                targetFile.lastModified() < sourceFile.lastModified()
            if (needsRefresh) {
                val tempFile = File(sharedDir, "Orbit-v$safeVer.apk.tmp")
                sourceFile.inputStream().use { input ->
                    tempFile.outputStream().use { out ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                        out.flush()
                    }
                }
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }
            val authority = "${context.packageName}.fileprovider"
            androidx.core.content.FileProvider.getUriForFile(context, authority, targetFile)
        }.getOrNull()
    }

    /**
     * Builds an invitation message containing the working direct APK download link so friends can
     * tap the link in any browser and download the Orbit Android app immediately.
     */
    fun buildOpenInviteShareMessage(
        inviterUsername: String,
        versionName: String,
        apkDownloadUrl: String = ""
    ): String {
        val handlePart = inviterUsername.trim().removePrefix("@").takeIf { it.isNotBlank() }?.let { " (@$it)" }.orEmpty()
        val resolvedDownloadUrl = resolveOpenShareableUrl(apkDownloadUrl)
        return buildString {
            append("You're invited to join me$handlePart on Orbit (v$versionName)!\n\n")
            if (resolvedDownloadUrl.isNotBlank()) {
                append("Download the Orbit Android App (APK):\n")
                append(resolvedDownloadUrl)
            } else {
                append("Install the attached Orbit-v$versionName.apk to join me on Orbit!")
            }
        }
    }

    /**
     * Builds a personalized invitation and direct download pass for a specific set of chosen people
     * so the user can share Orbit with selected friends/contacts.
     */
    fun buildSelectiveInviteShareMessage(
        inviterUsername: String,
        versionName: String,
        recipients: List<String>,
        apkDownloadUrl: String = ""
    ): String {
        val cleanRecipients = recipients
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val handlePart = inviterUsername.trim().removePrefix("@").takeIf { it.isNotBlank() }?.let { " (@$it)" }.orEmpty()
        val resolvedDownloadUrl = resolveOpenShareableUrl(apkDownloadUrl)
        return buildString {
            if (cleanRecipients.isNotEmpty()) {
                append("Private Orbit Invite for ${cleanRecipients.joinToString(", ")}\n\n")
            }
            append("You've been invited by me$handlePart to download and join Orbit (v$versionName Beta)!\n\n")
            if (resolvedDownloadUrl.isNotBlank()) {
                append("Tap to download the Orbit Android App (APK):\n")
                append(resolvedDownloadUrl)
                append("\n\nOnce downloaded, tap the APK file on your Android phone to install Orbit.")
            } else {
                append("Open the attached Orbit-v$versionName.apk on your Android phone to install and join Orbit!")
            }
        }
    }

    /**
     * Converts standard Google Drive share links or direct APK URLs into direct download endpoints
     * so HttpURLConnection can stream the APK with real progress into Downloads/Orbit.
     */
    fun resolveGoogleDriveDirectUrl(rawUrl: String): String {
        return resolveOpenShareableUrl(rawUrl)
    }

    /**
     * Streams the latest Orbit APK from a direct HTTPS or Google Drive URL into the user's Downloads/Orbit
     * folder while verifying the ZIP/APK `PK\x03\x04` binary header and full byte length so corrupted HTML
     * error pages are never saved as `.apk` files.
     */
    suspend fun downloadDriveApkWithProgress(
        context: Context,
        driveUrl: String,
        versionName: String,
        onProgress: (Float, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val directUrl = resolveGoogleDriveDirectUrl(driveUrl)
            require(directUrl.startsWith("https://") || directUrl.startsWith("http://")) {
                "No valid APK download link is available."
            }
            onProgress(0.03f, "Connecting to download server...")
            var currentUrl = directUrl
            var connection: HttpURLConnection? = null
            var redirects = 0
            while (redirects < 6) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Orbit-Android/${BuildConfig.VERSION_NAME}")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) error("Redirect failed from download server.")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                } else {
                    connection = conn
                    break
                }
            }
            val conn = connection ?: error("Could not reach download server.")
            try {
                val status = conn.responseCode
                if (status !in 200..299) {
                    error("Download link returned HTTP $status (link may have expired).")
                }
                val contentType = conn.contentType.orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) {
                    error("OPEN_IN_BROWSER")
                }

                val totalBytes = conn.contentLengthLong.coerceAtLeast(-1L)
                val safeVer = versionName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").ifBlank { BuildConfig.VERSION_NAME }
                val fileName = "Orbit-$safeVer.apk"
                val buffer = ByteArray(32 * 1024)
                var downloaded = 0L

                val stream = conn.inputStream
                // Read enough initial bytes to validate the ZIP/APK PK\x03\x04 magic signature
                val firstChunk = ByteArray(32 * 1024)
                var firstRead = 0
                while (firstRead < 4) {
                    val r = stream.read(firstChunk, firstRead, firstChunk.size - firstRead)
                    if (r <= 0) break
                    firstRead += r
                }
                if (!hasApkZipMagicHeader(firstChunk, firstRead)) {
                    if (isHtmlOrTextErrorPayload(firstChunk, firstRead)) {
                        error("OPEN_IN_BROWSER")
                    }
                    error("The download link did not return a valid Android APK package.")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Orbit")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val insertedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Failed to create file in Downloads.")
                    try {
                        stream.use { input ->
                            resolver.openOutputStream(insertedUri)?.use { out ->
                                out.write(firstChunk, 0, firstRead)
                                downloaded += firstRead
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    out.write(buffer, 0, read)
                                    downloaded += read
                                    val pct = if (totalBytes > 0L) {
                                        (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                                    } else {
                                        (0.15f + (downloaded.toFloat() / (downloaded + 2_000_000f)) * 0.8f).coerceIn(0.15f, 0.95f)
                                    }
                                    val statusText = if (totalBytes > 0L) {
                                        "Downloading ${formatFileSize(downloaded)} / ${formatFileSize(totalBytes)} (${(pct * 100).toInt()}%)"
                                    } else {
                                        "Downloading ${formatFileSize(downloaded)}..."
                                    }
                                    onProgress(pct, statusText)
                                }
                                out.flush()
                            } ?: error("Failed to write to Downloads.")
                        }
                        if (totalBytes > 0L && downloaded != totalBytes) {
                            error("APK download was incomplete ($downloaded of $totalBytes bytes).")
                        }
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(insertedUri, values, null, null)
                    } catch (e: Throwable) {
                        runCatching { resolver.delete(insertedUri, null, null) }
                        throw e
                    }
                } else {
                    val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    val outFile = File(targetDir, fileName)
                    try {
                        stream.use { input ->
                            outFile.outputStream().use { out ->
                                out.write(firstChunk, 0, firstRead)
                                downloaded += firstRead
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    out.write(buffer, 0, read)
                                    downloaded += read
                                    val pct = if (totalBytes > 0L) {
                                        (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                                    } else {
                                        (0.15f + (downloaded.toFloat() / (downloaded + 2_000_000f)) * 0.8f).coerceIn(0.15f, 0.95f)
                                    }
                                    val statusText = if (totalBytes > 0L) {
                                        "Downloading ${formatFileSize(downloaded)} / ${formatFileSize(totalBytes)} (${(pct * 100).toInt()}%)"
                                    } else {
                                        "Downloading ${formatFileSize(downloaded)}..."
                                    }
                                    onProgress(pct, statusText)
                                }
                                out.flush()
                            }
                        }
                        if (totalBytes > 0L && downloaded != totalBytes) {
                            error("APK download was incomplete ($downloaded of $totalBytes bytes).")
                        }
                        @Suppress("DEPRECATION")
                        val pkgInfo = runCatching {
                            context.packageManager.getPackageArchiveInfo(outFile.absolutePath, 0)
                        }.getOrNull()
                        require(pkgInfo != null && pkgInfo.packageName == context.packageName) {
                            "Downloaded APK failed package verification."
                        }
                    } catch (e: Throwable) {
                        runCatching { outFile.delete() }
                        throw e
                    }
                }
                onProgress(1f, "Saved $fileName (${formatFileSize(downloaded)}) to Downloads/Orbit")
                "Saved $fileName to Downloads/Orbit"
            } finally {
                conn.disconnect()
            }
        }
    }

    private enum class DownloadKind { IMAGE, VIDEO, DOCUMENT }

    private fun saveBytesZeroPermission(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        kind: DownloadKind
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val (collectionUri, relativeDir, label) = when (kind) {
                DownloadKind.IMAGE -> Triple(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${Environment.DIRECTORY_PICTURES}/Orbit",
                    "Gallery"
                )
                DownloadKind.VIDEO -> Triple(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "${Environment.DIRECTORY_MOVIES}/Orbit",
                    "Movies"
                )
                DownloadKind.DOCUMENT -> Triple(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${Environment.DIRECTORY_DOWNLOADS}/Orbit",
                    "Downloads"
                )
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val insertedUri = resolver.insert(collectionUri, values)
                ?: error("Failed to create destination file.")
            resolver.openOutputStream(insertedUri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: error("Failed to write downloaded file.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(insertedUri, values, null, null)
            return "Saved $fileName to $label"
        } else {
            val dirType = when (kind) {
                DownloadKind.IMAGE -> Environment.DIRECTORY_PICTURES
                DownloadKind.VIDEO -> Environment.DIRECTORY_MOVIES
                DownloadKind.DOCUMENT -> Environment.DIRECTORY_DOWNLOADS
            }
            val targetDir = context.getExternalFilesDir(dirType) ?: context.filesDir
            val outFile = File(targetDir, fileName)
            outFile.writeBytes(bytes)
            return "Saved $fileName"
        }
    }

    private fun saveStreamZeroPermission(
        context: Context,
        input: InputStream,
        fileName: String,
        mimeType: String,
        kind: DownloadKind
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val (collectionUri, relativeDir, label) = when (kind) {
                DownloadKind.IMAGE -> Triple(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${Environment.DIRECTORY_PICTURES}/Orbit",
                    "Gallery"
                )
                DownloadKind.VIDEO -> Triple(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "${Environment.DIRECTORY_MOVIES}/Orbit",
                    "Movies"
                )
                DownloadKind.DOCUMENT -> Triple(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${Environment.DIRECTORY_DOWNLOADS}/Orbit",
                    "Downloads"
                )
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val insertedUri = resolver.insert(collectionUri, values)
                ?: error("Failed to create destination file.")
            resolver.openOutputStream(insertedUri)?.use { out ->
                input.copyTo(out, bufferSize = 64 * 1024)
                out.flush()
            } ?: error("Failed to write downloaded file.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(insertedUri, values, null, null)
            return "Saved $fileName to $label"
        } else {
            val dirType = when (kind) {
                DownloadKind.IMAGE -> Environment.DIRECTORY_PICTURES
                DownloadKind.VIDEO -> Environment.DIRECTORY_MOVIES
                DownloadKind.DOCUMENT -> Environment.DIRECTORY_DOWNLOADS
            }
            val targetDir = context.getExternalFilesDir(dirType) ?: context.filesDir
            val outFile = File(targetDir, fileName)
            outFile.outputStream().use { out ->
                input.copyTo(out, bufferSize = 64 * 1024)
                out.flush()
            }
            return "Saved $fileName"
        }
    }

    internal fun decodeBytesFromUrlOrDataUri(urlOrDataUri: String): ByteArray? {
        val hd2kIdx = urlOrDataUri.indexOf(OrbitCacheManager.HD_2K_FRAGMENT_DELIMITER)
        if (hd2kIdx >= 0) {
            val hd2kUrl = urlOrDataUri
                .substring(hd2kIdx + OrbitCacheManager.HD_2K_FRAGMENT_DELIMITER.length)
                .trim()
            if (hd2kUrl.startsWith("https://", ignoreCase = true) || hd2kUrl.startsWith("http://", ignoreCase = true)) {
                decodeBytesFromUrlOrDataUri(hd2kUrl)?.let { return it }
            }
            val previewPart = urlOrDataUri.substring(0, hd2kIdx)
            return decodeBytesFromUrlOrDataUri(previewPart)
        }
        if (urlOrDataUri.startsWith("data:")) {
            val comma = urlOrDataUri.indexOf(',')
            if (comma in 1 until urlOrDataUri.lastIndex) {
                return runCatching {
                    Base64.decode(urlOrDataUri.substring(comma + 1), Base64.DEFAULT)
                }.getOrNull()
            }
            return null
        }
        if (urlOrDataUri.startsWith("http://") || urlOrDataUri.startsWith("https://")) {
            return runCatching {
                val conn = (URL(urlOrDataUri).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                }
                try {
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { it.readBytes() }
                    } else {
                        null
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
        return null
    }

    private fun sanitizeFileName(raw: String, defaultExt: String?): String {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").take(100).ifBlank { "orbit_file" }
        return if (defaultExt != null && !cleaned.contains('.')) "$cleaned.$defaultExt" else cleaned
    }
}
