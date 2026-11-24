package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.cache.OrbitMediaFiles
import com.example.data.model.ThreadPost
import com.example.data.model.UserProfile
import com.example.data.model.isGifUrl
import com.example.ui.components.CollapsibleSectionCard
import com.example.ui.components.GifCornerBadge
import com.example.data.model.PostAttachment
import com.example.ui.components.FullScreenImageLightbox
import com.example.ui.components.LocalFollowedProfiles
import com.example.ui.components.LocalMentionableProfiles
import com.example.ui.components.MentionSuggestionsDropdown
import com.example.ui.components.OrbitVideoPlayerDialog
import com.example.ui.components.QuotedPostCard
import com.example.ui.components.RichTextFormattingToolbar
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.UploadedAttachmentPreviewCard
import com.example.ui.components.UploadedPhotoPreviewTile
import com.example.ui.components.UserSearchDropdownField
import com.example.ui.components.applyBoldFormat
import com.example.ui.components.applyItalicFormat
import com.example.ui.components.buildReplyMentionCandidates
import com.example.ui.components.detectActiveMentionQuery
import com.example.ui.components.detectRichTextActiveState
import com.example.ui.components.handleRichTextValueChange
import com.example.ui.components.hasOrbitRichTextFormatting
import com.example.ui.components.insertMention
import com.example.ui.components.parseOrbitRichText
import com.example.ui.components.rememberKeyboardMediaReceiver
import com.example.ui.components.toggleBulletListFormat
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ComposeThreadSheet(
    myProfile: UserProfile,
    allProfiles: List<UserProfile> = emptyList(),
    currentUserId: String = myProfile.resolvedId,
    replyTarget: ThreadPost?,
    quoteTarget: ThreadPost? = null,
    isPosting: Boolean,
    onDismiss: () -> Unit,
    onPublish: (
        body: String,
        imageUris: List<Uri>,
        gifUrls: List<String>,
        attachmentUris: List<Uri>,
        audienceMode: String,
        replyMode: String,
        allowedReplyUserIds: List<String>,
        pollOptions: List<String>,
        pollDurationHours: Int
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    val bodyState = rememberTextFieldState("")
    var isPreviewMode by remember { mutableStateOf(false) }
    val selectedUris = remember { mutableStateListOf<Uri>() }
    val selectedGifUrls = remember { mutableStateListOf<String>() }
    val selectedAttachments = remember { mutableStateListOf<Uri>() }
    var showKeyboardGifTip by remember { mutableStateOf(false) }
    var isPollEnabled by remember { mutableStateOf(false) }
    val pollOptions = remember { mutableStateListOf("", "") }
    var pollDurationHours by remember { mutableIntStateOf(24) }
    var audienceMode by remember { mutableStateOf("everyone") }
    var replyMode by remember { mutableStateOf("everyone") }
    var selectedReplyUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var replyUserQuery by remember { mutableStateOf("") }

    val followedProfiles = LocalFollowedProfiles.current
    val allMentionableProfiles = LocalMentionableProfiles.current
    val followedIds = remember(followedProfiles) {
        followedProfiles.map { it.resolvedId }.toSet()
    }
    val mentionCandidates = remember(
        replyTarget,
        followedProfiles,
        allMentionableProfiles,
        allProfiles,
        currentUserId
    ) {
        val pool = allMentionableProfiles.ifEmpty { allProfiles }
        buildReplyMentionCandidates(
            replyTarget = replyTarget,
            followedProfiles = followedProfiles,
            allMentionableProfiles = pool,
            currentUserId = currentUserId
        )
    }

    val bodyText = bodyState.text.toString()
    val bodyTextFieldValue = TextFieldValue(bodyText, bodyState.selection)
    val activeFormatState = remember(bodyText, bodyState.selection) {
        detectRichTextActiveState(bodyTextFieldValue)
    }
    val activeMentionQuery = remember(bodyText, bodyState.selection, isPreviewMode) {
        if (isPreviewMode) null else detectActiveMentionQuery(bodyText, bodyState.selection)
    }
    val hasRichFormatting = remember(bodyText) {
        hasOrbitRichTextFormatting(bodyText)
    }
    val primaryColor = MaterialTheme.colorScheme.primary

    val richTextInputTransformation = remember {
        InputTransformation {
            if (length > 500) {
                revertAllChanges()
                return@InputTransformation
            }
            val before = originalText.toString()
            val after = asCharSequence().toString()
            val updated = handleRichTextValueChange(
                previous = TextFieldValue(before, originalSelection),
                incoming = TextFieldValue(after, selection),
                maxChars = 500
            )
            if (updated.text != after) {
                replace(0, length, updated.text)
                selection = updated.selection
            }
        }
    }

    val keyboardMediaReceiver = rememberKeyboardMediaReceiver { uri ->
        if (selectedUris.size + selectedGifUrls.size < 4 && uri !in selectedUris) {
            selectedUris.add(uri)
        }
        showKeyboardGifTip = false
    }

    // Automatically focus the composer and show the keyboard immediately when clicking "What's new?"
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    // Zero-permission Android Photo Picker (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val remaining = (4 - selectedUris.size - selectedGifUrls.size).coerceAtLeast(0)
            selectedUris.addAll(uris.take(remaining))
        }
    }

    // Zero-permission Android Video Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && selectedAttachments.size < 3 && uri !in selectedAttachments) {
            selectedAttachments.add(uri)
        }
    }

    // Zero-permission Android System Files Picker (SAF OpenDocument)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && selectedAttachments.size < 3 && uri !in selectedAttachments) {
            selectedAttachments.add(uri)
        }
    }

    val cleanedPollOptions = pollOptions.map { it.trim() }.filter { it.isNotEmpty() }
    val totalMediaCount = selectedUris.size + selectedGifUrls.size
    val hasValidContent = if (isPollEnabled) {
        cleanedPollOptions.size >= 2
    } else {
        bodyText.trim().isNotEmpty() ||
            selectedUris.isNotEmpty() ||
            selectedGifUrls.isNotEmpty() ||
            selectedAttachments.isNotEmpty() ||
            quoteTarget != null
    }
    val canPost = hasValidContent && !isPosting
    val remainingChars = 500 - bodyText.length

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Header bar: Cancel | Title | Post
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("compose_cancel_button")
                ) {
                    Text("Cancel")
                }
                Text(
                    text = when {
                        quoteTarget != null -> "Quote repost"
                        replyTarget != null -> "Reply"
                        else -> stringResource(R.string.compose_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button(
                    onClick = {
                        onPublish(
                            bodyText,
                            selectedUris.toList(),
                            selectedGifUrls.toList(),
                            selectedAttachments.toList(),
                            audienceMode,
                            replyMode,
                            selectedReplyUsers.map { it.resolvedId },
                            if (isPollEnabled) cleanedPollOptions else emptyList(),
                            pollDurationHours
                        )
                    },
                    enabled = canPost,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("compose_publish_button")
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(stringResource(R.string.compose_post))
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Under "New thread": per-post audience & reply permission controls
            if (replyTarget == null) {
                val audienceSummary = if (audienceMode == "followers_only") "Followers see" else "Everyone sees"
                val replySummary = when (replyMode) {
                    "followers_only" -> "Followers reply"
                    "selected_users" -> "${selectedReplyUsers.size} selected can reply"
                    else -> "Everyone replies"
                }
                CollapsibleSectionCard(
                    title = "Who can see & reply",
                    subtitle = "$audienceSummary · $replySummary",
                    icon = Icons.Outlined.Lock,
                    initiallyExpanded = false,
                    testTag = "compose_privacy_controls",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Who can see this post",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = audienceMode == "everyone",
                                onClick = { audienceMode = "everyone" },
                                label = { Text("Everyone") },
                                modifier = Modifier.testTag("compose_audience_everyone")
                            )
                            FilterChip(
                                selected = audienceMode == "followers_only",
                                onClick = { audienceMode = "followers_only" },
                                label = { Text("Only followers") },
                                modifier = Modifier.testTag("compose_audience_followers")
                            )
                        }

                        Text(
                            text = "Who can reply",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = replyMode == "everyone",
                                onClick = { replyMode = "everyone" },
                                label = { Text("Everyone") },
                                modifier = Modifier.testTag("compose_reply_everyone")
                            )
                            FilterChip(
                                selected = replyMode == "followers_only",
                                onClick = { replyMode = "followers_only" },
                                label = { Text("Only followers") },
                                modifier = Modifier.testTag("compose_reply_followers")
                            )
                            FilterChip(
                                selected = replyMode == "selected_users",
                                onClick = { replyMode = "selected_users" },
                                label = { Text("People I select") },
                                modifier = Modifier.testTag("compose_reply_selected")
                            )
                        }

                        if (replyMode == "selected_users") {
                            UserSearchDropdownField(
                                query = replyUserQuery,
                                onQueryChange = { replyUserQuery = it },
                                allUsers = allProfiles,
                                excludedUserIds = selectedReplyUsers.map { it.resolvedId }.toSet() + currentUserId,
                                placeholder = "Search people allowed to reply…",
                                actionLabel = "Allow",
                                testTag = "compose_reply_user_search",
                                onSelectUser = { user ->
                                    selectedReplyUsers = (selectedReplyUsers + user).distinctBy { it.resolvedId }
                                }
                            )
                            if (selectedReplyUsers.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    selectedReplyUsers.forEach { user ->
                                        InputChip(
                                            selected = true,
                                            onClick = {
                                                selectedReplyUsers = selectedReplyUsers.filterNot { it.resolvedId == user.resolvedId }
                                            },
                                            label = { Text("@${user.username}") },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "Remove @${user.username}",
                                                    modifier = Modifier.size(15.dp)
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

            // If replying, show compact parent context with vertical thread line
            if (replyTarget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp)
                    ) {
                        ThreadAvatar(
                            avatarUrl = replyTarget.authorAvatarUrl,
                            displayName = replyTarget.authorDisplayName.ifBlank { replyTarget.authorUsername },
                            size = 36.dp
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(1.75.dp)
                                .height(26.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = replyTarget.authorUsername,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val replyBodyFormatted = remember(replyTarget.body, primaryColor) {
                            parseOrbitRichText(replyTarget.body.ifBlank { "[Media attachment]" }, primaryColor)
                        }
                        Text(
                            text = replyBodyFormatted,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Main composer row (avatar + vertical line + clean borderless Threads input)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(40.dp)
                ) {
                    ThreadAvatar(
                        avatarUrl = myProfile.avatarUrl,
                        displayName = myProfile.displayName.ifBlank { myProfile.username },
                        size = 38.dp
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(1.75.dp)
                            .height(52.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = myProfile.username.ifBlank { "you" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(6.dp))

                    if (isPreviewMode) {
                        val previewAnnotated = remember(bodyText, primaryColor) {
                            parseOrbitRichText(bodyText, primaryColor)
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("compose_rich_text_preview")
                        ) {
                            Text(
                                text = previewAnnotated,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (bodyText.isEmpty()) {
                                Text(
                                    text = when {
                                        quoteTarget != null -> "Add a thought about this post…"
                                        replyTarget != null -> "Reply to @${replyTarget.authorUsername}…"
                                        else -> stringResource(R.string.compose_placeholder)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                state = bodyState,
                                inputTransformation = richTextInputTransformation,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .contentReceiver(keyboardMediaReceiver)
                                    .testTag("compose_body_input")
                            )
                        }
                    }

                    MentionSuggestionsDropdown(
                        activeQuery = activeMentionQuery,
                        followedProfiles = mentionCandidates,
                        followedIds = followedIds,
                        onSelectUser = { user ->
                            activeMentionQuery?.let { active ->
                                val updated = insertMention(
                                    value = TextFieldValue(bodyState.text.toString(), bodyState.selection),
                                    active = active,
                                    username = user.username,
                                    maxChars = 500
                                )
                                bodyState.edit {
                                    replace(0, length, updated.text)
                                    selection = updated.selection
                                }
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    AnimatedVisibility(visible = showKeyboardGifTip) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .testTag("compose_keyboard_gif_tip")
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

                    Spacer(Modifier.height(12.dp))

                    // Rich text formatting toolbar (Bold, Italics, Bullet Points, Preview)
                    RichTextFormattingToolbar(
                        activeState = activeFormatState,
                        hasFormatting = hasRichFormatting,
                        isPreviewMode = isPreviewMode,
                        onBoldClick = {
                            val updated = applyBoldFormat(
                                TextFieldValue(bodyState.text.toString(), bodyState.selection),
                                maxChars = 500
                            )
                            bodyState.edit {
                                replace(0, length, updated.text)
                                selection = updated.selection
                            }
                            runCatching { focusRequester.requestFocus() }
                        },
                        onItalicClick = {
                            val updated = applyItalicFormat(
                                TextFieldValue(bodyState.text.toString(), bodyState.selection),
                                maxChars = 500
                            )
                            bodyState.edit {
                                replace(0, length, updated.text)
                                selection = updated.selection
                            }
                            runCatching { focusRequester.requestFocus() }
                        },
                        onBulletClick = {
                            val updated = toggleBulletListFormat(
                                TextFieldValue(bodyState.text.toString(), bodyState.selection),
                                maxChars = 500
                            )
                            bodyState.edit {
                                replace(0, length, updated.text)
                                selection = updated.selection
                            }
                            runCatching { focusRequester.requestFocus() }
                        },
                        onTogglePreview = {
                            isPreviewMode = !isPreviewMode
                        }
                    )

                    if (quoteTarget != null) {
                        Spacer(Modifier.height(12.dp))
                        QuotedPostCard(
                            authorUsername = quoteTarget.authorUsername,
                            authorDisplayName = quoteTarget.authorDisplayName,
                            authorAvatarUrl = quoteTarget.authorAvatarUrl,
                            body = quoteTarget.body,
                            imageUrl = quoteTarget.imageUrls.firstOrNull().orEmpty()
                        )
                    }

                    // Interactive Poll Creator Card
                    AnimatedVisibility(visible = isPollEnabled) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("compose_poll_creator_card")
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.Poll,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.poll_creator_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                isPollEnabled = false
                                                pollOptions.clear()
                                                pollOptions.addAll(listOf("", ""))
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("compose_remove_poll_button")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.poll_remove),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    pollOptions.forEachIndexed { index, optionText ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedTextField(
                                                value = optionText,
                                                onValueChange = { next ->
                                                    if (next.length <= 60) {
                                                        pollOptions[index] = next
                                                    }
                                                },
                                                placeholder = {
                                                    Text(
                                                        text = stringResource(R.string.poll_option_placeholder, index + 1)
                                                    )
                                                },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("compose_poll_option_input_$index")
                                            )
                                            if (pollOptions.size > 2) {
                                                IconButton(
                                                    onClick = { pollOptions.removeAt(index) },
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .testTag("compose_poll_remove_option_$index")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = "Remove option ${index + 1}",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (pollOptions.size < 4) {
                                        TextButton(
                                            onClick = { pollOptions.add("") },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("compose_poll_add_option_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.poll_add_option),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }

                                    Text(
                                        text = stringResource(R.string.poll_duration_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val durationChoices = remember {
                                        listOf(1 to "1h", 6 to "6h", 24 to "1d", 72 to "3d", 168 to "7d")
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        durationChoices.forEach { (hours, label) ->
                                            FilterChip(
                                                selected = pollDurationHours == hours,
                                                onClick = { pollDurationHours = hours },
                                                label = { Text(label) },
                                                modifier = Modifier.testTag("compose_poll_duration_$hours")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    var previewLightboxUrl by remember { mutableStateOf<String?>(null) }
                    var previewVideoAttachment by remember { mutableStateOf<PostAttachment?>(null) }

                    if (selectedUris.isNotEmpty() || selectedGifUrls.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(selectedUris, key = { it.toString() }) { uri ->
                                UploadedPhotoPreviewTile(
                                    model = uri,
                                    previewLabel = "Preview",
                                    isGif = isGifUrl(uri.toString()),
                                    size = 108.dp,
                                    onPreviewClick = { previewLightboxUrl = uri.toString() },
                                    onRemoveClick = { selectedUris.remove(uri) },
                                    modifier = Modifier.testTag("compose_photo_preview_${uri.hashCode()}")
                                )
                            }

                            items(selectedGifUrls, key = { it }) { gifUrl ->
                                UploadedPhotoPreviewTile(
                                    model = gifUrl,
                                    previewLabel = "Preview",
                                    isGif = true,
                                    size = 108.dp,
                                    onPreviewClick = { previewLightboxUrl = gifUrl },
                                    onRemoveClick = { selectedGifUrls.remove(gifUrl) },
                                    modifier = Modifier.testTag("compose_gif_preview_${gifUrl.hashCode()}")
                                )
                            }
                        }
                    }

                    if (selectedAttachments.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
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

                    Spacer(Modifier.height(14.dp))

                    // Attachment & character count toolbar (Photos, GIFs, Poll, Videos, System Files)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = totalMediaCount < 4,
                            modifier = Modifier.testTag("compose_add_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "Attach photos",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                isPreviewMode = false
                                showKeyboardGifTip = true
                                runCatching { focusRequester.requestFocus() }
                                keyboardController?.show()
                            },
                            enabled = totalMediaCount < 4,
                            modifier = Modifier.testTag("compose_add_gif_button")
                        ) {
                            val hasAttachedGif = selectedGifUrls.isNotEmpty() ||
                                selectedUris.any { isGifUrl(it.toString()) }
                            Icon(
                                imageVector = Icons.Outlined.GifBox,
                                contentDescription = stringResource(R.string.giphy_attach_desc),
                                tint = if (hasAttachedGif || showKeyboardGifTip) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        if (replyTarget == null) {
                            IconButton(
                                onClick = { isPollEnabled = !isPollEnabled },
                                modifier = Modifier.testTag("compose_toggle_poll_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Poll,
                                    contentDescription = stringResource(R.string.poll_toggle_desc),
                                    tint = if (isPollEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            enabled = selectedAttachments.size < 3,
                            modifier = Modifier.testTag("compose_attach_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Movie,
                                contentDescription = "Attach video",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            enabled = selectedAttachments.size < 3,
                            modifier = Modifier.testTag("compose_attach_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = "Attach file from system files",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = "$remainingChars",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (remainingChars < 50) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}
