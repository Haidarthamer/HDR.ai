package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.auth.AuthenticationManager
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppDestination
import com.example.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppRoot(viewModel: ChatViewModel) {
    val currentUser by AuthenticationManager.currentUserFlow.collectAsState()
    val isGuest by AuthenticationManager.isGuestMode.collectAsState()
    val destination by viewModel.currentDestination.collectAsState()
    val apiKeyState by viewModel.apiKeySetupState.collectAsState()
    val apiKeyMessage by viewModel.apiKeySetupMessage.collectAsState()
    var apiKeyInput by remember { mutableStateOf("") }
    val identity = currentUser?.uid ?: if (isGuest) "guest" else null

    LaunchedEffect(identity) {
        if (identity != null) viewModel.loadApiKeyForIdentity(identity)
    }

    if (currentUser == null && !isGuest) {
        RealAuthScreen(
            onAuthSuccess = {
                // User authenticated via Google Credential Manager
            },
            onContinueAsGuest = {
                AuthenticationManager.setGuestMode(true)
            }
        )
    } else {
        if (apiKeyState == "REQUIRED" || apiKeyState == "INVALID" || apiKeyState == "ERROR" || apiKeyState == "CHECKING") {
            AlertDialog(
                onDismissRequest = { /* A key is required before chat can be used. */ },
                title = { Text("Connect your Gemini API key") },
                text = {
                    Column {
                        Text("It looks like you haven't linked an API key yet. Add your Gemini API key to start chatting and use NexusAI's features.")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("Type your API key") },
                            placeholder = { Text("Paste Gemini API key") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            enabled = apiKeyState != "CHECKING"
                        )
                        if (apiKeyMessage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = apiKeyMessage,
                                color = if (apiKeyState == "INVALID" || apiKeyState == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (apiKeyState == "CHECKING") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Checking your API key…")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.validateAndSaveApiKey(apiKeyInput) },
                        enabled = apiKeyState != "CHECKING" && apiKeyInput.isNotBlank()
                    ) { Text("Check and save") }
                },
                dismissButton = {
                    TextButton(onClick = { apiKeyInput = "" }) { Text("Clear") }
                }
            )
        }
        when (destination) {
            is AppDestination.Chat -> MainAssistantScreen(viewModel = viewModel)
            is AppDestination.Projects -> ProjectsScreen(
                viewModel = viewModel,
                onBack = { viewModel.navigateTo(AppDestination.Chat) }
            )
            is AppDestination.Artifacts -> ArtifactsScreen(
                viewModel = viewModel,
                onBack = { viewModel.navigateTo(AppDestination.Chat) }
            )
            is AppDestination.Automations -> AutomationsScreen(
                viewModel = viewModel,
                onBack = { viewModel.navigateTo(AppDestination.Chat) }
            )
            is AppDestination.Activities -> ActivitiesScreen(
                viewModel = viewModel,
                onBack = { viewModel.navigateTo(AppDestination.Chat) }
            )
            is AppDestination.PluginStore -> PluginStoreScreenWrapper(viewModel = viewModel)
            is AppDestination.Memory -> MemoryScreenWrapper(viewModel = viewModel)
            is AppDestination.Files -> FilesScreenWrapper(viewModel = viewModel)
            is AppDestination.Settings -> SettingsScreenWrapper(viewModel = viewModel)
        }
    }
}

@Composable
fun PluginStoreScreenWrapper(viewModel: ChatViewModel) {
    com.example.ui.screens.PluginStoreScreen(
        viewModel = com.example.viewmodel.MainViewModel(),
        onBack = { viewModel.navigateTo(AppDestination.Chat) }
    )
}

@Composable
fun MemoryScreenWrapper(viewModel: ChatViewModel) {
    com.example.ui.screens.CloudMemoryScreen(
        viewModel = viewModel,
        onBack = { viewModel.navigateTo(AppDestination.Chat) }
    )
}

@Composable
fun FilesScreenWrapper(viewModel: ChatViewModel) {
    com.example.ui.screens.CloudFilesScreen(
        viewModel = com.example.viewmodel.MainViewModel(),
        onBack = { viewModel.navigateTo(AppDestination.Chat) }
    )
}

@Composable
fun SettingsScreenWrapper(viewModel: ChatViewModel) {
    com.example.ui.screens.SettingsScreen(
        viewModel = com.example.viewmodel.MainViewModel(),
        chatViewModel = viewModel,
        onBack = { viewModel.navigateTo(AppDestination.Chat) }
    )
}
