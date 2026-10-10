package com.collabsphere.app.model

import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundSyncRegistryTest {
    @Test
    fun `logout cancellation stops and joins registered polling jobs`() = runTest {
        val enteredLoop = CompletableDeferred<Unit>()
        val job = backgroundScope.launch {
            enteredLoop.complete(Unit)
            awaitCancellation()
        }
        val key = "test:${UUID.randomUUID()}"
        runCurrent()

        assertTrue(BackgroundSyncRegistry.register(key, job))
        enteredLoop.await()
        BackgroundSyncRegistry.cancelAllAndJoin()

        assertFalse(job.isActive)
        BackgroundSyncRegistry.unregister(key, job)
    }
}
