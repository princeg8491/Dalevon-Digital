package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.cache.FeedCacheStatus
import com.example.data.cache.OrbitMediaFiles
import com.example.data.model.AppRelease
import com.example.data.model.OrbitProfileBadge
import com.example.data.model.UserProfile
import com.example.data.preferences.DarkThemeMode
import com.example.data.preferences.OrbitFontPreset
import com.example.data.preferences.OrbitNavSlot
import com.example.data.preferences.OrbitThemeConfig
import com.example.ui.components.CenteredThreadsContainer
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.orbitProfileBadgeIcon
import com.example.ui.components.orbitProfileBadgeTone
import com.example.ui.haptics.LocalOrbitHaptics
import com.example.ui.theme.OrbitPixelBadgeTone
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class SettingsSearchEntry(
    val title: String,
    val subtitle: String,
    val keywords: String,
    val targetPage: SettingsSubPage,
    val icon: ImageVector,
    val tone: OrbitPixelBadgeTone,
    val expandBadgeDropdown: Boolean = false
)

/**
 * Orbit Settings redesigned in the Google Pixel 11 Pro / Material 3 Expressive Settings style:
 * - Rounded pill "Search Settings" bar with live filtering
 * - Segmented grouped surface cards (24.dp outer, 6.dp inner corners, 2.dp row gaps)
 * - Pastel circular icon badges + circular user avatar on the Account row
 * - Dedicated sub-pages with BackHandler support preserving all controls and testTags
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    myProfile: UserProfile,
    allProfiles: List<UserProfile> = emptyList(),
    blockedUserIds: Set<String> = emptySet(),
    mutedUserIds: Set<String> = emptySet(),
    mutedWords: List<String> = emptyList(),
    themeConfig: OrbitThemeConfig,
    latestAppRelease: AppRelease?,
    feedCacheStatus: FeedCacheStatus = FeedCacheStatus(),
    isCheckingUpdate: Boolean = false,
    isDownloadingDriveApk: Boolean = false,
    driveDownloadProgress: Float = 0f,
    driveDownloadStatus: String? = null,
    onBack: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit = {},
    onDeleteAllPosts: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSetPrivateAccount: (Boolean) -> Unit = {},
    onToggleProfileBadge: (OrbitProfileBadge, Boolean) -> Unit = { _, _ -> },
    onToggleBlockUser: (String) -> Unit = {},
    onToggleMuteUser: (String) -> Unit = {},
    onAddMutedWord: (String) -> Unit = {},
    onRemoveMutedWord: (String) -> Unit = {},
    onDynamicWallpaperChanged: (Boolean) -> Unit,
    onDarkThemeModeChanged: (DarkThemeMode) -> Unit,
    onManualThemeOverride: (Boolean, DarkThemeMode) -> Unit = { overrideSystem, mode ->
        onDynamicWallpaperChanged(!overrideSystem)
        onDarkThemeModeChanged(if (overrideSystem) mode else DarkThemeMode.SYSTEM)
    },
    onCustomSeedColorChanged: (Long?) -> Unit,
    onFontPresetChanged: (OrbitFontPreset) -> Unit = {},
    onFontScaleChanged: (Float) -> Unit = {},
    onHapticsEnabledChanged: (Boolean) -> Unit,
    onHapticsStrengthChanged: (Float) -> Unit,
    onReadReceiptsEnabledChanged: (Boolean) -> Unit = {},
    onBottomNavSlotsChanged: (List<OrbitNavSlot>) -> Unit,
    onCheckForUpdate: () -> Unit = {},
    onSaveDriveDownloadUrl: (String) -> Unit = {},
    onDownloadFromDrive: (String) -> Unit = {},
    onPublishUpdateNotice: (notes: String, driveUrl: String) -> Unit,
    onClearOfflineFeedCache: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val scope = rememberCoroutineScope()

    var activeSubPage by rememberSaveable { mutableStateOf(SettingsSubPage.ROOT) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var badgeDropdownExpanded by rememberSaveable { mutableStateOf(false) }

    var showDeleteAllPostsConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showNotifyUpdateDialog by remember { mutableStateOf(false) }

    var hostedApkUrl by remember(context, latestAppRelease?.driveDownloadUrl, latestAppRelease?.versionCode) {
        mutableStateOf(
            OrbitMediaFiles.resolveActiveApkDownloadUrl(
                context = context,
                releaseDownloadUrl = latestAppRelease?.driveDownloadUrl,
                releaseVersionCode = latestAppRelease?.versionCode
            )
        )
    }
    var isGeneratingApkLink by remember { mutableStateOf(false) }
    var generateLinkProgress by remember { mutableFloatStateOf(0f) }
    var generateLinkStatus by remember { mutableStateOf("") }
    var isVerifyingApkLink by remember { mutableStateOf(false) }
    var apkLinkCheckResult by remember { mutableStateOf<OrbitMediaFiles.ApkLinkCheckResult?>(null) }
    var isSharingApkFile by remember { mutableStateOf(false) }

    val verifyOrRefreshApkLink: (forceRefresh: Boolean, onComplete: (OrbitMediaFiles.ApkLinkCheckResult, String) -> Unit) -> Unit = { forceRefresh, onComplete ->
        scope.launch {
            val currentUrl = OrbitMediaFiles.resolveOpenShareableUrl(hostedApkUrl)
            if (!forceRefresh && currentUrl.isNotBlank()) {
                isVerifyingApkLink = true
                val check = OrbitMediaFiles.inspectDirectApkDownloadUrl(currentUrl)
                apkLinkCheckResult = check
                isVerifyingApkLink = false
                if (check.isValid) {
                    onComplete(check, currentUrl)
                    return@launch
                }
            }
            if (!isGeneratingApkLink) {
                isGeneratingApkLink = true
                isVerifyingApkLink = false
                val res = OrbitMediaFiles.ensureHostedApkDownloadUrl(
                    context = context,
                    existingReleaseUrl = latestAppRelease?.driveDownloadUrl,
                    existingReleaseVersionCode = latestAppRelease?.versionCode,
                    forceRefresh = true
                ) { pct, status ->
                    generateLinkProgress = pct
                    generateLinkStatus = status
                }
                val newUrl = res.getOrDefault("")
                if (newUrl.isNotBlank()) {
                    hostedApkUrl = newUrl
                    onSaveDriveDownloadUrl(newUrl)
                    isGeneratingApkLink = false
                    isVerifyingApkLink = true
                    val verified = OrbitMediaFiles.inspectDirectApkDownloadUrl(newUrl)
                    apkLinkCheckResult = verified
                    isVerifyingApkLink = false
                    onComplete(verified, newUrl)
                } else {
                    val errMsg = res.exceptionOrNull()?.message ?: "Could not generate a live APK download link right now."
                    generateLinkStatus = errMsg
                    isGeneratingApkLink = false
                    val failed = OrbitMediaFiles.ApkLinkCheckResult(isValid = false, message = errMsg)
                    apkLinkCheckResult = failed
                    onComplete(failed, "")
                }
            }
        }
    }

    LaunchedEffect(activeSubPage, latestAppRelease?.driveDownloadUrl, latestAppRelease?.versionCode) {
        if (activeSubPage != SettingsSubPage.SHARE_ACCESS && activeSubPage != SettingsSubPage.UPDATES) {
            return@LaunchedEffect
        }
        val resolved = OrbitMediaFiles.resolveActiveApkDownloadUrl(
            context = context,
            releaseDownloadUrl = latestAppRelease?.driveDownloadUrl,
            releaseVersionCode = latestAppRelease?.versionCode
        )
        if (resolved.isNotBlank()) {
            isVerifyingApkLink = true
            val check = OrbitMediaFiles.inspectDirectApkDownloadUrl(resolved)
            apkLinkCheckResult = check
            isVerifyingApkLink = false
            if (check.isValid) {
                hostedApkUrl = resolved
                return@LaunchedEffect
            }
        }
        // Play Store App Bundle (AAB) installs use split APKs; don't try to upload base.apk
        if (OrbitMediaFiles.inspectInstalledApkIntegrity(context).hasSplitApks) {
            return@LaunchedEffect
        }
        if (!isGeneratingApkLink) {
            isGeneratingApkLink = true
            OrbitMediaFiles.ensureHostedApkDownloadUrl(
                context = context,
                existingReleaseUrl = latestAppRelease?.driveDownloadUrl,
                existingReleaseVersionCode = latestAppRelease?.versionCode,
                forceRefresh = resolved.isNotBlank()
            ) { pct, status ->
                generateLinkProgress = pct
                generateLinkStatus = status
            }.onSuccess { url ->
                hostedApkUrl = url
                onSaveDriveDownloadUrl(url)
                isGeneratingApkLink = false
                isVerifyingApkLink = true
                apkLinkCheckResult = OrbitMediaFiles.inspectDirectApkDownloadUrl(url)
                isVerifyingApkLink = false
            }.onFailure { err ->
                hostedApkUrl = ""
                generateLinkStatus = err.message ?: ""
                apkLinkCheckResult = OrbitMediaFiles.ApkLinkCheckResult(
                    isValid = false,
                    message = err.message ?: "Link unavailable"
                )
                isGeneratingApkLink = false
            }
        }
    }

    val profilesById = remember(allProfiles) { allProfiles.associateBy { it.resolvedId } }

    BackHandler {
        if (activeSubPage != SettingsSubPage.ROOT) {
            activeSubPage = SettingsSubPage.ROOT
        } else if (searchQuery.isNotBlank()) {
            searchQuery = ""
        } else {
            onBack()
        }
    }

    val hasNewerRelease = latestAppRelease != null &&
        latestAppRelease.versionCode > BuildConfig.VERSION_CODE.toLong()
    val boundDriveUrl = hostedApkUrl.trim()
    val activeDownloadUrl = remember(boundDriveUrl) {
        OrbitMediaFiles.resolveOpenShareableUrl(boundDriveUrl)
    }

    val systemIsDark = isSystemInDarkTheme()
    val resolvedDark = themeConfig.resolveDarkTheme(systemIsDark)
    val isManualOverride = !themeConfig.dynamicWallpaperEnabled ||
        themeConfig.darkThemeMode != DarkThemeMode.SYSTEM
    val activeThemeBadge = when {
        !isManualOverride -> stringResource(R.string.settings_theme_mode_system)
        resolvedDark -> stringResource(R.string.settings_theme_mode_dark)
        else -> stringResource(R.string.settings_theme_mode_light)
    }
    val strengthPercent = (themeConfig.hapticsStrength * 100f).roundToInt()
    val displayThemeTitle = stringResource(R.string.settings_manual_theme_title)

    val activeProfileBadges = remember(myProfile.profileBadges) { myProfile.resolvedProfileBadges }
    val badgeTitleText = stringResource(R.string.settings_badge_title)
    val badgeEmptySubtitle = stringResource(R.string.settings_badge_subtitle_empty)
    val badgeSummarySubtitle = remember(activeProfileBadges, badgeEmptySubtitle) {
        if (activeProfileBadges.isEmpty()) {
            badgeEmptySubtitle
        } else {
            activeProfileBadges.joinToString(" · ") { it.label }
        }
    }

    val searchIndex = remember(
        myProfile.displayName,
        myProfile.username,
        myProfile.privateAccount,
        badgeTitleText,
        badgeSummarySubtitle,
        themeConfig.readReceiptsEnabled,
        themeConfig.bottomNavSlots.size,
        themeConfig.hapticsEnabled,
        strengthPercent,
        activeThemeBadge,
        displayThemeTitle,
        themeConfig.fontPreset.label
    ) {
        listOf(
            SettingsSearchEntry(
                title = myProfile.displayName.ifBlank { "Orbit Account" },
                subtitle = "@${myProfile.username.ifBlank { "member" }} · Switch Gmail, sign out, delete posts & account",
                keywords = "account gmail sign in sign out log out switch delete posts",
                targetPage = SettingsSubPage.ACCOUNT,
                icon = Icons.Outlined.Public,
                tone = OrbitPixelBadgeTone.AMBER
            ),
            SettingsSearchEntry(
                title = badgeTitleText,
                subtitle = badgeSummarySubtitle,
                keywords = "badge status creator youtuber influencer developer profile handle",
                targetPage = SettingsSubPage.ROOT,
                icon = Icons.Outlined.Verified,
                tone = OrbitPixelBadgeTone.MINT,
                expandBadgeDropdown = true
            ),
            SettingsSearchEntry(
                title = "Share Orbit with people",
                subtitle = "Send private download access & APK installer to selected people",
                keywords = "share invite people download apk install link friends",
                targetPage = SettingsSubPage.SHARE_ACCESS,
                icon = Icons.Outlined.PersonAdd,
                tone = OrbitPixelBadgeTone.MINT
            ),
            SettingsSearchEntry(
                title = "Privacy & safety",
                subtitle = "Private page, read receipts, block & mute accounts, muted words",
                keywords = "privacy private account read receipts seen block mute words safety",
                targetPage = SettingsSubPage.PRIVACY,
                icon = Icons.Outlined.Lock,
                tone = OrbitPixelBadgeTone.SKY_BLUE
            ),
            SettingsSearchEntry(
                title = "Haptics & vibration",
                subtitle = if (themeConfig.hapticsEnabled) "$strengthPercent% strength" else "Disabled",
                keywords = "sound vibration haptics tactile strength slider",
                targetPage = SettingsSubPage.HAPTICS,
                icon = Icons.Outlined.Vibration,
                tone = OrbitPixelBadgeTone.PINK
            ),
            SettingsSearchEntry(
                title = displayThemeTitle,
                subtitle = "$activeThemeBadge · Dark theme, Light, System",
                keywords = "display touch dark light theme system override appearance",
                targetPage = SettingsSubPage.DISPLAY_THEME,
                icon = if (resolvedDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                tone = OrbitPixelBadgeTone.PEACH_ORANGE
            ),
            SettingsSearchEntry(
                title = "Wallpaper & style",
                subtitle = "Material You 3, ${themeConfig.fontPreset.label} font, RGB color wheel",
                keywords = "wallpaper style theme studio material you colors font rgb wheel",
                targetPage = SettingsSubPage.THEME_STUDIO,
                icon = Icons.Outlined.Palette,
                tone = OrbitPixelBadgeTone.AMBER
            ),
            SettingsSearchEntry(
                title = "About (Beta)",
                subtitle = "Orbit v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE} · Offline cache",
                keywords = "about beta build version info package android device offline cache datastore storage",
                targetPage = SettingsSubPage.ABOUT,
                icon = Icons.Outlined.Info,
                tone = OrbitPixelBadgeTone.PERIWINKLE
            )
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeSubPage == SettingsSubPage.ROOT) {
                            stringResource(R.string.settings_title)
                        } else {
                            activeSubPage.title
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.performTap()
                            if (activeSubPage != SettingsSubPage.ROOT) {
                                activeSubPage = SettingsSubPage.ROOT
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        CenteredThreadsContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeSubPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pixel_settings_page"
            ) { currentPage ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .testTag("app_settings_scroll_column")
                ) {
                    when (currentPage) {
                        SettingsSubPage.ROOT -> {
                            PixelSettingsSearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it }
                            )

                            val normalizedQuery = searchQuery.trim().lowercase()
                            if (normalizedQuery.isNotEmpty()) {
                                val matches = remember(normalizedQuery, searchIndex) {
                                    searchIndex.filter { entry ->
                                        entry.title.contains(normalizedQuery, ignoreCase = true) ||
                                            entry.subtitle.contains(normalizedQuery, ignoreCase = true) ||
                                            entry.keywords.contains(normalizedQuery, ignoreCase = true)
                                    }
                                }
                                if (matches.isEmpty()) {
                                    Surface(
                                        shape = pixelSegmentedShape(0, 1),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_no_search_results),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            TextButton(onClick = { searchQuery = "" }) {
                                                Text("Clear search")
                                            }
                                        }
                                    }
                                } else {
                                    PixelSettingsGroup {
                                        matches.forEachIndexed { idx, entry ->
                                            PixelSettingsRow(
                                                title = entry.title,
                                                subtitle = entry.subtitle,
                                                shape = pixelSegmentedShape(idx, matches.size),
                                                onClick = {
                                                    if (entry.expandBadgeDropdown) {
                                                        searchQuery = ""
                                                        badgeDropdownExpanded = true
                                                    } else {
                                                        activeSubPage = entry.targetPage
                                                    }
                                                },
                                                leadingContent = {
                                                    PixelSettingsBadgeIcon(
                                                        icon = entry.icon,
                                                        tone = entry.tone
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Group 1: User Account Card (Invite Friends & Updates and Download & Share Orbit removed from top per Screenshot 2)
                                PixelSettingsGroup {
                                    PixelSettingsRow(
                                        title = myProfile.displayName.ifBlank { "Orbit Member" },
                                        subtitle = "@${myProfile.username.ifBlank { "member" }} · Google account, sign out, posts & data",
                                        shape = pixelSegmentedShape(0, 1),
                                        onClick = { activeSubPage = SettingsSubPage.ACCOUNT },
                                        leadingContent = {
                                            ThreadAvatar(
                                                avatarUrl = myProfile.avatarUrl,
                                                displayName = myProfile.displayName.ifBlank { myProfile.username },
                                                size = 42.dp
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_section_account")
                                    )
                                }

                                // Group 1b: Badge Dropdown Tab (Creator, YouTuber, Influencer, Developer)
                                val badgeChevronRotation by animateFloatAsState(
                                    targetValue = if (badgeDropdownExpanded) 180f else 0f,
                                    label = "badge_dropdown_chevron"
                                )
                                val badgeCount = OrbitProfileBadge.entries.size
                                val totalBadgeRows = if (badgeDropdownExpanded) 1 + badgeCount else 1
                                PixelSettingsGroup {
                                    PixelSettingsRow(
                                        title = badgeTitleText,
                                        subtitle = badgeSummarySubtitle,
                                        shape = pixelSegmentedShape(0, totalBadgeRows),
                                        badgeText = if (activeProfileBadges.isNotEmpty()) "${activeProfileBadges.size} on" else null,
                                        onClick = { badgeDropdownExpanded = !badgeDropdownExpanded },
                                        leadingContent = {
                                            PixelSettingsBadgeIcon(
                                                icon = Icons.Outlined.Verified,
                                                tone = OrbitPixelBadgeTone.MINT
                                            )
                                        },
                                        trailingContent = {
                                            Icon(
                                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                                contentDescription = stringResource(R.string.settings_badge_toggle_desc),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.rotate(badgeChevronRotation)
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_badge_dropdown_tab")
                                    )

                                    AnimatedVisibility(
                                        visible = badgeDropdownExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("settings_badge_dropdown_content")
                                        ) {
                                            OrbitProfileBadge.entries.forEachIndexed { idx, badge ->
                                                val isEnabled = badge in activeProfileBadges
                                                PixelSettingsRow(
                                                    title = badge.label,
                                                    subtitle = badge.description,
                                                    shape = pixelSegmentedShape(idx + 1, 1 + badgeCount),
                                                    onClick = {
                                                        onToggleProfileBadge(badge, !isEnabled)
                                                    },
                                                    leadingContent = {
                                                        PixelSettingsBadgeIcon(
                                                            icon = orbitProfileBadgeIcon(badge),
                                                            tone = orbitProfileBadgeTone(badge)
                                                        )
                                                    },
                                                    trailingContent = {
                                                        Switch(
                                                            checked = isEnabled,
                                                            onCheckedChange = { checked ->
                                                                haptics.performSelectionTick()
                                                                onToggleProfileBadge(badge, checked)
                                                            },
                                                            modifier = Modifier.testTag("settings_badge_switch_${badge.id}")
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("settings_badge_row_${badge.id}")
                                                )
                                            }
                                        }
                                    }
                                }

                                // Group 2: Privacy & Safety (Matches Pixel Sky-Blue Connectivity group)
                                PixelSettingsGroup {
                                    PixelSettingsRow(
                                        title = "Privacy & safety",
                                        subtitle = buildString {
                                            append(if (myProfile.privateAccount) "Private page" else "Public page")
                                            append(" · ${blockedUserIds.size} blocked · ${mutedUserIds.size} muted")
                                        },
                                        shape = pixelSegmentedShape(0, 1),
                                        onClick = { activeSubPage = SettingsSubPage.PRIVACY },
                                        leadingContent = {
                                            PixelSettingsBadgeIcon(
                                                icon = Icons.Outlined.Lock,
                                                tone = OrbitPixelBadgeTone.SKY_BLUE
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_section_privacy")
                                    )
                                }

                                // Group 3: Haptics & Vibration
                                PixelSettingsGroup {
                                    PixelSettingsRow(
                                        title = "Haptics & vibration",
                                        subtitle = if (themeConfig.hapticsEnabled) {
                                            "Tactile feedback · $strengthPercent% strength"
                                        } else {
                                            "Vibration & tactile feedback off"
                                        },
                                        shape = pixelSegmentedShape(0, 1),
                                        onClick = { activeSubPage = SettingsSubPage.HAPTICS },
                                        leadingContent = {
                                            PixelSettingsBadgeIcon(
                                                icon = Icons.Outlined.Vibration,
                                                tone = OrbitPixelBadgeTone.PINK
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_section_haptics")
                                    )
                                }

                                // Group 4: Display & Wallpaper Studio (Matches Pixel Amber / Peach group)
                                PixelSettingsGroup {
                                    PixelSettingsRow(
                                        title = stringResource(R.string.settings_manual_theme_title),
                                        subtitle = if (isManualOverride) {
                                            "$activeThemeBadge · Overriding system Material You"
                                        } else {
                                            "System Material You · Dark theme, Light mode"
                                        },
                                        shape = pixelSegmentedShape(0, 2),
                                        badgeText = activeThemeBadge,
                                        onClick = { activeSubPage = SettingsSubPage.DISPLAY_THEME },
                                        leadingContent = {
                                            PixelSettingsBadgeIcon(
                                                icon = when {
                                                    !isManualOverride -> Icons.Outlined.BrightnessAuto
                                                    resolvedDark -> Icons.Outlined.DarkMode
                                                    else -> Icons.Outlined.LightMode
                                                },
                                                tone = OrbitPixelBadgeTone.PEACH_ORANGE
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_section_manual_theme")
                                    )
                                    PixelSettingsRow(
                                        title = "Wallpaper & style",
                                        subtitle = "Colors, ${themeConfig.fontPreset.label} font, RGB wheel",
                                        shape = pixelSegmentedShape(1, 2),
                                        onClick = { activeSubPage = SettingsSubPage.THEME_STUDIO },
                                        leadingContent = {
                                            PixelSettingsBadgeIcon(
                                                icon = Icons.Outlined.Palette,
                                                tone = OrbitPixelBadgeTone.AMBER
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_section_theme_studio")
                                    )
                                }

                                // Group 5: Share Orbit with Selected People & About Orbit (Beta)
                                PixelSettingsGroup {
                                    PixelSettingsRow(
                                        title = "Share Orbit with people",
                                        subtitle = "Send private download access & APK installer to selected people",
                                        shape = pixelSegmentedShape(0, 2),
                                        onClick = { activeSubPage = SettingsSubPage.SHARE_ACCESS },
                                        leadingContent = {
                                            PixelSettingsBadgeIcon(
                                                icon = Icons.Outlined.PersonAdd,
                                                tone = OrbitPixelBadgeTone.MINT
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_section_share_access")
                                    )
                                    PixelSettingsRow(
                                        title = stringResource(R.string.settings_about_title),
                                        subtitle = stringResource(
                                            R.string.settings_about_subtitle,
                                            BuildConfig.VERSION_NAME,
                                            BuildConfig.VERSION_CODE
                                        ),
                                        shape = pixelSegmentedShape(1, 2),
                                        badgeText = stringResource(R.string.settings_about_beta_badge),
                                        onClick = { activeSubPage = SettingsSubPage.ABOUT },
                                        leadingContent = {
                                            PixelSettingsBadgeIcon(
                                                icon = Icons.Outlined.Info,
                                                tone = OrbitPixelBadgeTone.PERIWINKLE
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_section_about")
                                    )
                                }
                            }
                        }

                        SettingsSubPage.ACCOUNT -> {
                            PixelAccountSubPage(
                                myProfile = myProfile,
                                onSwitchAccount = onSwitchAccount,
                                onSignOut = onSignOut,
                                onShowDeletePosts = { showDeleteAllPostsConfirm = true },
                                onShowDeleteAccount = { showDeleteAccountConfirm = true }
                            )
                        }

                        SettingsSubPage.PRIVACY -> {
                            PixelPrivacySubPage(
                                myProfile = myProfile,
                                allProfiles = allProfiles,
                                profilesById = profilesById,
                                blockedUserIds = blockedUserIds,
                                mutedUserIds = mutedUserIds,
                                mutedWords = mutedWords,
                                readReceiptsEnabled = themeConfig.readReceiptsEnabled,
                                onSetPrivateAccount = onSetPrivateAccount,
                                onReadReceiptsEnabledChanged = onReadReceiptsEnabledChanged,
                                onToggleBlockUser = onToggleBlockUser,
                                onToggleMuteUser = onToggleMuteUser,
                                onAddMutedWord = onAddMutedWord,
                                onRemoveMutedWord = onRemoveMutedWord
                            )
                        }

                        SettingsSubPage.DISPLAY_THEME -> {
                            PixelDisplayThemeSubPage(
                                isManualOverride = isManualOverride,
                                resolvedDark = resolvedDark,
                                onManualThemeOverride = onManualThemeOverride
                            )
                        }

                        SettingsSubPage.THEME_STUDIO -> {
                            PixelThemeStudioSubPage(
                                themeConfig = themeConfig,
                                onDynamicWallpaperChanged = onDynamicWallpaperChanged,
                                onDarkThemeModeChanged = onDarkThemeModeChanged,
                                onCustomSeedColorChanged = onCustomSeedColorChanged,
                                onFontPresetChanged = onFontPresetChanged,
                                onFontScaleChanged = onFontScaleChanged
                            )
                        }

                        SettingsSubPage.SHARE_ACCESS -> {
                            PixelShareAppWithPeopleSubPage(
                                myProfile = myProfile,
                                allProfiles = allProfiles,
                                resolvedApkUrl = activeDownloadUrl,
                                apkLinkCheckResult = apkLinkCheckResult,
                                isCheckingApkLink = isVerifyingApkLink,
                                isPreparingHostedLink = isGeneratingApkLink,
                                hostedLinkProgress = generateLinkProgress,
                                hostedLinkStatus = generateLinkStatus,
                                isSharingApkFile = isSharingApkFile,
                                onVerifyOrGenerateLink = {
                                    verifyOrRefreshApkLink(true) { check, _ ->
                                        val toastText = if (check.isValid) {
                                            "Verified working APK link (${check.hostLabel})"
                                        } else {
                                            check.message
                                        }
                                        Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSendSelectiveInviteLink = { recipients ->
                                    verifyOrRefreshApkLink(false) { check, verifiedUrl ->
                                        if (check.isValid && verifiedUrl.isNotBlank()) {
                                            val shareText = OrbitMediaFiles.buildSelectiveInviteShareMessage(
                                                inviterUsername = myProfile.username,
                                                versionName = BuildConfig.VERSION_NAME,
                                                recipients = recipients,
                                                apkDownloadUrl = verifiedUrl
                                            )
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "Private Orbit v${BuildConfig.VERSION_NAME} Download Invite")
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            runCatching {
                                                context.startActivity(
                                                    Intent.createChooser(sendIntent, "Send Orbit download invite")
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }
                                        } else {
                                            // Fallback: immediately share the standalone APK file directly
                                            scope.launch {
                                                isSharingApkFile = true
                                                val apkUri = OrbitMediaFiles.prepareShareableInstalledApkUri(context)
                                                isSharingApkFile = false
                                                val shareText = OrbitMediaFiles.buildSelectiveInviteShareMessage(
                                                    inviterUsername = myProfile.username,
                                                    versionName = BuildConfig.VERSION_NAME,
                                                    recipients = recipients,
                                                    apkDownloadUrl = ""
                                                )
                                                if (apkUri != null) {
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/vnd.android.package-archive"
                                                        putExtra(Intent.EXTRA_STREAM, apkUri)
                                                        putExtra(Intent.EXTRA_SUBJECT, "Orbit v${BuildConfig.VERSION_NAME} APK")
                                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                                        clipData = ClipData.newRawUri("Orbit APK", apkUri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent.createChooser(sendIntent, "Send Orbit APK to selected people")
                                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        )
                                                    }
                                                } else {
                                                    Toast.makeText(context, check.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                onSendSelectiveApkFile = { recipients ->
                                    if (!isSharingApkFile) {
                                        scope.launch {
                                            isSharingApkFile = true
                                            val apkUri = OrbitMediaFiles.prepareShareableInstalledApkUri(context)
                                            isSharingApkFile = false
                                            if (apkUri != null) {
                                                val shareText = OrbitMediaFiles.buildSelectiveInviteShareMessage(
                                                    inviterUsername = myProfile.username,
                                                    versionName = BuildConfig.VERSION_NAME,
                                                    recipients = recipients,
                                                    apkDownloadUrl = activeDownloadUrl
                                                )
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/vnd.android.package-archive"
                                                    putExtra(Intent.EXTRA_STREAM, apkUri)
                                                    putExtra(Intent.EXTRA_SUBJECT, "Orbit v${BuildConfig.VERSION_NAME} Android App")
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                    clipData = ClipData.newRawUri("Orbit APK", apkUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                runCatching {
                                                    context.startActivity(
                                                        Intent.createChooser(sendIntent, "Send Orbit-v${BuildConfig.VERSION_NAME}.apk")
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    )
                                                }
                                            } else {
                                                val integrity = OrbitMediaFiles.inspectInstalledApkIntegrity(context)
                                                Toast.makeText(
                                                    context,
                                                    integrity.reason.ifBlank { "Could not prepare Orbit APK installer file." },
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                onCopySelectiveInviteLink = { recipients ->
                                    verifyOrRefreshApkLink(false) { check, verifiedUrl ->
                                        val inviteText = OrbitMediaFiles.buildSelectiveInviteShareMessage(
                                            inviterUsername = myProfile.username,
                                            versionName = BuildConfig.VERSION_NAME,
                                            recipients = recipients,
                                            apkDownloadUrl = if (check.isValid) verifiedUrl else activeDownloadUrl
                                        )
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Orbit Personal Invite", inviteText))
                                        Toast.makeText(context, "Personal download invite copied!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        SettingsSubPage.ABOUT -> {
                            PixelAboutSubPage(
                                feedCacheStatus = feedCacheStatus,
                                onClearOfflineFeedCache = onClearOfflineFeedCache
                            )
                        }

                        SettingsSubPage.HAPTICS -> {
                            PixelHapticsSubPage(
                                themeConfig = themeConfig,
                                onHapticsEnabledChanged = onHapticsEnabledChanged,
                                onHapticsStrengthChanged = onHapticsStrengthChanged
                            )
                        }

                        SettingsSubPage.UPDATES -> {
                            PixelUpdatesAndShareSubPage(
                                context = context,
                                scope = scope,
                                myProfile = myProfile,
                                latestAppRelease = latestAppRelease,
                                hasNewerRelease = hasNewerRelease,
                                activeDownloadUrl = activeDownloadUrl,
                                apkLinkCheckResult = apkLinkCheckResult,
                                isVerifyingApkLink = isVerifyingApkLink,
                                isGeneratingApkLink = isGeneratingApkLink,
                                generateLinkProgress = generateLinkProgress,
                                generateLinkStatus = generateLinkStatus,
                                isCheckingUpdate = isCheckingUpdate,
                                isDownloadingDriveApk = isDownloadingDriveApk,
                                driveDownloadProgress = driveDownloadProgress,
                                driveDownloadStatus = driveDownloadStatus,
                                onVerifyOrRefreshLink = verifyOrRefreshApkLink,
                                onHostedUrlUpdated = { hostedApkUrl = it },
                                onGenerateProgressChanged = { generating, pct, status ->
                                    isGeneratingApkLink = generating
                                    generateLinkProgress = pct
                                    generateLinkStatus = status
                                },
                                onSaveDriveDownloadUrl = onSaveDriveDownloadUrl,
                                onCheckForUpdate = onCheckForUpdate,
                                onDownloadFromDrive = onDownloadFromDrive,
                                onOpenNotifyDialog = { showNotifyUpdateDialog = true }
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDeleteAllPostsConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllPostsConfirm = false },
            title = { Text("Delete all your posts?") },
            text = {
                Text("This permanently deletes every thread and reply you have published on Orbit. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.performHeavy()
                        showDeleteAllPostsConfirm = false
                        onDeleteAllPosts()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_all_posts_btn")
                ) {
                    Text("Delete all posts")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllPostsConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = { Text("Delete your Orbit account?") },
            text = {
                Text("Your profile, saved links, all your posts, and your account access will be permanently removed and you will be signed out.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.performHeavy()
                        showDeleteAccountConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_account_btn")
                ) {
                    Text("Delete account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNotifyUpdateDialog) {
        var releaseNotes by remember {
            mutableStateOf("Orbit v${BuildConfig.VERSION_NAME} is now available with Pixel 11 Pro Settings, verified APK downloads, DM emoji reactions, and bug fixes!")
        }
        AlertDialog(
            onDismissRequest = { showNotifyUpdateDialog = false },
            title = { Text("Publish update v${BuildConfig.VERSION_NAME}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Notify Orbit users of v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = releaseNotes,
                        onValueChange = { if (it.length <= 280) releaseNotes = it },
                        label = { Text("What's new") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("update_release_notes_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.performMedium()
                        showNotifyUpdateDialog = false
                        onPublishUpdateNotice(releaseNotes.trim(), boundDriveUrl)
                    },
                    enabled = releaseNotes.isNotBlank(),
                    modifier = Modifier.testTag("confirm_notify_update_btn")
                ) {
                    Text("Publish & Notify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifyUpdateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
