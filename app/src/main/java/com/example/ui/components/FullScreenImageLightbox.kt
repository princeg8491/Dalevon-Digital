package com.example.ui.components

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.cache.OrbitMediaFiles
import com.example.ui.adaptive.FoldableTabletopSplitLayout
import com.example.ui.adaptive.LocalFoldableLayoutInfo
import com.example.ui.haptics.LocalOrbitHaptics
import com.example.ui.share.OrbitShareHelper
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Shared action callbacks for [FullScreenImageLightbox]'s 3-dot overflow menu
 * (Save, Share, Download, Bookmark), provided at the app root so every opened photo
 * across Feeds, Frames, Profiles, Spaces, DMs, and Saved Posts supports all actions.
 */
@Immutable
data class OrbitLightboxActions(
    val onSavePhoto: ((String) -> Unit)? = null,
    val onSharePhoto: ((String) -> Unit)? = null,
    val onDownloadPhoto: ((String) -> Unit)? = null,
    val isBookmarked: ((String) -> Boolean)? = null,
    val onToggleBookmark: ((String) -> Unit)? = null
)

val LocalOrbitLightboxActions = staticCompositionLocalOf { OrbitLightboxActions() }

private enum class LightboxDragMode {
    NONE,
    DISMISS_DRAG,
    ZOOM_OR_PAN
}

@Composable
fun FullScreenImageLightbox(
    imageUrl: String,
    onDismiss: () -> Unit,
    onDownloadClick: (String) -> Unit = {},
    onSaveClick: ((String) -> Unit)? = null,
    onShareClick: ((String) -> Unit)? = null,
    onBookmarkClick: ((String) -> Unit)? = null,
    isBookmarked: Boolean? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalOrbitHaptics.current
    val foldableInfo = LocalFoldableLayoutInfo.current
    val sharedActions = LocalOrbitLightboxActions.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    val previewModel = remember(imageUrl) { OrbitCacheManager.toCoilModel(imageUrl) }
    val hasDedicated2k = remember(imageUrl) { OrbitCacheManager.hasDedicated2kSource(imageUrl) }
    val full2kRequest = remember(context, imageUrl) {
        OrbitCacheManager.buildFullScreen2kImageRequest(context, imageUrl)
    }

    var isLoading2k by remember(imageUrl) { mutableStateOf(hasDedicated2k) }
    var is2kLoaded by remember(imageUrl) { mutableStateOf(false) }

    var chromeVisible by remember { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var localBookmarkedFallback by remember(imageUrl) { mutableStateOf(false) }

    val resolvedBookmarked = isBookmarked
        ?: sharedActions.isBookmarked?.invoke(imageUrl)
        ?: localBookmarkedFallback

    val performSave: () -> Unit = {
        haptics.performTap()
        when {
            onSaveClick != null -> onSaveClick(imageUrl)
            sharedActions.onSavePhoto != null -> sharedActions.onSavePhoto.invoke(imageUrl)
            else -> {
                scope.launch {
                    OrbitMediaFiles.downloadPhotoToDevice(
                        context = context,
                        urlOrDataUri = imageUrl,
                        saveToDownloads = false
                    ).onSuccess { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, err.message ?: "Could not save photo.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val performShare: () -> Unit = {
        haptics.performTap()
        when {
            onShareClick != null -> onShareClick(imageUrl)
            sharedActions.onSharePhoto != null -> sharedActions.onSharePhoto.invoke(imageUrl)
            else -> OrbitShareHelper.sharePhotoItem(context = context, imageUrl = imageUrl)
        }
    }

    val performDownload: () -> Unit = {
        haptics.performTap()
        when {
            sharedActions.onDownloadPhoto != null -> sharedActions.onDownloadPhoto.invoke(imageUrl)
            else -> {
                onDownloadClick(imageUrl)
                scope.launch {
                    OrbitMediaFiles.downloadPhotoToDevice(
                        context = context,
                        urlOrDataUri = imageUrl,
                        saveToDownloads = true
                    ).onSuccess { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, err.message ?: "Download failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val performBookmark: () -> Unit = {
        haptics.performTap()
        when {
            onBookmarkClick != null -> onBookmarkClick(imageUrl)
            sharedActions.onToggleBookmark != null -> sharedActions.onToggleBookmark.invoke(imageUrl)
            else -> {
                localBookmarkedFallback = !localBookmarkedFallback
                Toast.makeText(
                    context,
                    if (localBookmarkedFallback) "Photo bookmarked" else "Bookmark removed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Zoom & pan state
    val zoomScale = remember { Animatable(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Google Photos-style swipe-down dismiss state
    val dismissOffsetX = remember { Animatable(0f) }
    val dismissOffsetY = remember { Animatable(0f) }
    var isDraggingDismiss by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    val fastSwipeMinDragPx = with(density) { 36.dp.toPx() }
    val fastSwipeVelocityPx = with(density) { 800.dp.toPx() }
    val maxDragPx = max(containerSize.height * 0.48f, with(density) { 280.dp.toPx() })
    val downwardDrag = dismissOffsetY.value.coerceAtLeast(0f)
    val dragFraction = (downwardDrag / maxDragPx).coerceIn(0f, 1f)
    val dismissScale = (1f - 0.34f * dragFraction).coerceIn(0.62f, 1f)
    val backdropAlpha = if (isDismissing) {
        (1f - dragFraction * 1.25f).coerceIn(0f, 1f)
    } else {
        (1f - dragFraction * 0.84f).coerceIn(0.10f, 1f)
    }
    val cornerRadiusDp = (dragFraction * 22f).dp

    fun animateDismissExit() {
        if (isDismissing) return
        isDismissing = true
        showMoreMenu = false
        chromeVisible = false
        val exitTargetY = max(containerSize.height.toFloat(), 1200f) * 0.72f
        scope.launch {
            launch {
                dismissOffsetY.animateTo(
                    targetValue = exitTargetY,
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing)
                )
            }
            launch {
                dismissOffsetX.animateTo(
                    targetValue = dismissOffsetX.value * 1.15f,
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing)
                )
            }
        }.invokeOnCompletion {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { animateDismissExit() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Configure the Dialog window to draw pure edge-to-edge behind the status bar,
        // display cutout, and navigation bar with zero dimmed top band.
        val dialogView = LocalView.current
        SideEffect {
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            if (dialogWindow != null) {
                WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
                dialogWindow.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                dialogWindow.setGravity(Gravity.FILL)
                dialogWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                dialogWindow.setDimAmount(0f)
                dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                @Suppress("DEPRECATION")
                dialogWindow.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                dialogWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dialogWindow.attributes = dialogWindow.attributes.apply {
                        layoutInDisplayCutoutMode =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                            } else {
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dialogWindow.isNavigationBarContrastEnforced = false
                    dialogWindow.isStatusBarContrastEnforced = false
                }
            }
        }

        DisposableEffect(dialogView, context) {
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            val activityWindow = (context as? Activity)?.window
            val dialogController = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
            val activityController = activityWindow?.let { WindowCompat.getInsetsController(it, it.decorView) }

            listOfNotNull(dialogController, activityController).forEach { controller ->
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
            onDispose {
                listOfNotNull(dialogController, activityController).forEach { controller ->
                    controller.show(WindowInsetsCompat.Type.statusBars())
                }
            }
        }

        if (foldableInfo.isTabletop) {
            FoldableTabletopSplitLayout(
                info = foldableInfo,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                topPane = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) break
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        if (zoomChange != 1f || zoomScale.value > 1.02f) {
                                            val next = (zoomScale.value * zoomChange).coerceIn(1f, 4f)
                                            scope.launch { zoomScale.snapTo(next) }
                                            panOffset = if (next > 1.02f) panOffset + panChange else Offset.Zero
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                    ) {
                        if (previewModel != null && !is2kLoaded) {
                            AsyncImage(
                                model = previewModel,
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = zoomScale.value,
                                        scaleY = zoomScale.value,
                                        translationX = panOffset.x,
                                        translationY = panOffset.y
                                    )
                            )
                        }
                        AsyncImage(
                            model = full2kRequest,
                            imageLoader = imageLoader,
                            contentDescription = "Full screen 2K image",
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center,
                            onLoading = { isLoading2k = true },
                            onSuccess = {
                                isLoading2k = false
                                is2kLoaded = true
                            },
                            onError = { isLoading2k = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoomScale.value,
                                    scaleY = zoomScale.value,
                                    translationX = panOffset.x,
                                    translationY = panOffset.y
                                )
                        )
                        if (hasDedicated2k && (isLoading2k || is2kLoaded)) {
                            Lightbox2kQualityBadge(
                                isLoading = isLoading2k && !is2kLoaded,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start)
                                    )
                                    .padding(14.dp)
                            )
                        }
                    }
                },
                bottomPane = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0E0E12))
                            .navigationBarsPadding()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Tabletop Viewer",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val next = (zoomScale.value - 0.5f).coerceAtLeast(1f)
                                    scope.launch { zoomScale.animateTo(next) }
                                    if (next <= 1.01f) panOffset = Offset.Zero
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
                            ) {
                                Icon(Icons.Outlined.ZoomOut, contentDescription = "Zoom out", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    scope.launch { zoomScale.animateTo(1f) }
                                    panOffset = Offset.Zero
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
                            ) {
                                Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset zoom", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    val next = (zoomScale.value + 0.5f).coerceAtMost(4f)
                                    scope.launch { zoomScale.animateTo(next) }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
                            ) {
                                Icon(Icons.Outlined.ZoomIn, contentDescription = "Zoom in", tint = Color.White)
                            }
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.White.copy(alpha = 0.14f), CircleShape)
                                        .testTag("lightbox_more_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.lightbox_more_options),
                                        tint = Color.White
                                    )
                                }
                                LightboxMoreDropdownMenu(
                                    expanded = showMoreMenu,
                                    isBookmarked = resolvedBookmarked,
                                    onDismiss = { showMoreMenu = false },
                                    onSave = {
                                        showMoreMenu = false
                                        performSave()
                                    },
                                    onShare = {
                                        showMoreMenu = false
                                        performShare()
                                    },
                                    onDownload = {
                                        showMoreMenu = false
                                        performDownload()
                                    },
                                    onBookmark = {
                                        showMoreMenu = false
                                        performBookmark()
                                    }
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                                    .testTag("lightbox_close_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close image preview", tint = Color.White)
                            }
                        }
                    }
                }
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backdropAlpha))
                    .onSizeChanged { containerSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (!isDismissing && !isDraggingDismiss) {
                                    if (showMoreMenu) {
                                        showMoreMenu = false
                                    } else {
                                        chromeVisible = !chromeVisible
                                    }
                                }
                            },
                            onDoubleTap = {
                                if (!isDismissing && !isDraggingDismiss) {
                                    showMoreMenu = false
                                    if (zoomScale.value > 1.2f) {
                                        panOffset = Offset.Zero
                                        scope.launch {
                                            zoomScale.animateTo(
                                                targetValue = 1f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    } else {
                                        chromeVisible = false
                                        scope.launch {
                                            zoomScale.animateTo(
                                                targetValue = 2.4f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            if (isDismissing) return@awaitEachGesture

                            val velocityTracker = VelocityTracker()
                            var totalDrag = Offset.Zero
                            var pastTouchSlop = false
                            var dragMode = LightboxDragMode.NONE

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount == 0) break

                                if (pressedCount >= 2) {
                                    if (dragMode == LightboxDragMode.DISMISS_DRAG) {
                                        isDraggingDismiss = false
                                        scope.launch {
                                            dismissOffsetX.snapTo(0f)
                                            dismissOffsetY.snapTo(0f)
                                        }
                                    }
                                    dragMode = LightboxDragMode.ZOOM_OR_PAN
                                    pastTouchSlop = true
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val nextScale = (zoomScale.value * zoomChange).coerceIn(1f, 4f)
                                    scope.launch { zoomScale.snapTo(nextScale) }
                                    if (nextScale > 1.02f) {
                                        val maxPanX = (containerSize.width * (nextScale - 1f)) / 2f
                                        val maxPanY = (containerSize.height * (nextScale - 1f)) / 2f
                                        panOffset = Offset(
                                            x = (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                            y = (panOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                        )
                                    } else {
                                        panOffset = Offset.Zero
                                    }
                                    event.changes.forEach { it.consume() }
                                } else {
                                    val change = event.changes.firstOrNull { it.pressed } ?: break
                                    val delta = change.positionChange()
                                    if (delta == Offset.Zero) continue
                                    totalDrag += delta

                                    if (!pastTouchSlop && totalDrag.getDistance() > viewConfiguration.touchSlop) {
                                        pastTouchSlop = true
                                        dragMode = when {
                                            zoomScale.value > 1.02f -> LightboxDragMode.ZOOM_OR_PAN
                                            abs(totalDrag.y) >= abs(totalDrag.x) * 0.8f -> {
                                                isDraggingDismiss = true
                                                showMoreMenu = false
                                                LightboxDragMode.DISMISS_DRAG
                                            }
                                            else -> LightboxDragMode.NONE
                                        }
                                    }

                                    when (dragMode) {
                                        LightboxDragMode.ZOOM_OR_PAN -> {
                                            if (zoomScale.value > 1.02f) {
                                                val maxPanX = (containerSize.width * (zoomScale.value - 1f)) / 2f
                                                val maxPanY = (containerSize.height * (zoomScale.value - 1f)) / 2f
                                                panOffset = Offset(
                                                    x = (panOffset.x + delta.x).coerceIn(-maxPanX, maxPanX),
                                                    y = (panOffset.y + delta.y).coerceIn(-maxPanY, maxPanY)
                                                )
                                                change.consume()
                                            }
                                        }
                                        LightboxDragMode.DISMISS_DRAG -> {
                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                            val nextX = dismissOffsetX.value + delta.x * 0.85f
                                            val projectedY = dismissOffsetY.value + delta.y
                                            val nextY = if (projectedY < 0f) {
                                                // Soft rubber-band resistance if nudged upward past center
                                                dismissOffsetY.value + delta.y * 0.22f
                                            } else {
                                                projectedY
                                            }
                                            scope.launch {
                                                dismissOffsetX.snapTo(nextX)
                                                dismissOffsetY.snapTo(nextY)
                                            }
                                            change.consume()
                                        }
                                        LightboxDragMode.NONE -> Unit
                                    }
                                }
                            }

                            if (dragMode == LightboxDragMode.DISMISS_DRAG) {
                                val velocity = velocityTracker.calculateVelocity()
                                val shouldDismiss = dismissOffsetY.value > dismissThresholdPx ||
                                    (dismissOffsetY.value > fastSwipeMinDragPx && velocity.y > fastSwipeVelocityPx)
                                if (shouldDismiss) {
                                    animateDismissExit()
                                } else {
                                    isDraggingDismiss = false
                                    val snapSpec = spring<Float>(
                                        dampingRatio = 0.78f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                    scope.launch {
                                        launch { dismissOffsetX.animateTo(0f, snapSpec) }
                                        launch { dismissOffsetY.animateTo(0f, snapSpec) }
                                    }
                                }
                            } else if (zoomScale.value <= 1.02f) {
                                panOffset = Offset.Zero
                            }
                        }
                    }
            ) {
                // Dead-centered 2K photo container with smooth Google Photos scale/translate/corner transforms
                val effectiveScale = zoomScale.value * dismissScale
                val effectiveTranslationX = if (zoomScale.value > 1.02f) panOffset.x else dismissOffsetX.value
                val effectiveTranslationY = if (zoomScale.value > 1.02f) panOffset.y else dismissOffsetY.value

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = effectiveScale
                            scaleY = effectiveScale
                            translationX = effectiveTranslationX
                            translationY = effectiveTranslationY
                            if (dragFraction > 0.01f) {
                                shape = RoundedCornerShape(cornerRadiusDp)
                                clip = true
                            }
                        }
                ) {
                    if (previewModel != null && !is2kLoaded) {
                        AsyncImage(
                            model = previewModel,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AsyncImage(
                        model = full2kRequest,
                        imageLoader = imageLoader,
                        contentDescription = "Full screen 2K image",
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.Center,
                        onLoading = { isLoading2k = true },
                        onSuccess = {
                            isLoading2k = false
                            is2kLoaded = true
                        },
                        onError = { isLoading2k = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Tap-to-fade overlay header with Close, 2K badge, and 3-dot overflow menu
                AnimatedVisibility(
                    visible = chromeVisible && !isDraggingDismiss && !isDismissing,
                    enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 180)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.68f),
                                        Color.Black.copy(alpha = 0.28f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = { animateDismissExit() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.44f), CircleShape)
                                    .testTag("lightbox_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close image preview",
                                    tint = Color.White
                                )
                            }

                            if (hasDedicated2k && (isLoading2k || is2kLoaded)) {
                                Lightbox2kQualityBadge(
                                    isLoading = isLoading2k && !is2kLoaded
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = {
                                    haptics.performTap()
                                    showMoreMenu = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.44f), CircleShape)
                                    .testTag("lightbox_more_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.lightbox_more_options),
                                    tint = Color.White
                                )
                            }

                            LightboxMoreDropdownMenu(
                                expanded = showMoreMenu,
                                isBookmarked = resolvedBookmarked,
                                onDismiss = { showMoreMenu = false },
                                onSave = {
                                    showMoreMenu = false
                                    performSave()
                                },
                                onShare = {
                                    showMoreMenu = false
                                    performShare()
                                },
                                onDownload = {
                                    showMoreMenu = false
                                    performDownload()
                                },
                                onBookmark = {
                                    showMoreMenu = false
                                    performBookmark()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LightboxMoreDropdownMenu(
    expanded: Boolean,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onBookmark: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("lightbox_more_menu")
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.lightbox_action_save)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null
                )
            },
            onClick = onSave,
            modifier = Modifier.testTag("lightbox_menu_save")
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.lightbox_action_share)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null
                )
            },
            onClick = onShare,
            modifier = Modifier.testTag("lightbox_menu_share")
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.lightbox_action_download)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null
                )
            },
            onClick = onDownload,
            modifier = Modifier.testTag("lightbox_menu_download")
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (isBookmarked) {
                        stringResource(R.string.lightbox_action_bookmarked)
                    } else {
                        stringResource(R.string.lightbox_action_bookmark)
                    }
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isBookmarked) {
                        Icons.Filled.Bookmark
                    } else {
                        Icons.Outlined.BookmarkBorder
                    },
                    contentDescription = null,
                    tint = if (isBookmarked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            onClick = onBookmark,
            modifier = Modifier.testTag("lightbox_menu_bookmark")
        )
    }
}

@Composable
private fun Lightbox2kQualityBadge(
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier.testTag("lightbox_2k_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 1.8.dp,
                    color = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = if (isLoading) {
                    stringResource(R.string.lightbox_loading_2k)
                } else {
                    stringResource(R.string.lightbox_badge_2k)
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
