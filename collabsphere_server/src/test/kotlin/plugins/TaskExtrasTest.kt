package plugins

import dto.ChecklistItem
import dto.TaskExtras
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskExtrasTest {

    @Test
    fun `checklist drops blank items, trims text and caps the list`() {
        val items = listOf(ChecklistItem("  write tests ", true), ChecklistItem("   ")) +
                (1..40).map { ChecklistItem("item $it") }
        val normalized = TaskExtras.normalizeChecklist(items)
        assertEquals(TaskExtras.MAX_CHECKLIST_ITEMS, normalized.size)
        assertEquals(ChecklistItem("write tests", true), normalized.first())
    }

    @Test
    fun `labels are trimmed, deduplicated ignoring case and capped`() {
        val labels = listOf(" Bug ", "bug", "", "frontend", "api", "urgent", "ux", "extra")
        assertEquals(listOf("Bug", "frontend", "api", "urgent", "ux"), TaskExtras.normalizeLabels(labels))
    }

    @Test
    fun `encoding round trips and empty lists are stored as null`() {
        val items = listOf(ChecklistItem("a"), ChecklistItem("b", true))
        assertEquals(items, TaskExtras.decodeChecklist(TaskExtras.encodeChecklist(items)))
        assertEquals(listOf("x", "y"), TaskExtras.decodeLabels(TaskExtras.encodeLabels(listOf("x", "y"))))
        assertNull(TaskExtras.encodeChecklist(emptyList()))
        assertEquals(emptyList(), TaskExtras.decodeLabels("not json"))
    }
}
