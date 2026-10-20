package com.opencell.memory

import kotlin.math.ln

/** Deterministic, local ranking for the small amount of context sent to an LLM. */
object MemoryRetriever {
    fun retrieve(
        facts: List<MemoryFact>,
        contactKey: String?,
        query: String,
        now: Long = System.currentTimeMillis(),
        limit: Int = 20,
        includeSensitive: Boolean = false,
    ): List<MemoryFact> {
        val queryWords = words(query)
        return facts.asSequence()
            .filter { !it.retracted && !it.needsConfirmation }
            .filter { it.expiresAtMillis == null || it.expiresAtMillis > now }
            .filter { it.contactKey == null || it.contactKey == contactKey }
            .filter { includeSensitive || it.category != MemoryCategory.SENSITIVE }
            .map { fact -> fact to score(fact, contactKey, queryWords, now) }
            .sortedWith(compareByDescending<Pair<MemoryFact, Double>> { it.second }.thenByDescending { it.first.createdAtMillis })
            .take(limit.coerceAtLeast(0))
            .map { it.first }
            .toList()
    }

    private fun score(fact: MemoryFact, contactKey: String?, queryWords: Set<String>, now: Long): Double {
        val exactScope = if (contactKey != null && fact.contactKey == contactKey) 40.0 else 0.0
        val confirmation = if (fact.sourceType == MemorySourceType.USER_CONFIRMED) 30.0 else 0.0
        val matchingWords = words(fact.text).intersect(queryWords).size * 8.0
        val ageDays = ((now - maxOf(fact.updatedAtMillis, fact.createdAtMillis)).coerceAtLeast(0) / 86_400_000L).toDouble()
        val recency = 8.0 / (1.0 + ln(1.0 + ageDays))
        return exactScope + confirmation + matchingWords + fact.confidence.coerceIn(0.0, 1.0) * 20.0 + recency
    }

    private fun words(text: String): Set<String> = Regex("[\\p{L}\\p{N}]{3,}")
        .findAll(text.lowercase())
        .map { it.value }
        .toSet()
}

data class RememberCommand(val text: String, val category: MemoryCategory)

/** Handles explicit user requests without spending an LLM call or guessing what to remember. */
object RememberCommandParser {
    fun parse(input: String): RememberCommand? {
        val text = input.trim().replace(Regex("\\s+"), " ")
            .replace(Regex("[.!?]+$"), "")
        val remembered = Regex("^(?:please\\s+)?remember(?:\\s+that)?\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)
            ?: Regex("^(?:please\\s+)?don't\\s+forget(?:\\s+that)?\\s+(.+)$", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)
            ?: return null
        val fact = remembered.trim().takeIf { it.length in 3..300 } ?: return null
        return RememberCommand(fact, categoryOf(fact))
    }

    private fun categoryOf(text: String): MemoryCategory {
        val lower = text.lowercase()
        return when {
            listOf("prefer", "like", "love", "hate", "reply", "language", "short").any(lower::contains) -> MemoryCategory.PREFERENCE
            listOf("sister", "brother", "mother", "father", "friend", "wife", "husband").any(lower::contains) -> MemoryCategory.RELATIONSHIP
            listOf("call", "send", "remember to", "promised", "promise").any(lower::contains) -> MemoryCategory.COMMITMENT
            listOf("at ", "every ", "tomorrow", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday").any(lower::contains) -> MemoryCategory.SCHEDULE
            listOf("instruction", "always", "never", "don't").any(lower::contains) -> MemoryCategory.INSTRUCTION
            else -> MemoryCategory.GENERAL
        }
    }
}
