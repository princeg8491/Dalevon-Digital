package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOutOrbit
import com.example.auth.switchGoogleAccount
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.cache.OrbitMediaFiles
import com.example.data.model.ProfileAlbum
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.preferences.OrbitThemeConfig
import com.example.data.preferences.ThemePreferencesRepository
import com.example.notifications.OrbitNotificationHelper
import com.example.ui.adaptive.FoldableTwoPaneLayout
import com.example.ui.adaptive.LocalFoldableLayoutInfo
import com.example.ui.adaptive.rememberFoldableLayoutInfo
import com.example.ui.components.DockingStationDrawerContent
import com.example.ui.components.EditPostDialog
import com.example.ui.components.FullScreenImageLightbox
import com.example.ui.components.LocalFollowedProfiles
import com.example.ui.components.LocalMentionableProfiles
import com.example.ui.components.LocalOptimisticPollVotes
import com.example.ui.components.LocalOrbitCurrentUserId
import com.example.ui.components.LocalOrbitLightboxActions
import com.example.ui.components.LocalOrbitModerationActions
import com.example.ui.components.LocalPhotoPeekController
import com.example.ui.components.LocalPollVoteHandler
import com.example.ui.components.OrbitLightboxActions
import com.example.ui.components.OrbitUserModerationActions
import com.example.ui.components.PhotoPeekController
import com.example.ui.share.OrbitShareHelper
import com.example.ui.components.PhotoPeekOverlayHost
import com.example.ui.components.RepostMenuDialog
import com.example.ui.components.ThemeStudioSheet
import com.example.ui.components.ThreadsBottomBar
import com.example.ui.components.ThreadsNavigationRail
import com.example.ui.haptics.LocalOrbitHaptics
import com.example.ui.haptics.rememberOrbitHapticController
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AppSettingsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatConversationScreen
import com.example.ui.screens.CommunitiesHubScreen
import com.example.ui.screens.CommunityDetailScreen
import com.example.ui.screens.ComposeThreadSheet
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.FoldableEmptyDetailPane
import com.example.ui.screens.FoldableFeedSupportingPane
import com.example.ui.screens.MessagesInboxScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SavedPostsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SpaceNotFoundScreen
import com.example.ui.screens.ThreadDetailScreen
import com.example.ui.theme.OrbitTheme
import com.example.ui.viewmodel.MainDestination
import com.example.ui.viewmodel.SpaceOverlay
import com.example.ui.viewmodel.ThreadsViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private data class PendingNotificationLink(
    val navType: String,
    val postId: String?,
    val spaceId: String?,
    val conversationId: String?
)

class MainActivity : ComponentActivity() {
    private val pendingNotificationLink = mutableStateOf<PendingNotificationLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrbitMediaFiles.runAppUpgradeHousekeepingIfNeeded(this)
        OrbitNotificationHelper.ensureNotificationChannel(this)
        captureNotificationIntent(intent)
        enableEdgeToEdge()

        setContent {
            OrbitAppRoot(
                pendingDeepLink = pendingNotificationLink.value,
                onConsumeDeepLink = { pendingNotificationLink.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNotificationIntent(intent)
    }

    private fun captureNotificationIntent(intent: Intent?) {
        val navType = intent?.getStringExtra(OrbitNotificationHelper.EXTRA_NAV_TYPE)
            ?.takeIf { it.isNotBlank() } ?: return
        pendingNotificationLink.value = PendingNotificationLink(
            navType = navType,
            postId = intent.getStringExtra(OrbitNotificationHelper.EXTRA_POST_ID),
            spaceId = intent.getStringExtra(OrbitNotificationHelper.EXTRA_SPACE_ID),
            conversationId = intent.getStringExtra(OrbitNotificationHelper.EXTRA_CONVERSATION_ID)
        )
        intent.removeExtra(OrbitNotificationHelper.EXTRA_NAV_TYPE)
    }
}

private fun openPlayStoreForOrbit(context: Context) {
    val pkg = context.packageName
    val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
        setPackage("com.android.vending")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val genericMarketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(playStoreIntent) }
        .onFailure {
            runCatching { context.startActivity(genericMarketIntent) }
                .onFailure { runCatching { context.startActivity(webIntent) } }
        }
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

@Composable
private fun OrbitAppRoot(
    pendingDeepLink: PendingNotificationLink?,
    onConsumeDeepLink: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val imageLoader = remember(context) { OrbitCacheManager.getImageLoader(context) }
    val themeRepo = remember(context) { ThemePreferencesRepository(context) }
    val themeConfig by themeRepo.themeConfigFlow.collectAsStateWithLifecycle(
        initialValue = OrbitThemeConfig()
    )

    val haptics = rememberOrbitHapticController(
        enabled = themeConfig.hapticsEnabled,
        strength = themeConfig.hapticsStrength
    )
    val photoPeekController = remember { PhotoPeekController() }

    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showAuthThemeSheet by remember { mutableStateOf(false) }

    // Attempt silent Credential Manager sign-in on cold start if already authorized
    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            isSigningIn = true
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isSigningIn = false
                    authError = null
                },
                onUnauthenticated = {
                    isSigningIn = false
                },
                scope = scope
            )
        }
    }

    OrbitTheme(themeConfig = themeConfig) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val foldableInfo = rememberFoldableLayoutInfo(
                maxWidth = maxWidth,
                maxHeight = maxHeight
            )
            CompositionLocalProvider(
                LocalOrbitImageLoader provides imageLoader,
                LocalFoldableLayoutInfo provides foldableInfo,
                LocalOrbitHaptics provides haptics,
                LocalPhotoPeekController provides photoPeekController
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val uid = currentUser?.uid
                        if (uid == null) {
                            AuthScreen(
                                isLoading = isSigningIn,
                                errorMessage = authError,
                                onContinueWithGoogle = {
                                    isSigningIn = true
                                    authError = null
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isSigningIn = false
                                            authError = null
                                        },
                                        onAuthError = { message ->
                                            isSigningIn = false
                                            authError = message
                                        },
                                        onAuthCancelled = {
                                            isSigningIn = false
                                        },
                                        scope = scope
                                    )
                                },
                                onOpenThemeSettings = { showAuthThemeSheet = true }
                            )

                            if (showAuthThemeSheet) {
                                ThemeStudioSheet(
                                    themeConfig = themeConfig,
                                    onDynamicWallpaperChanged = { enabled ->
                                        scope.launch { themeRepo.setDynamicWallpaperEnabled(enabled) }
                                    },
                                    onDarkThemeModeChanged = { mode ->
                                        scope.launch { themeRepo.setDarkThemeMode(mode) }
                                    },
                                    onCustomSeedColorChanged = { argb ->
                                        scope.launch { themeRepo.setCustomSeedColorArgb(argb) }
                                    },
                                    onFontPresetChanged = { preset ->
                                        scope.launch { themeRepo.setFontPreset(preset) }
                                    },
                                    onDismiss = { showAuthThemeSheet = false }
                                )
                            }
                        } else {
                            // Scope ViewModel strictly to authenticated user ID
                            key(uid) {
                                val application = context.applicationContext as Application
                                val threadsViewModel: ThreadsViewModel = viewModel(
                                    key = "threads_vm_$uid",
                                    factory = ThreadsViewModel.Factory(application, uid)
                                )
                                LaunchedEffect(uid) {
                                    threadsViewModel.resumeIfReauthenticated()
                                }
                                AuthenticatedOrbitShell(
                                    viewModel = threadsViewModel,
                                    pendingDeepLink = pendingDeepLink,
                                    onConsumeDeepLink = onConsumeDeepLink,
                                    onSignOut = {
                                        threadsViewModel.prepareForSignOut()
                                        OrbitCacheManager.clearMemoryCaches()
                                        signOutOrbit(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
                                    },
                                    onSwitchAccount = { onError ->
                                        switchGoogleAccount(
                                            context = context,
                                            credentialManager = credentialManager,
                                            onAuthSuccess = {
                                                OrbitCacheManager.clearMemoryCaches()
                                            },
                                            onAuthError = { err -> onError(err) },
                                            scope = scope
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Instagram-style long-press Photo Peek & Pop overlay above all screens
                    PhotoPeekOverlayHost(controller = photoPeekController)
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedOrbitShell(
    viewModel: ThreadsViewModel,
    pendingDeepLink: PendingNotificationLink?,
    onConsumeDeepLink: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchAccount: (onError: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val foldableInfo = LocalFoldableLayoutInfo.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Request POST_NOTIFICATIONS at runtime on Android 13+ so system status-bar alerts work
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Gracefully handled regardless of user choice */ }

    LaunchedEffect(viewModel.currentUserId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !OrbitNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Consume status-bar notification taps and route directly to the target screen
    LaunchedEffect(pendingDeepLink) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        viewModel.handleNotificationDeepLink(
            navType = link.navType,
            postId = link.postId,
            spaceId = link.spaceId,
            conversationId = link.conversationId
        )
        onConsumeDeepLink()
    }

    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val feedCacheStatus by viewModel.feedCacheStatus.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val communities by viewModel.communities.collectAsStateWithLifecycle()
    val spaceAdminUserIds by viewModel.spaceAdminUserIds.collectAsStateWithLifecycle()
    val resolvedCommunityCache by viewModel.resolvedCommunityCache.collectAsStateWithLifecycle()
    val activeCommunityPosts by viewModel.activeCommunityPosts.collectAsStateWithLifecycle()
    val activeCommunityComments by viewModel.activeCommunityComments.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeDirectMessages by viewModel.activeDirectMessages.collectAsStateWithLifecycle()
    val viewedProfileAlbums by viewModel.viewedProfileAlbums.collectAsStateWithLifecycle()
    val activeAlbumItems by viewModel.activeAlbumItems.collectAsStateWithLifecycle()

    val hasUnreadActivity by viewModel.hasUnreadActivity.collectAsStateWithLifecycle()
    val hasUnreadMessages by viewModel.hasUnreadMessages.collectAsStateWithLifecycle()
    val latestAppRelease by viewModel.latestAppRelease.collectAsStateWithLifecycle()

    val likedPostIds by viewModel.likedPostIds.collectAsStateWithLifecycle()
    val repostedPostIds by viewModel.repostedPostIds.collectAsStateWithLifecycle()
    val savedPostIds by viewModel.savedPostIds.collectAsStateWithLifecycle()
    val bookmarkedPhotoKeys by viewModel.bookmarkedPhotoKeys.collectAsStateWithLifecycle()
    val optimisticPollVotes by viewModel.optimisticPollVotes.collectAsStateWithLifecycle()
    val blockedUserIds by viewModel.blockedUserIds.collectAsStateWithLifecycle()
    val mutedUserIds by viewModel.mutedUserIds.collectAsStateWithLifecycle()
    val mutedWords by viewModel.mutedWords.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()

    val selectedDestination by viewModel.selectedDestination.collectAsStateWithLifecycle()
    val selectedFeedTab by viewModel.selectedFeedTab.collectAsStateWithLifecycle()
    val currentOverlay by viewModel.currentOverlay.collectAsStateWithLifecycle()
    val showComposerSheet by viewModel.showComposerSheet.collectAsStateWithLifecycle()
    val replyTargetPost by viewModel.replyTargetPost.collectAsStateWithLifecycle()
    val quoteTargetPost by viewModel.quoteTargetPost.collectAsStateWithLifecycle()
    val repostMenuPost by viewModel.repostMenuPost.collectAsStateWithLifecycle()
    val editingPost by viewModel.editingPost.collectAsStateWithLifecycle()
    val showThemeSheet by viewModel.showThemeSheet.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isPosting by viewModel.isPosting.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val isDownloadingDriveApk by viewModel.isDownloadingDriveApk.collectAsStateWithLifecycle()
    val driveDownloadProgress by viewModel.driveDownloadProgress.collectAsStateWithLifecycle()
    val driveDownloadStatus by viewModel.driveDownloadStatus.collectAsStateWithLifecycle()

    var lightboxImageUrl by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val triggerSwitchAccount = {
        onSwitchAccount { err ->
            scope.launch { snackbarHostState.showSnackbar(err) }
        }
    }

    val useNavigationRail = foldableInfo.useNavigationRail
    val supportsTwoPane = foldableInfo.supportsTwoPane
    val isCommunitiesOverlay = currentOverlay is SpaceOverlay.CommunitiesHub ||
        currentOverlay is SpaceOverlay.CommunityDetail
    val isMessagesOverlay = currentOverlay is SpaceOverlay.MessagesInbox ||
        currentOverlay is SpaceOverlay.ChatConversation
    val isFullScreenOverlay = currentOverlay != SpaceOverlay.None

    // Close Docking Station drawer first on system Back
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Return to Home tab when pressing system Back from secondary tabs
    BackHandler(
        enabled = !drawerState.isOpen && currentOverlay == SpaceOverlay.None && selectedDestination != MainDestination.HOME
    ) {
        viewModel.selectDestination(MainDestination.HOME)
    }

    // In dual-pane mode, embedded Spaces/DMs panes suppress per-pane BackHandlers so handle Back cleanly here
    BackHandler(
        enabled = !drawerState.isOpen && supportsTwoPane && (isCommunitiesOverlay || isMessagesOverlay)
    ) {
        viewModel.popOverlay()
    }

    // Apply privacy filters (blocked users, muted users, muted words, private accounts, followers-only posts)
    val filteredFeedState = remember(
        feedState,
        blockedUserIds,
        mutedUserIds,
        mutedWords,
        followingIds,
        profiles
    ) {
        when (val state = feedState) {
            is UiState.Success -> UiState.Success(
                state.data.filter { viewModel.isPostVisibleToCurrentUser(it) }
            )
            else -> state
        }
    }

    val allPosts = remember(filteredFeedState) {
        (filteredFeedState as? UiState.Success)?.data.orEmpty()
    }

    val primaryDestinationPane: @Composable () -> Unit = {
        Crossfade(
            targetState = selectedDestination,
            animationSpec = tween(durationMillis = 180),
            label = "main_tab_crossfade"
        ) { destination ->
            when (destination) {
                MainDestination.HOME -> FeedScreen(
                    feedState = filteredFeedState,
                    selectedTab = selectedFeedTab,
                    currentUserId = viewModel.currentUserId,
                    myProfile = myProfile,
                    likedPostIds = likedPostIds,
                    repostedPostIds = repostedPostIds,
                    savedPostIds = savedPostIds,
                    followingIds = followingIds,
                    hasDirectMessages = hasUnreadMessages,
                    isRefreshing = isRefreshing,
                    feedCacheStatus = feedCacheStatus,
                    refreshCompletedEvents = viewModel.refreshCompletedEvents,
                    homeScrollToTopEvents = viewModel.homeScrollToTopEvents,
                    onSelectTab = viewModel::selectFeedTab,
                    onRefresh = viewModel::refreshFeed,
                    onOrbitLogoClick = viewModel::onOrbitLogoClick,
                    onLoadMore = viewModel::loadMorePosts,
                    onComposeClick = { viewModel.openComposer(null) },
                    onOpenDockingStation = { scope.launch { drawerState.open() } },
                    onOpenSettings = viewModel::openAppSettings,
                    onOpenCommunities = { viewModel.openCommunitiesHub(replaceRoot = useNavigationRail) },
                    onOpenDirectMessages = { viewModel.openMessagesInbox(replaceRoot = useNavigationRail) },
                    onOpenThemeSettings = viewModel::openThemeSheet,
                    onPostClick = { viewModel.openThread(it.id) },
                    onQuotedPostClick = viewModel::openThread,
                    onAuthorClick = viewModel::openUserProfile,
                    onLikeClick = viewModel::toggleLike,
                    onReplyClick = { viewModel.openComposer(replyTo = it) },
                    onRepostClick = viewModel::openRepostMenu,
                    onSaveClick = viewModel::toggleSavePost,
                    onFollowClick = viewModel::toggleFollow,
                    onEditClick = viewModel::openEditPost,
                    onDeleteClick = viewModel::deleteThread,
                    onImageClick = { lightboxImageUrl = it },
                    onDownloadPhoto = viewModel::downloadPhoto,
                    onDownloadAttachment = viewModel::downloadAttachment
                )

                MainDestination.SEARCH -> SearchScreen(
                    profiles = profiles.filterNot { it.resolvedId in blockedUserIds },
                    posts = allPosts,
                    currentUserId = viewModel.currentUserId,
                    followingIds = followingIds,
                    likedPostIds = likedPostIds,
                    repostedPostIds = repostedPostIds,
                    savedPostIds = savedPostIds,
                    onProfileClick = viewModel::openUserProfile,
                    onFollowToggle = viewModel::toggleFollow,
                    onPostClick = { viewModel.openThread(it.id) },
                    onQuotedPostClick = viewModel::openThread,
                    onLikeClick = viewModel::toggleLike,
                    onReplyClick = { viewModel.openComposer(replyTo = it) },
                    onRepostClick = viewModel::openRepostMenu,
                    onSaveClick = viewModel::toggleSavePost,
                    onEditClick = viewModel::openEditPost,
                    onDeleteClick = viewModel::deleteThread,
                    onImageClick = { lightboxImageUrl = it },
                    onDownloadPhoto = viewModel::downloadPhoto,
                    onDownloadAttachment = viewModel::downloadAttachment
                )

                MainDestination.ACTIVITY -> ActivityScreen(
                    activities = activities,
                    onMarkRead = viewModel::markActivitiesRead,
                    onActorClick = viewModel::openUserProfile,
                    onPostClick = viewModel::openThread,
                    onOpenSpace = { spaceId ->
                        viewModel.openCommunitiesHub(replaceRoot = useNavigationRail)
                        viewModel.openCommunityDetail(spaceId)
                    },
                    onAcceptSpaceInvite = viewModel::acceptSpaceInviteById,
                    onOpenAppUpdates = viewModel::openAppSettings
                )

                MainDestination.PROFILE -> ProfileScreen(
                    profile = myProfile,
                    allPosts = allPosts,
                    albums = viewedProfileAlbums,
                    currentUserId = viewModel.currentUserId,
                    isOwnProfile = true,
                    isFollowing = false,
                    isSpaceAdmin = myProfile.resolvedId in spaceAdminUserIds,
                    likedPostIds = likedPostIds,
                    repostedPostIds = repostedPostIds,
                    savedPostIds = savedPostIds,
                    followingIds = followingIds,
                    onBack = null,
                    onOpenThemeSettings = viewModel::openThemeSheet,
                    onFollowToggle = viewModel::toggleFollow,
                    onMessageUser = null,
                    onUpdateProfile = viewModel::updateProfile,
                    onAddProfileLink = viewModel::addProfileLink,
                    onRemoveProfileLink = viewModel::removeProfileLink,
                    onTogglePinProfileLink = viewModel::togglePinProfileLink,
                    onReorderProfileLinks = viewModel::reorderProfileLinks,
                    onLoadAlbums = viewModel::setAlbumsOwnerId,
                    onCreateAlbum = { title, uris -> viewModel.createAlbum(title, uris) },
                    onOpenAlbum = { album ->
                        viewModel.openAlbumDetail(album.ownerId.ifBlank { viewModel.currentUserId }, album.id)
                    },
                    onPostClick = { viewModel.openThread(it.id) },
                    onQuotedPostClick = viewModel::openThread,
                    onAuthorClick = viewModel::openUserProfile,
                    onLikeClick = viewModel::toggleLike,
                    onReplyClick = { viewModel.openComposer(replyTo = it) },
                    onRepostClick = viewModel::openRepostMenu,
                    onSaveClick = viewModel::toggleSavePost,
                    onEditClick = viewModel::openEditPost,
                    onDeleteClick = viewModel::deleteThread,
                    onImageClick = { lightboxImageUrl = it },
                    onDownloadPhoto = viewModel::downloadPhoto,
                    onDownloadAttachment = viewModel::downloadAttachment
                )
            }
        }
    }

    val renderThreadDetail: @Composable (SpaceOverlay.ThreadDetail) -> Unit = { overlay ->
        val activeThreadPost = remember(overlay.postId, allPosts) {
            allPosts.firstOrNull { it.id == overlay.postId }
                ?: OrbitCacheManager.postCache.get(overlay.postId)
        }
        if (activeThreadPost != null) {
            val replies = remember(allPosts, activeThreadPost.id) {
                allPosts
                    .filter {
                        it.parentPostId == activeThreadPost.id ||
                            it.rootPostId == activeThreadPost.id
                    }
                    .sortedBy { it.createdAtMillis }
            }
            val canReply = viewModel.canCurrentUserReply(activeThreadPost)
            val replyRestrictionLabel = when (activeThreadPost.replyMode) {
                "followers_only" -> "Only followers of @${activeThreadPost.authorUsername} can reply to this thread."
                "selected_users" -> "Only selected people can reply to this thread."
                else -> null
            }
            ThreadDetailScreen(
                rootPost = activeThreadPost,
                replies = replies,
                currentUserId = viewModel.currentUserId,
                myProfile = myProfile,
                likedPostIds = likedPostIds,
                repostedPostIds = repostedPostIds,
                savedPostIds = savedPostIds,
                followingIds = followingIds,
                isPosting = isPosting,
                canReplyToRoot = canReply,
                replyRestrictionLabel = replyRestrictionLabel,
                onBack = viewModel::popOverlay,
                onAuthorClick = viewModel::openUserProfile,
                onQuotedPostClick = viewModel::openThread,
                onLikeClick = viewModel::toggleLike,
                onReplyModalClick = { post -> viewModel.openComposer(replyTo = post) },
                onRepostClick = viewModel::openRepostMenu,
                onSaveClick = viewModel::toggleSavePost,
                onFollowClick = viewModel::toggleFollow,
                onEditClick = viewModel::openEditPost,
                onDeleteClick = viewModel::deleteThread,
                onImageClick = { lightboxImageUrl = it },
                onDownloadPhoto = viewModel::downloadPhoto,
                onDownloadAttachment = viewModel::downloadAttachment,
                onQuickReplySend = { replyBody, imageUris ->
                    viewModel.publishThread(
                        body = replyBody,
                        imageUris = imageUris,
                        attachmentUris = emptyList(),
                        parentPost = activeThreadPost
                    )
                }
            )
        } else {
            LaunchedEffect(overlay.postId) { viewModel.popOverlay() }
        }
    }

    val renderUserProfileDetail: @Composable (SpaceOverlay.UserProfileDetail) -> Unit = { overlay ->
        val viewedProfile = remember(overlay.userId, profiles) {
            profiles.firstOrNull { it.resolvedId == overlay.userId }
                ?: OrbitCacheManager.profileCache.get(overlay.userId)
                ?: UserProfile(
                    id = overlay.userId,
                    userId = overlay.userId,
                    username = "member",
                    displayName = "Orbit Member"
                )
        }
        ProfileScreen(
            profile = viewedProfile,
            allPosts = allPosts,
            albums = viewedProfileAlbums,
            currentUserId = viewModel.currentUserId,
            isOwnProfile = viewedProfile.resolvedId == viewModel.currentUserId,
            isFollowing = viewedProfile.resolvedId in followingIds,
            isSpaceAdmin = viewedProfile.resolvedId in spaceAdminUserIds,
            likedPostIds = likedPostIds,
            repostedPostIds = repostedPostIds,
            savedPostIds = savedPostIds,
            followingIds = followingIds,
            onBack = viewModel::popOverlay,
            onOpenThemeSettings = viewModel::openThemeSheet,
            onFollowToggle = viewModel::toggleFollow,
            onMessageUser = { user -> viewModel.startOrOpenDirectChatWith(user) },
            onUpdateProfile = viewModel::updateProfile,
            onAddProfileLink = viewModel::addProfileLink,
            onRemoveProfileLink = viewModel::removeProfileLink,
            onTogglePinProfileLink = viewModel::togglePinProfileLink,
            onReorderProfileLinks = viewModel::reorderProfileLinks,
            onLoadAlbums = viewModel::setAlbumsOwnerId,
            onCreateAlbum = { title, uris -> viewModel.createAlbum(title, uris) },
            onOpenAlbum = { album ->
                viewModel.openAlbumDetail(album.ownerId.ifBlank { viewedProfile.resolvedId }, album.id)
            },
            onPostClick = { viewModel.openThread(it.id) },
            onQuotedPostClick = viewModel::openThread,
            onAuthorClick = viewModel::openUserProfile,
            onLikeClick = viewModel::toggleLike,
            onReplyClick = { viewModel.openComposer(replyTo = it) },
            onRepostClick = viewModel::openRepostMenu,
            onSaveClick = viewModel::toggleSavePost,
            onEditClick = viewModel::openEditPost,
            onDeleteClick = viewModel::deleteThread,
            onImageClick = { lightboxImageUrl = it },
            onDownloadPhoto = viewModel::downloadPhoto,
            onDownloadAttachment = viewModel::downloadAttachment
        )
    }

    val renderCommunityDetail: @Composable (String, (() -> Unit)?) -> Unit = { communityId, onBack ->
        val community = remember(communityId, communities, resolvedCommunityCache) {
            communities.firstOrNull { it.id == communityId }
                ?: resolvedCommunityCache[communityId]
        }
        val hasAttemptedLookup = communityId in resolvedCommunityCache
        if (community == null) {
            LaunchedEffect(communityId) {
                if (!hasAttemptedLookup) {
                    viewModel.resolveCommunityById(communityId)
                }
            }
            SpaceNotFoundScreen(
                isLoading = !hasAttemptedLookup,
                onBack = onBack
            )
        } else {
            val visibleCommunityPosts = remember(activeCommunityPosts, blockedUserIds, mutedUserIds, viewModel.currentUserId) {
                activeCommunityPosts.filter { post ->
                    post.authorId == viewModel.currentUserId ||
                        (post.authorId !in blockedUserIds && post.authorId !in mutedUserIds)
                }
            }
            CommunityDetailScreen(
                community = community,
                posts = visibleCommunityPosts,
                profiles = profiles,
                activeComments = activeCommunityComments,
                currentUserId = viewModel.currentUserId,
                isPosting = isPosting,
                onBack = onBack,
                onJoinAction = { viewModel.handleSpaceJoinAction(community) },
                onAuthorClick = viewModel::openUserProfile,
                onPlusOneClick = viewModel::toggleCommunityPlusOne,
                onEditPost = viewModel::editCommunityPost,
                onDeletePost = { post -> viewModel.deleteCommunityPost(post, community) },
                onOpenComments = viewModel::selectCommentPost,
                onAddComment = viewModel::addCommunityComment,
                onPublishPost = { streamCat, body, uris, attachmentUris, onDone ->
                    viewModel.publishCommunityPost(
                        space = community,
                        streamCategory = streamCat,
                        body = body,
                        imageUris = uris,
                        attachmentUris = attachmentUris,
                        onSuccess = onDone
                    )
                },
                onSaveAdminSettings = { name, desc, coverUrl, privacyMode, onDone ->
                    viewModel.updateSpaceAdminSettings(
                        spaceId = community.id,
                        name = name,
                        description = desc,
                        coverUrl = coverUrl,
                        privacyMode = privacyMode,
                        onDone = onDone
                    )
                },
                onDeleteSpace = { onDeleted ->
                    viewModel.deleteSpace(community.id, onDeleted)
                },
                onApproveRequest = { uid -> viewModel.approveJoinRequest(community, uid) },
                onDeclineRequest = { uid -> viewModel.declineJoinRequest(community, uid) },
                onInviteUser = { uid -> viewModel.inviteUserToSpace(community, uid) },
                onToggleModerator = { uid -> viewModel.toggleModerator(community, uid) },
                onMuteMember = { uid, hrs -> viewModel.muteSpaceMember(community, uid, hrs) },
                onKickMember = { uid -> viewModel.kickSpaceMember(community, uid) },
                onBanMember = { uid -> viewModel.banSpaceMember(community, uid) },
                onUnbanMember = { uid -> viewModel.unbanSpaceMember(community, uid) },
                onImageClick = { lightboxImageUrl = it },
                onDownloadPhoto = viewModel::downloadPhoto,
                onDownloadAttachment = viewModel::downloadAttachment
            )
        }
    }

    val renderChatConversation: @Composable (SpaceOverlay.ChatConversation, (() -> Unit)?) -> Unit = { overlay, onBack ->
        val conv = remember(overlay.conversationId, conversations) {
            conversations.firstOrNull { it.id == overlay.conversationId }
        }
        val resolvedPeer = remember(overlay.peerProfile, conv, profiles) {
            if (conv?.isGroupChat == true) {
                null
            } else {
                overlay.peerProfile
                    ?: conv?.peerId(viewModel.currentUserId)?.let { pid ->
                        profiles.firstOrNull { it.resolvedId == pid }
                    }
            }
        }
        ChatConversationScreen(
            conversationId = overlay.conversationId,
            conversation = conv,
            peerProfile = resolvedPeer,
            messages = activeDirectMessages,
            currentUserId = viewModel.currentUserId,
            isSending = isPosting,
            readReceiptsEnabled = themeConfig.readReceiptsEnabled,
            onBack = onBack,
            onOpenPeerProfile = viewModel::openUserProfile,
            onSendMessage = { body, imageUri, gifUrl, attachmentUri, onSent ->
                viewModel.sendDirectMessage(
                    conversationId = overlay.conversationId,
                    peerProfile = resolvedPeer,
                    body = body,
                    imageUri = imageUri,
                    gifUrl = gifUrl,
                    attachmentUri = attachmentUri,
                    onSent = onSent
                )
            },
            onImageClick = { lightboxImageUrl = it },
            onDownloadPhoto = viewModel::downloadPhoto,
            onDownloadAttachment = viewModel::downloadAttachment,
            onDeleteMessage = { msg -> viewModel.deleteDirectMessage(overlay.conversationId, msg.id) },
            onToggleReaction = { msg, emoji ->
                viewModel.toggleDirectMessageReaction(
                    conversationId = overlay.conversationId,
                    message = msg,
                    emoji = emoji
                )
            },
            onMarkConversationRead = { viewModel.markConversationRead(overlay.conversationId) }
        )
    }

    val renderAppSettings: @Composable () -> Unit = {
        AppSettingsScreen(
            myProfile = myProfile,
            allProfiles = profiles,
            blockedUserIds = blockedUserIds,
            mutedUserIds = mutedUserIds,
            mutedWords = mutedWords,
            themeConfig = themeConfig,
            latestAppRelease = latestAppRelease,
            feedCacheStatus = feedCacheStatus,
            isCheckingUpdate = isCheckingUpdate,
            isDownloadingDriveApk = isDownloadingDriveApk,
            driveDownloadProgress = driveDownloadProgress,
            driveDownloadStatus = driveDownloadStatus.ifBlank { null },
            onBack = viewModel::popOverlay,
            onSwitchAccount = triggerSwitchAccount,
            onSignOut = onSignOut,
            onDeleteAllPosts = { viewModel.deleteAllMyPosts() },
            onDeleteAccount = {
                viewModel.deleteMyAccount {
                    onSignOut()
                }
            },
            onSetPrivateAccount = viewModel::setPrivateAccount,
            onToggleProfileBadge = viewModel::setProfileBadgeEnabled,
            onToggleBlockUser = viewModel::toggleBlockUser,
            onToggleMuteUser = viewModel::toggleMuteUser,
            onAddMutedWord = viewModel::addMutedWord,
            onRemoveMutedWord = viewModel::removeMutedWord,
            onDynamicWallpaperChanged = viewModel::setDynamicWallpaperEnabled,
            onDarkThemeModeChanged = viewModel::setDarkThemeMode,
            onManualThemeOverride = viewModel::setManualThemeOverride,
            onCustomSeedColorChanged = viewModel::setCustomSeedColorArgb,
            onFontPresetChanged = viewModel::setFontPreset,
            onFontScaleChanged = viewModel::setFontScale,
            onHapticsEnabledChanged = viewModel::setHapticsEnabled,
            onHapticsStrengthChanged = viewModel::setHapticsStrength,
            onReadReceiptsEnabledChanged = viewModel::setReadReceiptsEnabled,
            onBottomNavSlotsChanged = viewModel::setBottomNavSlots,
            onCheckForUpdate = viewModel::checkForAppUpdate,
            onSaveDriveDownloadUrl = viewModel::saveDriveDownloadUrl,
            onDownloadFromDrive = { url ->
                viewModel.downloadApkFromDrive(url) { fallbackUrl ->
                    openExternalUrl(context, fallbackUrl)
                }
            },
            onPublishUpdateNotice = { notes, driveUrl ->
                viewModel.publishAppUpdateNotice(notes, driveUrl)
            },
            onClearOfflineFeedCache = viewModel::clearOfflineFeedCache
        )
    }

    val renderSavedPosts: @Composable () -> Unit = {
        SavedPostsScreen(
            allPosts = allPosts,
            savedPostIds = savedPostIds,
            likedPostIds = likedPostIds,
            repostedPostIds = repostedPostIds,
            currentUserId = viewModel.currentUserId,
            onBack = viewModel::popOverlay,
            onPostClick = { viewModel.openThread(it.id) },
            onAuthorClick = viewModel::openUserProfile,
            onLikeClick = viewModel::toggleLike,
            onReplyClick = { viewModel.openComposer(replyTo = it) },
            onRepostClick = viewModel::openRepostMenu,
            onSaveClick = viewModel::toggleSavePost,
            onEditPost = viewModel::openEditPost,
            onDeletePost = viewModel::deleteThread,
            onDownloadPhoto = viewModel::downloadPhoto,
            onDownloadAttachment = viewModel::downloadAttachment
        )
    }

    val renderAlbumDetail: @Composable (SpaceOverlay.AlbumDetail) -> Unit = { overlay ->
        LaunchedEffect(overlay.userId) {
            viewModel.setAlbumsOwnerId(overlay.userId)
        }
        val activeAlbum = remember(overlay.albumId, viewedProfileAlbums) {
            viewedProfileAlbums.firstOrNull { it.id == overlay.albumId }
                ?: ProfileAlbum(
                    id = overlay.albumId,
                    ownerId = overlay.userId,
                    title = "Album"
                )
        }
        AlbumDetailScreen(
            album = activeAlbum,
            items = activeAlbumItems,
            isOwner = overlay.userId == viewModel.currentUserId,
            allProfiles = profiles.filterNot { it.resolvedId in blockedUserIds },
            currentUserId = viewModel.currentUserId,
            onBack = viewModel::popOverlay,
            onAddMedia = { uris ->
                viewModel.addMediaToAlbum(
                    albumId = activeAlbum.id,
                    existingCount = activeAlbumItems.size,
                    existingCoverUrl = activeAlbum.coverUrl,
                    uris = uris
                )
            },
            onRemoveItems = { ids ->
                viewModel.removeItemsFromAlbum(activeAlbum, ids, activeAlbumItems)
            },
            onSetCover = { coverUrl ->
                viewModel.setAlbumCover(activeAlbum.id, coverUrl)
            },
            onReorderItems = { reordered ->
                viewModel.reorderAlbumItems(activeAlbum.id, reordered)
            },
            onDeleteAlbum = {
                viewModel.deleteAlbum(activeAlbum.id)
            },
            onShareToFeed = {
                viewModel.shareAlbumToFeed(activeAlbum, activeAlbumItems)
            },
            onShareToUser = { recipient ->
                viewModel.shareAlbumToUser(activeAlbum, activeAlbumItems, recipient)
            },
            onDownloadPhoto = viewModel::downloadPhoto
        )
    }

    val followedProfiles = remember(profiles, followingIds, blockedUserIds, viewModel.currentUserId) {
        profiles.filter { profile ->
            val uid = profile.resolvedId
            uid.isNotBlank() &&
                uid != viewModel.currentUserId &&
                uid in followingIds &&
                uid !in blockedUserIds
        }
    }

    val mentionableProfiles = remember(profiles, followingIds, blockedUserIds, viewModel.currentUserId) {
        profiles
            .filter { profile ->
                val uid = profile.resolvedId
                uid.isNotBlank() &&
                    uid != viewModel.currentUserId &&
                    uid !in blockedUserIds &&
                    profile.username.isNotBlank()
            }
            .sortedWith(
                compareByDescending<UserProfile> { it.resolvedId in followingIds }
                    .thenBy { it.username.lowercase() }
            )
    }

    val moderationActions = remember(blockedUserIds, mutedUserIds, viewModel) {
        OrbitUserModerationActions(
            blockedUserIds = blockedUserIds,
            mutedUserIds = mutedUserIds,
            onToggleMuteUser = viewModel::toggleMuteUser,
            onToggleBlockUser = viewModel::toggleBlockUser
        )
    }

    val lightboxActions = remember(savedPostIds, bookmarkedPhotoKeys, allPosts, viewModel, context) {
        OrbitLightboxActions(
            onSavePhoto = viewModel::savePhotoToGallery,
            onSharePhoto = { url -> OrbitShareHelper.sharePhotoItem(context = context, imageUrl = url) },
            onDownloadPhoto = viewModel::downloadPhotoToDownloads,
            isBookmarked = { url -> viewModel.isPhotoBookmarked(url) },
            onToggleBookmark = viewModel::toggleBookmarkPhoto
        )
    }

    CompositionLocalProvider(
        LocalOrbitCurrentUserId provides viewModel.currentUserId,
        LocalOrbitModerationActions provides moderationActions,
        LocalOrbitLightboxActions provides lightboxActions,
        LocalPollVoteHandler provides viewModel::voteOnPoll,
        LocalOptimisticPollVotes provides optimisticPollVotes,
        LocalFollowedProfiles provides followedProfiles,
        LocalMentionableProfiles provides mentionableProfiles
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            DockingStationDrawerContent(
                myProfile = myProfile,
                latestAppRelease = latestAppRelease,
                hasUnreadMessages = hasUnreadMessages,
                savedPostCount = savedPostIds.size,
                dockTabOrder = themeConfig.dockTabOrder,
                onUpdateDockTabOrder = viewModel::setDockTabOrder,
                onSwitchAccount = triggerSwitchAccount,
                onSignOut = onSignOut,
                onOpenSettings = viewModel::openAppSettings,
                onOpenSavedPosts = viewModel::openSavedPosts,
                onOpenThemeStudio = viewModel::openThemeSheet,
                onOpenSpaces = { viewModel.openCommunitiesHub(replaceRoot = useNavigationRail) },
                onOpenDms = { viewModel.openMessagesInbox(replaceRoot = useNavigationRail) },
                onOpenProfile = { viewModel.selectDestination(MainDestination.PROFILE) },
                onOpenPlayStoreUpdate = { openPlayStoreForOrbit(context) },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && !isFullScreenOverlay) {
                    ThreadsBottomBar(
                        currentDestination = selectedDestination,
                        slots = themeConfig.bottomNavSlots,
                        isSpacesSelected = isCommunitiesOverlay,
                        isDmsSelected = isMessagesOverlay,
                        hasActivityItems = hasUnreadActivity,
                        hasUnreadMessages = hasUnreadMessages,
                        onSelectDestination = viewModel::selectDestination,
                        onOpenSpaces = { viewModel.openCommunitiesHub(replaceRoot = false) },
                        onOpenDms = { viewModel.openMessagesInbox(replaceRoot = false) },
                        onComposeClick = { viewModel.openComposer(null) },
                        onUpdateSlots = viewModel::setBottomNavSlots
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && currentOverlay !is SpaceOverlay.AppSettings) {
                    ThreadsNavigationRail(
                        currentDestination = selectedDestination,
                        isCommunitiesSelected = isCommunitiesOverlay,
                        isMessagesSelected = isMessagesOverlay,
                        hasActivityItems = hasUnreadActivity,
                        hasDirectMessages = hasUnreadMessages,
                        onSelectDestination = viewModel::selectDestination,
                        onOpenCommunities = { viewModel.openCommunitiesHub(replaceRoot = true) },
                        onOpenMessages = { viewModel.openMessagesInbox(replaceRoot = true) },
                        onOpenThemeSettings = viewModel::openThemeSheet,
                        onOpenAppSettings = viewModel::openAppSettings,
                        onComposeClick = { viewModel.openComposer(null) }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (supportsTwoPane) {
                        when (val overlay = currentOverlay) {
                            is SpaceOverlay.AppSettings -> renderAppSettings()
                            is SpaceOverlay.SavedPosts -> renderSavedPosts()
                            is SpaceOverlay.AlbumDetail -> renderAlbumDetail(overlay)

                            is SpaceOverlay.CommunitiesHub,
                            is SpaceOverlay.CommunityDetail -> {
                                val selectedCommId = (overlay as? SpaceOverlay.CommunityDetail)?.communityId
                                FoldableTwoPaneLayout(
                                    info = foldableInfo,
                                    leftWeight = 0.42f,
                                    leftPane = {
                                        CommunitiesHubScreen(
                                            communities = communities,
                                            currentUserId = viewModel.currentUserId,
                                            selectedCommunityId = selectedCommId,
                                            onBack = null,
                                            onOpenCommunity = viewModel::openCommunityDetail,
                                            onJoinAction = viewModel::handleSpaceJoinAction,
                                            onCreateCommunity = viewModel::createCommunity
                                        )
                                    },
                                    rightPane = {
                                        if (overlay is SpaceOverlay.CommunityDetail) {
                                            renderCommunityDetail(overlay.communityId, null)
                                        } else {
                                            FoldableEmptyDetailPane(
                                                icon = Icons.Outlined.Groups,
                                                title = "Select a Space",
                                                subtitle = "Pick a Space on the left to browse categorized streams, +1 posts, and join discussions."
                                            )
                                        }
                                    }
                                )
                            }

                            is SpaceOverlay.MessagesInbox,
                            is SpaceOverlay.ChatConversation -> {
                                val selectedConvId = (overlay as? SpaceOverlay.ChatConversation)?.conversationId
                                FoldableTwoPaneLayout(
                                    info = foldableInfo,
                                    leftWeight = 0.42f,
                                    leftPane = {
                                        MessagesInboxScreen(
                                            conversations = conversations,
                                            allProfiles = profiles.filterNot { it.resolvedId in blockedUserIds },
                                            currentUserId = viewModel.currentUserId,
                                            selectedConversationId = selectedConvId,
                                            onBack = null,
                                            onMarkRead = viewModel::markMessagesRead,
                                            onOpenConversation = { conv ->
                                                val peer = if (conv.isGroupChat) {
                                                    null
                                                } else {
                                                    val peerId = conv.peerId(viewModel.currentUserId)
                                                    profiles.firstOrNull { it.resolvedId == peerId }
                                                }
                                                viewModel.openDirectChat(conv.id, peer)
                                            },
                                            onStartChatWithUser = viewModel::startOrOpenDirectChatWith,
                                            onStartGroupChat = viewModel::startOrOpenGroupChatWith,
                                            onDeleteConversation = { conv -> viewModel.deleteConversation(conv.id) }
                                        )
                                    },
                                    rightPane = {
                                        if (overlay is SpaceOverlay.ChatConversation) {
                                            renderChatConversation(overlay, null)
                                        } else {
                                            FoldableEmptyDetailPane(
                                                icon = Icons.Outlined.MailOutline,
                                                title = "Select a message",
                                                subtitle = "Choose an existing conversation on the left or start a new 1-on-1 or group chat."
                                            )
                                        }
                                    }
                                )
                            }

                            is SpaceOverlay.ThreadDetail,
                            is SpaceOverlay.UserProfileDetail,
                            SpaceOverlay.None -> {
                                FoldableTwoPaneLayout(
                                    info = foldableInfo,
                                    leftWeight = 0.54f,
                                    leftPane = primaryDestinationPane,
                                    rightPane = {
                                        when (overlay) {
                                            is SpaceOverlay.ThreadDetail -> renderThreadDetail(overlay)
                                            is SpaceOverlay.UserProfileDetail -> renderUserProfileDetail(overlay)
                                            else -> FoldableFeedSupportingPane(
                                                currentUserId = viewModel.currentUserId,
                                                communities = communities,
                                                conversations = conversations,
                                                profiles = profiles.filterNot { it.resolvedId in blockedUserIds },
                                                followingIds = followingIds,
                                                onComposeClick = { viewModel.openComposer(null) },
                                                onOpenCommunitiesHub = { viewModel.openCommunitiesHub(replaceRoot = true) },
                                                onOpenCommunity = { communityId ->
                                                    viewModel.openCommunitiesHub(replaceRoot = true)
                                                    viewModel.openCommunityDetail(communityId)
                                                },
                                                onOpenMessagesInbox = { viewModel.openMessagesInbox(replaceRoot = true) },
                                                onOpenConversation = { conv ->
                                                    val peer = if (conv.isGroupChat) {
                                                        null
                                                    } else {
                                                        val peerId = conv.peerId(viewModel.currentUserId)
                                                        profiles.firstOrNull { it.resolvedId == peerId }
                                                    }
                                                    viewModel.openMessagesInbox(replaceRoot = true)
                                                    viewModel.openDirectChat(conv.id, peer)
                                                },
                                                onOpenProfile = viewModel::openUserProfile,
                                                onFollowToggle = viewModel::toggleFollow
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        when (val overlay = currentOverlay) {
                            is SpaceOverlay.AppSettings -> renderAppSettings()
                            is SpaceOverlay.SavedPosts -> renderSavedPosts()
                            is SpaceOverlay.AlbumDetail -> renderAlbumDetail(overlay)
                            is SpaceOverlay.ThreadDetail -> renderThreadDetail(overlay)
                            is SpaceOverlay.UserProfileDetail -> renderUserProfileDetail(overlay)
                            is SpaceOverlay.CommunitiesHub -> CommunitiesHubScreen(
                                communities = communities,
                                currentUserId = viewModel.currentUserId,
                                onBack = viewModel::popOverlay,
                                onOpenCommunity = viewModel::openCommunityDetail,
                                onJoinAction = viewModel::handleSpaceJoinAction,
                                onCreateCommunity = viewModel::createCommunity
                            )
                            is SpaceOverlay.CommunityDetail -> renderCommunityDetail(
                                overlay.communityId,
                                viewModel::popOverlay
                            )
                            is SpaceOverlay.MessagesInbox -> MessagesInboxScreen(
                                conversations = conversations,
                                allProfiles = profiles.filterNot { it.resolvedId in blockedUserIds },
                                currentUserId = viewModel.currentUserId,
                                onBack = viewModel::popOverlay,
                                onMarkRead = viewModel::markMessagesRead,
                                onOpenConversation = { conv ->
                                    val peer = if (conv.isGroupChat) {
                                        null
                                    } else {
                                        val peerId = conv.peerId(viewModel.currentUserId)
                                        profiles.firstOrNull { it.resolvedId == peerId }
                                    }
                                    viewModel.openDirectChat(conv.id, peer)
                                },
                                onStartChatWithUser = viewModel::startOrOpenDirectChatWith,
                                onStartGroupChat = viewModel::startOrOpenGroupChatWith,
                                onDeleteConversation = { conv -> viewModel.deleteConversation(conv.id) }
                            )
                            is SpaceOverlay.ChatConversation -> renderChatConversation(
                                overlay,
                                viewModel::popOverlay
                            )
                            SpaceOverlay.None -> primaryDestinationPane()
                        }
                    }
                }
            }
        }
    }

    if (showComposerSheet) {
        ComposeThreadSheet(
            myProfile = myProfile,
            allProfiles = profiles.filterNot { it.resolvedId in blockedUserIds },
            currentUserId = viewModel.currentUserId,
            replyTarget = replyTargetPost,
            quoteTarget = quoteTargetPost,
            isPosting = isPosting,
            onDismiss = viewModel::closeComposer,
            onPublish = { body, imageUris, gifUrls, attachmentUris, audienceMode, replyMode, allowedReplyUserIds, pollOptions, pollDurationHours ->
                viewModel.publishThread(
                    body = body,
                    imageUris = imageUris,
                    gifUrls = gifUrls,
                    attachmentUris = attachmentUris,
                    parentPost = replyTargetPost,
                    quotedPost = quoteTargetPost,
                    audienceMode = audienceMode,
                    replyMode = replyMode,
                    allowedReplyUserIds = allowedReplyUserIds,
                    pollOptions = pollOptions,
                    pollDurationHours = pollDurationHours
                )
            }
        )
    }

    repostMenuPost?.let { targetPost ->
        RepostMenuDialog(
            isAlreadyReposted = targetPost.id in repostedPostIds,
            onRepostClick = { viewModel.toggleRepost(targetPost) },
            onQuoteClick = { viewModel.openComposer(quotePost = targetPost) },
            onDismiss = viewModel::closeRepostMenu
        )
    }

    editingPost?.let { postToEdit ->
        EditPostDialog(
            initialText = postToEdit.body,
            title = "Edit post",
            maxChars = 500,
            onDismiss = viewModel::closeEditPost,
            onSave = { updatedBody ->
                viewModel.saveEditedThread(postToEdit, updatedBody) {
                    viewModel.closeEditPost()
                }
            }
        )
    }

    if (showThemeSheet) {
        ThemeStudioSheet(
            themeConfig = themeConfig,
            onDynamicWallpaperChanged = viewModel::setDynamicWallpaperEnabled,
            onDarkThemeModeChanged = viewModel::setDarkThemeMode,
            onCustomSeedColorChanged = viewModel::setCustomSeedColorArgb,
            onFontPresetChanged = viewModel::setFontPreset,
            onFontScaleChanged = viewModel::setFontScale,
            onDismiss = viewModel::closeThemeSheet
        )
    }

    lightboxImageUrl?.let { url ->
        FullScreenImageLightbox(
            imageUrl = url,
            onDismiss = { lightboxImageUrl = null },
            onDownloadClick = viewModel::downloadPhoto
        )
    }
    }
}
