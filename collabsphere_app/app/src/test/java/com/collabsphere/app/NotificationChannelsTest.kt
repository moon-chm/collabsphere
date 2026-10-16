package com.collabsphere.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationChannelsTest {

    @Test
    fun eachNotificationTypeLandsInItsOwnChannel() {
        assertEquals(NotificationChannels.DIRECT, NotificationChannels.forType("DM"))
        assertEquals(NotificationChannels.MENTIONS, NotificationChannels.forType("MENTION"))
        assertEquals(NotificationChannels.CHANNELS, NotificationChannels.forType("CHANNEL_MESSAGE"))
        assertEquals(NotificationChannels.TASKS, NotificationChannels.forType("TASK_ASSIGNED"))
        assertEquals(NotificationChannels.TASKS, NotificationChannels.forType("TASK_UPDATED"))
        assertEquals(NotificationChannels.TASKS, NotificationChannels.forType("TASK_DUE"))
        assertEquals(NotificationChannels.GITHUB, NotificationChannels.forType("GITHUB_CI"))
        assertEquals(NotificationChannels.CHANNELS, NotificationChannels.forType("SOMETHING_NEW"))
        assertEquals(NotificationChannels.CHANNELS, NotificationChannels.forType(null))
    }

    @Test
    fun workColorsDifferFromMessageColors() {
        assertEquals(NotificationChannels.TASK_COLOR, NotificationChannels.colorFor("TASK_DUE"))
        assertEquals(NotificationChannels.GITHUB_COLOR, NotificationChannels.colorFor("GITHUB_CI"))
        assertEquals(NotificationChannels.BRAND_COLOR, NotificationChannels.colorFor("MENTION"))
    }

    @Test
    fun mediaOnlyMessagesGetReadablePreviews() {
        assertEquals("hello", dmPreviewText("hello", "https://res.cloudinary.com/x/image/upload/a.jpg"))
        assertEquals("📷 Photo", dmPreviewText("", "https://res.cloudinary.com/x/image/upload/a.jpg"))
        assertEquals("🎤 Voice message", dmPreviewText("  ", "https://res.cloudinary.com/x/video/upload/voice/a.m4a"))
        assertEquals("New message", dmPreviewText("", null))
    }
}
