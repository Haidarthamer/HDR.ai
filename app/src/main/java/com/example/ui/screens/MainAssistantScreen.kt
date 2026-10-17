package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.auth.AuthenticationManager
import com.example.calls.CallScheduleController
import android.widget.Toast
import com.example.model.*
import com.example.viewmodel.AppDestination
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAssistantScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val conversations by viewModel.conversations.collectAsState()
    val activeConvId by viewModel.activeConversationId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val currentStreamingText by viewModel.currentStreamingText.collectAsState()
    val statusNotice by viewModel.statusNotice.collectAsState()
    val researchProgress by viewModel.researchProgress.collectAsState()
    val activeTools by viewModel.activeToolRecords.collectAsState()
    val pendingApproval by viewModel.pendingApproval.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val artifacts by viewModel.artifacts.collectAsState()
    val forgeIntelligence by viewModel.forgeIntelligence.collectAsState()
    val limitProtection by viewModel.limitProtection.collectAsState()
    val isUltimateForge by viewModel.isUltimateForge.collectAsState()
    val isGuest by AuthenticationManager.isGuestMode.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var drawerOpen by remember { mutableStateOf(false) }
    var voiceListening by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Smooth auto-scroll when streaming or new message
    LaunchedEffect(messages.size, currentStreamingText.length) {
        val totalCount = messages.size + (if (isStreaming) 1 else 0)
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header Bar: Minimal, fast, responsive
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { drawerOpen = true },
                                modifier = Modifier.testTag("open_history_drawer_button")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Drawer")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                val currentTitle = conversations.find { it.id == activeConvId }?.title ?: "NexusAI"
                                Text(
                                    text = currentTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.widthIn(max = 180.dp)
                                )
                                if (activeProject != null) {
                                    Text(
                                        text = "Project: ${activeProject!!.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.navigateTo(AppDestination.Call) },
                                modifier = Modifier.testTag("header_voice_call_button")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Start NexusAI voice call")
                            }
                            IconButton(
                                onClick = { viewModel.startNewConversation() },
                                modifier = Modifier.testTag("header_new_chat_button")
                            ) {
                                Icon(Icons.Default.AddComment, contentDescription = "New Chat")
                            }
                            IconButton(
                                onClick = { viewModel.navigateTo(AppDestination.Artifacts) },
                                modifier = Modifier.testTag("header_artifacts_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (artifacts.isNotEmpty()) {
                                            Badge { Text("${artifacts.size}") }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = "Artifacts")
                                }
                            }
                            IconButton(
                                onClick = { viewModel.navigateTo(AppDestination.PluginStore) },
                                modifier = Modifier.testTag("header_plugins_button")
                            ) {
                                Icon(Icons.Default.Extension, contentDescription = "Plugins")
                            }
                        }
                    }

                    // Guest Mode Banner if active
                    if (isGuest) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.PersonOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Guest Mode • Conversations saved locally",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = { AuthenticationManager.setGuestMode(false) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Sign In", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Mode Switcher Chips (Normal, Deep Research, Agent, Forge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentMode == ChatAssistantMode.NORMAL,
                            onClick = { viewModel.setMode(ChatAssistantMode.NORMAL) },
                            label = { Text("Normal", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        FilterChip(
                            selected = currentMode == ChatAssistantMode.RESEARCH,
                            onClick = { viewModel.setMode(ChatAssistantMode.RESEARCH) },
                            label = { Text("Deep Research", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        FilterChip(
                            selected = currentMode == ChatAssistantMode.AGENT,
                            onClick = { viewModel.setMode(ChatAssistantMode.AGENT) },
                            label = { Text("Agent", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.AutoMode, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        FilterChip(
                            selected = currentMode == ChatAssistantMode.FORGE,
                            onClick = { viewModel.setMode(ChatAssistantMode.FORGE) },
                            label = { Text("Forge", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Default.Engineering, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("forge_mode_chip")
                        )
                    }

                    // Forge Mode Configuration Panel
                    AnimatedVisibility(visible = currentMode == ChatAssistantMode.FORGE) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Hardware,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Forge Intelligence",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Limit Protection Switch
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Protect Limits",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = limitProtection,
                                            onCheckedChange = { viewModel.setLimitProtection(it) },
                                            modifier = Modifier.size(36.dp, 20.dp).testTag("protect_limits_switch")
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Intelligence Selector Chips: Medium, High, Max
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        Triple(ForgeIntelligence.MEDIUM, "Medium", "1-2 models"),
                                        Triple(ForgeIntelligence.HIGH, "High", "2-3 models"),
                                        Triple(ForgeIntelligence.MAX, "Max", "Full pipeline")
                                    ).forEach { (intel, label, sub) ->
                                        val isSel = (forgeIntelligence == intel)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.setForgeIntelligence(intel) }
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = sub,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
                                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Real Usage Estimate & Models Summary
                                val estimatedUsageText = when (forgeIntelligence) {
                                    ForgeIntelligence.MEDIUM -> if (limitProtection) "Low (1-2 models)" else "Medium (2 models)"
                                    ForgeIntelligence.HIGH -> "Medium (Planner + Solvers)"
                                    ForgeIntelligence.MAX -> if (limitProtection) "Medium - High" else "High (Full Multi-Model Pipeline)"
                                    ForgeIntelligence.ULTIMATE_USAGE -> "Very High (All Eligible Providers)"
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Forge Mode • Intelligence: ${forgeIntelligence.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Usage: $estimatedUsageText • Limit Protection: ${if (limitProtection) "ON" else "OFF"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = if (limitProtection) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Real-time Status Notice / Deep Research progress banner
            AnimatedVisibility(visible = statusNotice != null || researchProgress != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusNotice ?: (researchProgress?.first ?: "Processing..."),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (researchProgress != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { researchProgress!!.second },
                                modifier = Modifier.fillMaxWidth().height(3.dp)
                            )
                        }
                    }
                }
            }

            // Messages Stream Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp)
            ) {
                if (messages.isEmpty() && !isStreaming) {
                    item {
                        EmptyChatHero(onPromptSelected = { prompt ->
                            viewModel.sendMessage(prompt)
                        })
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubbleItem(
                            message = msg,
                            onRegenerate = {
                                val lastUser = messages.findLast { it.role == "user" }
                                if (lastUser != null) viewModel.sendMessage(lastUser.content)
                            },
                            onOpenArtifact = { artId ->
                                val art = artifacts.find { it.id == artId }
                                viewModel.selectArtifact(art)
                            }
                        )
                    }

                    if (isStreaming) {
                        item {
                            StreamingAssistantBubble(
                                text = currentStreamingText,
                                toolRecords = activeTools,
                                onStop = { viewModel.stopGeneration() }
                            )
                        }
                    }
                }
            }

            // Confirmation sheet for dangerous actions
            if (pendingApproval != null) {
                DangerousActionApprovalSheet(
                    record = pendingApproval!!,
                    onApprove = { viewModel.approveAction() },
                    onReject = { viewModel.rejectAction() }
                )
            }

            // Voice Listening State Bar
            AnimatedVisibility(visible = voiceListening) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Listening... Speak your prompt", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { voiceListening = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close mic", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Bottom Composer (Clean, Floating, with Voice & Modes)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppDestination.Files) },
                        modifier = Modifier.testTag("attach_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Attachment,
                            contentDescription = "Attach Cloud Document",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            val hint = when (currentMode) {
                                ChatAssistantMode.RESEARCH -> "Enter research topic..."
                                ChatAssistantMode.AGENT -> "Assign multi-step task to Agent..."
                                ChatAssistantMode.FORGE -> "Enter complex task for Forge multi-model pipeline..."
                                ChatAssistantMode.NORMAL -> "Message NexusAI..."
                            }
                            Text(hint, fontSize = 14.sp)
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_composer"),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            voiceListening = !voiceListening
                            if (voiceListening) {
                                inputText = "Research the best Android Windows emulators in 2026."
                            }
                        },
                        modifier = Modifier.size(40.dp).testTag("voice_mic_button")
                    ) {
                        Icon(
                            imageVector = if (voiceListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Voice Mode",
                            tint = if (voiceListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    if (isStreaming) {
                        IconButton(
                            onClick = { viewModel.stopGeneration() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.size(44.dp).testTag("stop_generation_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop Generating")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotBlank()) {
                                    val scheduleResult = CallScheduleController.handleMessage(context, text)
                                    if (scheduleResult != null) {
                                        Toast.makeText(context, scheduleResult.message, Toast.LENGTH_LONG).show()
                                    }
                                    viewModel.sendMessage(text)
                                    inputText = ""
                                    voiceListening = false
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.size(44.dp).testTag("send_message_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }

        // Side History & Workspace Navigation Drawer
        if (drawerOpen) {
            ConversationHistoryDrawer(
                conversations = conversations,
                activeId = activeConvId,
                activeProject = activeProject,
                onSelect = {
                    viewModel.selectConversation(it)
                    drawerOpen = false
                },
                onNewChat = {
                    viewModel.startNewConversation()
                    drawerOpen = false
                },
                onDelete = { viewModel.deleteConversation(it) },
                onNavigate = { dest ->
                    viewModel.navigateTo(dest)
                    drawerOpen = false
                },
                onSignOut = {
                    AuthenticationManager.signOut(context, credentialManager, scope)
                },
                onDismiss = { drawerOpen = false }
            )
        }
    }
}

@Composable
fun ChatBubbleItem(
    message: FirestoreMessage,
    onRegenerate: () -> Unit,
    onOpenArtifact: (String) -> Unit
) {
    val isUser = message.role == "user"
    val clipboard = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Tool & Search execution compact cards
        if (message.toolExecutions.isNotEmpty()) {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                message.toolExecutions.forEach { tMap ->
                    val plugin = tMap["plugin"] ?: "Tool"
                    val tool = tMap["tool"] ?: "action"
                    val status = tMap["status"] ?: "SUCCESS"
                    val output = tMap["output"] ?: ""
                    val isSearch = plugin.contains("Web", ignoreCase = true) || tool.contains("search", ignoreCase = true)

                    var isExpanded by remember { mutableStateOf(false) }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .padding(vertical = 3.dp)
                            .clickable { isExpanded = !isExpanded }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSearch) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Web Search",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "✓",
                                        color = Color(0xFF10B981),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (message.citations.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "· ${message.citations.size} sources",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$plugin · $tool",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF10B981)
                                    )
                                }

                                if (isSearch) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // Expandable real query & real verified sources
                            if (isSearch && isExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                if (output.isNotBlank()) {
                                    Text(
                                        text = output.take(240),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (message.citations.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Verified Sources:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    message.citations.forEach { cit ->
                                        val cTitle = cit["title"] ?: "Source"
                                        val cUrl = cit["url"] ?: ""
                                        Text(
                                            text = "• $cTitle ($cUrl)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val isError = message.role == "error"

        if (isError) {
            val isQuotaExhausted = message.content.contains("[QUOTA_EXHAUSTED]")
            val isRateLimit = message.content.contains("[RATE_LIMIT_ERROR]")
            val title = when {
                isQuotaExhausted -> "Daily Free-Tier Quota Exceeded"
                isRateLimit -> "Rate Limit Reached (RPM)"
                message.content.contains("[PROVIDER_AUTH_ERROR]") -> "Authentication Error"
                message.content.contains("[NETWORK_ERROR]") -> "Network Connection Issue"
                else -> "Generation Issue"
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .testTag("chat_error_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error Notice",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        lineHeight = 20.sp
                    )
                    if (isQuotaExhausted) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tip: You can add your own billing-enabled API key in the AI Studio Secrets panel to remove free tier restrictions.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRegenerate,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("retry_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.95f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    // Artifact button link
                    if (!message.artifactId.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onOpenArtifact(message.artifactId) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Artifact Workspace", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (!isUser) {
                Row(modifier = Modifier.padding(top = 2.dp)) {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(message.content))
                            showCopied = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingAssistantBubble(
    text: String,
    toolRecords: List<ToolExecutionRecord>,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (toolRecords.isNotEmpty()) {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                toolRecords.forEach { rec ->
                    val isSearch = rec.pluginName.contains("Web", ignoreCase = true) || rec.toolDisplayName.contains("search", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 2.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isSearch) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Web Search",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (rec.status == ToolStatus.SUCCESS) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = if (rec.status == ToolStatus.SUCCESS) "Done" else "Searching...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (rec.status == ToolStatus.SUCCESS) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${rec.pluginName} · ${rec.toolDisplayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = if (rec.status == ToolStatus.SUCCESS) "Done" else "Running",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (rec.status == ToolStatus.SUCCESS) Color(0xFF10B981) else Color(0xFF3B82F6)
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thinking & routing tools...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DangerousActionApprovalSheet(
    record: ToolExecutionRecord,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Authorization Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${record.pluginName} is requesting to execute: ${record.toolDisplayName} with arguments ${record.inputArgs}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onReject) { Text("Deny", color = MaterialTheme.colorScheme.error) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
fun ConversationHistoryDrawer(
    conversations: List<FirestoreConversation>,
    activeId: String?,
    activeProject: FirestoreProject?,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onDelete: (String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(310.dp)
                .clickable(enabled = false) {},
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(14.dp)
            ) {
                Button(
                    onClick = onNewChat,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("drawer_new_chat_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Chat")
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Recent Conversations", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(conversations, key = { it.id }) { conv ->
                        val isSelected = conv.id == activeId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onSelect(conv.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = conv.title.ifBlank { "Conversation" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onDelete(conv.id) }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // Navigation Items
                NavigationDrawerItem(
                    label = { Text("Projects & Workspaces") },
                    icon = { Icon(Icons.Default.Workspaces, contentDescription = null) },
                    selected = false,
                    onClick = { onNavigate(AppDestination.Projects) }
                )
                NavigationDrawerItem(
                    label = { Text("Artifacts Workspace") },
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    selected = false,
                    onClick = { onNavigate(AppDestination.Artifacts) }
                )
                NavigationDrawerItem(
                    label = { Text("Cloud Automations") },
                    icon = { Icon(Icons.Default.Alarm, contentDescription = null) },
                    selected = false,
                    onClick = { onNavigate(AppDestination.Automations) }
                )
                NavigationDrawerItem(
                    label = { Text("Activity Center") },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    selected = false,
                    onClick = { onNavigate(AppDestination.Activities) }
                )
                NavigationDrawerItem(
                    label = { Text("Plugin Store") },
                    icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                    selected = false,
                    onClick = { onNavigate(AppDestination.PluginStore) }
                )
                NavigationDrawerItem(
                    label = { Text("Cloud Memory") },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                    selected = false,
                    onClick = { onNavigate(AppDestination.Memory) }
                )
                NavigationDrawerItem(
                    label = { Text("Cloud Documents") },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    selected = false,
                    onClick = { onNavigate(AppDestination.Files) }
                )
                if (AuthenticationManager.isGuestMode.value) {
                    NavigationDrawerItem(
                        label = { Text("Sign In with Google") },
                        icon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        selected = false,
                        onClick = {
                            AuthenticationManager.setGuestMode(false)
                        }
                    )
                } else {
                    NavigationDrawerItem(
                        label = { Text("Sign Out") },
                        icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                        selected = false,
                        onClick = onSignOut
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChatHero(onPromptSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 30.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("NexusAI Personal Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Powered by Gemini with real-time cloud tool routing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(20.dp))

        listOf(
            "Research the best Android Windows emulators in 2026.",
            "Analyze square/retrofit repository on GitHub.",
            "Find my project documents in Google Drive.",
            "Check my upcoming Calendar events for this week.",
            "Summarize unread emails in Gmail inbox."
        ).forEach { prompt ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { onPromptSelected(prompt) }
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(prompt, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
