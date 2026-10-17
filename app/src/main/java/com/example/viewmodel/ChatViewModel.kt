package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthenticationManager
import com.example.engine.FastStreamingEngine
import com.example.engine.StreamEvent
import com.example.model.*
import com.example.plugins.PluginRegistry
import com.example.repository.CloudDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

sealed class AppDestination {
    object Chat : AppDestination()
    object Projects : AppDestination()
    object Artifacts : AppDestination()
    object Automations : AppDestination()
    object Activities : AppDestination()
    object PluginStore : AppDestination()
    object Memory : AppDestination()
    object Files : AppDestination()
    object Settings : AppDestination()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val repository = CloudDataRepository(application)
    val pluginRegistry = PluginRegistry()
    private val engine = FastStreamingEngine(pluginRegistry)
    private val apiKeyStore = com.example.auth.ApiKeyStore(application)
    private val apiKeyHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    private val _apiKeySetupState = MutableStateFlow("LOADING")
    val apiKeySetupState: StateFlow<String> = _apiKeySetupState.asStateFlow()
    private val _apiKeySetupMessage = MutableStateFlow("")
    val apiKeySetupMessage: StateFlow<String> = _apiKeySetupMessage.asStateFlow()
    private val _userApiKey = MutableStateFlow("")
    private var apiKeyIdentity: String? = null

    fun loadApiKeyForIdentity(identity: String) {
        if (apiKeyIdentity == identity && _apiKeySetupState.value != "LOADING") return
        apiKeyIdentity = identity
        _userApiKey.value = ""
        _apiKeySetupMessage.value = ""
        _apiKeySetupState.value = "LOADING"
        viewModelScope.launch(Dispatchers.IO) {
            val savedKey = runCatching { apiKeyStore.getKey(identity) }.getOrNull().orEmpty()
            withContext(Dispatchers.Main) {
                if (apiKeyIdentity != identity) return@withContext
                _userApiKey.value = savedKey
                _apiKeySetupState.value = if (savedKey.isBlank()) "REQUIRED" else "READY"
            }
        }
    }

    fun validateAndSaveApiKey(rawKey: String) {
        val key = rawKey.trim()
        val identity = apiKeyIdentity
        if (identity.isNullOrBlank()) {
            _apiKeySetupState.value = "ERROR"
            _apiKeySetupMessage.value = "Please sign in or continue as guest first."
            return
        }
        if (key.isBlank()) {
            _apiKeySetupState.value = "INVALID"
            _apiKeySetupMessage.value = "Please enter your Gemini API key."
            return
        }
        _apiKeySetupState.value = "CHECKING"
        _apiKeySetupMessage.value = ""
        viewModelScope.launch {
            try {
                val valid = withContext(Dispatchers.IO) {
                    val encodedKey = java.net.URLEncoder.encode(key, "UTF-8")
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models?key=$encodedKey")
                        .get()
                        .build()
                    apiKeyHttpClient.newCall(request).execute().use { response -> response.isSuccessful }
                }
                if (!valid) {
                    _apiKeySetupState.value = "INVALID"
                    _apiKeySetupMessage.value = "Your API key is incorrect or doesn't have access. Check it and try again."
                    return@launch
                }
                withContext(Dispatchers.IO) { apiKeyStore.saveKey(identity, key) }
                if (apiKeyIdentity == identity) {
                    _userApiKey.value = key
                    _apiKeySetupState.value = "SAVED"
                    _apiKeySetupMessage.value = "API key verified and saved securely on this device."
                }
            } catch (_: Exception) {
                _apiKeySetupState.value = "ERROR"
                _apiKeySetupMessage.value = "Couldn't verify the key. Check your internet connection and try again."
            }
        }
    }

    private val _currentDestination = MutableStateFlow<AppDestination>(AppDestination.Chat)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    // Mode state: Normal, Deep Research, Agent
    private val _currentMode = MutableStateFlow(ChatAssistantMode.NORMAL)
    val currentMode: StateFlow<ChatAssistantMode> = _currentMode.asStateFlow()

    // Current Project Filter
    private val _activeProject = MutableStateFlow<FirestoreProject?>(null)
    val activeProject: StateFlow<FirestoreProject?> = _activeProject.asStateFlow()

    // Real-time Cloud Collections
    private val _conversations = MutableStateFlow<List<FirestoreConversation>>(emptyList())
    val conversations: StateFlow<List<FirestoreConversation>> = _conversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<FirestoreMessage>>(emptyList())
    val messages: StateFlow<List<FirestoreMessage>> = _messages.asStateFlow()

    private val _projects = MutableStateFlow<List<FirestoreProject>>(emptyList())
    val projects: StateFlow<List<FirestoreProject>> = _projects.asStateFlow()

    private val _artifacts = MutableStateFlow<List<FirestoreArtifact>>(emptyList())
    val artifacts: StateFlow<List<FirestoreArtifact>> = _artifacts.asStateFlow()

    private val _activeArtifact = MutableStateFlow<FirestoreArtifact?>(null)
    val activeArtifact: StateFlow<FirestoreArtifact?> = _activeArtifact.asStateFlow()

    private val _automations = MutableStateFlow<List<FirestoreAutomation>>(emptyList())
    val automations: StateFlow<List<FirestoreAutomation>> = _automations.asStateFlow()

    private val _activities = MutableStateFlow<List<FirestoreActivityItem>>(emptyList())
    val activities: StateFlow<List<FirestoreActivityItem>> = _activities.asStateFlow()

    private val _memories = MutableStateFlow<List<FirestoreMemory>>(emptyList())
    val memories: StateFlow<List<FirestoreMemory>> = _memories.asStateFlow()

    // Personalization Settings
    private val _personalization = MutableStateFlow(UserPersonalization())
    val personalization: StateFlow<UserPersonalization> = _personalization.asStateFlow()

    // Global Search Query & Results
    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    // Streaming state
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentStreamingText = MutableStateFlow("")
    val currentStreamingText: StateFlow<String> = _currentStreamingText.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    private val _researchProgress = MutableStateFlow<Pair<String, Float>?>(null)
    val researchProgress: StateFlow<Pair<String, Float>?> = _researchProgress.asStateFlow()

    private val _activeToolRecords = MutableStateFlow<List<ToolExecutionRecord>>(emptyList())
    val activeToolRecords: StateFlow<List<ToolExecutionRecord>> = _activeToolRecords.asStateFlow()

    private val _pendingApproval = MutableStateFlow<ToolExecutionRecord?>(null)
    val pendingApproval: StateFlow<ToolExecutionRecord?> = _pendingApproval.asStateFlow()

    // Forge Mode Configuration State
    private val _forgeIntelligence = MutableStateFlow(ForgeIntelligence.HIGH)
    val forgeIntelligence: StateFlow<ForgeIntelligence> = _forgeIntelligence.asStateFlow()

    private val _limitProtection = MutableStateFlow(true)
    val limitProtection: StateFlow<Boolean> = _limitProtection.asStateFlow()

    private val _isUltimateForge = MutableStateFlow(false)
    val isUltimateForge: StateFlow<Boolean> = _isUltimateForge.asStateFlow()

    private val _activeForgeSummary = MutableStateFlow<ForgeRunSummary?>(null)
    val activeForgeSummary: StateFlow<ForgeRunSummary?> = _activeForgeSummary.asStateFlow()

    fun setForgeIntelligence(intelligence: ForgeIntelligence) {
        _forgeIntelligence.value = intelligence
    }

    fun setLimitProtection(enabled: Boolean) {
        _limitProtection.value = enabled
    }

    fun setUltimateForge(enabled: Boolean) {
        _isUltimateForge.value = enabled
    }

    private val approvedToolIds = mutableSetOf<String>()
    private var streamingJob: Job? = null

    init {
        viewModelScope.launch {
            AuthenticationManager.currentUserFlow.collect { user ->
                if (user != null || AuthenticationManager.isGuestMode.value) {
                    loadCloudData()
                } else {
                    _conversations.value = emptyList()
                    _messages.value = emptyList()
                    _projects.value = emptyList()
                    _artifacts.value = emptyList()
                    _automations.value = emptyList()
                    _activities.value = emptyList()
                    _memories.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            AuthenticationManager.isGuestMode.collect { isGuest ->
                if (isGuest || AuthenticationManager.currentUserFlow.value != null) {
                    loadCloudData()
                }
            }
        }
    }

    fun navigateTo(dest: AppDestination) {
        _currentDestination.value = dest
    }

    fun setMode(mode: ChatAssistantMode) {
        _currentMode.value = mode
    }

    fun setActiveProject(project: FirestoreProject?) {
        _activeProject.value = project
        viewModelScope.launch {
            repository.observeConversations(project?.id).collect { list ->
                _conversations.value = list
                if (list.isNotEmpty()) {
                    selectConversation(list.first().id)
                } else {
                    startNewConversation()
                }
            }
        }
    }

    private fun loadCloudData() {
        viewModelScope.launch {
            repository.observeConversations().collect { list ->
                _conversations.value = list
                if (_activeConversationId.value == null && list.isNotEmpty()) {
                    selectConversation(list.first().id)
                }
            }
        }
        viewModelScope.launch {
            repository.observeProjects().collect { list ->
                _projects.value = list
            }
        }
        viewModelScope.launch {
            repository.observeArtifacts().collect { list ->
                _artifacts.value = list
            }
        }
        viewModelScope.launch {
            repository.observeAutomations().collect { list ->
                _automations.value = list
            }
        }
        viewModelScope.launch {
            repository.observeActivities().collect { list ->
                _activities.value = list
            }
        }
        viewModelScope.launch {
            repository.observeMemories().collect { list ->
                _memories.value = list
            }
        }
        viewModelScope.launch {
            _personalization.value = repository.loadPersonalization()
        }
    }

    private var observeMessagesJob: kotlinx.coroutines.Job? = null

    fun selectConversation(convId: String) {
        if (_activeConversationId.value != convId) {
            _activeConversationId.value = convId
            _messages.value = emptyList()
        }
        observeMessagesJob?.cancel()
        observeMessagesJob = viewModelScope.launch {
            repository.observeMessages(convId).collect { msgList ->
                if (msgList.isNotEmpty() || _messages.value.isEmpty()) {
                    _messages.value = msgList
                }
            }
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val projId = _activeProject.value?.id
            val newId = repository.createConversation("New Chat", projectId = projId, mode = _currentMode.value.name)
            selectConversation(newId)
            _messages.value = emptyList()
        }
    }

    fun deleteConversation(convId: String) {
        viewModelScope.launch {
            repository.deleteConversation(convId)
            if (_activeConversationId.value == convId) {
                val remaining = _conversations.value.filter { it.id != convId }
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    startNewConversation()
                }
            }
        }
    }

    fun renameConversation(convId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateConversationTitle(convId, newTitle)
        }
    }

    // --- Message Dispatch with Optimistic UI & Fast Streaming ---
    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        if (_userApiKey.value.isBlank()) {
            _apiKeySetupState.value = "REQUIRED"
            _apiKeySetupMessage.value = "Add and verify your Gemini API key before starting a chat."
            return
        }
        if (_isStreaming.value) {
            Log.w("NexusAI_Chat", "Ignoring duplicate concurrent send request while streaming is active.")
            return
        }
        val currentConvId = _activeConversationId.value

        viewModelScope.launch {
            val convId = if (currentConvId.isNullOrEmpty()) {
                val id = repository.createConversation(
                    initialTitle = userText.take(28),
                    projectId = _activeProject.value?.id,
                    mode = _currentMode.value.name
                )
                _activeConversationId.value = id
                selectConversation(id)
                id
            } else {
                if (observeMessagesJob == null || !observeMessagesJob!!.isActive) {
                    selectConversation(currentConvId)
                }
                currentConvId
            }

            if (_messages.value.isEmpty()) {
                repository.updateConversationTitle(convId, userText.take(28))
            }

            Log.i("NexusAI_Chat", "Trace[Submit]: promptLength=${userText.length}, convId=$convId, isGuest=${AuthenticationManager.isGuestMode.value}")

            // 1. Instant Optimistic Render
            val userMsgId = UUID.randomUUID().toString()
            val optimisticUserMsg = FirestoreMessage(
                id = userMsgId,
                conversationId = convId,
                userId = repository.currentUserId ?: "",
                role = "user",
                content = userText
            )
            _messages.value = _messages.value + optimisticUserMsg

            launch {
                repository.saveMessage(convId, userMsgId, "user", userText)
            }

            // Memory candidate extraction (lightweight heuristic, zero extra LLM calls)
            val candidate = com.example.engine.MemoryIntelligence.extractCandidate(
                userText = userText,
                activeProjectId = _activeProject.value?.id,
                conversationId = convId
            )
            if (candidate != null && !repository.isDeletedMemoryKey(candidate.key)) {
                launch {
                    repository.upsertMemory(
                        key = candidate.key,
                        value = candidate.value,
                        category = candidate.category,
                        projectId = candidate.projectId,
                        sourceConversationId = convId
                    )
                }
            }

            // 2. Prepare Streaming State
            _isStreaming.value = true
            _currentStreamingText.value = ""
            _statusNotice.value = "Thinking..."
            _researchProgress.value = null
            _activeToolRecords.value = emptyList()

            // Relevance-based memory retrieval: score and pick top-K memories
            val scoredMemories = com.example.engine.MemoryIntelligence.selectRelevantMemories(
                query = userText,
                allMemories = _memories.value,
                activeProjectId = _activeProject.value?.id,
                limit = 4
            )

            streamingJob = launch {
                try {
                    engine.streamQuery(
                        prompt = userText,
                        conversationHistory = _messages.value,
                        relevantMemories = scoredMemories,
                        mode = _currentMode.value,
                        projectContext = _activeProject.value,
                        apiKeyOverride = _userApiKey.value,
                        approvedToolIds = approvedToolIds,
                        forgeIntelligence = if (_isUltimateForge.value) ForgeIntelligence.ULTIMATE_USAGE else _forgeIntelligence.value,
                        limitProtection = _limitProtection.value,
                        personalization = _personalization.value
                    ).collect { event ->
                        when (event) {
                            is StreamEvent.StatusNotice -> {
                                _statusNotice.value = event.text
                            }
                            is StreamEvent.ResearchProgress -> {
                                _researchProgress.value = Pair(event.step, event.completedCount.toFloat() / event.totalCount)
                            }
                            is StreamEvent.ForgeProgress -> {
                                _researchProgress.value = Pair("${event.step} (${event.modelsActive} models)", event.currentRound.toFloat() / event.totalRounds)
                            }
                            is StreamEvent.ForgeRunCompleted -> {
                                _activeForgeSummary.value = event.summary
                            }
                            is StreamEvent.ToolStepStarted -> {
                                _activeToolRecords.value = _activeToolRecords.value + event.record
                            }
                            is StreamEvent.ToolStepCompleted -> {
                                _activeToolRecords.value = _activeToolRecords.value.map {
                                    if (it.id == event.record.id) event.record else it
                                }
                                repository.recordActivity(
                                    title = "${event.record.pluginName}.${event.record.toolDisplayName}",
                                    details = event.record.output?.take(100) ?: event.record.errorMessage ?: "Executed",
                                    category = "TOOL"
                                )
                            }
                            is StreamEvent.ToolRequiresUserApproval -> {
                                _pendingApproval.value = event.record
                                _statusNotice.value = "Action requires your approval: ${event.record.pluginName}"
                                _isStreaming.value = false
                            }
                            is StreamEvent.ContentChunk -> {
                                _currentStreamingText.value = _currentStreamingText.value + event.text
                            }
                            is StreamEvent.ArtifactCreated -> {
                                val savedArtId = repository.saveArtifact(event.title, event.type, event.content, event.language)
                                _activeArtifact.value = FirestoreArtifact(id = savedArtId, title = event.title, type = event.type, content = event.content, language = event.language)
                            }
                            is StreamEvent.StreamFinished -> {
                                val finalAssistantText = if (_currentStreamingText.value.isNotBlank()) {
                                    _currentStreamingText.value
                                } else event.fullResponse

                                Log.i("NexusAI_Chat", "Trace[StreamFinished]: responseLength=${finalAssistantText.length}, toolsCount=${event.toolExecutions.size}")

                                val assistantMsgId = UUID.randomUUID().toString()
                                val toolPayloads = event.toolExecutions.map {
                                    mapOf(
                                        "plugin" to it.pluginName,
                                        "tool" to it.toolDisplayName,
                                        "status" to it.status.name,
                                        "output" to (it.output ?: it.errorMessage ?: "")
                                    )
                                }
                                val citationPayloads = event.citations.map {
                                    mapOf("title" to it.title, "url" to it.url, "snippet" to (it.snippet ?: ""))
                                }

                                val assistantMsg = FirestoreMessage(
                                    id = assistantMsgId,
                                    conversationId = convId,
                                    userId = repository.currentUserId ?: "",
                                    role = "assistant",
                                    content = finalAssistantText,
                                    toolExecutions = toolPayloads,
                                    citations = citationPayloads,
                                    artifactId = event.detectedArtifact?.id
                                )

                                _messages.value = _messages.value.filter { it.id != assistantMsgId } + assistantMsg

                                launch {
                                    repository.saveMessage(
                                        convId = convId,
                                        msgId = assistantMsgId,
                                        role = "assistant",
                                        content = finalAssistantText,
                                        toolExecutions = toolPayloads,
                                        citations = citationPayloads,
                                        artifactId = event.detectedArtifact?.id
                                    )
                                }

                                _isStreaming.value = false
                                _currentStreamingText.value = ""
                                _statusNotice.value = null
                                _researchProgress.value = null
                                _activeToolRecords.value = emptyList()
                            }
                            is StreamEvent.StreamError -> {
                                Log.e("NexusAI_Chat", "Trace[StreamError]: category=${event.category}, error=${event.errorMessage}")
                                val errorMsgId = UUID.randomUUID().toString()
                                val errorMsg = FirestoreMessage(
                                    id = errorMsgId,
                                    conversationId = convId,
                                    userId = repository.currentUserId ?: "",
                                    role = "error",
                                    content = "[${event.category}] ${event.errorMessage}"
                                )
                                _messages.value = _messages.value.filter { it.id != errorMsgId } + errorMsg

                                launch {
                                    repository.saveMessage(
                                        convId = convId,
                                        msgId = errorMsgId,
                                        role = "error",
                                        content = "[${event.category}] ${event.errorMessage}"
                                    )
                                }

                                _isStreaming.value = false
                                _currentStreamingText.value = ""
                                _statusNotice.value = null
                                _researchProgress.value = null
                                _activeToolRecords.value = emptyList()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NexusAI_Chat", "Trace[CatchException]: Failed query execution", e)
                    val errorMsgId = UUID.randomUUID().toString()
                    val cat = if (e is java.io.IOException) "NETWORK_ERROR" else "PROVIDER_ERROR"
                    val errorContent = "[$cat] Request failed: ${e.localizedMessage ?: "Unexpected error"}. Please tap Retry."
                    val errorMsg = FirestoreMessage(
                        id = errorMsgId,
                        conversationId = convId,
                        userId = repository.currentUserId ?: "",
                        role = "error",
                        content = errorContent
                    )
                    _messages.value = _messages.value.filter { it.id != errorMsgId } + errorMsg
                    launch {
                        repository.saveMessage(
                            convId = convId,
                            msgId = errorMsgId,
                            role = "error",
                            content = errorContent
                        )
                    }
                    _isStreaming.value = false
                    _currentStreamingText.value = ""
                    _statusNotice.value = null
                    _researchProgress.value = null
                    _activeToolRecords.value = emptyList()
                } finally {
                    // A Flow can finish without emitting StreamFinished or StreamError.
                    // Always release the UI from the generating state in that case.
                    if (_isStreaming.value) {
                        Log.w("NexusAI_Chat", "Stream ended without a terminal event; clearing stuck streaming state.")
                        val unfinishedText = _currentStreamingText.value
                        val fallbackId = UUID.randomUUID().toString()
                        val fallbackRole = if (unfinishedText.isBlank()) "error" else "assistant"
                        val fallbackContent = if (unfinishedText.isBlank()) {
                            "[INCOMPLETE_STREAM] Generation ended without a final response. Please retry."
                        } else {
                            "$unfinishedText\n\n*(Generation stopped before completion. Please retry.)*"
                        }
                        val fallbackMessage = FirestoreMessage(
                            id = fallbackId,
                            conversationId = convId,
                            userId = repository.currentUserId ?: "",
                            role = fallbackRole,
                            content = fallbackContent
                        )
                        _messages.value = _messages.value + fallbackMessage
                        viewModelScope.launch {
                            repository.saveMessage(
                                convId = convId,
                                msgId = fallbackId,
                                role = fallbackRole,
                                content = fallbackContent
                            )
                        }
                        _isStreaming.value = false
                        _currentStreamingText.value = ""
                        _statusNotice.value = null
                        _researchProgress.value = null
                        _activeToolRecords.value = emptyList()
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        streamingJob?.cancel()
        _isStreaming.value = false
        _statusNotice.value = null
        _researchProgress.value = null

        val currentText = _currentStreamingText.value
        val convId = _activeConversationId.value
        if (convId != null) {
            val cancelledText = if (currentText.isNotBlank()) {
                "$currentText\n\n*(Cancelled by user)*"
            } else {
                "*(Generation cancelled by user)*"
            }
            val cancelMsgId = UUID.randomUUID().toString()
            val cancelMsg = FirestoreMessage(
                id = cancelMsgId,
                conversationId = convId,
                userId = repository.currentUserId ?: "",
                role = "assistant",
                content = cancelledText
            )
            _messages.value = _messages.value.filter { it.id != cancelMsgId } + cancelMsg
            viewModelScope.launch {
                repository.saveMessage(convId, cancelMsgId, "assistant", cancelledText)
                _currentStreamingText.value = ""
            }
        }
    }

    fun approveAction() {
        val pending = _pendingApproval.value ?: return
        approvedToolIds.add(pending.toolId)
        _pendingApproval.value = null
        val lastUser = _messages.value.findLast { it.role == "user" }
        if (lastUser != null) sendMessage(lastUser.content)
    }

    fun rejectAction() {
        _pendingApproval.value = null
        _isStreaming.value = false
        _statusNotice.value = null
    }

    fun regenerateLastMessage() {
        if (_isStreaming.value) return
        val lastUser = _messages.value.findLast { it.role == "user" } ?: return
        _messages.value = _messages.value.filter { it.role != "error" }
        sendMessage(lastUser.content)
    }

    // Projects API
    fun createProject(name: String, desc: String, instructions: String, plugins: List<String>) {
        viewModelScope.launch {
            repository.createProject(name, desc, instructions, plugins)
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repository.deleteProject(id)
            if (_activeProject.value?.id == id) {
                setActiveProject(null)
            }
        }
    }

    // Artifacts API
    fun selectArtifact(artifact: FirestoreArtifact?) {
        _activeArtifact.value = artifact
        if (artifact != null) navigateTo(AppDestination.Artifacts)
    }

    fun polishArtifact(artifact: FirestoreArtifact, report: com.example.engine.DesignIntelligence.DesignAuditReport) {
        val polishPrompt = "Please polish and refine the artifact '${artifact.title}'.\n\n" +
                report.toPolishPrompt() + "\n\n" +
                "Current Code:\n```${artifact.language}\n${artifact.content}\n```\n\n" +
                "Please output the complete, improved standalone code with all anti-slop fixes and full functionality."
        navigateTo(AppDestination.Chat)
        sendMessage(polishPrompt)
    }

    fun saveArtifact(title: String, type: String, content: String, language: String = "kotlin") {
        viewModelScope.launch {
            val id = repository.saveArtifact(title, type, content, language)
            _activeArtifact.value = _artifacts.value.find { it.id == id }
        }
    }

    // Automations API
    fun createAutomation(name: String, prompt: String, schedule: String, plugins: List<String>) {
        viewModelScope.launch {
            repository.createAutomation(name, prompt, schedule, plugins)
        }
    }

    fun runAutomationNow(auto: FirestoreAutomation) {
        viewModelScope.launch {
            repository.runAutomationNow(auto.id, auto.name, "Executed prompt: ${auto.prompt.take(30)}")
        }
    }

    fun toggleAutomation(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAutomation(id, enabled)
        }
    }

    fun deleteAutomation(id: String) {
        viewModelScope.launch {
            repository.deleteAutomation(id)
        }
    }

    // Memory API
    fun saveNewMemory(key: String, value: String, category: String = "PREFERENCE", projectId: String? = null) {
        viewModelScope.launch {
            repository.upsertMemory(key, value, category, projectId)
        }
    }

    fun updateExistingMemory(id: String, key: String, value: String, category: String = "PREFERENCE", projectId: String? = null) {
        viewModelScope.launch {
            repository.updateMemory(id, key, value, category, projectId)
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
        }
    }

    // Personalization API
    fun updatePersonalization(updated: UserPersonalization) {
        _personalization.value = updated
        viewModelScope.launch {
            repository.savePersonalization(updated)
        }
    }

    fun setResponseStyle(style: String) {
        val updated = _personalization.value.copy(responseStyle = style)
        updatePersonalization(updated)
    }

    fun setContextCompressionEnabled(enabled: Boolean) {
        val updated = _personalization.value.copy(contextCompressionEnabled = enabled)
        updatePersonalization(updated)
    }

    fun setGlobalSearchQuery(q: String) {
        _globalSearchQuery.value = q
    }
}
