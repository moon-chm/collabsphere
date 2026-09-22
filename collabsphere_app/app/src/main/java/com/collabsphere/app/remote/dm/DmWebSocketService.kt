package com.collabsphere.app.remote.dm

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
import com.collabsphere.app.MyApplication
import com.collabsphere.app.NotificationCenter
import com.collabsphere.app.NotificationHelper
import com.collabsphere.app.model.dm.DmRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DmWebSocketService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repo: DmRepo by inject()
    private val notificationHelper: NotificationHelper by inject()
    private lateinit var notificationManager: NotificationManager

    private var connectionJob: Job? = null
    private var currentBaseUrl: String? = null
    private var currentUserIdLong: Long = -1L

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "dm_service_channel_silent"
        private const val CHANNEL_NAME = "Sync Service"
        var activeChatPartnerId: Int? = null
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure any legacy persistent foreground notification is immediately cleared
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        notificationManager.cancel(NOTIFICATION_ID)

        // MENTION/CHANNEL_MESSAGE/TASK_ASSIGNED/TASK_UPDATED pushes arrive over the same DM socket
        // but are routed here via NotificationCenter (see DmApiService.observeIncomingDms) instead
        // of the DmDto flow above, since they aren't chat messages.
        serviceScope.launch {
            NotificationCenter.incoming.collect { notification ->
                notificationHelper.showGenericNotification(notification)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val baseUrl = intent?.getStringExtra("BASE_URL")
        val userId = intent?.getLongExtra("USER_ID", -1L) ?: -1L
        val updatePartnerId = intent?.getIntExtra("UPDATE_PARTNER_ID", -2)

        if (updatePartnerId != null && updatePartnerId != -2) {
            activeChatPartnerId = if (updatePartnerId == -1) null else updatePartnerId
            Log.d("DM_SERVICE", "Updated activeChatPartnerId = $activeChatPartnerId")
        }

        if (!baseUrl.isNullOrBlank() && userId != -1L) {
            val needsRestart = connectionJob == null || 
                               !connectionJob!!.isActive || 
                               baseUrl != currentBaseUrl || 
                               userId != currentUserIdLong

            if (needsRestart) {
                startConnectionLoop(baseUrl, userId)
            }
        }

        return START_STICKY
    }

    private fun startConnectionLoop(baseUrl: String, userId: Long) {
        currentBaseUrl = baseUrl
        currentUserIdLong = userId

        connectionJob?.cancel()
        connectionJob = serviceScope.launch {
            var backoffMs = 2000L
            val maxBackoffMs = 30000L

            while (isActive) {
                try {
                    Log.d("DM_SERVICE", "Connecting WebSocket to $baseUrl for user $userId...")
                    repo.connectToChat(baseUrl, userId)
                    Log.d("DM_SERVICE", "WebSocket connected successfully! Listening for DMs...")
                    backoffMs = 2000L

                    repo.listenForIncomingDms().collect { incomingDto ->
                        repo.saveIncomingDm(incomingDto, userId.toInt())

                        val partnerId = activeChatPartnerId
                        val senderIdInt = incomingDto.senderId

                        Log.d("DM_SERVICE", "Received DM: action=${incomingDto.action}, sender=$senderIdInt, current=$userId, activePartner=$partnerId, isAppForeground=${MyApplication.isAppForeground}")

                        val isNewMessage = (incomingDto.action == "RECEIVE_MESSAGE" || incomingDto.action == "SEND_MESSAGE")

                        // Suppress notification ONLY if the app is currently in the foreground AND the user is actively viewing this partner's chat!
                        val isActivelyViewingThisChat = MyApplication.isAppForeground && (activeChatPartnerId == senderIdInt)

                        if (isNewMessage && senderIdInt != userId.toInt() && !isActivelyViewingThisChat) {
                            notificationHelper.showDmNotification(incomingDto)
                        } else if (incomingDto.action == "DELETE_MESSAGE") {
                            val targetId = if (senderIdInt != 0 && senderIdInt != userId.toInt()) senderIdInt else incomingDto.receiverId
                            if (targetId != 0 && targetId != userId.toInt()) {
                                notificationHelper.removeMessageFromHistory(targetId, incomingDto.id ?: 0)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                    Log.w("DM_SERVICE", "WebSocket disconnected: ${e.message}. Retrying in ${backoffMs}ms...")
                }

                if (isActive) {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 1.5).toLong().coerceAtMost(maxBackoffMs)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onDestroy() {
        connectionJob?.cancel()
        // Deliberately outlives serviceScope (cancelled right after) so the disconnect handshake can
        // still finish even though the service itself is being torn down right now.
        GlobalScope.launch(Dispatchers.IO) {
            try {
                repo.disconnectChat()
            } finally {
                serviceScope.cancel()
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}