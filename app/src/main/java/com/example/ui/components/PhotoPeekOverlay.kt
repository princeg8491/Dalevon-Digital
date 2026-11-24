package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import coil.compose.AsyncImage
import com.example.data.cache.LocalOrbitImageLoader
import com.example.data.cache.OrbitCacheManager
import com.example.ui.haptics.LocalOrbitHaptics
import com.example.ui.share.OrbitShareHelper
import com.example.ui.theme.OrbitHeartRed

data class PhotoPeekRequest(
    val imageUrl: String,
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val caption: String = "",
    val isLiked: Boolean = false,
    val onLikeClick: (() -> Unit)? = null,
    val onDownloadClick: () -> Unit = {},
    val onOpenFullScreen: () -> Unit = {}
)

@Stable
class PhotoPeekController {
    var activeRequest by mutableStateOf<PhotoPeekRequest?>(null)
        private set

    fun show(request: PhotoPeekRequest) {
        activeRequest = request
    }

    fun dismiss() {
        activeRequest = null
    }
}

val LocalPhotoPeekController = staticCompositionLocalOf { PhotoPeekController() }

/**
 * Instagram-style Peek & Pop photo overlay triggered on long-press of any photo across Orbit.
 */
@Composable
fun PhotoPeekOverlayHost(
    controller: PhotoPeekController,
    modifier: Modifier = Modifier
) {
    val request = controller.activeRequest
    val context = LocalContext.current
    val haptics = LocalOrbitHaptics.current
    val imageLoader = LocalOrbitImageLoader.current ?: OrbitCacheManager.getImageLoader(context)

    BackHandler(enabled = request != null) {
        controller.dismiss()
    }

    AnimatedVisibility(
        visible = request != null,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(140)),
        modifier = modifier.fillMaxSize()
    ) {
        val current = request ?: return@AnimatedVisibility
        var localLiked by remember(current.imageUrl, current.isLiked) { mutableStateOf(current.isLiked) }
        val model = remember(current.imageUrl) { OrbitCacheManager.toCoilModel(current.imageUrl) }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.74f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptics.performTap()
                    controller.dismiss()
                }
                .padding(horizontal = 20.dp, vertical = 32.dp)
                .testTag("photo_peek_overlay")
        ) {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(
                    initialScale = 0.84f,
                    animationSpec = spring(
                        dampingRatio = 0.70f,
                        stiffness = 440f
                    )
                ) + fadeIn(tween(120)),
                exit = scaleOut(
                    targetScale = 0.88f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut(tween(110))
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    shadowElevation = 18.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .testTag("photo_peek_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Author Header (Instagram Peek Header)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            ThreadAvatar(
                                avatarUrl = current.authorAvatarUrl,
                                displayName = current.authorUsername.ifBlank { "Orbit" },
                                size = 32.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = current.authorUsername.ifBlank { "Orbit photo" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (current.caption.isNotBlank()) {
                                    Text(
                                        text = current.caption,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    haptics.performTap()
                                    controller.dismiss()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("photo_peek_close_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close preview",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Popped High-Res Image Preview
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 240.dp, max = 440.dp)
                                .aspectRatio(1f, matchHeightConstraintsFirst = false)
                                .background(Color(0xFF0A0A0D))
                                .clickable {
                                    haptics.performTap()
                                    controller.dismiss()
                                    current.onOpenFullScreen()
                                }
                        ) {
                            AsyncImage(
                                model = model,
                                imageLoader = imageLoader,
                                contentDescription = "Peeked photo preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Instagram-style Quick Action Bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (current.onLikeClick != null) {
                                IconButton(
                                    onClick = {
                                        val nextLiked = !localLiked
                                        haptics.performLike(isLiking = nextLiked)
                                        localLiked = nextLiked
                                        current.onLikeClick.invoke()
                                    },
                                    modifier = Modifier.testTag("photo_peek_like_btn")
                                ) {
                                    Icon(
                                        imageVector = if (localLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = if (localLiked) "Unlike" else "Like",
                                        tint = if (localLiked) OrbitHeartRed else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    haptics.performTap()
                                    current.onDownloadClick()
                                    controller.dismiss()
                                },
                                modifier = Modifier.testTag("photo_peek_download_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = "Download photo",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.performShare()
                                    OrbitShareHelper.sharePhotoItem(
                                        context = context,
                                        imageUrl = current.imageUrl,
                                        authorUsername = current.authorUsername,
                                        caption = current.caption
                                    )
                                    controller.dismiss()
                                },
                                modifier = Modifier.testTag("photo_peek_share_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = "Share photo",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.performTap()
                                    controller.dismiss()
                                    current.onOpenFullScreen()
                                },
                                modifier = Modifier.testTag("photo_peek_expand_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Fullscreen,
                                    contentDescription = "Open full screen",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
