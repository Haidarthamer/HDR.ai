package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuthType
import com.example.model.PluginPermission
import com.example.viewmodel.ActiveTab
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPluginDevScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var pluginId by remember { mutableStateOf("custom_weather") }
    var pluginName by remember { mutableStateOf("Live Weather API") }
    var pluginDesc by remember { mutableStateOf("Real-time weather station metrics and forecast querying") }
    var apiEndpoint by remember { mutableStateOf("https://api.weather-cloud.internal/v1") }
    var authType by remember { mutableStateOf(AuthType.API_KEY) }
    var toolsJson by remember {
        mutableStateOf(
            """[
  {
    "name": "get_forecast",
    "description": "Fetch weather forecast for city",
    "isDangerous": false,
    "requiredPermission": "weather.read"
  },
  {
    "name": "trigger_alert",
    "description": "Trigger broadcast weather alert banner",
    "isDangerous": true,
    "requiredPermission": "weather.alert"
  }
]""".trimIndent()
        )
    }

    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer / Register Custom Plugin", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.switchTab(ActiveTab.PluginStore) },
                        modifier = Modifier.testTag("back_to_plugin_store_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Custom Cloud Plugin Manifest",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Define an extensible cloud plugin. The manifest will be validated server-side and added to the plugin router.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pluginId,
                onValueChange = { pluginId = it },
                label = { Text("Plugin ID") },
                modifier = Modifier.fillMaxWidth().testTag("custom_plugin_id_input"),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = pluginName,
                onValueChange = { pluginName = it },
                label = { Text("Plugin Display Name") },
                modifier = Modifier.fillMaxWidth().testTag("custom_plugin_name_input"),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = pluginDesc,
                onValueChange = { pluginDesc = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = apiEndpoint,
                onValueChange = { apiEndpoint = it },
                label = { Text("Cloud Webhook / API Endpoint") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Authentication Mechanism", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(AuthType.NONE, AuthType.API_KEY, AuthType.OAUTH2, AuthType.BEARER_TOKEN).forEach { t ->
                    FilterChip(
                        selected = authType == t,
                        onClick = { authType = t },
                        label = { Text(t.name, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Tools JSON Schema", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = toolsJson,
                onValueChange = { toolsJson = it },
                modifier = Modifier.fillMaxWidth().height(160.dp).testTag("custom_tools_json_input"),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (resultMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSuccess) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        text = resultMessage!!,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Button(
                onClick = {
                    val perms = listOf(
                        PluginPermission("custom.read", "Read Access", "Access to execute safe custom read tools", false, true),
                        PluginPermission("custom.write", "Write Access", "Execute mutations", true, false)
                    )
                    val (success, msg) = viewModel.pluginRegistry.registerCustomPlugin(
                        id = pluginId.trim(),
                        name = pluginName.trim(),
                        description = pluginDesc.trim(),
                        endpoint = apiEndpoint.trim(),
                        authType = authType,
                        toolsJson = toolsJson.trim(),
                        permissionsList = perms
                    )
                    isSuccess = success
                    resultMessage = msg
                    if (success) {
                        viewModel.repository.addAuditLog("PLUGIN_REGISTER", "Registered custom plugin: $pluginName ($pluginId)")
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("register_plugin_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Validate & Register Plugin")
            }
        }
    }
}
