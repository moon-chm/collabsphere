package com.example.rohit_project_challlange.remote.dm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rohit_project_challlange.NotificationHelper
import com.example.rohit_project_challlange.model.dm.DmRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class DmWebSocketService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repo: DmRepo by inject()
    private val notificationHelper: NotificationHelper by inject()
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "dm_service_channel"
        private const val CHANNEL_NAME = "Chat Synchronization"

        var activeChatPartnerId: Int? = null
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildServiceNotification("Connecting..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            startForeground(NOTIFICATION_ID, buildServiceNotification("Connecting..."))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val baseUrl = intent?.getStringExtra("BASE_URL")
        val userId = intent?.getLongExtra("USER_ID", -1L) ?: -1L
        val updatePartnerId = intent?.getIntExtra("UPDATE_PARTNER_ID", -2)

        if (updatePartnerId != null && updatePartnerId != -2) {
            activeChatPartnerId = if (updatePartnerId == -1) null else updatePartnerId
        }

        if (!baseUrl.isNullOrBlank() && userId != -1L) {
            serviceScope.launch {
                try {
                    repo.connectToChat(baseUrl, userId)
                    observeIncomingTraffic(userId.toInt())
                } catch (e: Exception) {
                    Log.e("DM_SERVICE", "WebSocket initialization error", e)
                }
            }
        }

        return START_STICKY
    }

    private suspend fun observeIncomingTraffic(currentUserId: Int) {
        repo.listenForIncomingDms()
            .catch { e ->
                e.printStackTrace()
            }
            .collect { incomingDto ->
                repo.saveIncomingDm(incomingDto, currentUserId)

                val partnerId = activeChatPartnerId
                val senderIdInt = incomingDto.senderId

                if (incomingDto.action != "HISTORY" && senderIdInt != currentUserId && senderIdInt != partnerId) {
                    notificationHelper.showDmNotification(incomingDto)
                }
            }
    }


    private fun buildServiceNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Chat Status")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        val cleanupScope = CoroutineScope(Dispatchers.IO)
        cleanupScope.launch {
            try {
                withContext(NonCancellable) {
                    repo.disconnectChat()
                }
            } finally {
                serviceScope.cancel()
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}