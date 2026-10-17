package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
