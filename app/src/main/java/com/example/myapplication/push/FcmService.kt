package com.example.myapplication.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.chat.ui.conversations.ConversationsActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android counterpart to the iOS AppDelegate's UNUserNotificationCenter handling: caches/
 * uploads the push token, and on a new-message push, shows a notification that deep-links
 * into the conversation on tap (mirroring iOS's `conversation_id` / openConversationFromPush
 * handling). Inert until google-services.json is added — see app/build.gradle.kts.
 */
class FcmService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val EXTRA_OPEN_CONVERSATION_ID = "extra_open_conversation_id"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channelId = context.getString(R.string.notif_channel_chat_id)
            if (manager.getNotificationChannel(channelId) != null) return
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notif_channel_chat_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_chat_description)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenManager.cacheToken(applicationContext, token)
        serviceScope.launch { PushTokenManager.uploadIfNeeded(applicationContext) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val body = message.notification?.body ?: message.data["body"] ?: return
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val conversationId = message.data["conversation_id"]

        ensureChannel(applicationContext)

        val openIntent = Intent(applicationContext, ConversationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (conversationId != null) putExtra(EXTRA_OPEN_CONVERSATION_ID, conversationId)
        }
        val requestCode = conversationId?.hashCode() ?: System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = getString(R.string.notif_channel_chat_id)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(applicationContext, R.color.find_primary))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            NotificationManagerCompat.from(applicationContext).notify(requestCode, notification)
        }
    }
}