package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.cache.OrbitMediaFiles
import com.example.ui.adaptive.FoldableTabletopSplitLayout
import com.example.ui.adaptive.LocalFoldableLayoutInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-screen interactive cropper for Profile Pictures (1:1 circle) and Profile Banners (2.8:1 wide).
 * Supports smooth pinch-to-zoom, drag-to-pan, and a precision zoom slider.
 */
@Composable
fun ImageCropperDialog(
    sourceUri: Uri,
    isBanner: Boolean,
    onDismiss: () -> Unit,
    onCropped: (dataUri: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val foldableInfo = LocalFoldableLayoutInfo.current

    var sourceBitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(sourceUri) { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var userScale by remember(sourceUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(sourceUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(sourceUri) { mutableFloatStateOf(0f) }

    LaunchedEffect(sourceUri) {
        val decoded = withContext(Dispatchers.IO) { decodeOrientedBitmap(context, sourceUri) }
        if (decoded != null) {
            sourceBitmap = decoded
        } else {
            loadError = "Unable to open the selected image."
        }
    }

    val cropRect = remember(viewportSize, isBanner) {
        computeCropRect(viewportSize, isBanner)
    }

    val bmp = sourceBitmap
    val coverScale = remember(bmp, cropRect) {
        if (bmp == null || cropRect.isEmpty) {
            1f
        } else {
            max(
                cropRect.width / bmp.width.coerceAtLeast(1).toFloat(),
                cropRect.height / bmp.height.coerceAtLeast(1).toFloat()
            )
        }
    }

    fun clampOffsets(rawX: Float, rawY: Float, scale: Float): Pair<Float, Float> {
        val currentBmp = sourceBitmap ?: return 0f to 0f
        if (cropRect.isEmpty) return 0f to 0f
        val renderedW = currentBmp.width * coverScale * scale
        val renderedH = currentBmp.height * coverScale * scale
        val maxOffsetX = ((renderedW - cropRect.width) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((renderedH - cropRect.height) / 2f).coerceAtLeast(0f)
        return rawX.coerceIn(-maxOffsetX, maxOffsetX) to rawY.coerceIn(-maxOffsetY, maxOffsetY)
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF0A0A0D),
            modifier = Modifier.fillMaxSize()
        ) {
            val cropCanvas: @Composable (Modifier) -> Unit = { canvasModifier ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = canvasModifier
                        .fillMaxWidth()
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(sourceBitmap, cropRect, coverScale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (userScale * zoom).coerceIn(1f, 4f)
                                val (clampedX, clampedY) = clampOffsets(
                                    offsetX + pan.x,
                                    offsetY + pan.y,
                                    nextScale
                                )
                                userScale = nextScale
                                offsetX = clampedX
                                offsetY = clampedY
                            }
                        }
                ) {
                    when {
                        loadError != null -> {
                            Text(
                                text = loadError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        bmp == null -> {
                            CircularProgressIndicator(color = Color.White)
                        }

                        else -> {
                            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                            val fitScale = if (viewportSize.width > 0 && viewportSize.height > 0) {
                                min(
                                    viewportSize.width.toFloat() / bmp.width.coerceAtLeast(1),
                                    viewportSize.height.toFloat() / bmp.height.coerceAtLeast(1)
                                )
                            } else {
                                1f
                            }
                            val graphicsScale = (coverScale * userScale) / fitScale.coerceAtLeast(0.0001f)

                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Crop preview image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = graphicsScale,
                                        scaleY = graphicsScale,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    )
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (!cropRect.isEmpty) {
                                    val cutoutPath = Path().apply {
                                        if (isBanner) {
                                            addRoundRect(
                                                androidx.compose.ui.geometry.RoundRect(
                                                    rect = cropRect,
                                                    cornerRadius = CornerRadius(18f, 18f)
                                                )
                                            )
                                        } else {
                                            addOval(cropRect)
                                        }
                                    }

                                    clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                                        drawRect(color = Color.Black.copy(alpha = 0.68f))
                                    }

                                    val thirdW = cropRect.width / 3f
                                    val thirdH = cropRect.height / 3f
                                    val gridColor = Color.White.copy(alpha = 0.30f)
                                    clipPath(cutoutPath) {
                                        for (i in 1..2) {
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(cropRect.left + thirdW * i, cropRect.top),
                                                end = Offset(cropRect.left + thirdW * i, cropRect.bottom),
                                                strokeWidth = 1.5f
                                            )
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(cropRect.left, cropRect.top + thirdH * i),
                                                end = Offset(cropRect.right, cropRect.top + thirdH * i),
                                                strokeWidth = 1.5f
                                            )
                                        }
                                    }

                                    if (isBanner) {
                                        drawRoundRect(
                                            color = Color.White,
                                            topLeft = cropRect.topLeft,
                                            size = cropRect.size,
                                            cornerRadius = CornerRadius(18f, 18f),
                                            style = Stroke(width = 3f)
                                        )
                                    } else {
                                        drawOval(
                                            color = Color.White,
                                            topLeft = cropRect.topLeft,
                                            size = cropRect.size,
                                            style = Stroke(width = 3f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val performSave: () -> Unit = {
                val currentBmp = sourceBitmap
                if (currentBmp != null && !cropRect.isEmpty && !isSaving) {
                    isSaving = true
                    scope.launch {
                        val dataUri = withContext(Dispatchers.IO) {
                            cropBitmapAndEncode(
                                bitmap = currentBmp,
                                viewportSize = viewportSize,
                                cropRect = cropRect,
                                totalScale = coverScale * userScale,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                isBanner = isBanner
                            )
                        }
                        isSaving = false
                        onCropped(dataUri)
                    }
                }
            }

            if (foldableInfo.isTabletop) {
                // Tabletop posture: Crop preview on top screen above hinge, controls comfortably on flat base
                FoldableTabletopSplitLayout(
                    info = foldableInfo,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    topPane = {
                        cropCanvas(Modifier.fillMaxSize())
                    },
                    bottomPane = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF121218))
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = if (isBanner) "Crop profile banner (Tabletop)" else "Crop profile photo (Tabletop)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ZoomIn,
                                    contentDescription = "Zoom level",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Slider(
                                    value = userScale,
                                    onValueChange = { nextScale ->
                                        val (clampedX, clampedY) = clampOffsets(offsetX, offsetY, nextScale)
                                        userScale = nextScale
                                        offsetX = clampedX
                                        offsetY = clampedY
                                    },
                                    valueRange = 1f..4f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cropper_zoom_slider")
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = onDismiss,
                                    enabled = !isSaving,
                                    modifier = Modifier.testTag("cropper_cancel_button")
                                ) {
                                    Text("Cancel", color = Color.White.copy(alpha = 0.85f))
                                }
                                TextButton(
                                    onClick = {
                                        userScale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    },
                                    enabled = !isSaving,
                                    modifier = Modifier.testTag("cropper_reset_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.RestartAlt,
                                        contentDescription = "Reset zoom",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reset", color = Color.White.copy(alpha = 0.85f))
                                }
                                Button(
                                    onClick = performSave,
                                    enabled = sourceBitmap != null && !isSaving,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.testTag("cropper_save_button")
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text("Save")
                                    }
                                }
                            }
                        }
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            enabled = !isSaving,
                            modifier = Modifier.testTag("cropper_cancel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel crop",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = if (isBanner) "Crop profile banner" else "Crop profile photo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = {
                                userScale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            enabled = !isSaving,
                            modifier = Modifier.testTag("cropper_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset zoom",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Reset", color = Color.White.copy(alpha = 0.85f))
                        }

                        Spacer(Modifier.width(4.dp))

                        Button(
                            onClick = performSave,
                            enabled = sourceBitmap != null && !isSaving,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("cropper_save_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text("Save")
                            }
                        }
                    }

                    cropCanvas(Modifier.weight(1f))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Drag to reposition  ·  Pinch or slide to zoom",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomIn,
                                contentDescription = "Zoom level",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                            Slider(
                                value = userScale,
                                onValueChange = { nextScale ->
                                    val (clampedX, clampedY) = clampOffsets(offsetX, offsetY, nextScale)
                                    userScale = nextScale
                                    offsetX = clampedX
                                    offsetY = clampedY
                                },
                                valueRange = 1f..4f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cropper_zoom_slider")
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun computeCropRect(viewportSize: IntSize, isBanner: Boolean): Rect {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return Rect.Zero
    val margin = viewportSize.width * 0.06f
    val maxW = (viewportSize.width - margin * 2f).coerceAtLeast(100f)
    val maxH = (viewportSize.height - margin * 2f).coerceAtLeast(100f)

    val (cropW, cropH) = if (isBanner) {
        val aspect = 2.8f
        val w = maxW
        val h = (w / aspect).coerceAtMost(maxH)
        (h * aspect) to h
    } else {
        val side = min(maxW, maxH) * 0.92f
        side to side
    }

    val left = (viewportSize.width - cropW) / 2f
    val top = (viewportSize.height - cropH) / 2f
    return Rect(offset = Offset(left, top), size = Size(cropW, cropH))
}

private suspend fun cropBitmapAndEncode(
    bitmap: Bitmap,
    viewportSize: IntSize,
    cropRect: Rect,
    totalScale: Float,
    offsetX: Float,
    offsetY: Float,
    isBanner: Boolean
): String {
    val safeScale = totalScale.coerceAtLeast(0.0001f)
    val centerX = viewportSize.width / 2f
    val centerY = viewportSize.height / 2f

    val renderedW = bitmap.width * safeScale
    val renderedH = bitmap.height * safeScale
    val bmpLeft = centerX - renderedW / 2f + offsetX
    val bmpTop = centerY - renderedH / 2f + offsetY

    val srcX = ((cropRect.left - bmpLeft) / safeScale).roundToInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
    val srcY = ((cropRect.top - bmpTop) / safeScale).roundToInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
    val srcW = (cropRect.width / safeScale).roundToInt().coerceIn(1, (bitmap.width - srcX).coerceAtLeast(1))
    val srcH = (cropRect.height / safeScale).roundToInt().coerceIn(1, (bitmap.height - srcY).coerceAtLeast(1))

    val cropped = Bitmap.createBitmap(bitmap, srcX, srcY, srcW, srcH)
    return try {
        OrbitMediaFiles.encodeCroppedBitmapToDataUri(cropped, isBanner = isBanner)
    } finally {
        if (cropped !== bitmap) {
            cropped.recycle()
        }
    }
}

private fun decodeOrientedBitmap(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDim = max(info.size.width, info.size.height)
                if (maxDim > 1600) {
                    val scale = 1600f / maxDim
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val maxDim = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxDim / sampleSize > 1600) sampleSize *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }
    }.getOrNull()
}
