package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calls.CallCharacter
import com.example.calls.CallCharacterStore
import com.example.calls.CallScheduleController
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private val callAvatars = listOf("🤖", "🧑‍🚀", "🦊", "👾", "🐼", "🌌")
private val callAccent = Color(0xFF7C5CFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findHostActivity() }
    val autoAnswerRequested = hostActivity?.intent?.getBooleanExtra(CallScheduleController.EXTRA_AUTO_ANSWER_CALL, false) == true
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    var character by remember { mutableStateOf(CallCharacterStore.load(context)) }
    var characterName by remember { mutableStateOf(character.name) }
    var selectedAvatar by remember { mutableStateOf(character.avatar) }
    var selectedVoice by remember { mutableStateOf(character.voiceLanguage) }
    var showVoiceMenu by remember { mutableStateOf(false) }
    var inCall by remember { mutableStateOf(false) }
    var shouldListen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready for a voice conversation") }
    var lastTranscript by remember { mutableStateOf("") }
    var pendingAssistantResponse by remember { mutableStateOf(false) }
    var previousAssistantId by remember { mutableStateOf<String?>(null) }
    var audioGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    var waitingForMicPermission by remember { mutableStateOf(false) }
    var scheduleTimeText by remember { mutableStateOf("") }
    var showSchedulePanel by remember { mutableStateOf(false) }
    var showScheduleHelp by remember { mutableStateOf(false) }
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var mouthAnimating by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioGranted = granted
        if (granted && waitingForMicPermission) {
            inCall = true
            shouldListen = true
            status = "Listening… speak naturally"
        } else if (!granted) {
            status = "Microphone permission is required to talk to NexusAI."
        }
        waitingForMicPermission = false
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Enable notifications so scheduled calls can appear outside the app.", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(autoAnswerRequested, character.configured, audioGranted) {
        if (autoAnswerRequested && character.configured && !inCall) {
            hostActivity?.intent?.removeExtra(CallScheduleController.EXTRA_AUTO_ANSWER_CALL)
            if (audioGranted) {
                inCall = true
                shouldListen = true
                status = "Answering scheduled call…"
            } else {
                waitingForMicPermission = true
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val submitTranscript = rememberUpdatedState<(String) -> Unit>(newValue = { phrase ->
        lastTranscript = phrase
        status = "You: $phrase"
        val result = CallScheduleController.handleMessage(context, phrase)
        if (result != null) {
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            status = result.message
        }
        previousAssistantId = messages.lastOrNull { it.role == "assistant" }?.id
        pendingAssistantResponse = true
        shouldListen = false
        viewModel.sendMessage(phrase)
    })

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (result != TextToSpeech.SUCCESS) status = "Speech output is unavailable on this device."
        }
        ttsRef.value = engine
        onDispose {
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
            ttsRef.value = null
        }
    }

    LaunchedEffect(ttsReady, selectedVoice) {
        val engine = ttsRef.value
        if (ttsReady && engine != null) {
            val locale = when (selectedVoice) {
                "ar" -> Locale("ar")
                "en" -> Locale.US
                else -> Locale.getDefault()
            }
            val languageResult = engine.setLanguage(locale)
            if (languageResult < TextToSpeech.LANG_AVAILABLE) engine.language = Locale.getDefault()
            engine.setSpeechRate(1.0f)
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status = "Listening… speak naturally" }
            override fun onBeginningOfSpeech() { status = "Hearing you…" }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status = "Thinking…" }
            override fun onError(error: Int) {
                shouldListen = false
                status = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a working internet connection."
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the microphone and try again."
                    else -> "Couldn't hear that. Tap the microphone to try again."
                }
            }
            override fun onResults(results: Bundle?) {
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                shouldListen = false
                if (phrase.isNotBlank()) submitTranscript.value.invoke(phrase)
                else status = "I didn't catch that. Tap the microphone and try again."
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) lastTranscript = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { runCatching { recognizer?.cancel() } }
    }

    LaunchedEffect(inCall, audioGranted, shouldListen) {
        if (inCall && audioGranted && shouldListen) {
            if (recognizer == null) {
                status = "Speech recognition is not available on this device."
                shouldListen = false
            } else {
                delay(150)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to NexusAI")
                }
                runCatching { recognizer.startListening(intent) }
                    .onFailure { status = "Couldn't start listening. Tap the microphone to retry."; shouldListen = false }
            }
        } else if (!inCall) {
            runCatching { recognizer?.cancel() }
        }
    }

    val latestAssistant = messages.lastOrNull { it.role == "assistant" }
    LaunchedEffect(latestAssistant?.id, isStreaming, pendingAssistantResponse, inCall, ttsReady) {
        val response = latestAssistant
        if (inCall && ttsReady && pendingAssistantResponse && !isStreaming &&
            response != null && response.id != previousAssistantId && response.content.isNotBlank()
        ) {
            pendingAssistantResponse = false
            status = "${character.name} is speaking…"
            mouthAnimating = true
            val spoken = response.content.take(5000)
            ttsRef.value?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "nexusai-" + response.id)
            delay((spoken.length * 48L).coerceIn(1800L, 45000L))
            mouthAnimating = false
            if (inCall) {
                shouldListen = true
                status = "Listening… speak when you're ready"
            }
        }
    }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (character.configured) "NexusAI Calls" else "Create your AI character", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to chat") }
                }
            )
        }
    ) { padding ->
        if (!character.configured) {
            CharacterSetup(
                name = characterName,
                onNameChange = { characterName = it },
                selectedAvatar = selectedAvatar,
                onAvatarSelect = { selectedAvatar = it },
                selectedVoice = selectedVoice,
                onVoiceSelect = { selectedVoice = it },
                showVoiceMenu = showVoiceMenu,
                onShowVoiceMenu = { showVoiceMenu = it },
                onSave = {
                    CallCharacterStore.save(context, characterName, selectedAvatar, selectedVoice)
                    character = CallCharacterStore.load(context)
                    if (Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    status = "Character saved. Your first call is ready."
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                CallAvatarFace(
                    avatar = character.avatar,
                    speaking = mouthAnimating,
                    modifier = Modifier.size(178.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(character.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (lastTranscript.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("“$lastTranscript”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(18.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(155.dp).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages.takeLast(8), key = { it.id }) { message ->
                            val isUser = message.role == "user"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(if (isUser) "You" else character.name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(message.content, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = {
                            if (inCall) {
                                shouldListen = false
                                inCall = false
                                pendingAssistantResponse = false
                                runCatching { recognizer?.cancel() }
                                runCatching { ttsRef.value?.stop() }
                                mouthAnimating = false
                                status = "Call ended"
                            } else if (audioGranted) {
                                inCall = true
                                shouldListen = true
                                status = "Connecting…"
                            } else {
                                waitingForMicPermission = true
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(68.dp),
                        shape = CircleShape,
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (inCall) Color(0xFFDA3849) else Color(0xFF10A36A),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(if (inCall) Icons.Default.Stop else Icons.Default.Call, contentDescription = if (inCall) "End call" else "Start voice call", modifier = Modifier.size(30.dp))
                    }
                    FilledIconButton(
                        onClick = {
                            if (audioGranted) shouldListen = !shouldListen
                            else {
                                waitingForMicPermission = true
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (shouldListen) callAccent else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (shouldListen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(if (shouldListen) Icons.Default.Mic else Icons.Default.MicOff, contentDescription = "Toggle microphone")
                    }
                    FilledIconButton(onClick = { showSchedulePanel = !showSchedulePanel }, modifier = Modifier.size(56.dp), shape = CircleShape) {
                        Icon(Icons.Default.Schedule, contentDescription = "Schedule a call")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (inCall) "Your speech is transcribed into this chat. NexusAI replies aloud."
                    else "Your first call uses your selected character and voice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (showSchedulePanel) {
                    Spacer(Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Schedule a call", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = scheduleTimeText,
                                onValueChange = { scheduleTimeText = it },
                                label = { Text("Time, e.g. 12:39 PM or tomorrow 8:00 AM") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val result = CallScheduleController.scheduleFromText(context, scheduleTimeText)
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    status = result.message
                                    if (result.kind == com.example.calls.CallScheduleResult.Kind.SCHEDULED) {
                                        if (Build.VERSION.SDK_INT >= 33 &&
                                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        showSchedulePanel = false
                                    }
                                }) { Icon(Icons.Default.Check, contentDescription = null); Text("Save call") }
                                TextButton(onClick = { showScheduleHelp = true }) { Text("How it works") }
                            }
                        }
                    }
                }
                if (isStreaming) {
                    Spacer(Modifier.height(6.dp))
                    Text("NexusAI is thinking…", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showScheduleHelp) {
        AlertDialog(
            onDismissRequest = { showScheduleHelp = false },
            title = { Text("Scheduled calls") },
            text = { Text("Use a clear time such as 12:39 PM, 20:30, or tomorrow at 8:00 AM. Android may defer the notification if exact-alarm access is unavailable. Calls triggered by new emails or GitHub workflow results need that event source connected first.") },
            confirmButton = { TextButton(onClick = { showScheduleHelp = false }) { Text("Got it") } }
        )
    }
}

@Composable
private fun CharacterSetup(
    name: String,
    onNameChange: (String) -> Unit,
    selectedAvatar: String,
    onAvatarSelect: (String) -> Unit,
    selectedVoice: String,
    onVoiceSelect: (String) -> Unit,
    showVoiceMenu: Boolean,
    onShowVoiceMenu: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text("Build your call character", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Choose how NexusAI looks and sounds before the first call.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        CallAvatarFace(
            avatar = selectedAvatar,
            speaking = false,
            modifier = Modifier.size(126.dp)
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            callAvatars.forEach { avatar ->
                Surface(
                    shape = CircleShape,
                    color = if (avatar == selectedAvatar) callAccent.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp).clickable { onAvatarSelect(avatar) }
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(avatar, fontSize = 23.sp) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Character name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        Box {
            OutlinedButton(onClick = { onShowVoiceMenu(true) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.SettingsVoice, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(when (selectedVoice) { "ar" -> "Arabic system voice"; "en" -> "English system voice"; else -> "Device default voice" })
            }
            DropdownMenu(expanded = showVoiceMenu, onDismissRequest = { onShowVoiceMenu(false) }) {
                DropdownMenuItem(text = { Text("Device default voice") }, onClick = { onVoiceSelect("system"); onShowVoiceMenu(false) })
                DropdownMenuItem(text = { Text("English voice") }, onClick = { onVoiceSelect("en"); onShowVoiceMenu(false) })
                DropdownMenuItem(text = { Text("Arabic voice") }, onClick = { onVoiceSelect("ar"); onShowVoiceMenu(false) })
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Save character and continue")
        }
        Spacer(Modifier.height(10.dp))
        Text("Microphone access is requested only when you start a call.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}


@Composable
private fun CallAvatarFace(
    avatar: String,
    speaking: Boolean,
    modifier: Modifier = Modifier
) {
    val mouthHeight by animateDpAsState(
        targetValue = if (speaking) 17.dp else 4.dp,
        label = "NexusAI mouth animation"
    )
    val faceColor = when (avatar) {
        "🦊" -> Color(0xFFF1A365)
        "👾" -> Color(0xFF82D5BD)
        "🐼" -> Color(0xFFE9E9F2)
        "🌌" -> Color(0xFF9EA5EF)
        "🧑‍🚀" -> Color(0xFFE8C4A9)
        else -> Color(0xFFB7A6FA)
    }
    Canvas(modifier = modifier.clip(CircleShape)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension * 0.43f
        drawCircle(color = Color(0xFF32265F), radius = size.minDimension * 0.49f, center = Offset(centerX, centerY))
        drawCircle(color = faceColor, radius = radius, center = Offset(centerX, centerY))
        val eyeY = centerY - radius * 0.10f
        val eyeRadius = radius * 0.055f
        val eyeSpread = radius * 0.34f
        drawCircle(color = Color(0xFF211A33), radius = eyeRadius, center = Offset(centerX - eyeSpread, eyeY))
        drawCircle(color = Color(0xFF211A33), radius = eyeRadius, center = Offset(centerX + eyeSpread, eyeY))
        val mouthWidth = radius * 0.37f
        val mouthTop = centerY + radius * 0.22f
        val actualMouthHeight = mouthHeight.toPx()
        drawOval(
            color = Color(0xFF34202D),
            topLeft = Offset(centerX - mouthWidth / 2f, mouthTop),
            size = Size(mouthWidth, actualMouthHeight)
        )
        if (speaking) {
            drawOval(
                color = Color(0xFFE986A4),
                topLeft = Offset(centerX - mouthWidth * 0.25f, mouthTop + actualMouthHeight * 0.58f),
                size = Size(mouthWidth * 0.5f, actualMouthHeight * 0.24f)
            )
        }
    }
}


private fun Context.findHostActivity(): Activity? {
    var current: Context = this
    while (true) {
        if (current is Activity) return current
        if (current is ContextWrapper) {
            current = current.baseContext
        } else {
            return null
        }
    }
}
