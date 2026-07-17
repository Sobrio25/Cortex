package com.aiagents.app.presentation.workspace_detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import com.aiagents.app.data.diagnostics.userVisibleError
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.data.model.PermissionLevel
import com.aiagents.app.data.events.AgentChangeEvent
import com.aiagents.app.data.events.AgentChangeKind
import com.aiagents.app.data.model.SubagentExecutionEntity
import com.aiagents.app.data.repository.ContextCompactionPolicy
import com.aiagents.app.data.terminal.CommandRiskLevel
import com.aiagents.app.data.terminal.PermissionRequest
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.AgentFile
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.Workspace
import com.aiagents.app.presentation.tool_results.DirectToolResultPolicy
import com.aiagents.app.ui.components.DirectToolResultCard
import com.aiagents.app.ui.theme.ShapeTokens
import com.aiagents.app.ui.theme.CortexColors
import com.aiagents.app.ui.theme.CortexMark
import com.aiagents.app.ui.theme.CortexTheme
import com.aiagents.app.ui.theme.cortexGlass
import java.io.File
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import com.aiagents.app.ui.components.CodeBlock
import com.aiagents.app.ui.components.MarkdownTable
import com.aiagents.app.ui.components.ContentSegment
import com.aiagents.app.ui.components.OptionsSelectionCard
import com.aiagents.app.ui.components.SegmentType
import com.aiagents.app.ui.components.WebPreviewDialog
import com.aiagents.app.ui.components.parseMessageWithCodeBlocks
import com.aiagents.app.presentation.stt.STTViewModel
import com.aiagents.app.presentation.stt.STTSettingsDialog
import com.aiagents.app.presentation.stt.STTSettingsUiState
import com.aiagents.app.presentation.stt.VoiceInputButton
import com.aiagents.app.presentation.stt.VoiceInputOverlay
// TranscriptionPreview removed — transcription is now auto-sent
import com.aiagents.app.data.model.STTMode
import com.aiagents.app.data.model.CloudSTTProvider
import com.aiagents.app.data.model.LocalModelType
import com.aiagents.app.data.model.LocalSTTEngine
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.layout.ContentScale
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser

@Composable
fun SubConversationViewer(
    subConversationId: Long,
    modifier: Modifier = Modifier
) {
    val viewModel: WorkspaceDetailViewModel = hiltViewModel()
    val subMessages by viewModel.getSubConversationMessages(subConversationId)
        .collectAsState(initial = emptyList())

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.padding(top = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Trabajo del agente (${subMessages.size} mensajes)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            subMessages.forEach { msg ->
                val roleLabel = when (msg.role) {
                    MessageRole.USER -> "Tarea"
                    MessageRole.ASSISTANT -> "Agente"
                    MessageRole.TOOL -> "Tool"
                    MessageRole.SYSTEM -> "Sistema"
                }
                val roleColor = when (msg.role) {
                    MessageRole.USER -> MaterialTheme.colorScheme.primary
                    MessageRole.ASSISTANT -> MaterialTheme.colorScheme.secondary
                    MessageRole.TOOL -> Color(0xFF4CAF50)
                    MessageRole.SYSTEM -> MaterialTheme.colorScheme.tertiary
                }
                val bgColor = when (msg.role) {
                    MessageRole.TOOL -> Color(0xFF1E1E1E)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(
                            text = roleLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = roleColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = msg.content.take(500) + if (msg.content.length > 500) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.role == MessageRole.TOOL) Color(0xFF00FF00) else MaterialTheme.colorScheme.onSurface
                        )
                        if (msg.toolCalls.isNotEmpty()) {
                            Text(
                                text = "Tools: ${msg.toolCalls.joinToString(", ") { it.function.name }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFC107)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalBlock(
    command: String,
    isExecuting: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        onClick = { expanded = !expanded }
    ) {
        if (expanded) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = if (isExecuting) Color(0xFFFFC107) else Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isExecuting) "Ejecutando..." else "Comando ejecutado",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isExecuting) Color(0xFFFFC107) else Color(0xFF4CAF50)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$ $command",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = Color(0xFF00FF00)
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFFFFC107),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = if (isExecuting) "Ejecutando comando..." else "Comando ejecutado",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isExecuting) Color(0xFFFFC107) else Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun ToolResultBlock(
    content: String,
    modifier: Modifier = Modifier
) {
    // Extract image URLs from tool result content
    val imageUrls = remember(content) {
        val mdRegex = Regex("""!\[[^\]]*\]\((https?://[^\s)]+)\)""", RegexOption.IGNORE_CASE)
        val urls = mdRegex.findAll(content).map { it.groupValues[1] }
            .filter { url ->
                url.isNotBlank() && url.length > 10 && !url.endsWith("...")
            }
            .toList()
        android.util.Log.d("ToolResultBlock", "Extracted ${urls.size} image URLs from tool result (content length: ${content.length})")
        urls.forEachIndexed { i, url -> android.util.Log.d("ToolResultBlock", "  Image [$i]: $url") }
        if (urls.isEmpty()) {
            android.util.Log.d("ToolResultBlock", "Content preview: ${content.take(300)}")
        }
        urls
    }
    // Text without image markdown for the collapsed view
    val textContent = remember(content) {
        val mdRegex = Regex("""!\[[^\]]*\]\(https?://[^\s)]+\)""", RegexOption.IGNORE_CASE)
        mdRegex.replace(content, "").trim()
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        onClick = { expanded = !expanded }
    ) {
        if (expanded) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Salida:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (textContent.isNotBlank()) {
                    val displayText = textContent.take(500) + if (textContent.length > 500) "\n..." else ""
                    LinkableText(
                        text = displayText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFB0BEC5)
                        ),
                        linkColor = Color(0xFF64B5F6),
                        maxLines = 15
                    )
                }
                if (imageUrls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ImageCarousel(imageUrls = imageUrls)
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Salida del comando",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E)
                )
            }
        }
    }
}

@Composable
fun ReasoningBlock(
    reasoning: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A237E).copy(alpha = 0.15f)
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFF5C6BC0).copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CortexMark(modifier = Modifier.size(16.dp))
                Text(
                    text = "Proceso de pensamiento",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF5C6BC0)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reasoning,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = Color(0xFF7986CB)
            )
        }
    }
}

@Composable
fun LiveReasoningBlock(
    reasoning: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when reasoning updates
    LaunchedEffect(reasoning) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A237E).copy(alpha = 0.12f)
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFF5C6BC0).copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CortexMark(modifier = Modifier.size(16.dp))
                Text(
                    text = "Pensando...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF5C6BC0)
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Color(0xFF5C6BC0)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reasoning,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = Color(0xFF7986CB),
                modifier = Modifier
                    .heightIn(max = 200.dp)
                    .verticalScroll(scrollState)
            )
        }
    }
}

internal fun extractCommandFromToolCall(toolCall: com.aiagents.app.domain.model.ToolCall): String {
    return try {
        val args = com.google.gson.Gson().fromJson(
            toolCall.function.arguments,
            Map::class.java
        )
        args?.get("command")?.toString() ?: toolCall.function.arguments
    } catch (e: Exception) {
        toolCall.function.arguments
    }
}

/**
 * Renders text with clickable URLs that open in the browser.
 */
@Composable
fun LinkableText(
    text: String,
    style: TextStyle,
    linkColor: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    val uriHandler = LocalUriHandler.current

    val annotatedText = remember(text, style) {
        buildMarkdownAnnotatedString(text, style, linkColor)
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotatedText,
        style = style,
        maxLines = maxLines,
        modifier = modifier,
        onClick = { offset ->
            annotatedText.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (_: Exception) { }
                }
        }
    )
}

private fun buildMarkdownAnnotatedString(
    text: String,
    baseStyle: TextStyle,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { lineIndex, rawLine ->
            if (lineIndex > 0) append("\n")

            // Horizontal rule: ---, ***, ___  (3+ of same char, optionally spaced)
            if (rawLine.trim().matches(Regex("""^[-*_]{3,}$"""))) {
                append("─".repeat(20))
                return@forEachIndexed
            }

            // Headers: # to ###### → bold + scaled size
            val headerMatch = Regex("""^(#{1,6})\s+(.+)$""").find(rawLine)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val headerText = headerMatch.groupValues[2]
                val fontSize = when (level) {
                    1 -> (baseStyle.fontSize.value * 1.5f).sp
                    2 -> (baseStyle.fontSize.value * 1.3f).sp
                    3 -> (baseStyle.fontSize.value * 1.15f).sp
                    else -> (baseStyle.fontSize.value * 1.05f).sp
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = fontSize)) {
                    appendInlineMarkdown(headerText, linkColor)
                }
                return@forEachIndexed
            }

            // Blockquote: > text
            val blockquoteMatch = Regex("""^>\s*(.*)$""").find(rawLine)
            if (blockquoteMatch != null) {
                val quoteText = blockquoteMatch.groupValues[1]
                append("┃ ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineMarkdown(quoteText, linkColor)
                }
                return@forEachIndexed
            }

            // Bullet lists: - item or * item (but not ** or ***)
            val bulletMatch = Regex("""^(\s*)[-*](?!\*)\s+(.+)$""").find(rawLine)
            if (bulletMatch != null) {
                val indent = bulletMatch.groupValues[1]
                val bulletText = bulletMatch.groupValues[2]
                append("${indent}\u2022 ")
                appendInlineMarkdown(bulletText, linkColor)
                return@forEachIndexed
            }

            // Numbered lists: 1. item
            val numberedMatch = Regex("""^(\s*)(\d+)\.\s+(.+)$""").find(rawLine)
            if (numberedMatch != null) {
                val indent = numberedMatch.groupValues[1]
                val number = numberedMatch.groupValues[2]
                val itemText = numberedMatch.groupValues[3]
                append("${indent}$number. ")
                appendInlineMarkdown(itemText, linkColor)
                return@forEachIndexed
            }

            // Regular line with inline markdown
            appendInlineMarkdown(rawLine, linkColor)
        }
    }
}

private enum class InlineToken {
    BOLD_ITALIC_STAR,   // ***text***
    BOLD_ITALIC_UNDER,  // ___text___
    BOLD_STAR,          // **text**
    BOLD_UNDER,         // __text__
    ITALIC_STAR,        // *text*
    ITALIC_UNDER,       // _text_
    STRIKETHROUGH,      // ~~text~~
    CODE,               // `text`
    LINK,               // [text](url)
    RAW_URL             // https://...
}

private data class InlineMatch(
    val token: InlineToken,
    val range: IntRange,
    val content: String,
    val url: String? = null
)

private fun findInlineMatches(text: String): List<InlineMatch> {
    val matches = mutableListOf<InlineMatch>()

    // Each pattern with its token type - order defines priority for overlapping matches
    val patterns = listOf(
        InlineToken.BOLD_ITALIC_STAR to Regex("""\*\*\*(.+?)\*\*\*"""),
        InlineToken.BOLD_ITALIC_UNDER to Regex("""___(.+?)___"""),
        InlineToken.BOLD_STAR to Regex("""\*\*(.+?)\*\*"""),
        InlineToken.BOLD_UNDER to Regex("""__(.+?)__"""),
        InlineToken.ITALIC_STAR to Regex("""\*(.+?)\*"""),
        InlineToken.ITALIC_UNDER to Regex("""(?<=\s|^)_(.+?)_(?=\s|$|[.,;:!?])"""),
        InlineToken.STRIKETHROUGH to Regex("""~~(.+?)~~"""),
        InlineToken.CODE to Regex("""`([^`]+?)`"""),
        InlineToken.LINK to Regex("""\[([^\]]+?)\]\((https?://[^\s)]+)\)"""),
        InlineToken.RAW_URL to Regex("""(https?://[^\s)\]>]+)""")
    )

    // Collect all candidates
    val candidates = mutableListOf<InlineMatch>()
    for ((token, regex) in patterns) {
        regex.findAll(text).forEach { m ->
            val content = m.groupValues.getOrElse(1) { "" }
            val url = if (token == InlineToken.LINK) m.groupValues.getOrElse(2) { null } else null
            candidates.add(InlineMatch(token, m.range, content, url))
        }
    }

    // Sort by start position, then by longest match (higher priority patterns consume more)
    candidates.sortWith(compareBy({ it.range.first }, { -it.range.count() }))

    // Greedily pick non-overlapping matches
    var lastEnd = -1
    for (candidate in candidates) {
        if (candidate.range.first > lastEnd) {
            matches.add(candidate)
            lastEnd = candidate.range.last
        }
    }

    return matches
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    linkColor: Color
) {
    val matches = findInlineMatches(text)

    var lastIndex = 0
    for (m in matches) {
        // Append text before this match
        if (m.range.first > lastIndex) {
            append(text.substring(lastIndex, m.range.first))
        }

        when (m.token) {
            InlineToken.BOLD_ITALIC_STAR, InlineToken.BOLD_ITALIC_UNDER -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(m.content)
                }
            }
            InlineToken.BOLD_STAR, InlineToken.BOLD_UNDER -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineMarkdown(m.content, linkColor)
                }
            }
            InlineToken.ITALIC_STAR, InlineToken.ITALIC_UNDER -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(m.content)
                }
            }
            InlineToken.STRIKETHROUGH -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(m.content)
                }
            }
            InlineToken.CODE -> {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x33888888)
                )) {
                    append(m.content)
                }
            }
            InlineToken.LINK -> {
                pushStringAnnotation("URL", m.url!!)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(m.content)
                }
                pop()
            }
            InlineToken.RAW_URL -> {
                val url = m.content.trimEnd('.', ',', ';', ':', '!', '?')
                pushStringAnnotation("URL", url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(url)
                }
                pop()
                val trimmedChars = m.content.length - url.length
                if (trimmedChars > 0) {
                    append(m.content.takeLast(trimmedChars))
                }
            }
        }
        lastIndex = m.range.last + 1
    }

    // Append remaining text
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

/**
 * Renderiza el contenido de un mensaje, mostrando bloques de código
 * con syntax highlighting cuando corresponda.
 */
@Composable
fun MessageContent(
    content: String,
    isUser: Boolean,
    isTool: Boolean,
    modifier: Modifier = Modifier
) {
    val segments = try {
        parseMessageWithCodeBlocks(content)
    } catch (e: Exception) {
        listOf(ContentSegment(SegmentType.TEXT, content))
    }

    val textAndCodeSegments = segments.filter { it.type != SegmentType.IMAGE }
    val imageUrls = segments
        .filter { it.type == SegmentType.IMAGE }
        .map { it.content }
        .filter { url ->
            url.isNotBlank() &&
            (url.startsWith("http://") || url.startsWith("https://")) &&
            url.length > 10 &&
            !url.endsWith("...")
        }

    val context = LocalContext.current
    Column(modifier = modifier) {
        // Render text and code segments
        textAndCodeSegments.forEach { segment ->
            when (segment.type) {
                SegmentType.TEXT -> {
                    val textColor = when {
                        isUser -> Color.White
                        isTool -> Color(0xFF00FF00)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    LinkableText(
                        text = segment.content,
                        style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                        linkColor = Color(0xFF64B5F6)
                    )
                }
                SegmentType.CODE_BLOCK -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    CodeBlock(
                        code = segment.content,
                        language = segment.language ?: "text"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                SegmentType.TABLE -> {
                    val tableTextColor = when {
                        isUser -> Color.White
                        isTool -> Color(0xFF00FF00)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownTable(
                        tableContent = segment.content,
                        textColor = tableTextColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                SegmentType.WEATHER -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    com.aiagents.app.ui.components.WeatherResultCard(weatherJson = segment.content)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                else -> {}
            }
        }

        // Render images as carousel at the bottom
        if (imageUrls.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ImageCarousel(imageUrls = imageUrls)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCarousel(
    imageUrls: List<String>,
    modifier: Modifier = Modifier
) {
    if (imageUrls.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { imageUrls.size })
    var downloadingIndex by remember { mutableIntStateOf(-1) }
    // Track failed images to hide them visually
    val failedImages = remember { mutableStateMapOf<Int, Boolean>() }

    android.util.Log.d("ImageCarousel", "Rendering carousel with ${imageUrls.size} images")
    imageUrls.forEachIndexed { i, url ->
        android.util.Log.d("ImageCarousel", "  [$i] $url")
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 8.dp
            ) { page ->
                val url = imageUrls[page]
                val isFailed = failedImages[page] == true

                if (isFailed) {
                    // Show placeholder for failed images
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No se pudo cargar",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            when (state) {
                                is AsyncImagePainter.State.Error -> {
                                    android.util.Log.e("ImageCarousel", "Failed to load image [$page]: $url", state.result.throwable)
                                    failedImages[page] = true
                                }
                                is AsyncImagePainter.State.Success -> {
                                    android.util.Log.d("ImageCarousel", "Successfully loaded image [$page]: $url")
                                }
                                else -> {}
                            }
                        }
                    )
                }
            }

            // Page counter badge
            if (imageUrls.size > 1) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${imageUrls.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Download button (only for non-failed images)
            if (failedImages[pagerState.currentPage] != true) {
                val isDownloading = downloadingIndex == pagerState.currentPage
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clickable(enabled = !isDownloading) {
                            val currentPage = pagerState.currentPage
                            downloadingIndex = currentPage
                            scope.launch {
                                downloadImageToGallery(
                                    context,
                                    imageUrls[currentPage],
                                    "image_${System.currentTimeMillis()}"
                                )
                                downloadingIndex = -1
                            }
                        }
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Descargar imagen",
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        // Dot indicators
        if (imageUrls.size > 1) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                repeat(imageUrls.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val isFailed = failedImages[index] == true
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isFailed -> Color.Red.copy(alpha = 0.5f)
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                }
                            )
                    )
                }
            }
        }
    }
}

private suspend fun downloadImageToGallery(context: Context, url: String, fileName: String) {
    try {
        withContext(Dispatchers.IO) {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()
            val bytes = connection.inputStream.use { it.readBytes() }
            connection.disconnect()

            val contentType = connection.contentType ?: "image/jpeg"
            val extension = when {
                contentType.contains("png") -> "png"
                contentType.contains("webp") -> "webp"
                contentType.contains("gif") -> "gif"
                else -> "jpg"
            }
            val fullName = "$fileName.$extension"
            val mimeType = if (contentType.startsWith("image/")) contentType else "image/$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fullName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIAgents")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/AIAgents")
                dir.mkdirs()
                val file = File(dir, fullName)
                file.writeBytes(bytes)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Imagen guardada", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                userVisibleError(context, e, "workspace_media", "image_download"),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
