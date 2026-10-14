package plugins.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProgressionCatalogTest {
    private val catalog = ProgressionCatalogs.current

    @Test
    fun `the deployed catalog has the twenty specified designations in order`() {
        val expected = listOf(
            "Graduate Trainee" to 0L, "Junior Developer" to 100L, "Associate Software Engineer" to 250L,
            "Software Engineer I" to 500L, "Software Engineer II" to 800L, "Senior Software Engineer" to 1200L,
            "Technical Specialist" to 1700L, "Lead Software Engineer" to 2300L, "Staff Software Engineer" to 3000L,
            "Principal Software Engineer" to 3800L, "Engineering Manager" to 4700L, "Senior Engineering Manager" to 5700L,
            "Director of Engineering" to 6800L, "Senior Director of Engineering" to 8000L,
            "Vice President of Engineering" to 9500L, "Senior Vice President of Engineering" to 11200L,
            "Chief Technology Officer" to 13000L, "Chief Technology & Product Officer" to 15000L,
            "Chief Innovation Officer" to 17500L, "Legendary Industry Fellow" to 20000L
        )
        assertEquals(expected, catalog.levels.map { it.title to it.requiredSp })
        assertEquals((1..20).toList(), catalog.levels.map { it.level })
    }

    @Test
    fun `fifteen badge families define forty-five tiers`() {
        assertEquals(15, catalog.badges.size)
        assertEquals(45, catalog.badges.sumOf { BadgeTier.entries.size })
        assertEquals(
            listOf(
                "CODECRAFT", "DEADLINE_ACE", "SYNERGY_STAR", "TASK_TITAN", "MOMENTUM_KEEPER", "BUG_BUSTER",
                "KNOWLEDGE_ARCHITECT", "MILESTONE_MAVERICK", "GUARDIAN", "MERGE_MASTER", "BRIDGE_BUILDER",
                "MENTOR_SPARK", "LAUNCH_COMMANDER", "SPHERE_EXPLORER", "SPHERE_LEGEND"
            ),
            catalog.badges.map { it.key }
        )
        catalog.badges.forEach { assertTrue(it.bronze < it.silver && it.silver < it.gold, it.key) }
    }

    @Test
    fun `specified tier thresholds are configured`() {
        val tiers = catalog.badges.associate { it.key to Triple(it.bronze, it.silver, it.gold) }
        assertEquals(Triple(5L, 25L, 100L), tiers["CODECRAFT"])
        assertEquals(Triple(3L, 7L, 30L), tiers["MOMENTUM_KEEPER"])
        assertEquals(Triple(10L, 50L, 200L), tiers["TASK_TITAN"])
        assertEquals(Triple(1L, 5L, 20L), tiers["MILESTONE_MAVERICK"])
        assertEquals(Triple(3L, 15L, 40L), tiers["MENTOR_SPARK"])
        assertEquals(Triple(3L, 7L, 12L), tiers["SPHERE_LEGEND"])
    }

    @Test
    fun `every badge counter and reward rule referenced in code exists`() {
        val counters = catalog.badges.map { it.counter }.toSet()
        assertEquals(15, counters.size, "each family has its own counter")
        listOf(RuleKeys.TASK_COMPLETED, RuleKeys.TASK_COMPLETED_COMPLEX, RuleKeys.TASK_RELIABILITY,
            RuleKeys.TASK_SELF_COMPLETED, RuleKeys.NOTE_APPROVED).forEach { catalog.rule(it) }
    }

    @Test
    fun `invalid catalogs are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ProgressionCatalogs.validate(catalog.copy(levels = catalog.levels.map { if (it.level == 1) it.copy(requiredSp = 5) else it }))
        }
        assertFailsWith<IllegalArgumentException> {
            ProgressionCatalogs.validate(catalog.copy(levels = catalog.levels.map { if (it.level == 3) it.copy(requiredSp = 100) else it }))
        }
        assertFailsWith<IllegalArgumentException> {
            ProgressionCatalogs.validate(catalog.copy(badges = catalog.badges.map { if (it.key == "TASK_TITAN") it.copy(silver = 5) else it }))
        }
        assertFailsWith<IllegalArgumentException> {
            ProgressionCatalogs.validate(catalog.copy(rules = catalog.rules + catalog.rules.first()))
        }
    }
}
