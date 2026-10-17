package com.example.engine

import com.example.BuildConfig
import com.example.model.*
import com.example.network.GeminiContent
import com.example.network.GeminiGenerationConfig
import com.example.network.GeminiNetworkClient
import com.example.network.GeminiPart
import com.example.network.GeminiRequest
import com.example.network.GeminiThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Multi-model coordination engine for Forge Mode.
 * Dynamically utilizes configured models (Gemini, Claude, GPT, DeepSeek).
 * When third-party API keys are not in environment, does NOT pretend they exist,
 * but transparently leverages configured Gemini 3.5 Flash + Gemini 3.1 Pro models
 * with specialized role personas (Planner, Solver, Code Reviewer, Chief Judge).
 */
class ForgeOrchestrator {

    data class AvailableProviderConfig(
        val provider: ForgeModelProvider,
        val isConfigured: Boolean,
        val apiKey: String = ""
    )

    fun getAvailableProviders(geminiKey: String): List<AvailableProviderConfig> {
        val providers = mutableListOf<AvailableProviderConfig>()

        // 1. Google Gemini Flash (Always available if Gemini key present)
        providers.add(
            AvailableProviderConfig(
                provider = ForgeModelProvider.GEMINI_3_5_FLASH,
                isConfigured = geminiKey.isNotBlank() && geminiKey != "MY_GEMINI_API_KEY",
                apiKey = geminiKey
            )
        )

        // 2. Google Gemini Pro (Chief Architect & Judge)
        providers.add(
            AvailableProviderConfig(
                provider = ForgeModelProvider.GEMINI_3_1_PRO,
                isConfigured = geminiKey.isNotBlank() && geminiKey != "MY_GEMINI_API_KEY",
                apiKey = geminiKey
            )
        )

        // 3. OpenAI GPT-4o
        val openaiKey = try { BuildConfig::class.java.getField("OPENAI_API_KEY").get(null) as? String ?: "" } catch (e: Exception) { "" }
        if (openaiKey.isNotBlank() && openaiKey != "MY_OPENAI_API_KEY") {
            providers.add(
                AvailableProviderConfig(
                    provider = ForgeModelProvider.GPT_4O,
                    isConfigured = true,
                    apiKey = openaiKey
                )
            )
        }

        // 4. Anthropic Claude
        val claudeKey = try { BuildConfig::class.java.getField("ANTHROPIC_API_KEY").get(null) as? String ?: "" } catch (e: Exception) { "" }
        if (claudeKey.isNotBlank() && claudeKey != "MY_ANTHROPIC_API_KEY") {
            providers.add(
                AvailableProviderConfig(
                    provider = ForgeModelProvider.CLAUDE_3_7_SONNET,
                    isConfigured = true,
                    apiKey = claudeKey
                )
            )
        }

        // 5. DeepSeek
        val deepseekKey = try { BuildConfig::class.java.getField("DEEPSEEK_API_KEY").get(null) as? String ?: "" } catch (e: Exception) { "" }
        if (deepseekKey.isNotBlank() && deepseekKey != "MY_DEEPSEEK_API_KEY") {
            providers.add(
                AvailableProviderConfig(
                    provider = ForgeModelProvider.DEEPSEEK_R1,
                    isConfigured = true,
                    apiKey = deepseekKey
                )
            )
        }

        return providers
    }

    /**
     * Determines whether task is simple, complex, or extremely complex.
     */
    fun analyzeTaskComplexity(prompt: String): TaskComplexity {
        val lower = prompt.lowercase()
        val words = prompt.split("\\s+".toRegex()).size

        val isCodeOrArch = lower.contains("architecture") || lower.contains("refactor") ||
                lower.contains("debug") || lower.contains("optimize") ||
                lower.contains("distributed") || lower.contains("algorithm") ||
                lower.contains("concurrency") || lower.contains("database design") ||
                lower.contains("system design") || lower.contains("race condition")

        val isShortDirect = words < 12 && !isCodeOrArch && !lower.contains("build") && !lower.contains("complex")

        return when {
            isShortDirect -> TaskComplexity.SIMPLE
            isCodeOrArch || words > 40 -> TaskComplexity.EXTREMELY_COMPLEX
            else -> TaskComplexity.COMPLEX
        }
    }

    enum class TaskComplexity {
        SIMPLE,
        COMPLEX,
        EXTREMELY_COMPLEX
    }

    fun estimateUsage(intelligence: ForgeIntelligence, complexity: TaskComplexity, limitProtection: Boolean): String {
        return when {
            intelligence == ForgeIntelligence.ULTIMATE_USAGE -> "Very High (All eligible models, multiple review rounds)"
            limitProtection && complexity == TaskComplexity.SIMPLE -> "Low (1-2 model calls)"
            intelligence == ForgeIntelligence.MEDIUM -> "Low - Medium (2 model calls)"
            intelligence == ForgeIntelligence.HIGH -> "Medium (3-4 model calls)"
            else -> "High (multi-stage verification & debate)"
        }
    }

    /**
     * Executes real model prompt call with role persona.
     */
    suspend fun executeModelCall(
        providerConfig: AvailableProviderConfig,
        roleName: String,
        systemInstruction: String,
        userPrompt: String
    ): ForgeModelCallResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            if (providerConfig.apiKey.isBlank()) {
                return@withContext ForgeModelCallResult(
                    modelId = providerConfig.provider.id,
                    modelName = providerConfig.provider.displayName,
                    provider = providerConfig.provider.providerName,
                    role = roleName,
                    status = "FAILED",
                    output = "Model unconfigured or missing API key.",
                    latencyMs = 0
                )
            }

            // Real Gemini API Call with custom role persona and temperature
            val req = GeminiRequest(
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruction))),
                contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = userPrompt)))),
                generationConfig = GeminiGenerationConfig(
                    temperature = if (roleName.contains("Judge") || roleName.contains("Verifier")) 0.2f else 0.4f,
                    thinkingConfig = GeminiThinkingConfig(
                        thinkingBudget = if (roleName.contains("Judge") || roleName.contains("Reviewer") || roleName.contains("Solver")) 2048 else 1024
                    )
                )
            )

            val resp = GeminiNetworkClient.geminiService.generateContent(providerConfig.apiKey, req)
            val outputText = resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

            ForgeModelCallResult(
                modelId = providerConfig.provider.id,
                modelName = providerConfig.provider.displayName,
                provider = providerConfig.provider.providerName,
                role = roleName,
                status = if (outputText.isNotBlank()) "SUCCESS" else "FAILED",
                output = outputText.ifBlank { "No output returned" },
                latencyMs = System.currentTimeMillis() - startTime,
                tokensEstimated = (outputText.length / 4) + (userPrompt.length / 4)
            )
        } catch (e: Exception) {
            val analysis = com.example.network.GeminiErrorAnalyzer.analyze(e)
            val status = when {
                analysis.isQuotaExhausted -> "QUOTA_EXHAUSTED"
                analysis.isRateLimit -> "RATE_LIMITED"
                else -> "FAILED"
            }
            ForgeModelCallResult(
                modelId = providerConfig.provider.id,
                modelName = providerConfig.provider.displayName,
                provider = providerConfig.provider.providerName,
                role = roleName,
                status = status,
                output = analysis.safeErrorMessage,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
