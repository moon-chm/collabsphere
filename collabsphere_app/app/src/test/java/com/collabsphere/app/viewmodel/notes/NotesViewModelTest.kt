package com.collabsphere.app.viewmodel.notes

import com.collabsphere.app.model.notes.NotesRepo
import io.mockk.coEvery
// removed
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: NotesRepo

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        every { repo.getallnotestoscreen(3) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `repeated offline note creation is dispatched once and accepts a negative local ID`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Result<Long>>()
        coEvery { repo.addnotestoscreen(any()) } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = NotesViewModel(repo, loggedUserId = 7, loggedWorkspaceId = 3)
        viewModel.onNotesNameChange("Draft")

        viewModel.createNote()
        viewModel.createNote()

        assertTrue(viewModel.isCreatingNote.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.addnotestoscreen(any()) }

        finishRequest.complete(Result.success(-23L))
        runCurrent()
        assertFalse(viewModel.isCreatingNote.value)
        assertEquals("Notes Draft created successfully", viewModel.notesStatus.value)
        assertTrue(viewModel.notesName.value.isEmpty())
        coVerify(exactly = 1) { repo.addnotestoscreen(any()) }
    }
}
