package com.collabsphere.app.viewmodel.task

import com.collabsphere.app.model.task.TaskRepo
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: TaskRepo

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        every { repo.getTasks(3) } returns flowOf(emptyList())
        every { repo.getWorkspaceMembers(3) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid repeated task creation dispatches once and leaves dialog open until completion`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Result<Long>>()
        coEvery { repo.addTask(any()) } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = TaskViewModel(repo, loggedUserId = 7, loggedWorkspaceId = 3)
        viewModel.onTaskNameChange("Prepare release")
        var completedCount = 0

        viewModel.onCreateTask { completedCount++ }
        viewModel.onCreateTask { completedCount++ }

        runCurrent()
        assertTrue(viewModel.isCreatingTask.value)
        requestStarted.await()
        coVerify(exactly = 1) { repo.addTask(any()) }
        assertTrue(viewModel.taskName.value.isNotEmpty())
        assertTrue(completedCount == 0)

        finishRequest.complete(Result.success(90L))
        viewModel.isCreatingTask.first { !it }
        assertFalse(viewModel.isCreatingTask.value)
        assertTrue(viewModel.taskName.value.isEmpty())
        assertTrue(completedCount == 1)
        coVerify(exactly = 1) { repo.addTask(any()) }
    }
}
