package plugins.progression

/** Where a total SP sits on the career ladder. All values are integers; nothing divides by zero at the top level. */
data class LevelProgress(
    val level: CareerLevel,
    val next: CareerLevel?,
    /** SP earned since reaching [level]. */
    val spIntoLevel: Long,
    /** SP between [level] and [next]; 0 at the maximum level. */
    val spSpanToNext: Long
) {
    val isMaxLevel: Boolean get() = next == null
}

/** Deterministic level maths over a validated catalog (levels ordered, strictly increasing thresholds). */
class LevelCalculator(private val levels: List<CareerLevel>) {

    /** The highest level whose threshold does not exceed [totalSp]; SP past the top level stays at the top level. */
    fun levelFor(totalSp: Long): CareerLevel {
        val sp = totalSp.coerceAtLeast(0)
        return levels.last { it.requiredSp <= sp }
    }

    fun progress(totalSp: Long): LevelProgress {
        val sp = totalSp.coerceAtLeast(0)
        val current = levelFor(sp)
        val next = levels.getOrNull(current.level) // levels are 1-based, so index `level` is the next one
        return LevelProgress(
            level = current,
            next = next,
            spIntoLevel = sp - current.requiredSp,
            spSpanToNext = next?.let { it.requiredSp - current.requiredSp } ?: 0
        )
    }

    /** Every level newly reached when the total moves from [beforeSp] to [afterSp], lowest first. */
    fun levelsCrossed(beforeSp: Long, afterSp: Long): List<CareerLevel> {
        val from = levelFor(beforeSp).level
        val to = levelFor(afterSp).level
        return if (to <= from) emptyList() else levels.filter { it.level in (from + 1)..to }
    }
}
