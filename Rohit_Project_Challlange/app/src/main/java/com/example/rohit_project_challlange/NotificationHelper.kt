package com.example.rohit_project_challlange

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
import com.example.rohit_project_challlange.dto.dm.DmDto
import com.example.rohit_project_challlange.model.UserDao
import com.example.rohit_project_challlange.remote.dm.DmNotificationReplyReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class NotificationHelper(
    private val context: Context,
    private val userDao: UserDao
) {

    private val channelId = "collabsphere_dm_channel_v3"
    private val channelName = "CollabSphere Messages"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // In-memory conversation history cache: PartnerId -> List of Messages
    private val conversationHistory = ConcurrentHashMap<Int, MutableList<NotificationCompat.MessagingStyle.Message>>()
    private val partnerNames = ConcurrentHashMap<Int, String>()

    init {
        createNotificationChannel()
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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDmNotification(dm: DmDto) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w("NotificationHelper", "Notifications disabled in system settings or missing permission")
            return
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Resolve sender display name
        val senderName = runBlocking(Dispatchers.IO) {
            try {
                userDao.getUserById(dm.senderId)?.userName
            } catch (e: Exception) {
                null
            }
        } ?: "User #${dm.senderId}"

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
            historyList.add(newMessage)
            if (historyList.size > 8) {
                historyList.removeAt(0)
            }
        }

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
            dm.senderId,
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
            dm.senderId + 200000,
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
            dm.senderId + 300000,
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
            for (msg in historyList) {
                messagingStyle.addMessage(msg)
            }
        }

        val notificationId = dm.senderId
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup("collabsphere_dms")

        try {
            notificationManager.notify(notificationId, builder.build())
            Log.d("NotificationHelper", "WhatsApp-style notification posted for $senderName: ${dm.content}")
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
                NotificationCompat.MessagingStyle.Message(
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
            for (msg in historyList) {
                messagingStyle.addMessage(msg)
            }
        }

        val avatarBitmap = createAvatarBitmap(partnerName)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
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
        val historyList = conversationHistory[partnerId]
        if (historyList != null) {
            synchronized(historyList) {
                if (historyList.isEmpty()) {
                    notificationManager.cancel(partnerId)
                }
            }
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