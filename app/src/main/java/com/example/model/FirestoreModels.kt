package com.example.model

import com.google.firebase.Timestamp

data class AppUser(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null
)

enum class ChatAssistantMode {
    NORMAL,
    RESEARCH,
    AGENT,
    FORGE
}

data class FirestoreConversation(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val model: String = "gemini-3.5-flash",
    val projectId: String? = null,
    val mode: String = "NORMAL",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val messageCount: Int = 0
)

data class FirestoreMessage(
    val id: String = "",
    val conversationId: String = "",
    val userId: String = "",
    val role: String = "user", // "user" | "assistant" | "system"
    val content: String = "",
    val createdAt: Timestamp? = null,
    val toolExecutions: List<Map<String, String>> = emptyList(),
    val citations: List<Map<String, String>> = emptyList(),
    val artifactId: String? = null
)

data class FirestoreMemory(
    val id: String = "",
    val userId: String = "",
    val key: String = "",
    val value: String = "",
    val category: String = "FACT",
    val projectId: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val sourceConversationId: String? = null,
    val confidence: Float = 1.0f
)

data class FirestoreProject(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val customInstructions: String = "",
    val enabledPlugins: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class FirestoreArtifact(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val type: String = "code", // code, html, markdown, report, json
    val content: String = "",
    val language: String = "kotlin",
    val version: Int = 1,
    val versions: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class FirestoreAutomation(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val prompt: String = "",
    val schedule: String = "Daily at 8:00 AM",
    val enabled: Boolean = true,
    val allowedPlugins: List<String> = emptyList(),
    val lastRun: Timestamp? = null,
    val lastStatus: String = "Ready",
    val executionHistory: List<Map<String, String>> = emptyList(),
    val createdAt: Timestamp? = null
)

data class FirestoreActivityItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val details: String = "",
    val category: String = "TOOL", // TOOL, RESEARCH, AGENT, AUTOMATION, PLUGIN
    val status: String = "SUCCESS", // SUCCESS, FAILED, RUNNING
    val timestamp: Timestamp? = null
)

data class FirestorePluginConnection(
    val pluginId: String = "",
    val userId: String = "",
    val account: String? = null,
    val connected: Boolean = false,
    val grantedPermissions: List<String> = emptyList(),
    val connectedAt: Timestamp? = null
)

data class UserPersonalization(
    val assistantName: String = "NexusAI",
    val personality: String = "Helpful, concise, and expert",
    val responseStyle: String = "Concise & Structured",
    val preferredLanguage: String = "English",
    val customInstructions: String = "",
    val memoryEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val contextCompressionEnabled: Boolean = true
)
