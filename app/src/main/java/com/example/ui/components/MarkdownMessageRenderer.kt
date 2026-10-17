package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class NumberedList(val items: List<Pair<Int, String>>) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
}

@Composable
fun MarkdownMessageRenderer(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val blocks = remember(content) { parseMarkdown(content) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Header -> {
                    val (style, size) = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge to 18.sp
                        2 -> MaterialTheme.typography.titleMedium to 16.sp
                        else -> MaterialTheme.typography.titleSmall to 14.sp
                    }
                    Text(
                        text = parseInlineFormatting(block.text, textColor),
                        style = style.copy(fontWeight = FontWeight.Bold, fontSize = size),
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineFormatting(block.text, textColor),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = textColor
                    )
                }
                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(start = 4.dp)) {
                        block.items.forEach { item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("• ", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = parseInlineFormatting(item, textColor),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                    color = textColor
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.NumberedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(start = 4.dp)) {
                        block.items.forEach { (num, item) ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("$num. ", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = parseInlineFormatting(item, textColor),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                    color = textColor
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineFormatting(block.text, textColor.copy(alpha = 0.9f)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic, lineHeight = 20.sp),
                            color = textColor.copy(alpha = 0.9f)
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    CodeSnippetCard(language = block.language, code = block.code)
                }
                is MarkdownBlock.Table -> {
                    MarkdownTableView(headers = block.headers, rows = block.rows, textColor = textColor)
                }
            }
        }
    }
}

@Composable
fun CodeSnippetCard(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(14.dp),
                        tint = if (copied) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun MarkdownTableView(headers: List<String>, rows: List<List<String>>, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
            // Header Row
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)).padding(vertical = 6.dp)) {
                headers.forEach { h ->
                    Text(
                        text = h.trim(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.widthIn(min = 90.dp).padding(horizontal = 8.dp)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            // Rows
            rows.forEach { r ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    r.forEach { cell ->
                        Text(
                            text = cell.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            modifier = Modifier.widthIn(min = 90.dp).padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parses markdown into structured blocks (Headers, Paragraphs, Lists, Code blocks, Tables).
 */
fun parseMarkdown(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // 1. Fenced Code Block: ```lang ... ```
        if (line.trim().startsWith("```")) {
            val lang = line.trim().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            i++
            continue
        }

        // 2. Headings: #, ##, ###
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            val headerText = line.removePrefix("#".repeat(level)).trim()
            blocks.add(MarkdownBlock.Header(level, headerText))
            i++
            continue
        }

        // 3. Blockquote: > ...
        if (line.trimStart().startsWith(">")) {
            val quoteText = line.trimStart().removePrefix(">").trim()
            blocks.add(MarkdownBlock.Blockquote(quoteText))
            i++
            continue
        }

        // 4. Bullet lists: - item, * item, • item
        val bulletMatch = Regex("""^\s*[-*•]\s+(.+)""").find(line)
        if (bulletMatch != null) {
            val items = mutableListOf<String>()
            items.add(bulletMatch.groupValues[1])
            i++
            while (i < lines.size) {
                val nextBullet = Regex("""^\s*[-*•]\s+(.+)""").find(lines[i])
                if (nextBullet != null) {
                    items.add(nextBullet.groupValues[1])
                    i++
                } else break
            }
            blocks.add(MarkdownBlock.BulletList(items))
            continue
        }

        // 5. Numbered lists: 1. item, 2. item
        val numMatch = Regex("""^\s*(\d+)\.\s+(.+)""").find(line)
        if (numMatch != null) {
            val items = mutableListOf<Pair<Int, String>>()
            items.add(Pair(numMatch.groupValues[1].toIntOrNull() ?: 1, numMatch.groupValues[2]))
            i++
            while (i < lines.size) {
                val nextNum = Regex("""^\s*(\d+)\.\s+(.+)""").find(lines[i])
                if (nextNum != null) {
                    items.add(Pair(nextNum.groupValues[1].toIntOrNull() ?: (items.size + 1), nextNum.groupValues[2]))
                    i++
                } else break
            }
            blocks.add(MarkdownBlock.NumberedList(items))
            continue
        }

        // 6. Table: | col1 | col2 |
        if (line.trim().startsWith("|") && line.trim().endsWith("|") && i + 1 < lines.size && lines[i+1].contains("---")) {
            val headers = line.split("|").filter { it.isNotBlank() }
            i += 2 // skip header + divider
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                val rowCells = lines[i].split("|").filter { it.isNotBlank() }
                rows.add(rowCells)
                i++
            }
            blocks.add(MarkdownBlock.Table(headers, rows))
            continue
        }

        // 7. Normal text / paragraph
        if (line.isNotBlank()) {
            val paragraphLines = mutableListOf<String>()
            paragraphLines.add(line)
            i++
            while (i < lines.size && lines[i].isNotBlank() &&
                !lines[i].trim().startsWith("```") &&
                !lines[i].startsWith("#") &&
                !lines[i].trimStart().startsWith(">") &&
                !lines[i].matches(Regex("""^\s*[-*•]\s+.+""")) &&
                !lines[i].matches(Regex("""^\s*\d+\.\s+.+""")) &&
                !(lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|"))
            ) {
                paragraphLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString("\n")))
            continue
        }

        i++
    }
    return blocks
}

/**
 * Handles inline formatting: **bold**, *italic*, `code`, and cleans up markers.
 */
fun parseInlineFormatting(raw: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        val text = raw

        while (idx < text.length) {
            // Check for **bold**
            if (text.startsWith("**", idx)) {
                val end = text.indexOf("**", idx + 2)
                if (end != -1) {
                    val inner = text.substring(idx + 2, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor))
                    append(inner)
                    pop()
                    idx = end + 2
                    continue
                }
            }

            // Check for `inline code`
            if (text[idx] == '`') {
                val end = text.indexOf('`', idx + 1)
                if (end != -1) {
                    val inner = text.substring(idx + 1, end)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            background = defaultColor.copy(alpha = 0.1f)
                        )
                    )
                    append(" $inner ")
                    pop()
                    idx = end + 1
                    continue
                }
            }

            // Check for *italic*
            if (text[idx] == '*' && (idx + 1 < text.length && text[idx + 1] != '*')) {
                val end = text.indexOf('*', idx + 1)
                if (end != -1) {
                    val inner = text.substring(idx + 1, end)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor))
                    append(inner)
                    pop()
                    idx = end + 1
                    continue
                }
            }

            // Normal character
            append(text[idx])
            idx++
        }
    }
}
