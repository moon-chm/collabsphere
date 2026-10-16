package plugins

internal object FocusRules {
    const val MIN_FOCUS_MS = 60_000L
    const val MAX_FOCUS_MS = 12L * 60 * 60 * 1000
    const val MIN_HOLD_MS = 30_000L
    const val MAX_CONTENT_LENGTH = 4000

    fun untilRejection(until: Long?, now: Long): String? = when {
        until == null -> null
        until < now + MIN_FOCUS_MS -> "Focus must last at least a minute"
        until > now + MAX_FOCUS_MS -> "Focus can last up to 12 hours"
        else -> null
    }

    fun activeUntil(stored: Long?, now: Long): Long? = stored?.takeIf { it > now }

    fun holdSendAt(stored: Long?, now: Long): Long? = stored?.takeIf { it >= now + MIN_HOLD_MS }

    fun releaseAt(newUntil: Long?, now: Long): Long = activeUntil(newUntil, now) ?: now

    fun contentRejection(content: String): String? = when {
        content.isBlank() -> "Message cannot be empty"
        content.length > MAX_CONTENT_LENGTH -> "Message too long"
        else -> null
    }
}
