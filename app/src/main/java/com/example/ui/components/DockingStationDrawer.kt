package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppRelease
import com.example.data.model.UserProfile
import com.example.data.model.formatCompactCount
import com.example.data.preferences.OrbitDockTab
import com.example.ui.haptics.LocalOrbitHaptics

/**
 * Left-edge swipe-out "Docking Station" side panel with top account switcher / sign-out popup,
 * following & followers counts, long-press drag-and-drop reorderable navigation tabs,
 * Settings, and Play Store update banner.
 */
@Composable
fun DockingStationDrawerContent(
    myProfile: UserProfile,
    latestAppRelease: AppRelease?,
    hasUnreadMessages: Boolean,
    savedPostCount: Int = 0,
    dockTabOrder: List<OrbitDockTab> = OrbitDockTab.DEFAULT_ORDER,
    onUpdateDockTabOrder: (List<OrbitDockTab>) -> Unit = {},
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSavedPosts: () -> Unit = {},
    onOpenThemeStudio: () -> Unit,
    onOpenSpaces: () -> Unit,
    onOpenDms: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPlayStoreUpdate: () -> Unit = {},
    onCloseDrawer: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val hasNewerRelease = latestAppRelease != null &&
        latestAppRelease.versionCode > BuildConfig.VERSION_CODE.toLong()

    var workingOrder by remember(dockTabOrder) {
        mutableStateOf(
            (dockTabOrder.distinct() + OrbitDockTab.DEFAULT_ORDER).distinct()
        )
    }
    var draggedTab by remember { mutableStateOf<OrbitDockTab?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowSlotHeightPx = remember(density) { with(density) { 58.dp.toPx() } }
    val currentUpdateCallback by rememberUpdatedState(onUpdateDockTabOrder)

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(304.dp)
            .fillMaxHeight()
            .testTag("docking_station_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Docking Station Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OrbitSpiralMark(modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.docking_station_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Hold tab to move",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            // Top User Profile Card -> Popup for Switch Account / Sign Out
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.performTap()
                            accountMenuExpanded = true
                        }
                        .testTag("docking_station_account_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ThreadAvatar(
                                avatarUrl = myProfile.avatarUrl,
                                displayName = myProfile.displayName.ifBlank { myProfile.username },
                                size = 44.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = myProfile.displayName.ifBlank { "Orbit Member" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@${myProfile.username.ifBlank { "member" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Account options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("docking_station_follow_stats")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatCompactCount(myProfile.followingCount),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Following",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatCompactCount(myProfile.followerCount),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Followers",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = accountMenuExpanded,
                    onDismissRequest = { accountMenuExpanded = false },
                    modifier = Modifier.testTag("docking_station_account_popup")
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_switch_account)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.SwitchAccount, contentDescription = null)
                        },
                        onClick = {
                            haptics.performMedium()
                            accountMenuExpanded = false
                            onCloseDrawer()
                            onSwitchAccount()
                        },
                        modifier = Modifier.testTag("docking_switch_account_btn")
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_sign_out),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Outlined.ExitToApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            haptics.performHeavy()
                            accountMenuExpanded = false
                            onCloseDrawer()
                            onSignOut()
                        },
                        modifier = Modifier.testTag("docking_sign_out_btn")
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            // Long-press draggable Docking Station tabs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                workingOrder.forEach { tab ->
                    key(tab.id) {
                        val isDragging = draggedTab == tab
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.035f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "dock_tab_scale"
                        )
                        val animatedOffsetY by animateFloatAsState(
                            targetValue = if (isDragging) dragOffsetY else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "dock_tab_offset"
                        )
                        val elevation by animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 0.dp,
                            label = "dock_tab_elevation"
                        )

                        val tabLabel: String
                        val tabIcon: androidx.compose.ui.graphics.vector.ImageVector
                        val tabTestTag: String
                        val tabOnClick: () -> Unit
                        val tabBadge: (@Composable () -> Unit)?

                        when (tab) {
                            OrbitDockTab.SAVED_POSTS -> {
                                tabLabel = "Saved Posts"
                                tabIcon = Icons.Outlined.BookmarkBorder
                                tabTestTag = "docking_saved_posts_item"
                                tabOnClick = {
                                    haptics.performTap()
                                    onCloseDrawer()
                                    onOpenSavedPosts()
                                }
                                tabBadge = if (savedPostCount > 0) {
                                    {
                                        Text(
                                            text = savedPostCount.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null
                            }
                            OrbitDockTab.THEME_STUDIO -> {
                                tabLabel = stringResource(R.string.theme_studio_title)
                                tabIcon = Icons.Outlined.Palette
                                tabTestTag = "docking_theme_studio_item"
                                tabOnClick = {
                                    haptics.performTap()
                                    onCloseDrawer()
                                    onOpenThemeStudio()
                                }
                                tabBadge = null
                            }
                            OrbitDockTab.SPACES -> {
                                tabLabel = stringResource(R.string.spaces_title)
                                tabIcon = Icons.Outlined.Groups
                                tabTestTag = "docking_spaces_item"
                                tabOnClick = {
                                    haptics.performTap()
                                    onCloseDrawer()
                                    onOpenSpaces()
                                }
                                tabBadge = null
                            }
                            OrbitDockTab.DMS -> {
                                tabLabel = stringResource(R.string.messages_title)
                                tabIcon = Icons.Outlined.MailOutline
                                tabTestTag = "docking_dms_item"
                                tabOnClick = {
                                    haptics.performTap()
                                    onCloseDrawer()
                                    onOpenDms()
                                }
                                tabBadge = if (hasUnreadMessages) {
                                    {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "New",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                } else null
                            }
                            OrbitDockTab.PROFILE -> {
                                tabLabel = stringResource(R.string.tab_profile)
                                tabIcon = Icons.Outlined.Person
                                tabTestTag = "docking_profile_item"
                                tabOnClick = {
                                    haptics.performTap()
                                    onCloseDrawer()
                                    onOpenProfile()
                                }
                                tabBadge = null
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = if (isDragging) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            shadowElevation = elevation,
                            border = if (isDragging) {
                                BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 10f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else animatedOffsetY
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                }
                                .pointerInput(tab) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHeavy()
                                            draggedTab = tab
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            haptics.performMedium()
                                            draggedTab = null
                                            dragOffsetY = 0f
                                            currentUpdateCallback(workingOrder)
                                        },
                                        onDragCancel = {
                                            draggedTab = null
                                            dragOffsetY = 0f
                                            currentUpdateCallback(workingOrder)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val currentIdx = workingOrder.indexOf(tab)
                                            if (currentIdx != -1) {
                                                if (dragOffsetY > rowSlotHeightPx * 0.56f && currentIdx < workingOrder.lastIndex) {
                                                    val nextList = workingOrder.toMutableList()
                                                    val swapped = nextList[currentIdx + 1]
                                                    nextList[currentIdx + 1] = tab
                                                    nextList[currentIdx] = swapped
                                                    workingOrder = nextList
                                                    dragOffsetY -= rowSlotHeightPx
                                                    haptics.performTap()
                                                } else if (dragOffsetY < -rowSlotHeightPx * 0.56f && currentIdx > 0) {
                                                    val nextList = workingOrder.toMutableList()
                                                    val swapped = nextList[currentIdx - 1]
                                                    nextList[currentIdx - 1] = tab
                                                    nextList[currentIdx] = swapped
                                                    workingOrder = nextList
                                                    dragOffsetY += rowSlotHeightPx
                                                    haptics.performTap()
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            NavigationDrawerItem(
                                label = { Text(tabLabel) },
                                selected = isDragging,
                                onClick = tabOnClick,
                                icon = { Icon(tabIcon, contentDescription = null) },
                                badge = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        tabBadge?.invoke()
                                        Icon(
                                            imageVector = Icons.Outlined.DragIndicator,
                                            contentDescription = "Hold and drag to reorder",
                                            tint = if (isDragging) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                                    .testTag(tabTestTag)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            // App Version & Update Banner -> opens Google Play Store to update Orbit
            Surface(
                onClick = {
                    haptics.performTap()
                    onCloseDrawer()
                    onOpenPlayStoreUpdate()
                },
                shape = RoundedCornerShape(14.dp),
                color = if (hasNewerRelease) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("docking_update_status_card")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = if (hasNewerRelease) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasNewerRelease) {
                                "Update v${latestAppRelease?.versionName} available"
                            } else {
                                "Orbit v${BuildConfig.VERSION_NAME}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasNewerRelease) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = "Build ${BuildConfig.VERSION_CODE}  ·  Tap to update on Play Store",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasNewerRelease) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Settings pinned cleanly at the bottom of the Docking Station
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings_title)) },
                selected = false,
                onClick = {
                    haptics.performTap()
                    onCloseDrawer()
                    onOpenSettings()
                },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("docking_settings_item")
            )
        }
    }
}
