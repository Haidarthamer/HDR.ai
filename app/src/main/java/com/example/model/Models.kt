package com.example.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    val apiKeyConfigured: Boolean = false
)

enum class AuthType {
    NONE,
    OAUTH2,
    API_KEY,
    BEARER_TOKEN
}

data class PluginPermission(
    val id: String,
    val label: String,
    val description: String,
    val isDangerous: Boolean = false,
    var isGranted: Boolean = false
)

data class ToolParameter(
    val name: String,
    val type: String, // string, number, boolean, array, object
    val description: String,
    val required: Boolean = false
)

data class PluginTool(
    val id: String, // e.g. "github.search_repositories"
    val name: String, // e.g. "search_repositories"
    val description: String,
    val parameters: List<ToolParameter> = emptyList(),
    val requiredPermissionId: String? = null,
    val isDangerous: Boolean = false
)

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val iconEmoji: String,
    val authenticationType: AuthType,
    val permissions: List<PluginPermission>,
    val tools: List<PluginTool>,
    val apiEndpoint: String? = null,
    val isConnected: Boolean = false,
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false,
    val connectionAccount: String? = null,
    val autoApproveDangerous: Boolean = false
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

enum class ToolStatus {
    PENDING_APPROVAL,
    EXECUTING,
    SUCCESS,
    FAILED,
    REJECTED
}

data class ToolExecutionRecord(
    val id: String,
    val toolId: String,
    val pluginName: String,
    val toolDisplayName: String,
    val inputArgs: Map<String, String>,
    val status: ToolStatus,
    val output: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val toolExecutions: List<ToolExecutionRecord> = emptyList(),
    val attachments: List<FileAttachment> = emptyList(),
    val citations: List<Citation> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

data class Citation(
    val title: String,
    val url: String,
    val snippet: String? = null
)

data class FileAttachment(
    val id: String,
    val fileName: String,
    val fileType: String,
    val sizeBytes: Long,
    val contentSummary: String? = null
)

data class MemoryFact(
    val id: String,
    val key: String,
    val value: String,
    val category: String, // PREFERENCE, FACT, PROJECT, CONTEXT
    val createdAt: Long = System.currentTimeMillis()
)

data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)

data class PlatformAuditLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // AUTH, PLUGIN_CONNECT, TOOL_EXECUTE, PERMISSION_GRANT, ERROR
    val details: String,
    val severity: String = "INFO" // INFO, WARN, ERROR
)
