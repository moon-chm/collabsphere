package com.example.rohit_project_challlange

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.rohit_project_challlange.dto.dm.DmDto

class NotificationHelper(private val context: Context) {

    private val channelId = "dm_messages_channel"
    private val channelName = "Direct Messages"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for incoming direct messages"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDmNotification(dm: DmDto) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New Message")
            .setContentText(dm.content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationId = dm.senderId.hashCode()
        notificationManager.notify(notificationId, builder.build())
    }
}