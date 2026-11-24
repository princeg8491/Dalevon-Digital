package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PostAttachment
import com.example.data.model.ThreadPost
import com.example.data.model.UserProfile
import com.example.data.model.isGifUrl
import com.example.ui.components.CenteredThreadsContainer
import com.example.ui.components.LocalFollowedProfiles
import com.example.ui.components.LocalMentionableProfiles
import com.example.ui.components.MentionSuggestionsDropdown
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.ThreadPostRow
import com.example.ui.components.UploadedPhotoPreviewTile
import com.example.ui.components.buildReplyMentionCandidates
import com.example.ui.components.detectActiveMentionQuery
import com.example.ui.components.insertMention
import com.example.ui.components.rememberKeyboardMediaReceiver

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThreadDetailScreen(
    rootPost: ThreadPost,
    replies: List<ThreadPost>,
    currentUserId: String,
    myProfile: UserProfile,
    likedPostIds: Set<String>,
    repostedPostIds: Set<String>,
    savedPostIds: Set<String> = emptySet(),
    followingIds: Set<String>,
    isPosting: Boolean,
    canReplyToRoot: Boolean = true,
    replyRestrictionLabel: String? = null,
    onBack: () -> Unit,
    onAuthorClick: (String) -> Unit,
    onQuotedPostClick: (String) -> Unit = {},
    onLikeClick: (ThreadPost) -> Unit,
    onReplyModalClick: (ThreadPost) -> Unit,
    onRepostClick: (ThreadPost) -> Unit,
    onSaveClick: (ThreadPost) -> Unit = {},
    onFollowClick: (String) -> Unit,
    onEditClick: (ThreadPost) -> Unit = {},
    onDeleteClick: (ThreadPost) -> Unit,
    onImageClick: (String) -> Unit,
    onDownloadPhoto: (String) -> Unit = {},
    onDownloadAttachment: (PostAttachment) -> Unit = {},
    onQuickReplySend: (String, List<Uri>) -> Unit
) {
    BackHandler(onBack = onBack)

    val replyState = rememberTextFieldState("")
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var attachedReplyMediaUri by remember { mutableStateOf<Uri?>(null) }
    var showKeyboardGifTip by remember { mutableStateOf(false) }

    val followedProfiles = LocalFollowedProfiles.current
    val allMentionableProfiles = LocalMentionableProfiles.current
    val replyMentionCandidates = remember(
        rootPost,
        replies,
        followedProfiles,
        allMentionableProfiles,
        currentUserId
    ) {
        buildReplyMentionCandidates(
            replyTarget = rootPost,
            threadPosts = replies,
            followedProfiles = followedProfiles,
            allMentionableProfiles = allMentionableProfiles,
            currentUserId = currentUserId
        )
    }

    val replyText = replyState.text.toString()
    val activeMentionQuery = remember(replyText, replyState.selection) {
        detectActiveMentionQuery(replyText, replyState.selection)
    }
    val keyboardMediaReceiver = rememberKeyboardMediaReceiver { uri ->
        attachedReplyMediaUri = uri
        showKeyboardGifTip = false
    }

    CenteredThreadsContainer {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Thread",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("thread_detail_back_button")
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
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Column {
                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        if (!canReplyToRoot) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("thread_reply_restricted_notice")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = replyRestrictionLabel
                                        ?: "Only people chosen by @${rootPost.authorUsername} can reply to this thread.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            MentionSuggestionsDropdown(
                                activeQuery = activeMentionQuery,
                                followedProfiles = replyMentionCandidates,
                                followedIds = followingIds,
                                onSelectUser = { user ->
                                    activeMentionQuery?.let { active ->
                                        val updated = insertMention(
                                            value = TextFieldValue(replyState.text.toString(), replyState.selection),
                                            active = active,
                                            username = user.username,
                                            maxChars = 500
                                        )
                                        replyState.edit {
                                            replace(0, length, updated.text)
                                            selection = updated.selection
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )

                            AnimatedVisibility(visible = showKeyboardGifTip) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("thread_reply_keyboard_gif_tip")
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

                            attachedReplyMediaUri?.let { mediaUri ->
                                Box(
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                                ) {
                                    UploadedPhotoPreviewTile(
                                        model = mediaUri,
                                        previewLabel = "Preview",
                                        isGif = isGifUrl(mediaUri.toString()),
                                        size = 84.dp,
                                        onPreviewClick = { onImageClick(mediaUri.toString()) },
                                        onRemoveClick = { attachedReplyMediaUri = null },
                                        modifier = Modifier.testTag("thread_inline_reply_media_preview")
                                    )
                                }
                            }

                            val canSendReply = (replyText.trim().isNotEmpty() || attachedReplyMediaUri != null) && !isPosting
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                ThreadAvatar(
                                    avatarUrl = myProfile.avatarUrl,
                                    displayName = myProfile.displayName.ifBlank { myProfile.username },
                                    size = 34.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                showKeyboardGifTip = true
                                                runCatching { focusRequester.requestFocus() }
                                                keyboardController?.show()
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("thread_inline_reply_gif_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.GifBox,
                                                contentDescription = stringResource(R.string.giphy_attach_desc),
                                                tint = if (attachedReplyMediaUri != null || showKeyboardGifTip) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                        ) {
                                            if (replyText.isEmpty()) {
                                                Text(
                                                    text = "Reply to @${rootPost.authorUsername}…",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            BasicTextField(
                                                state = replyState,
                                                inputTransformation = InputTransformation.maxLength(500),
                                                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(focusRequester)
                                                    .contentReceiver(keyboardMediaReceiver)
                                                    .testTag("thread_inline_reply_input")
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        val trimmed = replyText.trim()
                                        val media = listOfNotNull(attachedReplyMediaUri)
                                        if ((trimmed.isNotEmpty() || media.isNotEmpty()) && !isPosting) {
                                            onQuickReplySend(trimmed, media)
                                            replyState.clearText()
                                            attachedReplyMediaUri = null
                                            showKeyboardGifTip = false
                                        }
                                    },
                                    enabled = canSendReply,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("thread_inline_reply_send")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send reply",
                                        tint = if (canSendReply) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item(key = "root_${rootPost.id}") {
                    ThreadPostRow(
                        post = rootPost,
                        isLiked = rootPost.id in likedPostIds,
                        isReposted = rootPost.id in repostedPostIds,
                        isSaved = rootPost.id in savedPostIds,
                        isOwnPost = rootPost.authorId == currentUserId,
                        isFollowingAuthor = rootPost.authorId in followingIds,
                        showThreadLine = replies.isNotEmpty(),
                        onPostClick = {},
                        onQuotedPostClick = onQuotedPostClick,
                        onAuthorClick = { onAuthorClick(rootPost.authorId) },
                        onLikeClick = { onLikeClick(rootPost) },
                        onReplyClick = { if (canReplyToRoot) onReplyModalClick(rootPost) },
                        onRepostClick = { onRepostClick(rootPost) },
                        onSaveClick = { onSaveClick(rootPost) },
                        onFollowAuthorClick = { onFollowClick(rootPost.authorId) },
                        onEditClick = { onEditClick(rootPost) },
                        onDeleteClick = { onDeleteClick(rootPost) },
                        onImageClick = onImageClick,
                        onDownloadPhoto = onDownloadPhoto,
                        onDownloadAttachment = onDownloadAttachment
                    )
                }

                item(key = "replies_header") {
                    Text(
                        text = if (replies.isEmpty()) "No replies yet" else "Replies (${replies.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                if (replies.isEmpty()) {
                    item(key = "empty_replies") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp)
                        ) {
                            Text(
                                text = "Be the first to add to this thread.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(
                        items = replies,
                        key = { it.id }
                    ) { reply ->
                        ThreadPostRow(
                            post = reply,
                            isLiked = reply.id in likedPostIds,
                            isReposted = reply.id in repostedPostIds,
                            isSaved = reply.id in savedPostIds,
                            isOwnPost = reply.authorId == currentUserId,
                            isFollowingAuthor = reply.authorId in followingIds,
                            showThreadLine = false,
                            onPostClick = {},
                            onQuotedPostClick = onQuotedPostClick,
                            onAuthorClick = { onAuthorClick(reply.authorId) },
                            onLikeClick = { onLikeClick(reply) },
                            onReplyClick = { if (canReplyToRoot) onReplyModalClick(reply) },
                            onRepostClick = { onRepostClick(reply) },
                            onSaveClick = { onSaveClick(reply) },
                            onFollowAuthorClick = { onFollowClick(reply.authorId) },
                            onEditClick = { onEditClick(reply) },
                            onDeleteClick = { onDeleteClick(reply) },
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
