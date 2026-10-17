package com.example.engine

import com.example.model.FirestoreMessage
import com.example.model.ToolExecutionRecord
import com.example.model.ToolStatus
import com.example.model.UserPersonalization
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Result of tool output compression following Headroom's Compress-Cache-Retrieve pattern.
 */
data class ToolCompressionResult(
    val refId: String?,
    val outputText: String,
    val originalChars: Int,
    val compressedChars: Int,
    val estimatedOriginalTokens: Int,
    val estimatedCompressedTokens: Int,
    val isCompressed: Boolean
) {
    val estimatedSavedTokens: Int get() = maxOf(0, estimatedOriginalTokens - estimatedCompressedTokens)
    val compressionRatio: Float get() = if (originalChars > 0) compressedChars.toFloat() / originalChars else 1.0f
}

/**
 * Result of conversation history compaction.
 */
data class HistoryCompressionResult(
    val messages: List<FirestoreMessage>,
    val summaryBlock: String?,
    val originalTotalChars: Int,
    val compressedTotalChars: Int,
    val estimatedOriginalTokens: Int,
    val estimatedCompressedTokens: Int,
    val olderMessagesCompacted: Int,
    val isCompressed: Boolean
) {
    val estimatedSavedTokens: Int get() = maxOf(0, estimatedOriginalTokens - estimatedCompressedTokens)
}

/**
 * In-memory cache for storing full uncompressed tool outputs and transcripts,
 * allowing models and users to reference the full payload without consuming prompt tokens.
 */
object ToolOutputCache {
    private const val MAX_ENTRIES = 120
    private val cache = ConcurrentHashMap<String, String>()
    private val insertionOrder = java.util.concurrent.ConcurrentLinkedQueue<String>()

    fun put(content: String): String {
        val refId = "ref_" + UUID.randomUUID().toString().take(8)
        if (cache.size >= MAX_ENTRIES) {
            val oldest = insertionOrder.poll()
            if (oldest != null) {
                cache.remove(oldest)
            }
        }
        cache[refId] = content
        insertionOrder.offer(refId)
        return refId
    }

    fun get(refId: String): String? = cache[refId]

    fun size(): Int = cache.size

    fun clear() {
        cache.clear()
        insertionOrder.clear()
    }
}

/**
 * Token estimator utilizing standard character-to-token heuristics.
 * Clearly designated as an estimate (BPE tokenization requires model-specific vocabularies).
 */
object TokenEstimator {
    private const val CHARS_PER_TOKEN = 4.0

    /**
     * Estimates token count from character count.
     * Based on empirical ratio of ~4 characters per token in standard LLM tokenizers.
     */
    fun estimate(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        return Math.ceil(text.length / CHARS_PER_TOKEN).toInt()
    }

    fun formatSavings(originalChars: Int, compressedChars: Int): String {
        val origTokens = estimate(String(CharArray(originalChars)))
        val compTokens = estimate(String(CharArray(compressedChars)))
        val saved = maxOf(0, origTokens - compTokens)
        val pct = if (originalChars > 0) ((1.0 - (compressedChars.toDouble() / originalChars)) * 100).toInt() else 0
        return "Estimated token reduction: ~$origTokens to ~$compTokens (~$saved saved, $pct% reduction)"
    }
}

/**
 * ContextCompressor implements token and context optimization inspired by:
 * - Headroom: Compress-Cache-Retrieve pattern for large payloads & older history.
 * - Caveman: High signal-to-noise directness eliminating repetitive filler while preserving technical depth.
 */
object ContextCompressor {

    private const val TOOL_COMPRESSION_THRESHOLD_CHARS = 320
    private const val MAX_TOOL_OUTPUT_CHARS = 750
    private const val DEFAULT_RECENT_TURNS_PROTECTED = 6 // Last 3 turns (3 user + 3 assistant)
    private const val MAX_OLDER_HISTORY_CHARS = 2400

    /**
     * Compresses a single tool execution record if it exceeds threshold.
     * Caches the original output in [ToolOutputCache] and returns a high-signal summary
     * with source citations, errors, and data points preserved.
     */
    fun compressToolOutput(
        pluginName: String,
        toolDisplayName: String,
        status: ToolStatus,
        rawOutput: String?,
        maxChars: Int = MAX_TOOL_OUTPUT_CHARS
    ): ToolCompressionResult {
        if (rawOutput.isNullOrBlank()) {
            val txt = "status=$status (no output)"
            return ToolCompressionResult(
                refId = null,
                outputText = txt,
                originalChars = 0,
                compressedChars = txt.length,
                estimatedOriginalTokens = 0,
                estimatedCompressedTokens = TokenEstimator.estimate(txt),
                isCompressed = false
            )
        }

        val originalLength = rawOutput.length
        val origTokens = TokenEstimator.estimate(rawOutput)

        // If short enough, keep original verbatim without caching
        if (originalLength <= TOOL_COMPRESSION_THRESHOLD_CHARS) {
            return ToolCompressionResult(
                refId = null,
                outputText = rawOutput.trim(),
                originalChars = originalLength,
                compressedChars = rawOutput.trim().length,
                estimatedOriginalTokens = origTokens,
                estimatedCompressedTokens = TokenEstimator.estimate(rawOutput.trim()),
                isCompressed = false
            )
        }

        // Cache full uncompressed output
        val refId = ToolOutputCache.put(rawOutput)

        // Intelligently compress based on output patterns
        val compressedBody = when {
            // Case 1: JSON payload
            rawOutput.trim().startsWith("{") || rawOutput.trim().startsWith("[") -> {
                compressJsonOutput(rawOutput, maxChars)
            }
            // Case 2: Stack traces / Log output
            rawOutput.contains("Exception") || rawOutput.contains("Error") || rawOutput.contains("at com.") -> {
                compressLogsAndErrors(rawOutput, maxChars)
            }
            // Case 3: Search / Web / Document results
            rawOutput.contains("http://") || rawOutput.contains("https://") || rawOutput.contains("Title:") -> {
                compressSearchResults(rawOutput, maxChars)
            }
            // Case 4: General multiline / text
            else -> {
                compressGeneralText(rawOutput, maxChars)
            }
        }

        val formattedOutput = buildString {
            appendLine("[Full output cached: $refId | Original size: $originalLength chars]")
            append(compressedBody)
        }

        val compChars = formattedOutput.length
        val compTokens = TokenEstimator.estimate(formattedOutput)

        return ToolCompressionResult(
            refId = refId,
            outputText = formattedOutput,
            originalChars = originalLength,
            compressedChars = compChars,
            estimatedOriginalTokens = origTokens,
            estimatedCompressedTokens = compTokens,
            isCompressed = true
        )
    }

    /**
     * Compresses multiple executed tools into an optimized Gemini context block.
     */
    fun compressToolOutputs(records: List<ToolExecutionRecord>): String {
        if (records.isEmpty()) return ""

        return buildString {
            appendLine("Verified Tool Outputs (Context-Optimized):")
            records.forEachIndexed { idx, rec ->
                val result = compressToolOutput(
                    pluginName = rec.pluginName,
                    toolDisplayName = rec.toolDisplayName,
                    status = rec.status,
                    rawOutput = rec.output ?: rec.errorMessage
                )
                appendLine("[${idx + 1}] ${rec.pluginName}.${rec.toolDisplayName} (status=${rec.status})")
                appendLine(result.outputText)
                if (idx < records.size - 1) appendLine()
            }
        }.trimEnd()
    }

    /**
     * Compresses conversation history safely:
     * - The most recent [protectedRecentCount] messages (e.g. last 6 = 3 full turns) are ALWAYS kept verbatim.
     * - Older messages are compacted into a structured conversation summary preserving user goals, decisions,
     *   technical constraints, and errors.
     * - Original user prompt is NEVER altered.
     */
    fun compressHistory(
        allMessages: List<FirestoreMessage>,
        protectedRecentCount: Int = DEFAULT_RECENT_TURNS_PROTECTED,
        maxOlderChars: Int = MAX_OLDER_HISTORY_CHARS
    ): HistoryCompressionResult {
        // Filter out blank / system error items
        val validMessages = allMessages.filter {
            (it.role == "user" || it.role == "assistant") && it.content.isNotBlank()
        }

        val originalTotalChars = validMessages.sumOf { it.content.length }
        val originalTokens = TokenEstimator.estimate(validMessages.joinToString("\n") { it.content })

        // If history is small enough, keep all messages verbatim
        if (validMessages.size <= protectedRecentCount && originalTotalChars <= maxOlderChars) {
            return HistoryCompressionResult(
                messages = validMessages,
                summaryBlock = null,
                originalTotalChars = originalTotalChars,
                compressedTotalChars = originalTotalChars,
                estimatedOriginalTokens = originalTokens,
                estimatedCompressedTokens = originalTokens,
                olderMessagesCompacted = 0,
                isCompressed = false
            )
        }

        // Split history into older turns and protected recent turns
        val recentCount = minOf(protectedRecentCount, validMessages.size)
        val olderMessages = validMessages.dropLast(recentCount)
        val recentMessages = validMessages.takeLast(recentCount)

        if (olderMessages.isEmpty()) {
            return HistoryCompressionResult(
                messages = recentMessages,
                summaryBlock = null,
                originalTotalChars = originalTotalChars,
                compressedTotalChars = originalTotalChars,
                estimatedOriginalTokens = originalTokens,
                estimatedCompressedTokens = originalTokens,
                olderMessagesCompacted = 0,
                isCompressed = false
            )
        }

        // Compact older messages into an informative Context Summary
        val summaryText = buildOlderTurnsSummary(olderMessages)

        val summaryMessage = FirestoreMessage(
            id = "summary_context_" + UUID.randomUUID().toString().take(6),
            conversationId = olderMessages.firstOrNull()?.conversationId ?: "",
            userId = olderMessages.firstOrNull()?.userId ?: "",
            role = "assistant",
            content = summaryText
        )

        val finalMessages = listOf(summaryMessage) + recentMessages
        val compressedTotalChars = finalMessages.sumOf { it.content.length }
        val compressedTokens = TokenEstimator.estimate(finalMessages.joinToString("\n") { it.content })

        return HistoryCompressionResult(
            messages = finalMessages,
            summaryBlock = summaryText,
            originalTotalChars = originalTotalChars,
            compressedTotalChars = compressedTotalChars,
            estimatedOriginalTokens = originalTokens,
            estimatedCompressedTokens = compressedTokens,
            olderMessagesCompacted = olderMessages.size,
            isCompressed = true
        )
    }

    /**
     * Builds a structured summary of older turns preserving user constraints, technical choices, and resolutions.
     */
    private fun buildOlderTurnsSummary(olderMessages: List<FirestoreMessage>): String {
        val points = mutableListOf<String>()

        for (m in olderMessages) {
            val content = m.content.trim()
            if (m.role == "user") {
                // Extract user intent or question
                val firstLine = content.lineSequence().firstOrNull()?.take(120) ?: ""
                val hasCode = content.contains("```")
                val codeNote = if (hasCode) " [includes code specification]" else ""
                points.add("User goal: \"$firstLine\"$codeNote")
            } else if (m.role == "assistant") {
                // Extract key technical outcomes, decisions, or fixes
                val errorResolved = if (content.contains("error", ignoreCase = true) || content.contains("fix", ignoreCase = true)) {
                    "Resolved technical issue / error."
                } else null

                val codeBlockCount = countOccurrences(content, "```") / 2
                val codeNote = if (codeBlockCount > 0) " Provided $codeBlockCount code implementation(s)." else ""

                val coreSummary = errorResolved ?: content.lineSequence()
                    .filter { it.isNotBlank() && !it.startsWith("```") }
                    .take(2)
                    .joinToString(" ")
                    .take(130)

                if (coreSummary.isNotBlank()) {
                    points.add("Assistant: $coreSummary$codeNote")
                }
            }
        }

        return buildString {
            appendLine("[Context Summary of Earlier Turns (${olderMessages.size} messages compacted)]:")
            points.take(10).forEach { pt ->
                appendLine("• $pt")
            }
            if (points.size > 10) {
                appendLine("• (... and ${points.size - 10} earlier interactions established)")
            }
        }.trimEnd()
    }

    /**
     * Builds Caveman-inspired response style directives.
     * Enforces high signal-to-noise ratio, eliminating repetitive greetings and filler
     * while guaranteeing deep technical explanations when requested or required.
     */
    fun buildResponseStyleDirectives(personalization: UserPersonalization?): String {
        val style = personalization?.responseStyle ?: "Concise & Structured"

        return when {
            style.contains("Direct", ignoreCase = true) || style.contains("Concise", ignoreCase = true) -> {
                """
                Response Style & Directness Guidelines:
                - High Signal-to-Noise: Begin directly with the answer, code, or actionable insight.
                - Eliminate conversational filler, throat-clearing, and generic pleasantries (e.g. do not say "Certainly!", "I hope this helps!", "As an AI...", "Here is what you need").
                - Present solutions cleanly with concise headings, bullet points, and idiomatic code.
                - Technical Completeness: Keep explanations tight, but preserve full code correctness, edge cases, and safety.
                - Override: If the user explicitly asks for detailed explanations, step-by-step walkthroughs, or architectural reasoning, provide full comprehensive depth.
                """.trimIndent()
            }
            style.contains("Detailed", ignoreCase = true) || style.contains("Comprehensive", ignoreCase = true) -> {
                """
                Response Style & Directness Guidelines:
                - In-Depth & Comprehensive: Provide thorough, step-by-step explanations with architectural background, code walkthroughs, and rationale.
                - Still avoid generic throat-clearing fluff; ensure all detail is substantive and valuable.
                """.trimIndent()
            }
            else -> {
                """
                Response Style & Directness Guidelines:
                - Balanced & Professional: Provide clear, natural explanations with a good balance of conciseness and context.
                - Avoid generic repetitive introductions and conversational filler.
                """.trimIndent()
            }
        }
    }

    // --- Compression Sub-routines ---

    private fun compressJsonOutput(json: String, maxChars: Int): String {
        // Strip excessive whitespace and extract key keys/values
        val cleaned = json.replace(Regex("\\s+"), " ")
        if (cleaned.length <= maxChars) return cleaned

        // Extract status, errors, and top items
        val statusMatch = Regex("\"status\"\\s*:\\s*\"?([^\",}]+)\"?").find(json)?.groupValues?.get(1)
        val errorMatch = Regex("\"(error|message)\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(2)
        val countMatch = Regex("\"(count|total)\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(2)

        return buildString {
            append("JSON Summary: ")
            if (statusMatch != null) append("status=$statusMatch ")
            if (errorMatch != null) append("error=\"$errorMatch\" ")
            if (countMatch != null) append("count=$countMatch ")
            appendLine()
            appendLine("Payload Preview: " + cleaned.take(maxChars - 100) + "...")
        }.trimEnd()
    }

    private fun compressLogsAndErrors(logs: String, maxChars: Int): String {
        val lines = logs.lines()
        val errorLines = lines.filter { line ->
            line.contains("Exception", ignoreCase = true) ||
            line.contains("Error", ignoreCase = true) ||
            line.contains("Caused by", ignoreCase = true) ||
            line.contains("FAIL", ignoreCase = true)
        }

        return buildString {
            appendLine("Log & Diagnostics Extraction:")
            errorLines.take(6).forEach { appendLine("• $it") }
            val remaining = lines.size - errorLines.size
            if (remaining > 0) {
                appendLine("[... $remaining standard execution lines omitted ...]")
            }
            // Include last 2 lines for exit code or status
            lines.takeLast(2).filter { it.isNotBlank() && !errorLines.contains(it) }.forEach {
                appendLine("Result: $it")
            }
        }.take(maxChars).trimEnd()
    }

    private fun compressSearchResults(text: String, maxChars: Int): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val urls = mutableListOf<String>()
        val bulletPoints = mutableListOf<String>()

        for (line in lines) {
            if (line.startsWith("http://") || line.startsWith("https://") || line.contains("url:", ignoreCase = true)) {
                urls.add(line)
            } else if (line.startsWith("-") || line.startsWith("*") || line.startsWith("•") || line.contains(":")) {
                bulletPoints.add(line)
            }
        }

        return buildString {
            appendLine("Extracted Findings & Citations:")
            bulletPoints.take(5).forEach { appendLine("• $it") }
            if (urls.isNotEmpty()) {
                appendLine("Sources:")
                urls.take(3).forEach { appendLine("  $it") }
            }
            if (bulletPoints.isEmpty() && urls.isEmpty()) {
                append(text.take(maxChars))
            }
        }.take(maxChars).trimEnd()
    }

    private fun compressGeneralText(text: String, maxChars: Int): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size <= 4) {
            return text.take(maxChars)
        }

        // Keep first few lines, key bullet points, and last line
        val head = lines.take(3).joinToString("\n")
        val tail = lines.takeLast(2).joinToString("\n")
        val omitted = lines.size - 5

        return if (omitted > 0) {
            "$head\n[... $omitted lines condensed ...]\n$tail"
        } else {
            text.take(maxChars)
        }
    }

    private fun countOccurrences(str: String, sub: String): Int {
        var count = 0
        var idx = 0
        while (str.indexOf(sub, idx).also { idx = it } != -1) {
            count++
            idx += sub.length
        }
        return count
    }
}
