package com.collabsphere.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPollingBackoffTest {
    @Test
    fun `successful polls keep interval and reset failure backoff`() {
        val backoff = SyncPollingBackoff(intervalMs = 1_000, maximumDelayMs = 8_000)

        assertEquals(1_000, backoff.delayAfter(successful = true))
        assertEquals(1_000, backoff.delayAfter(successful = false))
        assertEquals(2_000, backoff.delayAfter(successful = false))
        assertEquals(1_000, backoff.delayAfter(successful = true))
        assertEquals(1_000, backoff.delayAfter(successful = false))
    }

    @Test
    fun `repeated failures are capped`() {
        val backoff = SyncPollingBackoff(intervalMs = 5_000, maximumDelayMs = 60_000)

        repeat(10) { backoff.delayAfter(successful = false) }

        assertEquals(60_000, backoff.delayAfter(successful = false))
    }
}
