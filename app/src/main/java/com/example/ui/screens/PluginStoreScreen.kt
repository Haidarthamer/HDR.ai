package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PluginManifest
import com.example.model.PluginPermission
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginStoreScreen(
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val plugins by viewModel.pluginRegistry.pluginsState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedPluginForDetails by remember { mutableStateOf<PluginManifest?>(null) }

    if (selectedPluginForDetails != null) {
        BackHandler { selectedPluginForDetails = null }
    } else if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val filteredPlugins = remember(plugins, searchQuery) {
        if (searchQuery.isBlank()) plugins
        else plugins.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true) ||
            it.tools.any { t -> t.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cloud Plugin Store", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "${plugins.count { it.isConnected }} of ${plugins.size} connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.switchTab(com.example.viewmodel.ActiveTab.CustomPluginDev) },
                        modifier = Modifier.testTag("add_custom_plugin_nav_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Register Custom Plugin")
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
                .padding(horizontal = 16.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search plugins, capabilities, tools...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("search_plugins_input")
            )

            // Plugins List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                items(filteredPlugins, key = { it.id }) { plugin ->
                    PluginCard(
                        plugin = plugin,
                        onToggleEnabled = { viewModel.pluginRegistry.togglePluginEnabled(plugin.id) },
                        onConnect = { viewModel.pluginRegistry.connectPlugin(plugin.id) },
                        onDisconnect = { viewModel.pluginRegistry.disconnectPlugin(plugin.id) },
                        onViewDetails = { selectedPluginForDetails = plugin }
                    )
                }
            }
        }
    }

    // Plugin Detail & Permissions Dialog
    if (selectedPluginForDetails != null) {
        PluginDetailBottomSheet(
            plugin = selectedPluginForDetails!!,
            onDismiss = { selectedPluginForDetails = null },
            onPermissionToggle = { permId, granted ->
                viewModel.pluginRegistry.updatePermission(selectedPluginForDetails!!.id, permId, granted)
                // Refresh dialog reference
                selectedPluginForDetails = viewModel.pluginRegistry.pluginsState.value.find { it.id == selectedPluginForDetails!!.id }
            },
            onAutoApproveToggle = { autoApprove ->
                viewModel.pluginRegistry.setAutoApproveDangerous(selectedPluginForDetails!!.id, autoApprove)
                selectedPluginForDetails = viewModel.pluginRegistry.pluginsState.value.find { it.id == selectedPluginForDetails!!.id }
            }
        )
    }
}

@Composable
fun PluginCard(
    plugin: PluginManifest,
    onToggleEnabled: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onViewDetails: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onViewDetails() }
            .testTag("plugin_card_${plugin.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = plugin.iconEmoji, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = plugin.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "v${plugin.version}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = plugin.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Connection status chip
                if (plugin.isConnected) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Connected",
                                color = Color(0xFF10B981),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = "Disconnected",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${plugin.tools.size} tools · ${plugin.permissions.size} permissions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (plugin.isConnected) {
                        TextButton(
                            onClick = onDisconnect,
                            modifier = Modifier.testTag("disconnect_plugin_${plugin.id}")
                        ) {
                            Text("Disconnect", color = MaterialTheme.colorScheme.error)
                        }
                        Switch(
                            checked = plugin.isEnabled,
                            onCheckedChange = { onToggleEnabled() },
                            modifier = Modifier.testTag("toggle_plugin_${plugin.id}")
                        )
                    } else {
                        Button(
                            onClick = onConnect,
                            modifier = Modifier.testTag("connect_plugin_${plugin.id}")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginDetailBottomSheet(
    plugin: PluginManifest,
    onDismiss: () -> Unit,
    onPermissionToggle: (String, Boolean) -> Unit,
    onAutoApproveToggle: (Boolean) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = plugin.iconEmoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Auth Type: ${plugin.authenticationType.name} | Account: ${plugin.connectionAccount ?: "Not Connected"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = plugin.description, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "GRANULAR PERMISSIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            plugin.permissions.forEach { perm ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = perm.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (perm.isDangerous) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "Dangerous",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = perm.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = perm.isGranted,
                        onCheckedChange = { onPermissionToggle(perm.id, it) },
                        modifier = Modifier.testTag("perm_switch_${perm.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AVAILABLE CLOUD TOOLS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            plugin.tools.forEach { tool ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = tool.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text(
                            text = tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Auto-Approve Sensitive Actions", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Skip confirmation prompt for dangerous tools", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = plugin.autoApproveDangerous,
                    onCheckedChange = onAutoApproveToggle,
                    modifier = Modifier.testTag("auto_approve_switch_${plugin.id}")
                )
            }
        }
    }
}
