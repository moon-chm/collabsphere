package com.collabsphere.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.collabsphere.app.dto.dm.DmDto
import com.collabsphere.app.dto.notification.NotificationResponse
import com.collabsphere.app.model.UserDao
import com.collabsphere.app.remote.dm.DmNotificationReplyReceiver
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class NotificationHelper(
    private val context: Context,
    private val userDao: UserDao,
    private val quietHoursStore: com.collabsphere.app.model.QuietHoursStore
) {

    private val channelId = "collabsphere_dm_channel_v3"
    private val channelName = "CollabSphere Messages"
    private val genericChannelId = "collabsphere_activity_channel_v1"
    private val genericChannelName = "CollabSphere Activity"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // In-memory conversation history cache: PartnerId -> List of Messages
    // Int in the pair is the server message id (0 for messages with no id to key deletion on, e.g.
    // an outgoing reply recorded before its own send confirmation) — lets removeMessageFromHistory
    // actually find and drop a specific message instead of only ever seeing a never-empty list.
    private val conversationHistory = ConcurrentHashMap<Int, MutableList<Pair<Int, NotificationCompat.MessagingStyle.Message>>>()
    private val partnerNames = ConcurrentHashMap<Int, String>()

    init {
        createNotificationChannel()
        createGenericChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "CollabSphere real-time direct message notifications with heads-up peeking"
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 200)
                enableLights(true)
                lightColor = 0xFF25D366.toInt()
                // PRIVATE (not PUBLIC): a locked device still shows "New message from X" without
                // exposing the message content on the lock screen for anyone else to read.
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createGenericChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(genericChannelId, genericChannelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Mentions, channel messages, and task activity"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Posts a plain system-tray notification for MENTION/CHANNEL_MESSAGE/TASK_ASSIGNED/TASK_UPDATED
     * events — distinct from [showDmNotification]'s MessagingStyle thread since these aren't 1:1 chats.
     */
    fun showGenericNotification(notification: NotificationResponse) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (quietHoursStore.isQuietNow()) return

        val targetTab = if (notification.type == "TASK_ASSIGNED" || notification.type == "TASK_UPDATED" || notification.type == "TASK_DUE") 1 else 0
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            notification.workspaceId?.let { putExtra("workspace_id", it) }
            putExtra("target_tab", targetTab)
            putExtra("notification_id", notification.id)
            putExtra("partner_name", notification.actorUsername ?: "Workspace")
        }

        val clickPendingIntent = PendingIntent.getActivity(
            context,
            notification.id + 500_000,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val avatar = createAvatarBitmap(notification.actorUsername ?: notification.title)

        val builder = NotificationCompat.Builder(context, genericChannelId)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setLargeIcon(avatar)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(clickPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setColor(0xFFF0633D.toInt()) // CollabSphere coral

        try {
            notificationManager.notify(2_000_000 + notification.id, builder.build())
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed posting generic notification", e)
        }
    }

    suspend fun showDmNotification(dm: DmDto) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w("NotificationHelper", "Notifications disabled in system settings or missing permission")
            return
        }
        if (quietHoursStore.isQuietNow()) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Resolve sender display name — a plain suspend call instead of blocking the caller's thread.
        val senderName = (try {
            userDao.getUserById(dm.senderId)?.userName
        } catch (e: Exception) {
            null
        }) ?: "User #${dm.senderId}"

        partnerNames[dm.senderId] = senderName

        // Generate circular stylized initial avatar bitmap
        val avatarBitmap = createAvatarBitmap(senderName)
        val senderIcon = IconCompat.createWithBitmap(avatarBitmap)

        // Build Person objects
        val senderPerson = Person.Builder()
            .setName(senderName)
            .setKey(dm.senderId.toString())
            .setIcon(senderIcon)
            .setImportant(true)
            .build()

        val userPerson = Person.Builder()
            .setName("You")
            .setKey(dm.receiverId.toString())
            .build()

        // Cache conversational thread
        val historyList = conversationHistory.getOrPut(dm.senderId) { mutableListOf() }
        val newMessage = NotificationCompat.MessagingStyle.Message(
            dm.content,
            if (dm.timestamp > 0) dm.timestamp else System.currentTimeMillis(),
            senderPerson
        )
        synchronized(historyList) {
            historyList.add((dm.id ?: 0) to newMessage)
            if (historyList.size > 8) {
                historyList.removeAt(0)
            }
        }

        // Each of this sender's three possible actions (click/reply/mark-read) needs its own request
        // code — multiplying by 10 and adding a small per-action offset can never collide between
        // categories for any senderId, unlike the previous scheme's arbitrary +200000/+300000 offsets.
        val clickRequestCode = dm.senderId * 10
        val replyRequestCode = dm.senderId * 10 + 1
        val markReadRequestCode = dm.senderId * 10 + 2

        // Tap PendingIntent: Opens the chat screen directly
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("workspace_id", dm.workspaceId)
            putExtra("partner_id", dm.senderId)
            putExtra("partner_name", senderName)
        }
        val clickPendingIntent = PendingIntent.getActivity(
            context,
            clickRequestCode,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Inline Direct Reply Action (RemoteInput)
        val remoteInput = RemoteInput.Builder(DmNotificationReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("Reply to $senderName...")
            .build()

        val replyIntent = Intent(context, DmNotificationReplyReceiver::class.java).apply {
            action = DmNotificationReplyReceiver.ACTION_REPLY
            putExtra(DmNotificationReplyReceiver.EXTRA_WORKSPACE_ID, dm.workspaceId)
            putExtra(DmNotificationReplyReceiver.EXTRA_SENDER_ID, dm.senderId)
            putExtra(DmNotificationReplyReceiver.EXTRA_CURRENT_USER_ID, dm.receiverId)
            putExtra(DmNotificationReplyReceiver.EXTRA_NOTIFICATION_ID, dm.senderId)
        }

        // RemoteInput requires FLAG_MUTABLE on API 31+
        val replyPendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            replyRequestCode,
            replyIntent,
            replyPendingFlags
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        // Mark as Read Action
        val markReadIntent = Intent(context, DmNotificationReplyReceiver::class.java).apply {
            action = DmNotificationReplyReceiver.ACTION_MARK_READ
            putExtra(DmNotificationReplyReceiver.EXTRA_NOTIFICATION_ID, dm.senderId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            markReadRequestCode,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            "Mark as Read",
            markReadPendingIntent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        // Build MessagingStyle — 1:1 direct chat mode (omits conversationTitle so Android shows contact avatar on bubbles)
        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setGroupConversation(false)

        synchronized(historyList) {
            for ((_, msg) in historyList) {
                messagingStyle.addMessage(msg)
            }
        }

        val notificationId = dm.senderId
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setStyle(messagingStyle)
            .setContentTitle(senderName)
            .setContentText(dm.content)
            .setLargeIcon(avatarBitmap)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 150, 80, 200))
            .setContentIntent(clickPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setColor(0xFF25D366.toInt()) // WhatsApp-style emerald green accent
            .setSubText("Direct message")
            .addAction(replyAction)
            .addAction(markReadAction)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup("collabsphere_dms")

        try {
            notificationManager.notify(notificationId, builder.build())
            Log.d("NotificationHelper", "Notification posted for $senderName (message id=${dm.id})")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed posting notification", e)
        }
    }

    /**
     * Called by DmNotificationReplyReceiver when the user sends an inline reply from the notification shade.
     * Appends the reply to the messaging thread and refreshes the notification inline.
     */
    fun recordSentReply(partnerId: Int, currentUserId: Int, replyText: String) {
        val userPerson = Person.Builder()
            .setName("You")
            .setKey(currentUserId.toString())
            .build()

        val historyList = conversationHistory.getOrPut(partnerId) { mutableListOf() }
        synchronized(historyList) {
            historyList.add(
                0 to NotificationCompat.MessagingStyle.Message(
                    replyText,
                    System.currentTimeMillis(),
                    userPerson
                )
            )
        }

        val partnerName = partnerNames[partnerId] ?: "Chat"
        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setGroupConversation(false)

        synchronized(historyList) {
            for ((_, msg) in historyList) {
                messagingStyle.addMessage(msg)
            }
        }

        val avatarBitmap = createAvatarBitmap(partnerName)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setStyle(messagingStyle)
            .setLargeIcon(avatarBitmap)
            .setPriority(NotificationCompat.PRIORITY_LOW) // low priority update, already sent
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setColor(0xFF25D366.toInt())
            .setSubText("Direct message")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("collabsphere_dms")

        try {
            notificationManager.notify(partnerId, builder.build())
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed updating notification after direct reply", e)
        }
    }

    fun removeMessageFromHistory(partnerId: Int, messageId: Int) {
        val historyList = conversationHistory[partnerId] ?: return
        val stillHasMessages = synchronized(historyList) {
            if (messageId != 0) {
                historyList.removeAll { (id, _) -> id == messageId }
            }
            historyList.isNotEmpty()
        }

        if (!stillHasMessages) {
            notificationManager.cancel(partnerId)
            return
        }

        // Refresh the notification so the deleted message no longer shows in the thread.
        val partnerName = partnerNames[partnerId] ?: "Chat"
        val userPerson = Person.Builder().setName("You").build()
        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setGroupConversation(false)
        synchronized(historyList) {
            for ((_, msg) in historyList) {
                messagingStyle.addMessage(msg)
            }
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setStyle(messagingStyle)
            .setLargeIcon(createAvatarBitmap(partnerName))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setColor(0xFF25D366.toInt())
            .setSubText("Direct message")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("collabsphere_dms")

        try {
            notificationManager.notify(partnerId, builder.build())
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed refreshing notification after message delete", e)
        }
    }

    /**
     * Dismisses the notification for a specific chat partner and clears thread history.
     */
    fun dismissNotification(partnerId: Int) {
        conversationHistory.remove(partnerId)
        notificationManager.cancel(partnerId)
    }

    /**
     * Generates a circular avatar badge with the person's initial letter and tactile depth.
     */
    private fun createAvatarBitmap(name: String, sizePx: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // WhatsApp / modern messaging curated gradients
        val palette = intArrayOf(
            0xFF128C7E.toInt(), // WhatsApp Dark Teal
            0xFF25D366.toInt(), // WhatsApp Vibrant Green
            0xFF0088CC.toInt(), // Telegram Blue
            0xFFF0633D.toInt(), // CollabSphere Coral
            0xFF6C5CE7.toInt(), // Modern Violet
            0xFF0984E3.toInt(), // Electric Blue
            0xFF00B894.toInt()  // Emerald
        )
        val baseColor = palette[abs(name.hashCode()) % palette.size]
        paint.color = baseColor
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        // Specular glow highlight for polished tactile depth
        val glowPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            this.color = android.graphics.Color.WHITE
            alpha = 38
        }
        canvas.drawCircle(sizePx / 2f, sizePx * 0.35f, sizePx * 0.35f, glowPaint)

        // Draw initial letter
        val textPaint = Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.WHITE
            textSize = sizePx * 0.46f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val yOffset = (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(initial, sizePx / 2f, (sizePx / 2f) - yOffset, textPaint)

        return bitmap
    }
}