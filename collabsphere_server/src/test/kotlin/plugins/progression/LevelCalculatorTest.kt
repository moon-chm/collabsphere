package plugins.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelCalculatorTest {
    private val levels = ProgressionCatalogs.current.levels
    private val calculator = LevelCalculator(levels)

    @Test
    fun `zero sp is level one`() {
        assertEquals(1, calculator.levelFor(0).level)
        assertEquals(1, calculator.levelFor(-50).level, "negative totals are treated as zero")
    }

    @Test
    fun `each threshold selects its level exactly at and just below the boundary`() {
        levels.drop(1).forEach { level ->
            assertEquals(level.level, calculator.levelFor(level.requiredSp).level, "at ${level.requiredSp}")
            assertEquals(level.level - 1, calculator.levelFor(level.requiredSp - 1).level, "below ${level.requiredSp}")
        }
    }

    @Test
    fun `sp beyond level twenty stays at level twenty without dividing by zero`() {
        val top = calculator.progress(1_000_000)
        assertEquals(20, top.level.level)
        assertTrue(top.isMaxLevel)
        assertNull(top.next)
        assertEquals(0, top.spSpanToNext)
        assertEquals(1_000_000 - 20_000L, top.spIntoLevel)
    }

    @Test
    fun `progress is measured between the current and next thresholds`() {
        val progress = calculator.progress(300) // level 3 starts at 250, level 4 at 500
        assertEquals(3, progress.level.level)
        assertEquals(4, progress.next?.level)
        assertEquals(50, progress.spIntoLevel)
        assertEquals(250, progress.spSpanToNext)
        assertFalse(progress.isMaxLevel)
    }

    @Test
    fun `one award can cross several levels and each is reported once`() {
        assertEquals(listOf(2, 3, 4), calculator.levelsCrossed(90, 600).map { it.level })
        assertEquals(emptyList(), calculator.levelsCrossed(120, 240))
        assertEquals(listOf(20), calculator.levelsCrossed(19_999, 25_000).map { it.level })
        assertEquals(emptyList(), calculator.levelsCrossed(25_000, 30_000), "nothing past the top level")
    }

    @Test
    fun `changed thresholds recalculate deterministically`() {
        val lowered = LevelCalculator(levels.map { if (it.level == 2) it.copy(requiredSp = 50) else it })
        assertEquals(1, calculator.levelFor(60).level)
        assertEquals(2, lowered.levelFor(60).level)
    }
}
