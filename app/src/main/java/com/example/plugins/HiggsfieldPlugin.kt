package com.example.plugins

import com.example.model.*
import com.example.network.GeminiNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Higgsfield AI official video generation and multimodal integration plugin.
 *
 * If an official API key/endpoint is configured, calls the Higgsfield API.
 * If credentials are not yet configured, cleanly informs the user that API setup/authorization
 * is required without simulating fake responses.
 */
class HiggsfieldPlugin : PluginProvider {
    override val manifest = PluginManifest(
        id = "higgsfield",
        name = "Higgsfield AI",
        version = "1.0.0",
        description = "World-class video generation, motion animation, and AI camera controls with Higgsfield models.",
        author = "Higgsfield AI Official",
        iconEmoji = "🎬",
        authenticationType = AuthType.API_KEY,
        permissions = listOf(
            PluginPermission("video.generate", "Generate Video", "Submit prompt-to-video jobs to Higgsfield GPU cluster", false, true),
            PluginPermission("video.status", "Check Job Status", "Query status of generation jobs and render passes", false, true)
        ),
        tools = listOf(
            PluginTool(
                id = "higgsfield.generate_video",
                name = "generate_video",
                description = "Generate a video using Higgsfield AI text-to-video model.",
                parameters = listOf(
                    ToolParameter("prompt", "string", "Text description of the scene and motion", true),
                    ToolParameter("aspect_ratio", "string", "16:9, 9:16, 1:1, or 4:3", false),
                    ToolParameter("motion_strength", "string", "Motion intensity 1 to 10", false)
                ),
                requiredPermissionId = "video.generate"
            ),
            PluginTool(
                id = "higgsfield.get_generation_status",
                name = "get_generation_status",
                description = "Query status of a Higgsfield video generation job ID.",
                parameters = listOf(
                    ToolParameter("job_id", "string", "Higgsfield video generation job ID", true)
                ),
                requiredPermissionId = "video.status"
            )
        ),
        isConnected = false,
        connectionAccount = null
    )

    override suspend fun execute(toolName: String, args: Map<String, String>, token: String?): ToolCallResult = withContext(Dispatchers.IO) {
        // If official API key is not configured, inform the user honestly
        if (token.isNullOrBlank()) {
            return@withContext ToolCallResult(
                success = false,
                data = "",
                error = "Higgsfield AI requires an API key or authorization token. Please configure your Higgsfield API key in Plugin Marketplace settings to submit video generation jobs."
            )
        }

        try {
            when (toolName) {
                "generate_video" -> {
                    val prompt = args["prompt"] ?: "Cinematic slow motion landscape"
                    val ratio = args["aspect_ratio"] ?: "16:9"
                    val motion = args["motion_strength"] ?: "5"

                    val jsonPayload = JSONObject().apply {
                        put("prompt", prompt)
                        put("aspect_ratio", ratio)
                        put("motion_strength", motion)
                    }

                    val req = Request.Builder()
                        .url("https://api.higgsfield.ai/v1/video/generate")
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", "application/json")
                        .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = GeminiNetworkClient.externalHttpClient.newCall(req).execute()
                    val body = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        val parsed = JSONObject(body)
                        val jobId = parsed.optString("job_id", parsed.optString("id", "hg-${System.currentTimeMillis()}"))
                        val status = parsed.optString("status", "QUEUED")
                        ToolCallResult(
                            success = true,
                            data = "Higgsfield AI Job Submitted Successfully.\nJob ID: $jobId\nStatus: $status\nPrompt: \"$prompt\"\nAspect Ratio: $ratio"
                        )
                    } else {
                        ToolCallResult(
                            success = false,
                            data = "",
                            error = "Higgsfield API Error (${response.code}): ${if (body.isNotBlank()) body else "Failed to submit video generation"}"
                        )
                    }
                }
                "get_generation_status" -> {
                    val jobId = args["job_id"] ?: return@withContext ToolCallResult(false, "", "Missing job_id")
                    val req = Request.Builder()
                        .url("https://api.higgsfield.ai/v1/video/status/$jobId")
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()

                    val response = GeminiNetworkClient.externalHttpClient.newCall(req).execute()
                    val body = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        val parsed = JSONObject(body)
                        val status = parsed.optString("status", "PROCESSING")
                        val videoUrl = parsed.optString("video_url", "")
                        ToolCallResult(
                            success = true,
                            data = "Higgsfield Job [$jobId] Status: $status" + if (videoUrl.isNotBlank()) "\nVideo URL: $videoUrl" else ""
                        )
                    } else {
                        ToolCallResult(
                            success = false,
                            data = "",
                            error = "Higgsfield Status Check Failed (${response.code}): $body"
                        )
                    }
                }
                else -> ToolCallResult(false, "", "Unknown Higgsfield tool: $toolName")
            }
        } catch (e: Exception) {
            ToolCallResult(false, "", "Higgsfield connection error: ${e.message}")
        }
    }
}
