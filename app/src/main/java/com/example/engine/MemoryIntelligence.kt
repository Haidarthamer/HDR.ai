package com.example.engine

import com.example.model.FirestoreMemory
import java.util.Locale

data class MemoryCandidate(
    val key: String,
    val value: String,
    val category: String, // "PREFERENCE", "PROJECT_FACT", "TECH_STACK", "FACT"
    val projectId: String? = null,
    val confidence: Float = 0.9f
)

/**
 * Intelligent Long-Term Memory Scoring & Extraction Engine
 * Inspired by claude-mem, providing lightweight, relevance-based retrieval
 * and heuristic extraction without unbounded prompt flooding or extra API calls.
 */
object MemoryIntelligence {

    private val STOP_WORDS = setOf(
        "a", "about", "above", "after", "again", "all", "am", "an", "and", "any", "are", "as", "at",
        "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can",
        "did", "do", "does", "doing", "don", "down", "during", "each", "few", "for", "from",
        "further", "had", "has", "have", "having", "he", "her", "here", "hers", "herself", "him",
        "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself", "just",
        "me", "more", "most", "my", "myself", "no", "nor", "not", "now", "of", "off", "on", "once",
        "only", "or", "other", "our", "ours", "ourselves", "out", "over", "own", "s", "same", "she",
        "should", "so", "some", "such", "t", "than", "that", "the", "their", "theirs", "them",
        "themselves", "then", "there", "these", "they", "this", "those", "through", "to", "too",
        "under", "until", "up", "very", "was", "we", "were", "what", "when", "where", "which", "while",
        "who", "whom", "why", "will", "with", "you", "your", "yours", "yourself", "yourselves"
    )

    /**
     * Scores and selects only the top relevant memories for the current prompt and active project.
     */
    fun selectRelevantMemories(
        query: String,
        allMemories: List<FirestoreMemory>,
        activeProjectId: String?,
        limit: Int = 4
    ): List<FirestoreMemory> {
        if (allMemories.isEmpty()) return emptyList()

        val queryTokens = tokenize(query)
        val scoredMemories = allMemories.mapNotNull { memory ->
            val score = scoreMemory(memory, queryTokens, query, activeProjectId)
            if (score > 0.0f) Pair(memory, score) else null
        }

        return scoredMemories
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun scoreMemory(
        memory: FirestoreMemory,
        queryTokens: Set<String>,
        rawQuery: String,
        activeProjectId: String?
    ): Float {
        // Project Context Alignment
        if (!memory.projectId.isNullOrBlank()) {
            if (memory.projectId != activeProjectId) {
                // Belongs to a different project: strictly do not inject into this context
                return -10.0f
            }
        }

        var score = 0.0f

        // Project Match Bonus
        if (!activeProjectId.isNullOrBlank() && memory.projectId == activeProjectId) {
            score += 2.0f
        }

        val keyLower = memory.key.lowercase(Locale.ROOT)
        val valueLower = memory.value.lowercase(Locale.ROOT)
        val categoryLower = memory.category.lowercase(Locale.ROOT)

        val memoryTokens = (tokenize(keyLower) + tokenize(valueLower) + tokenize(categoryLower))

        // Direct Substring Match
        val rawQueryLower = rawQuery.lowercase(Locale.ROOT)
        if (rawQueryLower.contains(keyLower) && keyLower.length > 2) {
            score += 4.0f
        }
        if (rawQueryLower.contains(valueLower) && valueLower.length > 2) {
            score += 3.0f
        }

        // Token Overlap Scoring
        for (qToken in queryTokens) {
            if (memoryTokens.contains(qToken)) {
                score += if (keyLower.contains(qToken)) 2.0f else 1.0f
            } else if (qToken.length > 3 && (keyLower.contains(qToken) || valueLower.contains(qToken))) {
                score += 0.8f
            }
        }

        // Category Baseline Weight
        if (memory.category == "PREFERENCE" || memory.category == "TECH_STACK") {
            // High-value global preferences stay slightly warm
            if (score > 0.0f) score += 0.5f
        }

        // Freshness Bonus (recent memories get slight tie-breaker advantage)
        val now = System.currentTimeMillis()
        val memTime = memory.updatedAt?.toDate()?.time ?: memory.createdAt?.toDate()?.time ?: 0L
        if (memTime > 0) {
            val ageHours = (now - memTime) / (1000 * 3600)
            if (ageHours < 24) score += 0.3f
            else if (ageHours < 168) score += 0.1f
        }

        return score
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase(Locale.ROOT)
            .split(Regex("[^a-zA-Z0-9_+#.-]+"))
            .filter { it.length > 1 && !STOP_WORDS.contains(it) }
            .toSet()
    }

    /**
     * Extracts a memory candidate from a user turn without triggering an expensive LLM call.
     * Evaluates explicit instructions, preferences, and project assertions while
     * strictly rejecting questions, transient task instructions, and greetings.
     */
    fun extractCandidate(
        userText: String,
        activeProjectId: String?,
        conversationId: String?
    ): MemoryCandidate? {
        val trimmed = userText.trim()
        if (trimmed.length < 8 || trimmed.length > 300) return null

        // 1. Strictly Reject Questions & Routine Prompts
        if (isTransientOrQuestion(trimmed)) return null

        val lower = trimmed.lowercase(Locale.ROOT)

        // 2. Explicit User Instructions ("Remember that...", "Note that I prefer...", etc.)
        val explicitPatterns = listOf(
            Regex("^(?:please\\s+)?remember(?:\\s+that)?[:\\s]+(.+)", RegexOption.IGNORE_CASE),
            Regex("^(?:don't|do\\s+not)\\s+forget(?:\\s+that)?[:\\s]+(.+)", RegexOption.IGNORE_CASE),
            Regex("^(?:please\\s+)?note(?:\\s+that)?[:\\s]+(.+)", RegexOption.IGNORE_CASE),
            Regex("^(?:save|keep)(?:\\s+this)?\\s+preference[:\\s]+(.+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in explicitPatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val fact = match.groupValues[1].trim().trimEnd('.', '!')
                if (fact.isNotBlank()) {
                    val key = deriveKeyFromFact(fact)
                    val category = if (!activeProjectId.isNullOrBlank()) "PROJECT_FACT" else "PREFERENCE"
                    return MemoryCandidate(
                        key = key,
                        value = fact,
                        category = category,
                        projectId = activeProjectId,
                        confidence = 0.95f
                    )
                }
            }
        }

        // 3. Declarative Personal / Technical Preferences
        if (lower.startsWith("i prefer ") || lower.startsWith("my preference is ") || lower.startsWith("i always use ")) {
            val fact = trimmed.trimEnd('.', '!')
            val key = deriveKeyFromFact(fact)
            return MemoryCandidate(
                key = key,
                value = fact,
                category = "PREFERENCE",
                projectId = activeProjectId,
                confidence = 0.9f
            )
        }

        if (lower.startsWith("my tech stack is ") || lower.startsWith("our stack is ") || lower.startsWith("our architecture is ")) {
            val fact = trimmed.trimEnd('.', '!')
            val key = if (lower.contains("architecture")) "architecture" else "tech_stack"
            return MemoryCandidate(
                key = key,
                value = fact,
                category = "TECH_STACK",
                projectId = activeProjectId,
                confidence = 0.9f
            )
        }

        if (lower.startsWith("our database is ") || lower.startsWith("we use ") && lower.contains(" database")) {
            val fact = trimmed.trimEnd('.', '!')
            return MemoryCandidate(
                key = "database",
                value = fact,
                category = if (!activeProjectId.isNullOrBlank()) "PROJECT_FACT" else "TECH_STACK",
                projectId = activeProjectId,
                confidence = 0.9f
            )
        }

        if (lower.startsWith("always respond in ") || lower.startsWith("always write code in ") || lower.startsWith("never use ")) {
            val fact = trimmed.trimEnd('.', '!')
            return MemoryCandidate(
                key = if (lower.contains("never")) "constraint" else "coding_style",
                value = fact,
                category = "PREFERENCE",
                projectId = activeProjectId,
                confidence = 0.9f
            )
        }

        return null
    }

    private fun isTransientOrQuestion(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()

        if (text.endsWith("?") || text.contains("?\n")) return true
        if (text.startsWith("```") || text.contains("class ") || text.contains("fun ") || text.contains("def ")) return true

        val questionPrefixes = listOf(
            "what ", "what's ", "how ", "how's ", "why ", "when ", "where ", "who ",
            "can you", "could you", "would you", "will you", "tell me", "show me",
            "explain ", "summarize ", "write a ", "create a ", "build a ", "fix ",
            "debug ", "calculate ", "solve ", "search for ", "find "
        )
        if (questionPrefixes.any { lower.startsWith(it) }) return true

        val conversational = setOf("hi", "hello", "hey", "thanks", "thank you", "ok", "okay", "got it", "good morning", "bye")
        if (conversational.contains(lower)) return true

        return false
    }

    private fun deriveKeyFromFact(fact: String): String {
        val lower = fact.lowercase(Locale.ROOT)
        return when {
            lower.contains("kotlin") || lower.contains("java") || lower.contains("python") || lower.contains("typescript") -> "preferred_language"
            lower.contains("compose") || lower.contains("react") || lower.contains("flutter") -> "ui_framework"
            lower.contains("firestore") || lower.contains("postgres") || lower.contains("mongo") || lower.contains("sqlite") || lower.contains("database") -> "database"
            lower.contains("dark mode") || lower.contains("light mode") || lower.contains("theme") -> "preferred_theme"
            lower.contains("test") || lower.contains("robolectric") || lower.contains("junit") -> "testing_framework"
            lower.contains("architecture") || lower.contains("mvvm") || lower.contains("clean") -> "architecture"
            lower.contains("brief") || lower.contains("concise") || lower.contains("detailed") -> "response_style"
            else -> {
                val words = tokenize(fact).take(3)
                if (words.isNotEmpty()) words.joinToString("_") else "user_preference"
            }
        }
    }
}
