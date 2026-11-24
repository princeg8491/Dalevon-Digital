package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.cache.OrbitMediaFiles
import com.example.data.model.COMMUNITY_CATEGORIES
import com.example.data.model.COMMUNITY_STREAM_CATEGORIES
import com.example.data.model.Community
import com.example.data.model.CommunityComment
import com.example.data.model.CommunityPost
import com.example.data.model.PostAttachment
import com.example.data.model.UserProfile
import com.example.data.model.extractUrlsFromText
import com.example.ui.components.SpaceSearchDropdownField
import com.example.data.model.formatCompactCount
import com.example.data.model.formatRelativeTime
import androidx.compose.material.icons.outlined.Movie
import com.example.ui.components.CenteredThreadsContainer
import com.example.ui.components.EditPostDialog
import com.example.ui.components.FullScreenImageLightbox
import com.example.ui.components.ImageCropperDialog
import com.example.ui.components.InlineLinkThumbnailCard
import com.example.ui.components.LocalFollowedProfiles
import com.example.ui.components.LocalMentionableProfiles
import com.example.ui.components.LocalOrbitCurrentUserId
import com.example.ui.components.LocalOrbitModerationActions
import com.example.ui.components.LocalPhotoPeekController
import com.example.ui.components.MentionSuggestionsDropdown
import com.example.ui.components.OrbitRichTextVisualTransformation
import com.example.ui.components.OrbitVideoPlayerDialog
import com.example.ui.components.PhotoPeekRequest
import com.example.ui.components.PostAttachmentCard
import com.example.ui.components.SpaceAdminBadgePill
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.UploadedAttachmentPreviewCard
import com.example.ui.components.UploadedPhotoPreviewTile
import com.example.ui.components.detectActiveMentionQuery
import com.example.ui.components.insertMention
import com.example.ui.components.parseOrbitRichText
import com.example.ui.haptics.LocalOrbitHaptics
import com.example.ui.share.OrbitShareHelper
import java.util.concurrent.TimeUnit

private val googlePlusCoverGradients = listOf(
    listOf(Color(0xFFDB4437), Color(0xFF9E261C)), // Classic Google+ Crimson
    listOf(Color(0xFF0F9D58), Color(0xFF0A693A)), // Emerald
    listOf(Color(0xFF4285F4), Color(0xFF1E53B5)), // Cobalt Blue
    listOf(Color(0xFF8E24AA), Color(0xFF53136E)), // Deep Plum
    listOf(Color(0xFF00897B), Color(0xFF00574B)), // Teal
    listOf(Color(0xFFF4511E), Color(0xFFB33108))  // Warm Terracotta
)

private fun coverGradientFor(accentIndex: Long): Brush {
    val pair = googlePlusCoverGradients[(accentIndex.toInt().coerceAtLeast(0)) % googlePlusCoverGradients.size]
    return Brush.linearGradient(pair)
}

private fun spaceJoinButtonLabel(space: Community, uid: String): String {
    return when {
        space.isBanned(uid) -> "Banned"
        uid in space.memberIds -> "Joined"
        space.isInvited(uid) -> "Accept invite"
        space.hasPendingRequest(uid) -> "Requested"
        space.isPrivateSpace -> "Request to join"
        else -> "Join"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesHubScreen(
    communities: List<Community>,
    currentUserId: String,
    selectedCommunityId: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenCommunity: (String) -> Unit,
    onJoinAction: (Community) -> Unit,
    onCreateCommunity: (
        name: String,
        description: String,
        category: String,
        coverDataUri: String?,
        coverUri: Uri?,
        accentIndex: Long,
        privacyMode: String,
        onDone: (String) -> Unit
    ) -> Unit
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val haptics = LocalOrbitHaptics.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Discover, 1 = Your Spaces
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val trimmedQuery = searchQuery.trim().lowercase()
    val filteredCommunities = remember(communities, selectedTab, selectedCategory, trimmedQuery, currentUserId) {
        communities.filter { space ->
            val tabMatch = if (selectedTab == 1) currentUserId in space.memberIds else true
            val catMatch = selectedCategory == "All" || space.category.equals(selectedCategory, ignoreCase = true)
            val queryMatch = trimmedQuery.isEmpty() ||
                space.name.lowercase().contains(trimmedQuery) ||
                space.description.lowercase().contains(trimmedQuery) ||
                space.category.lowercase().contains(trimmedQuery)
            tabMatch && catMatch && queryMatch
        }
    }

    CenteredThreadsContainer {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Groups,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.spaces_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(
                                onClick = {
                                    haptics.performTap()
                                    onBack()
                                },
                                modifier = Modifier.testTag("communities_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = {
                                haptics.performTap()
                                showCreateDialog = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .testTag("communities_create_top_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Create Space")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        haptics.performTap()
                        showCreateDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("communities_create_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create Space")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Search Spaces bar with live dropdown suggestions
                SpaceSearchDropdownField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    allSpaces = communities,
                    placeholder = "Search Spaces by name, topic, or vibe",
                    testTag = "spaces_search_input",
                    onSelectSpace = { space ->
                        searchQuery = ""
                        onOpenCommunity(space.id)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTab, matchContentSize = false),
                            width = Dp.Unspecified,
                            height = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    listOf("Discover", "Your Spaces").forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                haptics.performSelectionTick()
                                selectedTab = index
                            },
                            modifier = Modifier.testTag("communities_tab_$index"),
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(COMMUNITY_CATEGORIES) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                haptics.performSelectionTick()
                                selectedCategory = cat
                            },
                            label = { Text(cat) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("community_cat_$cat")
                        )
                    }
                }

                if (filteredCommunities.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Groups,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = when {
                                    trimmedQuery.isNotEmpty() -> "No Spaces matching \"$searchQuery\""
                                    selectedTab == 1 -> "You haven’t joined a Space yet"
                                    else -> "Create the first Space"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Build public or private interest Spaces with categorized streams, +1s, and admin/mod roles.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    haptics.performTap()
                                    showCreateDialog = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("empty_create_community_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create a Space")
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 152.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 84.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredCommunities,
                            key = { it.id }
                        ) { space ->
                            GooglePlusSpaceCard(
                                space = space,
                                currentUserId = currentUserId,
                                isSelected = selectedCommunityId == space.id,
                                onOpen = {
                                    haptics.performTap()
                                    onOpenCommunity(space.id)
                                },
                                onJoinAction = {
                                    haptics.performMedium()
                                    onJoinAction(space)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateSpaceFullScreenDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, category, coverDataUri, coverUri, accent, privacyMode ->
                onCreateCommunity(name, desc, category, coverDataUri, coverUri, accent, privacyMode) { createdId ->
                    showCreateDialog = false
                    onOpenCommunity(createdId)
                }
            }
        )
    }
}

@Composable
private fun GooglePlusSpaceCard(
    space: Community,
    currentUserId: String,
    isSelected: Boolean = false,
    onOpen: () -> Unit,
    onJoinAction: () -> Unit
) {
    val context = LocalContext.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    val coverModel = remember(space.coverUrl) { OrbitCacheManager.toCoilModel(space.coverUrl) }
    val isMember = currentUserId in space.memberIds
    val isBanned = space.isBanned(currentUserId)
    val isRequested = space.hasPendingRequest(currentUserId)
    val actionLabel = spaceJoinButtonLabel(space, currentUserId)

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.6.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("community_card_${space.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(coverGradientFor(space.accentIndex))
            ) {
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        imageLoader = imageLoader,
                        contentDescription = "${space.name} cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = space.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                if (space.isPrivateSpace) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.56f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Private Space",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "PRIVATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                val isSpaceCreator = space.isAdmin(currentUserId)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = space.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        minLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSpaceCreator) {
                        SpaceAdminBadgePill(testTag = "community_card_admin_badge_${space.id}")
                    }
                }
                Spacer(Modifier.height(3.dp))
                val memberLabel = if (space.memberCount == 1L) "member" else "members"
                Text(
                    text = "${formatCompactCount(space.memberCount)} $memberLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (space.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = space.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (isMember || isRequested || isBanned) {
                    OutlinedButton(
                        onClick = onJoinAction,
                        enabled = !isBanned,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    Button(
                        onClick = onJoinAction,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CommunityDetailScreen(
    community: Community,
    posts: List<CommunityPost>,
    profiles: List<UserProfile>,
    activeComments: List<CommunityComment>,
    currentUserId: String,
    isPosting: Boolean,
    onBack: (() -> Unit)? = null,
    onJoinAction: () -> Unit,
    onAuthorClick: (String) -> Unit,
    onPlusOneClick: (String) -> Unit,
    onEditPost: (post: CommunityPost, newBody: String, onDone: () -> Unit) -> Unit = { _, _, done -> done() },
    onDeletePost: (CommunityPost) -> Unit,
    onOpenComments: (String?) -> Unit,
    onAddComment: (postId: String, body: String, onDone: () -> Unit) -> Unit,
    onPublishPost: (streamCategory: String, body: String, imageUris: List<Uri>, attachmentUris: List<Uri>, onDone: () -> Unit) -> Unit,
    onSaveAdminSettings: (name: String, description: String, coverUrl: String, privacyMode: String, onDone: () -> Unit) -> Unit = { _, _, _, _, done -> done() },
    onDeleteSpace: (onDeleted: () -> Unit) -> Unit = { it() },
    onApproveRequest: (String) -> Unit = {},
    onDeclineRequest: (String) -> Unit = {},
    onInviteUser: (String) -> Unit = {},
    onToggleModerator: (String) -> Unit = {},
    onMuteMember: (uid: String, hours: Int) -> Unit = { _, _ -> },
    onKickMember: (String) -> Unit = {},
    onBanMember: (String) -> Unit = {},
    onUnbanMember: (String) -> Unit = {},
    onImageClick: (String) -> Unit,
    onDownloadPhoto: (String) -> Unit = {},
    onDownloadAttachment: (PostAttachment) -> Unit = {}
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    val coverModel = remember(community.coverUrl) { OrbitCacheManager.toCoilModel(community.coverUrl) }

    val isMember = currentUserId in community.memberIds
    val isAdmin = community.isAdmin(currentUserId)
    val canModerate = community.canModerate(currentUserId)
    val isBanned = community.isBanned(currentUserId)
    val isMuted = community.isMuted(currentUserId)
    val isLockedOut = community.isPrivateSpace && !isMember && !canModerate
    val joinLabel = spaceJoinButtonLabel(community, currentUserId)

    var detailTab by remember { mutableIntStateOf(0) } // 0 = Stream, 1 = About, 2 = Members
    var selectedStream by remember { mutableStateOf("All posts") }
    var showPostComposer by remember { mutableStateOf(false) }
    var showSpaceSettings by remember { mutableStateOf(false) }
    var commentSheetPost by remember { mutableStateOf<CommunityPost?>(null) }
    var editingSpacePost by remember { mutableStateOf<CommunityPost?>(null) }
    var moderatingMember by remember { mutableStateOf<UserProfile?>(null) }

    val filteredPosts = remember(posts, selectedStream) {
        if (selectedStream == "All posts") posts
        else posts.filter { it.streamCategory.equals(selectedStream, ignoreCase = true) }
    }

    val memberProfiles = remember(profiles, community.memberIds, community.ownerId, community.moderatorIds) {
        profiles
            .filter { it.resolvedId in community.memberIds || it.resolvedId == community.ownerId }
            .sortedWith(
                compareByDescending<UserProfile> { community.isAdmin(it.resolvedId) }
                    .thenByDescending { community.isModerator(it.resolvedId) }
                    .thenBy { it.displayName.lowercase() }
            )
    }

    CenteredThreadsContainer {
        Scaffold(
            floatingActionButton = {
                if (detailTab == 0 && !isLockedOut && !isBanned && !isMuted) {
                    FloatingActionButton(
                        onClick = {
                            haptics.performTap()
                            showPostComposer = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("community_compose_fab")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Post in Space")
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Hero Space Banner
                item(key = "community_hero") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(198.dp)
                            .background(coverGradientFor(community.accentIndex))
                    ) {
                        if (coverModel != null) {
                            AsyncImage(
                                model = coverModel,
                                imageLoader = imageLoader,
                                contentDescription = "${community.name} banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.44f),
                                            Color.Black.copy(alpha = 0.22f),
                                            Color.Black.copy(alpha = 0.76f)
                                        )
                                    )
                                )
                        )

                        if (canModerate) {
                            IconButton(
                                onClick = {
                                    haptics.performTap()
                                    showSpaceSettings = true
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .statusBarsPadding()
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.40f), CircleShape)
                                    .testTag("space_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Space settings",
                                    tint = Color.White
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = community.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                if (community.isPrivateSpace) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.45f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Lock,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "Private Space",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = community.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val memberText = if (community.memberCount == 1L) "1 member" else "${formatCompactCount(community.memberCount)} members"
                                    val postText = if (community.postCount == 1L) "1 post" else "${formatCompactCount(community.postCount)} posts"
                                    Text(
                                        text = "$memberText  ·  $postText",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.88f)
                                    )
                                    if (community.ownerUsername.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "@${community.ownerUsername}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.92f),
                                                fontWeight = FontWeight.Medium
                                            )
                                            SpaceAdminBadgePill(
                                                onDarkOverlay = true,
                                                testTag = "space_hero_admin_badge"
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Button(
                                    onClick = {
                                        haptics.performMedium()
                                        onJoinAction()
                                    },
                                    enabled = !isBanned,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isMember) Color.White.copy(alpha = 0.24f) else Color.White,
                                        contentColor = if (isMember) Color.White else Color.Black
                                    ),
                                    modifier = Modifier.testTag("community_detail_join_button")
                                ) {
                                    Text(
                                        text = joinLabel,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Tabs: Stream | About | Members
                item(key = "community_tabs") {
                    PrimaryTabRow(
                        selectedTabIndex = detailTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        listOf("Stream", "About", "Members").forEachIndexed { idx, label ->
                            Tab(
                                selected = detailTab == idx,
                                onClick = {
                                    haptics.performSelectionTick()
                                    detailTab = idx
                                },
                                modifier = Modifier.testTag("space_detail_tab_$idx"),
                                text = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (detailTab == idx) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }

                when (detailTab) {
                    0 -> {
                        if (isMuted) {
                            item(key = "space_muted_banner") {
                                val until = community.mutedUntilMap[currentUserId] ?: 0L
                                val remainingHours = ((until - System.currentTimeMillis()).coerceAtLeast(60_000L) / TimeUnit.HOURS.toMillis(1)) + 1
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "You are paused from posting in this Space (~$remainingHours hr remaining).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        if (isLockedOut) {
                            item(key = "private_space_gate") {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = "This is a Private Space",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = when {
                                                community.isInvited(currentUserId) -> "You’ve been invited to join ${community.name}. Confirm below to enter."
                                                community.hasPendingRequest(currentUserId) -> "Your request has been sent to the Space Admin and Mods."
                                                else -> "Request access to view posts and join conversations inside ${community.name}."
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                haptics.performMedium()
                                                onJoinAction()
                                            },
                                            enabled = !isBanned,
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(joinLabel)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Stream Category Filter Chips
                            item(key = "stream_filters") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    items(COMMUNITY_STREAM_CATEGORIES) { stream ->
                                        FilterChip(
                                            selected = selectedStream == stream,
                                            onClick = {
                                                haptics.performSelectionTick()
                                                selectedStream = stream
                                            },
                                            label = { Text(stream) },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }

                            if (filteredPosts.isEmpty()) {
                                item(key = "empty_stream") {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 28.dp, vertical = 52.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "No posts in $selectedStream yet",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = "Tap the compose button to start a discussion in ${community.name}.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = filteredPosts,
                                    key = { it.id }
                                ) { post ->
                                    val isAuthor = post.authorId == currentUserId
                                    GooglePlusStreamCard(
                                        post = post,
                                        spaceName = community.name,
                                        isAuthorSpaceAdmin = community.isAdmin(post.authorId),
                                        isPlusOned = currentUserId in post.plusOneUserIds,
                                        canEdit = isAuthor,
                                        canDelete = isAuthor || canModerate,
                                        onAuthorClick = { onAuthorClick(post.authorId) },
                                        onPlusOneClick = { onPlusOneClick(post.id) },
                                        onCommentClick = {
                                            onOpenComments(post.id)
                                            commentSheetPost = post
                                        },
                                        onEditClick = { editingSpacePost = post },
                                        onDeleteClick = { onDeletePost(post) },
                                        onImageClick = onImageClick,
                                        onDownloadPhoto = onDownloadPhoto,
                                        onDownloadAttachment = onDownloadAttachment
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        item(key = "about_card") {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = "About ${community.name}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = community.description.ifBlank { "An Orbit Space for ${community.category} enthusiasts." },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(Modifier.height(12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Created by @${community.ownerUsername}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "Admin",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    val privacyLabel = if (community.isPrivateSpace) "Private Space" else "Public Space"
                                    Text(
                                        text = "Category: ${community.category}  ·  $privacyLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        if (canModerate) {
                            item(key = "members_mod_hint") {
                                Text(
                                    text = "Long-press a member to pause posting, kick, ban, or manage Mod role.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                        if (memberProfiles.isEmpty()) {
                            item(key = "members_empty") {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp)
                                ) {
                                    Text(
                                        text = "${community.memberCount} member(s) in this Space.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(memberProfiles, key = { it.resolvedId }) { member ->
                                val memberUid = member.resolvedId
                                val memberIsAdmin = community.isAdmin(memberUid)
                                val memberIsMod = community.isModerator(memberUid)
                                val memberIsMuted = community.isMuted(memberUid)
                                val canActOnMember = when {
                                    memberUid == currentUserId -> false
                                    isAdmin -> true
                                    canModerate && !memberIsAdmin && !memberIsMod -> true
                                    else -> false
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onAuthorClick(memberUid) },
                                            onLongClick = {
                                                if (canActOnMember) {
                                                    haptics.performHeavy()
                                                    moderatingMember = member
                                                }
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .testTag("space_member_row_$memberUid")
                                ) {
                                    ThreadAvatar(
                                        avatarUrl = member.avatarUrl,
                                        displayName = member.displayName.ifBlank { member.username },
                                        size = 42.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = member.displayName.ifBlank { member.username },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (memberIsAdmin) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "Admin",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else if (memberIsMod) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                                ) {
                                                    Text(
                                                        text = "Mod",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "@${member.username}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (memberIsMuted) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = "Paused",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPostComposer) {
        CreateCommunityPostSheet(
            communityName = community.name,
            isPosting = isPosting,
            onDismiss = { showPostComposer = false },
            onPublish = { streamCat, body, uris, attachmentUris ->
                onPublishPost(streamCat, body, uris, attachmentUris) {
                    showPostComposer = false
                }
            }
        )
    }

    if (showSpaceSettings) {
        SpaceSettingsDialog(
            space = community,
            allProfiles = profiles,
            currentUserId = currentUserId,
            onDismiss = { showSpaceSettings = false },
            onSaveAdminSettings = onSaveAdminSettings,
            onDeleteSpace = { onDone ->
                onDeleteSpace {
                    showSpaceSettings = false
                    onDone()
                }
            },
            onApproveRequest = onApproveRequest,
            onDeclineRequest = onDeclineRequest,
            onInviteUser = onInviteUser,
            onUnbanUser = onUnbanMember,
            onUnmuteUser = { uid -> onMuteMember(uid, 0) }
        )
    }

    editingSpacePost?.let { post ->
        EditPostDialog(
            initialText = post.body,
            title = "Edit Space post",
            maxChars = 1000,
            onDismiss = { editingSpacePost = null },
            onSave = { updated ->
                onEditPost(post, updated) {
                    editingSpacePost = null
                }
            }
        )
    }

    moderatingMember?.let { member ->
        SpaceMemberModerationSheet(
            member = member,
            space = community,
            actorIsAdmin = isAdmin,
            onDismiss = { moderatingMember = null },
            onToggleMod = {
                onToggleModerator(member.resolvedId)
                moderatingMember = null
            },
            onMuteHours = { hours ->
                onMuteMember(member.resolvedId, hours)
                moderatingMember = null
            },
            onKick = {
                onKickMember(member.resolvedId)
                moderatingMember = null
            },
            onBan = {
                onBanMember(member.resolvedId)
                moderatingMember = null
            }
        )
    }

    commentSheetPost?.let { post ->
        CommunityCommentsSheet(
            post = post,
            spaceOwnerId = community.ownerId,
            comments = activeComments,
            onDismiss = {
                commentSheetPost = null
                onOpenComments(null)
            },
            onSendComment = { text, onDone ->
                onAddComment(post.id, text, onDone)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpaceMemberModerationSheet(
    member: UserProfile,
    space: Community,
    actorIsAdmin: Boolean,
    onDismiss: () -> Unit,
    onToggleMod: () -> Unit,
    onMuteHours: (Int) -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val uid = member.resolvedId
    val isMod = space.isModerator(uid)
    val isMuted = space.isMuted(uid)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 18.dp)
                .testTag("space_member_mod_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThreadAvatar(
                    avatarUrl = member.avatarUrl,
                    displayName = member.displayName.ifBlank { member.username },
                    size = 44.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.displayName.ifBlank { member.username },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${member.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Admin-only Mod assignment
            if (actorIsAdmin) {
                OutlinedButton(
                    onClick = {
                        haptics.performMedium()
                        onToggleMod()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mod_sheet_toggle_mod")
                ) {
                    Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isMod) "Remove Mod role" else "Make Mod")
                }
            }

            // Pause posting durations: 1 hr, 5 hrs, 8 hrs, 24 hrs (or Unpause)
            Text(
                text = "Pause from posting",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(1 to "1 hr", 5 to "5 hrs", 8 to "8 hrs", 24 to "24 hrs").forEach { (hrs, label) ->
                    FilledTonalButton(
                        onClick = {
                            haptics.performMedium()
                            onMuteHours(hrs)
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mod_sheet_mute_${hrs}h")
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (isMuted) {
                OutlinedButton(
                    onClick = {
                        haptics.performTap()
                        onMuteHours(0)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mod_sheet_unmute")
                ) {
                    Text("Unpause member now")
                }
            }

            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.performHeavy()
                        onKick()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mod_sheet_kick")
                ) {
                    Icon(Icons.Outlined.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kick")
                }
                Button(
                    onClick = {
                        haptics.performHeavy()
                        onBan()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mod_sheet_ban")
                ) {
                    Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ban")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GooglePlusStreamCard(
    post: CommunityPost,
    spaceName: String = "",
    isAuthorSpaceAdmin: Boolean = false,
    isPlusOned: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onAuthorClick: () -> Unit,
    onPlusOneClick: () -> Unit,
    onCommentClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onDownloadPhoto: (String) -> Unit = {},
    onDownloadAttachment: (PostAttachment) -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val currentUserId = LocalOrbitCurrentUserId.current
    val moderation = LocalOrbitModerationActions.current
    val peekController = LocalPhotoPeekController.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    val embeddedUrls = remember(post.body) { extractUrlsFromText(post.body) }
    var postMenuExpanded by remember { mutableStateOf(false) }

    val plusScale by animateFloatAsState(
        targetValue = if (isPlusOned) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "plus_one_scale"
    )
    val plusBg by animateColorAsState(
        targetValue = if (isPlusOned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "plus_one_bg"
    )
    val plusFg by animateColorAsState(
        targetValue = if (isPlusOned) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "plus_one_fg"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("community_post_card_${post.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ThreadAvatar(
                    avatarUrl = post.authorAvatarUrl,
                    displayName = post.authorDisplayName.ifBlank { post.authorUsername },
                    size = 38.dp,
                    onClick = onAuthorClick
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorDisplayName.ifBlank { post.authorUsername },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(onClick = onAuthorClick)
                        )
                        if (isAuthorSpaceAdmin) {
                            Spacer(Modifier.width(5.dp))
                            SpaceAdminBadgePill(testTag = "space_post_admin_badge_${post.id}")
                        }
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(12.dp)
                        )
                        Text(
                            text = post.streamCategory,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@${post.authorUsername}  ·  ${formatRelativeTime(post.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (post.edited) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "(edited)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (canEdit) {
                    IconButton(
                        onClick = {
                            haptics.performTap()
                            onEditClick()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("space_post_edit_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit post",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (canDelete) {
                    IconButton(
                        onClick = {
                            haptics.performMedium()
                            onDeleteClick()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("space_post_delete_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete post",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (post.authorId.isNotBlank() && post.authorId != currentUserId) {
                    val handle = post.authorUsername.ifBlank { post.authorDisplayName.ifBlank { "user" } }
                    val isMutedAuthor = post.authorId in moderation.mutedUserIds
                    val isBlockedAuthor = post.authorId in moderation.blockedUserIds
                    Box {
                        IconButton(
                            onClick = {
                                haptics.performTap()
                                postMenuExpanded = true
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("space_post_more_${post.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreHoriz,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = postMenuExpanded,
                            onDismissRequest = { postMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isMutedAuthor) {
                                            stringResource(R.string.action_unmute_user, handle)
                                        } else {
                                            stringResource(R.string.action_mute_user, handle)
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Outlined.VolumeOff, contentDescription = null)
                                },
                                onClick = {
                                    postMenuExpanded = false
                                    haptics.performMedium()
                                    moderation.onToggleMuteUser(post.authorId)
                                },
                                modifier = Modifier.testTag("space_post_mute_${post.id}")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isBlockedAuthor) {
                                            stringResource(R.string.action_unblock_user, handle)
                                        } else {
                                            stringResource(R.string.action_block_user, handle)
                                        },
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Block,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    postMenuExpanded = false
                                    haptics.performHeavy()
                                    moderation.onToggleBlockUser(post.authorId)
                                },
                                modifier = Modifier.testTag("space_post_block_${post.id}")
                            )
                        }
                    }
                }
            }

            if (post.body.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                val primaryColor = MaterialTheme.colorScheme.primary
                val formattedBody = remember(post.body, primaryColor) {
                    parseOrbitRichText(post.body, linkColor = primaryColor, bulletColor = primaryColor)
                }
                Text(
                    text = formattedBody,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (embeddedUrls.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                InlineLinkThumbnailCard(
                    title = embeddedUrls.first(),
                    url = embeddedUrls.first()
                )
            }

            if (post.imageUrls.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(post.imageUrls) { url ->
                        val model = remember(url) { OrbitCacheManager.toCoilModel(url) }
                        Box(
                            modifier = Modifier
                                .width(if (post.imageUrls.size == 1) 300.dp else 200.dp)
                                .height(200.dp)
                        ) {
                            AsyncImage(
                                model = model,
                                imageLoader = imageLoader,
                                contentDescription = "Space post image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { onImageClick(url) },
                                        onLongClick = {
                                            haptics.performHeavy()
                                            peekController.show(
                                                PhotoPeekRequest(
                                                    imageUrl = url,
                                                    authorUsername = post.authorUsername,
                                                    authorAvatarUrl = post.authorAvatarUrl,
                                                    caption = post.body,
                                                    isLiked = isPlusOned,
                                                    onLikeClick = onPlusOneClick,
                                                    onDownloadClick = { onDownloadPhoto(url) },
                                                    onOpenFullScreen = { onImageClick(url) }
                                                )
                                            )
                                        }
                                    )
                            )
                            Surface(
                                onClick = {
                                    haptics.performTap()
                                    onDownloadPhoto(url)
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.56f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = "Download photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (post.attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.attachments.forEachIndexed { idx, attachment ->
                        PostAttachmentCard(
                            attachment = attachment,
                            cacheKey = "comm_${post.id}_$idx",
                            onImageClick = onImageClick,
                            onDownloadClick = { onDownloadAttachment(attachment) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Classic Google+ Action Row: [+1] pill, Comment pill, Share
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = {
                        haptics.performLike(isLiking = !isPlusOned)
                        onPlusOneClick()
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = plusBg,
                    modifier = Modifier
                        .scale(plusScale)
                        .testTag("community_plus_one_${post.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "+1",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = plusFg
                        )
                        if (post.plusOneCount > 0L) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = formatCompactCount(post.plusOneCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = plusFg
                            )
                        }
                    }
                }

                Surface(
                    onClick = {
                        haptics.performTap()
                        onCommentClick()
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.testTag("community_comment_btn_${post.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Comments",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (post.commentCount > 0L) formatCompactCount(post.commentCount) else "Comment",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = {
                        haptics.performShare()
                        OrbitShareHelper.shareCommunityPost(context, post, spaceName)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("community_share_btn_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share post",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Full-screen Space creator with Public/Private privacy selector and interactive Banner Cropper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSpaceFullScreenDialog(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        description: String,
        category: String,
        coverDataUri: String?,
        coverUri: Uri?,
        accentIndex: Long,
        privacyMode: String
    ) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Photography") }
    var accentIndex by remember { mutableLongStateOf(0L) }
    var privacyMode by remember { mutableStateOf("public") }
    var croppedCoverDataUri by remember { mutableStateOf<String?>(null) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pendingCropUri = uri
    }

    val previewModel = remember(croppedCoverDataUri) {
        croppedCoverDataUri?.let { OrbitCacheManager.toCoilModel(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Create a Space",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptics.performTap()
                                onDismiss()
                            },
                            modifier = Modifier.testTag("create_space_close_btn")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                haptics.performMedium()
                                onCreate(
                                    name,
                                    description,
                                    category,
                                    croppedCoverDataUri,
                                    null,
                                    accentIndex,
                                    privacyMode
                                )
                            },
                            enabled = name.trim().length >= 2,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("create_community_confirm_button")
                        ) {
                            Text("Create")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Live Space Banner Preview + Cropper trigger
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(coverGradientFor(accentIndex))
                        .clickable {
                            haptics.performTap()
                            coverPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("create_space_banner_picker")
                ) {
                    if (previewModel != null) {
                        AsyncImage(
                            model = previewModel,
                            imageLoader = imageLoader,
                            contentDescription = "Space banner preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.56f),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Crop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (croppedCoverDataUri == null) "Upload & crop banner" else "Re-crop banner",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }

                // Cover Gradient Swatches
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    googlePlusCoverGradients.indices.forEach { idx ->
                        val selected = accentIndex.toInt() == idx && croppedCoverDataUri == null
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(coverGradientFor(idx.toLong()))
                                .border(
                                    width = if (selected) 2.5.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    haptics.performSelectionTick()
                                    accentIndex = idx.toLong()
                                    croppedCoverDataUri = null
                                }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Space name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_community_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 300) description = it },
                    label = { Text("About this Space") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_community_desc_input")
                )

                // Public vs Private selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Privacy",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = privacyMode == "public",
                            onClick = {
                                haptics.performSelectionTick()
                                privacyMode = "public"
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("create_space_public_btn")
                        ) {
                            Text("Public")
                        }
                        SegmentedButton(
                            selected = privacyMode == "private",
                            onClick = {
                                haptics.performSelectionTick()
                                privacyMode = "private"
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("create_space_private_btn")
                        ) {
                            Text("Private")
                        }
                    }
                    Text(
                        text = if (privacyMode == "private") {
                            "Private Spaces require Admin/Mod approval or a direct invite to join."
                        } else {
                            "Anyone on Orbit can discover and join a Public Space immediately."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(COMMUNITY_CATEGORIES.drop(1)) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = {
                                    haptics.performSelectionTick()
                                    category = cat
                                },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingCropUri?.let { uri ->
        ImageCropperDialog(
            sourceUri = uri,
            isBanner = true,
            onDismiss = { pendingCropUri = null },
            onCropped = { dataUri ->
                croppedCoverDataUri = dataUri
                pendingCropUri = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCommunityPostSheet(
    communityName: String,
    isPosting: Boolean,
    onDismiss: () -> Unit,
    onPublish: (streamCategory: String, body: String, imageUris: List<Uri>, attachmentUris: List<Uri>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    var streamCategory by remember { mutableStateOf("Discussion") }
    var body by remember { mutableStateOf(TextFieldValue("")) }
    val followedProfiles = LocalFollowedProfiles.current
    val activeMentionQuery = remember(body.text, body.selection) {
        detectActiveMentionQuery(body)
    }
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val richTextTransformation = remember(markerColor, primaryColor) {
        OrbitRichTextVisualTransformation(
            markerColor = markerColor,
            accentColor = primaryColor
        )
    }
    val selectedUris = remember { mutableStateListOf<Uri>() }
    val selectedAttachments = remember { mutableStateListOf<Uri>() }
    var previewLightboxUrl by remember { mutableStateOf<String?>(null) }
    var previewVideoAttachment by remember { mutableStateOf<PostAttachment?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        val remaining = (4 - selectedUris.size).coerceAtLeast(0)
        selectedUris.addAll(uris.take(remaining))
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && selectedAttachments.size < 3 && uri !in selectedAttachments) {
            selectedAttachments.add(uri)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && selectedAttachments.size < 3 && uri !in selectedAttachments) {
            selectedAttachments.add(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Post to $communityName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        haptics.performMedium()
                        onPublish(streamCategory, body.text, selectedUris.toList(), selectedAttachments.toList())
                    },
                    enabled = (body.text.trim().isNotEmpty() || selectedUris.isNotEmpty() || selectedAttachments.isNotEmpty()) && !isPosting,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("community_post_publish_btn")
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Share")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(COMMUNITY_STREAM_CATEGORIES.drop(1)) { cat ->
                    FilterChip(
                        selected = streamCategory == cat,
                        onClick = { streamCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = body,
                onValueChange = { incoming -> if (incoming.text.length <= 1000) body = incoming },
                visualTransformation = richTextTransformation,
                placeholder = { Text("Share an update, photo, video (up to 500 MB), or @mention with this Space…") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("community_post_body_input")
            )

            val mentionableProfiles = LocalMentionableProfiles.current
            val followedIds = remember(followedProfiles) { followedProfiles.map { it.resolvedId }.toSet() }
            MentionSuggestionsDropdown(
                activeQuery = activeMentionQuery,
                followedProfiles = mentionableProfiles.ifEmpty { followedProfiles },
                followedIds = followedIds,
                onSelectUser = { user ->
                    activeMentionQuery?.let { active ->
                        body = insertMention(
                            value = body,
                            active = active,
                            username = user.username,
                            maxChars = 1000
                        )
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            )

            if (selectedUris.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedUris) { uri ->
                        UploadedPhotoPreviewTile(
                            model = uri,
                            previewLabel = "Preview",
                            size = 92.dp,
                            onPreviewClick = { previewLightboxUrl = uri.toString() },
                            onRemoveClick = { selectedUris.remove(uri) }
                        )
                    }
                }
            }

            if (selectedAttachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedAttachments.forEach { uri ->
                        UploadedAttachmentPreviewCard(
                            uri = uri,
                            onPreviewImage = { imgUrl -> previewLightboxUrl = imgUrl },
                            onPreviewVideo = { vidAtt -> previewVideoAttachment = vidAtt },
                            onRemove = { selectedAttachments.remove(uri) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = selectedUris.size < 4,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("community_add_photo_btn")
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Photos", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    enabled = selectedAttachments.size < 3,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("community_add_video_btn")
                ) {
                    Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Video", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = selectedAttachments.size < 3,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("community_attach_file_btn")
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("File", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(Modifier.weight(1f))
                Text(
                    text = "${1000 - body.text.length}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityCommentsSheet(
    post: CommunityPost,
    spaceOwnerId: String = "",
    comments: List<CommunityComment>,
    onDismiss: () -> Unit,
    onSendComment: (body: String, onDone: () -> Unit) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentText by remember { mutableStateOf(TextFieldValue("")) }
    val followedProfiles = LocalFollowedProfiles.current
    val activeMentionQuery = remember(commentText.text, commentText.selection) {
        detectActiveMentionQuery(commentText)
    }
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val richTextTransformation = remember(markerColor, primaryColor) {
        OrbitRichTextVisualTransformation(
            markerColor = markerColor,
            accentColor = primaryColor
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Text(
                text = "Comments (${comments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (comments.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp)
                ) {
                    Text(
                        text = "No comments yet. Join the conversation!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        val isCommentAuthorAdmin = spaceOwnerId.isNotBlank() && comment.authorId == spaceOwnerId
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            ThreadAvatar(
                                avatarUrl = comment.authorAvatarUrl,
                                displayName = comment.authorUsername,
                                size = 32.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "@${comment.authorUsername}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isCommentAuthorAdmin) {
                                        Spacer(Modifier.width(5.dp))
                                        SpaceAdminBadgePill(testTag = "space_comment_admin_badge_${comment.id}")
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = formatRelativeTime(comment.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val formattedComment = remember(comment.body, primaryColor) {
                                    parseOrbitRichText(comment.body, linkColor = primaryColor)
                                }
                                Text(
                                    text = formattedComment,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val mentionableProfiles = LocalMentionableProfiles.current
            val followedIds = remember(followedProfiles) { followedProfiles.map { it.resolvedId }.toSet() }
            MentionSuggestionsDropdown(
                activeQuery = activeMentionQuery,
                followedProfiles = mentionableProfiles.ifEmpty { followedProfiles },
                followedIds = followedIds,
                onSelectUser = { user ->
                    activeMentionQuery?.let { active ->
                        commentText = insertMention(
                            value = commentText,
                            active = active,
                            username = user.username,
                            maxChars = 500
                        )
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { incoming -> if (incoming.text.length <= 500) commentText = incoming },
                    visualTransformation = richTextTransformation,
                    placeholder = { Text("Add a comment… (type @ to tag)") },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("community_comment_input")
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val trimmed = commentText.text.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendComment(trimmed) { commentText = TextFieldValue("") }
                        }
                    },
                    enabled = commentText.text.trim().isNotEmpty(),
                    modifier = Modifier.testTag("community_comment_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send comment",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceNotFoundScreen(
    isLoading: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    CenteredThreadsContainer {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isLoading) "Loading Space…" else "Space unavailable",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("space_not_found_back")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp)
            ) {
                if (isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Syncing Space details…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "This Space is no longer available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "It may have been deleted by its Admin or the invite link expired.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (onBack != null) {
                            Spacer(Modifier.height(18.dp))
                            Button(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                                Text("Back to Spaces")
                            }
                        }
                    }
                }
            }
        }
    }
}

