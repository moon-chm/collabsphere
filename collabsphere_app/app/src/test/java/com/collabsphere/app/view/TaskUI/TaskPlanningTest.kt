package com.collabsphere.app.view.TaskUI

import com.collabsphere.app.model.task.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskPlanningTest {

    private val day = 24 * 60 * 60 * 1000L
    private val today = 1_760_000_000_000L - (1_760_000_000_000L % day)

    @Test
    fun dueStateClassifiesRelativeToToday() {
        assertEquals(DueState.OVERDUE, dueStateOf(today - day, today))
        assertEquals(DueState.TODAY, dueStateOf(today, today))
        assertEquals(DueState.TOMORROW, dueStateOf(today + day, today))
        assertEquals(DueState.LATER, dueStateOf(today + 2 * day, today))
    }

    @Test
    fun todayUtcMidnightIsAlignedToADay() {
        assertEquals(0L, todayUtcMidnight() % day)
    }

    @Test
    fun remotePriorityFallsBackToMedium() {
        assertEquals(TaskPriority.HIGH, TaskPriority.fromRemote("high"))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromRemote("urgent"))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromRemote(null))
    }
}
