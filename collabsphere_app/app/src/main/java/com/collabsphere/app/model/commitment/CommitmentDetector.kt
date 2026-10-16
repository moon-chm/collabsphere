package com.collabsphere.app.model.commitment

data class Commitment(
    val start: Int,
    val end: Int,
    val title: String,
    val dueInDays: Int?,
    val phrase: String
)

object CommitmentDetector {

    private const val APOS = "['’]"
    private const val PREFIX = "(?:(?:by|before|on|until|till)\\s+)?"

    private val trigger = Regex(
        "\\b(?:i$APOS?ll|i will|i shall|i$APOS?m going to|i am going to|i$APOS?m gonna|i am gonna|let me)\\s+",
        RegexOption.IGNORE_CASE
    )

    private val rejectedFirstWords = setOf(
        "not", "never", "be", "have", "know", "see", "think", "guess", "say", "admit", "probably", "maybe",
        "just", "also", "need", "want", "get", "go", "come", "ask", "tell", "wait", "pass", "leave", "keep"
    )

    private val stopWords = setOf(
        "it", "that", "this", "them", "those", "these", "do", "the", "a", "an", "to", "you", "your", "for",
        "of", "on", "in", "and", "or", "up", "with", "me", "my", "we", "us", "our", "some", "thing", "stuff", "one"
    )

    private val trailingConnectors = setOf("by", "before", "on", "until", "till", "this", "next", "the", "at", "around", "in", "and", "so")

    private val weekdays = listOf(
        listOf("monday", "mon"),
        listOf("tuesday", "tues", "tue"),
        listOf("wednesday", "wed"),
        listOf("thursday", "thurs", "thur", "thu"),
        listOf("friday", "fri"),
        listOf("saturday"),
        listOf("sunday")
    )

    private val numberWords = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7
    )

    private class DeadlineRule(pattern: String, val resolve: (MatchResult, Int) -> Int?) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private val deadlineRules = listOf(
        DeadlineRule("\\b${PREFIX}(?:today|tonight|eod|end of (?:the )?day)\\b") { _, _ -> 0 },
        DeadlineRule("\\b${PREFIX}(?:tomorrow|tmrw|tmr)\\b") { _, _ -> 1 },
        DeadlineRule("\\b${PREFIX}(?:end of (?:the )?week|eow|this week)\\b") { _, today -> untilWeekday(5, today) },
        DeadlineRule("\\b${PREFIX}next week\\b") { _, today -> untilWeekday(1, today).let { if (it == 0) 7 else it } },
        DeadlineRule("\\b${PREFIX}(?:this |the )?weekend\\b") { _, today -> if (today == 7) 0 else untilWeekday(6, today) },
        DeadlineRule("\\bin\\s+(\\d{1,2}|a|an|one|two|three|four|five|six|seven)\\s+(days?|weeks?)\\b") { m, _ ->
            val nText = m.groupValues[1].lowercase()
            val n = nText.toIntOrNull() ?: numberWords[nText]
            val perUnit = if (m.groupValues[2].lowercase().startsWith("week")) 7 else 1
            n?.times(perUnit)?.takeIf { it in 0..90 }
        },
        DeadlineRule(
            "\\b${PREFIX}(?:(next|this|coming)\\s+)?(" +
                weekdays.flatten().sortedByDescending { it.length }.joinToString("|") +
                ")\\b(?!$APOS)"
        ) { m, today ->
            val dayText = m.groupValues[2].lowercase()
            val target = weekdays.indexOfFirst { dayText in it } + 1
            val base = untilWeekday(target, today)
            if (m.groupValues[1].lowercase() == "next" && base == 0) 7 else base
        }
    )

    fun detect(text: String, todayDayOfWeek: Int): Commitment? {
        require(todayDayOfWeek in 1..7)
        for (match in trigger.findAll(text)) {
            val clauseStart = match.range.last + 1
            val sentenceEnd = text.indexOfAny(charArrayOf('.', '!', '?', '\n', ';'), clauseStart).let { if (it < 0) text.length else it }
            if (sentenceEnd < text.length && text[sentenceEnd] == '?') continue
            val clause = text.substring(clauseStart, sentenceEnd)
            val firstWord = clause.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.trim(',', ':') ?: continue
            if (firstWord.isEmpty() || firstWord in rejectedFirstWords) continue

            val deadline = findDeadline(clause, todayDayOfWeek)
            val actionRaw = if (deadline != null) clause.substring(0, deadline.first.first) else clause
            val title = cleanTitle(actionRaw) ?: continue
            val dueInDays = deadline?.second

            val contentWords = title.lowercase().split(Regex("[^a-z0-9#@-]+")).filter { it.length > 1 && it !in stopWords }
            if (contentWords.isEmpty()) continue
            if (contentWords.size < 2 && dueInDays == null) continue

            val end = clauseStart + (deadline?.first?.last?.plus(1) ?: actionRaw.trimEnd().length)
            return Commitment(
                start = match.range.first,
                end = end,
                title = title,
                dueInDays = dueInDays,
                phrase = text.substring(match.range.first, end)
            )
        }
        return null
    }

    private fun findDeadline(clause: String, today: Int): Pair<IntRange, Int>? {
        var best: Pair<IntRange, Int>? = null
        for (rule in deadlineRules) {
            for (m in rule.regex.findAll(clause)) {
                val days = rule.resolve(m, today) ?: continue
                val current = best
                val better = current == null ||
                    m.range.first < current.first.first ||
                    (m.range.first == current.first.first && m.range.last > current.first.last)
                if (better) best = m.range to days
                break
            }
        }
        return best
    }

    private fun cleanTitle(raw: String): String? {
        val words = raw.trim().trim(',', ':', '-').split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        while (words.isNotEmpty() && words.last().lowercase().trim(',') in trailingConnectors) words.removeAt(words.lastIndex)
        if (words.isEmpty()) return null
        val joined = words.joinToString(" ").trimEnd(',', ':', '-').take(80).trim()
        if (joined.length < 3) return null
        return joined.replaceFirstChar { it.uppercase() }
    }

    private fun untilWeekday(target: Int, today: Int): Int = ((target - today) % 7 + 7) % 7
}
