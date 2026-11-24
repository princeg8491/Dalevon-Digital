package com.example.ui.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.cache.OrbitCacheManager
import com.example.data.cache.OrbitMediaFiles
import com.example.data.model.CommunityPost
import com.example.data.model.PostAttachment
import com.example.data.model.ThreadPost
import com.example.ui.components.stripOrbitRichText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Native Android Sharesheet helper for sharing Orbit posts, frames, Space discussions,
 * media links, inline photo/video/file attachments, and rich Sharesheet image thumbnail
 * previews to other apps installed on the device.
 */
object OrbitShareHelper {

    private const val SHARED_DIR_NAME = "shared"
    private const val MAX_CACHED_SHARED_FILES = 18
    private const val THUMBNAIL_MAX_DIM_PX = 512
    private const val REMOTE_THUMBNAIL_TIMEOUT_MS = 1_800L

    private val shareScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun shareThreadPost(context: Context, post: ThreadPost) {
        val authorHandle = post.authorUsername.ifBlank { "orbit_user" }
        val authorLine = buildString {
            if (post.authorDisplayName.isNotBlank() && !post.authorDisplayName.equals(authorHandle, ignoreCase = true)) {
                append("${post.authorDisplayName} (@$authorHandle)")
            } else {
                append("@$authorHandle")
            }
            if (post.topic.isNotBlank()) {
                append(" · #${post.topic}")
            }
        }

        val httpMediaLinks = collectHttpMediaLinks(
            imageUrls = post.imageUrls + listOfNotNull(post.quotedImageUrl.takeIf { it.isNotBlank() }),
            attachments = post.attachments
        )
        val nonHttpAttachmentNames = post.attachments
            .filter { !isHttpUrl(it.dataUri) && it.fileName.isNotBlank() }
            .map { it.fileName }
            .distinct()

        val cleanBody = stripOrbitRichText(post.body).trim()
        val cleanQuotedBody = stripOrbitRichText(post.quotedBody).trim()
        val shareText = buildString {
            append(authorLine)
            if (cleanBody.isNotBlank()) {
                append("\n\n")
                append(cleanBody)
            }
            if (post.hasQuotedPost) {
                append("\n\nQuoted @${post.quotedAuthorUsername.ifBlank { "orbit_user" }}:")
                if (cleanQuotedBody.isNotBlank()) {
                    append(" \"$cleanQuotedBody\"")
                }
            }
            if (httpMediaLinks.isNotEmpty()) {
                append("\n\nMedia:")
                httpMediaLinks.forEach { link ->
                    append("\n$link")
                }
            }
            if (nonHttpAttachmentNames.isNotEmpty()) {
                append("\n\nAttachments: ${nonHttpAttachmentNames.joinToString(", ")}")
            }
            append("\n\nShared via Orbit")
        }

        val primaryPreviewSource = resolvePrimaryPreviewImageSource(
            imageUrls = post.imageUrls,
            attachments = post.attachments,
            fallbackImageUrl = post.quotedImageUrl
        )

        val subject = "Post by @$authorHandle on Orbit"
        val previewTitle = cleanBody.take(72).ifBlank { subject }
        val prefix = "post_${post.id.ifBlank { "item" }}"

        prepareAndLaunchShareSheet(
            context = context,
            prefix = prefix,
            text = shareText,
            subject = subject,
            previewTitle = previewTitle,
            chooserTitle = "Share post",
            imageUrls = post.imageUrls,
            attachments = post.attachments,
            previewImageSource = primaryPreviewSource
        )
    }

    fun shareCommunityPost(
        context: Context,
        post: CommunityPost,
        spaceName: String = ""
    ) {
        val authorHandle = post.authorUsername.ifBlank { "orbit_user" }
        val header = buildString {
            if (post.authorDisplayName.isNotBlank() && !post.authorDisplayName.equals(authorHandle, ignoreCase = true)) {
                append("${post.authorDisplayName} (@$authorHandle)")
            } else {
                append("@$authorHandle")
            }
            if (spaceName.isNotBlank()) {
                append(" in $spaceName")
            }
            if (post.streamCategory.isNotBlank()) {
                append(" · ${post.streamCategory}")
            }
        }

        val httpMediaLinks = collectHttpMediaLinks(
            imageUrls = post.imageUrls,
            attachments = post.attachments
        )
        val nonHttpAttachmentNames = post.attachments
            .filter { !isHttpUrl(it.dataUri) && it.fileName.isNotBlank() }
            .map { it.fileName }
            .distinct()

        val shareText = buildString {
            append(header)
            if (post.body.isNotBlank()) {
                append("\n\n")
                append(post.body.trim())
            }
            if (httpMediaLinks.isNotEmpty()) {
                append("\n\nMedia:")
                httpMediaLinks.forEach { link ->
                    append("\n$link")
                }
            }
            if (nonHttpAttachmentNames.isNotEmpty()) {
                append("\n\nAttachments: ${nonHttpAttachmentNames.joinToString(", ")}")
            }
            append("\n\nShared via Orbit Spaces")
        }

        val primaryPreviewSource = resolvePrimaryPreviewImageSource(
            imageUrls = post.imageUrls,
            attachments = post.attachments
        )

        val subject = if (spaceName.isNotBlank()) {
            "$spaceName · Post by @$authorHandle"
        } else {
            "Space post by @$authorHandle on Orbit"
        }
        val previewTitle = post.body.trim().take(72).ifBlank { subject }
        val prefix = "space_${post.id.ifBlank { "item" }}"

        prepareAndLaunchShareSheet(
            context = context,
            prefix = prefix,
            text = shareText,
            subject = subject,
            previewTitle = previewTitle,
            chooserTitle = "Share Space post",
            imageUrls = post.imageUrls,
            attachments = post.attachments,
            previewImageSource = primaryPreviewSource
        )
    }

    fun sharePhotoItem(
        context: Context,
        imageUrl: String,
        authorUsername: String = "",
        caption: String = ""
    ) {
        val cleanShareUrl = OrbitCacheManager.extractHd2kUrl(imageUrl)
            ?: OrbitCacheManager.stripHd2kFragment(imageUrl)
        val shareText = buildString {
            if (authorUsername.isNotBlank()) {
                append("@$authorUsername")
                if (caption.isNotBlank()) append(": ")
            }
            if (caption.isNotBlank()) {
                append(caption.trim())
            }
            if (isHttpUrl(cleanShareUrl)) {
                if (isNotEmpty()) append("\n\n")
                append(cleanShareUrl.trim())
            }
            if (isNotEmpty()) append("\n\n")
            append("Shared via Orbit")
        }

        val subject = if (authorUsername.isNotBlank()) {
            "Photo by @$authorUsername on Orbit"
        } else {
            "Photo from Orbit"
        }

        val appContext = context.applicationContext
        val previewTitle = caption.trim().take(72).ifBlank { subject }
        shareScope.launch {
            val payload = withContext(Dispatchers.IO) {
                val streams = mutableListOf<ShareableStream>()
                val sharedDir = File(appContext.cacheDir, SHARED_DIR_NAME).apply { mkdirs() }
                pruneOldSharedFiles(sharedDir)
                val photoBytes = OrbitMediaFiles.decodeBytesFromUrlOrDataUri(imageUrl)
                if (photoBytes != null && photoBytes.isNotEmpty()) {
                    runCatching {
                        val isGif = OrbitMediaFiles.hasGifMagicHeader(photoBytes)
                        val ext = if (isGif) "gif" else "jpg"
                        val mime = if (isGif) "image/gif" else "image/jpeg"
                        val outFile = File(sharedDir, "photo_${System.currentTimeMillis()}.$ext")
                        outFile.writeBytes(photoBytes)
                        val authority = "${appContext.packageName}.fileprovider"
                        val uri = FileProvider.getUriForFile(appContext, authority, outFile)
                        streams.add(ShareableStream(uri = uri, mimeType = mime))
                    }
                }
                val thumbUri = materializeThumbnailPreviewUri(
                    context = appContext,
                    prefix = "photo",
                    imageSource = OrbitCacheManager.stripHd2kFragment(imageUrl)
                )
                PreparedSharePayload(
                    mediaStreams = streams,
                    thumbnailPreviewUri = thumbUri
                )
            }

            launchNativeShareSheet(
                context = context,
                text = shareText,
                subject = subject,
                previewTitle = previewTitle,
                chooserTitle = "Share photo",
                mediaStreams = payload.mediaStreams,
                thumbnailPreviewUri = payload.thumbnailPreviewUri
            )
        }
    }

    private data class ShareableStream(
        val uri: Uri,
        val mimeType: String
    )

    private data class PreparedSharePayload(
        val mediaStreams: List<ShareableStream>,
        val thumbnailPreviewUri: Uri?
    )

    private fun prepareAndLaunchShareSheet(
        context: Context,
        prefix: String,
        text: String,
        subject: String,
        previewTitle: String,
        chooserTitle: String,
        imageUrls: List<String>,
        attachments: List<PostAttachment>,
        previewImageSource: String?
    ) {
        val appContext = context.applicationContext
        shareScope.launch {
            val payload = withContext(Dispatchers.IO) {
                val streams = materializeInlineMediaUris(
                    context = appContext,
                    prefix = prefix,
                    imageUrls = imageUrls,
                    attachments = attachments
                )
                val thumbUri = previewImageSource?.let { source ->
                    materializeThumbnailPreviewUri(
                        context = appContext,
                        prefix = prefix,
                        imageSource = source
                    )
                }
                PreparedSharePayload(
                    mediaStreams = streams,
                    thumbnailPreviewUri = thumbUri
                )
            }

            launchNativeShareSheet(
                context = context,
                text = text,
                subject = subject,
                previewTitle = previewTitle,
                chooserTitle = chooserTitle,
                mediaStreams = payload.mediaStreams,
                thumbnailPreviewUri = payload.thumbnailPreviewUri
            )
        }
    }

    private fun launchNativeShareSheet(
        context: Context,
        text: String,
        subject: String,
        previewTitle: String,
        chooserTitle: String,
        mediaStreams: List<ShareableStream>,
        thumbnailPreviewUri: Uri?
    ) {
        val sendIntent = when (mediaStreams.size) {
            0 -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TITLE, previewTitle)
                if (thumbnailPreviewUri != null) {
                    clipData = ClipData.newUri(context.contentResolver, previewTitle, thumbnailPreviewUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            1 -> {
                val stream = mediaStreams.first()
                Intent(Intent.ACTION_SEND).apply {
                    type = stream.mimeType
                    putExtra(Intent.EXTRA_STREAM, stream.uri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TITLE, previewTitle)
                    clipData = ClipData.newUri(context.contentResolver, previewTitle, stream.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            else -> {
                val uris = ArrayList(mediaStreams.map { it.uri })
                val commonMime = resolveCommonMimeType(mediaStreams.map { it.mimeType })
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = commonMime
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TITLE, previewTitle)
                    val clip = ClipData.newUri(context.contentResolver, previewTitle, uris.first())
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }

        val chooserPreviewUri = thumbnailPreviewUri ?: mediaStreams.firstOrNull()?.uri
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            if (chooserPreviewUri != null) {
                clipData = ClipData.newUri(context.contentResolver, previewTitle, chooserPreviewUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        runCatching {
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "No compatible app found to share this post.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolvePrimaryPreviewImageSource(
        imageUrls: List<String>,
        attachments: List<PostAttachment>,
        fallbackImageUrl: String = ""
    ): String? {
        imageUrls.firstOrNull { it.isNotBlank() }?.let { return it.trim() }
        attachments.firstOrNull { it.isImage && it.dataUri.isNotBlank() }?.let { return it.dataUri.trim() }
        attachments.firstNotNullOfOrNull { att ->
            if (att.isVideo) OrbitMediaFiles.extractPosterFromVideoDataUri(att.dataUri) else null
        }?.let { return it }
        return fallbackImageUrl.trim().takeIf { it.isNotBlank() }
    }

    /**
     * Produces a compact JPEG thumbnail (<= 512px) in cacheDir/shared exposed via FileProvider
     * for Android Sharesheet rich image previews — supporting both inline data: URIs and remote
     * HTTP(S) post images via Coil's cache.
     */
    private suspend fun materializeThumbnailPreviewUri(
        context: Context,
        prefix: String,
        imageSource: String
    ): Uri? = runCatching {
        val cleanSource = OrbitCacheManager.stripHd2kFragment(imageSource)
        val rawBitmap: Bitmap? = when {
            cleanSource.startsWith("data:image/", ignoreCase = true) -> {
                val commaIndex = cleanSource.indexOf(',')
                if (commaIndex <= 0) null
                else {
                    val bytes = Base64.decode(cleanSource.substring(commaIndex + 1), Base64.DEFAULT)
                    decodeScaledBitmap(bytes, THUMBNAIL_MAX_DIM_PX)
                }
            }
            isHttpUrl(cleanSource) -> {
                withTimeoutOrNull(REMOTE_THUMBNAIL_TIMEOUT_MS) {
                    val loader = OrbitCacheManager.getImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(cleanSource)
                        .size(THUMBNAIL_MAX_DIM_PX)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    val drawable = (result as? SuccessResult)?.drawable ?: return@withTimeoutOrNull null
                    (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
                }
            }
            else -> null
        }

        val bitmap = rawBitmap ?: return null
        val thumbBitmap = downscaleIfNeeded(bitmap, THUMBNAIL_MAX_DIM_PX)

        val sharedDir = File(context.cacheDir, SHARED_DIR_NAME).apply { mkdirs() }
        pruneOldSharedFiles(sharedDir)
        val thumbFile = File(sharedDir, "thumb_${prefix}_${System.currentTimeMillis()}.jpg")
        thumbFile.outputStream().use { out ->
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (thumbBitmap !== bitmap) {
            thumbBitmap.recycle()
        }

        val authority = "${context.packageName}.fileprovider"
        FileProvider.getUriForFile(context, authority, thumbFile)
    }.getOrNull()

    private fun decodeScaledBitmap(bytes: ByteArray, maxDim: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide > 0 && maxSide / sample > maxDim * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val largest = max(width, height)
        if (largest <= maxDim || width <= 0 || height <= 0) return bitmap
        val ratio = maxDim.toFloat() / largest.toFloat()
        val targetW = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun collectHttpMediaLinks(
        imageUrls: List<String>,
        attachments: List<PostAttachment>
    ): List<String> = buildList {
        imageUrls.forEach { raw ->
            val trimmed = (OrbitCacheManager.extractHd2kUrl(raw) ?: OrbitCacheManager.stripHd2kFragment(raw)).trim()
            if (isHttpUrl(trimmed)) add(trimmed)
        }
        attachments.forEach { att ->
            val trimmed = att.dataUri.trim()
            if (isHttpUrl(trimmed)) add(trimmed)
        }
    }.distinct()

    private fun materializeInlineMediaUris(
        context: Context,
        prefix: String,
        imageUrls: List<String>,
        attachments: List<PostAttachment>
    ): List<ShareableStream> {
        val results = mutableListOf<ShareableStream>()
        val sharedDir = File(context.cacheDir, SHARED_DIR_NAME).apply { mkdirs() }
        pruneOldSharedFiles(sharedDir)

        var idx = 0
        imageUrls.forEach { raw ->
            if (raw.startsWith("data:", ignoreCase = true)) {
                decodeDataUriToStream(
                    context = context,
                    dir = sharedDir,
                    baseName = "${prefix}_img_${idx++}",
                    dataUri = raw,
                    fallbackMime = "image/jpeg"
                )?.let(results::add)
            }
        }
        attachments.forEach { att ->
            val cleanUri = att.dataUri.substringBefore('#')
            if (cleanUri.startsWith("file://")) {
                runCatching {
                    val localFile = File(cleanUri.removePrefix("file://"))
                    if (localFile.exists() && localFile.length() > 0L) {
                        val authority = "${context.packageName}.fileprovider"
                        val contentUri = FileProvider.getUriForFile(context, authority, localFile)
                        results.add(
                            ShareableStream(
                                uri = contentUri,
                                mimeType = att.mimeType.ifBlank { "video/mp4" }
                            )
                        )
                    }
                }
            } else if (att.dataUri.startsWith("data:", ignoreCase = true)) {
                val safeBase = att.fileName
                    .substringBeforeLast('.')
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    .take(36)
                    .ifBlank { "${prefix}_att_${idx++}" }
                decodeDataUriToStream(
                    context = context,
                    dir = sharedDir,
                    baseName = safeBase,
                    dataUri = att.dataUri,
                    fallbackMime = att.mimeType.ifBlank { "application/octet-stream" }
                )?.let(results::add)
            }
        }
        return results
    }

    private fun decodeDataUriToStream(
        context: Context,
        dir: File,
        baseName: String,
        dataUri: String,
        fallbackMime: String
    ): ShareableStream? = runCatching {
        val cleanDataUri = OrbitCacheManager.stripHd2kFragment(dataUri)
        val commaIndex = cleanDataUri.indexOf(',')
        if (commaIndex <= 0) return null
        val header = cleanDataUri.substring(5, commaIndex)
        val mime = header.substringBefore(';').ifBlank { fallbackMime }
        val payload = cleanDataUri.substring(commaIndex + 1)
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        if (bytes.isEmpty()) return null

        val ext = extensionForMime(mime)
        val file = File(dir, "${baseName}_${System.currentTimeMillis()}.$ext")
        file.outputStream().use { it.write(bytes) }

        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        ShareableStream(uri = contentUri, mimeType = mime)
    }.getOrNull()

    private fun pruneOldSharedFiles(dir: File) {
        runCatching {
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            if (files.size > MAX_CACHED_SHARED_FILES) {
                files.drop(MAX_CACHED_SHARED_FILES).forEach { it.delete() }
            }
        }
    }

    private fun resolveCommonMimeType(mimes: List<String>): String {
        val distinct = mimes.distinct()
        if (distinct.size == 1) return distinct.first()
        val prefixes = distinct.map { it.substringBefore('/') }.distinct()
        return if (prefixes.size == 1) "${prefixes.first()}/*" else "*/*"
    }

    private fun extensionForMime(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "application/pdf" -> "pdf"
        "text/plain" -> "txt"
        else -> if (mime.startsWith("image/")) "jpg" else "bin"
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
}
