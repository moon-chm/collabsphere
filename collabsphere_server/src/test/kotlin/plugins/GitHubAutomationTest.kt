package plugins

import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubAutomationTest {

    @Test
    fun `taskReferences finds every mentioned task`() {
        assertEquals(setOf(12, 7), taskReferences("Refactor login for T-12 and t-7"))
    }

    @Test
    fun `taskReferences ignores ids embedded in other words`() {
        assertEquals(emptySet(), taskReferences("See PROJECT-T-12 and XT-4"))
    }

    @Test
    fun `closingTaskReferences matches closing keywords only`() {
        val message = "Fixes T-3, closes T-4, resolved: T-5, mentions T-6"
        assertEquals(setOf(3, 4, 5), closingTaskReferences(message))
    }

    @Test
    fun `closingTaskReferences ignores plain mentions`() {
        assertEquals(emptySet(), closingTaskReferences("Work on T-9 continues"))
    }
}
