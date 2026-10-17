package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.OrchestrationEngine
import com.example.engine.OrchestrationEvent
import com.example.model.*
import com.example.plugins.PluginRegistry
import com.example.repository.PlatformRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class ActiveTab {
    object Chat : ActiveTab()
    object PluginStore : ActiveTab()
    object CustomPluginDev : ActiveTab()
    object CloudFiles : ActiveTab()
    object MemoryManager : ActiveTab()
    object Settings : ActiveTab()
    object AuditLogs : ActiveTab()
}

class MainViewModel(
    val repository: PlatformRepository = PlatformRepository(),
    val pluginRegistry: PluginRegistry = PluginRegistry()
) : ViewModel() {

    val orchestrationEngine = OrchestrationEngine(pluginRegistry)

    private val _currentTab = MutableStateFlow<ActiveTab>(ActiveTab.Chat)
    val currentTab: StateFlow<ActiveTab> = _currentTab.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _currentStatusIndicator = MutableStateFlow<String?>(null)
    val currentStatusIndicator: StateFlow<String?> = _currentStatusIndicator.asStateFlow()

    private val _pendingApprovalRecord = MutableStateFlow<ToolExecutionRecord?>(null)
    val pendingApprovalRecord: StateFlow<ToolExecutionRecord?> = _pendingApprovalRecord.asStateFlow()

    private val approvedToolsSet = mutableSetOf<String>()

    private val _apiKeyOverride = MutableStateFlow<String>("")
    val apiKeyOverride: StateFlow<String> = _apiKeyOverride.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _temperature = MutableStateFlow(0.4f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    fun switchTab(tab: ActiveTab) {
        _currentTab.value = tab
    }

    fun setApiKeyOverride(key: String) {
        _apiKeyOverride.value = key
    }

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
    }

    fun setTemperature(temp: Float) {
        _temperature.value = temp
    }

    fun sendMessage(promptText: String, attachedFiles: List<FileAttachment> = emptyList()) {
        if (promptText.isBlank() && attachedFiles.isEmpty()) return
        if (_isGenerating.value) return

        val convId = repository.activeConversationId.value
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = MessageRole.USER,
            content = promptText,
            attachments = attachedFiles
        )
        repository.addMessage(userMsg)

        val assistantMsgId = UUID.randomUUID().toString()
        val initialAssistantMsg = ChatMessage(
            id = assistantMsgId,
            conversationId = convId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        repository.addMessage(initialAssistantMsg)

        _isGenerating.value = true
        _currentStatusIndicator.value = "Thinking..."

        viewModelScope.launch {
            try {
                orchestrationEngine.processQuery(
                    userPrompt = promptText,
                    conversationHistory = repository.currentMessages.value,
                    memoryFacts = if (repository.isMemoryEnabled.value) repository.memoryFacts.value else emptyList(),
                    attachments = attachedFiles,
                    approvedToolIds = approvedToolsSet,
                    apiKeyOverride = _apiKeyOverride.value
                ).collect { event ->
                    when (event) {
                        is OrchestrationEvent.StatusUpdate -> {
                            _currentStatusIndicator.value = event.statusText
                        }
                        is OrchestrationEvent.ToolApprovalRequired -> {
                            _pendingApprovalRecord.value = event.record
                            _currentStatusIndicator.value = "Awaiting your confirmation for ${event.record.pluginName}.${event.record.toolDisplayName}"
                            _isGenerating.value = false
                        }
                        is OrchestrationEvent.ToolExecuting -> {
                            _currentStatusIndicator.value = "Running ${event.record.pluginName}..."
                            updateAssistantMessageWithRecord(convId, assistantMsgId, event.record)
                        }
                        is OrchestrationEvent.ToolCompleted -> {
                            updateAssistantMessageWithRecord(convId, assistantMsgId, event.record)
                            repository.addAuditLog(
                                eventType = "TOOL_EXECUTE",
                                details = "${event.record.pluginName}.${event.record.toolDisplayName} completed (${event.record.status})"
                            )
                        }
                        is OrchestrationEvent.FinalResponseChunk -> {
                            appendAssistantMessageChunk(convId, assistantMsgId, event.chunk)
                        }
                        is OrchestrationEvent.FinalResponseComplete -> {
                            val finalMsg = ChatMessage(
                                id = assistantMsgId,
                                conversationId = convId,
                                role = MessageRole.ASSISTANT,
                                content = event.fullMessage,
                                toolExecutions = event.records,
                                citations = event.citations,
                                isStreaming = false
                            )
                            repository.updateMessage(finalMsg)
                            _currentStatusIndicator.value = null
                            _isGenerating.value = false
                        }
                        is OrchestrationEvent.ErrorOccurred -> {
                            val errorMsg = ChatMessage(
                                id = assistantMsgId,
                                conversationId = convId,
                                role = MessageRole.ASSISTANT,
                                content = "⚠️ Orchestration Error: ${event.message}",
                                isStreaming = false
                            )
                            repository.updateMessage(errorMsg)
                            _currentStatusIndicator.value = null
                            _isGenerating.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    id = assistantMsgId,
                    conversationId = convId,
                    role = MessageRole.ASSISTANT,
                    content = "Error during execution: ${e.message}",
                    isStreaming = false
                )
                repository.updateMessage(errorMsg)
                _currentStatusIndicator.value = null
                _isGenerating.value = false
            }
        }
    }

    fun approvePendingTool() {
        val pending = _pendingApprovalRecord.value ?: return
        approvedToolsSet.add(pending.toolId)
        _pendingApprovalRecord.value = null
        repository.addAuditLog("APPROVAL", "User approved dangerous action: ${pending.toolId}")

        // Re-trigger the query that was paused
        val convMessages = repository.currentMessages.value
        val lastUserMsg = convMessages.findLast { it.role == MessageRole.USER }
        if (lastUserMsg != null) {
            sendMessage(lastUserMsg.content, lastUserMsg.attachments)
        }
    }

    fun rejectPendingTool() {
        val pending = _pendingApprovalRecord.value ?: return
        _pendingApprovalRecord.value = null
        repository.addAuditLog("APPROVAL", "User rejected dangerous action: ${pending.toolId}", "WARN")

        val convId = repository.activeConversationId.value
        val lastAssistant = repository.currentMessages.value.findLast { it.role == MessageRole.ASSISTANT }
        if (lastAssistant != null) {
            val rejectedRec = pending.copy(status = ToolStatus.REJECTED, errorMessage = "Execution rejected by user")
            repository.updateMessage(
                lastAssistant.copy(
                    content = "Operation `${pending.pluginName}.${pending.toolDisplayName}` was cancelled per user instruction.",
                    toolExecutions = listOf(rejectedRec),
                    isStreaming = false
                )
            )
        }
        _currentStatusIndicator.value = null
        _isGenerating.value = false
    }

    private fun updateAssistantMessageWithRecord(convId: String, msgId: String, record: ToolExecutionRecord) {
        val current = repository.currentMessages.value.find { it.id == msgId } ?: return
        val existingRecords = current.toolExecutions.filter { it.id != record.id } + record
        repository.updateMessage(current.copy(toolExecutions = existingRecords))
    }

    private fun appendAssistantMessageChunk(convId: String, msgId: String, chunk: String) {
        val current = repository.currentMessages.value.find { it.id == msgId } ?: return
        repository.updateMessage(current.copy(content = current.content + chunk))
    }
}
