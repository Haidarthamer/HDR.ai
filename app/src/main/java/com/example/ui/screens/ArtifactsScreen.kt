package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.DesignIntelligence
import com.example.model.FirestoreArtifact
import com.example.ui.components.InteractiveWebPreview
import com.example.viewmodel.AppDestination
import com.example.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artifacts by viewModel.artifacts.collectAsState()
    val activeArtifact by viewModel.activeArtifact.collectAsState()

    BackHandler {
        if (activeArtifact != null) {
            viewModel.selectArtifact(null)
        } else {
            onBack()
        }
    }

    var activeTab by remember { mutableIntStateOf(1) } // 0: Code, 1: Live Preview, 2: Design Audit, 3: History
    val clipboard = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeArtifact?.title ?: "Nexus Canvas",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1
                        )
                        Text(
                            text = if (activeArtifact != null) {
                                "${activeArtifact?.type?.uppercase()} · ${activeArtifact?.language?.uppercase()} · v${activeArtifact?.version ?: 1}"
                            } else "Build, run, and inspect interactive apps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeArtifact != null) {
                                viewModel.selectArtifact(null)
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("back_from_artifacts_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activeArtifact != null) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(activeArtifact!!.content))
                            showCopied = true
                        }) {
                            Icon(
                                if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy Code"
                            )
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
                .padding(horizontal = 16.dp)
        ) {
            if (activeArtifact == null) {
                // List of artifacts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Saved Artifacts & Prototypes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${artifacts.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (artifacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Web,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Your Canvas is ready",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Choose a starting point. NexusAI will generate the artifact, then you can run the interactive preview here.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    onBack()
                                    viewModel.navigateTo(AppDestination.Chat)
                                    viewModel.sendMessage("Build a polished, playable HTML5 mini-game with responsive mobile controls. Return the complete self-contained HTML artifact so I can run it in Nexus Canvas.")
                                },
                                modifier = Modifier.fillMaxWidth().testTag("canvas_template_game")
                            ) {
                                Icon(Icons.Default.SportsEsports, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create a playable mini-game")
                            }
                            OutlinedButton(
                                onClick = {
                                    onBack()
                                    viewModel.sendMessage("Build a polished, responsive interactive dashboard as a self-contained HTML artifact with realistic sample data, useful charts, filters, and a refined visual design.")
                                },
                                modifier = Modifier.fillMaxWidth().testTag("canvas_template_dashboard")
                            ) {
                                Icon(Icons.Default.Dashboard, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create a dashboard")
                            }
                            OutlinedButton(
                                onClick = {
                                    onBack()
                                    viewModel.sendMessage("Build a polished responsive web-app prototype as a self-contained HTML artifact. Include working navigation, interactive controls, thoughtful empty states, and a cohesive visual design.")
                                },
                                modifier = Modifier.fillMaxWidth().testTag("canvas_template_webapp")
                            ) {
                                Icon(Icons.Default.Web, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create a web app prototype")
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(artifacts, key = { it.id }) { art ->
                            val isHtmlOrWeb = art.type == "html" || art.language in listOf("html", "js", "svg", "css")
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("artifact_card_${art.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isHtmlOrWeb) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    if (isHtmlOrWeb) Icons.Default.Preview else Icons.Default.Code,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (isHtmlOrWeb) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(art.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                                            Text(
                                                "${art.type.uppercase()} · ${art.language} · v${art.version}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Button(onClick = { viewModel.selectArtifact(art) }) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val currentArt = activeArtifact!!
                val isWebPreview = currentArt.type == "html" ||
                        currentArt.language in listOf("html", "js", "javascript", "svg", "css") ||
                        currentArt.content.contains("<!doctype html", ignoreCase = true) ||
                        currentArt.content.contains("<html", ignoreCase = true) ||
                        currentArt.content.contains("<canvas", ignoreCase = true)

                val auditReport = remember(currentArt.content) {
                    DesignIntelligence.auditCode(currentArt.content, currentArt.language)
                }

                // Tab Switcher
                ScrollableTabRow(
                    selectedTabIndex = activeTab,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Code") },
                        icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text(if (isWebPreview) "Live Preview" else "Document") },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = { Text("Design Audit (${auditReport.score})") },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        text = { Text("History (${currentArt.versions.size})") },
                        icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (activeTab) {
                    0 -> {
                        // Monospace Code Editor / Viewer
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = currentArt.content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                    1 -> {
                        // Live Interactive Preview (WebView for HTML/Games/JS/SVG)
                        if (isWebPreview) {
                            InteractiveWebPreview(
                                htmlContent = currentArt.content,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 16.dp)
                            )
                        } else {
                            // Document / Markdown / Text rendering
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        currentArt.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                    Text(
                                        currentArt.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // Design Audit, Anti-Slop Detector & Taste Critique
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp)
                        ) {
                            item {
                                // Score Banner Card
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            auditReport.score >= 85 -> MaterialTheme.colorScheme.primaryContainer
                                            auditReport.score >= 70 -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.errorContainer
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    "DESIGN INTELLIGENCE AUDIT",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp
                                                )
                                                Text(
                                                    auditReport.grade,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                shadowElevation = 2.dp,
                                                modifier = Modifier.padding(4.dp)
                                            ) {
                                                Text(
                                                    "${auditReport.score}/100",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 22.sp,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text("Style: ${auditReport.inferredStyle.displayName}", fontSize = 11.sp) }
                                            )
                                            if (auditReport.hasGameLoop) {
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text("60FPS Loop", fontSize = 11.sp) }
                                                )
                                            }
                                            if (auditReport.isMobileOptimized) {
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text("Viewport Ready", fontSize = 11.sp) }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Apply Impeccable Polish Button
                                        Button(
                                            onClick = {
                                                viewModel.polishArtifact(currentArt, auditReport)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("apply_impeccable_polish_button")
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Apply Impeccable Polish with NexusAI")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Detected Issues Section
                                if (auditReport.issues.isNotEmpty()) {
                                    Text(
                                        "DETECTED DEFECTS & AI-SLOP WARNINGS (${auditReport.issues.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }

                            items(auditReport.issues) { issue ->
                                val badgeColor = when (issue.severity) {
                                    DesignIntelligence.IssueSeverity.HIGH -> MaterialTheme.colorScheme.error
                                    DesignIntelligence.IssueSeverity.MEDIUM -> Color(0xFFF59E0B)
                                    DesignIntelligence.IssueSeverity.LOW -> MaterialTheme.colorScheme.outline
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = badgeColor.copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    issue.severity.name,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = badgeColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                issue.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(issue.description, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Fix: ${issue.recommendation}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "PASSED IMPECCABLE STANDARDS (${auditReport.passedRules.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            items(auditReport.passedRules) { rule ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(rule.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(rule.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // Version history
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (currentArt.versions.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No previous versions yet. When you polish or edit this artifact, previous iterations will appear here.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(currentArt.versions) { verContent ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Previous Iteration Content:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                verContent.take(180) + "...",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
