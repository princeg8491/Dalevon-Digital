package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.cache.OrbitMediaFiles
import com.example.data.model.PostAttachment
import com.example.data.model.ThreadPost
import com.example.data.model.buildLinkThumbnailUrl
import com.example.data.model.extractHostFromUrl
import com.example.data.model.extractUrlsFromText
import com.example.data.model.formatCompactCount
import com.example.data.model.formatRelativeTime
import com.example.data.model.isGifUrl
import com.example.data.model.normalizeWebUrl
import com.example.data.preferences.DarkThemeMode
import com.example.data.preferences.OrbitNavSlot
import com.example.data.preferences.OrbitThemeConfig
import com.example.ui.adaptive.FoldableTabletopSplitLayout
import com.example.ui.adaptive.LocalFoldableLayoutInfo
import androidx.compose.material.icons.outlined.Share
import com.example.ui.haptics.LocalOrbitHaptics
import com.example.ui.share.OrbitShareHelper
import com.example.ui.theme.OrbitHeartRed
import com.example.ui.theme.OrbitRepostGreen
import com.example.ui.viewmodel.MainDestination
import java.io.File

/**
 * Shared moderation callbacks for 3-dot overflow menus across feeds, frames, profiles, and spaces.
 * Muted users are stored privately and never notified; both muted and blocked users appear in
 * Privacy settings.
 */
data class OrbitUserModerationActions(
    val blockedUserIds: Set<String> = emptySet(),
    val mutedUserIds: Set<String> = emptySet(),
    val onToggleMuteUser: (String) -> Unit = {},
    val onToggleBlockUser: (String) -> Unit = {}
)

val LocalOrbitModerationActions = staticCompositionLocalOf { OrbitUserModerationActions() }

/**
 * Minimalist Threads-inspired orbital mark drawn crisply on Canvas.
 */
@Composable
fun OrbitSpiralMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.09f
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension * 0.42f
        val innerRadius = size.minDimension * 0.19f

        // Outer open orbital arc (Threads-inspired continuous loop)
        drawArc(
            color = color,
            startAngle = -35f,
            sweepAngle = 310f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Inner planetary ring
        drawCircle(
            color = accentColor,
            radius = innerRadius,
            center = center,
            style = Stroke(width = strokeWidth * 0.92f)
        )

        // Orbital satellite dot along the open gap
        val dotAngleRad = Math.toRadians(-12.0)
        val dotCenter = Offset(
            x = center.x + (outerRadius * kotlin.math.cos(dotAngleRad)).toFloat(),
            y = center.y + (outerRadius * kotlin.math.sin(dotAngleRad)).toFloat()
        )
        drawCircle(
            color = accentColor,
            radius = strokeWidth * 0.72f,
            center = dotCenter
        )
    }
}

@Composable
fun ThreadAvatar(
    avatarUrl: String,
    displayName: String,
    size: Dp = 40.dp,
    showQuickFollowBadge: Boolean = false,
    onQuickFollowClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    val model = remember(avatarUrl) { OrbitCacheManager.toCoilModel(avatarUrl) }
    val initial = remember(displayName) {
        displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "O"
    }

    Box(
        modifier = modifier
            .size(size)
            .let { base ->
                if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    imageLoader = imageLoader,
                    contentDescription = "$displayName avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showQuickFollowBadge && onQuickFollowClick != null) {
            Surface(
                onClick = onQuickFollowClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.action_follow),
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadPostRow(
    post: ThreadPost,
    isLiked: Boolean,
    isReposted: Boolean,
    isSaved: Boolean = false,
    isOwnPost: Boolean,
    isFollowingAuthor: Boolean = false,
    showThreadLine: Boolean = post.replyCount > 0,
    repostedHeaderLabel: String? = null,
    onPostClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    onRepostClick: () -> Unit,
    onSaveClick: () -> Unit = {},
    onFollowAuthorClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit,
    onQuotedPostClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onDownloadPhoto: (String) -> Unit = {},
    onDownloadAttachment: (PostAttachment) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalOrbitHaptics.current
    val peekController = LocalPhotoPeekController.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    val moderation = LocalOrbitModerationActions.current
    var menuExpanded by remember { mutableStateOf(false) }

    val extractedLinks = remember(post.body) { extractUrlsFromText(post.body) }

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.22f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heart_scale"
    )
    val saveScale by animateFloatAsState(
        targetValue = if (isSaved) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "save_scale"
    )
    val heartTint by animateColorAsState(
        targetValue = if (isLiked) OrbitHeartRed else MaterialTheme.colorScheme.onSurface,
        label = "heart_tint"
    )
    val repostTint by animateColorAsState(
        targetValue = if (isReposted) OrbitRepostGreen else MaterialTheme.colorScheme.onSurface,
        label = "repost_tint"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPostClick)
            .testTag("thread_post_card_${post.id}")
    ) {
        if (!repostedHeaderLabel.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 52.dp, top = 10.dp, bottom = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = repostedHeaderLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
        ) {
            // Left rail: Avatar + vertical Threads connector line
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(42.dp)
            ) {
                ThreadAvatar(
                    avatarUrl = post.authorAvatarUrl,
                    displayName = post.authorDisplayName.ifBlank { post.authorUsername },
                    size = 38.dp,
                    showQuickFollowBadge = !isOwnPost && !isFollowingAuthor,
                    onQuickFollowClick = {
                        haptics.performTap()
                        onFollowAuthorClick()
                    },
                    onClick = onAuthorClick,
                    modifier = Modifier.testTag("post_avatar_${post.id}")
                )

                if (showThreadLine) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(1.75.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Right column: Header, Body, Links, Quoted Post, Media Strip, Action Row, Counts
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = post.authorUsername.ifBlank { post.authorDisplayName },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(onClick = onAuthorClick)
                        )

                        if (post.edited) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "(edited)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (post.topic.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = post.topic,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (post.audienceMode == "followers_only" || post.replyMode != "everyone") {
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Restricted audience or replies",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Flush to right edge: relative time + 3-dot menu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = formatRelativeTime(post.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box {
                            IconButton(
                                onClick = {
                                    haptics.performTap()
                                    menuExpanded = true
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("post_more_${post.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreHoriz,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isSaved) "Remove from Saved" else "Save post") },
                                    leadingIcon = {
                                        Icon(
                                            if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        haptics.performSave(isSaving = !isSaved)
                                        onSaveClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share post") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Share, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        haptics.performShare()
                                        OrbitShareHelper.shareThreadPost(context, post)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("View profile") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Person, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAuthorClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy text") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        clipboard.setText(AnnotatedString(post.body))
                                    }
                                )
                            if (isOwnPost) {
                                DropdownMenuItem(
                                    text = { Text("Edit post") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onEditClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Delete",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDeleteClick()
                                    }
                                )
                            } else if (post.authorId.isNotBlank()) {
                                val handle = post.authorUsername.ifBlank { post.authorDisplayName.ifBlank { "user" } }
                                val isMutedAuthor = post.authorId in moderation.mutedUserIds
                                val isBlockedAuthor = post.authorId in moderation.blockedUserIds
                                HorizontalDivider(thickness = 0.6.dp)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isMutedAuthor) {
                                                stringResource(R.string.action_unmute_user, handle)
                                            } else {
                                                stringResource(R.string.action_mute_user, handle)
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.VolumeOff,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        haptics.performMedium()
                                        moderation.onToggleMuteUser(post.authorId)
                                    },
                                    modifier = Modifier.testTag("post_mute_${post.id}")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isBlockedAuthor) {
                                                stringResource(R.string.action_unblock_user, handle)
                                            } else {
                                                stringResource(R.string.action_block_user, handle)
                                            },
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Block,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        haptics.performHeavy()
                                        moderation.onToggleBlockUser(post.authorId)
                                    },
                                    modifier = Modifier.testTag("post_block_${post.id}")
                                )
                            }
                        }
                    }
                    }
                }

                if (post.body.isNotBlank()) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val formattedBody = remember(post.body, primaryColor) {
                        parseOrbitRichText(
                            raw = post.body,
                            linkColor = primaryColor,
                            bulletColor = primaryColor
                        )
                    }
                    Text(
                        text = formattedBody,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                    )
                }

                // Rich Clickable Link Preview Cards with site thumbnails
                if (extractedLinks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        extractedLinks.take(2).forEach { rawUrl ->
                            InlineLinkThumbnailCard(
                                title = extractHostFromUrl(rawUrl).ifBlank { rawUrl },
                                url = rawUrl
                            )
                        }
                    }
                }

                // Interactive Poll Card
                if (post.hasPoll) {
                    Spacer(Modifier.height(10.dp))
                    ThreadPollCard(post = post)
                }

                // Embedded Quoted Post Card (Quote Repost)
                if (post.hasQuotedPost) {
                    Spacer(Modifier.height(8.dp))
                    QuotedPostCard(
                        authorUsername = post.quotedAuthorUsername,
                        authorDisplayName = post.quotedAuthorDisplayName,
                        authorAvatarUrl = post.quotedAuthorAvatarUrl,
                        body = post.quotedBody,
                        imageUrl = post.quotedImageUrl,
                        onClick = {
                            haptics.performTap()
                            onQuotedPostClick(post.quotedPostId)
                        }
                    )
                }

                // Threads horizontal photo carousel with Instagram-style long-press Peek & Pop
                if (post.imageUrls.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    if (post.imageUrls.size == 1) {
                        val imgUrl = post.imageUrls.first()
                        val model = remember(imgUrl) { OrbitCacheManager.toCoilModel(imgUrl) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = model,
                                imageLoader = imageLoader,
                                contentDescription = "Attached thread photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        0.75.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .combinedClickable(
                                        onClick = { onImageClick(imgUrl) },
                                        onLongClick = {
                                            haptics.performHeavy()
                                            peekController.show(
                                                PhotoPeekRequest(
                                                    imageUrl = imgUrl,
                                                    authorUsername = post.authorUsername.ifBlank { post.authorDisplayName },
                                                    authorAvatarUrl = post.authorAvatarUrl,
                                                    caption = post.body,
                                                    isLiked = isLiked,
                                                    onLikeClick = onLikeClick,
                                                    onDownloadClick = { onDownloadPhoto(imgUrl) },
                                                    onOpenFullScreen = { onImageClick(imgUrl) }
                                                )
                                            )
                                        }
                                    )
                            )
                            if (isGifUrl(imgUrl)) {
                                GifCornerBadge(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                )
                            }
                            Surface(
                                onClick = {
                                    haptics.performTap()
                                    onDownloadPhoto(imgUrl)
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.56f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(34.dp)
                                    .testTag("download_photo_btn_${post.id}_0")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = "Download photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(end = 8.dp)
                        ) {
                            itemsIndexed(post.imageUrls) { index, imgUrl ->
                                val model = remember(imgUrl) { OrbitCacheManager.toCoilModel(imgUrl) }
                                Box(modifier = Modifier.size(210.dp)) {
                                    AsyncImage(
                                        model = model,
                                        imageLoader = imageLoader,
                                        contentDescription = "Attached photo ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(
                                                0.75.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .combinedClickable(
                                                onClick = { onImageClick(imgUrl) },
                                                onLongClick = {
                                                    haptics.performHeavy()
                                                    peekController.show(
                                                        PhotoPeekRequest(
                                                            imageUrl = imgUrl,
                                                            authorUsername = post.authorUsername.ifBlank { post.authorDisplayName },
                                                            authorAvatarUrl = post.authorAvatarUrl,
                                                            caption = post.body,
                                                            isLiked = isLiked,
                                                            onLikeClick = onLikeClick,
                                                            onDownloadClick = { onDownloadPhoto(imgUrl) },
                                                            onOpenFullScreen = { onImageClick(imgUrl) }
                                                        )
                                                    )
                                                }
                                            )
                                    )
                                    if (isGifUrl(imgUrl)) {
                                        GifCornerBadge(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = {
                                            haptics.performTap()
                                            onDownloadPhoto(imgUrl)
                                        },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.56f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .size(32.dp)
                                            .testTag("download_photo_btn_${post.id}_$index")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = "Download photo ${index + 1}",
                                                tint = Color.White,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // File and video attachments with inline preview & one-tap download
                if (post.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        post.attachments.forEachIndexed { idx, attachment ->
                            PostAttachmentCard(
                                attachment = attachment,
                                cacheKey = "${post.id}_att_$idx",
                                onImageClick = onImageClick,
                                onDownloadClick = { onDownloadAttachment(attachment) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Engagement Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.offset(x = (-10).dp)
                ) {
                    AnimatedHeartActionChip(
                        count = post.likeCount.toInt(),
                        isLiked = isLiked,
                        onClick = {
                            haptics.performLike(isLiking = !isLiked)
                            onLikeClick()
                        },
                        testTag = "like_button_${post.id}"
                    )

                    ThreadActionChip(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Reply to thread",
                        count = post.replyCount,
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            haptics.performTap()
                            onReplyClick()
                        },
                        testTag = "reply_button_${post.id}"
                    )

                    ThreadActionChip(
                        icon = if (isReposted) Icons.Filled.Repeat else Icons.Outlined.Repeat,
                        contentDescription = if (isReposted) "Repost options" else "Repost or quote",
                        count = post.repostCount,
                        tint = repostTint,
                        onClick = {
                            haptics.performTap()
                            onRepostClick()
                        },
                        testTag = "repost_button_${post.id}"
                    )

                    ThreadActionChip(
                        icon = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share thread",
                        count = 0L,
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            haptics.performShare()
                            OrbitShareHelper.shareThreadPost(context, post)
                        },
                        testTag = "share_button_${post.id}"
                    )

                    Spacer(Modifier.weight(1f))

                    ThreadActionChip(
                        icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isSaved) "Remove saved post" else "Save post",
                        count = 0L,
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        iconScale = if (isSaved) saveScale else 1f,
                        onClick = {
                            haptics.performSave(isSaving = !isSaved)
                            onSaveClick()
                        },
                        testTag = "save_button_${post.id}"
                    )
                }

                if (post.replyCount > 0 || post.likeCount > 0) {
                    val summaryParts = buildList {
                        if (post.replyCount > 0) {
                            val label = if (post.replyCount == 1L) "reply" else "replies"
                            add("${formatCompactCount(post.replyCount)} $label")
                        }
                        if (post.likeCount > 0) {
                            val label = if (post.likeCount == 1L) "like" else "likes"
                            add("${formatCompactCount(post.likeCount)} $label")
                        }
                    }.joinToString("  ·  ")
                    Text(
                        text = summaryParts,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InlineLinkThumbnailCard(
    title: String,
    url: String,
    thumbnailUrl: String = "",
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    val normalized = remember(url) { normalizeWebUrl(url) }
    val host = remember(normalized) { extractHostFromUrl(normalized) }
    val resolvedThumb = remember(thumbnailUrl, normalized) {
        thumbnailUrl.ifBlank { buildLinkThumbnailUrl(normalized) }
    }

    val openLink = {
        haptics.performTap()
        if (normalized.isNotBlank()) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = openLink,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    if (resolvedThumb.isNotBlank()) {
                        AsyncImage(
                            model = resolvedThumb,
                            imageLoader = imageLoader,
                            contentDescription = "$title thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { host.ifBlank { normalized } },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = host.ifBlank { normalized },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Open link",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun QuotedPostCard(
    authorUsername: String,
    authorDisplayName: String,
    authorAvatarUrl: String,
    body: String,
    imageUrl: String = "",
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)
    val thumbModel = remember(imageUrl) { OrbitCacheManager.toCoilModel(imageUrl) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThreadAvatar(
                    avatarUrl = authorAvatarUrl,
                    displayName = authorDisplayName.ifBlank { authorUsername },
                    size = 20.dp
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = authorUsername.ifBlank { authorDisplayName },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (body.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                val primaryColor = MaterialTheme.colorScheme.primary
                val formattedQuote = remember(body, primaryColor) {
                    parseOrbitRichText(body, linkColor = primaryColor, bulletColor = primaryColor)
                }
                Text(
                    text = formattedQuote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (thumbModel != null) {
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = thumbModel,
                    imageLoader = imageLoader,
                    contentDescription = "Quoted post photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

@Composable
private fun ThreadActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    count: Long,
    tint: Color,
    iconScale: Float = 1f,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .scale(iconScale)
        )
        if (count > 0L) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatCompactCount(count),
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}

@Composable
fun PostAttachmentCard(
    attachment: PostAttachment,
    cacheKey: String,
    onImageClick: (String) -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    when {
        attachment.isVideo -> {
            var isPlaying by remember(cacheKey) { mutableStateOf(false) }
            var videoFile by remember(cacheKey) { mutableStateOf<File?>(null) }
            val posterDataUri = remember(attachment.dataUri) {
                OrbitMediaFiles.extractPosterFromVideoDataUri(attachment.dataUri)
            }
            val posterModel = remember(posterDataUri) {
                OrbitCacheManager.toCoilModel(posterDataUri)
            }

            LaunchedEffect(isPlaying, attachment.dataUri) {
                if (isPlaying && videoFile == null) {
                    videoFile = OrbitMediaFiles.materializeVideoToTempFile(
                        context = context,
                        dataUri = attachment.dataUri,
                        cacheKey = cacheKey
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("video_attachment_card_$cacheKey")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(Color(0xFF0E0E12))
                            .clickable(enabled = !isPlaying) { isPlaying = true }
                    ) {
                        val readyFile = videoFile
                        if (isPlaying && readyFile != null) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val controller = MediaController(ctx)
                                        controller.setAnchorView(this)
                                        setMediaController(controller)
                                        setVideoURI(Uri.fromFile(readyFile))
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = false
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            if (posterModel != null) {
                                AsyncImage(
                                    model = posterModel,
                                    imageLoader = imageLoader,
                                    contentDescription = attachment.fileName.ifBlank { "Video poster preview" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.36f))
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Play video",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (isPlaying) "Preparing video…" else "Tap to play video",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.92f)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.fileName.ifBlank { "Video clip" },
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = OrbitMediaFiles.formatFileSize(attachment.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier.testTag("download_attachment_btn_$cacheKey")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = "Download video",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        attachment.isImage -> {
            val model = remember(attachment.dataUri) { OrbitCacheManager.toCoilModel(attachment.dataUri) }
            Box(modifier = modifier.fillMaxWidth()) {
                AsyncImage(
                    model = model,
                    imageLoader = imageLoader,
                    contentDescription = attachment.fileName.ifBlank { "Attached photo" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable { onImageClick(attachment.dataUri) }
                )
                Surface(
                    onClick = onDownloadClick,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .testTag("download_attachment_btn_$cacheKey")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Download image",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        else -> {
            val extLabel = remember(attachment.fileName, attachment.mimeType) {
                val fromName = attachment.fileName.substringAfterLast('.', "").uppercase()
                when {
                    fromName.isNotBlank() && fromName.length <= 5 -> fromName
                    else -> attachment.mimeType.substringAfterLast('/').uppercase().take(6).ifBlank { "FILE" }
                }
            }
            val fileIcon = if (attachment.mimeType.contains("pdf") || attachment.mimeType.contains("text")) {
                Icons.Outlined.Description
            } else {
                Icons.AutoMirrored.Outlined.InsertDriveFile
            }

            Surface(
                onClick = onDownloadClick,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("file_attachment_card_$cacheKey")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = fileIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.fileName.ifBlank { "Attachment" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$extLabel  ·  ${OrbitMediaFiles.formatFileSize(attachment.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.testTag("download_attachment_btn_$cacheKey")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Download file",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// FullScreenImageLightbox moved to FullScreenImageLightbox.kt in the same package.


@Composable
fun ThemeSettingsSheet(
    themeConfig: OrbitThemeConfig,
    onDynamicWallpaperChanged: (Boolean) -> Unit,
    onDarkThemeModeChanged: (DarkThemeMode) -> Unit,
    onCustomSeedColorChanged: (Long?) -> Unit = {},
    onDismiss: () -> Unit
) {
    ThemeStudioSheet(
        themeConfig = themeConfig,
        onDynamicWallpaperChanged = onDynamicWallpaperChanged,
        onDarkThemeModeChanged = onDarkThemeModeChanged,
        onCustomSeedColorChanged = onCustomSeedColorChanged,
        onDismiss = onDismiss
    )
}

/**
 * Customizable bottom navigation bar supporting up to 6 icons (Home, Search, Compose, Spaces, DMs, Activity, Profile).
 * Long-pressing any icon opens an instant rearrangement & toggle sheet!
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThreadsBottomBar(
    currentDestination: MainDestination,
    slots: List<OrbitNavSlot> = OrbitNavSlot.DEFAULT_ENABLED,
    isSpacesSelected: Boolean = false,
    isDmsSelected: Boolean = false,
    hasActivityItems: Boolean,
    hasUnreadMessages: Boolean = false,
    onSelectDestination: (MainDestination) -> Unit,
    onOpenSpaces: () -> Unit = {},
    onOpenDms: () -> Unit = {},
    onComposeClick: () -> Unit,
    onUpdateSlots: (List<OrbitNavSlot>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var showCustomizeSheet by remember { mutableStateOf(false) }

    val activeSlots = remember(slots) {
        slots.distinct().take(OrbitNavSlot.MAX_SLOTS).ifEmpty { OrbitNavSlot.DEFAULT_ENABLED }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(
                thickness = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 4.dp)
            ) {
                activeSlots.forEach { slot ->
                    when (slot) {
                        OrbitNavSlot.COMPOSE -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 38.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .combinedClickable(
                                        onClick = {
                                            haptics.performTap()
                                            onComposeClick()
                                        },
                                        onLongClick = {
                                            haptics.performHeavy()
                                            showCustomizeSheet = true
                                        }
                                    )
                                    .testTag("nav_compose_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.tab_compose),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }

                        OrbitNavSlot.HOME -> BottomNavIconButton(
                            selected = !isSpacesSelected && !isDmsSelected && currentDestination == MainDestination.HOME,
                            selectedIcon = Icons.Filled.Home,
                            unselectedIcon = Icons.Outlined.Home,
                            contentDescription = stringResource(R.string.tab_home),
                            onClick = {
                                haptics.performTap()
                                onSelectDestination(MainDestination.HOME)
                            },
                            onLongClick = {
                                haptics.performHeavy()
                                showCustomizeSheet = true
                            },
                            testTag = "nav_home_button"
                        )

                        OrbitNavSlot.SEARCH -> BottomNavIconButton(
                            selected = !isSpacesSelected && !isDmsSelected && currentDestination == MainDestination.SEARCH,
                            selectedIcon = Icons.Filled.Search,
                            unselectedIcon = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.tab_search),
                            onClick = {
                                haptics.performTap()
                                onSelectDestination(MainDestination.SEARCH)
                            },
                            onLongClick = {
                                haptics.performHeavy()
                                showCustomizeSheet = true
                            },
                            testTag = "nav_search_button"
                        )

                        OrbitNavSlot.SPACES -> BottomNavIconButton(
                            selected = isSpacesSelected,
                            selectedIcon = Icons.Filled.Groups,
                            unselectedIcon = Icons.Outlined.Groups,
                            contentDescription = stringResource(R.string.tab_spaces),
                            onClick = {
                                haptics.performTap()
                                onOpenSpaces()
                            },
                            onLongClick = {
                                haptics.performHeavy()
                                showCustomizeSheet = true
                            },
                            testTag = "nav_spaces_button"
                        )

                        OrbitNavSlot.DMS -> BottomNavIconButton(
                            selected = isDmsSelected,
                            selectedIcon = Icons.Filled.Mail,
                            unselectedIcon = Icons.Outlined.MailOutline,
                            contentDescription = stringResource(R.string.tab_dms),
                            showDot = hasUnreadMessages && !isDmsSelected,
                            onClick = {
                                haptics.performTap()
                                onOpenDms()
                            },
                            onLongClick = {
                                haptics.performHeavy()
                                showCustomizeSheet = true
                            },
                            testTag = "nav_dms_button"
                        )

                        OrbitNavSlot.ACTIVITY -> BottomNavIconButton(
                            selected = !isSpacesSelected && !isDmsSelected && currentDestination == MainDestination.ACTIVITY,
                            selectedIcon = Icons.Filled.Favorite,
                            unselectedIcon = Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.tab_activity),
                            showDot = hasActivityItems && !(!isSpacesSelected && !isDmsSelected && currentDestination == MainDestination.ACTIVITY),
                            onClick = {
                                haptics.performTap()
                                onSelectDestination(MainDestination.ACTIVITY)
                            },
                            onLongClick = {
                                haptics.performHeavy()
                                showCustomizeSheet = true
                            },
                            testTag = "nav_activity_button"
                        )

                        OrbitNavSlot.PROFILE -> BottomNavIconButton(
                            selected = !isSpacesSelected && !isDmsSelected && currentDestination == MainDestination.PROFILE,
                            selectedIcon = Icons.Filled.Person,
                            unselectedIcon = Icons.Outlined.Person,
                            contentDescription = stringResource(R.string.tab_profile),
                            onClick = {
                                haptics.performTap()
                                onSelectDestination(MainDestination.PROFILE)
                            },
                            onLongClick = {
                                haptics.performHeavy()
                                showCustomizeSheet = true
                            },
                            testTag = "nav_profile_button"
                        )
                    }
                }
            }
        }
    }

    if (showCustomizeSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCustomizeSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding()
                    .testTag("customize_nav_bar_sheet")
            ) {
                Text(
                    text = "Customize Navigation Bar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Toggle up to ${OrbitNavSlot.MAX_SLOTS} icons on/off and use the arrows to rearrange their order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                activeSlots.forEachIndexed { idx, slot ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Text(
                            text = "${idx + 1}. ${slot.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    haptics.performTap()
                                    val copy = activeSlots.toMutableList()
                                    val moved = copy.removeAt(idx)
                                    copy.add(idx - 1, moved)
                                    onUpdateSlots(copy)
                                }
                            },
                            enabled = idx > 0,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move left")
                        }
                        IconButton(
                            onClick = {
                                if (idx < activeSlots.lastIndex) {
                                    haptics.performTap()
                                    val copy = activeSlots.toMutableList()
                                    val moved = copy.removeAt(idx)
                                    copy.add(idx + 1, moved)
                                    onUpdateSlots(copy)
                                }
                            },
                            enabled = idx < activeSlots.lastIndex,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move right")
                        }
                        Switch(
                            checked = true,
                            enabled = activeSlots.size > OrbitNavSlot.MIN_SLOTS && slot != OrbitNavSlot.HOME,
                            onCheckedChange = {
                                haptics.performTap()
                                onUpdateSlots(activeSlots.filterNot { it == slot })
                            }
                        )
                    }
                }

                val hiddenSlots = OrbitNavSlot.entries.filter { it !in activeSlots }
                if (hiddenSlots.isNotEmpty()) {
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    hiddenSlots.forEach { slot ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Text(
                                text = slot.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = false,
                                enabled = activeSlots.size < OrbitNavSlot.MAX_SLOTS,
                                onCheckedChange = {
                                    haptics.performTap()
                                    onUpdateSlots(activeSlots + slot)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomNavIconButton(
    selected: Boolean,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    showDot: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    testTag: String
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_icon_scale"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag(testTag)
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .size(25.dp)
                .scale(scale)
        )
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = 10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun ThreadsNavigationRail(
    currentDestination: MainDestination,
    isCommunitiesSelected: Boolean = false,
    isMessagesSelected: Boolean = false,
    hasActivityItems: Boolean = false,
    hasDirectMessages: Boolean = false,
    onSelectDestination: (MainDestination) -> Unit,
    onOpenCommunities: () -> Unit = {},
    onOpenMessages: () -> Unit = {},
    onOpenThemeSettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onComposeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabSelected = !isCommunitiesSelected && !isMessagesSelected
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            ) {
                OrbitSpiralMark(
                    modifier = Modifier.size(30.dp)
                )
                Surface(
                    onClick = onComposeClick,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("rail_compose_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.tab_compose),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) {
        Spacer(Modifier.height(6.dp))
        NavigationRailItem(
            selected = tabSelected && currentDestination == MainDestination.HOME,
            onClick = { onSelectDestination(MainDestination.HOME) },
            icon = {
                Icon(
                    if (tabSelected && currentDestination == MainDestination.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = stringResource(R.string.tab_home)
                )
            },
            label = { Text(stringResource(R.string.tab_home)) },
            modifier = Modifier.testTag("rail_home_button")
        )
        NavigationRailItem(
            selected = tabSelected && currentDestination == MainDestination.SEARCH,
            onClick = { onSelectDestination(MainDestination.SEARCH) },
            icon = {
                Icon(
                    if (tabSelected && currentDestination == MainDestination.SEARCH) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.tab_search)
                )
            },
            label = { Text(stringResource(R.string.tab_search)) },
            modifier = Modifier.testTag("rail_search_button")
        )
        NavigationRailItem(
            selected = isCommunitiesSelected,
            onClick = onOpenCommunities,
            icon = {
                Icon(
                    Icons.Outlined.Groups,
                    contentDescription = stringResource(R.string.communities_title)
                )
            },
            label = { Text("Spaces") },
            modifier = Modifier.testTag("rail_communities_button")
        )
        NavigationRailItem(
            selected = isMessagesSelected,
            onClick = onOpenMessages,
            icon = {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.MailOutline,
                        contentDescription = stringResource(R.string.messages_title)
                    )
                    if (hasDirectMessages && !isMessagesSelected) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-1).dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            },
            label = { Text("DMs") },
            modifier = Modifier.testTag("rail_messages_button")
        )
        NavigationRailItem(
            selected = tabSelected && currentDestination == MainDestination.ACTIVITY,
            onClick = { onSelectDestination(MainDestination.ACTIVITY) },
            icon = {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (tabSelected && currentDestination == MainDestination.ACTIVITY) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.tab_activity)
                    )
                    if (hasActivityItems && !(tabSelected && currentDestination == MainDestination.ACTIVITY)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-1).dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            },
            label = { Text(stringResource(R.string.tab_activity)) },
            modifier = Modifier.testTag("rail_activity_button")
        )
        NavigationRailItem(
            selected = tabSelected && currentDestination == MainDestination.PROFILE,
            onClick = { onSelectDestination(MainDestination.PROFILE) },
            icon = {
                Icon(
                    if (tabSelected && currentDestination == MainDestination.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.tab_profile)
                )
            },
            label = { Text(stringResource(R.string.tab_profile)) },
            modifier = Modifier.testTag("rail_profile_button")
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onOpenThemeSettings,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .testTag("rail_theme_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = stringResource(R.string.theme_settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onOpenAppSettings,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .testTag("rail_settings_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "App Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CenteredThreadsContainer(
    modifier: Modifier = Modifier,
    fillAvailableWidth: Boolean = false,
    content: @Composable () -> Unit
) {
    val foldableInfo = LocalFoldableLayoutInfo.current
    val maxWidth = when {
        fillAvailableWidth || foldableInfo.supportsTwoPane -> Dp.Unspecified
        else -> foldableInfo.singlePaneMaxWidth
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        val innerModifier = if (maxWidth != Dp.Unspecified) {
            Modifier
                .fillMaxSize()
                .widthIn(max = maxWidth)
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = innerModifier) {
            content()
        }
    }
}

@Composable
fun OrbitVideoPlayerDialog(
    attachment: PostAttachment,
    onDismiss: () -> Unit,
    onDownload: () -> Unit = {}
) {
    val context = LocalContext.current
    var videoFile by remember(attachment.dataUri) { mutableStateOf<File?>(null) }

    LaunchedEffect(attachment.dataUri) {
        videoFile = OrbitMediaFiles.materializeVideoToTempFile(
            context = context,
            dataUri = attachment.dataUri,
            cacheKey = "album_vid_${attachment.fileName.hashCode()}_${attachment.dataUri.hashCode()}"
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView, context) {
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            val activityWindow = (context as? Activity)?.window
            val dialogController = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
            val activityController = activityWindow?.let { WindowCompat.getInsetsController(it, it.decorView) }
            listOfNotNull(dialogController, activityController).forEach { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
            onDispose {
                listOfNotNull(dialogController, activityController).forEach { controller ->
                    controller.show(WindowInsetsCompat.Type.statusBars())
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val ready = videoFile
            if (ready != null) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoURI(Uri.fromFile(ready))
                            setOnPreparedListener { mp ->
                                mp.isLooping = false
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Preparing video…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close video",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Interactive thumbnail tile for any uploaded photo or GIF with a tap-to-expand full-screen preview
 * and a remove button.
 */
@Composable
fun UploadedPhotoPreviewTile(
    model: Any?,
    previewLabel: String = "Preview",
    isGif: Boolean = false,
    size: Dp = 104.dp,
    onPreviewClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onPreviewClick)
    ) {
        AsyncImage(
            model = model,
            imageLoader = imageLoader,
            contentDescription = previewLabel,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        if (isGif) {
            GifCornerBadge(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            )
        } else {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ZoomIn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = previewLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        Surface(
            onClick = onRemoveClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.68f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove photo",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Rich attachment preview card for composers (Thread, Space, DM) that renders a visual photo
 * thumbnail for image files and an extracted video frame thumbnail for videos (up to 500 MB),
 * with tap-to-open full-screen photo/video preview before publishing.
 */
@Composable
fun UploadedAttachmentPreviewCard(
    uri: Uri,
    onPreviewImage: (String) -> Unit,
    onPreviewVideo: (PostAttachment) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    val fileName = remember(uri) { OrbitMediaFiles.queryDisplayName(context, uri) }
    val mimeType = remember(uri) { OrbitMediaFiles.queryMimeType(context, uri) }
    val fileSize = remember(uri) { OrbitMediaFiles.queryFileSize(context, uri) }
    val isImage = mimeType.startsWith("image/", ignoreCase = true)
    val isVideo = mimeType.startsWith("video/", ignoreCase = true)

    val videoPosterDataUri by androidx.compose.runtime.produceState<String?>(initialValue = null, uri, isVideo) {
        if (isVideo) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                OrbitMediaFiles.extractVideoFrameDataUri(context, uri = uri)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isImage || isVideo) {
                if (isImage) {
                    onPreviewImage(uri.toString())
                } else if (isVideo) {
                    onPreviewVideo(
                        PostAttachment(
                            fileName = fileName,
                            mimeType = mimeType,
                            sizeBytes = fileSize.coerceAtLeast(0L),
                            dataUri = uri.toString()
                        )
                    )
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            when {
                isImage -> {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = uri,
                            imageLoader = imageLoader,
                            contentDescription = "Uploaded photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                isVideo -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF101016))
                            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        val posterModel = remember(videoPosterDataUri) {
                            OrbitCacheManager.toCoilModel(videoPosterDataUri)
                        }
                        if (posterModel != null) {
                            AsyncImage(
                                model = posterModel,
                                imageLoader = imageLoader,
                                contentDescription = "Video frame preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Black.copy(alpha = 0.35f))
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Preview video",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                else -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sizeLabel = if (fileSize > 0L) OrbitMediaFiles.formatFileSize(fileSize) else ""
                val actionHint = when {
                    isImage -> listOfNotNull(sizeLabel.ifBlank { null }, "Photo · Tap to preview").joinToString(" · ")
                    isVideo -> listOfNotNull(sizeLabel.ifBlank { null }, "Video (max 500 MB) · Tap to preview").joinToString(" · ")
                    else -> listOfNotNull(sizeLabel.ifBlank { null }, "Document attachment").joinToString(" · ")
                }
                Text(
                    text = actionHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove attachment",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}


