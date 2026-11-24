package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.components.LocalOrbitModerationActions
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.data.model.PostAttachment
import com.example.data.model.ThreadPost
import com.example.data.model.formatCompactCount
import com.example.data.model.formatRelativeTime
import com.example.data.model.isGifUrl
import com.example.ui.components.GifCornerBadge
import com.example.ui.components.LocalPhotoPeekController
import com.example.ui.components.PhotoPeekRequest
import com.example.ui.components.PostAttachmentCard
import com.example.ui.components.ThreadAvatar
import com.example.ui.components.ThreadPollCard
import com.example.ui.components.parseOrbitRichText
import androidx.compose.material.icons.outlined.Share
import com.example.ui.haptics.BindScrollHaptics
import com.example.ui.haptics.LocalOrbitHaptics
import com.example.ui.share.OrbitShareHelper
import com.example.ui.theme.OrbitHeartRed
import com.example.ui.theme.OrbitRepostGreen
import kotlinx.coroutines.delay

/**
 * Instagram-style visual "Frames" feed displaying only photo and video posts
 * with edge-to-edge media, multi-photo swipe pager, double-tap heart burst,
 * long-press Instagram Peek & Pop, and smooth scroll haptics.
 */
@Composable
fun FramesFeedContent(
    posts: List<ThreadPost>,
    currentUserId: String,
    likedPostIds: Set<String>,
    repostedPostIds: Set<String>,
    savedPostIds: Set<String> = emptySet(),
    followingIds: Set<String>,
    listState: LazyListState,
    onComposeClick: () -> Unit,
    onPostClick: (ThreadPost) -> Unit,
    onAuthorClick: (String) -> Unit,
    onLikeClick: (ThreadPost) -> Unit,
    onReplyClick: (ThreadPost) -> Unit,
    onRepostClick: (ThreadPost) -> Unit,
    onSaveClick: (ThreadPost) -> Unit = {},
    onFollowClick: (String) -> Unit,
    onEditClick: (ThreadPost) -> Unit,
    onDeleteClick: (ThreadPost) -> Unit,
    onImageClick: (String) -> Unit,
    onDownloadPhoto: (String) -> Unit,
    onDownloadAttachment: (PostAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    BindScrollHaptics(listState)

    val visualPosts = remember(posts) {
        posts.filter { it.isTopLevel && it.hasVisualMedia }
    }

    if (visualPosts.isEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .testTag("frames_empty_state")
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Frames is quiet right now",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Share a photo or video thread and it will appear right here in full-bleed Frames.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onComposeClick,
                modifier = Modifier.testTag("frames_empty_post_btn")
            ) {
                Text("Share a photo or video")
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 28.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("frames_lazy_column")
    ) {
        items(items = visualPosts, key = { "frame_${it.id}" }) { post ->
            FrameInstagramPostCard(
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
                onFollowClick = { onFollowClick(post.authorId) },
                onEditClick = { onEditClick(post) },
                onDeleteClick = { onDeleteClick(post) },
                onImageClick = onImageClick,
                onDownloadPhoto = onDownloadPhoto,
                onDownloadAttachment = onDownloadAttachment
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrameInstagramPostCard(
    post: ThreadPost,
    isLiked: Boolean,
    isReposted: Boolean,
    isSaved: Boolean,
    isOwnPost: Boolean,
    isFollowingAuthor: Boolean,
    onPostClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    onRepostClick: () -> Unit,
    onSaveClick: () -> Unit,
    onFollowClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onDownloadPhoto: (String) -> Unit,
    onDownloadAttachment: (PostAttachment) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalOrbitHaptics.current
    val peekController = LocalPhotoPeekController.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    var menuExpanded by remember { mutableStateOf(false) }
    var showHeartBurst by remember { mutableStateOf(false) }

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(650)
            showHeartBurst = false
        }
    }

    val photoItems = remember(post.imageUrls, post.attachments) {
        buildList {
            addAll(post.imageUrls)
            post.attachments.filter { it.isImage }.forEach { add(it.dataUri) }
        }
    }
    val videoAttachments = remember(post.attachments) {
        post.attachments.filter { it.isVideo }
    }

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "frame_like_scale"
    )
    val saveScale by animateFloatAsState(
        targetValue = if (isSaved) 1.16f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "frame_save_scale"
    )
    val heartTint by animateColorAsState(
        targetValue = if (isLiked) OrbitHeartRed else MaterialTheme.colorScheme.onSurface,
        label = "frame_heart_tint"
    )
    val repostTint by animateColorAsState(
        targetValue = if (isReposted) OrbitRepostGreen else MaterialTheme.colorScheme.onSurface,
        label = "frame_repost_tint"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("frame_post_card_${post.id}")
    ) {
        // Instagram Header Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            ThreadAvatar(
                avatarUrl = post.authorAvatarUrl,
                displayName = post.authorDisplayName.ifBlank { post.authorUsername },
                size = 36.dp,
                onClick = onAuthorClick
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAuthorClick)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.authorUsername.ifBlank { post.authorDisplayName },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (post.edited) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(edited)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = formatRelativeTime(post.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isOwnPost && !isFollowingAuthor) {
                OutlinedButton(
                    onClick = {
                        haptics.performTap()
                        onFollowClick()
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("frame_follow_btn_${post.id}")
                ) {
                    Text(
                        text = "Follow",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            Box {
                IconButton(
                    onClick = {
                        haptics.performTap()
                        menuExpanded = true
                    },
                    modifier = Modifier.testTag("frame_more_btn_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreHoriz,
                        contentDescription = "More options"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open thread") },
                        onClick = {
                            menuExpanded = false
                            onPostClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share frame") },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            haptics.performShare()
                            OrbitShareHelper.shareThreadPost(context, post)
                        }
                    )
                    if (post.body.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("Copy caption") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                clipboard.setText(AnnotatedString(post.body))
                            }
                        )
                    }
                    if (isOwnPost) {
                        DropdownMenuItem(
                            text = { Text("Edit post") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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
                        val moderation = LocalOrbitModerationActions.current
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
                                Icon(Icons.AutoMirrored.Outlined.VolumeOff, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                haptics.performMedium()
                                moderation.onToggleMuteUser(post.authorId)
                            },
                            modifier = Modifier.testTag("frame_mute_${post.id}")
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
                                    Icons.Outlined.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                haptics.performHeavy()
                                moderation.onToggleBlockUser(post.authorId)
                            },
                            modifier = Modifier.testTag("frame_block_${post.id}")
                        )
                    }
                }
            }
        }

        // Edge-to-Edge Media Carousel (Photos)
        if (photoItems.isNotEmpty()) {
            val pagerState = rememberPagerState(pageCount = { photoItems.size })

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.92f)
                    .background(Color(0xFF0A0A0D))
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val imgUrl = photoItems[page]
                    val model = remember(imgUrl) { OrbitCacheManager.toCoilModel(imgUrl) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = model,
                            imageLoader = imageLoader,
                            contentDescription = "Frame photo ${page + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = { onImageClick(imgUrl) },
                                    onDoubleClick = {
                                        haptics.performLike(isLiking = true)
                                        showHeartBurst = true
                                        if (!isLiked) onLikeClick()
                                    },
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
                                    .padding(12.dp)
                            )
                        }
                    }
                }

                // Multi-photo counter pill (top-right like Instagram)
                if (photoItems.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.62f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${photoItems.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                // Double-tap animated heart burst
                androidx.compose.animation.AnimatedVisibility(
                    visible = showHeartBurst,
                    enter = scaleIn(
                        initialScale = 0.4f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f)
                    ) + fadeIn(),
                    exit = scaleOut(targetScale = 1.25f) + fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.94f),
                        modifier = Modifier.size(92.dp)
                    )
                }
            }

            // Carousel indicator dots
            if (photoItems.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    repeat(photoItems.size) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (active) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
            }
        }

        // Inline Videos (if post includes video attachments)
        if (videoAttachments.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                videoAttachments.forEachIndexed { idx, video ->
                    PostAttachmentCard(
                        attachment = video,
                        cacheKey = "frame_${post.id}_vid_$idx",
                        onImageClick = onImageClick,
                        onDownloadClick = { onDownloadAttachment(video) }
                    )
                }
            }
        }

        // Instagram Action Row (Like, Comment, Repost, Share, Download on right)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            com.example.ui.components.AnimatedHeartIconButton(
                isLiked = isLiked,
                onClick = {
                    haptics.performLike(isLiking = !isLiked)
                    onLikeClick()
                },
                testTag = "frame_like_btn_${post.id}"
            )

            IconButton(
                onClick = {
                    haptics.performTap()
                    onReplyClick()
                },
                modifier = Modifier.testTag("frame_comment_btn_${post.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Comment",
                    modifier = Modifier.size(23.dp)
                )
            }

            IconButton(
                onClick = {
                    haptics.performTap()
                    onRepostClick()
                },
                modifier = Modifier.testTag("frame_repost_btn_${post.id}")
            ) {
                Icon(
                    imageVector = if (isReposted) Icons.Filled.Repeat else Icons.Outlined.Repeat,
                    contentDescription = "Repost or quote",
                    tint = repostTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = {
                    haptics.performShare()
                    OrbitShareHelper.shareThreadPost(context, post)
                },
                modifier = Modifier.testTag("frame_share_btn_${post.id}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "Share",
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            if (photoItems.isNotEmpty()) {
                IconButton(
                    onClick = {
                        haptics.performTap()
                        onDownloadPhoto(photoItems.first())
                    },
                    modifier = Modifier.testTag("frame_download_btn_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Download photo",
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    haptics.performSave(isSaving = !isSaved)
                    onSaveClick()
                },
                modifier = Modifier.testTag("frame_save_btn_${post.id}")
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (isSaved) "Remove saved post" else "Save post",
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(23.dp)
                        .scale(saveScale)
                )
            }
        }

        // Likes count + Instagram Caption + View Comments
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 14.dp)
        ) {
            if (post.likeCount > 0L) {
                Text(
                    text = "${formatCompactCount(post.likeCount)} ${if (post.likeCount == 1L) "like" else "likes"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (post.body.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                val primaryColor = MaterialTheme.colorScheme.primary
                val captionAnnotated = remember(post.authorUsername, post.authorDisplayName, post.body, primaryColor) {
                    val trimmedBody = post.body.trimStart()
                    val startsWithBullet = trimmedBody.startsWith("• ") ||
                        trimmedBody.startsWith("- ") ||
                        trimmedBody.startsWith("* ")
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(post.authorUsername.ifBlank { post.authorDisplayName })
                        }
                        append(if (startsWithBullet) "\n" else "  ")
                        append(parseOrbitRichText(post.body, linkColor = primaryColor, bulletColor = primaryColor))
                    }
                }
                Text(
                    text = captionAnnotated,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable(onClick = onPostClick)
                )
            }

            if (post.hasPoll) {
                Spacer(Modifier.height(8.dp))
                ThreadPollCard(post = post)
            }

            if (post.replyCount > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "View all ${formatCompactCount(post.replyCount)} ${if (post.replyCount == 1L) "comment" else "comments"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onPostClick)
                )
            }
        }

        HorizontalDivider(
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    }
}
