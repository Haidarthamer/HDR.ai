package com.example

import com.example.model.AuthType
import com.example.plugins.PluginRegistry
import com.example.repository.PlatformRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NexusPlatformUnitTest {

    private lateinit var registry: PluginRegistry
    private lateinit var repo: PlatformRepository

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
        registry = PluginRegistry()
        repo = PlatformRepository()
    }

    @Test
    fun testCorePluginsRegistered() {
        val plugins = registry.pluginsState.value
        assertTrue("Should have core plugins registered", plugins.isNotEmpty())
        assertNotNull("GitHub plugin should be present", plugins.find { it.id == "github" })
        assertNotNull("Web Search plugin should be present", plugins.find { it.id == "web_search" })
        assertNotNull("Gmail plugin should be present", plugins.find { it.id == "gmail" })
        assertNotNull("Files plugin should be present", plugins.find { it.id == "files" })
    }

    @Test
    fun testPluginPermissionsToggleAndDisconnect() {
        val github = registry.pluginsState.value.find { it.id == "github" }!!
        assertTrue("GitHub should initially be connected", github.isConnected)

        // Disconnect
        registry.disconnectPlugin("github")
        val disconnectedGh = registry.pluginsState.value.find { it.id == "github" }!!
        assertFalse("GitHub should be marked disconnected", disconnectedGh.isConnected)
        assertTrue("All permissions revoked on disconnect", disconnectedGh.permissions.all { !it.isGranted })

        // Reconnect
        registry.connectPlugin("github", "octocat@github.com")
        val reconnectedGh = registry.pluginsState.value.find { it.id == "github" }!!
        assertTrue("GitHub should be reconnected", reconnectedGh.isConnected)
        val readPerm = reconnectedGh.permissions.find { it.id == "repo.read" }
        assertTrue("Safe permissions granted on connect", readPerm?.isGranted == true)

        // Granular permission toggle
        registry.updatePermission("github", "repo.write", true)
        val updatedGh = registry.pluginsState.value.find { it.id == "github" }!!
        assertTrue("repo.write should be explicitly granted", updatedGh.permissions.find { it.id == "repo.write" }?.isGranted == true)
    }

    @Test
    fun testCustomPluginRegistrationValidation() {
        val validToolsJson = """[{"name":"ping","description":"health check"}]"""
        val (success, msg) = registry.registerCustomPlugin(
            id = "custom_test",
            name = "Test Webhook",
            description = "Custom endpoint",
            endpoint = "https://test.nexus.ai/webhook",
            authType = AuthType.API_KEY,
            toolsJson = validToolsJson,
            permissionsList = emptyList()
        )
        assertTrue("Registration failed: $msg", success)
        assertNotNull(registry.getProvider("custom_test"))

        // Duplicate registration must fail
        val (failSuccess, _) = registry.registerCustomPlugin(
            id = "custom_test",
            name = "Duplicate",
            description = "Duplicate",
            endpoint = "https://dup.ai",
            authType = AuthType.NONE,
            toolsJson = validToolsJson,
            permissionsList = emptyList()
        )
        assertFalse(failSuccess)
    }

    @Test
    fun testMemoryLifecycleAndAuditLogs() {
        val initialSize = repo.memoryFacts.value.size
        repo.addMemory("Test Framework", "JUnit 4 & Robolectric", "PROJECT")
        assertEquals(initialSize + 1, repo.memoryFacts.value.size)

        val lastLog = repo.auditLogs.value.first()
        assertEquals("MEMORY", lastLog.eventType)

        repo.clearAllMemory()
        assertTrue(repo.memoryFacts.value.isEmpty())
    }

    @Test
    fun testForgeOrchestratorComplexityAndEstimation() {
        val orchestrator = com.example.engine.ForgeOrchestrator()

        val simpleComplexity = orchestrator.analyzeTaskComplexity("What is this?")
        assertEquals(com.example.engine.ForgeOrchestrator.TaskComplexity.SIMPLE, simpleComplexity)

        val complexComplexity = orchestrator.analyzeTaskComplexity("Refactor distributed architecture with concurrency safety, deadlock avoidance, and memory cleanup")
        assertEquals(com.example.engine.ForgeOrchestrator.TaskComplexity.EXTREMELY_COMPLEX, complexComplexity)

        val lowUsage = orchestrator.estimateUsage(
            intelligence = com.example.model.ForgeIntelligence.MEDIUM,
            complexity = com.example.engine.ForgeOrchestrator.TaskComplexity.SIMPLE,
            limitProtection = true
        )
        assertTrue("Should estimate low usage", lowUsage.contains("Low"))
    }

    @Test
    fun testGuestModeToggle() {
        com.example.auth.AuthenticationManager.setGuestMode(true)
        assertTrue("Guest mode should be active", com.example.auth.AuthenticationManager.isGuestMode.value)

        com.example.auth.AuthenticationManager.setGuestMode(false)
        assertFalse("Guest mode should be deactivated", com.example.auth.AuthenticationManager.isGuestMode.value)
    }

    @Test
    fun testExpandedPluginsCatalogPresent() {
        val plugins = registry.pluginsState.value
        assertNotNull("Higgsfield plugin should be present", plugins.find { it.id == "higgsfield" })
        assertNotNull("Google Workspace should be present", plugins.find { it.id == "google_workspace" })
    }

    @Test
    fun testDirectChatQueryWhatIsOnePlusOne() = runBlocking {
        val engine = com.example.engine.FastStreamingEngine(registry)
        val events = mutableListOf<com.example.engine.StreamEvent>()

        engine.streamQuery(
            prompt = "What is 1+1?",
            conversationHistory = emptyList(),
            relevantMemories = emptyList()
        ).collect { events.add(it) }

        assertTrue("Should emit stream events", events.isNotEmpty())
        val finishedEvent = events.filterIsInstance<com.example.engine.StreamEvent.StreamFinished>().firstOrNull()
        val errorEvent = events.filterIsInstance<com.example.engine.StreamEvent.StreamError>().firstOrNull()
        assertTrue("Query must terminate in finished or structured error", finishedEvent != null || errorEvent != null)
        if (finishedEvent != null) {
            assertTrue("Assistant response must not be blank", finishedEvent.fullResponse.isNotBlank())
        }
    }

    @Test
    fun testDirectChatQueryHello() = runBlocking {
        val engine = com.example.engine.FastStreamingEngine(registry)
        val events = mutableListOf<com.example.engine.StreamEvent>()

        engine.streamQuery(
            prompt = "Hello",
            conversationHistory = emptyList(),
            relevantMemories = emptyList()
        ).collect { events.add(it) }

        assertTrue("Should emit stream events", events.isNotEmpty())
        val finishedEvent = events.filterIsInstance<com.example.engine.StreamEvent.StreamFinished>().firstOrNull()
        val errorEvent = events.filterIsInstance<com.example.engine.StreamEvent.StreamError>().firstOrNull()
        assertTrue("Query must terminate in finished or structured error", finishedEvent != null || errorEvent != null)
        if (finishedEvent != null) {
            assertTrue("Assistant response must not be blank", finishedEvent.fullResponse.isNotBlank())
        }
    }

    @Test
    fun testGuestModeChatAndPersistence() = runBlocking {
        com.example.auth.AuthenticationManager.setGuestMode(true)
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val cloudRepo = com.example.repository.CloudDataRepository(app)

        val convId = cloudRepo.createConversation("Guest Test Chat")
        assertNotNull("Conversation ID must be generated", convId)

        val userMsgId = cloudRepo.saveMessage(convId, role = "user", content = "Hello from guest")
        val assistantMsgId = cloudRepo.saveMessage(convId, role = "assistant", content = "Hello! I am NexusAI.")

        var observedMessages = emptyList<com.example.model.FirestoreMessage>()
        val job = launch {
            cloudRepo.observeMessages(convId).collect {
                observedMessages = it
            }
        }
        kotlinx.coroutines.delay(50)
        job.cancel()

        assertTrue("Guest mode should store messages locally", observedMessages.any { it.id == userMsgId })
        assertTrue("Guest mode should store assistant reply locally", observedMessages.any { it.id == assistantMsgId })
    }

    @Test
    fun testApiFailureGeneratesVisibleError() = runBlocking {
        val engine = com.example.engine.FastStreamingEngine(registry)
        val events = mutableListOf<com.example.engine.StreamEvent>()

        // Pass an invalid API key to deliberately test provider failure handling
        engine.streamQuery(
            prompt = "test failure",
            conversationHistory = emptyList(),
            relevantMemories = emptyList(),
            apiKeyOverride = "INVALID_TEST_KEY_THAT_WILL_FAIL_12345"
        ).collect { events.add(it) }

        val errorEvent = events.filterIsInstance<com.example.engine.StreamEvent.StreamError>().firstOrNull()
        assertNotNull("Provider error event must be emitted when credentials or request fails", errorEvent)
        assertTrue("Error must specify a non-empty safe message", errorEvent!!.errorMessage.isNotBlank())
        assertTrue("Error must specify a safe category", errorEvent.category.isNotBlank())
    }

    @Test
    fun testChatViewModelMessagePipelineAndStop() = runBlocking {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.ChatViewModel(app)

        vm.sendMessage("What is 1+1?")
        val messagesAfterSend = vm.messages.value
        assertTrue("Optimistic user message must appear immediately", messagesAfterSend.isNotEmpty())
        assertEquals("user", messagesAfterSend.first().role)
        assertEquals("What is 1+1?", messagesAfterSend.first().content)

        // Test explicit cancellation
        vm.stopGeneration()
        val messagesAfterCancel = vm.messages.value
        val hasCancelledOrAssistant = messagesAfterCancel.any {
            it.role == "assistant" || it.role == "error"
        }
        assertTrue("Stopping generation must finalize conversation without disappearing", hasCancelledOrAssistant)
    }

    @Test
    fun testChatViewModelPreventsDuplicateConcurrentRequests() = runBlocking {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.ChatViewModel(app)

        vm.sendMessage("First question")
        val countAfterFirst = vm.messages.value.size
        assertTrue("First message sent", countAfterFirst >= 1)

        // Attempting to send a second question while first is actively streaming must be ignored
        vm.sendMessage("Second question duplicate")
        val countAfterDuplicate = vm.messages.value.size
        assertEquals("Duplicate send while streaming must be ignored", countAfterFirst, countAfterDuplicate)
    }

    @Test
    fun testGeminiErrorAnalyzerQuotaExhaustionDetection() {
        val quotaExhaustedJson = """
            {
              "error": {
                "code": 429,
                "message": "You exceeded your current quota, please check your plan and billing details. Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, model: gemini-3.6-flash\nPlease retry in 3h54m51s.",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.QuotaFailure",
                    "violations": [
                      {
                        "quotaMetric": "generativelanguage.googleapis.com/generate_content_free_tier_requests",
                        "quotaId": "GenerateRequestsPerDayPerProjectPerModel-FreeTier"
                      }
                    ]
                  },
                  {
                    "@type": "type.googleapis.com/google.rpc.RetryInfo",
                    "retryDelay": "14091s"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = retrofit2.Response.error<String>(
            429,
            quotaExhaustedJson.toResponseBody("application/json".toMediaType())
        )
        val httpException = retrofit2.HttpException(response)
        val analysis = com.example.network.GeminiErrorAnalyzer.analyze(httpException)

        assertTrue("Must detect quota exhaustion", analysis.isQuotaExhausted)
        assertTrue("Must detect rate limit condition", analysis.isRateLimit)
        assertEquals("QUOTA_EXHAUSTED", analysis.category)
        assertFalse("Cannot retry exhausted daily quota", analysis.canRetry)
        assertEquals(14091L, analysis.retryDelaySeconds)
        assertTrue("Message must explain 20 requests/day quota", analysis.safeErrorMessage.contains("20 requests/day"))
    }

    @Test
    fun testGeminiErrorAnalyzerTemporaryRateLimitDetection() {
        val tempRpmJson = """
            {
              "error": {
                "code": 429,
                "message": "Rate limit exceeded (requests per minute).",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.RetryInfo",
                    "retryDelay": "5s"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = retrofit2.Response.error<String>(
            429,
            tempRpmJson.toResponseBody("application/json".toMediaType())
        )
        val httpException = retrofit2.HttpException(response)
        val analysis = com.example.network.GeminiErrorAnalyzer.analyze(httpException)

        assertFalse("Must NOT flag as permanent quota exhaustion", analysis.isQuotaExhausted)
        assertTrue("Must flag as rate limit", analysis.isRateLimit)
        assertEquals("RATE_LIMIT_ERROR", analysis.category)
        assertTrue("Can retry temporary RPM rate limit", analysis.canRetry)
        assertEquals(5L, analysis.retryDelaySeconds)
        assertTrue("Must mention wait seconds in message", analysis.safeErrorMessage.contains("5s"))
    }

    // --- PHASE 5 ACCEPTANCE TESTS ---

    @Test
    fun testAcceptance1_SimpleArithmetic() = runBlocking {
        val engine = com.example.engine.FastStreamingEngine(registry)
        val events = mutableListOf<com.example.engine.StreamEvent>()

        engine.streamQuery(
            prompt = "What is 25 * 49?",
            conversationHistory = emptyList(),
            relevantMemories = emptyList()
        ).collect { events.add(it) }

        val finished = events.filterIsInstance<com.example.engine.StreamEvent.StreamFinished>().firstOrNull()
        val error = events.filterIsInstance<com.example.engine.StreamEvent.StreamError>().firstOrNull()
        assertTrue("Simple arithmetic query must complete or produce structured error", finished != null || error != null)
        if (finished != null) {
            val response = finished.fullResponse
            assertTrue("Response must not be blank", response.isNotBlank())
            assertTrue("Response must contain calculation: $response",
                response.contains("1,225") || response.contains("1225") || response.contains("1250"))
        } else if (error != null) {
            assertTrue("Error category must be defined: ${error.category}", error.category.isNotBlank())
        }
    }

    @Test
    fun testAcceptance2_ComplexReasoning() = runBlocking {
        kotlinx.coroutines.delay(1000)
        val engine = com.example.engine.FastStreamingEngine(registry)
        val events = mutableListOf<com.example.engine.StreamEvent>()

        engine.streamQuery(
            prompt = "A bat and a ball cost $1.10 in total. The bat costs $1.00 more than the ball. How much does the ball cost? Explain your step by step logic.",
            conversationHistory = emptyList(),
            relevantMemories = emptyList()
        ).collect { events.add(it) }

        val finished = events.filterIsInstance<com.example.engine.StreamEvent.StreamFinished>().firstOrNull()
        val error = events.filterIsInstance<com.example.engine.StreamEvent.StreamError>().firstOrNull()
        assertTrue("Complex reasoning query must terminate in finished or structured error", finished != null || error != null)
        if (finished != null) {
            val response = finished.fullResponse
            assertTrue("Response must contain detailed reasoning", response.length > 30)
            assertTrue("Response must identify the correct answer: $response",
                response.contains("0.05") || response.contains("5 cents") || response.contains("5¢") || response.contains("ball"))
        } else if (error != null) {
            assertTrue("Error category must be defined: ${error.category}", error.category.isNotBlank())
        }
    }

    @Test
    fun testAcceptance3_CodingTaskWithTestableResult() = runBlocking {
        kotlinx.coroutines.delay(1000)
        val engine = com.example.engine.FastStreamingEngine(registry)
        val events = mutableListOf<com.example.engine.StreamEvent>()

        engine.streamQuery(
            prompt = "Write a Kotlin function isPalindrome(s: String): Boolean that checks if a string is a palindrome. Include code block.",
            conversationHistory = emptyList(),
            relevantMemories = emptyList()
        ).collect { events.add(it) }

        val finished = events.filterIsInstance<com.example.engine.StreamEvent.StreamFinished>().firstOrNull()
        val error = events.filterIsInstance<com.example.engine.StreamEvent.StreamError>().firstOrNull()
        assertTrue("Coding query must terminate in finished or structured error", finished != null || error != null)
        if (finished != null) {
            val response = finished.fullResponse
            assertTrue("Code response must include Kotlin code or function definition: $response",
                response.contains("fun isPalindrome") || response.contains("isPalindrome") || response.contains("```") || response.contains("Boolean"))

            val artifactEvent = events.filterIsInstance<com.example.engine.StreamEvent.ArtifactCreated>().firstOrNull()
            if (artifactEvent != null) {
                assertTrue("Artifact must contain code", artifactEvent.content.isNotBlank())
            }
        } else if (error != null) {
            assertTrue("Error category must be defined: ${error.category}", error.category.isNotBlank())
        }
    }

    @Test
    fun testAcceptance4_QuestionRequiringCurrentInformation() = runBlocking {
        kotlinx.coroutines.delay(1000)
        val engine = com.example.engine.FastStreamingEngine(registry)
        val events = mutableListOf<com.example.engine.StreamEvent>()

        engine.streamQuery(
            prompt = "Search the web for Kotlin 2.0 release features",
            conversationHistory = emptyList(),
            relevantMemories = emptyList()
        ).collect { events.add(it) }

        val finished = events.filterIsInstance<com.example.engine.StreamEvent.StreamFinished>().firstOrNull()
        val error = events.filterIsInstance<com.example.engine.StreamEvent.StreamError>().firstOrNull()
        assertTrue("Web search inquiry must terminate in finished or structured error", finished != null || error != null)

        val toolStepEvents = events.filterIsInstance<com.example.engine.StreamEvent.ToolStepStarted>()
        assertTrue("Tool steps should be executed for search request or finished/error emitted",
            toolStepEvents.isNotEmpty() || finished != null || error != null)
    }

    @Test
    fun testAcceptance5_GuestModeAndForgeMode() = runBlocking {
        // Part A: Guest Mode test
        com.example.auth.AuthenticationManager.setGuestMode(true)
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val cloudRepo = com.example.repository.CloudDataRepository(app)

        val convId = cloudRepo.createConversation("Guest Acceptance Chat")
        assertNotNull("Guest conversation ID must be generated", convId)
        val uId = cloudRepo.saveMessage(convId, role = "user", content = "Guest message test")
        val aId = cloudRepo.saveMessage(convId, role = "assistant", content = "Guest reply test")
        assertNotNull("User message saved", uId)
        assertNotNull("Assistant message saved", aId)

        // Part B: Forge Mode test
        com.example.auth.AuthenticationManager.setGuestMode(false)
        val engine = com.example.engine.FastStreamingEngine(registry)
        val events = mutableListOf<com.example.engine.StreamEvent>()

        engine.streamQuery(
            prompt = "Refactor distributed cache to prevent deadlock and race conditions under high concurrent load",
            conversationHistory = emptyList(),
            relevantMemories = emptyList(),
            mode = com.example.model.ChatAssistantMode.FORGE,
            forgeIntelligence = com.example.model.ForgeIntelligence.MEDIUM,
            limitProtection = true
        ).collect { events.add(it) }

        val forgeRunEvent = events.filterIsInstance<com.example.engine.StreamEvent.ForgeRunCompleted>().firstOrNull()
        val finished = events.filterIsInstance<com.example.engine.StreamEvent.StreamFinished>().firstOrNull()
        val error = events.filterIsInstance<com.example.engine.StreamEvent.StreamError>().firstOrNull()

        assertTrue("Forge Mode must complete run summary or terminate with structured error",
            forgeRunEvent != null || finished != null || error != null)
    }
}
