package com.collabsphere.app.model

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSignalsTest {

    @Before
    fun setUp() = SyncSignals.resetForTest()

    @After
    fun tearDown() = SyncSignals.resetForTest()

    @Test
    fun eventsBumpOnlyTheirWorkspaceAndScope() {
        SyncSignals.onEvent(WorkspaceSyncEvent(workspaceId = 5, scopes = listOf("tasks", "votes")))
        assertEquals(1, SyncSignals.version(5, "tasks"))
        assertEquals(1, SyncSignals.version(5, "votes"))
        assertEquals(0, SyncSignals.version(5, "notes"))
        assertEquals(0, SyncSignals.version(6, "tasks"))
    }

    @Test
    fun workspaceScopeAlsoWakesTheUserWideWorkspaceLoop() {
        SyncSignals.onEvent(WorkspaceSyncEvent(workspaceId = 9, scopes = listOf("workspace")))
        assertEquals(1, SyncSignals.version(9, "workspace"))
        assertEquals(1, SyncSignals.version(null, "workspace"))
    }

    @Test
    fun pollingRelaxesOnlyWhileLiveAndHealthy() {
        assertEquals(1_000, SyncSignals.waitTimeout(1_000, lastSyncSucceeded = true, live = false))
        assertEquals(60_000, SyncSignals.waitTimeout(1_000, lastSyncSucceeded = true, live = true))
        assertEquals(8_000, SyncSignals.waitTimeout(8_000, lastSyncSucceeded = false, live = true))
        assertEquals(90_000, SyncSignals.waitTimeout(90_000, lastSyncSucceeded = true, live = true))
    }

    @Test
    fun liveRequiresBothServerReadyAndSocket() {
        SyncSignals.isSocketConnected = { true }
        assertFalse(SyncSignals.isLive)
        SyncSignals.onReady()
        assertTrue(SyncSignals.isLive)
        SyncSignals.isSocketConnected = { false }
        assertFalse(SyncSignals.isLive)
    }

    @Test
    fun aSignalWakesAWaitingLoopImmediately() = runTest {
        SyncSignals.isSocketConnected = { true }
        SyncSignals.onReady()
        val seen = SyncSignals.version(1, "tasks")
        val waiting = async { SyncSignals.awaitNext(1, "tasks", seen, 1_000, lastSyncSucceeded = true) }
        advanceTimeBy(500)
        assertFalse(waiting.isCompleted)
        SyncSignals.onEvent(WorkspaceSyncEvent(workspaceId = 1, scopes = listOf("tasks")))
        advanceTimeBy(1)
        assertTrue(waiting.isCompleted)
    }

    @Test
    fun aSignalThatArrivedDuringTheSyncIsNotMissed() = runTest {
        val seen = SyncSignals.version(1, "notes")
        SyncSignals.onEvent(WorkspaceSyncEvent(workspaceId = 1, scopes = listOf("notes")))
        val waiting = async { SyncSignals.awaitNext(1, "notes", seen, 60_000, lastSyncSucceeded = true) }
        advanceTimeBy(1)
        assertTrue(waiting.isCompleted)
    }

    @Test
    fun disconnectAndForegroundWakeEveryLoop() = runTest {
        SyncSignals.isSocketConnected = { true }
        SyncSignals.onReady()
        val seen = SyncSignals.version(3, "files")
        val waiting = async { SyncSignals.awaitNext(3, "files", seen, 5_000, lastSyncSucceeded = true) }
        advanceTimeBy(10_000)
        assertFalse(waiting.isCompleted)
        SyncSignals.onDisconnected()
        advanceTimeBy(1)
        assertTrue(waiting.isCompleted)
        assertFalse(SyncSignals.serverReady)

        val seenAgain = SyncSignals.version(3, "files")
        val waitingAgain = async { SyncSignals.awaitNext(3, "files", seenAgain, 5_000, lastSyncSucceeded = true) }
        SyncSignals.onAppForeground()
        advanceTimeBy(1)
        assertTrue(waitingAgain.isCompleted)
    }
}
