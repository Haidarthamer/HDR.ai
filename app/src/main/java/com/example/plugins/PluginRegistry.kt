package com.example.plugins

import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class PluginRegistry {
    private val providers = mutableMapOf<String, PluginProvider>()
    private val _pluginsState = MutableStateFlow<List<PluginManifest>>(emptyList())
    val pluginsState: StateFlow<List<PluginManifest>> = _pluginsState.asStateFlow()

    init {
        // Register core built-in cloud plugins
        registerProvider(GitHubPlugin())
        registerProvider(WebSearchPlugin())
        registerProvider(GmailPlugin())
        registerProvider(FilesPlugin())
        registerProvider(GoogleWorkspacePlugin())

        // Register official expanded integrations
        registerProvider(HiggsfieldPlugin())
        registerProvider(SlackPlugin())
        registerProvider(DiscordPlugin())
        registerProvider(NotionPlugin())
        registerProvider(DropboxPlugin())
        registerProvider(OneDrivePlugin())
        registerProvider(MicrosoftOutlookPlugin())
        registerProvider(MicrosoftCalendarPlugin())
        registerProvider(TrelloPlugin())
        registerProvider(JiraPlugin())
        registerProvider(LinearPlugin())
        registerProvider(AsanaPlugin())
        registerProvider(GitLabPlugin())
        registerProvider(BitbucketPlugin())
        registerProvider(FigmaPlugin())
        registerProvider(CanvaPlugin())
        registerProvider(YouTubePlugin())
        registerProvider(SpotifyPlugin())
    }

    @Synchronized
    fun registerProvider(provider: PluginProvider) {
        providers[provider.manifest.id] = provider
        updateState()
    }

    @Synchronized
    fun getProvider(pluginId: String): PluginProvider? = providers[pluginId]

    @Synchronized
    fun getEnabledAndConnectedPlugins(): List<PluginManifest> {
        return _pluginsState.value.filter { it.isConnected && it.isEnabled }
    }

    @Synchronized
    fun togglePluginEnabled(pluginId: String) {
        val current = _pluginsState.value.find { it.id == pluginId } ?: return
        val updated = current.copy(isEnabled = !current.isEnabled)
        replacePlugin(updated)
    }

    @Synchronized
    fun connectPlugin(pluginId: String, accountName: String? = null) {
        val current = _pluginsState.value.find { it.id == pluginId } ?: return
        // Grant non-dangerous permissions by default on connect
        val updatedPerms = current.permissions.map {
            if (!it.isDangerous) it.copy(isGranted = true) else it
        }
        val updated = current.copy(
            isConnected = true,
            connectionAccount = accountName ?: "${pluginId}_user@nexus.cloud",
            permissions = updatedPerms
        )
        replacePlugin(updated)
    }

    @Synchronized
    fun disconnectPlugin(pluginId: String) {
        val current = _pluginsState.value.find { it.id == pluginId } ?: return
        val updatedPerms = current.permissions.map { it.copy(isGranted = false) }
        val updated = current.copy(
            isConnected = false,
            connectionAccount = null,
            permissions = updatedPerms
        )
        replacePlugin(updated)
    }

    @Synchronized
    fun updatePermission(pluginId: String, permissionId: String, granted: Boolean) {
        val current = _pluginsState.value.find { it.id == pluginId } ?: return
        val updatedPerms = current.permissions.map {
            if (it.id == permissionId) it.copy(isGranted = granted) else it
        }
        replacePlugin(current.copy(permissions = updatedPerms))
    }

    @Synchronized
    fun setAutoApproveDangerous(pluginId: String, enabled: Boolean) {
        val current = _pluginsState.value.find { it.id == pluginId } ?: return
        replacePlugin(current.copy(autoApproveDangerous = enabled))
    }

    @Synchronized
    fun registerCustomPlugin(
        id: String,
        name: String,
        description: String,
        endpoint: String,
        authType: AuthType,
        toolsJson: String,
        permissionsList: List<PluginPermission>
    ): Pair<Boolean, String> {
        try {
            if (id.isBlank() || name.isBlank() || endpoint.isBlank()) {
                return Pair(false, "ID, name and API endpoint cannot be empty")
            }
            if (_pluginsState.value.any { it.id == id }) {
                return Pair(false, "Plugin ID '$id' is already registered")
            }

            val parsedTools = mutableListOf<PluginTool>()
            val toolsArray = org.json.JSONArray(toolsJson)
            for (i in 0 until toolsArray.length()) {
                val tObj = toolsArray.getJSONObject(i)
                val tName = tObj.getString("name")
                val tDesc = tObj.optString("description", "Custom tool")
                val isDangerous = tObj.optBoolean("isDangerous", false)
                val reqPerm = if (tObj.has("requiredPermission")) tObj.getString("requiredPermission") else null

                parsedTools.add(
                    PluginTool(
                        id = "$id.$tName",
                        name = tName,
                        description = tDesc,
                        requiredPermissionId = reqPerm,
                        isDangerous = isDangerous
                    )
                )
            }

            val manifest = PluginManifest(
                id = id,
                name = name,
                version = "1.0.0",
                description = description,
                author = "Community Developer",
                iconEmoji = "⚡",
                authenticationType = authType,
                permissions = permissionsList,
                tools = parsedTools,
                apiEndpoint = endpoint,
                isConnected = true,
                isEnabled = true,
                isCustom = true,
                connectionAccount = "custom_dev@endpoint"
            )

            val customProvider = object : PluginProvider {
                override val manifest: PluginManifest = manifest
                override suspend fun execute(toolName: String, args: Map<String, String>, token: String?): ToolCallResult {
                    // Custom plugin cloud webhook execution
                    return ToolCallResult(
                        success = true,
                        data = "Executed custom tool '$toolName' on endpoint $endpoint with args: $args"
                    )
                }
            }

            registerProvider(customProvider)
            return Pair(true, "Successfully registered plugin '$name'")
        } catch (e: Exception) {
            return Pair(false, "Failed to parse custom plugin manifest: ${e.message}")
        }
    }

    private fun replacePlugin(updated: PluginManifest) {
        val oldProvider = providers[updated.id]
        if (oldProvider != null) {
            providers[updated.id] = object : PluginProvider {
                override val manifest: PluginManifest = updated
                override suspend fun execute(toolName: String, args: Map<String, String>, token: String?): ToolCallResult {
                    return oldProvider.execute(toolName, args, token)
                }
            }
        }
        updateState()
    }

    private fun updateState() {
        _pluginsState.value = providers.values.map { it.manifest }.sortedBy { it.name }
    }
}
