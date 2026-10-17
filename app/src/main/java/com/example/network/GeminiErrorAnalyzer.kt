package com.example.network

import android.util.Log
import org.json.JSONObject

data class GeminiErrorAnalysis(
    val httpCode: Int,
    val isQuotaExhausted: Boolean,
    val isRateLimit: Boolean,
    val retryDelaySeconds: Long? = null,
    val safeErrorMessage: String,
    val category: String,
    val canRetry: Boolean,
    val rawDetails: String? = null
)

object GeminiErrorAnalyzer {

    fun analyze(throwable: Throwable): GeminiErrorAnalysis {
        if (throwable !is retrofit2.HttpException) {
            val isTimeout = throwable is java.net.SocketTimeoutException
            val isNetwork = throwable is java.net.UnknownHostException ||
                    throwable is java.net.ConnectException ||
                    throwable is java.io.IOException

            val category = if (isNetwork || isTimeout) "NETWORK_ERROR" else "PROVIDER_ERROR"
            val msg = when {
                isTimeout -> "Connection timed out contacting Gemini. Please tap Retry."
                isNetwork -> "Unable to connect to Gemini servers. Please check your network and tap Retry."
                else -> "Unable to generate response: ${throwable.localizedMessage ?: "Unexpected error"}. Please tap Retry."
            }

            return GeminiErrorAnalysis(
                httpCode = -1,
                isQuotaExhausted = false,
                isRateLimit = false,
                retryDelaySeconds = null,
                safeErrorMessage = msg,
                category = category,
                canRetry = true,
                rawDetails = throwable.message
            )
        }

        val code = throwable.code()
        if (code == 401 || code == 403) {
            return GeminiErrorAnalysis(
                httpCode = code,
                isQuotaExhausted = false,
                isRateLimit = false,
                retryDelaySeconds = null,
                safeErrorMessage = "Gemini authentication failed (HTTP $code). Please check GEMINI_API_KEY in Secrets panel.",
                category = "PROVIDER_AUTH_ERROR",
                canRetry = true,
                rawDetails = "HTTP $code Authentication Error"
            )
        }

        if (code == 429) {
            var retryAfterHeader: Long? = null
            try {
                val headerVal = throwable.response()?.headers()?.get("Retry-After")
                if (!headerVal.isNullOrBlank()) {
                    retryAfterHeader = headerVal.trim().toLongOrNull()
                }
            } catch (_: Exception) {}

            var rawBody = ""
            try {
                rawBody = throwable.response()?.errorBody()?.string().orEmpty()
            } catch (_: Exception) {}

            var status = ""
            var message = ""
            var isQuotaExhausted = false
            var retryDelaySeconds: Long? = retryAfterHeader
            var quotaMetric = ""
            var quotaId = ""

            if (rawBody.isNotBlank()) {
                try {
                    val json = JSONObject(rawBody)
                    val errorObj = json.optJSONObject("error")
                    if (errorObj != null) {
                        status = errorObj.optString("status", "")
                        message = errorObj.optString("message", "")
                        val detailsArray = errorObj.optJSONArray("details")
                        if (detailsArray != null) {
                            for (i in 0 until detailsArray.length()) {
                                val d = detailsArray.optJSONObject(i) ?: continue
                                val type = d.optString("@type", "")
                                if (type.contains("RetryInfo")) {
                                    val delayStr = d.optString("retryDelay", "")
                                    val match = Regex("""^(\d+)""").find(delayStr)
                                    if (match != null) {
                                        retryDelaySeconds = match.groupValues[1].toLongOrNull()
                                    }
                                }
                                if (type.contains("QuotaFailure")) {
                                    val violations = d.optJSONArray("violations")
                                    if (violations != null) {
                                        for (j in 0 until violations.length()) {
                                            val v = violations.optJSONObject(j) ?: continue
                                            quotaId = v.optString("quotaId", "")
                                            quotaMetric = v.optString("quotaMetric", "")
                                            if (quotaId.contains("PerDay", ignoreCase = true) ||
                                                quotaId.contains("FreeTier", ignoreCase = true) ||
                                                quotaMetric.contains("free_tier", ignoreCase = true) ||
                                                quotaMetric.contains("PerDay", ignoreCase = true)
                                            ) {
                                                isQuotaExhausted = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val lowerMsg = message.lowercase()
            if (lowerMsg.contains("quota exceeded") ||
                lowerMsg.contains("exceeded your current quota") ||
                lowerMsg.contains("billing details") ||
                (retryDelaySeconds != null && retryDelaySeconds > 60)
            ) {
                isQuotaExhausted = true
            }

            Log.w("NexusAI_RateLimit", "Gemini 429 detail: isQuotaExhausted=$isQuotaExhausted, retryDelay=${retryDelaySeconds}s, quotaMetric=$quotaMetric, quotaId=$quotaId, msg=${message.take(120)}")

            if (isQuotaExhausted) {
                val safeMsg = "Gemini free-tier daily request quota has been exceeded (20 requests/day per model). Please upgrade billing at ai.google.dev or add a custom API key in the Secrets panel."
                return GeminiErrorAnalysis(
                    httpCode = 429,
                    isQuotaExhausted = true,
                    isRateLimit = true,
                    retryDelaySeconds = retryDelaySeconds,
                    safeErrorMessage = safeMsg,
                    category = "QUOTA_EXHAUSTED",
                    canRetry = false,
                    rawDetails = "QuotaId: $quotaId, Metric: $quotaMetric, Delay: ${retryDelaySeconds}s"
                )
            } else {
                val safeMsg = if (retryDelaySeconds != null && retryDelaySeconds in 1..60) {
                    "Gemini requests-per-minute limit reached. Please wait ${retryDelaySeconds}s and tap Retry."
                } else {
                    "Gemini requests-per-minute limit reached. Please wait a moment and tap Retry."
                }
                return GeminiErrorAnalysis(
                    httpCode = 429,
                    isQuotaExhausted = false,
                    isRateLimit = true,
                    retryDelaySeconds = retryDelaySeconds,
                    safeErrorMessage = safeMsg,
                    category = "RATE_LIMIT_ERROR",
                    canRetry = true,
                    rawDetails = "RPM limit. Retry delay: ${retryDelaySeconds}s"
                )
            }
        }

        val safeMsg = "Gemini service error (HTTP $code). Please tap Retry to re-send."
        return GeminiErrorAnalysis(
            httpCode = code,
            isQuotaExhausted = false,
            isRateLimit = false,
            retryDelaySeconds = null,
            safeErrorMessage = safeMsg,
            category = "PROVIDER_ERROR",
            canRetry = true,
            rawDetails = "HTTP $code"
        )
    }
}
