package com.collabsphere.app.model

import com.collabsphere.app.remote.ApiStatusException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class SyncPolicyTest {

    @Test
    fun `success statuses are done`() {
        assertEquals(SyncDecision.DONE, SyncPolicy.forStatus(200, isDelete = false))
        assertEquals(SyncDecision.DONE, SyncPolicy.forStatus(204, isDelete = true))
    }

    @Test
    fun `a delete of something already gone is done`() {
        assertEquals(SyncDecision.DONE, SyncPolicy.forStatus(404, isDelete = true))
        assertEquals(SyncDecision.DONE, SyncPolicy.forStatus(410, isDelete = true))
    }

    @Test
    fun `a create or update of something missing is dropped, not retried forever`() {
        assertEquals(SyncDecision.DROP, SyncPolicy.forStatus(404, isDelete = false))
    }

    @Test
    fun `server errors and throttling are retried`() {
        listOf(408, 425, 429, 500, 502, 503, 504).forEach { code ->
            assertEquals("status $code", SyncDecision.RETRY, SyncPolicy.forStatus(code, isDelete = false))
            assertEquals("status $code", SyncDecision.RETRY, SyncPolicy.forStatus(code, isDelete = true))
        }
    }

    @Test
    fun `permanent client errors are dropped`() {
        listOf(400, 401, 403, 409, 413, 422).forEach { code ->
            assertEquals("status $code", SyncDecision.DROP, SyncPolicy.forStatus(code, isDelete = false))
        }
    }

    @Test
    fun `network failures are retried`() {
        assertEquals(SyncDecision.RETRY, SyncPolicy.forFailure(IOException("offline"), isDelete = false))
    }

    @Test
    fun `api status failures use the status rules`() {
        assertEquals(SyncDecision.DROP, SyncPolicy.forFailure(ApiStatusException(403), isDelete = false))
        assertEquals(SyncDecision.RETRY, SyncPolicy.forFailure(ApiStatusException(503), isDelete = true))
        assertEquals(SyncDecision.DONE, SyncPolicy.forFailure(ApiStatusException(404), isDelete = true))
    }
}
