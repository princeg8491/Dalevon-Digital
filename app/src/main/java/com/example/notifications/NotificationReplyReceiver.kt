package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.R
import com.example.data.cache.OrbitCacheManager
import com.example.data.model.UserProfile
import com.example.data.repository.SocialSpacesRepository
import com.example.data.repository.ThreadsRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles direct inline replies sent from the Android status bar notification shade
 * for both Direct Messages and Thread replies.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OrbitNotificationHelper.ACTION_INLINE_REPLY) return
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(OrbitNotificationHelper.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (replyText.isBlank()) return

        val navType = intent.getStringExtra(OrbitNotificationHelper.EXTRA_NAV_TYPE).orEmpty()
        val postId = intent.getStringExtra(OrbitNotificationHelper.EXTRA_POST_ID).orEmpty()
        val conversationId = intent.getStringExtra(OrbitNotificationHelper.EXTRA_CONVERSATION_ID).orEmpty()
        val notificationId = intent.getIntExtra(OrbitNotificationHelper.EXTRA_NOTIFICATION_ID, 0)
        val senderName = intent.getStringExtra(OrbitNotificationHelper.EXTRA_SENDER_NAME).orEmpty()

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val user = Firebase.auth.currentUser ?: return@launch
                val uid = user.uid
                val cachedProfile = OrbitCacheManager.profileCache.get(uid)
                    ?: UserProfile(
                        id = uid,
                        userId = uid,
                        displayName = user.displayName ?: "Orbit Member",
                        username = user.email?.substringBefore("@") ?: "member",
                        avatarUrl = user.photoUrl?.toString().orEmpty()
                    )

                val sentOk = when (navType) {
                    OrbitNotificationHelper.NAV_TYPE_DM -> {
                        if (conversationId.isNotBlank()) {
                            val spacesRepo = SocialSpacesRepository(appContext)
                            spacesRepo.sendDirectMessage(
                                conversationId = conversationId,
                                me = cachedProfile,
                                peer = null,
                                body = replyText
                            ).isSuccess
                        } else {
                            false
                        }
                    }
                    OrbitNotificationHelper.NAV_TYPE_POST -> {
                        if (postId.isNotBlank()) {
                            val threadsRepo = ThreadsRepository(appContext)
                            val parentPost = threadsRepo.getPostById(postId).getOrNull()
                            if (parentPost != null) {
                                threadsRepo.createPost(
                                    author = cachedProfile,
                                    body = replyText,
                                    parentPost = parentPost
                                ).isSuccess
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                    else -> false
                }

                if (notificationId > 0 && OrbitNotificationHelper.hasNotificationPermission(appContext)) {
                    val statusText = if (sentOk) {
                        if (senderName.isNotBlank()) "Replied to $senderName" else "Reply sent"
                    } else {
                        "Could not send reply. Open Orbit to retry."
                    }
                    val updated = NotificationCompat.Builder(appContext, OrbitNotificationHelper.CHANNEL_ID_ALERTS)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Orbit")
                        .setContentText(statusText)
                        .setAutoCancel(true)
                        .setTimeoutAfter(4000L)
                        .build()
                    runCatching {
                        NotificationManagerCompat.from(appContext).notify(notificationId, updated)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
