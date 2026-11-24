package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ForwardToInbox
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.cache.OrbitMediaFiles
import com.example.data.model.Conversation
import com.example.data.model.DM_REACTION_EMOJIS
import com.example.data.model.DirectMessage
import com.example.data.model.DmReactionChipSummary
import com.example.data.model.PostAttachment
import com.example.data.model.SeenReceiptInfo
import com.example.data.model.UserProfile
import com.example.data.model.formatCompactCount
import com.example.data.model.formatRelativeTime
import com.example.data.model.isGifUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.components.CenteredThreadsContainer
import com.example.ui.components.ConversationAvatar
import com.example.ui.components.GifCornerBadge
import com.example.ui.components.GroupConversationAvatar
import com.example.ui.components.LocalPhotoPeekController
import com.example.ui.components.OrbitVideoPlayerDialog
import com.example.ui.components.PhotoPeekRequest
import com.example.ui.components.PostAttachmentCard
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.UploadedAttachmentPreviewCard
import com.example.ui.components.UploadedPhotoPreviewTile
import com.example.ui.components.UserSearchDropdownField
import com.example.ui.components.filterSuggestedUsers
import com.example.ui.components.rememberKeyboardMediaReceiver
import com.example.ui.haptics.LocalOrbitHaptics

/**
 * Twitter/X-inspired Direct Messages Inbox Screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesInboxScreen(
    conversations: List<Conversation>,
    allProfiles: List<UserProfile>,
    currentUserId: String,
    selectedConversationId: String? = null,
    onBack: (() -> Unit)? = null,
    onMarkRead: () -> Unit = {},
    onOpenConversation: (Conversation) -> Unit,
    onStartChatWithUser: (UserProfile) -> Unit,
    onStartGroupChat: (List<UserProfile>, String) -> Unit = { users, _ ->
        users.firstOrNull()?.let(onStartChatWithUser)
    },
    onDeleteConversation: (Conversation) -> Unit = {}
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    LaunchedEffect(conversations.size) {
        onMarkRead()
    }

    var searchQuery by remember { mutableStateOf("") }
    var showNewMessageSheet by remember { mutableStateOf(false) }
    var conversationPendingDelete by remember { mutableStateOf<Conversation?>(null) }

    val searchableProfiles = remember(allProfiles, conversations, currentUserId) {
        val byId = LinkedHashMap<String, UserProfile>()
        allProfiles.forEach { profile ->
            val uid = profile.resolvedId
            if (uid.isNotBlank() && uid != currentUserId) {
                byId[uid] = profile
            }
        }
        conversations.forEach { conv ->
            conv.otherParticipantIds(currentUserId).forEach { peerId ->
                if (peerId.isNotBlank() && peerId != currentUserId && peerId !in byId) {
                    byId[peerId] = UserProfile(
                        id = peerId,
                        userId = peerId,
                        username = conv.participantUsername(peerId),
                        displayName = conv.participantDisplayName(peerId),
                        avatarUrl = conv.participantAvatarUrl(peerId)
                    )
                }
            }
        }
        byId.values.toList()
    }

    val normalizedQuery = searchQuery.trim().removePrefix("@").lowercase()
    val hasSuggestedUsers = remember(normalizedQuery, searchableProfiles, currentUserId) {
        normalizedQuery.isNotEmpty() && filterSuggestedUsers(
            query = normalizedQuery,
            allUsers = searchableProfiles,
            excludedUserIds = setOf(currentUserId)
        ).isNotEmpty()
    }

    val filteredConversations = remember(conversations, normalizedQuery, currentUserId) {
        if (normalizedQuery.isEmpty()) {
            conversations
        } else {
            conversations.filter { conv ->
                conv.displayTitle(currentUserId).lowercase().contains(normalizedQuery) ||
                    conv.groupTitle.lowercase().contains(normalizedQuery) ||
                    conv.lastMessageText.lowercase().contains(normalizedQuery) ||
                    conv.otherParticipantIds(currentUserId).any { pid ->
                        conv.participantDisplayName(pid).lowercase().contains(normalizedQuery) ||
                            conv.participantUsername(pid).lowercase().contains(normalizedQuery)
                    }
            }
        }
    }

    CenteredThreadsContainer {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.messages_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("messages_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showNewMessageSheet = true },
                            modifier = Modifier.testTag("messages_new_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ForwardToInbox,
                                contentDescription = stringResource(R.string.messages_new)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showNewMessageSheet = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("messages_new_fab")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MailOutline,
                        contentDescription = stringResource(R.string.messages_new)
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Unified suggested-users dropdown search bar (same logic as Spaces invite)
                UserSearchDropdownField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    allUsers = searchableProfiles,
                    excludedUserIds = setOf(currentUserId),
                    placeholder = stringResource(R.string.messages_search_hint),
                    actionLabel = "Message",
                    testTag = "messages_search_input",
                    clearOnSelect = true,
                    onSelectUser = { user ->
                        val existing = conversations.firstOrNull { conv ->
                            !conv.isGroupChat && conv.peerId(currentUserId) == user.resolvedId
                        }
                        if (existing != null) {
                            onOpenConversation(existing)
                        } else {
                            onStartChatWithUser(user)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                HorizontalDivider(
                    thickness = 0.6.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )

                if (filteredConversations.isEmpty()) {
                    if (normalizedQuery.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Welcome to your inbox!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Drop a line, share photos, and start private 1-on-1 or group conversations with anyone on Orbit.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(22.dp))
                                Button(
                                    onClick = { showNewMessageSheet = true },
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                    modifier = Modifier.testTag("messages_write_cta_button")
                                ) {
                                    Text(
                                        text = "Write a message",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                            Text(
                                text = if (hasSuggestedUsers) {
                                    "No existing conversations yet — select a suggested user above to start messaging."
                                } else {
                                    "No matching conversations for \"$normalizedQuery\"."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 84.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (normalizedQuery.isNotEmpty()) {
                            item(key = "dm_search_conversations_header") {
                                Text(
                                    text = "Existing conversations",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                        items(
                            items = filteredConversations,
                            key = { it.id }
                        ) { conversation ->
                            XConversationRow(
                                conversation = conversation,
                                currentUserId = currentUserId,
                                isSelected = selectedConversationId == conversation.id,
                                onClick = { onOpenConversation(conversation) },
                                onLongClick = { conversationPendingDelete = conversation }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewMessageSheet) {
        NewMessageUserPickerSheet(
            profiles = searchableProfiles,
            onDismiss = { showNewMessageSheet = false },
            onStartConversation = { selectedUsers, groupTitle ->
                showNewMessageSheet = false
                if (selectedUsers.size == 1) {
                    val user = selectedUsers.first()
                    val existing = conversations.firstOrNull { conv ->
                        !conv.isGroupChat && conv.peerId(currentUserId) == user.resolvedId
                    }
                    if (existing != null) {
                        onOpenConversation(existing)
                    } else {
                        onStartChatWithUser(user)
                    }
                } else if (selectedUsers.size >= 2) {
                    onStartGroupChat(selectedUsers, groupTitle)
                }
            }
        )
    }

    conversationPendingDelete?.let { conv ->
        val deleteMessage = if (conv.isGroupChat) {
            "Remove the group chat \"${conv.displayTitle(currentUserId)}\"? This cannot be undone."
        } else {
            "Remove your DM thread with ${conv.displayTitle(currentUserId)}? This cannot be undone."
        }
        AlertDialog(
            onDismissRequest = { conversationPendingDelete = null },
            title = { Text("Delete conversation?") },
            text = { Text(deleteMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        conversationPendingDelete = null
                        onDeleteConversation(conv)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_conversation_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun XConversationRow(
    conversation: Conversation,
    currentUserId: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val haptics = LocalOrbitHaptics.current
    val displayTitle = conversation.displayTitle(currentUserId)
    val displaySubtitle = conversation.displaySubtitle(currentUserId)
    val sentByMe = conversation.lastMessageSenderId == currentUserId
    val isLastMessageSeen = conversation.isLastMessageSeenByPeer(currentUserId)
    val previewText = when {
        conversation.lastMessageText.isBlank() -> "Start the conversation"
        sentByMe -> "You: ${conversation.lastMessageText}"
        conversation.isGroupChat && conversation.lastMessageSenderId.isNotBlank() ->
            "${conversation.lastMessageSenderLabel(currentUserId)}: ${conversation.lastMessageText}"
        else -> conversation.lastMessageText
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHeavy()
                    onLongClick()
                }
            )
            .testTag("dm_conversation_row_${conversation.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            ConversationAvatar(
                conversation = conversation,
                currentUserId = currentUserId,
                size = 48.dp,
                borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = displaySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "· ${formatRelativeTime(conversation.lastMessageAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (sentByMe && conversation.lastMessageText.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isLastMessageSeen) Icons.Filled.DoneAll else Icons.Filled.Done,
                            contentDescription = if (isLastMessageSeen) {
                                stringResource(R.string.dm_status_seen)
                            } else {
                                stringResource(R.string.dm_status_sent)
                            },
                            tint = if (isLastMessageSeen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        if (isLastMessageSeen) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.dm_status_seen),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("dm_inbox_seen_${conversation.id}")
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewMessageUserPickerSheet(
    profiles: List<UserProfile>,
    onDismiss: () -> Unit,
    onStartConversation: (selectedUsers: List<UserProfile>, groupTitle: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalOrbitHaptics.current
    var filter by remember { mutableStateOf("") }
    var groupTitle by remember { mutableStateOf("") }
    val selectedUsers = remember { mutableStateListOf<UserProfile>() }
    val selectedIds = selectedUsers.map { it.resolvedId }.toSet()
    val maxPeers = 11 // Up to 12 total participants including current user

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.messages_new),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (selectedUsers.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = if (selectedUsers.size == 1) "1 selected" else "${selectedUsers.size} selected · Group",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.messages_new_picker_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            UserSearchDropdownField(
                query = filter,
                onQueryChange = { filter = it },
                allUsers = profiles,
                excludedUserIds = selectedIds,
                placeholder = "Search people by name or @username",
                actionLabel = if (selectedUsers.size >= maxPeers) null else "Add",
                testTag = "dm_picker_search_input",
                clearOnSelect = true,
                onSelectUser = { user ->
                    if (user.resolvedId !in selectedIds && selectedUsers.size < maxPeers) {
                        selectedUsers.add(user)
                    }
                }
            )

            AnimatedVisibility(visible = selectedUsers.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(14.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedUsers.forEach { user ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    haptics.performTap()
                                    selectedUsers.removeAll { it.resolvedId == user.resolvedId }
                                },
                                label = {
                                    Text(
                                        text = user.displayName.ifBlank { "@${user.username}" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                avatar = {
                                    ThreadAvatar(
                                        avatarUrl = user.avatarUrl,
                                        displayName = user.displayName.ifBlank { user.username },
                                        size = 22.dp
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove ${user.displayName.ifBlank { user.username }}",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("dm_selected_user_chip_${user.resolvedId}")
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectedUsers.size >= 2) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            GroupConversationAvatar(
                                participants = selectedUsers.map {
                                    it.avatarUrl to it.displayName.ifBlank { it.username }
                                },
                                totalOtherCount = selectedUsers.size,
                                size = 48.dp,
                                borderColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            Spacer(Modifier.width(12.dp))
                            OutlinedTextField(
                                value = groupTitle,
                                onValueChange = { if (it.length <= 60) groupTitle = it },
                                label = { Text(stringResource(R.string.messages_group_name_label)) },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.messages_group_name_placeholder),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dm_group_title_input")
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    haptics.performMedium()
                    onStartConversation(selectedUsers.toList(), groupTitle.trim())
                },
                enabled = selectedUsers.isNotEmpty(),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(vertical = 13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dm_start_conversation_btn")
            ) {
                if (selectedUsers.size >= 2) {
                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (selectedUsers.size >= 2) {
                        stringResource(R.string.messages_start_group_chat, selectedUsers.size + 1)
                    } else {
                        stringResource(R.string.messages_start_chat)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Twitter/X-inspired 1-on-1 & Group Direct Message Chat Screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatConversationScreen(
    conversationId: String,
    conversation: Conversation?,
    peerProfile: UserProfile?,
    messages: List<DirectMessage>,
    currentUserId: String,
    isSending: Boolean,
    readReceiptsEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
    onOpenPeerProfile: (String) -> Unit,
    onSendMessage: (body: String, imageUri: Uri?, gifUrl: String?, attachmentUri: Uri?, onSent: () -> Unit) -> Unit,
    onImageClick: (String) -> Unit,
    onDownloadPhoto: (String) -> Unit = {},
    onDownloadAttachment: (PostAttachment) -> Unit = {},
    onDeleteMessage: (DirectMessage) -> Unit = {},
    onToggleReaction: (DirectMessage, String) -> Unit = { _, _ -> },
    onMarkConversationRead: () -> Unit = {}
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    val listState = rememberLazyListState()

    val groupConversation = conversation?.takeIf { it.isGroupChat }
    val isGroupChat = groupConversation != null
    val peerId = if (isGroupChat) {
        ""
    } else {
        peerProfile?.resolvedId?.ifBlank { null } ?: conversation?.peerId(currentUserId).orEmpty()
    }
    val chatTitle = if (groupConversation != null) {
        groupConversation.displayTitle(currentUserId)
    } else {
        peerProfile?.displayName?.ifBlank { null }
            ?: conversation?.peerName(currentUserId)
            ?: "Orbit Member"
    }
    val chatSubtitle = if (groupConversation != null) {
        groupConversation.displaySubtitle(currentUserId)
    } else {
        val handle = peerProfile?.username?.ifBlank { null }
            ?: conversation?.peerUsername(currentUserId)
            ?: "member"
        "@$handle"
    }
    val peerAvatar = peerProfile?.avatarUrl?.ifBlank { null }
        ?: conversation?.peerAvatarUrl(currentUserId).orEmpty()

    var showGroupMembersSheet by remember { mutableStateOf(false) }
    var receiptInspectMessage by remember { mutableStateOf<DirectMessage?>(null) }
    var reactionTargetMessage by remember { mutableStateOf<DirectMessage?>(null) }

    val draftState = rememberTextFieldState("")
    val draftFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var attachedGifUrl by remember { mutableStateOf<String?>(null) }
    var attachedFileUri by remember { mutableStateOf<Uri?>(null) }
    var showKeyboardGifTip by remember { mutableStateOf(false) }
    var messagePendingDelete by remember { mutableStateOf<DirectMessage?>(null) }

    val keyboardMediaReceiver = rememberKeyboardMediaReceiver { uri ->
        attachedImageUri = uri
        attachedGifUrl = null
        showKeyboardGifTip = false
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedImageUri = uri
            attachedGifUrl = null
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) attachedFileUri = uri
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    // Mark incoming messages as read whenever the thread is open, unread messages exist, and read receipts are enabled
    val latestIncomingMessage = remember(messages, currentUserId) {
        messages.lastOrNull { it.senderId != currentUserId }
    }
    val myLastReadAtMillis = conversation?.myLastReadAtMillis(currentUserId) ?: 0L
    LaunchedEffect(
        conversationId,
        latestIncomingMessage?.id,
        latestIncomingMessage?.createdAtMillis,
        conversation != null,
        readReceiptsEnabled
    ) {
        if (!readReceiptsEnabled) return@LaunchedEffect
        val incoming = latestIncomingMessage ?: return@LaunchedEffect
        if (conversation != null && (myLastReadAtMillis == 0L || myLastReadAtMillis < incoming.createdAtMillis)) {
            onMarkConversationRead()
        }
    }

    val lastMyMessageId = remember(messages, currentUserId) {
        messages.lastOrNull { it.senderId == currentUserId }?.id
    }
    val lastSeenMyMessageId = remember(messages, currentUserId, conversation?.lastReadAtByUser) {
        if (conversation == null) {
            null
        } else {
            messages.lastOrNull { msg ->
                msg.senderId == currentUserId &&
                    conversation.isMessageSeenByPeer(currentUserId, msg.createdAtMillis)
            }?.id
        }
    }

    CenteredThreadsContainer {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable(enabled = isGroupChat || peerId.isNotBlank()) {
                                    if (isGroupChat) {
                                        showGroupMembersSheet = true
                                    } else if (peerId.isNotBlank()) {
                                        onOpenPeerProfile(peerId)
                                    }
                                }
                                .testTag("chat_header_title_row")
                        ) {
                            if (groupConversation != null) {
                                ConversationAvatar(
                                    conversation = groupConversation,
                                    currentUserId = currentUserId,
                                    size = 38.dp
                                )
                            } else {
                                ThreadAvatar(
                                    avatarUrl = peerAvatar,
                                    displayName = chatTitle,
                                    size = 34.dp
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = chatTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = chatSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("chat_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        var previewVideoAttachment by remember { mutableStateOf<PostAttachment?>(null) }

                        if (attachedImageUri != null || !attachedGifUrl.isNullOrBlank()) {
                            val activeModel = attachedImageUri ?: attachedGifUrl
                            val activeUrlString = attachedImageUri?.toString() ?: attachedGifUrl.orEmpty()
                            val isGifPreview = isGifUrl(activeUrlString) ||
                                (attachedImageUri == null && !attachedGifUrl.isNullOrBlank())
                            Box(
                                modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                            ) {
                                UploadedPhotoPreviewTile(
                                    model = activeModel,
                                    previewLabel = "Preview",
                                    isGif = isGifPreview,
                                    size = 92.dp,
                                    onPreviewClick = {
                                        if (activeUrlString.isNotBlank()) {
                                            onImageClick(activeUrlString)
                                        }
                                    },
                                    onRemoveClick = {
                                        attachedImageUri = null
                                        attachedGifUrl = null
                                    },
                                    modifier = Modifier.testTag("chat_uploaded_photo_preview")
                                )
                            }
                        }

                        AnimatedVisibility(visible = showKeyboardGifTip) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .testTag("chat_keyboard_gif_tip")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.GifBox,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.keyboard_gif_hint),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { showKeyboardGifTip = false },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Dismiss tip",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        attachedFileUri?.let { currentAttachmentUri ->
                            UploadedAttachmentPreviewCard(
                                uri = currentAttachmentUri,
                                onPreviewImage = { imgUrl -> onImageClick(imgUrl) },
                                onPreviewVideo = { vidAtt -> previewVideoAttachment = vidAtt },
                                onRemove = { attachedFileUri = null },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        previewVideoAttachment?.let { activeVid ->
                            OrbitVideoPlayerDialog(
                                attachment = activeVid,
                                onDismiss = { previewVideoAttachment = null }
                            )
                        }

                        // Signature Twitter/X floating pill composer bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(26.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            photoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .testTag("chat_attach_photo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Image,
                                            contentDescription = "Attach photo",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            showKeyboardGifTip = true
                                            runCatching { draftFocusRequester.requestFocus() }
                                            keyboardController?.show()
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .testTag("chat_attach_gif_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.GifBox,
                                            contentDescription = stringResource(R.string.giphy_attach_desc),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(
                                        onClick = { filePicker.launch(arrayOf("*/*")) },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .testTag("chat_attach_file_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AttachFile,
                                            contentDescription = "Attach file from system files",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    val draftText = draftState.text.toString()
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                    ) {
                                        if (draftText.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.messages_input_hint),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        BasicTextField(
                                            state = draftState,
                                            inputTransformation = InputTransformation.maxLength(1000),
                                            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(draftFocusRequester)
                                                .contentReceiver(keyboardMediaReceiver)
                                                .testTag("chat_message_input")
                                        )
                                    }

                                    val haptics = LocalOrbitHaptics.current
                                    val canSend = (draftText.trim().isNotEmpty() || attachedImageUri != null || !attachedGifUrl.isNullOrBlank() || attachedFileUri != null) && !isSending
                                    IconButton(
                                        onClick = {
                                            if (canSend) {
                                                haptics.performMedium()
                                                val textToSend = draftText
                                                val imgToSend = attachedImageUri
                                                val gifToSend = attachedGifUrl
                                                val fileToSend = attachedFileUri
                                                onSendMessage(textToSend, imgToSend, gifToSend, fileToSend) {
                                                    draftState.clearText()
                                                    attachedImageUri = null
                                                    attachedGifUrl = null
                                                    attachedFileUri = null
                                                    showKeyboardGifTip = false
                                                }
                                            }
                                        },
                                        enabled = canSend,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("chat_send_button")
                                    ) {
                                        if (isSending) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send message",
                                                tint = if (canSend) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Classic Twitter/X top-of-DM profile or group introduction card
                item(key = "x_dm_profile_header") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                            .clickable(enabled = isGroupChat || peerId.isNotBlank()) {
                                if (isGroupChat) {
                                    showGroupMembersSheet = true
                                } else if (peerId.isNotBlank()) {
                                    onOpenPeerProfile(peerId)
                                }
                            }
                    ) {
                        if (groupConversation != null) {
                            ConversationAvatar(
                                conversation = groupConversation,
                                currentUserId = currentUserId,
                                size = 68.dp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = chatTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "$chatSubtitle · Tap to view members",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val memberSummary = remember(groupConversation, currentUserId) {
                                groupConversation.otherParticipantIds(currentUserId)
                                    .map { groupConversation.participantDisplayName(it) }
                                    .joinToString(", ")
                            }
                            if (memberSummary.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "With $memberSummary",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        } else {
                            ThreadAvatar(
                                avatarUrl = peerAvatar,
                                displayName = chatTitle,
                                size = 64.dp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = chatTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = chatSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!peerProfile?.bio.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = peerProfile?.bio.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                            if (peerProfile != null) {
                                Spacer(Modifier.height(6.dp))
                                val label = if (peerProfile.followerCount == 1L) "Follower" else "Followers"
                                Text(
                                    text = "${formatCompactCount(peerProfile.followerCount)} $label",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                itemsIndexed(
                    items = messages,
                    key = { _, msg -> msg.id }
                ) { index, msg ->
                    val isMine = msg.senderId == currentUserId
                    val seenUsers = if (isMine && conversation != null) {
                        conversation.seenByUsers(currentUserId, msg.createdAtMillis)
                    } else {
                        emptyList()
                    }
                    val isSeen = isMine && seenUsers.isNotEmpty()
                    val showSeenLabel = isMine && msg.id == lastSeenMyMessageId
                    val showSentLabel = isMine && !isSeen && msg.id == lastMyMessageId
                    val showGroupSenderHeader = isGroupChat && !isMine &&
                        (index == 0 || messages[index - 1].senderId != msg.senderId)
                    val senderDisplayName = conversation?.participantDisplayName(msg.senderId)
                        ?: msg.senderUsername.ifBlank { "Member" }
                    val senderAvatarUrl = conversation?.participantAvatarUrl(msg.senderId).orEmpty()
                    val seenLabelText = if (showSeenLabel) {
                        if (isGroupChat) {
                            when {
                                seenUsers.isEmpty() -> stringResource(R.string.dm_status_seen)
                                seenUsers.size == 1 -> "Seen by ${seenUsers[0].displayName}"
                                seenUsers.size == 2 -> "Seen by ${seenUsers[0].displayName}, ${seenUsers[1].displayName}"
                                else -> "Seen by ${seenUsers[0].displayName} +${seenUsers.size - 1}"
                            }
                        } else {
                            val seenAt = seenUsers.firstOrNull()?.readAtMillis ?: 0L
                            if (seenAt > 0L) {
                                stringResource(R.string.dm_status_seen_at, formatReadReceiptTime(seenAt))
                            } else {
                                stringResource(R.string.dm_status_seen)
                            }
                        }
                    } else {
                        stringResource(R.string.dm_status_seen)
                    }
                    XDirectMessageBubble(
                        message = msg,
                        currentUserId = currentUserId,
                        isMine = isMine,
                        isSeen = isSeen,
                        seenUsers = seenUsers,
                        showSeenLabel = showSeenLabel,
                        seenLabelText = seenLabelText,
                        showSentLabel = showSentLabel,
                        showSenderHeader = showGroupSenderHeader,
                        senderDisplayName = senderDisplayName,
                        senderAvatarUrl = senderAvatarUrl,
                        onSenderClick = {
                            if (msg.senderId.isNotBlank()) onOpenPeerProfile(msg.senderId)
                        },
                        onInspectReceipts = if (isMine) {
                            { receiptInspectMessage = msg }
                        } else {
                            null
                        },
                        onImageClick = onImageClick,
                        onDownloadPhoto = onDownloadPhoto,
                        onDownloadAttachment = onDownloadAttachment,
                        onLongPressMessage = {
                            reactionTargetMessage = msg
                        },
                        onToggleReaction = { emoji ->
                            onToggleReaction(msg, emoji)
                        }
                    )
                }
            }
        }
    }

    reactionTargetMessage?.let { targetRef ->
        val liveTarget = remember(targetRef.id, messages) {
            messages.firstOrNull { it.id == targetRef.id } ?: targetRef
        }
        val isMine = liveTarget.senderId == currentUserId
        DmMessageReactionSheet(
            message = liveTarget,
            conversation = conversation,
            currentUserId = currentUserId,
            isMine = isMine,
            onDismiss = { reactionTargetMessage = null },
            onSelectEmoji = { emoji ->
                reactionTargetMessage = null
                onToggleReaction(liveTarget, emoji)
            },
            onCopyMessageText = if (liveTarget.body.isNotBlank()) {
                {
                    clipboardManager.setText(AnnotatedString(liveTarget.body))
                    reactionTargetMessage = null
                }
            } else {
                null
            },
            onInspectReceipts = if (isMine) {
                {
                    reactionTargetMessage = null
                    receiptInspectMessage = liveTarget
                }
            } else {
                null
            },
            onDeleteMessage = if (isMine) {
                {
                    reactionTargetMessage = null
                    messagePendingDelete = liveTarget
                }
            } else {
                null
            }
        )
    }

    receiptInspectMessage?.let { inspectedMsg ->
        val inspectedSeenUsers = remember(inspectedMsg.id, conversation?.lastReadAtByUser) {
            conversation?.seenByUsers(currentUserId, inspectedMsg.createdAtMillis).orEmpty()
        }
        ReadReceiptsDetailSheet(
            message = inspectedMsg,
            seenUsers = inspectedSeenUsers,
            readReceiptsEnabled = readReceiptsEnabled,
            onDismiss = { receiptInspectMessage = null },
            onUserClick = { uid ->
                receiptInspectMessage = null
                onOpenPeerProfile(uid)
            }
        )
    }

    if (showGroupMembersSheet && groupConversation != null) {
        val membersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showGroupMembersSheet = false },
            sheetState = membersSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ConversationAvatar(
                        conversation = groupConversation,
                        currentUserId = currentUserId,
                        size = 44.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chatTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${stringResource(R.string.messages_group_members_title)} · ${groupConversation.participantIds.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(
                    thickness = 0.6.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    conversation.participantIds.forEach { memberUid ->
                        val isMe = memberUid == currentUserId
                        val memberName = conversation.participantDisplayName(memberUid)
                        val memberHandle = conversation.participantUsername(memberUid)
                        val memberAvatar = conversation.participantAvatarUrl(memberUid)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isMe) {
                                    showGroupMembersSheet = false
                                    onOpenPeerProfile(memberUid)
                                }
                                .padding(horizontal = 6.dp, vertical = 10.dp)
                                .testTag("group_member_row_$memberUid")
                        ) {
                            ThreadAvatar(
                                avatarUrl = memberAvatar,
                                displayName = memberName,
                                size = 40.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isMe) "$memberName (You)" else memberName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@$memberHandle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    messagePendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { messagePendingDelete = null },
            title = { Text("Delete message?") },
            text = { Text("This direct message will be permanently deleted from the conversation.") },
            confirmButton = {
                Button(
                    onClick = {
                        messagePendingDelete = null
                        onDeleteMessage(pending)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_dm_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { messagePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun XDirectMessageBubble(
    message: DirectMessage,
    currentUserId: String,
    isMine: Boolean,
    isSeen: Boolean = false,
    seenUsers: List<SeenReceiptInfo> = emptyList(),
    showSeenLabel: Boolean = false,
    seenLabelText: String = "",
    showSentLabel: Boolean = false,
    showSenderHeader: Boolean = false,
    senderDisplayName: String = "",
    senderAvatarUrl: String = "",
    onSenderClick: () -> Unit = {},
    onInspectReceipts: (() -> Unit)? = null,
    onImageClick: (String) -> Unit,
    onDownloadPhoto: (String) -> Unit = {},
    onDownloadAttachment: (PostAttachment) -> Unit = {},
    onLongPressMessage: () -> Unit = {},
    onToggleReaction: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    val imageModel = remember(message.imageUrl) { OrbitCacheManager.toCoilModel(message.imageUrl) }
    val reactionSummaries = remember(message.reactions, currentUserId) {
        message.reactionSummaries(currentUserId)
    }

    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    }

    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isMine && onInspectReceipts != null) {
                        haptics.performTap()
                        onInspectReceipts()
                    }
                },
                onLongClick = {
                    haptics.performHeavy()
                    onLongPressMessage()
                }
            )
            .testTag("dm_bubble_${message.id}")
    ) {
        if (showSenderHeader && !isMine) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 4.dp, start = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onSenderClick)
            ) {
                ThreadAvatar(
                    avatarUrl = senderAvatarUrl,
                    displayName = senderDisplayName.ifBlank { message.senderUsername },
                    size = 20.dp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = senderDisplayName.ifBlank { "@${message.senderUsername}" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (imageModel != null) {
            Box(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .height(190.dp)
            ) {
                AsyncImage(
                    model = imageModel,
                    imageLoader = imageLoader,
                    contentDescription = "Direct message photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = { onImageClick(message.imageUrl) },
                            onLongClick = {
                                haptics.performHeavy()
                                onLongPressMessage()
                            }
                        )
                )
                if (isGifUrl(message.imageUrl)) {
                    GifCornerBadge(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    )
                }
                Surface(
                    onClick = { onDownloadPhoto(message.imageUrl) },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.56f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Download photo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            if (message.body.isNotBlank() || message.attachments.isNotEmpty()) Spacer(Modifier.height(4.dp))
        }

        if (message.attachments.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(max = 276.dp)
            ) {
                message.attachments.forEachIndexed { idx, attachment ->
                    PostAttachmentCard(
                        attachment = attachment,
                        cacheKey = "dm_${message.id}_$idx",
                        onImageClick = onImageClick,
                        onDownloadClick = { onDownloadAttachment(attachment) }
                    )
                }
            }
            if (message.body.isNotBlank()) Spacer(Modifier.height(4.dp))
        }

        if (message.body.isNotBlank()) {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {
                            if (isMine && onInspectReceipts != null) {
                                haptics.performTap()
                                onInspectReceipts()
                            }
                        },
                        onLongClick = {
                            haptics.performHeavy()
                            onLongPressMessage()
                        }
                    )
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        if (reactionSummaries.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(
                    space = 5.dp,
                    alignment = if (isMine) Alignment.End else Alignment.Start
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .testTag("dm_reactions_row_${message.id}")
            ) {
                reactionSummaries.forEachIndexed { idx, summary ->
                    DmReactionPill(
                        summary = summary,
                        onClick = {
                            haptics.performTap()
                            onToggleReaction(summary.emoji)
                        },
                        modifier = Modifier.testTag("dm_reaction_chip_${message.id}_$idx")
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = isMine && onInspectReceipts != null) {
                    haptics.performTap()
                    onInspectReceipts?.invoke()
                }
                .padding(top = 3.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = formatRelativeTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isMine) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isSeen) Icons.Filled.DoneAll else Icons.Filled.Done,
                    contentDescription = if (isSeen) {
                        stringResource(R.string.dm_status_seen)
                    } else {
                        stringResource(R.string.dm_status_sent)
                    },
                    tint = if (isSeen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(14.dp)
                )
                if (showSeenLabel) {
                    if (seenUsers.isNotEmpty()) {
                        Spacer(Modifier.width(5.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-4).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            seenUsers.take(3).forEach { reader ->
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                ) {
                                    ThreadAvatar(
                                        avatarUrl = reader.avatarUrl,
                                        displayName = reader.displayName,
                                        size = 16.dp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = seenLabelText.ifBlank { stringResource(R.string.dm_status_seen) },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("dm_seen_indicator_${message.id}")
                    )
                } else if (showSentLabel) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.dm_status_sent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("dm_sent_indicator_${message.id}")
                    )
                }
            }
        }
    }
}

@Composable
private fun DmReactionPill(
    summary: DmReactionChipSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (summary.reactedByMe) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (summary.reactedByMe) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }
    val textColor = if (summary.reactedByMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = summary.emoji,
                style = MaterialTheme.typography.labelMedium
            )
            if (summary.count > 1) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = summary.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DmMessageReactionSheet(
    message: DirectMessage,
    conversation: Conversation?,
    currentUserId: String,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onSelectEmoji: (String) -> Unit,
    onCopyMessageText: (() -> Unit)? = null,
    onInspectReceipts: (() -> Unit)? = null,
    onDeleteMessage: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalOrbitHaptics.current
    val myCurrentReaction = remember(message.reactions, currentUserId) {
        message.myReaction(currentUserId)
    }
    val reactionSummaries = remember(message.reactions, currentUserId) {
        message.reactionSummaries(currentUserId)
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
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
                .testTag("dm_reaction_picker_sheet")
        ) {
            Text(
                text = stringResource(R.string.dm_react_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (myCurrentReaction != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.dm_react_tap_to_remove),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))

            // Elevated Emoji Reaction Tray
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    DM_REACTION_EMOJIS.forEachIndexed { index, emoji ->
                        val isSelected = myCurrentReaction == emoji
                        Surface(
                            onClick = {
                                haptics.performMedium()
                                onSelectEmoji(emoji)
                            },
                            shape = CircleShape,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .testTag("dm_reaction_emoji_$index")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = emoji,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                }
            }

            if (message.body.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            if (reactionSummaries.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reactionSummaries.forEach { summary ->
                        val reactorLabels = summary.userIds.map { uid ->
                            if (uid == currentUserId) {
                                "You"
                            } else {
                                conversation?.participantDisplayName(uid) ?: "Member"
                            }
                        }.joinToString(", ")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = summary.emoji,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = reactorLabels,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (onCopyMessageText != null || onInspectReceipts != null || onDeleteMessage != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(
                    thickness = 0.6.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(6.dp))
            }

            if (onCopyMessageText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptics.performTap()
                            onCopyMessageText()
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .testTag("dm_reaction_sheet_copy_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.dm_action_copy_text),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (onInspectReceipts != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptics.performTap()
                            onInspectReceipts()
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .testTag("dm_reaction_sheet_receipts_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.dm_action_view_receipts),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (onDeleteMessage != null && isMine) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptics.performHeavy()
                            onDeleteMessage()
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .testTag("dm_reaction_sheet_delete_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.dm_action_delete_message),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadReceiptsDetailSheet(
    message: DirectMessage,
    seenUsers: List<SeenReceiptInfo>,
    readReceiptsEnabled: Boolean,
    onDismiss: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
                .testTag("dm_read_receipts_sheet")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (seenUsers.isNotEmpty()) Icons.Filled.DoneAll else Icons.Filled.Done,
                    contentDescription = null,
                    tint = if (seenUsers.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dm_receipt_details_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatReadReceiptTime(message.createdAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (message.body.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(
                thickness = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(10.dp))

            if (seenUsers.isEmpty()) {
                Text(
                    text = stringResource(R.string.dm_receipt_not_seen_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Text(
                    text = "${stringResource(R.string.dm_receipt_seen_by_header)} (${seenUsers.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                seenUsers.forEach { reader ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onUserClick(reader.uid) }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .testTag("dm_receipt_reader_${reader.uid}")
                    ) {
                        ThreadAvatar(
                            avatarUrl = reader.avatarUrl,
                            displayName = reader.displayName,
                            size = 38.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reader.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "@${reader.username}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatReadReceiptTime(reader.readAtMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (!readReceiptsEnabled) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.dm_receipt_paused_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatReadReceiptTime(millis: Long): String {
    if (millis <= 0L) return ""
    val diffMs = (System.currentTimeMillis() - millis).coerceAtLeast(0L)
    return when {
        diffMs < 60_000L -> "just now"
        diffMs < 86_400_000L -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
    }
}

