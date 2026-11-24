package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.cache.FeedCacheStatus
import com.example.data.cache.FeedDataSource
import com.example.data.cache.OrbitMediaFiles
import com.example.data.model.AppRelease
import com.example.data.model.UserProfile
import com.example.data.preferences.DarkThemeMode
import com.example.data.preferences.OrbitFontPreset
import com.example.data.preferences.OrbitNavSlot
import com.example.data.preferences.OrbitThemeConfig
import com.example.ui.components.ThemeStudioContent
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.UserSearchDropdownField
import com.example.ui.haptics.LocalOrbitHaptics
import com.example.ui.theme.OrbitPixelBadgeTone
import com.example.ui.theme.rememberOrbitPixelBadgeColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SettingsSubPage(val title: String) {
    ROOT("Settings"),
    ACCOUNT("Account"),
    UPDATES("App updates & invite"),
    SHARE_ACCESS("Share Orbit with people"),
    PRIVACY("Privacy & safety"),
    HAPTICS("Haptics & vibration"),
    DISPLAY_THEME("Display & theme mode"),
    THEME_STUDIO("Wallpaper & style"),
    ABOUT("About")
}

/**
 * Pixel 11 Pro / Material 3 Expressive segmented card corners:
 * `24.dp` outer corners with `6.dp` inner corners separated by `2.dp` gaps.
 */
fun pixelSegmentedShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer,
            topEnd = outer,
            bottomStart = inner,
            bottomEnd = inner
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = inner,
            topEnd = inner,
            bottomStart = outer,
            bottomEnd = outer
        )
        else -> RoundedCornerShape(inner)
    }
}

@Composable
fun PixelSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
fun PixelSettingsBadgeIcon(
    icon: ImageVector,
    tone: OrbitPixelBadgeTone,
    modifier: Modifier = Modifier
) {
    val palette = rememberOrbitPixelBadgeColors(tone)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(palette.container)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.content,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun PixelSettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_search_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { if (it.length <= 60) onQueryChange(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_search_input")
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        haptics.performTap()
                        onQueryChange("")
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PixelSettingsRow(
    title: String,
    subtitle: String? = null,
    shape: RoundedCornerShape,
    onClick: (() -> Unit)? = null,
    badgeText: String? = null,
    leadingContent: @Composable () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptics.performTap()
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 74.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            leadingContent()
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(12.dp))
                trailingContent()
            }
        }
    }
}

@Composable
fun PixelAccountSubPage(
    myProfile: UserProfile,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit = {},
    onShowDeletePosts: () -> Unit,
    onShowDeleteAccount: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PixelSettingsGroup {
            Surface(
                shape = pixelSegmentedShape(0, 3),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    ThreadAvatar(
                        avatarUrl = myProfile.avatarUrl,
                        displayName = myProfile.displayName.ifBlank { myProfile.username },
                        size = 50.dp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = myProfile.displayName.ifBlank { "Orbit Member" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "@${myProfile.username.ifBlank { "member" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            PixelSettingsRow(
                title = "Sign in with another Gmail account",
                subtitle = "Switch Google credentials on this device",
                shape = pixelSegmentedShape(1, 3),
                onClick = {
                    haptics.performMedium()
                    onSwitchAccount()
                },
                leadingContent = {
                    PixelSettingsBadgeIcon(
                        icon = Icons.Outlined.SwitchAccount,
                        tone = OrbitPixelBadgeTone.PERIWINKLE
                    )
                },
                modifier = Modifier.testTag("settings_switch_gmail_btn")
            )
            PixelSettingsRow(
                title = stringResource(R.string.action_sign_out),
                subtitle = "Sign out of @${myProfile.username.ifBlank { "member" }} on this device",
                shape = pixelSegmentedShape(2, 3),
                onClick = {
                    haptics.performHeavy()
                    onSignOut()
                },
                leadingContent = {
                    PixelSettingsBadgeIcon(
                        icon = Icons.AutoMirrored.Outlined.ExitToApp,
                        tone = OrbitPixelBadgeTone.AMBER
                    )
                },
                modifier = Modifier.testTag("settings_sign_out_btn")
            )
        }

        PixelSettingsGroup {
            PixelSettingsRow(
                title = "Delete all my posts",
                subtitle = "Permanently remove your threads and replies",
                shape = pixelSegmentedShape(0, 2),
                onClick = {
                    haptics.performMedium()
                    onShowDeletePosts()
                },
                leadingContent = {
                    PixelSettingsBadgeIcon(
                        icon = Icons.Outlined.DeleteSweep,
                        tone = OrbitPixelBadgeTone.PEACH_ORANGE
                    )
                },
                modifier = Modifier.testTag("settings_delete_all_posts_btn")
            )
            PixelSettingsRow(
                title = "Delete account",
                subtitle = "Erase profile, posts, and account data",
                shape = pixelSegmentedShape(1, 2),
                onClick = {
                    haptics.performHeavy()
                    onShowDeleteAccount()
                },
                leadingContent = {
                    PixelSettingsBadgeIcon(
                        icon = Icons.Outlined.DeleteForever,
                        tone = OrbitPixelBadgeTone.PINK
                    )
                },
                modifier = Modifier.testTag("settings_delete_account_btn")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PixelPrivacySubPage(
    myProfile: UserProfile,
    allProfiles: List<UserProfile>,
    profilesById: Map<String, UserProfile>,
    blockedUserIds: Set<String>,
    mutedUserIds: Set<String>,
    mutedWords: List<String>,
    readReceiptsEnabled: Boolean,
    onSetPrivateAccount: (Boolean) -> Unit,
    onReadReceiptsEnabledChanged: (Boolean) -> Unit,
    onToggleBlockUser: (String) -> Unit,
    onToggleMuteUser: (String) -> Unit,
    onAddMutedWord: (String) -> Unit,
    onRemoveMutedWord: (String) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var blockSearchQuery by remember { mutableStateOf("") }
    var muteSearchQuery by remember { mutableStateOf("") }
    var mutedWordInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PixelSettingsGroup {
            PixelSettingsRow(
                title = "Private Page",
                subtitle = "Only people who follow you can view your profile posts and albums.",
                shape = pixelSegmentedShape(0, 2),
                leadingContent = {
                    PixelSettingsBadgeIcon(
                        icon = Icons.Outlined.Public,
                        tone = OrbitPixelBadgeTone.SKY_BLUE
                    )
                },
                trailingContent = {
                    Switch(
                        checked = myProfile.privateAccount,
                        onCheckedChange = { enabled ->
                            haptics.performMedium()
                            onSetPrivateAccount(enabled)
                        },
                        modifier = Modifier.testTag("settings_private_account_switch")
                    )
                }
            )
            PixelSettingsRow(
                title = stringResource(R.string.settings_read_receipts_label),
                subtitle = stringResource(R.string.settings_read_receipts_desc),
                shape = pixelSegmentedShape(1, 2),
                leadingContent = {
                    PixelSettingsBadgeIcon(
                        icon = Icons.Outlined.Info,
                        tone = OrbitPixelBadgeTone.MINT
                    )
                },
                trailingContent = {
                    Switch(
                        checked = readReceiptsEnabled,
                        onCheckedChange = { enabled ->
                            haptics.performMedium()
                            onReadReceiptsEnabledChanged(enabled)
                        },
                        modifier = Modifier.testTag("settings_read_receipts_switch")
                    )
                }
            )
        }

        // Block People card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelSettingsBadgeIcon(
                            icon = Icons.Outlined.Block,
                            tone = OrbitPixelBadgeTone.PINK
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Blocked accounts (${blockedUserIds.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.privacy_blocked_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    UserSearchDropdownField(
                        query = blockSearchQuery,
                        onQueryChange = { blockSearchQuery = it },
                        allUsers = allProfiles,
                        excludedUserIds = blockedUserIds + myProfile.resolvedId,
                        placeholder = "Search user to block…",
                        actionLabel = "Block",
                        testTag = "settings_block_user_search",
                        onSelectUser = { user -> onToggleBlockUser(user.resolvedId) }
                    )
                    if (blockedUserIds.isEmpty()) {
                        Text(
                            text = "No blocked accounts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        blockedUserIds.forEach { uid ->
                            val p = profilesById[uid]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("privacy_blocked_row_$uid")
                            ) {
                                ThreadAvatar(
                                    avatarUrl = p?.avatarUrl.orEmpty(),
                                    displayName = p?.displayName ?: "User",
                                    size = 36.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (!p?.displayName.isNullOrBlank()) {
                                        Text(
                                            text = p?.displayName.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = p?.let { "@${it.username}" } ?: uid.take(10),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = {
                                    haptics.performTap()
                                    onToggleBlockUser(uid)
                                }) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            }

        // Mute People & Muted Words card
        PixelSettingsGroup {
            Surface(
                shape = pixelSegmentedShape(0, 2),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelSettingsBadgeIcon(
                            icon = Icons.AutoMirrored.Outlined.VolumeOff,
                            tone = OrbitPixelBadgeTone.PERIWINKLE
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Muted accounts (${mutedUserIds.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.privacy_muted_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    UserSearchDropdownField(
                        query = muteSearchQuery,
                        onQueryChange = { muteSearchQuery = it },
                        allUsers = allProfiles,
                        excludedUserIds = mutedUserIds + myProfile.resolvedId,
                        placeholder = "Search user to mute…",
                        actionLabel = "Mute",
                        testTag = "settings_mute_user_search",
                        onSelectUser = { user -> onToggleMuteUser(user.resolvedId) }
                    )
                    if (mutedUserIds.isEmpty()) {
                        Text(
                            text = "No muted accounts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        mutedUserIds.forEach { uid ->
                            val p = profilesById[uid]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("privacy_muted_row_$uid")
                            ) {
                                ThreadAvatar(
                                    avatarUrl = p?.avatarUrl.orEmpty(),
                                    displayName = p?.displayName ?: "User",
                                    size = 36.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (!p?.displayName.isNullOrBlank()) {
                                        Text(
                                            text = p?.displayName.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = p?.let { "@${it.username}" } ?: uid.take(10),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = {
                                    haptics.performTap()
                                    onToggleMuteUser(uid)
                                }) {
                                    Text("Unmute")
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = pixelSegmentedShape(1, 2),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Muted Words (${mutedWords.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = mutedWordInput,
                            onValueChange = { if (it.length <= 40) mutedWordInput = it },
                            placeholder = { Text("Add a word or phrase to hide…") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_muted_word_input")
                        )
                        FilledTonalButton(
                            onClick = {
                                val word = mutedWordInput.trim()
                                if (word.isNotEmpty()) {
                                    haptics.performTap()
                                    onAddMutedWord(word)
                                    mutedWordInput = ""
                                }
                            },
                            enabled = mutedWordInput.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("settings_add_muted_word_btn")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add muted word", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (mutedWords.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            mutedWords.forEach { word ->
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        haptics.performTap()
                                        onRemoveMutedWord(word)
                                    },
                                    label = { Text(word) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "Remove $word",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PixelDisplayThemeSubPage(
    isManualOverride: Boolean,
    resolvedDark: Boolean,
    onManualThemeOverride: (Boolean, DarkThemeMode) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PixelSettingsGroup {
            PixelSettingsRow(
                title = stringResource(R.string.settings_manual_theme_override_label),
                subtitle = stringResource(R.string.settings_manual_theme_override_desc),
                shape = pixelSegmentedShape(0, 2),
                leadingContent = {
                    PixelSettingsBadgeIcon(
                        icon = Icons.Outlined.BrightnessAuto,
                        tone = OrbitPixelBadgeTone.PEACH_ORANGE
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isManualOverride,
                        onCheckedChange = { enableManualOverride ->
                            haptics.performMedium()
                            if (enableManualOverride) {
                                val initialMode = if (resolvedDark) DarkThemeMode.DARK else DarkThemeMode.LIGHT
                                onManualThemeOverride(true, initialMode)
                            } else {
                                onManualThemeOverride(false, DarkThemeMode.SYSTEM)
                            }
                        },
                        modifier = Modifier.testTag("settings_manual_theme_override_switch")
                    )
                }
            )
            PixelSettingsRow(
                title = stringResource(R.string.settings_manual_dark_mode_label),
                subtitle = when {
                    !isManualOverride -> stringResource(R.string.settings_manual_dark_mode_desc_system)
                    resolvedDark -> stringResource(R.string.settings_manual_dark_mode_desc_dark)
                    else -> stringResource(R.string.settings_manual_dark_mode_desc_light)
                },
                shape = pixelSegmentedShape(1, 2),
                leadingContent = {
                    PixelSettingsBadgeIcon(
                        icon = if (resolvedDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        tone = OrbitPixelBadgeTone.AMBER
                    )
                },
                trailingContent = {
                    Switch(
                        checked = resolvedDark,
                        onCheckedChange = { wantDark ->
                            haptics.performMedium()
                            onManualThemeOverride(true, if (wantDark) DarkThemeMode.DARK else DarkThemeMode.LIGHT)
                        },
                        modifier = Modifier.testTag("settings_manual_dark_mode_switch")
                    )
                }
            )
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Theme mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isManualOverride && !resolvedDark,
                        onClick = {
                            haptics.performTap()
                            onManualThemeOverride(true, DarkThemeMode.LIGHT)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = {
                            Icon(Icons.Outlined.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("settings_manual_theme_light")
                    ) {
                        Text(stringResource(R.string.settings_theme_mode_light))
                    }
                    SegmentedButton(
                        selected = isManualOverride && resolvedDark,
                        onClick = {
                            haptics.performTap()
                            onManualThemeOverride(true, DarkThemeMode.DARK)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = {
                            Icon(Icons.Outlined.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("settings_manual_theme_dark")
                    ) {
                        Text(stringResource(R.string.settings_theme_mode_dark))
                    }
                    SegmentedButton(
                        selected = !isManualOverride,
                        onClick = {
                            haptics.performTap()
                            onManualThemeOverride(false, DarkThemeMode.SYSTEM)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = {
                            Icon(Icons.Outlined.BrightnessAuto, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("settings_manual_theme_system")
                    ) {
                        Text(stringResource(R.string.settings_theme_mode_system))
                    }
                }
            }
        }
    }
}

@Composable
fun PixelThemeStudioSubPage(
    themeConfig: OrbitThemeConfig,
    onDynamicWallpaperChanged: (Boolean) -> Unit,
    onDarkThemeModeChanged: (DarkThemeMode) -> Unit,
    onCustomSeedColorChanged: (Long?) -> Unit,
    onFontPresetChanged: (OrbitFontPreset) -> Unit,
    onFontScaleChanged: (Float) -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            ThemeStudioContent(
                themeConfig = themeConfig,
                onDynamicWallpaperChanged = onDynamicWallpaperChanged,
                onDarkThemeModeChanged = onDarkThemeModeChanged,
                onCustomSeedColorChanged = onCustomSeedColorChanged,
                onFontPresetChanged = onFontPresetChanged,
                onFontScaleChanged = onFontScaleChanged
            )
        }
    }
}

@Composable
fun PixelNavBarSubPage(
    enabledSlots: List<OrbitNavSlot>,
    onBottomNavSlotsChanged: (List<OrbitNavSlot>) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val disabledSlots = OrbitNavSlot.entries.filter { it !in enabledSlots }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Add up to 6 icons, toggle them on or off, and arrange their order. Tip: You can also long-press any icon on the bottom bar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        PixelSettingsGroup {
            enabledSlots.forEachIndexed { idx, slot ->
                Surface(
                    shape = pixelSegmentedShape(idx, enabledSlots.size),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${idx + 1}. ${slot.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    haptics.performTap()
                                    val updated = enabledSlots.toMutableList()
                                    val item = updated.removeAt(idx)
                                    updated.add(idx - 1, item)
                                    onBottomNavSlotsChanged(updated)
                                }
                            },
                            enabled = idx > 0,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move earlier")
                        }
                        IconButton(
                            onClick = {
                                if (idx < enabledSlots.lastIndex) {
                                    haptics.performTap()
                                    val updated = enabledSlots.toMutableList()
                                    val item = updated.removeAt(idx)
                                    updated.add(idx + 1, item)
                                    onBottomNavSlotsChanged(updated)
                                }
                            },
                            enabled = idx < enabledSlots.lastIndex,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move later")
                        }
                        Switch(
                            checked = true,
                            enabled = enabledSlots.size > OrbitNavSlot.MIN_SLOTS && slot != OrbitNavSlot.HOME,
                            onCheckedChange = {
                                haptics.performTap()
                                onBottomNavSlotsChanged(enabledSlots.filterNot { it == slot })
                            },
                            modifier = Modifier.testTag("settings_nav_toggle_${slot.id}")
                        )
                    }
                }
            }
        }

        if (disabledSlots.isNotEmpty()) {
            PixelSettingsGroup {
                disabledSlots.forEachIndexed { idx, slot ->
                    Surface(
                        shape = pixelSegmentedShape(idx, disabledSlots.size),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = slot.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = false,
                                enabled = enabledSlots.size < OrbitNavSlot.MAX_SLOTS,
                                onCheckedChange = {
                                    haptics.performTap()
                                    onBottomNavSlotsChanged(enabledSlots + slot)
                                },
                                modifier = Modifier.testTag("settings_nav_toggle_${slot.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PixelHapticsSubPage(
    themeConfig: OrbitThemeConfig,
    onHapticsEnabledChanged: (Boolean) -> Unit,
    onHapticsStrengthChanged: (Float) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val strengthPercent = (themeConfig.hapticsStrength * 100f).roundToInt()
    val strengthLabel = when {
        strengthPercent < 35 -> "Soft"
        strengthPercent < 65 -> "Medium"
        strengthPercent < 88 -> "Standard"
        else -> "Strong"
    }

    PixelSettingsGroup {
        PixelSettingsRow(
            title = "Enable Haptics",
            subtitle = "Tactile feedback on buttons, long-press peeks, and feed scrolling.",
            shape = pixelSegmentedShape(0, 2),
            leadingContent = {
                PixelSettingsBadgeIcon(
                    icon = Icons.Outlined.Campaign,
                    tone = OrbitPixelBadgeTone.PINK
                )
            },
            trailingContent = {
                Switch(
                    checked = themeConfig.hapticsEnabled,
                    onCheckedChange = { enabled ->
                        onHapticsEnabledChanged(enabled)
                        if (enabled) haptics.performMedium()
                    },
                    modifier = Modifier.testTag("settings_haptics_switch")
                )
            }
        )
        Surface(
            shape = pixelSegmentedShape(1, 2),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Haptic Strength",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$strengthLabel ($strengthPercent%)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = themeConfig.hapticsStrength,
                    onValueChange = onHapticsStrengthChanged,
                    onValueChangeFinished = { haptics.performMedium() },
                    valueRange = 0.1f..1.0f,
                    enabled = themeConfig.hapticsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_haptics_slider")
                )
            }
        }
    }
}

@Composable
fun PixelRootShareAppCard(
    context: Context,
    myProfile: UserProfile,
    activeDownloadUrl: String,
    apkLinkCheckResult: OrbitMediaFiles.ApkLinkCheckResult?,
    isVerifyingApkLink: Boolean,
    isGeneratingApkLink: Boolean,
    generateLinkProgress: Float,
    generateLinkStatus: String,
    onVerifyOrRefreshLink: (
        forceRefresh: Boolean,
        onComplete: (OrbitMediaFiles.ApkLinkCheckResult, String) -> Unit
    ) -> Unit,
    onOpenFullShareOptions: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val isBusy = isGeneratingApkLink || isVerifyingApkLink
    val badgeText = when {
        isGeneratingApkLink -> "Uploading…"
        isVerifyingApkLink -> stringResource(R.string.settings_checking_link)
        apkLinkCheckResult?.isValid == true -> "Verified live"
        activeDownloadUrl.isNotBlank() -> "Check link"
        else -> "Open access"
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_root_share_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PixelSettingsBadgeIcon(
                    icon = Icons.Outlined.Public,
                    tone = OrbitPixelBadgeTone.MINT
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_share_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (apkLinkCheckResult?.isValid == true) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                if (apkLinkCheckResult?.isValid == true) {
                                    Icon(
                                        imageVector = Icons.Outlined.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (apkLinkCheckResult?.isValid == true) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = "Open download link so anyone can install Orbit v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !isBusy) { onOpenFullShareOptions() }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = when {
                            activeDownloadUrl.isNotBlank() -> activeDownloadUrl
                            isGeneratingApkLink -> generateLinkStatus.ifBlank { "Generating verified direct APK download link…" }
                            else -> "Tap Share or Check link to create a live APK download link"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("settings_root_share_url_text")
                    )
                    if (apkLinkCheckResult != null && !isGeneratingApkLink) {
                        Text(
                            text = apkLinkCheckResult.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (apkLinkCheckResult.isValid) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.testTag("settings_root_share_status_text")
                        )
                    }
                    if (isGeneratingApkLink) {
                        Spacer(Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { generateLinkProgress.coerceIn(0.05f, 0.98f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (isVerifyingApkLink) {
                        Spacer(Modifier.height(2.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        haptics.performMedium()
                        onVerifyOrRefreshLink(false) { _, url ->
                            if (url.isNotBlank()) {
                                val shareBody = OrbitMediaFiles.buildOpenInviteShareMessage(
                                    inviterUsername = myProfile.username,
                                    versionName = BuildConfig.VERSION_NAME,
                                    apkDownloadUrl = url
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Download Orbit (v${BuildConfig.VERSION_NAME})")
                                    putExtra(Intent.EXTRA_TEXT, shareBody)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Orbit download link"))
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Tap Invite Friends & Updates to send the APK file directly.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_root_share_link_btn")
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }

                OutlinedButton(
                    onClick = {
                        haptics.performTap()
                        onVerifyOrRefreshLink(false) { _, url ->
                            if (url.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Orbit APK Download Link", url))
                                Toast.makeText(context, "Verified download link copied", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not verify link right now", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("settings_root_copy_link_btn")
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy download link", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }

                FilledTonalButton(
                    onClick = {
                        haptics.performTap()
                        onVerifyOrRefreshLink(false) { result, _ ->
                            Toast.makeText(
                                context,
                                if (result.isValid) result.message else "Link check: ${result.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("settings_root_check_link_btn")
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isVerifyingApkLink) "…" else "Check")
                }
            }
        }
    }
}

@Composable
fun PixelUpdatesAndShareSubPage(
    context: Context,
    scope: CoroutineScope,
    myProfile: UserProfile,
    latestAppRelease: AppRelease?,
    hasNewerRelease: Boolean,
    activeDownloadUrl: String,
    apkLinkCheckResult: OrbitMediaFiles.ApkLinkCheckResult? = null,
    isVerifyingApkLink: Boolean = false,
    isGeneratingApkLink: Boolean,
    generateLinkProgress: Float,
    generateLinkStatus: String,
    isCheckingUpdate: Boolean,
    isDownloadingDriveApk: Boolean,
    driveDownloadProgress: Float,
    driveDownloadStatus: String?,
    onVerifyOrRefreshLink: (
        forceRefresh: Boolean,
        onComplete: (OrbitMediaFiles.ApkLinkCheckResult, String) -> Unit
    ) -> Unit = { _, _ -> },
    onHostedUrlUpdated: (String) -> Unit,
    onGenerateProgressChanged: (Boolean, Float, String) -> Unit,
    onSaveDriveDownloadUrl: (String) -> Unit,
    onCheckForUpdate: () -> Unit,
    onDownloadFromDrive: (String) -> Unit,
    onOpenNotifyDialog: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val integrityReport = remember(context) { OrbitMediaFiles.inspectInstalledApkIntegrity(context) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Invite Friends & Direct APK Link Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_open_invite_card")
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PixelSettingsBadgeIcon(
                        icon = Icons.Outlined.Public,
                        tone = OrbitPixelBadgeTone.MINT
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Invite Friends · Direct App Download",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Share a verified direct APK link or send the standalone APK file directly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!integrityReport.isShareable && integrityReport.reason.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = integrityReport.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = when {
                                activeDownloadUrl.isNotBlank() -> activeDownloadUrl
                                isGeneratingApkLink -> generateLinkStatus.ifBlank { "Verifying & generating direct APK download link…" }
                                else -> "Tap 'Invite Friends' or 'Check link' to generate a verified APK link"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("settings_open_invite_url_text")
                        )
                        if (apkLinkCheckResult != null && !isGeneratingApkLink) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (apkLinkCheckResult.isValid) {
                                    Icon(
                                        imageVector = Icons.Outlined.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = apkLinkCheckResult.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (apkLinkCheckResult.isValid) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    modifier = Modifier.testTag("settings_open_invite_status_text")
                                )
                            }
                        }
                        if (isGeneratingApkLink) {
                            Spacer(Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { generateLinkProgress.coerceIn(0.05f, 0.98f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (isVerifyingApkLink) {
                            Spacer(Modifier.height(2.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptics.performTap()
                        onVerifyOrRefreshLink(false) { result, _ ->
                            Toast.makeText(
                                context,
                                if (result.isValid) result.message else "Link check: ${result.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = !isGeneratingApkLink && !isVerifyingApkLink,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_check_download_link_btn")
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isVerifyingApkLink) {
                            stringResource(R.string.settings_checking_link)
                        } else {
                            stringResource(R.string.settings_check_link)
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            haptics.performMedium()
                            scope.launch {
                                val resolvedUrl = if (activeDownloadUrl.isNotBlank() &&
                                    OrbitMediaFiles.verifyDirectApkDownloadUrl(activeDownloadUrl)
                                ) {
                                    activeDownloadUrl
                                } else {
                                    onGenerateProgressChanged(true, 0.05f, "Generating verified APK link…")
                                    val res = OrbitMediaFiles.ensureHostedApkDownloadUrl(
                                        context = context,
                                        existingReleaseUrl = latestAppRelease?.driveDownloadUrl,
                                        existingReleaseVersionCode = latestAppRelease?.versionCode,
                                        forceRefresh = true
                                    ) { pct, status ->
                                        onGenerateProgressChanged(true, pct, status)
                                    }
                                    res.onSuccess { url ->
                                        onGenerateProgressChanged(false, 1f, "Ready")
                                        onHostedUrlUpdated(url)
                                        onSaveDriveDownloadUrl(url)
                                    }.onFailure { err ->
                                        onGenerateProgressChanged(false, 0f, err.message ?: "")
                                    }.getOrDefault("")
                                }

                                if (resolvedUrl.isNotBlank()) {
                                    val shareBody = OrbitMediaFiles.buildOpenInviteShareMessage(
                                        inviterUsername = myProfile.username,
                                        versionName = BuildConfig.VERSION_NAME,
                                        apkDownloadUrl = resolvedUrl
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Download Orbit (v${BuildConfig.VERSION_NAME})")
                                        putExtra(Intent.EXTRA_TEXT, shareBody)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(shareIntent, "Invite to Orbit"))
                                    }
                                } else {
                                    val apkUri = OrbitMediaFiles.prepareShareableInstalledApkUri(context)
                                    val shareBody = OrbitMediaFiles.buildOpenInviteShareMessage(
                                        inviterUsername = myProfile.username,
                                        versionName = BuildConfig.VERSION_NAME,
                                        apkDownloadUrl = ""
                                    )
                                    val shareIntent = if (apkUri != null) {
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "application/vnd.android.package-archive"
                                            putExtra(Intent.EXTRA_STREAM, apkUri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Install Orbit (v${BuildConfig.VERSION_NAME})")
                                            putExtra(Intent.EXTRA_TEXT, shareBody)
                                            clipData = ClipData.newUri(context.contentResolver, "Orbit APK", apkUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    } else {
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Join me on Orbit")
                                            putExtra(Intent.EXTRA_TEXT, shareBody)
                                        }
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(shareIntent, "Invite to Orbit"))
                                    }
                                }
                            }
                        },
                        enabled = !isGeneratingApkLink,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_share_app_btn")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isGeneratingApkLink) "Preparing…" else "Invite Friends")
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.performTap()
                            scope.launch {
                                val resolvedUrl = if (activeDownloadUrl.isNotBlank() &&
                                    OrbitMediaFiles.verifyDirectApkDownloadUrl(activeDownloadUrl)
                                ) {
                                    activeDownloadUrl
                                } else {
                                    onGenerateProgressChanged(true, 0.05f, "Generating verified APK link…")
                                    val res = OrbitMediaFiles.ensureHostedApkDownloadUrl(
                                        context = context,
                                        existingReleaseUrl = latestAppRelease?.driveDownloadUrl,
                                        existingReleaseVersionCode = latestAppRelease?.versionCode,
                                        forceRefresh = true
                                    ) { pct, status ->
                                        onGenerateProgressChanged(true, pct, status)
                                    }
                                    res.onSuccess { url ->
                                        onGenerateProgressChanged(false, 1f, "Ready")
                                        onHostedUrlUpdated(url)
                                        onSaveDriveDownloadUrl(url)
                                    }.onFailure { err ->
                                        onGenerateProgressChanged(false, 0f, err.message ?: "")
                                    }.getOrDefault("")
                                }
                                if (resolvedUrl.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Orbit APK Download Link", resolvedUrl))
                                    Toast.makeText(context, "Verified download link copied", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        integrityReport.reason.ifBlank { "Could not generate link right now. Use 'Send APK File' below." },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !isGeneratingApkLink,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("settings_copy_app_link_btn")
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Orbit download link", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy link")
                    }
                }

                FilledTonalButton(
                    onClick = {
                        haptics.performMedium()
                        scope.launch {
                            val apkUri = OrbitMediaFiles.prepareShareableInstalledApkUri(context)
                            if (apkUri != null) {
                                val shareBody = OrbitMediaFiles.buildOpenInviteShareMessage(
                                    inviterUsername = myProfile.username,
                                    versionName = BuildConfig.VERSION_NAME,
                                    apkDownloadUrl = activeDownloadUrl
                                )
                                val sendApkIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.android.package-archive"
                                    putExtra(Intent.EXTRA_STREAM, apkUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Orbit-v${BuildConfig.VERSION_NAME}.apk")
                                    putExtra(Intent.EXTRA_TEXT, shareBody)
                                    clipData = ClipData.newUri(context.contentResolver, "Orbit-v${BuildConfig.VERSION_NAME}.apk", apkUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(sendApkIntent, "Send Orbit APK"))
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    integrityReport.reason.ifBlank { "Could not prepare APK file for sharing." },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_send_apk_file_btn")
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Orbit APK File Directly (v${BuildConfig.VERSION_NAME})")
                }

                Text(
                    text = "Install & Play Store tip: Every link is verified to return an Android APK binary. If Google Play shows \"Can't update Orbit\" or Android shows \"App not installed\", uninstall any older Orbit copy signed with a different key once, then tap Install — all your posts, profile, DMs, and Spaces remain safely synced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // App Updates Card Group
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Orbit v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Build ${BuildConfig.VERSION_CODE}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            haptics.performTap()
                            onCheckForUpdate()
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.testTag("settings_check_update_btn")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (isCheckingUpdate) "Checking…" else "Check now")
                    }
                }

                if (isCheckingUpdate) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_update_check_progress")
                        )
                        Text(
                            text = "Checking for the latest Orbit release…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (latestAppRelease != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (hasNewerRelease) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (hasNewerRelease) {
                                    "New release available: v${latestAppRelease.versionName} (Build ${latestAppRelease.versionCode})"
                                } else {
                                    "Latest published release: v${latestAppRelease.versionName}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasNewerRelease) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (latestAppRelease.releaseNotes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = latestAppRelease.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasNewerRelease) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        haptics.performMedium()
                        onDownloadFromDrive(activeDownloadUrl)
                    },
                    enabled = !isDownloadingDriveApk && !isGeneratingApkLink,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_download_from_drive_btn")
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isDownloadingDriveApk) {
                            "Downloading APK… ${(driveDownloadProgress * 100f).roundToInt()}%"
                        } else {
                            "Download Latest Orbit APK (v${latestAppRelease?.versionName?.ifBlank { BuildConfig.VERSION_NAME } ?: BuildConfig.VERSION_NAME})"
                        }
                    )
                }

                if (isDownloadingDriveApk || !driveDownloadStatus.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isDownloadingDriveApk) {
                            if (driveDownloadProgress > 0f) {
                                LinearProgressIndicator(
                                    progress = { driveDownloadProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_drive_download_progress")
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_drive_download_progress")
                                )
                            }
                        }
                        if (!driveDownloadStatus.isNullOrBlank()) {
                            Text(
                                text = driveDownloadStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedButton(
                    onClick = {
                        haptics.performTap()
                        onOpenNotifyDialog()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_notify_users_update_btn")
                ) {
                    Icon(Icons.Outlined.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Publish & notify users of update")
                }
            }
        }
    }
}

@Composable
fun PixelAboutSubPage(
    feedCacheStatus: FeedCacheStatus = FeedCacheStatus(),
    onClearOfflineFeedCache: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val buildChannel = stringResource(R.string.settings_about_beta_badge)
    val deviceName = remember {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
    }
    val buildInfoSummary = remember(deviceName, buildChannel) {
        buildString {
            appendLine("Orbit v${BuildConfig.VERSION_NAME} ($buildChannel)")
            appendLine("Build: ${BuildConfig.VERSION_CODE} (${BuildConfig.BUILD_TYPE})")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            if (deviceName.isNotBlank()) {
                append("Device: $deviceName")
            }
        }
    }
    val cacheLayerBadge = when (feedCacheStatus.source) {
        FeedDataSource.MEMORY_L1 -> "L1 Memory"
        FeedDataSource.DATASTORE_L2 -> "L2 DataStore"
        FeedDataSource.NETWORK_L3 -> "L3 Live Sync"
        FeedDataSource.NONE -> if (feedCacheStatus.cachedPostCount > 0) "L2 DataStore" else "Idle"
    }
    val formattedCacheSize = remember(feedCacheStatus.approxSizeBytes) {
        OrbitMediaFiles.formatFileSize(feedCacheStatus.approxSizeBytes.coerceAtLeast(0L))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.testTag("settings_about_subpage")
    ) {
        // Hero App Identity & Beta Badge Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                PixelSettingsBadgeIcon(
                    icon = Icons.Outlined.Info,
                    tone = OrbitPixelBadgeTone.PERIWINKLE
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.testTag("settings_about_beta_pill")
                        ) {
                            Text(
                                text = buildChannel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("settings_about_version_summary")
                    )
                }
            }
        }

        // Multi-Layer Offline Feed Cache (L1 Memory + L2 DataStore + L3 Firestore) Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_offline_cache_card")
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PixelSettingsBadgeIcon(
                        icon = if (feedCacheStatus.isOffline) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                        tone = OrbitPixelBadgeTone.MINT
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_offline_cache_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = cacheLayerBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (feedCacheStatus.cachedPostCount > 0) {
                                stringResource(
                                    R.string.settings_offline_cache_summary,
                                    feedCacheStatus.cachedPostCount,
                                    formattedCacheSize
                                )
                            } else {
                                stringResource(R.string.settings_offline_cache_empty)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        haptics.performMedium()
                        onClearOfflineFeedCache()
                    },
                    enabled = feedCacheStatus.cachedPostCount > 0,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("settings_clear_offline_cache_btn")
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_offline_cache_clear))
                }
            }
        }

        // Segmented Pixel Build Info Rows
        PixelSettingsGroup {
            PixelAboutInfoRow(
                label = "Version",
                value = "v${BuildConfig.VERSION_NAME}",
                badgeText = buildChannel,
                shape = pixelSegmentedShape(0, 5)
            )
            PixelAboutInfoRow(
                label = "Build number",
                value = "${BuildConfig.VERSION_CODE} (${BuildConfig.BUILD_TYPE})",
                shape = pixelSegmentedShape(1, 5)
            )
            PixelAboutInfoRow(
                label = "Application ID",
                value = BuildConfig.APPLICATION_ID,
                shape = pixelSegmentedShape(2, 5)
            )
            PixelAboutInfoRow(
                label = "Android OS",
                value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                shape = pixelSegmentedShape(3, 5)
            )
            PixelAboutInfoRow(
                label = "Device",
                value = deviceName.ifBlank { "Android device" },
                shape = pixelSegmentedShape(4, 5)
            )
        }

        FilledTonalButton(
            onClick = {
                haptics.performTap()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Orbit Build Info", buildInfoSummary))
                Toast.makeText(context, "Build info copied", Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_about_copy_build_btn")
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Copy build info")
        }
    }
}

@Composable
private fun PixelAboutInfoRow(
    label: String,
    value: String,
    shape: RoundedCornerShape,
    badgeText: String? = null
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Settings subpage allowing the user to select specific people and send them
 * a verified direct download link or standalone installable Orbit APK file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PixelShareAppWithPeopleSubPage(
    myProfile: UserProfile,
    allProfiles: List<UserProfile>,
    resolvedApkUrl: String,
    apkLinkCheckResult: com.example.data.cache.OrbitMediaFiles.ApkLinkCheckResult?,
    isCheckingApkLink: Boolean,
    isPreparingHostedLink: Boolean,
    hostedLinkProgress: Float,
    hostedLinkStatus: String?,
    isSharingApkFile: Boolean,
    onVerifyOrGenerateLink: () -> Unit,
    onSendSelectiveInviteLink: (List<String>) -> Unit,
    onSendSelectiveApkFile: (List<String>) -> Unit,
    onCopySelectiveInviteLink: (List<String>) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var recipientInput by remember { mutableStateOf("") }
    var selectedRecipients by remember { mutableStateOf(listOf<String>()) }

    val suggestedPeople = remember(allProfiles, myProfile.resolvedId) {
        allProfiles
            .filter { it.resolvedId.isNotBlank() && it.resolvedId != myProfile.resolvedId }
            .distinctBy { it.resolvedId }
            .take(12)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.testTag("settings_share_access_subpage")
    ) {
        // Step 1: Select specific people to share Orbit with
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSettingsBadgeIcon(
                        icon = Icons.Outlined.PersonAdd,
                        tone = OrbitPixelBadgeTone.MINT
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Choose who can download Orbit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Pick specific people or add their name, @handle, phone, or email to send a personal download pass.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = recipientInput,
                        onValueChange = { if (it.length <= 64) recipientInput = it },
                        placeholder = { Text("Name, @handle, phone, or email…") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_access_recipient_input")
                    )
                    FilledTonalButton(
                        onClick = {
                            val clean = recipientInput.trim()
                            if (clean.isNotBlank() && clean !in selectedRecipients) {
                                haptics.performTap()
                                selectedRecipients = selectedRecipients + clean
                                recipientInput = ""
                            }
                        },
                        enabled = recipientInput.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("share_access_add_recipient_btn")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add person", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                if (suggestedPeople.isNotEmpty()) {
                    Text(
                        text = "Quick-select from your Orbit contacts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestedPeople.forEach { person ->
                            val label = person.username.takeIf { it.isNotBlank() }?.let { "@$it" }
                                ?: person.displayName.ifBlank { "Member" }
                            val isSelected = label in selectedRecipients
                            InputChip(
                                selected = isSelected,
                                onClick = {
                                    haptics.performTap()
                                    selectedRecipients = if (isSelected) {
                                        selectedRecipients - label
                                    } else {
                                        selectedRecipients + label
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("share_access_contact_chip_${person.resolvedId}")
                            )
                        }
                    }
                }

                if (selectedRecipients.isNotEmpty()) {
                    HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Selected people (${selectedRecipients.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedRecipients.forEachIndexed { idx, person ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    haptics.performTap()
                                    selectedRecipients = selectedRecipients - person
                                },
                                label = { Text(person) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove $person",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("share_access_selected_chip_$idx")
                            )
                        }
                    }
                }
            }
        }

        // Step 2: Live Download Link Status & Verification + Share Buttons
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSettingsBadgeIcon(
                        icon = Icons.Outlined.Verified,
                        tone = OrbitPixelBadgeTone.SKY_BLUE
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Orbit v${BuildConfig.VERSION_NAME} (Beta) Installer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val statusSubtitle = when {
                            isPreparingHostedLink -> hostedLinkStatus ?: "Preparing direct download link…"
                            isCheckingApkLink -> "Testing & verifying download link…"
                            apkLinkCheckResult?.isValid == true -> apkLinkCheckResult.message
                            resolvedApkUrl.isNotBlank() -> "Direct APK link ready (${resolvedApkUrl.take(42)}…)"
                            else -> "Tap below to generate a verified download link or send the standalone APK file directly."
                        }
                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (apkLinkCheckResult?.isValid == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.testTag("share_access_link_status")
                        )
                    }
                }

                if (isPreparingHostedLink || isCheckingApkLink) {
                    if (isPreparingHostedLink && hostedLinkProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { hostedLinkProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                val recipientSummary = if (selectedRecipients.isEmpty()) {
                    "Selected People"
                } else if (selectedRecipients.size == 1) {
                    selectedRecipients.first()
                } else {
                    "${selectedRecipients.size} Selected People"
                }

                Button(
                    onClick = {
                        haptics.performMedium()
                        onSendSelectiveInviteLink(selectedRecipients)
                    },
                    enabled = !isPreparingHostedLink,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_access_send_invite_btn")
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Download Link to $recipientSummary")
                }

                FilledTonalButton(
                    onClick = {
                        haptics.performMedium()
                        onSendSelectiveApkFile(selectedRecipients)
                    },
                    enabled = !isSharingApkFile,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_access_send_apk_file_btn")
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isSharingApkFile) {
                            "Preparing Orbit-v${BuildConfig.VERSION_NAME}.apk…"
                        } else {
                            "Send Standalone APK File to $recipientSummary"
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.performTap()
                            onCopySelectiveInviteLink(selectedRecipients)
                        },
                        enabled = !isPreparingHostedLink,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_access_copy_link_btn")
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Invite")
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.performTap()
                            onVerifyOrGenerateLink()
                        },
                        enabled = !isPreparingHostedLink && !isCheckingApkLink,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_access_verify_link_btn")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Link")
                    }
                }
            }
        }
    }
}

