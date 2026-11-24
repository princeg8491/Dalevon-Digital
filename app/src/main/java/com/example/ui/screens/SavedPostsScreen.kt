package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PostAttachment
import com.example.data.model.ThreadPost
import com.example.ui.components.FullScreenImageLightbox
import com.example.ui.components.ThreadPostRow
import com.example.ui.haptics.LocalOrbitHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPostsScreen(
    allPosts: List<ThreadPost>,
    savedPostIds: Set<String>,
    likedPostIds: Set<String>,
    repostedPostIds: Set<String>,
    currentUserId: String,
    onBack: () -> Unit,
    onPostClick: (ThreadPost) -> Unit,
    onAuthorClick: (String) -> Unit,
    onLikeClick: (ThreadPost) -> Unit,
    onReplyClick: (ThreadPost) -> Unit,
    onRepostClick: (ThreadPost) -> Unit,
    onSaveClick: (ThreadPost) -> Unit,
    onEditPost: (ThreadPost) -> Unit,
    onDeletePost: (ThreadPost) -> Unit,
    onDownloadPhoto: (String) -> Unit,
    onDownloadAttachment: (PostAttachment) -> Unit
) {
    BackHandler(onBack = onBack)
    val haptics = LocalOrbitHaptics.current
    var lightboxImageUrl by remember { mutableStateOf<String?>(null) }

    val savedPosts = remember(allPosts, savedPostIds) {
        allPosts.filter { it.id in savedPostIds }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Saved Posts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${savedPosts.size} saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.performTap()
                            onBack()
                        },
                        modifier = Modifier.testTag("saved_posts_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (savedPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No saved posts yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the bookmark icon on any post in your feed to save it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                items(savedPosts, key = { it.id }) { post ->
                    ThreadPostRow(
                        post = post,
                        isLiked = post.id in likedPostIds,
                        isReposted = post.id in repostedPostIds,
                        isSaved = true,
                        isOwnPost = post.authorId == currentUserId,
                        onPostClick = { onPostClick(post) },
                        onAuthorClick = { onAuthorClick(post.authorId) },
                        onLikeClick = { onLikeClick(post) },
                        onReplyClick = { onReplyClick(post) },
                        onRepostClick = { onRepostClick(post) },
                        onSaveClick = { onSaveClick(post) },
                        onEditClick = { onEditPost(post) },
                        onDeleteClick = { onDeletePost(post) },
                        onImageClick = { lightboxImageUrl = it },
                        onDownloadPhoto = onDownloadPhoto,
                        onDownloadAttachment = onDownloadAttachment
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    lightboxImageUrl?.let { url ->
        FullScreenImageLightbox(
            imageUrl = url,
            onDismiss = { lightboxImageUrl = null },
            onDownloadClick = { onDownloadPhoto(url) }
        )
    }
}
