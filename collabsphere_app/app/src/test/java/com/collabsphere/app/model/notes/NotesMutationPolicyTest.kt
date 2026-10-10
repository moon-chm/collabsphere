package com.collabsphere.app.model.notes

import android.util.Log
import androidx.work.WorkManager
import com.collabsphere.app.remote.ApiStatusException
import com.collabsphere.app.remote.note.NoteApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class NotesMutationPolicyTest {
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `permanent note update refusal keeps cached note unchanged and is not queued`() = runBlocking {
        val dao = mockk<NotesDao>(relaxed = true)
        val api = mockk<NoteApiService>()
        val workManager = mockk<WorkManager>(relaxed = true)
        coEvery { api.updateNote(12, any()) } throws ApiStatusException(403)
        val repo = NotesRepo(dao, api, workManager, mockk(relaxed = true))

        val updated = NotesEntity(12, 7, 3, "Renamed", "new text")
        val saved = repo.updatetheNote(updated)

        assertFalse(saved)
        coVerify(exactly = 0) { dao.updatenotes(any()) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<androidx.work.OneTimeWorkRequest>()) }
    }
}
