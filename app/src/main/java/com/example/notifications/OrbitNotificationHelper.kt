package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ActivityItem
import com.example.data.model.Conversation

object OrbitNotificationHelper {

    const val CHANNEL_ID_ALERTS = "orbit_alerts"
    const val CHANNEL_NAME_ALERTS = "Orbit Notifications"

    const val ACTION_INLINE_REPLY = "com.example.orbit.ACTION_INLINE_REPLY"
    const val KEY_TEXT_REPLY = "key_orbit_text_reply"

    const val EXTRA_NAV_TYPE = "extra_orbit_nav_type"
    const val EXTRA_POST_ID = "extra_orbit_post_id"
    const val EXTRA_SPACE_ID = "extra_orbit_space_id"
    const val EXTRA_CONVERSATION_ID = "extra_orbit_conversation_id"
    const val EXTRA_NOTIFICATION_ID = "extra_orbit_notification_id"
    const val EXTRA_SENDER_NAME = "extra_orbit_sender_name"

    const val NAV_TYPE_ACTIVITY = "activity"
    const val NAV_TYPE_POST = "post"
    const val NAV_TYPE_SPACE = "space"
    const val NAV_TYPE_DM = "dm"
    const val NAV_TYPE_SETTINGS_UPDATE = "settings_update"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID_ALERTS)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    CHANNEL_NAME_ALERTS,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Replies, mentions, Spaces, Direct Messages, and Orbit updates"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showActivityNotification(context: Context, item: ActivityItem) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannel(context)

        val notificationId = (item.id.hashCode() and 0x7FFFFFFF).coerceAtLeast(1001)
        val (title, body, navType) = when (item.type) {
            "like" -> Triple("@${item.actorUsername} liked your thread", item.snippet.ifBlank { "Tap to view thread" }, NAV_TYPE_POST)
            "reply" -> Triple("@${item.actorUsername} replied", item.snippet.ifBlank { "New reply on your thread" }, NAV_TYPE_POST)
            "repost" -> Triple("@${item.actorUsername} reposted your thread", item.snippet.ifBlank { "Tap to view" }, NAV_TYPE_POST)
            "quote" -> Triple("@${item.actorUsername} quoted your thread", item.snippet.ifBlank { "Tap to view quote" }, NAV_TYPE_POST)
            "mention" -> Triple(
                "@${item.actorUsername} mentioned you",
                item.snippet.ifBlank { "Tap to view mention" },
                if (item.postId.isNotBlank()) NAV_TYPE_POST else if (item.spaceId.isNotBlank()) NAV_TYPE_SPACE else NAV_TYPE_ACTIVITY
            )
            "follow" -> Triple("@${item.actorUsername} followed you", "Tap to view your activity", NAV_TYPE_ACTIVITY)
            "space_invite" -> Triple("Space invite from @${item.actorUsername}", item.snippet.ifBlank { "Tap to view Space" }, NAV_TYPE_SPACE)
            "space_join_request" -> Triple("Join request from @${item.actorUsername}", item.snippet.ifBlank { "Tap to review request" }, NAV_TYPE_SPACE)
            "space_join_approved" -> Triple("Space request approved!", item.snippet.ifBlank { "Tap to open Space" }, NAV_TYPE_SPACE)
            "space_member_joined" -> Triple("@${item.actorUsername} joined your Space", item.snippet.ifBlank { "Tap to view Space" }, NAV_TYPE_SPACE)
            "app_update" -> Triple("Orbit update available", item.snippet.ifBlank { "Tap to open Updates in Settings" }, NAV_TYPE_SETTINGS_UPDATE)
            else -> Triple("Orbit Notification", item.snippet.ifBlank { "New activity" }, NAV_TYPE_ACTIVITY)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TYPE, navType)
            putExtra(EXTRA_POST_ID, item.postId)
            putExtra(EXTRA_SPACE_ID, item.spaceId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        // Allow direct inline reply from notification shade when replying to a thread or mention
        if ((item.type == "reply" || item.type == "quote" || item.type == "mention") && item.postId.isNotBlank()) {
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Reply to @${item.actorUsername}...")
                .build()
            val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
                action = ACTION_INLINE_REPLY
                putExtra(EXTRA_NAV_TYPE, NAV_TYPE_POST)
                putExtra(EXTRA_POST_ID, item.postId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_SENDER_NAME, "@${item.actorUsername}")
            }
            val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 50_000,
                replyIntent,
                replyFlags
            )
            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Reply",
                replyPendingIntent
            ).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build()
            builder.addAction(replyAction)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    fun showDirectMessageNotification(
        context: Context,
        conversation: Conversation,
        myUid: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannel(context)

        val chatTitle = conversation.displayTitle(myUid)
        val rawBody = conversation.lastMessageText.ifBlank { "Sent a message" }
        val body = if (conversation.isGroupChat && conversation.lastMessageSenderId.isNotBlank()) {
            "${conversation.lastMessageSenderLabel(myUid)}: $rawBody"
        } else {
            rawBody
        }
        val peerName = chatTitle
        val notificationId = (conversation.id.hashCode() and 0x7FFFFFFF).coerceAtLeast(2001)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TYPE, NAV_TYPE_DM)
            putExtra(EXTRA_CONVERSATION_ID, conversation.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply to $peerName...")
            .build()
        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_INLINE_REPLY
            putExtra(EXTRA_NAV_TYPE, NAV_TYPE_DM)
            putExtra(EXTRA_CONVERSATION_ID, conversation.id)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_SENDER_NAME, peerName)
        }
        val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 70_000,
            replyIntent,
            replyFlags
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(peerName)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(replyAction)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
