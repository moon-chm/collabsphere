package com.collabsphere.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.LruCache
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

object NotificationChannels {
    const val DIRECT = "collabsphere_dm_channel_v3"
    const val URGENT = "collabsphere_urgent_v1"
    const val MENTIONS = "collabsphere_mentions_v1"
    const val CHANNELS = "collabsphere_activity_channel_v1"
    const val TASKS = "collabsphere_tasks_v1"
    const val GITHUB = "collabsphere_github_v1"

    const val BRAND_COLOR = 0xFFF0633D.toInt()
    const val URGENT_COLOR = 0xFFD64545.toInt()
    const val TASK_COLOR = 0xFF4E9E78.toInt()
    const val GITHUB_COLOR = 0xFF4C55C4.toInt()

    private const val GROUP_MESSAGES = "collabsphere_group_messages"
    private const val GROUP_WORK = "collabsphere_group_work"

    val DM_VIBRATION = longArrayOf(0, 150, 80, 200)
    val URGENT_VIBRATION = longArrayOf(0, 300, 120, 300, 120, 500)

    fun forType(type: String?): String = when (type) {
        "DM" -> DIRECT
        "MENTION" -> MENTIONS
        "CHANNEL_MESSAGE" -> CHANNELS
        "TASK_ASSIGNED", "TASK_UPDATED", "TASK_DUE" -> TASKS
        "GITHUB_CI" -> GITHUB
        else -> CHANNELS
    }

    fun colorFor(type: String?): Int = when (forType(type)) {
        TASKS -> TASK_COLOR
        GITHUB -> GITHUB_COLOR
        else -> BRAND_COLOR
    }

    fun ensureCreated(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(GROUP_MESSAGES, "Messages"),
                NotificationChannelGroup(GROUP_WORK, "Work")
            )
        )
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
            .build()

        fun channel(id: String, name: String, description: String, importance: Int, group: String) =
            NotificationChannel(id, name, importance).apply {
                this.description = description
                this.group = group
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

        manager.createNotificationChannels(
            listOf(
                channel(DIRECT, "Direct messages", "Messages sent to you personally", NotificationManager.IMPORTANCE_HIGH, GROUP_MESSAGES).apply {
                    setSound(sound, audio)
                    enableVibration(true)
                    vibrationPattern = DM_VIBRATION
                    enableLights(true)
                    lightColor = BRAND_COLOR
                },
                channel(URGENT, "Urgent messages", "Teammates breaking through your focus for something important", NotificationManager.IMPORTANCE_HIGH, GROUP_MESSAGES).apply {
                    setSound(sound, audio)
                    enableVibration(true)
                    vibrationPattern = URGENT_VIBRATION
                    enableLights(true)
                    lightColor = URGENT_COLOR
                },
                channel(MENTIONS, "Mentions", "When someone @mentions you in a channel", NotificationManager.IMPORTANCE_HIGH, GROUP_MESSAGES).apply {
                    setSound(sound, audio)
                    enableVibration(true)
                },
                channel(CHANNELS, "Channel messages", "New messages in channels you follow", NotificationManager.IMPORTANCE_DEFAULT, GROUP_MESSAGES),
                channel(TASKS, "Tasks", "Assignments, updates and due-date reminders", NotificationManager.IMPORTANCE_DEFAULT, GROUP_WORK),
                channel(GITHUB, "GitHub & CI", "Build results and repository activity", NotificationManager.IMPORTANCE_LOW, GROUP_WORK)
            )
        )
    }
}

class NotificationAvatars(private val context: Context) {

    private val loader by lazy {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .allowHardware(false)
            .build()
    }
    private val cache = LruCache<String, Bitmap>(40)

    suspend fun load(url: String?, name: String, sizePx: Int = 128): Bitmap {
        val key = url?.takeIf { it.isNotBlank() } ?: return initials(name, sizePx)
        cache.get(key)?.let { return it }
        val loaded = withTimeoutOrNull(3_000L) {
            runCatching {
                val result = loader.execute(
                    ImageRequest.Builder(context)
                        .data(key)
                        .size(sizePx)
                        .transformations(CircleCropTransformation())
                        .build()
                )
                ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
        }
        return if (loaded != null) {
            cache.put(key, loaded)
            loaded
        } else {
            initials(name, sizePx)
        }
    }

    fun initials(name: String, sizePx: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = intArrayOf(
            0xFFF0633D.toInt(),
            0xFF4C55C4.toInt(),
            0xFF4E9E78.toInt(),
            0xFFD6501F.toInt(),
            0xFF6C5CE7.toInt(),
            0xFF2E86AB.toInt(),
            0xFFB5651D.toInt()
        )
        val fill = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = palette[abs(name.hashCode()) % palette.size]
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, fill)
        val text = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = sizePx * 0.44f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val yOffset = (text.descent() + text.ascent()) / 2
        canvas.drawText(initial, sizePx / 2f, sizePx / 2f - yOffset, text)
        return bitmap
    }
}

fun dmPreviewText(content: String, mediaUrl: String?): String = when {
    content.isNotBlank() -> content
    mediaUrl.isNullOrBlank() -> "New message"
    com.collabsphere.app.view.components.isVoiceMediaUrl(mediaUrl) -> "🎤 Voice message"
    else -> "📷 Photo"
}
