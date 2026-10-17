package com.example.repository

import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class PlatformRepository {
    private val _currentUser = MutableStateFlow(
        User(
            id = "usr_94821",
            name = "Alex Vance",
            email = "alex.vance@enterprise.io",
            avatarUrl = null,
            apiKeyConfigured = true
        )
    )
    val currentUser: StateFlow<User> = _currentUser.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(
        listOf(
            Conversation("conv-1", "GitHub Repo Analysis & CI", System.currentTimeMillis() - 3600000, 4),
            Conversation("conv-2", "Web Search & Market Research", System.currentTimeMillis() - 86400000, 2),
            Conversation("conv-3", "Quarterly Cloud Documents", System.currentTimeMillis() - 172800000, 3)
        )
    )
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow("conv-1")
    val activeConversationId: StateFlow<String> = _activeConversationId.asStateFlow()

    private val messagesStore = mutableMapOf<String, MutableList<ChatMessage>>(
        "conv-1" to mutableListOf(
            ChatMessage(
                id = "m1",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = "Can you inspect square/retrofit on GitHub and summarize its stats?"
            ),
            ChatMessage(
                id = "m2",
                conversationId = "conv-1",
                role = MessageRole.ASSISTANT,
                content = "I queried the GitHub plugin for `square/retrofit`:\n\n• **Repository**: square/retrofit\n• **Stars**: 43,000+\n• **Forks**: 7,300+\n• **Default Branch**: master\n\nRetrofit is a type-safe HTTP client for Android and the JVM developed by Square.",
                toolExecutions = listOf(
                    ToolExecutionRecord(
                        id = "tx1",
                        toolId = "github.get_repository",
                        pluginName = "GitHub",
                        toolDisplayName = "get_repository",
                        inputArgs = mapOf("owner" to "square", "repo" to "retrofit"),
                        status = ToolStatus.SUCCESS,
                        output = "Repository: square/retrofit\nDescription: A type-safe HTTP client for Android and the JVM\nStars: 43100"
                    )
                ),
                citations = listOf(Citation("square/retrofit", "https://github.com/square/retrofit", "A type-safe HTTP client"))
            )
        )
    )

    private val _currentMessages = MutableStateFlow<List<ChatMessage>>(messagesStore["conv-1"] ?: emptyList())
    val currentMessages: StateFlow<List<ChatMessage>> = _currentMessages.asStateFlow()

    private val _memoryFacts = MutableStateFlow<List<MemoryFact>>(
        listOf(
            MemoryFact("mem-1", "Preferred Language", "Kotlin & Modern Jetpack Compose", "PREFERENCE"),
            MemoryFact("mem-2", "Current Project", "Nexus Cloud AI Orchestration Platform", "PROJECT"),
            MemoryFact("mem-3", "Deployment Target", "Google Cloud Run & Android Native", "CONTEXT"),
            MemoryFact("mem-4", "Tone Style", "Technical, concise, and structured", "PREFERENCE")
        )
    )
    val memoryFacts: StateFlow<List<MemoryFact>> = _memoryFacts.asStateFlow()

    private val _isMemoryEnabled = MutableStateFlow(true)
    val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<PlatformAuditLog>>(
        listOf(
            PlatformAuditLog("log-1", System.currentTimeMillis() - 7200000, "AUTH", "User alex.vance authenticated via OAuth2 Bearer Session"),
            PlatformAuditLog("log-2", System.currentTimeMillis() - 5400000, "PLUGIN_CONNECT", "Connected GitHub plugin (Scope: repo.read, issues.read)"),
            PlatformAuditLog("log-3", System.currentTimeMillis() - 3600000, "TOOL_EXECUTE", "Executed github.get_repository: square/retrofit (200 OK, 142ms)"),
            PlatformAuditLog("log-4", System.currentTimeMillis() - 1800000, "TOOL_EXECUTE", "Executed web_search.search: duckduckgo query (200 OK, 310ms)")
        )
    )
    val auditLogs: StateFlow<List<PlatformAuditLog>> = _auditLogs.asStateFlow()

    private val _cloudFiles = MutableStateFlow<List<FileAttachment>>(
        listOf(
            FileAttachment("f-1", "quarterly_architecture.pdf", "PDF", 1450000, "Deep architecture overview of cloud plugins and Gemini routing"),
            FileAttachment("f-2", "security_audit_spec.docx", "DOCX", 420000, "Zero-trust backend isolation and OAuth token encryption"),
            FileAttachment("f-3", "plugin_registry_telemetry.csv", "CSV", 89000, "Performance and error rates across all microservices")
        )
    )
    val cloudFiles: StateFlow<List<FileAttachment>> = _cloudFiles.asStateFlow()

    fun selectConversation(id: String) {
        _activeConversationId.value = id
        _currentMessages.value = messagesStore[id]?.toList() ?: emptyList()
    }

    fun createNewConversation(initialTitle: String = "New Conversation"): String {
        val newId = "conv-${System.currentTimeMillis()}"
        val newConv = Conversation(newId, initialTitle, System.currentTimeMillis(), 0)
        _conversations.value = listOf(newConv) + _conversations.value
        messagesStore[newId] = mutableListOf()
        selectConversation(newId)
        return newId
    }

    fun deleteConversation(id: String) {
        _conversations.value = _conversations.value.filter { it.id != id }
        messagesStore.remove(id)
        if (_activeConversationId.value == id) {
            val first = _conversations.value.firstOrNull()
            if (first != null) selectConversation(first.id) else createNewConversation()
        }
    }

    fun addMessage(message: ChatMessage) {
        val list = messagesStore.getOrPut(message.conversationId) { mutableListOf() }
        list.add(message)
        if (message.conversationId == _activeConversationId.value) {
            _currentMessages.value = list.toList()
        }
        // Update conversation timestamp
        _conversations.value = _conversations.value.map {
            if (it.id == message.conversationId) {
                it.copy(updatedAt = System.currentTimeMillis(), messageCount = list.size)
            } else it
        }
    }

    fun updateMessage(message: ChatMessage) {
        val list = messagesStore[message.conversationId] ?: return
        val idx = list.indexOfFirst { it.id == message.id }
        if (idx != -1) {
            list[idx] = message
            if (message.conversationId == _activeConversationId.value) {
                _currentMessages.value = list.toList()
            }
        }
    }

    fun addMemory(key: String, value: String, category: String) {
        val fact = MemoryFact(UUID.randomUUID().toString(), key, value, category)
        _memoryFacts.value = listOf(fact) + _memoryFacts.value
        addAuditLog("MEMORY", "Added user memory: $key")
    }

    fun removeMemory(id: String) {
        _memoryFacts.value = _memoryFacts.value.filter { it.id != id }
        addAuditLog("MEMORY", "Removed memory item: $id")
    }

    fun clearAllMemory() {
        _memoryFacts.value = emptyList()
        addAuditLog("MEMORY", "Cleared all long-term memory")
    }

    fun toggleMemory(enabled: Boolean) {
        _isMemoryEnabled.value = enabled
    }

    fun addAuditLog(eventType: String, details: String, severity: String = "INFO") {
        val log = PlatformAuditLog(UUID.randomUUID().toString(), System.currentTimeMillis(), eventType, details, severity)
        _auditLogs.value = listOf(log) + _auditLogs.value.take(49)
    }

    fun addCloudFile(name: String, type: String, size: Long, summary: String) {
        val file = FileAttachment(UUID.randomUUID().toString(), name, type, size, summary)
        _cloudFiles.value = listOf(file) + _cloudFiles.value
        addAuditLog("FILE", "Uploaded document: $name ($type)")
    }

    fun deleteCloudFile(id: String) {
        _cloudFiles.value = _cloudFiles.value.filter { it.id != id }
        addAuditLog("FILE", "Deleted cloud document: $id")
    }

    fun updateUser(name: String, email: String) {
        _currentUser.value = _currentUser.value.copy(name = name, email = email)
        addAuditLog("AUTH", "Updated user profile: $email")
    }
}
