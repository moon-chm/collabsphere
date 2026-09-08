package com.example.rohit_project_challlange.remote.dm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.example.rohit_project_challlange.NotificationHelper
import com.example.rohit_project_challlange.model.dm.DmRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DmNotificationReplyReceiver : BroadcastReceiver(), KoinComponent {

    private val dmRepo: DmRepo by inject()
    private val notificationHelper: NotificationHelper by inject()

    companion object {
        const val ACTION_REPLY = "com.example.rohit_project_challlange.ACTION_REPLY"
        const val ACTION_MARK_READ = "com.example.rohit_project_challlange.ACTION_MARK_READ"
        const val KEY_TEXT_REPLY = "key_text_reply"

        const val EXTRA_WORKSPACE_ID = "extra_workspace_id"
        const val EXTRA_SENDER_ID = "extra_sender_id" // Original sender (the chat partner)
        const val EXTRA_CURRENT_USER_ID = "extra_current_user_id" // Receiver (current logged in user)
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        when (intent.action) {
            ACTION_MARK_READ -> {
                if (notificationId != -1) {
                    notificationManager.cancel(notificationId)
                }
            }
            ACTION_REPLY -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()

                val workspaceId = intent.getIntExtra(EXTRA_WORKSPACE_ID, -1)
                val partnerId = intent.getIntExtra(EXTRA_SENDER_ID, -1)
                val currentUserId = intent.getIntExtra(EXTRA_CURRENT_USER_ID, -1)

                if (!replyText.isNullOrBlank() && workspaceId != -1 && partnerId != -1 && currentUserId != -1) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            dmRepo.sendRealtimeDm(
                                id = null,
                                workspaceId = workspaceId,
                                senderId = currentUserId,
                                receiverId = partnerId,
                                content = replyText
                            )
                            Log.d("DmReplyReceiver", "Direct reply successfully sent to $partnerId: $replyText")
                            notificationHelper.recordSentReply(partnerId, currentUserId, replyText)
                        } catch (e: Exception) {
                            Log.e("DmReplyReceiver", "Failed to send direct reply", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                } else {
                    if (notificationId != -1) {
                        notificationManager.cancel(notificationId)
                    }
                }
            }
        }
    }
}
