package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Settings
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.model.Community
import com.example.data.model.UserProfile
import com.example.ui.components.CollapsibleSectionCard
import com.example.ui.components.ImageCropperDialog
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.UserSearchDropdownField
import com.example.ui.haptics.LocalOrbitHaptics
import java.util.concurrent.TimeUnit

/**
 * Full-screen Space Settings dialog supporting Admin-only configuration (name, banner crop,
 * about, privacy mode, delete space) and Admin/Mod governance (join requests, invites,
 * banned users, and paused/muted users).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceSettingsDialog(
    space: Community,
    allProfiles: List<UserProfile>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onSaveAdminSettings: (name: String, description: String, coverUrl: String, privacyMode: String, onDone: () -> Unit) -> Unit,
    onDeleteSpace: (onDeleted: () -> Unit) -> Unit,
    onApproveRequest: (String) -> Unit,
    onDeclineRequest: (String) -> Unit,
    onInviteUser: (String) -> Unit,
    onUnbanUser: (String) -> Unit,
    onUnmuteUser: (String) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    val isAdmin = space.isAdmin(currentUserId)
    val profilesById = remember(allProfiles) { allProfiles.associateBy { it.resolvedId } }

    var spaceName by remember(space.name) { mutableStateOf(space.name) }
    var spaceDescription by remember(space.description) { mutableStateOf(space.description) }
    var coverUrl by remember(space.coverUrl) { mutableStateOf(space.coverUrl) }
    var privacyMode by remember(space.privacyMode) { mutableStateOf(space.privacyMode.ifBlank { "public" }) }
    var pendingCropBannerUri by remember { mutableStateOf<Uri?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var inviteSearchQuery by remember { mutableStateOf("") }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pendingCropBannerUri = uri
    }

    val nowMillis = System.currentTimeMillis()
    val activeMutedUsers = remember(space.mutedUntilMap, nowMillis) {
        space.mutedUntilMap.filter { (_, until) -> until > nowMillis }.entries.toList()
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
                        Column {
                            Text(
                                text = "Space Settings",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAdmin) "Admin controls" else "Moderator controls",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptics.performTap()
                                onDismiss()
                            },
                            modifier = Modifier.testTag("space_settings_close_btn")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Space Settings")
                        }
                    },
                    actions = {
                        if (isAdmin) {
                            TextButton(
                                onClick = {
                                    haptics.performMedium()
                                    onSaveAdminSettings(
                                        spaceName.trim(),
                                        spaceDescription.trim(),
                                        coverUrl,
                                        privacyMode
                                    ) {
                                        onDismiss()
                                    }
                                },
                                enabled = spaceName.trim().length >= 2,
                                modifier = Modifier.testTag("space_settings_save_btn")
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // ==================== ADMIN-ONLY SECTION (COLLAPSIBLE) ====================
                if (isAdmin) {
                    CollapsibleSectionCard(
                        title = "Space Identity (Admin Only)",
                        subtitle = "Edit name, banner, about section & privacy",
                        icon = Icons.Outlined.Settings,
                        initiallyExpanded = false,
                        testTag = "space_settings_identity_section"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                            // Banner preview + crop trigger
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(126.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        haptics.performTap()
                                        bannerPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .testTag("space_settings_banner_picker")
                            ) {
                                val model = remember(coverUrl) { OrbitCacheManager.toCoilModel(coverUrl) }
                                if (model != null) {
                                    AsyncImage(
                                        model = model,
                                        imageLoader = imageLoader,
                                        contentDescription = "Space banner preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.Black.copy(alpha = 0.62f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Crop,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Edit & crop banner",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = spaceName,
                                onValueChange = { if (it.length <= 48) spaceName = it },
                                label = { Text("Space name") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("space_settings_name_input")
                            )

                            OutlinedTextField(
                                value = spaceDescription,
                                onValueChange = { if (it.length <= 280) spaceDescription = it },
                                label = { Text("About this Space") },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("space_settings_about_input")
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Privacy",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = privacyMode == "public",
                                        onClick = {
                                            haptics.performTap()
                                            privacyMode = "public"
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        icon = {
                                            Icon(
                                                Icons.Outlined.Public,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("space_privacy_public_btn")
                                    ) {
                                        Text("Public")
                                    }
                                    SegmentedButton(
                                        selected = privacyMode == "private",
                                        onClick = {
                                            haptics.performTap()
                                            privacyMode = "private"
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        icon = {
                                            Icon(
                                                Icons.Outlined.Lock,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("space_privacy_private_btn")
                                    ) {
                                        Text("Private")
                                    }
                                }
                                Text(
                                    text = if (privacyMode == "private") {
                                        "Private: Users must be invited or request to join (approved by Admin or Mods)."
                                    } else {
                                        "Public: Anyone on Orbit can view posts and join immediately."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "You are a Moderator. You can manage join requests, invite users, unban/unpause members, and moderate posts. Only the Admin can edit the Space name, banner, about section, privacy, or delete the Space.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // ==================== PENDING JOIN REQUESTS (COLLAPSIBLE) ====================
                if (space.pendingRequestIds.isNotEmpty() || space.isPrivateSpace) {
                    CollapsibleSectionCard(
                        title = "Join Requests (${space.pendingRequestIds.size})",
                        subtitle = if (space.pendingRequestIds.isEmpty()) "No pending requests" else "Approve or decline requests",
                        icon = Icons.Outlined.HowToReg,
                        badgeText = if (space.pendingRequestIds.isNotEmpty()) "${space.pendingRequestIds.size}" else null,
                        initiallyExpanded = false,
                        testTag = "space_settings_requests_section"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (space.pendingRequestIds.isEmpty()) {
                                Text(
                                    text = "No pending requests to join this Space.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                space.pendingRequestIds.forEach { uid ->
                                    val profile = profilesById[uid]
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        ThreadAvatar(
                                            avatarUrl = profile?.avatarUrl.orEmpty(),
                                            displayName = profile?.displayName ?: "Member",
                                            size = 38.dp
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = profile?.displayName?.ifBlank { profile.username } ?: "Orbit User",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "@${profile?.username ?: uid.take(8)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                haptics.performTap()
                                                onDeclineRequest(uid)
                                            },
                                            modifier = Modifier.testTag("decline_request_$uid")
                                        ) {
                                            Text("Decline")
                                        }
                                        Button(
                                            onClick = {
                                                haptics.performMedium()
                                                onApproveRequest(uid)
                                            },
                                            modifier = Modifier.testTag("approve_request_$uid")
                                        ) {
                                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Approve")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== INVITE USERS (COLLAPSIBLE + SEARCH DROPDOWN ONLY) ====================
                CollapsibleSectionCard(
                    title = "Invite Users",
                    subtitle = "Type a name or @username to invite",
                    icon = Icons.Outlined.PersonAdd,
                    initiallyExpanded = false,
                    testTag = "space_settings_invite_section"
                ) {
                    UserSearchDropdownField(
                        query = inviteSearchQuery,
                        onQueryChange = { inviteSearchQuery = it },
                        allUsers = allProfiles,
                        excludedUserIds = (space.memberIds + space.bannedUserIds + space.invitedUserIds).toSet(),
                        placeholder = "Type a name or @username…",
                        actionLabel = "Invite",
                        testTag = "space_invite_search_input",
                        onSelectUser = { user ->
                            onInviteUser(user.resolvedId)
                        }
                    )
                }

                // ==================== BANNED & PAUSED MEMBERS (COLLAPSIBLE) ====================
                CollapsibleSectionCard(
                    title = "Banned (${space.bannedUserIds.size}) & Paused (${activeMutedUsers.size})",
                    subtitle = "Manage restricted members",
                    icon = Icons.Outlined.Block,
                    iconTint = MaterialTheme.colorScheme.error,
                    initiallyExpanded = false,
                    testTag = "space_settings_moderation_section"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                        if (space.bannedUserIds.isEmpty() && activeMutedUsers.isEmpty()) {
                            Text(
                                text = "No banned or paused members.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        space.bannedUserIds.forEach { uid ->
                            val profile = profilesById[uid]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ThreadAvatar(
                                    avatarUrl = profile?.avatarUrl.orEmpty(),
                                    displayName = profile?.displayName ?: "User",
                                    size = 36.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile?.displayName?.ifBlank { profile.username } ?: "Banned User",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Banned from Space",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        haptics.performMedium()
                                        onUnbanUser(uid)
                                    },
                                    modifier = Modifier.testTag("space_unban_user_$uid")
                                ) {
                                    Text("Unban")
                                }
                            }
                        }

                        if (space.bannedUserIds.isNotEmpty() && activeMutedUsers.isNotEmpty()) {
                            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        activeMutedUsers.forEach { (uid, untilMillis) ->
                            val profile = profilesById[uid]
                            val hrsLeft = TimeUnit.MILLISECONDS.toHours((untilMillis - nowMillis).coerceAtLeast(0L))
                                .coerceAtLeast(1L)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile?.displayName?.ifBlank { profile.username } ?: "Member",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Posting paused · ~${hrsLeft}h remaining",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        haptics.performTap()
                                        onUnmuteUser(uid)
                                    },
                                    modifier = Modifier.testTag("space_unmute_user_$uid")
                                ) {
                                    Text("Unpause")
                                }
                            }
                        }
                    }
                }

                // ==================== DANGER ZONE (ADMIN ONLY — LIFTED ABOVE SYSTEM HOME BAR) ====================
                if (isAdmin) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            haptics.performHeavy()
                            showDeleteConfirm = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("space_settings_delete_space_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Space", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Generous clearance above the Android gesture / 3-button home bar
                Spacer(Modifier.height(72.dp))
            }
        }
    }

    pendingCropBannerUri?.let { uri ->
        ImageCropperDialog(
            sourceUri = uri,
            isBanner = true,
            onDismiss = { pendingCropBannerUri = null },
            onCropped = { croppedDataUri ->
                coverUrl = croppedDataUri
                pendingCropBannerUri = null
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${space.name}?") },
            text = {
                Text("This permanently deletes this Space for all members. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.performHeavy()
                        showDeleteConfirm = false
                        onDeleteSpace { onDismiss() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_space_btn")
                ) {
                    Text("Delete Space")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
