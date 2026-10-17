package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    chatViewModel: com.example.viewmodel.ChatViewModel? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val user by viewModel.repository.currentUser.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val apiKeyOverride by viewModel.apiKeyOverride.collectAsState()

    val personalizationState = chatViewModel?.personalization?.collectAsState()
    val currentStyle = personalizationState?.value?.responseStyle ?: "Concise & Direct"
    val compressionEnabled = personalizationState?.value?.contextCompressionEnabled ?: true
    val secondaryKeyState = chatViewModel?.secondaryApiKey?.collectAsState()
    val dailyRequestState = chatViewModel?.dailyRequestCount?.collectAsState()

    var secondaryApiKeyInput by remember { mutableStateOf(secondaryKeyState?.value.orEmpty()) }
    LaunchedEffect(secondaryKeyState?.value) { secondaryApiKeyInput = secondaryKeyState?.value.orEmpty() }
    var userNameInput by remember { mutableStateOf(user.name) }
    var userEmailInput by remember { mutableStateOf(user.email) }
    var apiKeyInput by remember { mutableStateOf(apiKeyOverride) }
    var showSavedKeyNotice by remember { mutableStateOf(false) }

    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System & AI Configuration", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
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
            // User Profile Section
            Text("USER IDENTITY & SESSION", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = userNameInput,
                        onValueChange = { userNameInput = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth().testTag("user_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userEmailInput,
                        onValueChange = { userEmailInput = it },
                        label = { Text("Account Email") },
                        modifier = Modifier.fillMaxWidth().testTag("user_email_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.repository.updateUser(userNameInput, userEmailInput) },
                        modifier = Modifier.align(Alignment.End).testTag("save_profile_button")
                    ) {
                        Text("Update Profile")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Gemini API Model Section
            Text("GEMINI ORCHESTRATION ENGINE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Model Selection", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("gemini-3.5-flash", "gemini-3.1-pro-preview").forEach { m ->
                            FilterChip(
                                selected = selectedModel == m,
                                onClick = { viewModel.setSelectedModel(m) },
                                label = { Text(m, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Temperature: ${String.format("%.1f", temperature)}", fontWeight = FontWeight.SemiBold)
                        Text(if (temperature < 0.5f) "Precise / Deterministic" else "Creative", style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = temperature,
                        onValueChange = { viewModel.setTemperature(it) },
                        valueRange = 0.0f..1.0f,
                        steps = 10,
                        modifier = Modifier.testTag("temperature_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Custom Gemini API Key Override", fontWeight = FontWeight.Bold)
                    Text(
                        "Leave blank to use environment default injected via Google AI Studio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth().testTag("api_key_override_input"),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showSavedKeyNotice) {
                            Text("Key saved successfully!", color = Color(0xFF10B981), style = MaterialTheme.typography.labelSmall)
                        } else Spacer(modifier = Modifier.width(1.dp))

                        Button(
                            onClick = {
                                viewModel.setApiKeyOverride(apiKeyInput.trim())
                                showSavedKeyNotice = true
                            },
                            modifier = Modifier.testTag("save_api_key_button")
                        ) {
                            Text("Save Key")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Secondary API Key (Automatic Fallback)", fontWeight = FontWeight.Bold)
                    Text("Used when the primary key reports quota exhaustion. Stored encrypted on this device for the current account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = secondaryApiKeyInput, onValueChange = { secondaryApiKeyInput = it }, placeholder = { Text("Optional secondary Gemini key") }, modifier = Modifier.fillMaxWidth().testTag("secondary_api_key_input"), shape = RoundedCornerShape(8.dp), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { chatViewModel?.validateAndSaveSecondaryApiKey(secondaryApiKeyInput.trim()) }, modifier = Modifier.fillMaxWidth().testTag("save_secondary_api_key_button")) { Text("Verify & Save Secondary Key") }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Daily Request Tracker (this device)", fontWeight = FontWeight.Bold)
                    Text("${dailyRequestState?.value ?: 0} requests started today", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { ((dailyRequestState?.value ?: 0).coerceAtLeast(0) / 50f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("daily_usage_progress"))
                    Text("Progress is a local visual reference (50 requests), not the provider daily limit or remaining quota. Gemini limits vary by model and project.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Token Optimization & Response Directness (Headroom & Caveman inspired)
            Text("TOKEN OPTIMIZATION & RESPONSE STYLE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("token_optimization_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Response Directness (Caveman)", fontWeight = FontWeight.Bold)
                    Text(
                        "Controls verbosity and filler phrasing while preserving technical depth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Pair("Concise & Direct", "style_chip_concise"),
                            Pair("Balanced", "style_chip_balanced"),
                            Pair("Detailed & Comprehensive", "style_chip_detailed")
                        ).forEach { (styleName, tag) ->
                            FilterChip(
                                selected = currentStyle == styleName,
                                onClick = {
                                    chatViewModel?.setResponseStyle(styleName)
                                },
                                label = { Text(styleName, fontSize = 11.sp) },
                                modifier = Modifier.testTag(tag)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Context & Tool Compression", fontWeight = FontWeight.Bold)
                            Text(
                                "Headroom-inspired Compress-Cache-Retrieve: compacts older conversation turns and large tool outputs while caching raw details.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = compressionEnabled,
                            onCheckedChange = { isChecked ->
                                chatViewModel?.setContextCompressionEnabled(isChecked)
                            },
                            modifier = Modifier.testTag("context_compression_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Security & Isolation Specifications
            Text("SECURITY & ZERO-TRUST POLICY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("• Server-Side Tool Isolation", fontWeight = FontWeight.Bold)
                    Text("External webhooks and OAuth refresh flows run isolated from client memory.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Sensitive Tool Confirmation Gate", fontWeight = FontWeight.Bold)
                    Text("Dangerous operations (releases, file deletion, email transmission) pause and mandate explicit user approval.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Granular OAuth Permission Scopes", fontWeight = FontWeight.Bold)
                    Text("Every plugin requests minimal least-privilege tokens. Users can revoke individual permissions in the Store at any time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
