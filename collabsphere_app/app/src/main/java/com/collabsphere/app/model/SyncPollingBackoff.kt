package com.collabsphere.app.model

/** Keeps the normal sync cadence while reducing repeated requests during outages. */
class SyncPollingBackoff(
    private val intervalMs: Long,
    private val maximumDelayMs: Long = 60_000L
) {
    private var consecutiveFailures = 0

    fun delayAfter(successful: Boolean): Long {
        if (successful) {
            consecutiveFailures = 0
            return intervalMs
        }

        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(30)
        val multiplier = 1L shl (consecutiveFailures - 1).coerceAtMost(30)
        return if (intervalMs > maximumDelayMs / multiplier) maximumDelayMs
        else (intervalMs * multiplier).coerceAtMost(maximumDelayMs)
    }
}
