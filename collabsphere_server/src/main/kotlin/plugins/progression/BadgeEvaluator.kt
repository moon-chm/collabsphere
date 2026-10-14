package plugins.progression

/** Pure badge-tier logic. Tiers are cumulative: reaching Gold implies Silver and Bronze. */
object BadgeEvaluator {

    /** Every tier whose threshold [count] meets, lowest first. */
    fun tiersReached(family: BadgeFamily, count: Long): List<BadgeTier> =
        BadgeTier.entries.filter { count >= family.threshold(it) }

    /** The next tier to earn and its threshold, or null once Gold is reached. */
    fun nextTier(family: BadgeFamily, count: Long): Pair<BadgeTier, Long>? =
        BadgeTier.entries.firstOrNull { count < family.threshold(it) }?.let { it to family.threshold(it) }

    /**
     * Longest run of consecutive days containing [day], given every active day (epoch-day numbers).
     * Late-arriving events can fill a gap, so the run is measured in both directions.
     */
    fun runContaining(day: Long, activeDays: Set<Long>): Int {
        if (day !in activeDays) return 0
        var start = day
        while ((start - 1) in activeDays) start--
        var end = day
        while ((end + 1) in activeDays) end++
        return (end - start + 1).toInt()
    }

    /** Length of the streak still alive on [today]: ending today, or yesterday if today has no activity yet. */
    fun currentStreak(today: Long, activeDays: Set<Long>): Int {
        val anchor = when {
            today in activeDays -> today
            (today - 1) in activeDays -> today - 1
            else -> return 0
        }
        var start = anchor
        while ((start - 1) in activeDays) start--
        return (anchor - start + 1).toInt()
    }
}
