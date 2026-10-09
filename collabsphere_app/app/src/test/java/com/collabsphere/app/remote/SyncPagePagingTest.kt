package com.collabsphere.app.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPagePagingTest {
    @Test
    fun `applies every delta page before returning the final cursor`() = runTest {
        val fetchedTokens = mutableListOf<String?>()
        val applied = mutableListOf<Int>()

        val result = applySyncPages(
            fetch = { token ->
                fetchedTokens += token
                when (token) {
                    null -> SyncPage(items = listOf(1, 2), cursor = null, reset = false, nextPageToken = "next")
                    "next" -> SyncPage(items = listOf(3), cursor = 91, reset = false)
                    else -> error("Unexpected page token: $token")
                }
            },
            apply = { applied += it }
        )

        assertEquals(listOf(null, "next"), fetchedTokens)
        assertEquals(listOf(1, 2, 3), applied)
        assertEquals(91L, result.cursor)
        assertEquals(null, result.nextPageToken)
        assertEquals(emptyList<Int>(), result.items)
    }

    @Test
    fun `does not apply rows when server requests a cursor reset`() = runTest {
        val applied = mutableListOf<Int>()
        val result = applySyncPages(
            fetch = { SyncPage(items = emptyList(), cursor = 5, reset = true) },
            apply = { applied += it }
        )

        assertEquals(emptyList<Int>(), applied)
        assertEquals(true, result.reset)
    }
}
