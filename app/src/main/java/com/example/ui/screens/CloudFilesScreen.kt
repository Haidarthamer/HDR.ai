package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.model.FileAttachment
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudFilesScreen(
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val files by viewModel.repository.cloudFiles.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    var newFileName by remember { mutableStateOf("") }
    var newFileType by remember { mutableStateOf("PDF") }
    var newFileSummary by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Files & Documents", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("upload_file_button")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload Cloud File")
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
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Encrypted in cloud storage. Gemini parses content via the Files Cloud Plugin for multi-step summarization and extraction.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("file_card_${file.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                val icon = when (file.fileType) {
                                    "PDF" -> Icons.Default.PictureAsPdf
                                    "CSV" -> Icons.Default.TableChart
                                    else -> Icons.Default.Description
                                }
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(file.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "${file.fileType} · ${(file.sizeBytes / 1024)} KB · ${file.contentSummary ?: "Indexed"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.repository.deleteCloudFile(file.id) },
                                modifier = Modifier.testTag("delete_file_${file.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Upload Document to Cloud") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("File Name (e.g. system_audit.pdf)") },
                        modifier = Modifier.fillMaxWidth().testTag("upload_file_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("PDF", "DOCX", "CSV", "TXT").forEach { t ->
                            FilterChip(
                                selected = newFileType == t,
                                onClick = { newFileType = t },
                                label = { Text(t) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileSummary,
                        onValueChange = { newFileSummary = it },
                        label = { Text("Document Content / Summary") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.repository.addCloudFile(
                                newFileName,
                                newFileType,
                                512000L,
                                newFileSummary.ifBlank { "Parsed content ready for analysis" }
                            )
                            newFileName = ""
                            newFileSummary = ""
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_upload_button")
                ) {
                    Text("Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
