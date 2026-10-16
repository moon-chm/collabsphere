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
import kotlinx.coroutines.launch

class NotificationHelper(
    private val context: Context,
    private val userDao: UserDao,
    private val quietHoursStore: com.collabsphere.app.model.QuietHoursStore,
    private val focusRepo: com.collabsphere.app.model.focus.FocusRepo
) {

    private val channelId = NotificationChannels.DIRECT
    private val genericChannelId = NotificationChannels.CHANNELS
    private val avatars = NotificationAvatars(context)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // In-memory conversation history cache: PartnerId -> List of Messages
    // Int in the pair is the server message id (0 for messages with no id to key deletion on, e.g.
    // an outgoing reply recorded before its own send confirmation) — lets removeMessageFromHistory
    // actually find and drop a specific message instead of only ever seeing a never-empty list.
    private val conversationHistory = ConcurrentHashMap<Int, MutableList<Pair<Int, NotificationCompat.MessagingStyle.Message>>>()
    private val partnerNames = ConcurrentHashMap<Int, String>()
    private val channelHistory = ConcurrentHashMap<Int, MutableList<NotificationCompat.MessagingStyle.Message>>()
    private val channelMeta = ConcurrentHashMap<Int, NotificationResponse>()
    private val channelThreadChannel = ConcurrentHashMap<Int, String>()
    private val partnerAvatars = ConcurrentHashMap<Int, Bitmap>()

    @Volatile
    var activeChannelId: Int? = null

    init {
        NotificationChannels.ensureCreated(notificationManager)
    }

    private val recentGenericNotifications = ConcurrentHashMap<String, Long>()
    /** Server notification ids already posted, oldest first. Guarded by itself. */
    private val shownNotificationIds = LinkedHashSet<Int>()

    /**
     * Posts a plain system-tray notification for MENTION/CHANNEL_MESSAGE/TASK_ASSIGNED/TASK_UPDATED/GITHUB_CI
     * events — distinct from [showDmNotification]'s MessagingStyle thread since these aren't 1:1 chats.
     */
    fun showGenericNotification(notification: NotificationResponse) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (quietHoursStore.isQuietNow()) return
        if (focusRepo.isFocusingNow()) return

        // The server delivers each notification over both the WebSocket and FCM, with no bound on how far
        // apart; post a given server notification id once.
        synchronized(shownNotificationIds) {
            if (!shownNotificationIds.add(notification.id)) {
                Log.d("NotificationHelper", "Suppressed already-shown notification id=${notification.id}")
                return
            }
            if (shownNotificationIds.size > 200) shownNotificationIds.remove(shownNotificationIds.first())
        }

        // Deduplicate identical push messages received in rapid succession (e.g. multi-workspace GitHub CI hooks)
        val dedupeKey = "${notification.title}:${notification.body}"
        val now = System.currentTimeMillis()
        val lastSeen = recentGenericNotifications[dedupeKey] ?: 0L
        if (now - lastSeen < 4_000L) {
            Log.d("NotificationHelper", "Suppressed duplicate generic notification: ${notification.title}")
            return
        }
        recentGenericNotifications[dedupeKey] = now
        if (recentGenericNotifications.size > 20) {
            recentGenericNotifications.clear()
        }

        MyApplication.appScope.launch { postGeneric(notification) }
    }

    private suspend fun postGeneric(notification: NotificationResponse) {
        val channelIdForThread = notification.channelId
        if (channelIdForThread != null && (notification.type == "CHANNEL_MESSAGE" || notification.type == "MENTION")) {
            if (MyApplication.isAppForeground && activeChannelId == channelIdForThread) return
            showChannelNotification(notification, channelIdForThread)
            return
        }

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

        val actorName = notification.actorUsername ?: notification.title
        val avatar = avatars.load(notification.actorAvatarUrl, actorName)
        val androidChannel = NotificationChannels.forType(notification.type)
        val color = NotificationChannels.colorFor(notification.type)
        val groupKey = "collabsphere_activity_group"
        val notificationId = 2_000_000 + notification.id

        val builder = NotificationCompat.Builder(context, androidChannel)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setLargeIcon(avatar)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(clickPendingIntent)
            .setPriority(if (androidChannel == NotificationChannels.GITHUB) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setCategory(if (notification.type == "TASK_DUE") NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_SOCIAL)
            .setColor(color)
            .setWhen(notification.createdAt)
            .setShowWhen(true)
        taskDoneAction(notification, notificationId)?.let { builder.addAction(it) }

        val summaryNotification = NotificationCompat.Builder(context, androidChannel)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setContentTitle("CollabSphere")
            .setContentText("New workspace updates")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Workspace updates"))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(NotificationChannels.BRAND_COLOR)
            .build()

        try {
            notificationManager.notify(notificationId, builder.build())
            notificationManager.notify(2_999_999, summaryNotification)
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed posting generic notification", e)
        }
    }

    private fun taskDoneAction(notification: NotificationResponse, notificationId: Int): NotificationCompat.Action? {
        if (notification.type != "TASK_ASSIGNED" && notification.type != "TASK_UPDATED" && notification.type != "TASK_DUE") return null
        val taskId = notification.referenceId?.takeIf { it > 0 } ?: return null
        val intent = Intent(context, com.collabsphere.app.remote.task.TaskNotificationReceiver::class.java).apply {
            action = com.collabsphere.app.remote.task.TaskNotificationReceiver.ACTION_MARK_DONE
            putExtra(com.collabsphere.app.remote.task.TaskNotificationReceiver.EXTRA_TASK_ID, taskId)
            putExtra(com.collabsphere.app.remote.task.TaskNotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_collabsphere, "Mark done", pending)
            .setShowsUserInterface(false)
            .build()
    }

    private fun pushConversationShortcut(partnerId: Int, name: String, icon: IconCompat, person: Person, clickIntent: Intent): String? =
        runCatching {
            val id = "dm_$partnerId"
            val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(name)
                .setLongLived(true)
                .setPerson(person)
                .setIcon(icon)
                .setIntent(Intent(clickIntent).setAction(Intent.ACTION_VIEW))
                .setCategories(setOf("android.shortcut.conversation"))
                .build()
            androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            id
        }.onFailure { Log.w("NotificationHelper", "Couldn't publish conversation shortcut", it) }.getOrNull()

    suspend fun showDmNotification(incoming: DmDto) {
        val previewed = incoming.copy(content = dmPreviewText(incoming.content, incoming.mediaUrl))
        val dm = if (previewed.urgent) previewed.copy(content = "\u26A1 Urgent \u00B7 ${previewed.content}") else previewed
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w("NotificationHelper", "Notifications disabled in system settings or missing permission")
            return
        }
        if (quietHoursStore.isQuietNow()) return
        if (focusRepo.isFocusingNow() && !dm.urgent) return

        // The same DM can arrive over the WebSocket and as an FCM push (e.g. while the socket reconnects).
        val messageId = dm.id ?: 0
        if (messageId > 0) {
            val alreadyShown = conversationHistory[dm.senderId]?.let { history ->
                synchronized(history) { history.any { it.first == messageId } }
            } == true
            if (alreadyShown) {
                Log.d("NotificationHelper", "Suppressed already-shown DM id=$messageId")
                return
            }
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Resolve sender display name — a plain suspend call instead of blocking the caller's thread.
        val sender = try {
            userDao.getUserById(dm.senderId)
        } catch (e: Exception) {
            null
        }
        val senderName = sender?.userName ?: "User #${dm.senderId}"

        partnerNames[dm.senderId] = senderName

        // Generate circular stylized initial avatar bitmap
        val avatarBitmap = avatars.load(sender?.avatarUrl, senderName)
        partnerAvatars[dm.senderId] = avatarBitmap
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
            putExtra(DmNotificationReplyReceiver.EXTRA_WORKSPACE_ID, dm.workspaceId)
            putExtra(DmNotificationReplyReceiver.EXTRA_SENDER_ID, dm.senderId)
            putExtra(DmNotificationReplyReceiver.EXTRA_CURRENT_USER_ID, dm.receiverId)
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
        val shortcutId = pushConversationShortcut(dm.senderId, senderName, senderIcon, senderPerson, clickIntent)
        val androidChannel = if (dm.urgent) NotificationChannels.URGENT else NotificationChannels.DIRECT
        val builder = NotificationCompat.Builder(context, androidChannel)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setStyle(messagingStyle)
            .setContentTitle(senderName)
            .setContentText(dm.content)
            .setLargeIcon(avatarBitmap)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(soundUri)
            .setVibrate(if (dm.urgent) NotificationChannels.URGENT_VIBRATION else NotificationChannels.DM_VIBRATION)
            .setContentIntent(clickPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setColor(if (dm.urgent) NotificationChannels.URGENT_COLOR else NotificationChannels.BRAND_COLOR)
            .setSubText(if (dm.urgent) "Urgent" else "Direct message")
            .addAction(replyAction)
            .addAction(markReadAction)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setShortcutId(shortcutId)
            .setGroup("collabsphere_dms")

        try {
            notificationManager.notify(notificationId, builder.build())
            postDmSummary()
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

        val avatarBitmap = partnerAvatars[partnerId] ?: avatars.initials(partnerName)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setStyle(messagingStyle)
            .setLargeIcon(avatarBitmap)
            .setPriority(NotificationCompat.PRIORITY_LOW) // low priority update, already sent
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setColor(NotificationChannels.BRAND_COLOR)
            .setSubText("Direct message")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("collabsphere_dms")
            .setShortcutId("dm_$partnerId")

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
            .setLargeIcon(partnerAvatars[partnerId] ?: avatars.initials(partnerName))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setColor(NotificationChannels.BRAND_COLOR)
            .setSubText("Direct message")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("collabsphere_dms")
            .setShortcutId("dm_$partnerId")

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
        if (conversationHistory.isEmpty()) notificationManager.cancel(DM_SUMMARY_ID)
    }

    private fun postDmSummary() {
        val count = conversationHistory.size
        if (count == 0) return
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setContentTitle("CollabSphere")
            .setContentText(if (count == 1) "New direct message" else "Messages from $count people")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Direct messages"))
            .setGroup("collabsphere_dms")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setColor(NotificationChannels.BRAND_COLOR)
            .build()
        runCatching { notificationManager.notify(DM_SUMMARY_ID, summary) }
    }

    private suspend fun showChannelNotification(notification: NotificationResponse, channelId: Int) {
        val workspaceId = notification.workspaceId ?: return
        val actorName = notification.actorUsername ?: "Someone"
        val channelLabel = notification.channelName?.let { "#$it" } ?: "Channel"
        channelMeta[channelId] = notification

        val actor = Person.Builder()
            .setName(actorName)
            .setKey("u${notification.actorId ?: actorName}")
            .setIcon(IconCompat.createWithBitmap(avatars.load(notification.actorAvatarUrl, actorName)))
            .build()
        val me = Person.Builder().setName("You").setKey(notification.recipientId.toString()).build()

        val history = channelHistory.getOrPut(channelId) { mutableListOf() }
        val text = if (notification.type == "MENTION") "@ ${notification.body}" else notification.body
        synchronized(history) {
            history.add(NotificationCompat.MessagingStyle.Message(text, notification.createdAt, actor))
            if (history.size > 8) history.removeAt(0)
        }
        postChannelThread(channelId, me, channelLabel, notification, alert = true)
    }

    fun recordChannelReply(channelId: Int, replyText: String) {
        val meta = channelMeta[channelId] ?: return
        val me = Person.Builder().setName("You").setKey(meta.recipientId.toString()).build()
        val history = channelHistory.getOrPut(channelId) { mutableListOf() }
        synchronized(history) {
            history.add(NotificationCompat.MessagingStyle.Message(replyText, System.currentTimeMillis(), me))
        }
        postChannelThread(channelId, me, meta.channelName?.let { "#$it" } ?: "Channel", meta, alert = false)
    }

    fun dismissChannel(channelId: Int) {
        channelHistory.remove(channelId)
        channelMeta.remove(channelId)
        channelThreadChannel.remove(channelId)
        notificationManager.cancel(CHANNEL_NOTIFICATION_BASE + channelId)
        if (channelHistory.isEmpty()) notificationManager.cancel(CHANNEL_SUMMARY_ID)
    }

    private fun postChannelThread(
        channelId: Int,
        me: Person,
        channelLabel: String,
        meta: NotificationResponse,
        alert: Boolean
    ) {
        val workspaceId = meta.workspaceId ?: return
        val requestBase = CHANNEL_NOTIFICATION_BASE + channelId * 4
        val androidChannel = if (alert) {
            (if (meta.type == "MENTION") NotificationChannels.MENTIONS else NotificationChannels.CHANNELS)
                .also { channelThreadChannel[channelId] = it }
        } else {
            channelThreadChannel[channelId] ?: NotificationChannels.CHANNELS
        }
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(channelLabel)
            .setGroupConversation(true)
        channelHistory[channelId]?.let { history -> synchronized(history) { history.forEach(style::addMessage) } }

        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("workspace_id", workspaceId)
            putExtra("target_tab", 0)
            putExtra("notification_id", meta.id)
            putExtra("partner_name", channelLabel)
        }
        val clickPending = PendingIntent.getActivity(
            context, requestBase, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun receiverIntent(action: String) = Intent(context, com.collabsphere.app.remote.message.ChannelNotificationReceiver::class.java).apply {
            this.action = action
            putExtra(com.collabsphere.app.remote.message.ChannelNotificationReceiver.EXTRA_WORKSPACE_ID, workspaceId)
            putExtra(com.collabsphere.app.remote.message.ChannelNotificationReceiver.EXTRA_CHANNEL_ID, channelId)
            putExtra(com.collabsphere.app.remote.message.ChannelNotificationReceiver.EXTRA_CURRENT_USER_ID, meta.recipientId)
            putExtra(com.collabsphere.app.remote.message.ChannelNotificationReceiver.EXTRA_LAST_MESSAGE_ID, meta.referenceId ?: -1)
        }
        val mutableFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            PendingIntent.getBroadcast(
                context, requestBase + 1,
                receiverIntent(com.collabsphere.app.remote.message.ChannelNotificationReceiver.ACTION_REPLY),
                mutableFlags
            )
        )
            .addRemoteInput(
                RemoteInput.Builder(com.collabsphere.app.remote.message.ChannelNotificationReceiver.KEY_TEXT_REPLY)
                    .setLabel("Reply in $channelLabel…")
                    .build()
            )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            "Mark as Read",
            PendingIntent.getBroadcast(
                context, requestBase + 2,
                receiverIntent(com.collabsphere.app.remote.message.ChannelNotificationReceiver.ACTION_MARK_READ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val builder = NotificationCompat.Builder(context, androidChannel)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setStyle(style)
            .setContentTitle(channelLabel)
            .setContentText(meta.body)
            .setContentIntent(clickPending)
            .setPriority(if (alert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(!alert)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setColor(NotificationChannels.BRAND_COLOR)
            .setGroup(CHANNEL_GROUP)
            .addAction(replyAction)
            .addAction(markReadAction)

        val summary = NotificationCompat.Builder(context, androidChannel)
            .setSmallIcon(R.drawable.ic_stat_collabsphere)
            .setContentTitle("CollabSphere")
            .setContentText(if (channelHistory.size == 1) "New channel activity" else "Activity in ${channelHistory.size} channels")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Channels"))
            .setGroup(CHANNEL_GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setColor(NotificationChannels.BRAND_COLOR)
            .build()

        try {
            notificationManager.notify(CHANNEL_NOTIFICATION_BASE + channelId, builder.build())
            notificationManager.notify(CHANNEL_SUMMARY_ID, summary)
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed posting channel notification", e)
        }
    }

    private companion object {
        const val DM_SUMMARY_ID = 1_999_999
        const val CHANNEL_SUMMARY_ID = 3_999_999
        const val CHANNEL_NOTIFICATION_BASE = 3_000_000
        const val CHANNEL_GROUP = "collabsphere_channels"
    }
}
