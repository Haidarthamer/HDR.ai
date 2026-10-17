package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudMemoryScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val memories by viewModel.memories.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    var keyInput by remember { mutableStateOf("") }
    var valueInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Long-Term Memory", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_from_memory_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_memory_item_button")) {
                        Icon(Icons.Default.Add, contentDescription = "Add Memory")
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Synchronized to your cloud account. The AI assistant extracts and queries these facts across conversations.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${memories.size} Memories Stored", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (memories.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearAllMemories() }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(memories, key = { it.id }) { mem ->
                    var showEditDialog by remember { mutableStateOf(false) }
                    var editKey by remember { mutableStateOf(mem.key) }
                    var editValue by remember { mutableStateOf(mem.value) }
                    var editCategory by remember { mutableStateOf(mem.category) }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("memory_item_${mem.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(mem.key, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = mem.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(mem.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!mem.projectId.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Project Scoped",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        editKey = mem.key
                                        editValue = mem.value
                                        editCategory = mem.category
                                        showEditDialog = true
                                    },
                                    modifier = Modifier.testTag("edit_memory_${mem.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Memory", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { viewModel.deleteMemory(mem.id) },
                                    modifier = Modifier.testTag("delete_memory_${mem.id}")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Memory", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (showEditDialog) {
                        AlertDialog(
                            onDismissRequest = { showEditDialog = false },
                            title = { Text("Edit Stored Memory") },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = editKey,
                                        onValueChange = { editKey = it },
                                        label = { Text("Memory Key") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = editValue,
                                        onValueChange = { editValue = it },
                                        label = { Text("Memory Value") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = editCategory,
                                        onValueChange = { editCategory = it },
                                        label = { Text("Category (PREFERENCE, TECH_STACK, etc.)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (editKey.isNotBlank() && editValue.isNotBlank()) {
                                            viewModel.updateExistingMemory(mem.id, editKey, editValue, editCategory, mem.projectId)
                                            showEditDialog = false
                                        }
                                    }
                                ) {
                                    Text("Update")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
        }
    }

    var categoryInput by remember { mutableStateOf("PREFERENCE") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Cloud Memory") },
            text = {
                Column {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Memory Key (e.g. Preferred Language)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it },
                        label = { Text("Value (e.g. Kotlin & Compose)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("Category (PREFERENCE, TECH_STACK, etc.)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyInput.isNotBlank() && valueInput.isNotBlank()) {
                            viewModel.saveNewMemory(keyInput, valueInput, categoryInput)
                            keyInput = ""
                            valueInput = ""
                            categoryInput = "PREFERENCE"
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
