package com.collabsphere.app.remote.message

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.collabsphere.app.MyApplication
import com.collabsphere.app.NotificationHelper
import com.collabsphere.app.model.message.MessageEntity
import com.collabsphere.app.model.message.MessageRepo
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ChannelNotificationReceiver : BroadcastReceiver(), KoinComponent {

    private val messageRepo: MessageRepo by inject()
    private val notificationHelper: NotificationHelper by inject()

    companion object {
        const val ACTION_REPLY = "com.collabsphere.app.ACTION_CHANNEL_REPLY"
        const val ACTION_MARK_READ = "com.collabsphere.app.ACTION_CHANNEL_MARK_READ"
        const val KEY_TEXT_REPLY = "key_channel_text_reply"
        const val EXTRA_WORKSPACE_ID = "extra_channel_workspace_id"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_CURRENT_USER_ID = "extra_channel_current_user_id"
        const val EXTRA_LAST_MESSAGE_ID = "extra_channel_last_message_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val workspaceId = intent.getIntExtra(EXTRA_WORKSPACE_ID, -1)
        val channelId = intent.getIntExtra(EXTRA_CHANNEL_ID, -1)
        val currentUserId = intent.getIntExtra(EXTRA_CURRENT_USER_ID, -1)
        val lastMessageId = intent.getIntExtra(EXTRA_LAST_MESSAGE_ID, -1)
        if (workspaceId == -1 || channelId == -1) return

        when (intent.action) {
            ACTION_MARK_READ -> {
                notificationHelper.dismissChannel(channelId)
                if (lastMessageId > 0) {
                    val pending = goAsync()
                    MyApplication.appScope.launch {
                        try {
                            messageRepo.markRead(workspaceId, channelId, lastMessageId)
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
            ACTION_REPLY -> {
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()
                if (replyText.isNullOrBlank() || currentUserId == -1) {
                    notificationHelper.dismissChannel(channelId)
                    return
                }
                val pending = goAsync()
                MyApplication.appScope.launch {
                    try {
                        messageRepo.sendMessageToUser(
                            MessageEntity(
                                userId = currentUserId,
                                workspaceId = workspaceId,
                                channelId = channelId,
                                userName = "",
                                content = replyText
                            )
                        )
                        if (lastMessageId > 0) messageRepo.markRead(workspaceId, channelId, lastMessageId)
                        notificationHelper.recordChannelReply(channelId, replyText)
                    } catch (e: Exception) {
                        Log.e("ChannelReplyReceiver", "Failed to send channel reply", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
