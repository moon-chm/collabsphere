package com.collabsphere.app.model

import com.collabsphere.app.dto.notification.NotificationResponse
import java.util.Calendar
import java.util.TimeZone

enum class InboxFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    MENTIONS("Mentions"),
    TASKS("Tasks"),
    MESSAGES("Messages"),
    ACTIVITY("Activity");

    fun matches(notification: NotificationResponse): Boolean = when (this) {
        ALL -> true
        UNREAD -> !notification.isRead
        MENTIONS -> notification.type == "MENTION"
        TASKS -> notification.type in TASK_TYPES
        MESSAGES -> notification.type in MESSAGE_TYPES
        ACTIVITY -> notification.type != "MENTION" && notification.type !in TASK_TYPES && notification.type !in MESSAGE_TYPES
    }

    private companion object {
        val TASK_TYPES = setOf("TASK_ASSIGNED", "TASK_UPDATED", "TASK_DUE")
        val MESSAGE_TYPES = setOf("DM", "CHANNEL_MESSAGE")
    }
}

data class InboxSection(val title: String, val items: List<NotificationResponse>)

object NotificationInbox {

    fun sections(
        notifications: List<NotificationResponse>,
        now: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): List<InboxSection> {
        val todayStart = startOfDay(now, zone)
        val yesterdayStart = todayStart - dayLength(todayStart - 1, zone)
        val weekStart = Calendar.getInstance(zone).apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -6)
        }.timeInMillis
        val buckets = linkedMapOf(
            "Today" to mutableListOf<NotificationResponse>(),
            "Yesterday" to mutableListOf(),
            "Earlier this week" to mutableListOf(),
            "Older" to mutableListOf()
        )
        notifications.sortedByDescending { it.createdAt }.forEach { n ->
            val key = when {
                n.createdAt >= todayStart -> "Today"
                n.createdAt >= yesterdayStart -> "Yesterday"
                n.createdAt >= weekStart -> "Earlier this week"
                else -> "Older"
            }
            buckets.getValue(key).add(n)
        }
        return buckets.filterValues { it.isNotEmpty() }.map { (title, items) -> InboxSection(title, items) }
    }

    fun mergeIncoming(current: List<NotificationResponse>, incoming: NotificationResponse): List<NotificationResponse> {
        val existing = current.indexOfFirst { it.id == incoming.id }
        return if (existing >= 0) current.toMutableList().also { it[existing] = incoming } else listOf(incoming) + current
    }

    fun appendPage(current: List<NotificationResponse>, page: List<NotificationResponse>): List<NotificationResponse> {
        val seen = current.mapTo(HashSet()) { it.id }
        return current + page.filter { seen.add(it.id) }
    }

    fun unreadCounts(notifications: List<NotificationResponse>): Map<InboxFilter, Int> =
        InboxFilter.entries.associateWith { filter -> notifications.count { !it.isRead && filter.matches(it) } }

    fun emptyMessage(filter: InboxFilter): Pair<String, String> = when (filter) {
        InboxFilter.ALL -> "You're all caught up" to "New mentions, tasks and messages will show up here."
        InboxFilter.UNREAD -> "Nothing unread" to "You've seen everything. Nice work."
        InboxFilter.MENTIONS -> "No mentions yet" to "When someone @mentions you, you'll see it here."
        InboxFilter.TASKS -> "No task updates" to "Assignments, changes and due dates land here."
        InboxFilter.MESSAGES -> "No message alerts" to "Messages you missed while away show up here."
        InboxFilter.ACTIVITY -> "No other activity" to "Invites, GitHub updates and achievements appear here."
    }

    fun relativeTime(timestamp: Long, now: Long): String {
        val minutes = ((now - timestamp).coerceAtLeast(0)) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m"
            minutes < 24 * 60 -> "${minutes / 60}h"
            minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d"
            else -> "${minutes / (7 * 24 * 60)}w"
        }
    }

    private fun startOfDay(time: Long, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayLength(timeInDay: Long, zone: TimeZone): Long {
        val start = startOfDay(timeInDay, zone)
        val next = Calendar.getInstance(zone).apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return next - start
    }
}
