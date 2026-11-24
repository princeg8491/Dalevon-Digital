package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.model.PostAttachment
import com.example.data.model.ProfileAlbum
import com.example.data.model.ProfileLink
import com.example.data.model.ProfileTab
import com.example.data.model.ThreadPost
import com.example.data.model.UserProfile
import com.example.data.model.formatCompactCount
import com.example.data.model.normalizeWebUrl
import com.example.ui.components.CenteredThreadsContainer
import com.example.ui.components.FullScreenImageLightbox
import com.example.ui.components.ImageCropperDialog
import com.example.ui.components.InlineLinkThumbnailCard
import com.example.ui.components.LocalOrbitModerationActions
import com.example.ui.components.OrbitProfileBadgePill
import com.example.ui.components.SpaceAdminBadgePill
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.ThreadPostRow
import com.example.ui.components.parseOrbitRichText
import com.example.ui.haptics.LocalOrbitHaptics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    allPosts: List<ThreadPost>,
    albums: List<ProfileAlbum> = emptyList(),
    currentUserId: String,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    isSpaceAdmin: Boolean = false,
    likedPostIds: Set<String>,
    repostedPostIds: Set<String>,
    savedPostIds: Set<String> = emptySet(),
    followingIds: Set<String>,
    onBack: (() -> Unit)? = null,
    onOpenThemeSettings: () -> Unit,
    onFollowToggle: (String) -> Unit,
    onMessageUser: ((UserProfile) -> Unit)? = null,
    onUpdateProfile: (
        displayName: String,
        username: String,
        bio: String,
        linkUrl: String,
        extraLinks: List<String>,
        croppedAvatarDataUri: String?,
        croppedBannerDataUri: String?,
        onDone: () -> Unit
    ) -> Unit,
    onAddProfileLink: (title: String, url: String, onDone: () -> Unit) -> Unit = { _, _, done -> done() },
    onRemoveProfileLink: (String) -> Unit = {},
    onTogglePinProfileLink: (String) -> Unit = {},
    onReorderProfileLinks: (List<ProfileLink>) -> Unit = {},
    onLoadAlbums: (String) -> Unit = {},
    onCreateAlbum: (title: String, uris: List<Uri>) -> Unit = { _, _ -> },
    onOpenAlbum: (ProfileAlbum) -> Unit = {},
    onPostClick: (ThreadPost) -> Unit,
    onQuotedPostClick: (String) -> Unit = {},
    onAuthorClick: (String) -> Unit,
    onLikeClick: (ThreadPost) -> Unit,
    onReplyClick: (ThreadPost) -> Unit,
    onRepostClick: (ThreadPost) -> Unit,
    onSaveClick: (ThreadPost) -> Unit = {},
    onEditClick: (ThreadPost) -> Unit = {},
    onDeleteClick: (ThreadPost) -> Unit,
    onImageClick: (String) -> Unit,
    onDownloadPhoto: (String) -> Unit = {},
    onDownloadAttachment: (PostAttachment) -> Unit = {}
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val moderation = LocalOrbitModerationActions.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    var selectedTab by remember { mutableStateOf(ProfileTab.THREADS) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var isArrangingLinks by remember { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }

    // Direct quick-crop from tapping the banner on own profile
    var quickCropBannerUri by remember { mutableStateOf<Uri?>(null) }
    val quickBannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) quickCropBannerUri = uri
    }

    val profileId = profile.resolvedId
    val bannerModel = remember(profile.bannerUrl) { OrbitCacheManager.toCoilModel(profile.bannerUrl) }
    val isLockedPrivateProfile = profile.privateAccount && !isOwnProfile && !isFollowing

    LaunchedEffect(profileId, selectedTab, isLockedPrivateProfile) {
        if (selectedTab == ProfileTab.MEDIA && profileId.isNotBlank() && !isLockedPrivateProfile) {
            onLoadAlbums(profileId)
        }
    }

    val orderedLinks = remember(profile.savedLinks) {
        profile.savedLinks.sortedWith(
            compareByDescending<ProfileLink> { it.pinned }.thenBy { it.sortOrder }.thenByDescending { it.createdAtMillis }
        )
    }

    val postsById = remember(allPosts) {
        allPosts.associateBy { it.id }
    }

    val tabPosts = remember(allPosts, selectedTab, profileId, repostedPostIds, isOwnProfile) {
        when (selectedTab) {
            ProfileTab.THREADS -> allPosts.filter { it.authorId == profileId && it.isTopLevel }
            ProfileTab.REPLIES -> allPosts.filter { it.authorId == profileId && !it.isTopLevel }
            ProfileTab.REPOSTS -> {
                if (isOwnProfile) {
                    allPosts.filter { it.id in repostedPostIds }
                } else {
                    allPosts.filter { it.authorId == profileId && it.repostOfPostId.isNotBlank() }
                }
            }
            ProfileTab.MEDIA, ProfileTab.LINKS -> emptyList()
        }
    }

    CenteredThreadsContainer {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Profile Banner positioned directly under the system status bar with a clean non-touching gap
            item(key = "profile_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 10.dp)
                        .height(220.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        )
                        .let { base ->
                            if (isOwnProfile) {
                                base.clickable {
                                    haptics.performTap()
                                    quickBannerPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            } else if (profile.bannerUrl.isNotBlank()) {
                                base.clickable { onImageClick(profile.bannerUrl) }
                            } else {
                                base
                            }
                        }
                        .testTag("profile_banner_header")
                ) {
                    if (bannerModel != null) {
                        AsyncImage(
                            model = bannerModel,
                            imageLoader = imageLoader,
                            contentDescription = "${profile.displayName} banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (onBack != null) {
                        Surface(
                            onClick = {
                                haptics.performTap()
                                onBack()
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.52f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .size(38.dp)
                                .testTag("profile_back_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (!isOwnProfile && profileId.isNotBlank()) {
                        val handle = profile.username.ifBlank { profile.displayName.ifBlank { "user" } }
                        val isMutedProfile = profileId in moderation.mutedUserIds
                        val isBlockedProfile = profileId in moderation.blockedUserIds
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Surface(
                                onClick = {
                                    haptics.performTap()
                                    profileMenuExpanded = true
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.52f),
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("profile_more_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreHoriz,
                                        contentDescription = "Profile options",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = profileMenuExpanded,
                                onDismissRequest = { profileMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isMutedProfile) {
                                                stringResource(R.string.action_unmute_user, handle)
                                            } else {
                                                stringResource(R.string.action_mute_user, handle)
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.VolumeOff,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        profileMenuExpanded = false
                                        haptics.performMedium()
                                        moderation.onToggleMuteUser(profileId)
                                    },
                                    modifier = Modifier.testTag("profile_mute_user_btn")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isBlockedProfile) {
                                                stringResource(R.string.action_unblock_user, handle)
                                            } else {
                                                stringResource(R.string.action_block_user, handle)
                                            },
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Block,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        profileMenuExpanded = false
                                        haptics.performHeavy()
                                        moderation.onToggleBlockUser(profileId)
                                    },
                                    modifier = Modifier.testTag("profile_block_user_btn")
                                )
                            }
                        }
                    }

                    // Paint brush button on bottom-right of banner to open edit banner
                    if (isOwnProfile) {
                        Surface(
                            onClick = {
                                haptics.performTap()
                                quickBannerPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.62f),
                            border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.22f)),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(38.dp)
                                .testTag("profile_change_banner_pill")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Brush,
                                    contentDescription = "Edit banner",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Classic Threads asymmetric profile header with @username positioned under the banner
            item(key = "profile_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.displayName.ifBlank { "Orbit Member" },
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            val activeBadges = remember(profile.profileBadges) { profile.resolvedProfileBadges }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_handle_and_badges_row")
                            ) {
                                Text(
                                    text = "@${profile.username.removePrefix("@").ifBlank { "member" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .testTag("profile_handle_under_banner")
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                ) {
                                    Text(
                                        text = "orbit.app",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                if (isSpaceAdmin) {
                                    SpaceAdminBadgePill(
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        testTag = "profile_badge_admin"
                                    )
                                }
                                activeBadges.forEach { badge ->
                                    OrbitProfileBadgePill(
                                        badge = badge,
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        testTag = "profile_badge_${badge.id}"
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        ThreadAvatar(
                            avatarUrl = profile.avatarUrl,
                            displayName = profile.displayName.ifBlank { profile.username },
                            size = 68.dp,
                            onClick = {
                                if (isOwnProfile) {
                                    haptics.performTap()
                                    showEditDialog = true
                                } else if (profile.avatarUrl.isNotBlank()) {
                                    onImageClick(profile.avatarUrl)
                                }
                            }
                        )
                    }

                    if (profile.bio.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = profile.bio,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Followers AND Following counts
                    val followerLabel = if (profile.followerCount == 1L) "follower" else "followers"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${formatCompactCount(profile.followerCount)} $followerLabel  ·  ${formatCompactCount(profile.followingCount)} following",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("profile_follow_counts")
                        )
                    }

                    // Clickable primary website link + up to 3 extra links directly underneath
                    if (profile.linkUrl.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        ProfileHeaderLinkRow(
                            rawUrl = profile.linkUrl,
                            testTag = "profile_bio_link"
                        )
                    }

                    val visibleExtraLinks = remember(profile.extraLinks) {
                        profile.extraLinks
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .take(3)
                    }
                    if (visibleExtraLinks.isNotEmpty()) {
                        Spacer(Modifier.height(if (profile.linkUrl.isNotBlank()) 3.dp else 6.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            visibleExtraLinks.forEachIndexed { idx, extraUrl ->
                                ProfileHeaderLinkRow(
                                    rawUrl = extraUrl,
                                    testTag = "profile_extra_link_$idx"
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Action Buttons row (Threads dual pill buttons)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isOwnProfile) {
                            OutlinedButton(
                                onClick = {
                                    haptics.performTap()
                                    showEditDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("profile_edit_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_edit),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    haptics.performShare()
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Follow @${profile.username} on Orbit!"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share profile"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("profile_share_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_share),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            if (isFollowing) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.performMedium()
                                        onFollowToggle(profileId)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("profile_follow_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_following),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        haptics.performMedium()
                                        onFollowToggle(profileId)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onSurface,
                                        contentColor = MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("profile_follow_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_follow),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                            if (onMessageUser != null) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.performTap()
                                        onMessageUser(profile)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("profile_message_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MailOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Message",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sliding Threads | Replies | Reposts | Links tab row
            item(key = "profile_tabs") {
                Spacer(Modifier.height(4.dp))
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTab.ordinal, matchContentSize = false),
                            width = Dp.Unspecified,
                            height = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    divider = {
                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        )
                    }
                ) {
                    ProfileTab.entries.forEach { tab ->
                        val selected = tab == selectedTab
                        Tab(
                            selected = selected,
                            onClick = {
                                haptics.performSelectionTick()
                                selectedTab = tab
                            },
                            modifier = Modifier.testTag("profile_tab_${tab.name.lowercase()}"),
                            text = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        )
                    }
                }
            }

            if (isLockedPrivateProfile) {
                item(key = "profile_private_notice") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 28.dp)
                            .testTag("profile_private_lock_notice")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "This account is private",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Follow @${profile.username} to see their threads, media albums, and saved links.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (selectedTab == ProfileTab.MEDIA) {
                item(key = "profile_media_albums") {
                    ProfileMediaAlbumsTab(
                        albums = albums,
                        isOwnProfile = isOwnProfile,
                        onCreateAlbum = onCreateAlbum,
                        onOpenAlbum = onOpenAlbum
                    )
                }
            } else if (selectedTab == ProfileTab.LINKS) {
                if (isOwnProfile) {
                    item(key = "profile_add_link_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Saved Links (${orderedLinks.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (orderedLinks.size > 1) {
                                        OutlinedButton(
                                            onClick = {
                                                haptics.performTap()
                                                isArrangingLinks = !isArrangingLinks
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("profile_arrange_links_button")
                                        ) {
                                            Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isArrangingLinks) "Done" else "Arrange")
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            haptics.performTap()
                                            showAddLinkDialog = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("profile_add_link_button")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Add link")
                                    }
                                }
                            }
                            if (orderedLinks.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isArrangingLinks) {
                                        "Use arrows to rearrange your links."
                                    } else {
                                        "Hold any link to pin or unpin it to the top."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (orderedLinks.isEmpty()) {
                    item(key = "profile_empty_links") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = if (isOwnProfile) {
                                        "No saved links yet. Add named links with live thumbnails!"
                                    } else {
                                        "No links shared yet."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = orderedLinks,
                        key = { "link_${it.id}_${it.url}" }
                    ) { savedLink ->
                        val idx = orderedLinks.indexOfFirst { it.id == savedLink.id }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .testTag("profile_saved_link_${savedLink.id}")
                        ) {
                            if (savedLink.pinned) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PushPin,
                                        contentDescription = "Pinned link",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Pinned",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                InlineLinkThumbnailCard(
                                    title = savedLink.title.ifBlank { savedLink.url },
                                    url = savedLink.url,
                                    thumbnailUrl = savedLink.thumbnailUrl,
                                    onLongClick = if (isOwnProfile) {
                                        {
                                            haptics.performHeavy()
                                            onTogglePinProfileLink(savedLink.id)
                                        }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isOwnProfile && isArrangingLinks) {
                                    IconButton(
                                        onClick = {
                                            if (idx > 0) {
                                                haptics.performSelectionTick()
                                                val mutable = orderedLinks.toMutableList()
                                                val moved = mutable.removeAt(idx)
                                                mutable.add(idx - 1, moved)
                                                onReorderProfileLinks(mutable)
                                            }
                                        },
                                        enabled = idx > 0,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move link up")
                                    }
                                    IconButton(
                                        onClick = {
                                            if (idx in 0 until orderedLinks.lastIndex) {
                                                haptics.performSelectionTick()
                                                val mutable = orderedLinks.toMutableList()
                                                val moved = mutable.removeAt(idx)
                                                mutable.add(idx + 1, moved)
                                                onReorderProfileLinks(mutable)
                                            }
                                        },
                                        enabled = idx in 0 until orderedLinks.lastIndex,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move link down")
                                    }
                                } else if (isOwnProfile) {
                                    IconButton(
                                        onClick = {
                                            haptics.performTap()
                                            onRemoveProfileLink(savedLink.id)
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("profile_delete_link_${savedLink.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Remove link",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (tabPosts.isEmpty()) {
                item(key = "profile_empty_tab") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 24.dp)
                    ) {
                        val message = when (selectedTab) {
                            ProfileTab.THREADS -> "No threads posted yet."
                            ProfileTab.REPLIES -> "No replies posted yet."
                            ProfileTab.REPOSTS -> "No reposts yet."
                            ProfileTab.MEDIA -> "No media albums yet."
                            ProfileTab.LINKS -> "No links yet."
                        }
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    items = tabPosts,
                    key = { "${selectedTab.name}_${it.id}" }
                ) { post ->
                    if (selectedTab == ProfileTab.REPLIES) {
                        val parentPost = postsById[post.parentPostId] ?: postsById[post.rootPostId]
                        ProfileReplyParentContextCard(
                            replyPost = post,
                            parentPost = parentPost,
                            onParentClick = {
                                if (parentPost != null) {
                                    onPostClick(parentPost)
                                } else {
                                    onPostClick(post)
                                }
                            },
                            onParentAuthorClick = { authorId ->
                                if (authorId.isNotBlank()) onAuthorClick(authorId)
                            }
                        )
                    }
                    ThreadPostRow(
                        post = post,
                        isLiked = post.id in likedPostIds,
                        isReposted = post.id in repostedPostIds,
                        isSaved = post.id in savedPostIds,
                        isOwnPost = post.authorId == currentUserId,
                        isFollowingAuthor = post.authorId in followingIds,
                        repostedHeaderLabel = if (selectedTab == ProfileTab.REPOSTS) {
                            "${profile.username} reposted"
                        } else {
                            null
                        },
                        onPostClick = { onPostClick(post) },
                        onQuotedPostClick = onQuotedPostClick,
                        onAuthorClick = { onAuthorClick(post.authorId) },
                        onLikeClick = { onLikeClick(post) },
                        onReplyClick = { onReplyClick(post) },
                        onRepostClick = { onRepostClick(post) },
                        onSaveClick = { onSaveClick(post) },
                        onFollowAuthorClick = { onFollowToggle(post.authorId) },
                        onEditClick = { onEditClick(post) },
                        onDeleteClick = { onDeleteClick(post) },
                        onImageClick = onImageClick,
                        onDownloadPhoto = onDownloadPhoto,
                        onDownloadAttachment = onDownloadAttachment
                    )
                }
            }
        }
    }

    quickCropBannerUri?.let { uri ->
        ImageCropperDialog(
            sourceUri = uri,
            isBanner = true,
            onDismiss = { quickCropBannerUri = null },
            onCropped = { bannerDataUri ->
                quickCropBannerUri = null
                onUpdateProfile(
                    profile.displayName,
                    profile.username,
                    profile.bio,
                    profile.linkUrl,
                    profile.extraLinks,
                    null,
                    bannerDataUri
                ) {}
            }
        )
    }

    if (showEditDialog) {
        EditProfileFullScreenDialog(
            profile = profile,
            onDismiss = { showEditDialog = false },
            onSave = { displayName, username, bio, linkUrl, extraLinks, croppedAvatarDataUri, croppedBannerDataUri ->
                onUpdateProfile(
                    displayName,
                    username,
                    bio,
                    linkUrl,
                    extraLinks,
                    croppedAvatarDataUri,
                    croppedBannerDataUri
                ) {
                    showEditDialog = false
                }
            }
        )
    }

    if (showAddLinkDialog) {
        AddProfileLinkDialog(
            onDismiss = { showAddLinkDialog = false },
            onSave = { title, url ->
                onAddProfileLink(title, url) {
                    showAddLinkDialog = false
                }
            }
        )
    }
}

@Composable
private fun AddProfileLinkDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, url: String) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var linkName by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Save a Link",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = linkName,
                    onValueChange = { if (it.length <= 60) linkName = it },
                    label = { Text("Link name") },
                    placeholder = { Text("e.g. Portfolio, YouTube, Shop") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_link_name_input")
                )
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { if (it.length <= 300) linkUrl = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_link_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.performMedium()
                    onSave(linkName.trim(), linkUrl.trim())
                },
                enabled = linkUrl.trim().length >= 4,
                modifier = Modifier.testTag("add_link_save_button")
            ) {
                Text("Save Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProfileHeaderLinkRow(
    rawUrl: String,
    testTag: String
) {
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val normalized = remember(rawUrl) { normalizeWebUrl(rawUrl) }
    val displayLabel = remember(rawUrl) {
        rawUrl.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                haptics.performTap()
                if (normalized.isNotBlank()) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
                    }
                }
            }
            .padding(vertical = 2.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Outlined.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileReplyParentContextCard(
    replyPost: ThreadPost,
    parentPost: ThreadPost?,
    onParentClick: () -> Unit,
    onParentAuthorClick: (String) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val targetUsername = parentPost?.authorUsername?.ifBlank { null }
        ?: replyPost.replyToAuthorUsername.ifBlank { null }
    val targetDisplayName = parentPost?.authorDisplayName?.ifBlank { null }
        ?: targetUsername?.let { "@$it" }
        ?: "Original thread"
    val targetAvatar = parentPost?.authorAvatarUrl.orEmpty()
    val rawSnippet = parentPost?.body?.ifBlank { null }
        ?: replyPost.replyToBody.ifBlank { null }
        ?: when {
            parentPost?.imageUrls?.isNotEmpty() == true -> "[Photo / GIF post]"
            parentPost?.hasPoll == true -> "[Poll]"
            parentPost?.attachments?.isNotEmpty() == true -> "[Attachment]"
            else -> "View original thread"
        }
    val formattedSnippet = remember(rawSnippet, primaryColor) {
        parseOrbitRichText(rawSnippet, linkColor = primaryColor)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .testTag("profile_reply_parent_${replyPost.id}")
    ) {
        if (!targetUsername.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.profile_replying_to, targetUsername),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    haptics.performTap()
                    onParentClick()
                }
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                ThreadAvatar(
                    avatarUrl = targetAvatar,
                    displayName = targetDisplayName,
                    size = 30.dp,
                    onClick = if (parentPost != null && parentPost.authorId.isNotBlank()) {
                        { onParentAuthorClick(parentPost.authorId) }
                    } else null
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = targetDisplayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!targetUsername.isNullOrBlank()) {
                            Text(
                                text = "@$targetUsername",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = formattedSnippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (parentPost != null && (parentPost.imageUrls.isNotEmpty() || parentPost.hasPoll)) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                if (parentPost.imageUrls.isNotEmpty()) {
                                    append(if (parentPost.imageUrls.size == 1) "1 media attachment" else "${parentPost.imageUrls.size} media attachments")
                                }
                                if (parentPost.hasPoll) {
                                    if (isNotEmpty()) append(" · ")
                                    append("Includes poll")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        // Subtle vertical connector from the original post card into the user's reply row
        Box(
            modifier = Modifier
                .padding(start = 19.dp)
                .width(1.75.dp)
                .height(10.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/**
 * Full-screen Edit Profile experience with live banner cropper, avatar cropper, and bio/handle editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileFullScreenDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        username: String,
        bio: String,
        linkUrl: String,
        extraLinks: List<String>,
        croppedAvatarDataUri: String?,
        croppedBannerDataUri: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    var displayName by remember { mutableStateOf(profile.displayName) }
    var username by remember { mutableStateOf(profile.username) }
    var bio by remember { mutableStateOf(profile.bio) }
    var linkUrl by remember { mutableStateOf(profile.linkUrl) }
    var extraLink1 by remember { mutableStateOf(profile.extraLinks.getOrElse(0) { "" }) }
    var extraLink2 by remember { mutableStateOf(profile.extraLinks.getOrElse(1) { "" }) }
    var extraLink3 by remember { mutableStateOf(profile.extraLinks.getOrElse(2) { "" }) }

    var croppedAvatarDataUri by remember { mutableStateOf<String?>(null) }
    var croppedBannerDataUri by remember { mutableStateOf<String?>(null) }

    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCropIsBanner by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingCropIsBanner = false
            pendingCropUri = uri
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingCropIsBanner = true
            pendingCropUri = uri
        }
    }

    val previewBanner = croppedBannerDataUri ?: profile.bannerUrl
    val previewBannerModel = remember(previewBanner) { OrbitCacheManager.toCoilModel(previewBanner) }

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
                            text = stringResource(R.string.profile_edit),
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
                            modifier = Modifier.testTag("edit_profile_close_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                haptics.performMedium()
                                val cleanedExtraLinks = listOf(extraLink1, extraLink2, extraLink3)
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .take(3)
                                onSave(
                                    displayName,
                                    username,
                                    bio,
                                    linkUrl,
                                    cleanedExtraLinks,
                                    croppedAvatarDataUri,
                                    croppedBannerDataUri
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("edit_profile_save_button")
                        ) {
                            Text("Save")
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
                var previewPhotoUrl by remember { mutableStateOf<String?>(null) }

                // Live Banner preview + Upload & Crop trigger
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        )
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        .clickable {
                            haptics.performTap()
                            bannerPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("edit_profile_banner_button")
                ) {
                    if (previewBannerModel != null) {
                        AsyncImage(
                            model = previewBannerModel,
                            imageLoader = imageLoader,
                            contentDescription = "Profile banner preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.58f),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Change & crop banner",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                    if (previewBanner.isNotBlank()) {
                        Surface(
                            onClick = { previewPhotoUrl = previewBanner },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Preview banner",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Live Avatar preview + Upload & Crop trigger
                val activeAvatarUrl = croppedAvatarDataUri ?: profile.avatarUrl
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = activeAvatarUrl.isNotBlank()) {
                                previewPhotoUrl = activeAvatarUrl
                            }
                    ) {
                        ThreadAvatar(
                            avatarUrl = activeAvatarUrl,
                            displayName = displayName.ifBlank { username },
                            size = 76.dp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.performTap()
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("edit_profile_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Change & crop photo")
                    }
                    if (activeAvatarUrl.isNotBlank()) {
                        TextButton(
                            onClick = { previewPhotoUrl = activeAvatarUrl }
                        ) {
                            Text("Preview")
                        }
                    }
                }

                previewPhotoUrl?.let { url ->
                    FullScreenImageLightbox(
                        imageUrl = url,
                        onDismiss = { previewPhotoUrl = null },
                        onDownloadClick = { previewPhotoUrl = null }
                    )
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 50) displayName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_name_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { if (it.length <= 30) username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    prefix = { Text("@") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_username_input")
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 240) bio = it },
                    label = { Text("Bio") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_bio_input")
                )

                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { if (it.length <= 300) linkUrl = it },
                    label = { Text("Primary website link") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_link_input")
                )

                Text(
                    text = stringResource(R.string.profile_extra_links_heading),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = extraLink1,
                    onValueChange = { if (it.length <= 300) extraLink1 = it },
                    label = { Text(stringResource(R.string.profile_extra_link_label, 1)) },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_extra_link_0")
                )

                OutlinedTextField(
                    value = extraLink2,
                    onValueChange = { if (it.length <= 300) extraLink2 = it },
                    label = { Text(stringResource(R.string.profile_extra_link_label, 2)) },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_extra_link_1")
                )

                OutlinedTextField(
                    value = extraLink3,
                    onValueChange = { if (it.length <= 300) extraLink3 = it },
                    label = { Text(stringResource(R.string.profile_extra_link_label, 3)) },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_extra_link_2")
                )
            }
        }
    }

    pendingCropUri?.let { uri ->
        ImageCropperDialog(
            sourceUri = uri,
            isBanner = pendingCropIsBanner,
            onDismiss = { pendingCropUri = null },
            onCropped = { croppedDataUri ->
                if (pendingCropIsBanner) {
                    croppedBannerDataUri = croppedDataUri
                } else {
                    croppedAvatarDataUri = croppedDataUri
                }
                pendingCropUri = null
            }
        )
    }
}
