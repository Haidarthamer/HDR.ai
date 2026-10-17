package com.example

import com.example.engine.ContextCompressor
import com.example.engine.HistoryCompressionResult
import com.example.engine.TokenEstimator
import com.example.engine.ToolCompressionResult
import com.example.engine.ToolOutputCache
import com.example.model.FirestoreMessage
import com.example.model.ToolStatus
import com.example.model.UserPersonalization
import com.example.repository.CloudDataRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContextCompressionUnitTest {

    private lateinit var repository: CloudDataRepository

    @Before
    fun setup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId("1:1234567890:android:abcdef")
                .setProjectId("nexus-ai-project")
                .setApiKey("AQ.Ab8RN6JgmIqahB4KbojDbrCZ2rV8A62M1lmJAkFwMd4w_Hfo3g")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(context, options)
        }
        repository = CloudDataRepository(context)
        ToolOutputCache.clear()
    }

    // --- Requirement 1 & 3: Compress-Cache-Retrieve & Tool Output Optimization ---

    @Test
    fun testToolOutputCompression_CompressesLargeOutputAndCachesOriginal() {
        val largeSearchOutput = buildString {
            appendLine("Search results for 'NexusAI Kotlin Coroutines Flow architecture':")
            appendLine("Title: Kotlin Flows in Android Jetpack Compose")
            appendLine("URL: https://developer.android.com/kotlin/flow")
            appendLine("Snippet: StateFlow and SharedFlow are reactive primitives designed for Android UI state.")
            repeat(30) { i ->
                appendLine("Item #$i: Supplemental log data or redundant index node with metadata hash 0xABCD$i")
            }
        }

        assertTrue("Original output must be sufficiently large (> 320 chars)", largeSearchOutput.length > 320)

        val result: ToolCompressionResult = ContextCompressor.compressToolOutput(
            pluginName = "WebSearch",
            toolDisplayName = "SearchQuery",
            status = ToolStatus.SUCCESS,
            rawOutput = largeSearchOutput
        )

        // 1. Verify it was compressed
        assertTrue(result.isCompressed)
        assertNotNull(result.refId)
        assertTrue(result.compressedChars < result.originalChars)

        // 2. Verify savings measured (distinguishing estimates)
        assertTrue("Estimated saved tokens must be > 0", result.estimatedSavedTokens > 0)
        assertTrue("Original estimated tokens must exceed compressed estimated tokens",
            result.estimatedOriginalTokens > result.estimatedCompressedTokens)

        // 3. Verify Cache-Retrieve pattern works
        val retrievedOriginal = ToolOutputCache.get(result.refId!!)
        assertEquals("Cache retrieval must return exact original uncompressed payload", largeSearchOutput, retrievedOriginal)

        // 4. Verify essential information preserved (URLs & title)
        assertTrue("Compressed output must preserve source URL",
            result.outputText.contains("https://developer.android.com/kotlin/flow"))
    }

    @Test
    fun testToolOutputCompression_PreservesShortOutputVerbatim() {
        val shortOutput = "Calculated Fibonacci(10) = 55. Execution time: 1.2ms."
        val result = ContextCompressor.compressToolOutput(
            pluginName = "Calculator",
            toolDisplayName = "Compute",
            status = ToolStatus.SUCCESS,
            rawOutput = shortOutput
        )

        assertFalse("Short output should not trigger compression", result.isCompressed)
        assertNull("Short output does not allocate cache ref", result.refId)
        assertEquals(shortOutput, result.outputText)
        assertEquals(shortOutput.length, result.originalChars)
        assertEquals(shortOutput.length, result.compressedChars)
        assertEquals(0, result.estimatedSavedTokens)
    }

    @Test
    fun testToolOutputCompression_PreservesErrorLinesAndExceptions() {
        val errorLogs = buildString {
            appendLine("Starting container deployment...")
            repeat(15) { i -> appendLine("INFO [2026-10-12 12:00:$i] Normal background heartbeat ping OK") }
            appendLine("FATAL: java.lang.IllegalStateException: Missing required Firestore database credentials")
            appendLine("Caused by: com.google.firebase.FirebaseException: Quota limit reached on API endpoint")
            repeat(15) { i -> appendLine("DEBUG [2026-10-12 12:01:$i] Trace cleanup worker thread terminated") }
            appendLine("Process exited with code 1")
        }

        val result = ContextCompressor.compressToolOutput(
            pluginName = "BashRunner",
            toolDisplayName = "DeployApp",
            status = ToolStatus.FAILED,
            rawOutput = errorLogs
        )

        assertTrue(result.isCompressed)
        assertTrue("Compressed logs must preserve the fatal exception",
            result.outputText.contains("java.lang.IllegalStateException"))
        assertTrue("Compressed logs must preserve the root cause exception",
            result.outputText.contains("FirebaseException: Quota limit reached"))
        assertTrue("Cache must store full trace for retrieval",
            ToolOutputCache.get(result.refId!!)!!.contains("Trace cleanup worker"))
    }

    @Test
    fun testToolOutputCompression_JsonPayloadCompaction() {
        val jsonPayload = """
            {
                "status": "success",
                "count": 150,
                "data": [
                    {"id": 1, "name": "Item Alpha", "tags": ["kotlin", "mobile"]},
                    {"id": 2, "name": "Item Beta", "tags": ["jetpack", "compose"]},
                    {"id": 3, "name": "Item Gamma", "tags": ["firebase", "cloud"]},
                    {"id": 4, "name": "Item Delta", "tags": ["grpc", "network"]},
                    {"id": 5, "name": "Item Epsilon", "tags": ["security", "auth"]},
                    {"id": 6, "name": "Item Zeta", "tags": ["analytics", "metrics"]}
                ],
                "execution_ms": 42
            }
        """.trimIndent().repeat(3) // Make large

        val result = ContextCompressor.compressToolOutput(
            pluginName = "ApiFetcher",
            toolDisplayName = "GetRecords",
            status = ToolStatus.SUCCESS,
            rawOutput = jsonPayload
        )

        assertTrue(result.isCompressed)
        assertTrue("JSON summary must extract status", result.outputText.contains("status=success"))
        assertTrue("JSON summary must extract count", result.outputText.contains("count=150"))
    }

    // --- Requirement 2: Safe Conversation History Management ---

    @Test
    fun testHistoryCompaction_ProtectsRecentTurnsVerbatim() {
        val messages = mutableListOf<FirestoreMessage>()
        // 10 messages = 5 turns
        for (i in 1..10) {
            val role = if (i % 2 != 0) "user" else "assistant"
            messages.add(
                FirestoreMessage(
                    id = "msg_$i",
                    conversationId = "conv_1",
                    role = role,
                    content = if (role == "user") "Turn $i: User prompt text with specific question #$i"
                              else "Turn $i: Assistant answer text with technical solution #$i"
                )
            )
        }

        val result: HistoryCompressionResult = ContextCompressor.compressHistory(
            allMessages = messages,
            protectedRecentCount = 6 // Protect last 6 messages (last 3 turns)
        )

        assertTrue(result.isCompressed)
        assertEquals(4, result.olderMessagesCompacted) // First 4 messages compacted

        // The final messages list should have 1 summary message + 6 protected recent messages = 7 messages
        assertEquals(7, result.messages.size)

        // Verify recent 6 messages are 100% verbatim identical to original tail
        val originalRecent6 = messages.takeLast(6)
        val finalRecent6 = result.messages.takeLast(6)

        for (idx in 0 until 6) {
            assertEquals("Recent message ID must match", originalRecent6[idx].id, finalRecent6[idx].id)
            assertEquals("Recent message content must remain completely verbatim",
                originalRecent6[idx].content, finalRecent6[idx].content)
        }

        // Verify summary block captures earlier conversation context
        assertNotNull(result.summaryBlock)
        assertTrue(result.summaryBlock!!.contains("Turn 1"))
        assertTrue(result.summaryBlock!!.contains("Turn 3"))
    }

    @Test
    fun testHistoryCompaction_LeavesShortHistoryIntact() {
        val messages = listOf(
            FirestoreMessage(id = "1", role = "user", content = "Hello!"),
            FirestoreMessage(id = "2", role = "assistant", content = "Hi! How can I assist you?")
        )

        val result = ContextCompressor.compressHistory(
            allMessages = messages,
            protectedRecentCount = 6
        )

        assertFalse("Short history under protected limit should not be compressed", result.isCompressed)
        assertEquals(2, result.messages.size)
        assertEquals("Hello!", result.messages[0].content)
        assertEquals("Hi! How can I assist you?", result.messages[1].content)
        assertEquals(0, result.olderMessagesCompacted)
    }

    @Test
    fun testHistoryCompaction_PreservesCodeBlocksInSummary() {
        val messages = listOf(
            FirestoreMessage(id = "1", role = "user", content = "Please implement Room DB schema with ```@Entity data class User(val id: String)```"),
            FirestoreMessage(id = "2", role = "assistant", content = "Here is the Room entity:\n```@Entity\ndata class User(val id: String)\n```"),
            FirestoreMessage(id = "3", role = "user", content = "Turn 2 question"),
            FirestoreMessage(id = "4", role = "assistant", content = "Turn 2 answer"),
            FirestoreMessage(id = "5", role = "user", content = "Turn 3 question"),
            FirestoreMessage(id = "6", role = "assistant", content = "Turn 3 answer"),
            FirestoreMessage(id = "7", role = "user", content = "Turn 4 question"),
            FirestoreMessage(id = "8", role = "assistant", content = "Turn 4 answer")
        )

        val result = ContextCompressor.compressHistory(
            allMessages = messages,
            protectedRecentCount = 4
        )

        assertTrue(result.isCompressed)
        // Summary must note code specification
        assertNotNull(result.summaryBlock)
        assertTrue("Summary must preserve reference to code specification in earlier turn",
            result.summaryBlock!!.contains("code specification") || result.summaryBlock!!.contains("code implementation"))
    }

    // --- Requirement 4 & 5: Caveman Directness & Settings Persistence ---

    @Test
    fun testCavemanDirectness_GeneratesDirectGuidelinesForConciseStyle() {
        val concisePersonalization = UserPersonalization(
            responseStyle = "Concise & Direct"
        )
        val directives = ContextCompressor.buildResponseStyleDirectives(concisePersonalization)

        assertTrue(directives.contains("High Signal-to-Noise"))
        assertTrue(directives.contains("Eliminate conversational filler"))
        assertTrue(directives.contains("Override"))
    }

    @Test
    fun testCavemanDirectness_GeneratesDetailedGuidelinesForDetailedStyle() {
        val detailedPersonalization = UserPersonalization(
            responseStyle = "Detailed & Comprehensive"
        )
        val directives = ContextCompressor.buildResponseStyleDirectives(detailedPersonalization)

        assertTrue(directives.contains("In-Depth & Comprehensive"))
        assertTrue(directives.contains("step-by-step"))
    }

    @Test
    fun testPersonalizationPersistence_PersistsContextCompressionAndStyle() = runBlocking {
        // Test in Guest Mode
        val customSettings = UserPersonalization(
            assistantName = "NexusPro",
            responseStyle = "Concise & Direct",
            contextCompressionEnabled = true
        )
        repository.savePersonalization(customSettings)

        val loaded = repository.loadPersonalization()
        assertEquals("Concise & Direct", loaded.responseStyle)
        assertTrue(loaded.contextCompressionEnabled)

        // Update toggle to false
        repository.savePersonalization(customSettings.copy(contextCompressionEnabled = false))
        val loadedFalse = repository.loadPersonalization()
        assertFalse(loadedFalse.contextCompressionEnabled)
    }

    // --- Requirement 6: Token Estimation & Measurement ---

    @Test
    fun testTokenEstimator_DistinguishesEstimatesFromMeasurements() {
        assertEquals(0, TokenEstimator.estimate(null))
        assertEquals(0, TokenEstimator.estimate(""))

        val text100 = "a".repeat(100)
        val estTokens = TokenEstimator.estimate(text100)
        assertEquals("100 chars should be estimated at ~25 tokens", 25, estTokens)

        val savingsString = TokenEstimator.formatSavings(originalChars = 400, compressedChars = 100)
        assertTrue(savingsString.contains("Estimated token reduction"))
        assertTrue(savingsString.contains("75% reduction"))
    }
}
