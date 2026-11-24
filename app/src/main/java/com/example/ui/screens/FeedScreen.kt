package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.cache.FeedCacheStatus
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.model.FeedTab
import com.example.data.model.PostAttachment
import com.example.data.model.ThreadPost
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.formatRelativeTime
import com.example.ui.components.CenteredThreadsContainer
import com.example.ui.components.OrbitSpiralMark
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.ThreadPostRow
import com.example.ui.haptics.BindScrollHaptics
import com.example.ui.haptics.LocalOrbitHaptics
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    feedState: UiState<List<ThreadPost>>,
    selectedTab: FeedTab,
    currentUserId: String,
    myProfile: UserProfile,
    likedPostIds: Set<String>,
    repostedPostIds: Set<String>,
    savedPostIds: Set<String> = emptySet(),
    followingIds: Set<String>,
    hasDirectMessages: Boolean = false,
    isRefreshing: Boolean,
    feedCacheStatus: FeedCacheStatus = FeedCacheStatus(),
    refreshCompletedEvents: SharedFlow<Boolean>? = null,
    homeScrollToTopEvents: SharedFlow<Unit>? = null,
    onSelectTab: (FeedTab) -> Unit,
    onRefresh: () -> Unit,
    onOrbitLogoClick: () -> Unit = onRefresh,
    onLoadMore: () -> Unit,
    onComposeClick: () -> Unit,
    onOpenDockingStation: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenCommunities: () -> Unit = {},
    onOpenDirectMessages: () -> Unit = {},
    onOpenThemeSettings: () -> Unit = {},
    onPostClick: (ThreadPost) -> Unit,
    onQuotedPostClick: (String) -> Unit = {},
    onAuthorClick: (String) -> Unit,
    onLikeClick: (ThreadPost) -> Unit,
    onReplyClick: (ThreadPost) -> Unit,
    onRepostClick: (ThreadPost) -> Unit,
    onSaveClick: (ThreadPost) -> Unit = {},
    onFollowClick: (String) -> Unit,
    onEditClick: (ThreadPost) -> Unit = {},
    onDeleteClick: (ThreadPost) -> Unit,
    onImageClick: (String) -> Unit,
    onDownloadPhoto: (String) -> Unit = {},
    onDownloadAttachment: (PostAttachment) -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    val listState = rememberLazyListState()
    val framesListState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()

    BindScrollHaptics(listState)
    BindScrollHaptics(framesListState)

    val currentSelectedTab by rememberUpdatedState(selectedTab)
    LaunchedEffect(homeScrollToTopEvents) {
        homeScrollToTopEvents?.collect {
            val targetState = if (currentSelectedTab == FeedTab.FRAMES) framesListState else listState
            if (targetState.firstVisibleItemIndex > 10) {
                targetState.scrollToItem(10)
            }
            targetState.animateScrollToItem(0)
        }
    }

    // Tactile pull-threshold detent when user pulls far enough to trigger refresh
    var crossedPullThreshold by remember { mutableStateOf(false) }
    LaunchedEffect(pullRefreshState) {
        snapshotFlow { pullRefreshState.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { pastThreshold ->
                if (pastThreshold && !crossedPullThreshold && !isRefreshing) {
                    haptics.performSelectionTick()
                }
                crossedPullThreshold = pastThreshold
            }
    }

    // Crisp confirmation haptic once Firestore pull-to-refresh finishes updating
    LaunchedEffect(refreshCompletedEvents) {
        refreshCompletedEvents?.collect { succeeded ->
            if (succeeded) {
                haptics.performRefreshComplete()
            } else {
                haptics.performMedium()
            }
        }
    }

    val allSuccessPosts = remember(feedState) {
        (feedState as? UiState.Success)?.data.orEmpty()
    }

    val topLevelPosts = remember(allSuccessPosts, selectedTab, followingIds, currentUserId) {
        val all = allSuccessPosts.filter { it.isTopLevel }
        when (selectedTab) {
            FeedTab.FOR_YOU -> all
            FeedTab.FOLLOWING -> all.filter { it.authorId in followingIds || it.authorId == currentUserId }
            FeedTab.FRAMES -> all.filter { it.hasVisualMedia }
        }
    }

    val activeListState = if (selectedTab == FeedTab.FRAMES) framesListState else listState

    // Collapsing brand header behavior like Threads when scrolling down
    val showBrandLogo by remember(activeListState) {
        derivedStateOf {
            activeListState.firstVisibleItemIndex == 0 && activeListState.firstVisibleItemScrollOffset < 64
        }
    }

    // Scroll-ahead media prefetching: pre-warm Coil RAM/disk cache for upcoming items
    val firstVisibleIndex by remember(activeListState) { derivedStateOf { activeListState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleIndex, topLevelPosts) {
        if (topLevelPosts.isNotEmpty()) {
            OrbitCacheManager.prefetchFeedMedia(
                context = context,
                imageLoader = imageLoader,
                posts = topLevelPosts,
                firstVisibleIndex = firstVisibleIndex,
                prefetchCount = 6
            )
        }
    }

    // Automatic incremental pagination near the end of the LazyColumn
    val shouldLoadMore by remember(topLevelPosts.size, activeListState) {
        derivedStateOf {
            val total = activeListState.layoutInfo.totalItemsCount
            val lastVisible = activeListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 10 && lastVisible >= total - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    CenteredThreadsContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar: Docking Station Avatar (left) | Orbit Mark (center) | Settings (right)
            AnimatedVisibility(
                visible = showBrandLogo,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptics.performTap()
                                onOpenDockingStation()
                            }
                            .testTag("feed_docking_station_button")
                    ) {
                        ThreadAvatar(
                            avatarUrl = myProfile.avatarUrl,
                            displayName = myProfile.displayName.ifBlank { myProfile.username },
                            size = 32.dp
                        )
                    }

                    // Top Center: Orbit Spiral Mark (tap to go Home or refresh + scroll to top when on Home)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptics.performMedium()
                                scope.launch { activeListState.animateScrollToItem(0) }
                                onOrbitLogoClick()
                            }
                            .testTag("feed_logo_button")
                    ) {
                        OrbitSpiralMark(
                            color = MaterialTheme.colorScheme.onSurface,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Top Right: Settings button
                    IconButton(
                        onClick = {
                            haptics.performTap()
                            onOpenSettings()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .testTag("feed_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Sliding underline tab bar ("For you" | "Following" | "Frames")
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
                FeedTab.entries.forEach { tab ->
                    val selected = tab == selectedTab
                    Tab(
                        selected = selected,
                        onClick = {
                            haptics.performTap()
                            onSelectTab(tab)
                        },
                        modifier = Modifier.testTag("feed_tab_${tab.name.lowercase()}"),
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

            // Offline mode banner showing DataStore cached feed availability & quick Retry
            AnimatedVisibility(
                visible = feedCacheStatus.isOffline,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val syncedAgo = remember(feedCacheStatus.lastSyncedAtMillis) {
                    if (feedCacheStatus.lastSyncedAtMillis > 0L) {
                        formatRelativeTime(Timestamp(Date(feedCacheStatus.lastSyncedAtMillis)))
                    } else {
                        "recently"
                    }
                }
                val displayedCount = feedCacheStatus.cachedPostCount.takeIf { it > 0 } ?: allSuccessPosts.size
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feed_offline_cache_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = stringResource(R.string.feed_offline_banner_title),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.feed_offline_banner_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = if (displayedCount > 0) {
                                    stringResource(
                                        R.string.feed_offline_banner_subtitle,
                                        displayedCount,
                                        syncedAgo
                                    )
                                } else {
                                    stringResource(R.string.feed_offline_banner_no_cache)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        TextButton(
                            onClick = {
                                haptics.performTap()
                                onRefresh()
                            },
                            modifier = Modifier.testTag("feed_offline_retry_button")
                        ) {
                            Text(
                                text = stringResource(R.string.feed_offline_retry),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    haptics.performTap()
                    onRefresh()
                },
                state = pullRefreshState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullRefreshState,
                        isRefreshing = isRefreshing,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .testTag("feed_pull_refresh_indicator")
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("feed_pull_to_refresh_box")
            ) {
                when (feedState) {
                    is UiState.Loading -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.5.dp)
                        }
                    }

                    is UiState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(32.dp)
                        ) {
                            Text(
                                text = "Couldn’t load threads",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = feedState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onRefresh) {
                                Text("Retry")
                            }
                        }
                    }

                    is UiState.Success -> {
                        if (selectedTab == FeedTab.FRAMES) {
                            FramesFeedContent(
                                posts = allSuccessPosts,
                                currentUserId = currentUserId,
                                likedPostIds = likedPostIds,
                                repostedPostIds = repostedPostIds,
                                savedPostIds = savedPostIds,
                                followingIds = followingIds,
                                listState = framesListState,
                                onComposeClick = onComposeClick,
                                onPostClick = onPostClick,
                                onAuthorClick = onAuthorClick,
                                onLikeClick = onLikeClick,
                                onReplyClick = onReplyClick,
                                onRepostClick = onRepostClick,
                                onSaveClick = onSaveClick,
                                onFollowClick = onFollowClick,
                                onEditClick = onEditClick,
                                onDeleteClick = onDeleteClick,
                                onImageClick = onImageClick,
                                onDownloadPhoto = onDownloadPhoto,
                                onDownloadAttachment = onDownloadAttachment
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(bottom = 24.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("feed_lazy_column")
                            ) {
                                // Inline Threads-style quick composer prompt at top of feed
                                item(key = "quick_compose_prompt", contentType = "composer_prompt") {
                                    QuickComposeBar(
                                        profile = myProfile,
                                        onClick = {
                                            haptics.performTap()
                                            onComposeClick()
                                        }
                                    )
                                }

                                if (topLevelPosts.isEmpty()) {
                                    item(key = "empty_feed", contentType = "empty_state") {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 32.dp, vertical = 56.dp)
                                        ) {
                                            OrbitSpiralMark(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                accentColor = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                text = if (selectedTab == FeedTab.FOLLOWING) {
                                                    "Your Following feed is quiet"
                                                } else {
                                                    "Start the first thread"
                                                },
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = if (selectedTab == FeedTab.FOLLOWING) {
                                                    "Follow accounts in Search or share a thought to see threads here."
                                                } else {
                                                    "Share a thought, link, or photo with everyone on Orbit."
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(Modifier.height(20.dp))
                                            Button(
                                                onClick = onComposeClick,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.testTag("empty_feed_compose_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("New thread")
                                            }
                                        }
                                    }
                                } else {
                                    items(
                                        items = topLevelPosts,
                                        key = { it.id },
                                        contentType = { if (it.imageUrls.isEmpty()) "text_post" else "media_post" }
                                    ) { post ->
                                        ThreadPostRow(
                                            post = post,
                                            isLiked = post.id in likedPostIds,
                                            isReposted = post.id in repostedPostIds,
                                            isSaved = post.id in savedPostIds,
                                            isOwnPost = post.authorId == currentUserId,
                                            isFollowingAuthor = post.authorId in followingIds,
                                            onPostClick = { onPostClick(post) },
                                            onAuthorClick = { onAuthorClick(post.authorId) },
                                            onLikeClick = { onLikeClick(post) },
                                            onReplyClick = { onReplyClick(post) },
                                            onRepostClick = { onRepostClick(post) },
                                            onSaveClick = { onSaveClick(post) },
                                            onFollowAuthorClick = { onFollowClick(post.authorId) },
                                            onEditClick = { onEditClick(post) },
                                            onDeleteClick = { onDeleteClick(post) },
                                            onQuotedPostClick = onQuotedPostClick,
                                            onImageClick = onImageClick,
                                            onDownloadPhoto = onDownloadPhoto,
                                            onDownloadAttachment = onDownloadAttachment
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

@Composable
private fun QuickComposeBar(
    profile: UserProfile,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("quick_compose_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            ThreadAvatar(
                avatarUrl = profile.avatarUrl,
                displayName = profile.displayName.ifBlank { profile.username },
                size = 38.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.username.ifBlank { "you" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.compose_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = stringResource(R.string.compose_post),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
        HorizontalDivider(
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    }
}
