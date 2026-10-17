package com.example.model

enum class ForgeIntelligence {
    MEDIUM,
    HIGH,
    MAX,
    ULTIMATE_USAGE
}

enum class ForgeModelProvider(
    val id: String,
    val displayName: String,
    val providerName: String,
    val iconName: String,
    val defaultRole: String
) {
    GEMINI_3_5_FLASH("gemini-3.5-flash", "Gemini 3.5 Flash", "Google", "gemini", "Planner & Synthesizer"),
    GEMINI_3_1_PRO("gemini-3.1-pro-preview", "Gemini 3.1 Pro", "Google", "gemini", "Chief Architect & Judge"),
    GPT_4O("gpt-4o", "GPT-4o", "OpenAI", "openai", "Deep Solver"),
    CLAUDE_3_7_SONNET("claude-3-7-sonnet", "Claude 3.7 Sonnet", "Anthropic", "anthropic", "Code Reviewer"),
    DEEPSEEK_R1("deepseek-r1", "DeepSeek-R1", "DeepSeek", "deepseek", "Reasoning & Math")
}

data class ForgeModelCallResult(
    val modelId: String,
    val modelName: String,
    val provider: String,
    val role: String,
    val status: String, // "SUCCESS", "FAILED", "SKIPPED", "RATE_LIMITED"
    val output: String,
    val reasoning: String? = null,
    val latencyMs: Long = 0,
    val tokensEstimated: Int = 0
)

data class ForgeReviewItem(
    val reviewerModel: String,
    val targetModel: String,
    val feedback: String,
    val score: Int // 1 to 10
)

data class ForgeRunSummary(
    val intelligence: ForgeIntelligence,
    val totalModelsUsed: Int,
    val configuredModelsCount: Int,
    val estimatedUsage: String, // "Low", "Medium", "High"
    val limitProtection: Boolean,
    val roundsExecuted: Int,
    val modelResults: List<ForgeModelCallResult> = emptyList(),
    val crossReviews: List<ForgeReviewItem> = emptyList(),
    val consensusReached: Boolean = true,
    val finalSynthesis: String = ""
)
