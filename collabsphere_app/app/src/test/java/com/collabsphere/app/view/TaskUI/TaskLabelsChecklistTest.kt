package com.collabsphere.app.view.TaskUI

import com.collabsphere.app.dto.task.ChecklistItem
import com.collabsphere.app.model.task.TaskListCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskLabelsChecklistTest {

    @Test
    fun addLabelTrimsAndRejectsDuplicatesIgnoringCase() {
        assertEquals(listOf("Bug"), addLabel(emptyList(), "  Bug  "))
        assertEquals(listOf("Bug"), addLabel(listOf("Bug"), "bug"))
        assertEquals(listOf("Bug"), addLabel(listOf("Bug"), "   "))
    }

    @Test
    fun addLabelStopsAtTheLimitAndCapsLength() {
        val full = (1..MAX_TASK_LABELS).map { "l$it" }
        assertEquals(full, addLabel(full, "extra"))
        assertEquals(MAX_TASK_LABEL_LENGTH, addLabel(emptyList(), "x".repeat(40)).single().length)
    }

    @Test
    fun codecRoundTripsAndToleratesBadInput() {
        val items = listOf(ChecklistItem("a"), ChecklistItem("b", done = true))
        assertEquals(items, TaskListCodec.decodeChecklist(TaskListCodec.encodeChecklist(items)))
        assertEquals(listOf("x"), TaskListCodec.decodeLabels(TaskListCodec.encodeLabels(listOf("x"))))
        assertEquals(emptyList<ChecklistItem>(), TaskListCodec.decodeChecklist("{broken"))
        assertEquals(emptyList<String>(), TaskListCodec.decodeLabels(null))
    }
}
