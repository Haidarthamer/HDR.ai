package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

data class WebConsoleEntry(
    val level: String, // LOG, WARN, ERROR, INFO
    val message: String,
    val lineNumber: Int,
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ViewportMode(val displayName: String, val widthDp: Int?) {
    RESPONSIVE("Responsive Full", null),
    MOBILE("Mobile (390dp)", 390),
    TABLET("Tablet (768dp)", 768)
}

class NexusWebBridge(
    private val onLogReceived: (WebConsoleEntry) -> Unit
) {
    @JavascriptInterface
    fun postMessage(level: String, message: String, line: Int, source: String) {
        onLogReceived(
            WebConsoleEntry(
                level = level.uppercase(),
                message = message,
                lineNumber = line,
                source = source
            )
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveWebPreview(
    htmlContent: String,
    modifier: Modifier = Modifier,
    onScreenshotCaptured: ((Bitmap) -> Unit)? = null
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var viewportMode by remember { mutableStateOf(ViewportMode.RESPONSIVE) }
    val consoleLogs = remember { mutableStateListOf<WebConsoleEntry>() }
    var showConsoleSheet by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSnapshotDialog by remember { mutableStateOf(false) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // Prepare complete HTML with error interceptor bridge
    val preparedHtml = remember(htmlContent) {
        val bridgeScript = """
            <script>
            window.addEventListener('error', function(e) {
                if (window.nexusBridge) {
                    window.nexusBridge.postMessage('ERROR', e.message || 'Script error', e.lineno || 0, e.filename || 'inline');
                }
            });
            console.error = (function(oldErr) {
                return function() {
                    oldErr.apply(console, arguments);
                    if (window.nexusBridge) {
                        var msg = Array.prototype.slice.call(arguments).join(' ');
                        window.nexusBridge.postMessage('ERROR', msg, 0, 'console');
                    }
                };
            })(console.error);
            </script>
        """.trimIndent()

        if (htmlContent.contains("<head>", ignoreCase = true)) {
            htmlContent.replace("<head>", "<head>\n$bridgeScript\n", ignoreCase = true)
        } else {
            "$bridgeScript\n$htmlContent"
        }
    }

    val errorCount = consoleLogs.count { it.level == "ERROR" }
    val warnCount = consoleLogs.count { it.level == "WARN" }

    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar for Viewport and Interactive Controls
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Viewport selector chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ViewportMode.values().forEach { mode ->
                        FilterChip(
                            selected = viewportMode == mode,
                            onClick = { viewportMode = mode },
                            label = { Text(mode.displayName, fontSize = 11.sp) },
                            modifier = Modifier.testTag("viewport_${mode.name.lowercase()}")
                        )
                    }
                }

                // Action buttons: Reload, Console Logs, Screenshot
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            consoleLogs.clear()
                            reloadTrigger++
                            webViewInstance?.loadDataWithBaseURL("https://nexus.local/", preparedHtml, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.size(36.dp).testTag("reload_webview_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(18.dp))
                    }

                    // Visual Screenshot Capture
                    IconButton(
                        onClick = {
                            webViewInstance?.let { wv ->
                                try {
                                    val w = wv.width.coerceAtLeast(1)
                                    val h = wv.height.coerceAtLeast(1)
                                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    val c = Canvas(bmp)
                                    wv.draw(c)
                                    capturedBitmap = bmp
                                    showSnapshotDialog = true
                                    onScreenshotCaptured?.invoke(bmp)
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.size(36.dp).testTag("capture_screenshot_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture Visual Snapshot", modifier = Modifier.size(18.dp))
                    }

                    // Console button with error badge
                    FilledTonalButton(
                        onClick = { showConsoleSheet = !showConsoleSheet },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp).testTag("toggle_console_button")
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Console", fontSize = 11.sp)
                        if (errorCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text("$errorCount", color = MaterialTheme.colorScheme.onError, fontSize = 9.sp)
                            }
                        } else if (consoleLogs.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                Text("${consoleLogs.size}", fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        // Viewport Container with Border & Emulated Device Frame
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val containerModifier = if (viewportMode.widthDp != null) {
                Modifier
                    .width(viewportMode.widthDp!!.dp)
                    .fillMaxHeight()
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            } else {
                Modifier
                    .fillMaxSize()
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
            }

            Box(modifier = containerModifier.background(Color.White)) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_NO_CACHE
                                allowFileAccess = false
                                allowContentAccess = false
                            }

                            val bridge = NexusWebBridge { entry ->
                                consoleLogs.add(0, entry)
                            }
                            addJavascriptInterface(bridge, "nexusBridge")

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    return true // Contain all links in preview
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                    if (cm != null) {
                                        val level = when (cm.messageLevel()) {
                                            ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                                            ConsoleMessage.MessageLevel.WARNING -> "WARN"
                                            ConsoleMessage.MessageLevel.LOG -> "LOG"
                                            ConsoleMessage.MessageLevel.TIP -> "INFO"
                                            else -> "DEBUG"
                                        }
                                        consoleLogs.add(
                                            0,
                                            WebConsoleEntry(
                                                level = level,
                                                message = cm.message() ?: "",
                                                lineNumber = cm.lineNumber(),
                                                source = cm.sourceId() ?: "inline"
                                            )
                                        )
                                    }
                                    return true
                                }
                            }

                            loadDataWithBaseURL("https://nexus.local/", preparedHtml, "text/html", "UTF-8", null)
                            webViewInstance = this
                        }
                    },
                    update = { wv ->
                        webViewInstance = wv
                        // If content changed or reloaded
                        wv.loadDataWithBaseURL("https://nexus.local/", preparedHtml, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Live Console Drawer
        AnimatedVisibility(visible = showConsoleSheet) {
            Surface(
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Console Outputs (${consoleLogs.size}) · $errorCount Errors · $warnCount Warnings",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            TextButton(onClick = { consoleLogs.clear() }) {
                                Text("Clear", fontSize = 11.sp)
                            }
                            IconButton(onClick = { showConsoleSheet = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (consoleLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No console outputs recorded yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(consoleLogs) { log ->
                                val color = when (log.level) {
                                    "ERROR" -> MaterialTheme.colorScheme.error
                                    "WARN" -> Color(0xFFF59E0B)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "[${log.level}]",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = color,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        log.message,
                                        fontSize = 11.sp,
                                        color = color,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (log.lineNumber > 0) {
                                        Text(
                                            ":${log.lineNumber}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Snapshot Preview Dialog
        if (showSnapshotDialog && capturedBitmap != null) {
            AlertDialog(
                onDismissRequest = { showSnapshotDialog = false },
                title = { Text("Visual Verification Snapshot", fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Rendered Resolution: ${capturedBitmap!!.width} x ${capturedBitmap!!.height} px",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = "Rendered Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showSnapshotDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
