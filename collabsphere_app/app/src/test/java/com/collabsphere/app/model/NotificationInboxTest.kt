package com.collabsphere.app.model

import com.collabsphere.app.dto.notification.NotificationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NotificationInboxTest {

    private val zone = TimeZone.getTimeZone("Asia/Kolkata")
    private val now = Calendar.getInstance(zone).apply { clear(); set(2026, Calendar.OCTOBER, 16, 15, 0) }.timeInMillis
    private val hour = 60 * 60 * 1000L

    private fun n(id: Int, type: String = "MENTION", read: Boolean = false, at: Long = now) =
        NotificationResponse(id = id, recipientId = 1, type = type, title = "t$id", body = "b$id", isRead = read, createdAt = at)

    @Test
    fun filtersSplitTypesWithoutOverlap() {
        val all = listOf(
            n(1, "MENTION"), n(2, "TASK_DUE"), n(3, "TASK_ASSIGNED"), n(4, "DM"),
            n(5, "CHANNEL_MESSAGE"), n(6, "GITHUB_CI"), n(7, "WORKSPACE_INVITE", read = true)
        )
        assertEquals(listOf(1), all.filter { InboxFilter.MENTIONS.matches(it) }.map { it.id })
        assertEquals(listOf(2, 3), all.filter { InboxFilter.TASKS.matches(it) }.map { it.id })
        assertEquals(listOf(4, 5), all.filter { InboxFilter.MESSAGES.matches(it) }.map { it.id })
        assertEquals(listOf(6, 7), all.filter { InboxFilter.ACTIVITY.matches(it) }.map { it.id })
        assertEquals(6, all.count { InboxFilter.UNREAD.matches(it) })
        val categorised = listOf(InboxFilter.MENTIONS, InboxFilter.TASKS, InboxFilter.MESSAGES, InboxFilter.ACTIVITY)
        all.forEach { item -> assertEquals(1, categorised.count { it.matches(item) }) }
    }

    @Test
    fun sectionsFollowLocalCalendarDays() {
        val sections = NotificationInbox.sections(
            listOf(
                n(1, at = now - hour),
                n(2, at = now - 16 * hour),
                n(3, at = now - 40 * hour),
                n(4, at = now - 4 * 24 * hour),
                n(5, at = now - 30 * 24 * hour)
            ),
            now,
            zone
        )
        assertEquals(listOf("Today", "Yesterday", "Earlier this week", "Older"), sections.map { it.title })
        assertEquals(listOf(1), sections[0].items.map { it.id })
        assertEquals(listOf(2), sections[1].items.map { it.id })
        assertEquals(listOf(3, 4), sections[2].items.map { it.id })
        assertEquals(listOf(5), sections[3].items.map { it.id })
    }

    @Test
    fun emptySectionsAreDropped() {
        val sections = NotificationInbox.sections(listOf(n(1, at = now - hour)), now, zone)
        assertEquals(listOf("Today"), sections.map { it.title })
        assertTrue(NotificationInbox.sections(emptyList(), now, zone).isEmpty())
    }

    @Test
    fun liveDuplicatesReplaceInsteadOfStacking() {
        val current = listOf(n(2), n(1))
        val merged = NotificationInbox.mergeIncoming(current, n(1, read = true))
        assertEquals(listOf(2, 1), merged.map { it.id })
        assertTrue(merged.last().isRead)
        assertEquals(listOf(3, 2, 1), NotificationInbox.mergeIncoming(current, n(3)).map { it.id })
    }

    @Test
    fun pagesSkipItemsAlreadyShown() {
        val merged = NotificationInbox.appendPage(listOf(n(5), n(4)), listOf(n(4), n(3), n(2)))
        assertEquals(listOf(5, 4, 3, 2), merged.map { it.id })
    }

    @Test
    fun unreadCountsPerFilter() {
        val counts = NotificationInbox.unreadCounts(listOf(n(1, "MENTION"), n(2, "TASK_DUE"), n(3, "TASK_DUE", read = true)))
        assertEquals(2, counts[InboxFilter.ALL])
        assertEquals(1, counts[InboxFilter.MENTIONS])
        assertEquals(1, counts[InboxFilter.TASKS])
        assertEquals(0, counts[InboxFilter.MESSAGES])
    }

    @Test
    fun relativeTimeIsCompact() {
        assertEquals("just now", NotificationInbox.relativeTime(now - 10_000, now))
        assertEquals("5m", NotificationInbox.relativeTime(now - 5 * 60_000, now))
        assertEquals("3h", NotificationInbox.relativeTime(now - 3 * hour, now))
        assertEquals("2d", NotificationInbox.relativeTime(now - 50 * hour, now))
        assertEquals("2w", NotificationInbox.relativeTime(now - 15 * 24 * hour, now))
        assertFalse(NotificationInbox.relativeTime(now + hour, now).startsWith("-"))
    }
}
