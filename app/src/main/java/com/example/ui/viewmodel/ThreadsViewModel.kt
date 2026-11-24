package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.auth.authStateFlow
import com.example.data.cache.FeedCacheMetadata
import com.example.data.cache.FeedCacheRepository
import com.example.data.cache.FeedCacheStatus
import com.example.data.cache.FeedDataSource
import com.example.data.cache.NetworkConnectivityMonitor
import com.example.data.cache.OrbitCacheManager
import com.example.data.cache.OrbitMediaFiles
import com.example.data.model.ActivityItem
import com.example.data.model.AlbumMediaItem
import com.example.data.model.AppRelease
import com.example.data.model.Community
import com.example.data.model.CommunityComment
import com.example.data.model.CommunityPost
import com.example.data.model.Conversation
import com.example.data.model.DirectMessage
import com.example.data.model.FeedTab
import com.example.data.model.OrbitProfileBadge
import com.example.data.model.PostAttachment
import com.example.data.model.PrivateUserState
import com.example.data.model.ProfileAlbum
import com.example.data.model.ProfileLink
import com.example.data.model.ThreadPost
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.preferences.DarkThemeMode
import com.example.data.preferences.OrbitDockTab
import com.example.data.preferences.OrbitFontPreset
import com.example.data.preferences.OrbitNavSlot
import com.example.data.preferences.OrbitThemeConfig
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.repository.SocialSpacesRepository
import com.example.data.repository.ThreadsRepository
import com.example.notifications.OrbitNotificationHelper
import com.example.ui.components.resolveMentionedUserIds
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainDestination {
    HOME,
    SEARCH,
    ACTIVITY,
    PROFILE
}

sealed class SpaceOverlay {
    data object None : SpaceOverlay()
    data class ThreadDetail(val postId: String) : SpaceOverlay()
    data class UserProfileDetail(val userId: String) : SpaceOverlay()
    data object CommunitiesHub : SpaceOverlay()
    data class CommunityDetail(val communityId: String) : SpaceOverlay()
    data object MessagesInbox : SpaceOverlay()
    data class ChatConversation(val conversationId: String, val peerProfile: UserProfile?) : SpaceOverlay()
    data object AppSettings : SpaceOverlay()
    data object SavedPosts : SpaceOverlay()
    data class AlbumDetail(val userId: String, val albumId: String) : SpaceOverlay()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadsViewModel(
    application: Application,
    val currentUserId: String
) : AndroidViewModel(application) {

    private val repository = ThreadsRepository(application)
    private val spacesRepository = SocialSpacesRepository(application)
    private val themePrefs = ThemePreferencesRepository(application)
    private val feedCache = FeedCacheRepository(application)
    private val connectivityMonitor = NetworkConnectivityMonitor(application)

    private val _pageLimit = MutableStateFlow(50L)
    private val _refreshSignal = MutableStateFlow(0)
    private val _signedOutFlag = MutableStateFlow(false)

    private val activeAuthUid: StateFlow<String?> = Firebase.auth.authStateFlow()
        .combine(_signedOutFlag) { user, signedOut ->
            if (signedOut) null else user?.uid?.takeIf { it == currentUserId }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            Firebase.auth.currentUser?.uid?.takeIf { it == currentUserId }
        )

    private val _feedState = MutableStateFlow<UiState<List<ThreadPost>>>(UiState.Loading)
    val feedState: StateFlow<UiState<List<ThreadPost>>> = _feedState.asStateFlow()

    private val _activeFeedSource = MutableStateFlow(FeedDataSource.NONE)

    val isOnline: StateFlow<Boolean> = connectivityMonitor.isOnlineFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            connectivityMonitor.isCurrentlyOnline()
        )

    val feedCacheStatus: StateFlow<FeedCacheStatus> = combine(
        feedCache.observeCacheMetadata(currentUserId),
        _activeFeedSource,
        isOnline
    ) { meta, source, online ->
        FeedCacheStatus(
            source = source,
            cachedPostCount = meta.cachedPostCount,
            lastSyncedAtMillis = meta.cachedAtMillis,
            isOffline = !online,
            approxSizeBytes = meta.approxSizeBytes
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        FeedCacheStatus(isOffline = !connectivityMonitor.isCurrentlyOnline())
    )

    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    private val _privateState = MutableStateFlow(PrivateUserState(id = "state", userId = currentUserId))
    val privateState: StateFlow<PrivateUserState> = _privateState.asStateFlow()

    private val _activities = MutableStateFlow<List<ActivityItem>>(emptyList())
    val activities: StateFlow<List<ActivityItem>> = _activities.asStateFlow()

    private val _latestAppRelease = MutableStateFlow<AppRelease?>(null)
    val latestAppRelease: StateFlow<AppRelease?> = _latestAppRelease.asStateFlow()

    private val _bookmarkedPhotoKeys = MutableStateFlow<Set<String>>(loadBookmarkedPhotoKeys())
    val bookmarkedPhotoKeys: StateFlow<Set<String>> = _bookmarkedPhotoKeys.asStateFlow()

    // Orbit Spaces state
    private val _communities = MutableStateFlow<List<Community>>(emptyList())
    val communities: StateFlow<List<Community>> = _communities.asStateFlow()

    val spaceAdminUserIds: StateFlow<Set<String>> = _communities
        .map { list -> list.mapNotNull { it.ownerId.takeIf { uid -> uid.isNotBlank() } }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _activeCommunityId = MutableStateFlow<String?>(null)
    val activeCommunityPosts: StateFlow<List<CommunityPost>> = combine(activeAuthUid, _activeCommunityId) { uid, commId ->
        if (uid != null && !commId.isNullOrBlank()) commId else null
    }.flatMapLatest { commId ->
        if (commId == null) emptyFlow()
        else spacesRepository.observeCommunityPosts(commId).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeCommentPostId = MutableStateFlow<String?>(null)
    val activeCommunityComments: StateFlow<List<CommunityComment>> = combine(activeAuthUid, _activeCommentPostId) { uid, postId ->
        if (uid != null && !postId.isNullOrBlank()) postId else null
    }.flatMapLatest { postId ->
        if (postId == null) emptyFlow()
        else spacesRepository.observeCommunityComments(postId).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Twitter/X Direct Messages state
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeDirectMessages: StateFlow<List<DirectMessage>> = combine(activeAuthUid, _activeConversationId) { uid, convId ->
        if (uid != null && !convId.isNullOrBlank()) convId else null
    }.flatMapLatest { convId ->
        if (convId == null) emptyFlow()
        else spacesRepository.observeDirectMessages(convId).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Profile Media Albums state
    private val _albumsOwnerId = MutableStateFlow(currentUserId)
    val viewedProfileAlbums: StateFlow<List<ProfileAlbum>> = combine(activeAuthUid, _albumsOwnerId) { uid, ownerId ->
        if (uid != null && ownerId.isNotBlank()) ownerId else null
    }.flatMapLatest { ownerId ->
        if (ownerId == null) emptyFlow()
        else repository.observeUserAlbums(ownerId).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeAlbumKey = MutableStateFlow<Pair<String, String>?>(null)
    val activeAlbumItems: StateFlow<List<AlbumMediaItem>> = combine(activeAuthUid, _activeAlbumKey) { uid, key ->
        if (uid != null && key != null && key.first.isNotBlank() && key.second.isNotBlank()) key else null
    }.flatMapLatest { key ->
        if (key == null) emptyFlow()
        else repository.observeAlbumItems(key.first, key.second).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Community direct lookup cache (prevents synthetic broken "Space" screen)
    private val _resolvedCommunityCache = MutableStateFlow<Map<String, Community?>>(emptyMap())
    val resolvedCommunityCache: StateFlow<Map<String, Community?>> = _resolvedCommunityCache.asStateFlow()

    // Drive APK download progress state
    private val _isDownloadingDriveApk = MutableStateFlow(false)
    val isDownloadingDriveApk: StateFlow<Boolean> = _isDownloadingDriveApk.asStateFlow()
    private val _driveDownloadProgress = MutableStateFlow(0f)
    val driveDownloadProgress: StateFlow<Float> = _driveDownloadProgress.asStateFlow()
    private val _driveDownloadStatus = MutableStateFlow("")
    val driveDownloadStatus: StateFlow<String> = _driveDownloadStatus.asStateFlow()

    // Notification baseline tracking so cold start never spams old alerts
    private var activitiesBaselineInitialized = false
    private val notifiedActivityIds = mutableSetOf<String>()
    private var conversationsBaselineInitialized = false
    private val lastNotifiedDmMillis = mutableMapOf<String, Long>()

    // Optimistic overrides
    private val _optimisticLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _optimisticReposts = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _optimisticFollows = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _optimisticSaves = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _optimisticPollVotes = MutableStateFlow<Map<String, Int>>(emptyMap())
    val optimisticPollVotes: StateFlow<Map<String, Int>> = _optimisticPollVotes.asStateFlow()

    val likedPostIds: StateFlow<Set<String>> = combine(_privateState, _optimisticLikes) { state, overrides ->
        val result = state.likedPostIds.toMutableSet()
        overrides.forEach { (postId, liked) ->
            if (liked) result.add(postId) else result.remove(postId)
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val repostedPostIds: StateFlow<Set<String>> = combine(_privateState, _optimisticReposts) { state, overrides ->
        val result = state.repostedPostIds.toMutableSet()
        overrides.forEach { (postId, reposted) ->
            if (reposted) result.add(postId) else result.remove(postId)
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val savedPostIds: StateFlow<Set<String>> = combine(_privateState, _optimisticSaves) { state, overrides ->
        val result = state.savedPostIds.toMutableSet()
        overrides.forEach { (postId, saved) ->
            if (saved) result.add(postId) else result.remove(postId)
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val blockedUserIds: StateFlow<Set<String>> = _privateState
        .combine(_optimisticFollows) { state, _ -> state.blockedUserIds.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val mutedUserIds: StateFlow<Set<String>> = _privateState
        .combine(_optimisticFollows) { state, _ -> state.mutedUserIds.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val mutedWords: StateFlow<List<String>> = _privateState
        .combine(_optimisticFollows) { state, _ -> state.mutedWords }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followingIds: StateFlow<Set<String>> = combine(_privateState, _optimisticFollows) { state, overrides ->
        val result = state.followingIds.toMutableSet()
        overrides.forEach { (uid, following) ->
            if (following) result.add(uid) else result.remove(uid)
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _optimisticProfileBadges = MutableStateFlow<List<String>?>(null)

    val myProfile: StateFlow<UserProfile> = combine(_profiles, _privateState, _optimisticProfileBadges) { list, _, badgeOverride ->
        val base = list.firstOrNull { it.resolvedId == currentUserId }
            ?: OrbitCacheManager.profileCache.get(currentUserId)
            ?: UserProfile(
                id = currentUserId,
                userId = currentUserId,
                displayName = Firebase.auth.currentUser?.displayName ?: "Orbit Member",
                username = Firebase.auth.currentUser?.email?.substringBefore("@") ?: "member",
                avatarUrl = Firebase.auth.currentUser?.photoUrl?.toString().orEmpty()
            )
        if (badgeOverride != null) base.copy(profileBadges = badgeOverride) else base
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserProfile(id = currentUserId, userId = currentUserId)
    )

    val themeConfig: StateFlow<OrbitThemeConfig> = themePrefs.themeConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrbitThemeConfig())

    // Fixed unread notification bell dot: compares latest activity timestamp against lastSeenActivityMillis
    val hasUnreadActivity: StateFlow<Boolean> = combine(_activities, themeConfig) { list, cfg ->
        val latest = list.maxOfOrNull { it.createdAtMillis } ?: 0L
        latest > 0L && latest > cfg.lastSeenActivityMillis
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasUnreadMessages: StateFlow<Boolean> = combine(_conversations, themeConfig) { list, cfg ->
        val latest = list
            .filter { it.lastMessageSenderId.isNotBlank() && it.lastMessageSenderId != currentUserId }
            .maxOfOrNull { it.lastMessageAtMillis } ?: 0L
        latest > 0L && latest > cfg.lastSeenMessagesMillis
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _selectedDestination = MutableStateFlow(MainDestination.HOME)
    val selectedDestination: StateFlow<MainDestination> = _selectedDestination.asStateFlow()

    private val _selectedFeedTab = MutableStateFlow(FeedTab.FOR_YOU)
    val selectedFeedTab: StateFlow<FeedTab> = _selectedFeedTab.asStateFlow()

    private val _overlayStack = MutableStateFlow<List<SpaceOverlay>>(emptyList())
    val currentOverlay: StateFlow<SpaceOverlay> = _overlayStack
        .combine(_selectedDestination) { stack, _ -> stack.lastOrNull() ?: SpaceOverlay.None }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpaceOverlay.None)

    private val _showComposerSheet = MutableStateFlow(false)
    val showComposerSheet: StateFlow<Boolean> = _showComposerSheet.asStateFlow()

    private val _replyTargetPost = MutableStateFlow<ThreadPost?>(null)
    val replyTargetPost: StateFlow<ThreadPost?> = _replyTargetPost.asStateFlow()

    private val _quoteTargetPost = MutableStateFlow<ThreadPost?>(null)
    val quoteTargetPost: StateFlow<ThreadPost?> = _quoteTargetPost.asStateFlow()

    private val _repostMenuPost = MutableStateFlow<ThreadPost?>(null)
    val repostMenuPost: StateFlow<ThreadPost?> = _repostMenuPost.asStateFlow()

    private val _editingPost = MutableStateFlow<ThreadPost?>(null)
    val editingPost: StateFlow<ThreadPost?> = _editingPost.asStateFlow()

    private val _showThemeSheet = MutableStateFlow(false)
    val showThemeSheet: StateFlow<Boolean> = _showThemeSheet.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshCompletedEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val refreshCompletedEvents: SharedFlow<Boolean> = _refreshCompletedEvents.asSharedFlow()

    private val _homeScrollToTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val homeScrollToTopEvents: SharedFlow<Unit> = _homeScrollToTopEvents.asSharedFlow()

    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    init {
        startAuthScopedObservers()
    }

    private fun friendlyMessage(error: Throwable, fallback: String): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "Unable to sync right now. Please tap Retry or sign in again."
            raw.contains("UNAVAILABLE", ignoreCase = true) ->
                "You appear to be offline. Showing cached content."
            raw.startsWith("Firestore Error:") -> fallback
            raw.isNotBlank() -> raw.take(120)
            else -> fallback
        }
    }

    private fun startAuthScopedObservers() {
        viewModelScope.launch {
            activeAuthUid.collect { uid ->
                if (uid != null) {
                    val user = Firebase.auth.currentUser
                    repository.ensureUserInitialized(
                        uid = uid,
                        displayName = user?.displayName,
                        email = user?.email,
                        photoUrl = user?.photoUrl?.toString()
                    ).onFailure { err ->
                        _toastEvents.tryEmit(friendlyMessage(err, "Unable to sync your profile."))
                    }
                }
            }
        }

        // Immediate L1 (Memory) -> L2 (DataStore) hydration for instant cold-start & offline viewing
        viewModelScope.launch {
            val cached = feedCache.loadCachedFeed(currentUserId)
            if (cached != null && cached.posts.isNotEmpty() && _feedState.value !is UiState.Success) {
                _feedState.value = UiState.Success(cached.posts)
                _activeFeedSource.value = cached.source
            }
        }

        viewModelScope.launch {
            combine(activeAuthUid, _pageLimit, _refreshSignal) { uid, limit, _ ->
                if (uid != null) limit else null
            }.flatMapLatest { limit ->
                if (limit == null) {
                    emptyFlow()
                } else {
                    repository.observePublicPosts(limit)
                        .catch { err ->
                            if (activeAuthUid.value != null) {
                                val fallback = feedCache.loadCachedFeed(currentUserId)
                                if (fallback != null && fallback.posts.isNotEmpty()) {
                                    _feedState.value = UiState.Success(fallback.posts)
                                    _activeFeedSource.value = fallback.source
                                } else if (_feedState.value !is UiState.Success) {
                                    _feedState.value = UiState.Error(
                                        friendlyMessage(err, "Couldn’t load threads right now.")
                                    )
                                }
                            }
                        }
                }
            }.collect { posts ->
                if (posts.isEmpty() && !connectivityMonitor.isCurrentlyOnline()) {
                    val fallback = feedCache.loadCachedFeed(currentUserId)
                    if (fallback != null && fallback.posts.isNotEmpty()) {
                        _feedState.value = UiState.Success(fallback.posts)
                        _activeFeedSource.value = fallback.source
                        return@collect
                    }
                }
                _feedState.value = UiState.Success(posts)
                _activeFeedSource.value = if (connectivityMonitor.isCurrentlyOnline()) {
                    FeedDataSource.NETWORK_L3
                } else {
                    FeedDataSource.DATASTORE_L2
                }
                if (posts.isNotEmpty()) {
                    viewModelScope.launch {
                        feedCache.saveFeedSnapshot(currentUserId, posts)
                    }
                }
                val postsById = posts.associateBy { it.id }
                _optimisticPollVotes.update { current ->
                    current.filterNot { (postId, desiredIdx) ->
                        postsById[postId]?.pollVotes?.get(currentUserId)?.toInt() == desiredIdx
                    }
                }
            }
        }

        viewModelScope.launch {
            activeAuthUid.flatMapLatest { uid ->
                if (uid == null) emptyFlow()
                else repository.observePublicProfiles().catch { /* keep cache */ }
            }.collect { list ->
                _profiles.value = list
            }
        }

        viewModelScope.launch {
            activeAuthUid.flatMapLatest { uid ->
                if (uid == null) emptyFlow()
                else repository.observePrivateState(uid).catch { /* keep cache */ }
            }.collect { state ->
                _privateState.value = state
                _optimisticLikes.update { current ->
                    current.filterNot { (postId, desired) -> (postId in state.likedPostIds) == desired }
                }
                _optimisticReposts.update { current ->
                    current.filterNot { (postId, desired) -> (postId in state.repostedPostIds) == desired }
                }
                _optimisticFollows.update { current ->
                    current.filterNot { (uid, desired) -> (uid in state.followingIds) == desired }
                }
                _optimisticSaves.update { current ->
                    current.filterNot { (postId, desired) -> (postId in state.savedPostIds) == desired }
                }
            }
        }

        viewModelScope.launch {
            activeAuthUid.flatMapLatest { uid ->
                if (uid == null) emptyFlow()
                else repository.observeActivities(uid).catch { /* keep cache */ }
            }.collect { list ->
                val appContext = getApplication<Application>()
                if (!activitiesBaselineInitialized) {
                    activitiesBaselineInitialized = true
                    list.forEach { if (it.id.isNotBlank()) notifiedActivityIds.add(it.id) }
                } else {
                    val newlyArrived = list.filter { item ->
                        item.id.isNotBlank() &&
                            item.id !in notifiedActivityIds &&
                            item.actorId != currentUserId &&
                            item.actorId !in _privateState.value.blockedUserIds &&
                            item.actorId !in _privateState.value.mutedUserIds
                    }
                    newlyArrived.forEach { item ->
                        notifiedActivityIds.add(item.id)
                        OrbitNotificationHelper.showActivityNotification(appContext, item)
                    }
                }
                _activities.value = list
                if (_selectedDestination.value == MainDestination.ACTIVITY && _overlayStack.value.isEmpty()) {
                    markActivitiesRead(list)
                }
            }
        }

        viewModelScope.launch {
            activeAuthUid.flatMapLatest { uid ->
                if (uid == null) emptyFlow()
                else repository.observeLatestAppRelease().catch { emit(null) }
            }.collect { release ->
                _latestAppRelease.value = release
                val existingUrl = release?.driveDownloadUrl?.trim().orEmpty()
                if (!OrbitMediaFiles.isReleaseUrlCurrentOrNewer(release) ||
                    !OrbitMediaFiles.verifyDirectApkDownloadUrl(existingUrl)
                ) {
                    ensureDirectApkDownloadLinkSynced(release)
                }
            }
        }

        viewModelScope.launch {
            activeAuthUid.flatMapLatest { uid ->
                if (uid == null) emptyFlow()
                else spacesRepository.observeCommunities().catch { /* keep cache */ }
            }.collect { list ->
                _communities.value = list
            }
        }

        viewModelScope.launch {
            activeAuthUid.flatMapLatest { uid ->
                if (uid == null) emptyFlow()
                else spacesRepository.observeConversations(uid).catch { /* keep cache */ }
            }.collect { list ->
                val appContext = getApplication<Application>()
                if (!conversationsBaselineInitialized) {
                    conversationsBaselineInitialized = true
                    list.forEach { conv ->
                        if (conv.id.isNotBlank()) {
                            lastNotifiedDmMillis[conv.id] = conv.lastMessageAtMillis
                        }
                    }
                } else {
                    list.forEach { conv ->
                        if (conv.id.isNotBlank() &&
                            conv.lastMessageSenderId.isNotBlank() &&
                            conv.lastMessageSenderId != currentUserId &&
                            conv.lastMessageSenderId !in _privateState.value.blockedUserIds &&
                            conv.lastMessageSenderId !in _privateState.value.mutedUserIds
                        ) {
                            val prevMs = lastNotifiedDmMillis[conv.id] ?: 0L
                            val currMs = conv.lastMessageAtMillis
                            val viewingThisChat = (currentOverlay.value as? SpaceOverlay.ChatConversation)?.conversationId == conv.id
                            if (currMs > prevMs) {
                                lastNotifiedDmMillis[conv.id] = currMs
                                if (!viewingThisChat) {
                                    OrbitNotificationHelper.showDirectMessageNotification(appContext, conv, currentUserId)
                                }
                            }
                        }
                    }
                }
                _conversations.value = list
            }
        }
    }

    fun prepareForSignOut() {
        _signedOutFlag.value = true
        _activeCommunityId.value = null
        _activeCommentPostId.value = null
        _activeConversationId.value = null
        _overlayStack.value = emptyList()
        _optimisticLikes.value = emptyMap()
        _optimisticReposts.value = emptyMap()
        _optimisticFollows.value = emptyMap()
        _optimisticSaves.value = emptyMap()
        _optimisticPollVotes.value = emptyMap()
        _optimisticProfileBadges.value = null
        _activeFeedSource.value = FeedDataSource.NONE
        feedCache.clearMemoryLayerOnly()
        viewModelScope.launch {
            feedCache.clearAll()
        }
    }

    fun clearOfflineFeedCache() {
        viewModelScope.launch {
            feedCache.clearAll()
            _toastEvents.tryEmit("Offline feed cache cleared")
        }
    }

    fun resumeIfReauthenticated() {
        if (_signedOutFlag.value && Firebase.auth.currentUser?.uid == currentUserId) {
            _signedOutFlag.value = false
            _feedState.value = UiState.Loading
            _refreshSignal.update { it + 1 }
        }
    }

    fun markActivitiesRead(list: List<ActivityItem> = _activities.value) {
        val latest = maxOf(list.maxOfOrNull { it.createdAtMillis } ?: 0L, System.currentTimeMillis())
        viewModelScope.launch {
            themePrefs.setLastSeenActivityMillis(latest)
        }
    }

    fun markMessagesRead() {
        val latest = maxOf(_conversations.value.maxOfOrNull { it.lastMessageAtMillis } ?: 0L, System.currentTimeMillis())
        viewModelScope.launch {
            themePrefs.setLastSeenMessagesMillis(latest)
        }
    }

    // ==================== NAVIGATION & OVERLAYS ====================

    fun selectDestination(destination: MainDestination) {
        _overlayStack.value = emptyList()
        _activeCommunityId.value = null
        _activeConversationId.value = null
        _selectedDestination.value = destination
        if (destination == MainDestination.HOME) {
            _homeScrollToTopEvents.tryEmit(Unit)
        }
        if (destination == MainDestination.ACTIVITY) {
            markActivitiesRead()
        }
    }

    fun onOrbitLogoClick() {
        val alreadyHome = _selectedDestination.value == MainDestination.HOME && _overlayStack.value.isEmpty()
        if (alreadyHome) {
            _homeScrollToTopEvents.tryEmit(Unit)
            refreshFeed()
        } else {
            selectDestination(MainDestination.HOME)
        }
    }

    fun selectFeedTab(tab: FeedTab) {
        _selectedFeedTab.value = tab
    }

    fun openThread(postId: String) {
        _overlayStack.update { stack ->
            val base = if (stack.lastOrNull() is SpaceOverlay.ThreadDetail) stack.dropLast(1) else stack
            base + SpaceOverlay.ThreadDetail(postId)
        }
    }

    fun openUserProfile(userId: String) {
        if (userId == currentUserId) {
            selectDestination(MainDestination.PROFILE)
        } else {
            _overlayStack.update { stack ->
                val base = if (stack.lastOrNull() is SpaceOverlay.UserProfileDetail) stack.dropLast(1) else stack
                base + SpaceOverlay.UserProfileDetail(userId)
            }
        }
    }

    fun openCommunitiesHub(replaceRoot: Boolean = false) {
        _activeCommunityId.value = null
        _activeCommentPostId.value = null
        _activeConversationId.value = null
        _overlayStack.update { stack ->
            when {
                replaceRoot -> listOf(SpaceOverlay.CommunitiesHub)
                stack.lastOrNull() == SpaceOverlay.CommunitiesHub -> stack
                else -> stack + SpaceOverlay.CommunitiesHub
            }
        }
    }

    fun openCommunityDetail(communityId: String) {
        _activeCommunityId.value = communityId
        if (_communities.value.none { it.id == communityId }) {
            resolveCommunityById(communityId)
        }
        _overlayStack.update { stack ->
            val base = if (stack.lastOrNull() is SpaceOverlay.CommunityDetail) stack.dropLast(1) else stack
            base + SpaceOverlay.CommunityDetail(communityId)
        }
    }

    fun resolveCommunityById(communityId: String) {
        if (communityId.isBlank()) return
        viewModelScope.launch {
            spacesRepository.getCommunityById(communityId)
                .onSuccess { fetched ->
                    _resolvedCommunityCache.update { it + (communityId to fetched) }
                }
                .onFailure {
                    _resolvedCommunityCache.update { it + (communityId to null) }
                }
        }
    }

    fun openMessagesInbox(replaceRoot: Boolean = false) {
        _activeCommunityId.value = null
        _activeCommentPostId.value = null
        _activeConversationId.value = null
        markMessagesRead()
        _overlayStack.update { stack ->
            when {
                replaceRoot -> listOf(SpaceOverlay.MessagesInbox)
                stack.lastOrNull() == SpaceOverlay.MessagesInbox -> stack
                else -> stack + SpaceOverlay.MessagesInbox
            }
        }
    }

    fun openDirectChat(conversationId: String, peerProfile: UserProfile? = null) {
        _activeConversationId.value = conversationId
        markMessagesRead()
        markConversationRead(conversationId)
        _overlayStack.update { stack ->
            val base = if (stack.lastOrNull() is SpaceOverlay.ChatConversation) stack.dropLast(1) else stack
            base + SpaceOverlay.ChatConversation(conversationId, peerProfile)
        }
    }

    fun openAppSettings() {
        _overlayStack.update { stack ->
            if (stack.lastOrNull() == SpaceOverlay.AppSettings) stack
            else stack + SpaceOverlay.AppSettings
        }
    }

    fun openSavedPosts() {
        _overlayStack.update { stack ->
            if (stack.lastOrNull() == SpaceOverlay.SavedPosts) stack
            else stack + SpaceOverlay.SavedPosts
        }
    }

    fun setAlbumsOwnerId(userId: String) {
        if (userId.isNotBlank() && _albumsOwnerId.value != userId) {
            _albumsOwnerId.value = userId
        }
    }

    fun openAlbumDetail(userId: String, albumId: String) {
        _albumsOwnerId.value = userId
        _activeAlbumKey.value = userId to albumId
        _overlayStack.update { stack ->
            val base = if (stack.lastOrNull() is SpaceOverlay.AlbumDetail) stack.dropLast(1) else stack
            base + SpaceOverlay.AlbumDetail(userId, albumId)
        }
    }

    fun handleNotificationDeepLink(
        navType: String?,
        postId: String?,
        spaceId: String?,
        conversationId: String?
    ) {
        when (navType) {
            OrbitNotificationHelper.NAV_TYPE_POST -> {
                if (!postId.isNullOrBlank()) openThread(postId) else selectDestination(MainDestination.ACTIVITY)
            }
            OrbitNotificationHelper.NAV_TYPE_SPACE -> {
                if (!spaceId.isNullOrBlank()) openCommunityDetail(spaceId) else openCommunitiesHub()
            }
            OrbitNotificationHelper.NAV_TYPE_DM -> {
                if (!conversationId.isNullOrBlank()) openDirectChat(conversationId) else openMessagesInbox()
            }
            OrbitNotificationHelper.NAV_TYPE_SETTINGS_UPDATE -> {
                openAppSettings()
            }
            OrbitNotificationHelper.NAV_TYPE_ACTIVITY -> {
                selectDestination(MainDestination.ACTIVITY)
            }
        }
    }

    fun startOrOpenDirectChatWith(peer: UserProfile) {
        viewModelScope.launch {
            spacesRepository.ensureConversation(myProfile.value, peer)
                .onSuccess { convId ->
                    openDirectChat(convId, peer)
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Couldn’t start conversation."))
                }
        }
    }

    fun startOrOpenGroupChatWith(peers: List<UserProfile>, groupTitle: String = "") {
        val validPeers = peers
            .filter { it.resolvedId.isNotBlank() && it.resolvedId != currentUserId }
            .distinctBy { it.resolvedId }
        if (validPeers.isEmpty()) return
        if (validPeers.size == 1) {
            startOrOpenDirectChatWith(validPeers.first())
            return
        }
        viewModelScope.launch {
            spacesRepository.ensureGroupConversation(myProfile.value, validPeers, groupTitle)
                .onSuccess { convId ->
                    openDirectChat(convId, null)
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Couldn’t start group conversation."))
                }
        }
    }

    fun popOverlay() {
        _overlayStack.update { stack ->
            val popped = stack.dropLast(1)
            val top = popped.lastOrNull()
            if (top !is SpaceOverlay.CommunityDetail) {
                _activeCommunityId.value = null
                _activeCommentPostId.value = null
            } else {
                _activeCommunityId.value = top.communityId
            }
            if (top !is SpaceOverlay.ChatConversation) {
                _activeConversationId.value = null
            } else {
                _activeConversationId.value = top.conversationId
            }
            popped
        }
    }

    fun selectCommentPost(postId: String?) {
        _activeCommentPostId.value = postId
    }

    fun openComposer(replyTo: ThreadPost? = null, quotePost: ThreadPost? = null) {
        _replyTargetPost.value = replyTo
        _quoteTargetPost.value = quotePost
        _showComposerSheet.value = true
    }

    fun closeComposer() {
        _showComposerSheet.value = false
        _replyTargetPost.value = null
        _quoteTargetPost.value = null
    }

    fun openRepostMenu(post: ThreadPost) {
        _repostMenuPost.value = post
    }

    fun closeRepostMenu() {
        _repostMenuPost.value = null
    }

    fun openEditPost(post: ThreadPost) {
        _editingPost.value = post
    }

    fun closeEditPost() {
        _editingPost.value = null
    }

    fun openThemeSheet() {
        _showThemeSheet.value = true
    }

    fun closeThemeSheet() {
        _showThemeSheet.value = false
    }

    fun loadMorePosts() {
        val current = _pageLimit.value
        if (current < 300L) {
            _pageLimit.value = (current + 40L).coerceAtMost(300L)
        }
    }

    fun refreshFeed() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val startedAtMs = System.currentTimeMillis()
            if (_feedState.value is UiState.Error) {
                _feedState.value = UiState.Loading
            }
            var succeeded = false
            try {
                supervisorScope {
                    val postsDeferred = async { repository.fetchFreshPublicPosts(_pageLimit.value) }
                    val profilesDeferred = async { repository.fetchFreshPublicProfiles() }

                    val postsResult = postsDeferred.await()
                    val profilesResult = profilesDeferred.await()

                    profilesResult.onSuccess { freshProfiles ->
                        _profiles.value = freshProfiles
                    }

                    postsResult
                        .onSuccess { freshPosts ->
                            _feedState.value = UiState.Success(freshPosts)
                            _activeFeedSource.value = if (connectivityMonitor.isCurrentlyOnline()) {
                                FeedDataSource.NETWORK_L3
                            } else {
                                FeedDataSource.DATASTORE_L2
                            }
                            if (freshPosts.isNotEmpty()) {
                                launch { feedCache.saveFeedSnapshot(currentUserId, freshPosts) }
                            }
                            _refreshSignal.update { it + 1 }
                            succeeded = true
                        }
                        .onFailure { err ->
                            val msg = friendlyMessage(err, "Couldn’t refresh threads right now.")
                            if (_feedState.value !is UiState.Success) {
                                val fallback = feedCache.loadCachedFeed(currentUserId)
                                if (fallback != null && fallback.posts.isNotEmpty()) {
                                    _feedState.value = UiState.Success(fallback.posts)
                                    _activeFeedSource.value = fallback.source
                                    _toastEvents.tryEmit("Offline: showing cached threads")
                                } else {
                                    _feedState.value = UiState.Error(msg)
                                }
                            } else {
                                _toastEvents.tryEmit(msg)
                            }
                        }
                }
            } finally {
                val elapsed = System.currentTimeMillis() - startedAtMs
                if (elapsed < 320L) {
                    delay(320L - elapsed)
                }
                _isRefreshing.value = false
                _refreshCompletedEvents.tryEmit(succeeded)
            }
        }
    }

    // ==================== PRIVACY & VISIBILITY HELPERS ====================

    fun isPostVisibleToCurrentUser(post: ThreadPost): Boolean {
        if (post.authorId == currentUserId) return true
        val blocked = _privateState.value.blockedUserIds
        val muted = _privateState.value.mutedUserIds
        val words = _privateState.value.mutedWords
        if (post.authorId in blocked || post.authorId in muted) return false
        if (words.isNotEmpty()) {
            val haystack = "${post.body} ${post.topic} ${post.quotedBody}".lowercase()
            if (words.any { word -> word.isNotBlank() && haystack.contains(word) }) return false
        }
        val authorProfile = _profiles.value.firstOrNull { it.resolvedId == post.authorId }
        val isFollowingAuthor = post.authorId in followingIds.value
        if (authorProfile?.privateAccount == true && !isFollowingAuthor) return false
        if (post.audienceMode == "followers_only" && !isFollowingAuthor) return false
        return true
    }

    fun canCurrentUserReply(post: ThreadPost): Boolean {
        if (post.authorId == currentUserId) return true
        if (post.authorId in _privateState.value.blockedUserIds) return false
        return when (post.replyMode) {
            "followers_only" -> post.authorId in followingIds.value
            "selected_users" -> currentUserId in post.allowedReplyUserIds
            else -> true
        }
    }

    // ==================== THREADS ACTIONS ====================

    fun publishThread(
        body: String,
        imageUris: List<Uri>,
        gifUrls: List<String> = emptyList(),
        attachmentUris: List<Uri> = emptyList(),
        parentPost: ThreadPost? = null,
        quotedPost: ThreadPost? = null,
        audienceMode: String = "everyone",
        replyMode: String = "everyone",
        allowedReplyUserIds: List<String> = emptyList(),
        pollOptions: List<String> = emptyList(),
        pollDurationHours: Int = 24,
        onSuccess: () -> Unit = {}
    ) {
        if (_isPosting.value) return
        if (parentPost != null && !canCurrentUserReply(parentPost)) {
            _toastEvents.tryEmit("Replies are restricted on this thread.")
            return
        }
        viewModelScope.launch {
            _isPosting.value = true
            val context = getApplication<Application>()
            val encodedImages = imageUris.take(4).mapNotNull { uri ->
                OrbitCacheManager.encodeUriToDataUri(context, uri, isAvatar = false)
            }
            val combinedMedia = (encodedImages + gifUrls.map { it.trim() }.filter { it.isNotBlank() }).take(4)
            val encodedAttachments = mutableListOf<PostAttachment>()
            for (uri in attachmentUris.take(3)) {
                val res = OrbitMediaFiles.encodeUriToAttachment(context, uri)
                res.onSuccess { encodedAttachments.add(it) }
                    .onFailure { err ->
                        _isPosting.value = false
                        _toastEvents.tryEmit(err.message ?: "Could not attach selected file.")
                        return@launch
                    }
            }
            val mentionedUserIds = resolveMentionedUserIds(
                text = body,
                allProfiles = _profiles.value,
                excludeUserIds = blockedUserIds.value + currentUserId
            )
            val result = repository.createPost(
                author = myProfile.value,
                body = body,
                topic = "",
                imageUrls = combinedMedia,
                attachments = encodedAttachments,
                parentPost = parentPost,
                quotedPost = quotedPost,
                audienceMode = audienceMode,
                replyMode = replyMode,
                allowedReplyUserIds = allowedReplyUserIds,
                pollOptions = pollOptions,
                pollDurationHours = pollDurationHours,
                mentionedUserIds = mentionedUserIds
            )
            _isPosting.value = false
            result.onSuccess {
                closeComposer()
                val msg = when {
                    quotedPost != null -> "Quote repost published"
                    parentPost != null -> "Reply posted"
                    pollOptions.count { it.trim().isNotEmpty() } >= 2 -> "Poll published"
                    else -> "Posted"
                }
                _toastEvents.tryEmit(msg)
                onSuccess()
            }.onFailure { err ->
                _toastEvents.tryEmit(friendlyMessage(err, "Could not publish post."))
            }
        }
    }

    fun voteOnPoll(post: ThreadPost, optionIndex: Int) {
        if (!post.hasPoll || post.isPollEnded() || optionIndex !in post.pollOptions.indices) return
        val currentEffective = _optimisticPollVotes.value[post.id]
            ?: post.pollVotes[currentUserId]?.toInt()
        if (currentEffective == optionIndex) return
        val previousOptimistic = _optimisticPollVotes.value[post.id]
        _optimisticPollVotes.update { it + (post.id to optionIndex) }
        viewModelScope.launch {
            repository.voteOnPoll(post.id, optionIndex)
                .onFailure { err ->
                    _optimisticPollVotes.update { map ->
                        if (previousOptimistic != null) map + (post.id to previousOptimistic)
                        else map - post.id
                    }
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not record your vote."))
                }
        }
    }

    fun saveEditedThread(post: ThreadPost, newBody: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.editPost(post, newBody)
                .onSuccess {
                    feedCache.upsertPost(currentUserId, post.copy(body = newBody.trim(), edited = true))
                    closeEditPost()
                    _toastEvents.tryEmit("Post updated")
                    onDone()
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not edit post."))
                }
        }
    }

    fun deleteThread(post: ThreadPost) {
        viewModelScope.launch {
            repository.deletePost(post)
                .onSuccess {
                    feedCache.removePost(currentUserId, post.id)
                    if (currentOverlay.value == SpaceOverlay.ThreadDetail(post.id)) {
                        popOverlay()
                    }
                    _toastEvents.tryEmit("Thread deleted")
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not delete thread."))
                }
        }
    }

    fun deleteAllMyPosts(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAllMyPosts()
                .onSuccess { count ->
                    feedCache.clearAll()
                    _toastEvents.tryEmit(
                        if (count == 0) "You have no posts to delete."
                        else "Deleted $count post${if (count == 1) "" else "s"}."
                    )
                    onComplete()
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not delete posts."))
                }
        }
    }

    fun deleteMyAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            prepareForSignOut()
            repository.deleteMyAccount()
                .onSuccess {
                    _toastEvents.tryEmit("Account deleted")
                    onDeleted()
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not complete account deletion."))
                    onDeleted()
                }
        }
    }

    fun toggleLike(post: ThreadPost) {
        val currentlyLiked = post.id in likedPostIds.value
        _optimisticLikes.update { it + (post.id to !currentlyLiked) }
        viewModelScope.launch {
            repository.toggleLike(post, myProfile.value, currentlyLiked)
                .onFailure {
                    _optimisticLikes.update { it - post.id }
                    _toastEvents.tryEmit("Could not update like")
                }
        }
    }

    fun toggleRepost(post: ThreadPost) {
        val currentlyReposted = post.id in repostedPostIds.value
        _optimisticReposts.update { it + (post.id to !currentlyReposted) }
        viewModelScope.launch {
            repository.toggleRepost(post, myProfile.value, currentlyReposted)
                .onSuccess { nowReposted ->
                    _toastEvents.tryEmit(if (nowReposted) "Reposted" else "Removed repost")
                }
                .onFailure {
                    _optimisticReposts.update { it - post.id }
                    _toastEvents.tryEmit("Could not update repost")
                }
        }
    }

    fun toggleFollow(targetUserId: String) {
        if (targetUserId.isBlank() || targetUserId == currentUserId) return
        val currentlyFollowing = targetUserId in followingIds.value
        _optimisticFollows.update { it + (targetUserId to !currentlyFollowing) }
        viewModelScope.launch {
            repository.toggleFollow(targetUserId, myProfile.value, currentlyFollowing)
                .onFailure {
                    _optimisticFollows.update { it - targetUserId }
                    _toastEvents.tryEmit("Could not update follow")
                }
        }
    }

    fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        linkUrl: String,
        extraLinks: List<String>,
        croppedAvatarDataUri: String?,
        croppedBannerDataUri: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val resolvedAvatar = croppedAvatarDataUri ?: myProfile.value.avatarUrl
            val resolvedBanner = croppedBannerDataUri ?: myProfile.value.bannerUrl
            repository.updateProfile(
                displayName = displayName,
                username = username,
                bio = bio,
                linkUrl = linkUrl,
                extraLinks = extraLinks,
                avatarUrl = resolvedAvatar,
                bannerUrl = resolvedBanner
            ).onSuccess {
                _toastEvents.tryEmit("Profile updated")
                onDone()
            }.onFailure { err ->
                _toastEvents.tryEmit(friendlyMessage(err, "Failed to update profile."))
            }
        }
    }

    fun addProfileLink(title: String, url: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addProfileLink(myProfile.value.savedLinks, title, url)
                .onSuccess {
                    _toastEvents.tryEmit("Link saved to profile")
                    onDone()
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not save link."))
                }
        }
    }

    fun removeProfileLink(linkId: String) {
        viewModelScope.launch {
            repository.removeProfileLink(myProfile.value.savedLinks, linkId)
                .onSuccess { _toastEvents.tryEmit("Link removed") }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not remove link."))
                }
        }
    }

    fun toggleSavePost(post: ThreadPost) {
        val currentlySaved = post.id in savedPostIds.value
        _optimisticSaves.update { it + (post.id to !currentlySaved) }
        viewModelScope.launch {
            repository.toggleSavedPost(post.id, currentlySaved)
                .onSuccess { nowSaved ->
                    _toastEvents.tryEmit(if (nowSaved) "Saved to Docking Station" else "Removed from Saved Posts")
                }
                .onFailure {
                    _optimisticSaves.update { it - post.id }
                    _toastEvents.tryEmit("Could not update saved post")
                }
        }
    }

    fun setPrivateAccount(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPrivateAccount(enabled)
                .onSuccess {
                    _toastEvents.tryEmit(if (enabled) "Account set to Private" else "Account set to Public")
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not update privacy setting."))
                }
        }
    }

    fun setProfileBadgeEnabled(badge: OrbitProfileBadge, enabled: Boolean) {
        val current = OrbitProfileBadge.normalizeIds(myProfile.value.profileBadges).toMutableSet()
        if (enabled) {
            current.add(badge.id)
        } else {
            current.remove(badge.id)
        }
        val updated = OrbitProfileBadge.normalizeIds(current)
        _optimisticProfileBadges.value = updated
        viewModelScope.launch {
            repository.updateProfileBadges(updated)
                .onSuccess {
                    // Keep optimistic value briefly until Firestore snapshot catches up, then clear
                    delay(450L)
                    if (_optimisticProfileBadges.value == updated) {
                        _optimisticProfileBadges.value = null
                    }
                }
                .onFailure { err ->
                    _optimisticProfileBadges.value = null
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not update ${badge.label} badge."))
                }
        }
    }

    fun toggleBlockUser(targetUserId: String) {
        if (targetUserId.isBlank() || targetUserId == currentUserId) return
        val currentlyBlocked = targetUserId in blockedUserIds.value
        viewModelScope.launch {
            repository.toggleBlockUser(targetUserId, currentlyBlocked)
                .onSuccess { nowBlocked ->
                    _toastEvents.tryEmit(if (nowBlocked) "User blocked" else "User unblocked")
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not update block list."))
                }
        }
    }

    fun toggleMuteUser(targetUserId: String) {
        if (targetUserId.isBlank() || targetUserId == currentUserId) return
        val currentlyMuted = targetUserId in mutedUserIds.value
        val handle = _profiles.value.firstOrNull { it.resolvedId == targetUserId }?.username
            ?.takeIf { it.isNotBlank() }
            ?.let { "@$it" }
            ?: "user"
        viewModelScope.launch {
            repository.toggleMuteUser(targetUserId, currentlyMuted)
                .onSuccess { nowMuted ->
                    _toastEvents.tryEmit(
                        if (nowMuted) {
                            "Muted $handle — they won't be notified and their posts are hidden."
                        } else {
                            "Unmuted $handle"
                        }
                    )
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not update muted list."))
                }
        }
    }

    fun addMutedWord(word: String) {
        val cleaned = word.trim().lowercase()
        if (cleaned.isBlank()) return
        val updated = (_privateState.value.mutedWords + cleaned).distinct()
        viewModelScope.launch {
            repository.updateMutedWords(updated)
                .onSuccess { _toastEvents.tryEmit("Muted \"$cleaned\"") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not mute word.")) }
        }
    }

    fun removeMutedWord(word: String) {
        val updated = _privateState.value.mutedWords.filterNot { it.equals(word, ignoreCase = true) }
        viewModelScope.launch {
            repository.updateMutedWords(updated)
                .onSuccess { _toastEvents.tryEmit("Removed muted word") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not remove muted word.")) }
        }
    }

    fun togglePinProfileLink(linkId: String) {
        val current = myProfile.value.savedLinks
        val target = current.firstOrNull { it.id == linkId } ?: return
        val toggled = current.map { link ->
            if (link.id == linkId) link.copy(pinned = !link.pinned) else link
        }
        val sorted = toggled.filter { it.pinned } + toggled.filterNot { it.pinned }
        viewModelScope.launch {
            repository.updateProfileLinks(sorted)
                .onSuccess {
                    _toastEvents.tryEmit(if (!target.pinned) "Link pinned to top" else "Link unpinned")
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not update link pin."))
                }
        }
    }

    fun reorderProfileLinks(reorderedLinks: List<ProfileLink>) {
        viewModelScope.launch {
            repository.updateProfileLinks(reorderedLinks)
                .onSuccess { _toastEvents.tryEmit("Link order saved") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not save link order.")) }
        }
    }

    // ==================== PROFILE MEDIA ALBUMS ACTIONS ====================

    fun createAlbum(title: String, initialUris: List<Uri> = emptyList(), onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.createAlbum(myProfile.value, title)
                .onSuccess { albumId ->
                    if (initialUris.isNotEmpty()) {
                        addMediaToAlbum(albumId = albumId, existingCount = 0, existingCoverUrl = "", uris = initialUris)
                    } else {
                        _toastEvents.tryEmit("Album \"$title\" created")
                    }
                    onCreated(albumId)
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not create album."))
                }
        }
    }

    fun addMediaToAlbum(
        albumId: String,
        existingCount: Int,
        existingCoverUrl: String,
        uris: List<Uri>,
        onDone: () -> Unit = {}
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            val prepared = mutableListOf<Triple<String, String, String>>()
            for (uri in uris.take(12)) {
                val mime = OrbitMediaFiles.queryMimeType(context, uri).ifBlank { "image/jpeg" }
                val name = OrbitMediaFiles.queryDisplayName(context, uri)
                if (mime.startsWith("video/", ignoreCase = true)) {
                    OrbitMediaFiles.encodeUriToAttachment(context, uri)
                        .onSuccess { att ->
                            prepared.add(Triple(att.dataUri, att.mimeType, att.fileName))
                        }
                        .onFailure { err ->
                            _toastEvents.tryEmit(err.message ?: "Video exceeds the 500 MB limit.")
                        }
                } else {
                    val dataUri = OrbitCacheManager.encodeUriToDataUri(context, uri, isAvatar = false)
                    if (!dataUri.isNullOrBlank()) {
                        prepared.add(Triple(dataUri, "image/jpeg", name))
                    }
                }
            }
            if (prepared.isEmpty()) {
                _toastEvents.tryEmit("No compatible photos or videos selected.")
                return@launch
            }
            repository.addItemsToAlbum(albumId, existingCount, existingCoverUrl, prepared)
                .onSuccess { added ->
                    _toastEvents.tryEmit("Added $added item${if (added == 1) "" else "s"} to album")
                    onDone()
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not add items to album."))
                }
        }
    }

    fun removeItemsFromAlbum(album: ProfileAlbum, itemIds: Set<String>, currentItems: List<AlbumMediaItem>) {
        if (itemIds.isEmpty()) return
        viewModelScope.launch {
            val remaining = currentItems.filterNot { it.id in itemIds }
            repository.removeAlbumItems(album.id, itemIds, remaining, album.coverUrl)
                .onSuccess { count ->
                    _toastEvents.tryEmit("Removed $count item${if (count == 1) "" else "s"}")
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not remove album items."))
                }
        }
    }

    fun setAlbumCover(albumId: String, coverUrl: String) {
        viewModelScope.launch {
            repository.setAlbumCover(albumId, coverUrl)
                .onSuccess { _toastEvents.tryEmit("Album cover updated") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not set cover photo.")) }
        }
    }

    fun reorderAlbumItems(albumId: String, orderedItems: List<AlbumMediaItem>) {
        viewModelScope.launch {
            repository.reorderAlbumItems(albumId, orderedItems)
                .onSuccess { _toastEvents.tryEmit("Album order saved") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not save album order.")) }
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch {
            repository.deleteAlbum(albumId)
                .onSuccess {
                    if ((currentOverlay.value as? SpaceOverlay.AlbumDetail)?.albumId == albumId) {
                        popOverlay()
                    }
                    _toastEvents.tryEmit("Album deleted")
                }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not delete album.")) }
        }
    }

    fun shareAlbumToFeed(album: ProfileAlbum, items: List<AlbumMediaItem>) {
        viewModelScope.launch {
            val previewImages = items.filterNot { it.isVideo }.map { it.mediaUrl }.take(4)
            val body = "Shared album \"${album.title}\" (${items.size} item${if (items.size == 1) "" else "s"})"
            repository.createPost(
                author = myProfile.value,
                body = body,
                imageUrls = previewImages
            ).onSuccess {
                _toastEvents.tryEmit("Album shared to main feed!")
            }.onFailure { err ->
                _toastEvents.tryEmit(friendlyMessage(err, "Could not share album to feed."))
            }
        }
    }

    fun shareAlbumToUser(album: ProfileAlbum, items: List<AlbumMediaItem>, recipient: UserProfile) {
        viewModelScope.launch {
            val previewImage = items.firstOrNull { !it.isVideo }?.mediaUrl.orEmpty().ifBlank { album.coverUrl }
            val body = "Check out my album \"${album.title}\" (${items.size} item${if (items.size == 1) "" else "s"})"
            spacesRepository.ensureConversation(myProfile.value, recipient)
                .onSuccess { convId ->
                    spacesRepository.sendDirectMessage(
                        conversationId = convId,
                        me = myProfile.value,
                        peer = recipient,
                        body = body,
                        imageUrl = previewImage
                    ).onSuccess {
                        _toastEvents.tryEmit("Shared album with @${recipient.username}")
                    }.onFailure { err ->
                        _toastEvents.tryEmit(friendlyMessage(err, "Could not send album DM."))
                    }
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not open DM with user."))
                }
        }
    }

    // ==================== APP UPDATES & GOOGLE DRIVE APK ====================

    fun checkForAppUpdate() {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            try {
                repository.fetchLatestAppRelease()
                    .onSuccess { release ->
                        _latestAppRelease.value = release
                        if (release != null && release.versionCode > BuildConfig.VERSION_CODE.toLong()) {
                            _toastEvents.tryEmit(
                                "Orbit v${release.versionName} is available!"
                            )
                        } else {
                            _toastEvents.tryEmit("You’re on the latest version (v${BuildConfig.VERSION_NAME}).")
                        }
                    }
                    .onFailure { err ->
                        _toastEvents.tryEmit(friendlyMessage(err, "Could not check for updates right now."))
                    }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    private var isProvisioningApkLink = false

    private fun ensureDirectApkDownloadLinkSynced(currentRelease: AppRelease?) {
        if (isProvisioningApkLink) return
        val context = getApplication<Application>()
        // Play Store App Bundle (AAB) installs use split APKs — never attempt to upload base.apk
        if (OrbitMediaFiles.inspectInstalledApkIntegrity(context).hasSplitApks) return
        viewModelScope.launch {
            isProvisioningApkLink = true
            try {
                OrbitMediaFiles.ensureHostedApkDownloadUrl(
                    context = context,
                    existingReleaseUrl = currentRelease?.driveDownloadUrl,
                    existingReleaseVersionCode = currentRelease?.versionCode
                ).onSuccess { hostedUrl ->
                    val currentVerCode = currentRelease?.versionCode ?: 0L
                    val isOutdatedRelease = currentVerCode < BuildConfig.VERSION_CODE.toLong()
                    if (OrbitMediaFiles.isValidApkDownloadUrl(hostedUrl) &&
                        (hostedUrl != currentRelease?.driveDownloadUrl?.trim() || isOutdatedRelease)
                    ) {
                        val targetVersionCode = maxOf(currentVerCode, BuildConfig.VERSION_CODE.toLong())
                        val targetVersionName = if (isOutdatedRelease) {
                            BuildConfig.VERSION_NAME
                        } else {
                            currentRelease?.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
                        }
                        repository.saveDriveDownloadUrl(
                            driveDownloadUrl = hostedUrl,
                            versionCode = targetVersionCode,
                            versionName = targetVersionName,
                            releaseNotes = currentRelease?.releaseNotes.orEmpty()
                        )
                    }
                }
            } finally {
                isProvisioningApkLink = false
            }
        }
    }

    fun saveDriveDownloadUrl(driveUrl: String) {
        if (!OrbitMediaFiles.isValidApkDownloadUrl(driveUrl)) return
        viewModelScope.launch {
            val current = _latestAppRelease.value
            val currentVerCode = current?.versionCode ?: 0L
            val isOutdated = currentVerCode < BuildConfig.VERSION_CODE.toLong()
            repository.saveDriveDownloadUrl(
                driveDownloadUrl = driveUrl,
                versionCode = maxOf(currentVerCode, BuildConfig.VERSION_CODE.toLong()),
                versionName = if (isOutdated) {
                    BuildConfig.VERSION_NAME
                } else {
                    current?.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
                },
                releaseNotes = current?.releaseNotes.orEmpty()
            )
        }
    }

    fun downloadApkFromDrive(
        driveUrl: String,
        onOpenInBrowserFallback: (String) -> Unit
    ) {
        if (_isDownloadingDriveApk.value) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            _isDownloadingDriveApk.value = true
            _driveDownloadProgress.value = 0.02f
            _driveDownloadStatus.value = "Verifying APK download link…"

            val currentRelease = _latestAppRelease.value
            val candidateUrl = driveUrl.trim().ifBlank {
                if (OrbitMediaFiles.isReleaseUrlCurrentOrNewer(currentRelease)) {
                    currentRelease?.driveDownloadUrl.orEmpty()
                } else {
                    ""
                }
            }
            val normalizedCandidate = OrbitMediaFiles.resolveOpenShareableUrl(candidateUrl)
            val candidateVerified = normalizedCandidate.isNotBlank() &&
                OrbitMediaFiles.verifyDirectApkDownloadUrl(normalizedCandidate)

            val resolvedUrl = if (candidateVerified) {
                normalizedCandidate
            } else {
                OrbitMediaFiles.ensureHostedApkDownloadUrl(
                    context = context,
                    existingReleaseUrl = currentRelease?.driveDownloadUrl,
                    existingReleaseVersionCode = currentRelease?.versionCode,
                    forceRefresh = normalizedCandidate.isNotBlank()
                ) { pct, status ->
                    _driveDownloadProgress.value = pct * 0.5f
                    _driveDownloadStatus.value = status
                }.onSuccess { freshUrl ->
                    saveDriveDownloadUrl(freshUrl)
                }.getOrElse { "" }
            }

            if (resolvedUrl.isBlank()) {
                _isDownloadingDriveApk.value = false
                _driveDownloadProgress.value = 0f
                _driveDownloadStatus.value = "Could not verify a working APK download link."
                _toastEvents.tryEmit("Could not verify a working APK download link right now.")
                return@launch
            }

            val versionLabel = if ((currentRelease?.versionCode ?: 0L) >= BuildConfig.VERSION_CODE.toLong()) {
                currentRelease?.versionName?.ifBlank { BuildConfig.VERSION_NAME } ?: BuildConfig.VERSION_NAME
            } else {
                BuildConfig.VERSION_NAME
            }
            val result = OrbitMediaFiles.downloadDriveApkWithProgress(
                context = context,
                driveUrl = resolvedUrl,
                versionName = versionLabel
            ) { pct, status ->
                _driveDownloadProgress.value = pct
                _driveDownloadStatus.value = status
            }
            _isDownloadingDriveApk.value = false
            result.onSuccess { msg ->
                _toastEvents.tryEmit(msg)
            }.onFailure { err ->
                if (err.message == "OPEN_IN_BROWSER") {
                    _driveDownloadProgress.value = 0f
                    _driveDownloadStatus.value = "Opening download link in browser…"
                    onOpenInBrowserFallback(resolvedUrl)
                } else {
                    _driveDownloadProgress.value = 0f
                    _driveDownloadStatus.value = err.message ?: "Download failed."
                    _toastEvents.tryEmit(err.message ?: "Could not download Orbit APK.")
                }
            }
        }
    }

    fun publishAppUpdateNotice(releaseNotes: String, driveDownloadUrl: String = "") {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val recipients = _profiles.value.map { it.resolvedId }
            val currentRelease = _latestAppRelease.value
            val candidateUrl = driveDownloadUrl.trim().ifBlank {
                if (OrbitMediaFiles.isReleaseUrlCurrentOrNewer(currentRelease)) {
                    currentRelease?.driveDownloadUrl.orEmpty()
                } else {
                    ""
                }
            }
            val normalizedCandidate = OrbitMediaFiles.resolveOpenShareableUrl(candidateUrl)
            val resolvedDriveUrl = if (normalizedCandidate.isNotBlank() &&
                OrbitMediaFiles.verifyDirectApkDownloadUrl(normalizedCandidate)
            ) {
                normalizedCandidate
            } else {
                OrbitMediaFiles.ensureHostedApkDownloadUrl(
                    context = context,
                    existingReleaseUrl = currentRelease?.driveDownloadUrl,
                    existingReleaseVersionCode = currentRelease?.versionCode,
                    forceRefresh = normalizedCandidate.isNotBlank()
                ).getOrDefault("")
            }
            repository.publishAppUpdateAnnouncement(
                actor = myProfile.value,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                versionName = BuildConfig.VERSION_NAME,
                releaseNotes = releaseNotes,
                driveDownloadUrl = resolvedDriveUrl,
                recipientUserIds = recipients
            ).onSuccess { count ->
                _toastEvents.tryEmit("Update v${BuildConfig.VERSION_NAME} announced to $count user${if (count == 1) "" else "s"}")
            }.onFailure { err ->
                _toastEvents.tryEmit(friendlyMessage(err, "Could not announce update."))
            }
        }
    }

    fun downloadPhoto(urlOrDataUri: String) {
        savePhotoToGallery(urlOrDataUri)
    }

    fun savePhotoToGallery(urlOrDataUri: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            OrbitMediaFiles.downloadPhotoToDevice(
                context = context,
                urlOrDataUri = urlOrDataUri,
                saveToDownloads = false
            )
                .onSuccess { msg -> _toastEvents.tryEmit(msg) }
                .onFailure { err -> _toastEvents.tryEmit(err.message ?: "Could not save photo.") }
        }
    }

    fun downloadPhotoToDownloads(urlOrDataUri: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            OrbitMediaFiles.downloadPhotoToDevice(
                context = context,
                urlOrDataUri = urlOrDataUri,
                saveToDownloads = true
            )
                .onSuccess { msg -> _toastEvents.tryEmit(msg) }
                .onFailure { err -> _toastEvents.tryEmit(err.message ?: "Download failed.") }
        }
    }

    private fun normalizePhotoBookmarkKey(urlOrDataUri: String): String {
        val stripped = OrbitCacheManager.stripHd2kFragment(urlOrDataUri).trim()
        return stripped.hashCode().toString() + ":" + stripped.length
    }

    private fun loadBookmarkedPhotoKeys(): Set<String> {
        return runCatching {
            val prefs = getApplication<Application>()
                .getSharedPreferences("orbit_photo_bookmarks_$currentUserId", android.content.Context.MODE_PRIVATE)
            prefs.getStringSet("keys", emptySet())?.toSet().orEmpty()
        }.getOrDefault(emptySet())
    }

    private fun persistBookmarkedPhotoKeys(keys: Set<String>) {
        runCatching {
            val prefs = getApplication<Application>()
                .getSharedPreferences("orbit_photo_bookmarks_$currentUserId", android.content.Context.MODE_PRIVATE)
            prefs.edit().putStringSet("keys", keys).apply()
        }
    }

    private fun findThreadPostForImage(urlOrDataUri: String): ThreadPost? {
        val targetStripped = OrbitCacheManager.stripHd2kFragment(urlOrDataUri).trim()
        if (targetStripped.isBlank()) return null
        val posts = (_feedState.value as? UiState.Success)?.data.orEmpty()
        return posts.firstOrNull { post ->
            post.imageUrls.any { candidate ->
                candidate == urlOrDataUri || OrbitCacheManager.stripHd2kFragment(candidate).trim() == targetStripped
            } || (post.quotedImageUrl.isNotBlank() && (
                post.quotedImageUrl == urlOrDataUri ||
                    OrbitCacheManager.stripHd2kFragment(post.quotedImageUrl).trim() == targetStripped
                ))
        }
    }

    fun isPhotoBookmarked(urlOrDataUri: String): Boolean {
        val matchingPost = findThreadPostForImage(urlOrDataUri)
        if (matchingPost != null) {
            return matchingPost.id in savedPostIds.value
        }
        return normalizePhotoBookmarkKey(urlOrDataUri) in _bookmarkedPhotoKeys.value
    }

    fun toggleBookmarkPhoto(urlOrDataUri: String) {
        val matchingPost = findThreadPostForImage(urlOrDataUri)
        if (matchingPost != null) {
            toggleSavePost(matchingPost)
            return
        }
        val key = normalizePhotoBookmarkKey(urlOrDataUri)
        val currentlyBookmarked = key in _bookmarkedPhotoKeys.value
        val updated = if (currentlyBookmarked) {
            _bookmarkedPhotoKeys.value - key
        } else {
            _bookmarkedPhotoKeys.value + key
        }
        _bookmarkedPhotoKeys.value = updated
        persistBookmarkedPhotoKeys(updated)
        _toastEvents.tryEmit(if (currentlyBookmarked) "Removed photo bookmark" else "Photo bookmarked")
    }

    fun downloadAttachment(attachment: PostAttachment) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            OrbitMediaFiles.downloadAttachmentToDevice(context, attachment)
                .onSuccess { msg -> _toastEvents.tryEmit(msg) }
                .onFailure { err -> _toastEvents.tryEmit(err.message ?: "Download failed.") }
        }
    }

    // ==================== ORBIT SPACES ACTIONS ====================

    fun createCommunity(
        name: String,
        description: String,
        category: String,
        coverDataUriOrRawUri: String?,
        coverUri: Uri?,
        accentIndex: Long,
        privacyMode: String = "public",
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val encodedCover = coverDataUriOrRawUri?.takeIf { it.isNotBlank() }
                ?: coverUri?.let {
                    OrbitCacheManager.encodeUriToDataUri(context, it, isAvatar = false)
                }.orEmpty()
            spacesRepository.createCommunity(
                owner = myProfile.value,
                name = name,
                description = description,
                category = category,
                coverUrl = encodedCover,
                accentIndex = accentIndex,
                privacyMode = privacyMode
            ).onSuccess { id ->
                _toastEvents.tryEmit("Space created")
                onCreated(id)
            }.onFailure { err ->
                _toastEvents.tryEmit(friendlyMessage(err, "Could not create Space."))
            }
        }
    }

    fun updateSpaceAdminSettings(
        spaceId: String,
        name: String,
        description: String,
        coverUrl: String,
        privacyMode: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            spacesRepository.updateSpaceAdminSettings(spaceId, name, description, coverUrl, privacyMode)
                .onSuccess {
                    _toastEvents.tryEmit("Space settings saved")
                    onDone()
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not save Space settings."))
                }
        }
    }

    fun deleteSpace(spaceId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            spacesRepository.deleteSpace(spaceId)
                .onSuccess {
                    _toastEvents.tryEmit("Space deleted")
                    popOverlay()
                    onDeleted()
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not delete Space."))
                }
        }
    }

    fun handleSpaceJoinAction(space: Community) {
        viewModelScope.launch {
            val uid = currentUserId
            when {
                space.isBanned(uid) -> _toastEvents.tryEmit("You are banned from this Space.")
                uid in space.memberIds -> {
                    spacesRepository.toggleJoinCommunity(space.id)
                        .onSuccess { _toastEvents.tryEmit("Left ${space.name}") }
                        .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not leave Space.")) }
                }
                space.isInvited(uid) -> {
                    spacesRepository.acceptSpaceInvite(space, myProfile.value)
                        .onSuccess { _toastEvents.tryEmit("Joined ${space.name}") }
                        .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not accept invite.")) }
                }
                space.isPrivateSpace -> {
                    if (space.hasPendingRequest(uid)) {
                        _toastEvents.tryEmit("Join request already sent")
                    } else {
                        spacesRepository.requestToJoinSpace(space, myProfile.value)
                            .onSuccess { _toastEvents.tryEmit("Join request sent to Admin & Mods") }
                            .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not request access.")) }
                    }
                }
                else -> {
                    spacesRepository.toggleJoinCommunity(space.id)
                        .onSuccess { joined ->
                            _toastEvents.tryEmit(if (joined) "Joined ${space.name}" else "Left ${space.name}")
                        }
                        .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not update membership.")) }
                }
            }
        }
    }

    fun acceptSpaceInviteById(spaceId: String) {
        val space = _communities.value.firstOrNull { it.id == spaceId } ?: return
        viewModelScope.launch {
            spacesRepository.acceptSpaceInvite(space, myProfile.value)
                .onSuccess {
                    _toastEvents.tryEmit("Joined ${space.name}")
                    openCommunityDetail(space.id)
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not accept Space invite."))
                }
        }
    }

    fun approveJoinRequest(space: Community, targetUid: String) {
        viewModelScope.launch {
            spacesRepository.approveJoinRequest(space, targetUid, myProfile.value)
                .onSuccess { _toastEvents.tryEmit("Member approved and notified") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not approve request.")) }
        }
    }

    fun declineJoinRequest(space: Community, targetUid: String) {
        viewModelScope.launch {
            spacesRepository.declineJoinRequest(space, targetUid)
                .onSuccess { _toastEvents.tryEmit("Join request declined") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not decline request.")) }
        }
    }

    fun inviteUserToSpace(space: Community, targetUid: String) {
        viewModelScope.launch {
            spacesRepository.inviteUserToSpace(space, targetUid, myProfile.value)
                .onSuccess { _toastEvents.tryEmit("Invite sent!") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not invite user.")) }
        }
    }

    fun toggleModerator(space: Community, targetUid: String) {
        viewModelScope.launch {
            spacesRepository.toggleModerator(space, targetUid)
                .onSuccess { nowMod ->
                    _toastEvents.tryEmit(if (nowMod) "Promoted to Moderator" else "Removed Moderator role")
                }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not update role.")) }
        }
    }

    fun muteSpaceMember(space: Community, targetUid: String, durationHours: Int) {
        viewModelScope.launch {
            spacesRepository.muteMember(space, targetUid, durationHours)
                .onSuccess {
                    val label = if (durationHours <= 0) "Posting pause lifted" else "Paused from posting for ${durationHours}h"
                    _toastEvents.tryEmit(label)
                }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not update pause.")) }
        }
    }

    fun kickSpaceMember(space: Community, targetUid: String) {
        viewModelScope.launch {
            spacesRepository.kickMember(space, targetUid)
                .onSuccess { _toastEvents.tryEmit("Member kicked from Space") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not kick member.")) }
        }
    }

    fun banSpaceMember(space: Community, targetUid: String) {
        viewModelScope.launch {
            spacesRepository.banMember(space, targetUid)
                .onSuccess { _toastEvents.tryEmit("Member banned from Space") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not ban member.")) }
        }
    }

    fun unbanSpaceMember(space: Community, targetUid: String) {
        viewModelScope.launch {
            spacesRepository.unbanMember(space, targetUid)
                .onSuccess { _toastEvents.tryEmit("User unbanned") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not unban user.")) }
        }
    }

    fun publishCommunityPost(
        space: Community,
        streamCategory: String,
        body: String,
        imageUris: List<Uri>,
        attachmentUris: List<Uri> = emptyList(),
        onSuccess: () -> Unit
    ) {
        if (_isPosting.value) return
        viewModelScope.launch {
            _isPosting.value = true
            val context = getApplication<Application>()
            val encodedImages = imageUris.take(4).mapNotNull { uri ->
                OrbitCacheManager.encodeUriToDataUri(context, uri, isAvatar = false)
            }
            val encodedAttachments = mutableListOf<PostAttachment>()
            for (uri in attachmentUris.take(3)) {
                val res = OrbitMediaFiles.encodeUriToAttachment(context, uri)
                res.onSuccess { encodedAttachments.add(it) }
                    .onFailure { err ->
                        _isPosting.value = false
                        _toastEvents.tryEmit(err.message ?: "Could not attach selected file.")
                        return@launch
                    }
            }
            val mentionedUserIds = resolveMentionedUserIds(
                text = body,
                allProfiles = _profiles.value,
                excludeUserIds = blockedUserIds.value + currentUserId
            )
            val result = spacesRepository.createCommunityPost(
                space = space,
                author = myProfile.value,
                streamCategory = streamCategory,
                body = body,
                imageUrls = encodedImages,
                attachments = encodedAttachments,
                mentionedUserIds = mentionedUserIds
            )
            _isPosting.value = false
            result.onSuccess {
                _toastEvents.tryEmit("Posted to ${space.name}")
                onSuccess()
            }.onFailure { err ->
                _toastEvents.tryEmit(friendlyMessage(err, "Could not publish post."))
            }
        }
    }

    fun editCommunityPost(post: CommunityPost, newBody: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            spacesRepository.editCommunityPost(post, newBody)
                .onSuccess {
                    _toastEvents.tryEmit("Space post updated")
                    onDone()
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not edit post."))
                }
        }
    }

    fun toggleCommunityPlusOne(postId: String) {
        viewModelScope.launch {
            spacesRepository.togglePlusOne(postId)
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not update +1."))
                }
        }
    }

    fun addCommunityComment(postId: String, body: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val spaceId = activeCommunityPosts.value.firstOrNull { it.id == postId }?.communityId.orEmpty()
            val mentionedUserIds = resolveMentionedUserIds(
                text = body,
                allProfiles = _profiles.value,
                excludeUserIds = blockedUserIds.value + currentUserId
            )
            spacesRepository.addCommunityComment(
                postId = postId,
                author = myProfile.value,
                body = body,
                spaceId = spaceId,
                mentionedUserIds = mentionedUserIds
            )
                .onSuccess { onDone() }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not add comment."))
                }
        }
    }

    fun deleteCommunityPost(post: CommunityPost, space: Community?) {
        viewModelScope.launch {
            spacesRepository.deleteCommunityPost(post, space)
                .onSuccess { _toastEvents.tryEmit("Post deleted") }
                .onFailure { err ->
                    _toastEvents.tryEmit(friendlyMessage(err, "Could not delete post."))
                }
        }
    }

    // ==================== TWITTER/X DIRECT MESSAGES ACTIONS ====================

    fun sendDirectMessage(
        conversationId: String,
        peerProfile: UserProfile?,
        body: String,
        imageUri: Uri?,
        gifUrl: String? = null,
        attachmentUri: Uri? = null,
        onSent: () -> Unit = {}
    ) {
        if (_isPosting.value) return
        viewModelScope.launch {
            _isPosting.value = true
            val context = getApplication<Application>()
            val encodedImage = imageUri?.let {
                OrbitCacheManager.encodeUriToDataUri(context, it, isAvatar = false)
            }.orEmpty()
            val resolvedImage = encodedImage.ifBlank { gifUrl?.trim().orEmpty() }
            val encodedAttachments = if (attachmentUri != null) {
                val res = OrbitMediaFiles.encodeUriToAttachment(context, attachmentUri)
                res.getOrElse { err ->
                    _isPosting.value = false
                    _toastEvents.tryEmit(err.message ?: "Could not attach file.")
                    return@launch
                }.let { listOf(it) }
            } else {
                emptyList()
            }
            val result = spacesRepository.sendDirectMessage(
                conversationId = conversationId,
                me = myProfile.value,
                peer = peerProfile,
                body = body,
                imageUrl = resolvedImage,
                attachments = encodedAttachments
            )
            _isPosting.value = false
            result.onSuccess {
                onSent()
            }.onFailure { err ->
                _toastEvents.tryEmit(friendlyMessage(err, "Could not send message."))
            }
        }
    }

    fun markConversationRead(conversationId: String) {
        if (conversationId.isBlank()) return
        if (!themeConfig.value.readReceiptsEnabled) return
        viewModelScope.launch {
            spacesRepository.markConversationRead(conversationId)
        }
    }

    fun toggleDirectMessageReaction(
        conversationId: String,
        message: DirectMessage,
        emoji: String
    ) {
        if (conversationId.isBlank() || message.id.isBlank()) return
        val myCurrent = message.myReaction(currentUserId)
        viewModelScope.launch {
            spacesRepository.toggleDirectMessageReaction(
                conversationId = conversationId,
                messageId = message.id,
                emoji = emoji,
                currentReaction = myCurrent
            ).onFailure { err ->
                _toastEvents.tryEmit(friendlyMessage(err, "Could not update reaction."))
            }
        }
    }

    fun deleteDirectMessage(conversationId: String, messageId: String) {
        viewModelScope.launch {
            spacesRepository.deleteDirectMessage(conversationId, messageId)
                .onSuccess { _toastEvents.tryEmit("Message deleted") }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not delete message.")) }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            spacesRepository.deleteConversation(conversationId)
                .onSuccess {
                    if ((currentOverlay.value as? SpaceOverlay.ChatConversation)?.conversationId == conversationId) {
                        popOverlay()
                    }
                    _toastEvents.tryEmit("Conversation deleted")
                }
                .onFailure { err -> _toastEvents.tryEmit(friendlyMessage(err, "Could not delete conversation.")) }
        }
    }

    // ==================== THEME, FONTS, HAPTICS & NAV PREFERENCES ====================

    fun setDynamicWallpaperEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePrefs.setDynamicWallpaperEnabled(enabled)
        }
    }

    fun setDarkThemeMode(mode: DarkThemeMode) {
        viewModelScope.launch {
            themePrefs.setDarkThemeMode(mode)
        }
    }

    fun setManualThemeOverride(overrideSystemMaterialYou: Boolean, mode: DarkThemeMode) {
        viewModelScope.launch {
            themePrefs.setManualThemeOverride(overrideSystemMaterialYou, mode)
        }
    }

    fun setCustomSeedColorArgb(argb: Long?) {
        viewModelScope.launch {
            themePrefs.setCustomSeedColorArgb(argb)
        }
    }

    fun setFontPreset(preset: OrbitFontPreset) {
        viewModelScope.launch {
            themePrefs.setFontPreset(preset)
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            themePrefs.setFontScale(scale)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePrefs.setHapticsEnabled(enabled)
        }
    }

    fun setHapticsStrength(strength: Float) {
        viewModelScope.launch {
            themePrefs.setHapticsStrength(strength)
        }
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePrefs.setReadReceiptsEnabled(enabled)
        }
    }

    fun setBottomNavSlots(slots: List<OrbitNavSlot>) {
        viewModelScope.launch {
            themePrefs.setBottomNavSlots(slots)
        }
    }

    fun setDockTabOrder(tabs: List<OrbitDockTab>) {
        viewModelScope.launch {
            themePrefs.setDockTabOrder(tabs)
        }
    }

    class Factory(
        private val application: Application,
        private val uid: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ThreadsViewModel(application, uid) as T
        }
    }
}
