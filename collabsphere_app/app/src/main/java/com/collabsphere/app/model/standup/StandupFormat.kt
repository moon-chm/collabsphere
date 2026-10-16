package com.collabsphere.app.model.standup

import java.util.Calendar

object StandupFormat {
    const val MAX_LENGTH = 4000
    private const val BULLET = "• "

    fun toEditable(lines: List<String>): String = lines.joinToString("\n") { BULLET + it }

    fun parseEditable(text: String): List<String> =
        text.lines()
            .map { it.trim().removePrefix("•").removePrefix("- ").removePrefix("* ").trim() }
            .filter { it.isNotEmpty() }

    fun compose(dateLabel: String, yesterday: String, today: String, blockers: String): String? {
        val y = parseEditable(yesterday)
        val t = parseEditable(today)
        val b = parseEditable(blockers)
        if (y.isEmpty() && t.isEmpty()) return null
        val out = StringBuilder("**Standup · $dateLabel**")
        fun section(title: String, lines: List<String>) {
            out.append("\n**").append(title).append("**")
            lines.forEach { out.append('\n').append(BULLET).append(it) }
        }
        if (y.isNotEmpty()) section("Yesterday", y)
        if (t.isNotEmpty()) section("Today", t)
        section("Blockers", b.ifEmpty { listOf("None") })
        return out.toString().take(MAX_LENGTH)
    }

    fun previousWorkdayStart(now: Calendar = Calendar.getInstance()): Long {
        val day = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val back = when (day.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 3
            Calendar.SUNDAY -> 2
            else -> 1
        }
        day.add(Calendar.DAY_OF_YEAR, -back)
        return day.timeInMillis
    }
}
