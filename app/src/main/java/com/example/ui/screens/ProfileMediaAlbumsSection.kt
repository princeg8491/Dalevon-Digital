package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.cache.OrbitMediaFiles
import com.example.data.model.AlbumMediaItem
import com.example.data.model.PostAttachment
import com.example.data.model.ProfileAlbum
import com.example.data.model.UserProfile
import com.example.ui.components.FullScreenImageLightbox
import com.example.ui.components.OrbitVideoPlayerDialog
import com.example.ui.components.UploadedPhotoPreviewTile
import com.example.ui.components.UserSearchDropdownField
import com.example.ui.haptics.LocalOrbitHaptics

/**
 * Profile → Media tab: displays user-created media albums in a 2-column grid with cover art,
 * item counts, and quick creation of new photo/video albums.
 */
@Composable
fun ProfileMediaAlbumsTab(
    albums: List<ProfileAlbum>,
    isOwnProfile: Boolean,
    onCreateAlbum: (title: String, initialUris: List<Uri>) -> Unit,
    onOpenAlbum: (ProfileAlbum) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isOwnProfile) {
            FilledTonalButton(
                onClick = {
                    haptics.performTap()
                    showCreateDialog = true
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_album_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create media album")
            }
        }

        if (albums.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isOwnProfile) "No albums yet" else "No public media albums yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isOwnProfile) {
                            "Organize your photos and videos into albums, set cover photos, and share them."
                        } else {
                            "Albums created by this user will appear here."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val rows = remember(albums) { albums.chunked(2) }
            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { album ->
                        AlbumGridCard(
                            album = album,
                            onClick = {
                                haptics.performTap()
                                onOpenAlbum(album)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAlbumDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, uris ->
                showCreateDialog = false
                onCreateAlbum(title, uris)
            }
        )
    }
}

@Composable
private fun AlbumGridCard(
    album: ProfileAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageLoader = LocalOrbitImageLoader.current
    val coverModel = remember(album.coverUrl) { OrbitCacheManager.toCoilModel(album.coverUrl) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("album_card_${album.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverModel != null && imageLoader != null) {
                AsyncImage(
                    model = coverModel,
                    imageLoader = imageLoader,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.itemCount} item${if (album.itemCount == 1L) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateAlbumDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, uris: List<Uri>) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var pickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var previewLightboxUrl by remember { mutableStateOf<String?>(null) }
    var previewVideoAttachment by remember { mutableStateOf<PostAttachment?>(null) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        if (uris.isNotEmpty()) {
            pickedUris = (pickedUris + uris).distinct().take(12)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Media Album") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 80) title = it },
                    label = { Text("Album name") },
                    placeholder = { Text("Summer Trips, Portraits...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_album_title_input")
                )
                OutlinedButton(
                    onClick = {
                        mediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (pickedUris.isEmpty()) "Add photos & videos (up to 500 MB)"
                        else "${pickedUris.size} item${if (pickedUris.size == 1) "" else "s"} selected · Add more"
                    )
                }

                if (pickedUris.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(pickedUris, key = { it.toString() }) { uri ->
                            val mime = remember(uri) { OrbitMediaFiles.queryMimeType(context, uri) }
                            val isVideo = mime.startsWith("video/", ignoreCase = true)
                            val videoPoster by androidx.compose.runtime.produceState<String?>(initialValue = null, uri, isVideo) {
                                if (isVideo) {
                                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        OrbitMediaFiles.extractVideoFrameDataUri(context, uri = uri)
                                    }
                                }
                            }
                            val displayModel = if (isVideo) {
                                OrbitCacheManager.toCoilModel(videoPoster)
                            } else {
                                uri
                            }
                            UploadedPhotoPreviewTile(
                                model = displayModel,
                                previewLabel = if (isVideo) "Video" else "Preview",
                                size = 82.dp,
                                onPreviewClick = {
                                    if (isVideo) {
                                        previewVideoAttachment = PostAttachment(
                                            fileName = OrbitMediaFiles.queryDisplayName(context, uri),
                                            mimeType = mime,
                                            sizeBytes = OrbitMediaFiles.queryFileSize(context, uri).coerceAtLeast(0L),
                                            dataUri = uri.toString()
                                        )
                                    } else {
                                        previewLightboxUrl = uri.toString()
                                    }
                                },
                                onRemoveClick = {
                                    pickedUris = pickedUris - uri
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title.trim(), pickedUris) },
                enabled = title.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_create_album_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    previewLightboxUrl?.let { activeUrl ->
        FullScreenImageLightbox(
            imageUrl = activeUrl,
            onDismiss = { previewLightboxUrl = null },
            onDownloadClick = { previewLightboxUrl = null }
        )
    }

    previewVideoAttachment?.let { activeVid ->
        OrbitVideoPlayerDialog(
            attachment = activeVid,
            onDismiss = { previewVideoAttachment = null }
        )
    }
}

/**
 * Full-screen Album viewer & organizer supporting:
 * - Add photos & videos
 * - Long-press + drag/swipe multi-select across thumbnails + Select All
 * - Remove selected items
 * - Pick cover photo
 * - Rearrange / move images around in album
 * - Share album to Main Feed or to a specific user via autocomplete dropdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: ProfileAlbum,
    items: List<AlbumMediaItem>,
    isOwner: Boolean,
    allProfiles: List<UserProfile>,
    currentUserId: String,
    onBack: () -> Unit,
    onAddMedia: (List<Uri>) -> Unit,
    onRemoveItems: (Set<String>) -> Unit,
    onSetCover: (String) -> Unit,
    onReorderItems: (List<AlbumMediaItem>) -> Unit,
    onDeleteAlbum: () -> Unit,
    onShareToFeed: () -> Unit,
    onShareToUser: (UserProfile) -> Unit,
    onDownloadPhoto: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val haptics = LocalOrbitHaptics.current
    val imageLoader = LocalOrbitImageLoader.current

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isReorderMode by remember { mutableStateOf(false) }
    var workingOrder by remember(items) { mutableStateOf(items) }
    LaunchedEffect(items, isReorderMode) {
        if (!isReorderMode) workingOrder = items
    }

    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    var playingVideoAttachment by remember { mutableStateOf<PostAttachment?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showDeleteAlbumConfirm by remember { mutableStateOf(false) }
    var showCoverPickerHint by remember { mutableStateOf(false) }

    // Bounds map in root coordinates for smooth long-press & drag-across multi-selection
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    var gridRootOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragSelectAdding by remember { mutableStateOf(true) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        if (uris.isNotEmpty()) onAddMedia(uris)
    }

    val isSelectionMode = selectedIds.isNotEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isReorderMode) {
                        Text(
                            text = "Move items",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Column {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${items.size} item${if (items.size == 1) "" else "s"} · @${album.ownerUsername}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.performTap()
                            when {
                                isSelectionMode -> selectedIds = emptySet()
                                isReorderMode -> {
                                    isReorderMode = false
                                    workingOrder = items
                                }
                                else -> onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode || isReorderMode) Icons.Outlined.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(
                            onClick = {
                                haptics.performTap()
                                selectedIds = if (selectedIds.size == items.size) {
                                    emptySet()
                                } else {
                                    items.map { it.id }.toSet()
                                }
                            },
                            modifier = Modifier.testTag("album_select_all_button")
                        ) {
                            Icon(Icons.Outlined.DoneAll, contentDescription = "Select all")
                        }
                        if (isOwner) {
                            val selectedPhoto = items.firstOrNull { it.id in selectedIds && !it.isVideo }
                            if (selectedIds.size == 1 && selectedPhoto != null) {
                                IconButton(
                                    onClick = {
                                        haptics.performMedium()
                                        onSetCover(selectedPhoto.mediaUrl)
                                        selectedIds = emptySet()
                                    },
                                    modifier = Modifier.testTag("album_set_cover_button")
                                ) {
                                    Icon(Icons.Outlined.Image, contentDescription = "Set as cover photo")
                                }
                            }
                            IconButton(
                                onClick = {
                                    haptics.performMedium()
                                    onRemoveItems(selectedIds)
                                    selectedIds = emptySet()
                                },
                                modifier = Modifier.testTag("album_remove_selected_button")
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Remove selected",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else if (isReorderMode) {
                        TextButton(
                            onClick = {
                                haptics.performMedium()
                                onReorderItems(workingOrder)
                                isReorderMode = false
                            },
                            modifier = Modifier.testTag("album_save_order_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                haptics.performTap()
                                showShareDialog = true
                            },
                            modifier = Modifier.testTag("album_share_button")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share album")
                        }
                        if (isOwner && items.size > 1) {
                            IconButton(
                                onClick = {
                                    haptics.performTap()
                                    isReorderMode = true
                                },
                                modifier = Modifier.testTag("album_reorder_button")
                            ) {
                                Icon(Icons.Outlined.SwapHoriz, contentDescription = "Rearrange photos")
                            }
                        }
                        if (isOwner) {
                            IconButton(
                                onClick = {
                                    haptics.performTap()
                                    showDeleteAlbumConfirm = true
                                },
                                modifier = Modifier.testTag("album_delete_button")
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete album",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (isOwner && !isSelectionMode && !isReorderMode) {
                FloatingActionButton(
                    onClick = {
                        haptics.performTap()
                        mediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("album_add_media_fab")
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Add photos or videos")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Action helper strip
            if (isOwner && items.isNotEmpty() && !isReorderMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (showCoverPickerHint) {
                                "Tap any photo below to set it as the album cover"
                            } else if (isSelectionMode) {
                                "Swipe across items to select more"
                            } else {
                                "Long-press & swipe to select · Tap to view"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isSelectionMode) {
                            TextButton(
                                onClick = {
                                    haptics.performTap()
                                    showCoverPickerHint = !showCoverPickerHint
                                }
                            ) {
                                Text(
                                    text = if (showCoverPickerHint) "Cancel cover" else "Pick cover",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            TextButton(
                                onClick = {
                                    haptics.performTap()
                                    selectedIds = items.map { it.id }.toSet()
                                }
                            ) {
                                Text("Select all", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (workingOrder.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Collections,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "This album is empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isOwner) "Tap + below to upload photos or videos to \"${album.title}\"."
                            else "No photos or videos have been added yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Grid supporting long-press and drag/swipe across items to multi-select
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            gridRootOrigin = coords.positionInRoot()
                        }
                        .pointerInput(workingOrder, isReorderMode, showCoverPickerHint) {
                            if (isReorderMode || showCoverPickerHint) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startOffset ->
                                    val rootPt = gridRootOrigin + startOffset
                                    val hitId = itemBounds.entries.firstOrNull { (_, rect) ->
                                        rect.contains(rootPt)
                                    }?.key
                                    if (hitId != null) {
                                        haptics.performMedium()
                                        val alreadySelected = hitId in selectedIds
                                        dragSelectAdding = !alreadySelected
                                        selectedIds = if (dragSelectAdding) {
                                            selectedIds + hitId
                                        } else {
                                            selectedIds - hitId
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val rootPt = gridRootOrigin + change.position
                                    val hitId = itemBounds.entries.firstOrNull { (_, rect) ->
                                        rect.contains(rootPt)
                                    }?.key
                                    if (hitId != null) {
                                        val changed = if (dragSelectAdding) {
                                            hitId !in selectedIds
                                        } else {
                                            hitId in selectedIds
                                        }
                                        if (changed) {
                                            haptics.performSelectionTick()
                                            selectedIds = if (dragSelectAdding) {
                                                selectedIds + hitId
                                            } else {
                                                selectedIds - hitId
                                            }
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(workingOrder, key = { _, item -> item.id }) { index, item ->
                            val isSelected = item.id in selectedIds
                            val isCover = album.coverUrl.isNotBlank() && item.mediaUrl == album.coverUrl
                            val coilModel = remember(item.mediaUrl, item.isVideo) {
                                if (item.isVideo) {
                                    OrbitCacheManager.toCoilModel(
                                        OrbitMediaFiles.extractPosterFromVideoDataUri(item.mediaUrl)
                                    )
                                } else {
                                    OrbitCacheManager.toCoilModel(item.mediaUrl)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInRoot()
                                        val sz = coords.size
                                        itemBounds[item.id] = Rect(
                                            left = pos.x,
                                            top = pos.y,
                                            right = pos.x + sz.width,
                                            bottom = pos.y + sz.height
                                        )
                                    }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        haptics.performTap()
                                        when {
                                            isReorderMode -> Unit
                                            showCoverPickerHint -> {
                                                if (!item.isVideo) {
                                                    onSetCover(item.mediaUrl)
                                                    showCoverPickerHint = false
                                                }
                                            }
                                            isSelectionMode -> {
                                                selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                            }
                                            item.isVideo -> {
                                                playingVideoAttachment = PostAttachment(
                                                    fileName = item.fileName.ifBlank { "Album Video.mp4" },
                                                    mimeType = item.mimeType,
                                                    dataUri = item.mediaUrl
                                                )
                                            }
                                            else -> lightboxUrl = item.mediaUrl
                                        }
                                    }
                            ) {
                                if (coilModel != null && imageLoader != null) {
                                    AsyncImage(
                                        model = coilModel,
                                        imageLoader = imageLoader,
                                        contentDescription = item.fileName.ifBlank { "Album photo" },
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.65f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = "Play video",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .size(26.dp)
                                            )
                                        }
                                    }
                                }

                                if (isCover) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(5.dp)
                                    ) {
                                        Text(
                                            text = "Cover",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (isSelectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                            contentDescription = if (isSelected) "Selected" else "Not selected",
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                if (isReorderMode) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .padding(horizontal = 2.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    haptics.performSelectionTick()
                                                    val mutable = workingOrder.toMutableList()
                                                    val moved = mutable.removeAt(index)
                                                    mutable.add(index - 1, moved)
                                                    workingOrder = mutable
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Outlined.ArrowBack,
                                                contentDescription = "Move left",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (index < workingOrder.lastIndex) {
                                                    haptics.performSelectionTick()
                                                    val mutable = workingOrder.toMutableList()
                                                    val moved = mutable.removeAt(index)
                                                    mutable.add(index + 1, moved)
                                                    workingOrder = mutable
                                                }
                                            },
                                            enabled = index < workingOrder.lastIndex,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Outlined.ArrowForward,
                                                contentDescription = "Move right",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    lightboxUrl?.let { url ->
        FullScreenImageLightbox(
            imageUrl = url,
            onDismiss = { lightboxUrl = null },
            onDownloadClick = { onDownloadPhoto(url) }
        )
    }

    playingVideoAttachment?.let { att ->
        OrbitVideoPlayerDialog(
            attachment = att,
            onDismiss = { playingVideoAttachment = null },
            onDownload = { /* handled via photo/file download if desired */ }
        )
    }

    if (showShareDialog) {
        var userSearchQuery by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Share \"${album.title}\"") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            haptics.performShare()
                            showShareDialog = false
                            onShareToFeed()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_album_to_feed_button")
                    ) {
                        Icon(Icons.Outlined.DynamicFeed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share to Main Feed")
                    }

                    HorizontalDivider(thickness = 0.5.dp)

                    Text(
                        text = "Or send directly to a user",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    UserSearchDropdownField(
                        query = userSearchQuery,
                        onQueryChange = { userSearchQuery = it },
                        allUsers = allProfiles,
                        excludedUserIds = setOf(currentUserId),
                        placeholder = "Type a name or @username...",
                        actionLabel = "Send",
                        testTag = "share_album_user_search",
                        onSelectUser = { user ->
                            haptics.performShare()
                            showShareDialog = false
                            onShareToUser(user)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareDialog = false }) { Text("Close") }
            }
        )
    }

    if (showDeleteAlbumConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAlbumConfirm = false },
            title = { Text("Delete album?") },
            text = { Text("Delete \"${album.title}\" and all ${items.size} item${if (items.size == 1) "" else "s"} inside it? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAlbumConfirm = false
                        onDeleteAlbum()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlbumConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
