package com.aiagents.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration

/**
 * Parsea formato Markdown inline (**negrita**, *cursiva*, `código`, etc.) y devuelve AnnotatedString
 */
fun parseMarkdownInline(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Código inline: `text`
                text[i] == '`' -> {
                    val endIndex = text.indexOf('`', i + 1)
                    if (endIndex != -1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF2D2D2D),
                            color = Color(0xFFCE9178)
                        )) {
                            append(text.substring(i + 1, endIndex))
                        }
                        i = endIndex + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Negrita: **text** o __text__
                (text.startsWith("**", i) || text.startsWith("__", i)) -> {
                    val marker = if (text.startsWith("**", i)) "**" else "__"
                    val endIndex = text.indexOf(marker, i + 2)
                    if (endIndex != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, endIndex))
                        }
                        i = endIndex + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Cursiva: *text* o _text_ (pero no ** ni __)
                ((text[i] == '*' || text[i] == '_') &&
                    !(i + 1 < text.length && (text[i + 1] == '*' || text[i + 1] == '_'))) -> {
                    val marker = text[i].toString()
                    val endIndex = text.indexOf(marker, i + 1)
                    if (endIndex != -1 && endIndex > i + 1) {
                        withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(text.substring(i + 1, endIndex))
                        }
                        i = endIndex + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Tachado: ~~text~~
                text.startsWith("~~", i) -> {
                    val endIndex = text.indexOf("~~", i + 2)
                    if (endIndex != -1) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 2, endIndex))
                        }
                        i = endIndex + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

/**
 * Colores para syntax highlighting (tema oscuro tipo VS Code)
 */
object SyntaxColors {
    val background = Color(0xFF1E1E1E)
    val keyword = Color(0xFF569CD6)      // Azul - fun, val, if, else, etc.
    val string = Color(0xFFCE9178)       // Naranja - "texto"
    val comment = Color(0xFF6A9955)      // Verde - // comentario
    val number = Color(0xFFB5CEA8)       // Verde claro - 123
    val function = Color(0xFFDCDCAA)     // Amarillo - nombreFuncion()
    val type = Color(0xFF4EC9B0)         // Turquesa - Int, String, etc.
    val operator = Color(0xFFD4D4D4)     // Gris claro - +, -, =, etc.
    val default = Color(0xFFD4D4D4)      // Gris claro - texto normal
}

/**
 * Palabras clave por lenguaje para highlighting
 */
object SyntaxKeywords {
    val commonKeywords = setOf(
        "fun", "val", "var", "if", "else", "when", "for", "while", "return",
        "class", "object", "interface", "data", "sealed", "open", "abstract",
        "private", "public", "protected", "internal", "override", "suspend",
        "import", "package", "try", "catch", "finally", "throw", "null",
        "true", "false", "in", "is", "as", "by", "lazy", "lateinit",
        "const", "operator", "infix", "inline", "noinline", "crossinline"
    )
    
    val kotlinTypes = setOf(
        "Int", "Long", "Float", "Double", "Boolean", "Char", "String",
        "Unit", "Any", "Nothing", "List", "Map", "Set", "Array",
        "MutableList", "MutableMap", "MutableSet"
    )
    
    val pythonKeywords = setOf(
        "def", "class", "if", "elif", "else", "for", "while", "try",
        "except", "finally", "with", "as", "import", "from", "return",
        "yield", "lambda", "and", "or", "not", "in", "is", "None",
        "True", "False", "pass", "break", "continue", "raise", "assert"
    )
    
    val javascriptKeywords = setOf(
        "function", "const", "let", "var", "if", "else", "for", "while",
        "do", "switch", "case", "break", "continue", "return", "try",
        "catch", "finally", "throw", "new", "this", "class", "extends",
        "import", "export", "default", "async", "await", "null", "undefined",
        "true", "false", "typeof", "instanceof", "in", "of"
    )
}

/**
 * Aplica syntax highlighting básico al código
 */
private const val MAX_HIGHLIGHT_CHARS = 8000

fun highlightCode(code: String, language: String): AnnotatedString {
    // Skip highlighting for very large code blocks to avoid OOM
    if (code.length > MAX_HIGHLIGHT_CHARS) {
        return AnnotatedString(code, SpanStyle(color = SyntaxColors.default))
    }
    return buildAnnotatedString {
        val lines = code.lines()
        
        lines.forEachIndexed { index, line ->
            var i = 0
            while (i < line.length) {
                val remaining = line.substring(i)
                
                when {
                    // Comentarios
                    remaining.startsWith("//") || remaining.startsWith("#") -> {
                        withStyle(SpanStyle(color = SyntaxColors.comment)) {
                            append(remaining)
                        }
                        i = line.length
                    }
                    // Strings (comillas dobles)
                    remaining.startsWith("\"") -> {
                        val endIndex = remaining.indexOf('"', 1).let { 
                            if (it == -1) remaining.length else it + 1 
                        }
                        withStyle(SpanStyle(color = SyntaxColors.string)) {
                            append(remaining.substring(0, endIndex))
                        }
                        i += endIndex
                    }
                    // Strings (comillas simples)
                    remaining.startsWith("'") -> {
                        val endIndex = remaining.indexOf('\'', 1).let { 
                            if (it == -1) remaining.length else it + 1 
                        }
                        withStyle(SpanStyle(color = SyntaxColors.string)) {
                            append(remaining.substring(0, endIndex))
                        }
                        i += endIndex
                    }
                    // Números
                    remaining[0].isDigit() -> {
                        val numEnd = remaining.indexOfFirst { !it.isDigit() && it != '.' }.let {
                            if (it == -1) remaining.length else it
                        }
                        withStyle(SpanStyle(color = SyntaxColors.number)) {
                            append(remaining.substring(0, numEnd))
                        }
                        i += numEnd
                    }
                    // Keywords y tipos
                    else -> {
                        val wordEnd = remaining.indexOfFirst { !it.isLetterOrDigit() && it != '_' }.let {
                            if (it == -1) remaining.length else it
                        }
                        val word = remaining.substring(0, wordEnd)
                        
                        val color = when {
                            isKeyword(word, language) -> SyntaxColors.keyword
                            isType(word, language) -> SyntaxColors.type
                            word.endsWith("(") || (i > 0 && line.getOrNull(i - 1)?.isWhitespace() == true && 
                                wordEnd <= remaining.length && remaining.substring(wordEnd).trimStart().startsWith("(")) -> SyntaxColors.function
                            else -> SyntaxColors.default
                        }
                        
                        withStyle(SpanStyle(color = color)) {
                            append(word)
                        }
                        i += wordEnd
                    }
                }
            }
            
            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

private fun isKeyword(word: String, language: String): Boolean {
    return when (language.lowercase()) {
        "python" -> SyntaxKeywords.pythonKeywords.contains(word)
        "javascript", "js", "typescript", "ts" -> SyntaxKeywords.javascriptKeywords.contains(word)
        else -> SyntaxKeywords.commonKeywords.contains(word) || SyntaxKeywords.pythonKeywords.contains(word)
    }
}

private fun isType(word: String, language: String): Boolean {
    return when (language.lowercase()) {
        "kotlin", "kt" -> SyntaxKeywords.kotlinTypes.contains(word)
        else -> word[0].isUpperCase() && word.length > 1
    }
}

fun copyToClipboard(context: Context, text: String, label: String = "Código") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
}

@Composable
fun CodeBlock(
    code: String,
    language: String = "text",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = SyntaxColors.background
        )
    ) {
        Column {
            // Header con lenguaje y botón copiar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifEmpty { "text" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                
                IconButton(
                    onClick = { copyToClipboard(context, code) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copiar código",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Código con syntax highlighting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                val highlightedCode = try {
                    highlightCode(code, language)
                } catch (e: Throwable) {
                    // Catch OOM and other errors — fall back to plain text
                    AnnotatedString(code)
                }
                
                Text(
                    text = highlightedCode,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier.padding(12.dp),
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Renders a markdown table as a proper Compose table with borders.
 * Columns have uniform width based on the widest content in each column.
 */
@Composable
fun MarkdownTable(
    tableContent: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val lines = tableContent.split("\n").filter { it.isNotBlank() }
    if (lines.size < 2) return

    // Parse rows: split by | and trim
    fun parseCells(line: String): List<String> {
        return line.trim().removePrefix("|").removeSuffix("|")
            .split("|")
            .map { it.trim() }
    }

    // Find separator row index (the one with ---)
    val separatorIndex = lines.indexOfFirst { line ->
        line.trim().matches(Regex("""^\|[\s:]*-{2,}[\s:|-]*\|?$"""))
    }
    if (separatorIndex < 0) return

    val headerRows = lines.subList(0, separatorIndex)
    val dataRows = lines.subList(separatorIndex + 1, lines.size)
    val headerCells = if (headerRows.isNotEmpty()) parseCells(headerRows[0]) else emptyList()
    val columnCount = headerCells.size
    if (columnCount == 0) return

    val borderColor = textColor.copy(alpha = 0.2f)
    val headerBgColor = textColor.copy(alpha = 0.08f)

    val scrollState = rememberScrollState()

    // Collect all rows (header + data) for column width calculation
    val allRows = mutableListOf(headerCells)
    dataRows.forEach { line ->
        allRows.add(parseCells(line))
    }

    // Calculate max width for each column using intrinsic measurements
    val columnWidths = remember(allRows) {
        List(columnCount) { colIndex ->
            var maxWidth = 80.dp // Minimum width
            allRows.forEach { row ->
                val cellText = row.getOrElse(colIndex) { "" }
                // Estimate width: ~8dp per character for bodyMedium font
                // Header is bold so needs slightly more
                val isHeader = row == headerCells
                val charWidth = if (isHeader) 9f else 8f
                val estimatedWidth = (cellText.length * charWidth + 20).coerceAtLeast(80f).dp
                if (estimatedWidth > maxWidth) {
                    maxWidth = estimatedWidth
                }
            }
            maxWidth
        }
    }

    Box(
        modifier = modifier
            .horizontalScroll(scrollState)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .background(headerBgColor)
            ) {
                headerCells.forEachIndexed { index, cell ->
                    Box(
                        modifier = Modifier
                            .width(columnWidths[index])
                            .border(0.5.dp, borderColor)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        val annotatedText = parseMarkdownInline(cell, textColor)
                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        )
                    }
                }
            }

            // Data rows
            dataRows.forEach { line ->
                val cells = parseCells(line)
                Row {
                    for (i in 0 until columnCount) {
                        val cellText = cells.getOrElse(i) { "" }
                        Box(
                            modifier = Modifier
                                .width(columnWidths[i])
                                .border(0.5.dp, borderColor)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            val annotatedText = parseMarkdownInline(cellText, textColor)
                            Text(
                                text = annotatedText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = textColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Parsea el contenido del mensaje y extrae bloques de código
 */
data class ContentSegment(
    val type: SegmentType,
    val content: String,
    val language: String? = null
)

enum class SegmentType {
    TEXT,
    CODE_BLOCK,
    IMAGE,
    TABLE,
    WEATHER
}

fun parseMessageWithCodeBlocks(content: String): List<ContentSegment> {
    if (content.isBlank()) {
        return listOf(ContentSegment(SegmentType.TEXT, ""))
    }
    
    val segments = mutableListOf<ContentSegment>()
    // Regex más seguro que maneja casos edge
    val codeBlockRegex = """```(\w+)?\n?([\s\S]*?)```""".toRegex()
    
    var lastIndex = 0
    
    try {
        codeBlockRegex.findAll(content).forEach { match ->
            // Texto antes del bloque de código
            if (match.range.first > lastIndex) {
                val text = content.substring(lastIndex, match.range.first).trim()
                if (text.isNotEmpty()) {
                    segments.add(ContentSegment(SegmentType.TEXT, text))
                }
            }
            
            // Bloque de código
            val language = match.groupValues.getOrNull(1)?.ifEmpty { "text" } ?: "text"
            val code = match.groupValues.getOrNull(2)?.trim() ?: ""
            if (code.isNotEmpty()) {
                segments.add(ContentSegment(SegmentType.CODE_BLOCK, code, language))
            }
            
            lastIndex = match.range.last + 1
        }
        
        // Texto después del último bloque de código
        if (lastIndex < content.length) {
            val text = content.substring(lastIndex).trim()
            if (text.isNotEmpty()) {
                segments.add(ContentSegment(SegmentType.TEXT, text))
            }
        }
        
        // Si no se encontró nada, devolver el contenido completo como texto
        if (segments.isEmpty() && content.isNotEmpty()) {
            segments.add(ContentSegment(SegmentType.TEXT, content))
        }
    } catch (e: Exception) {
        // En caso de error, devolver todo como texto plano
        segments.clear()
        segments.add(ContentSegment(SegmentType.TEXT, content))
    }

    // Post-process: extract weather, tables and image URLs from TEXT segments
    return segments.flatMap { segment ->
        if (segment.type == SegmentType.TEXT) {
            extractWeatherSegments(segment.content).flatMap { wSeg ->
                if (wSeg.type == SegmentType.TEXT) {
                    extractTableSegments(wSeg.content).flatMap { seg ->
                        if (seg.type == SegmentType.TEXT) {
                            extractImageSegments(seg.content)
                        } else {
                            listOf(seg)
                        }
                    }
                } else {
                    listOf(wSeg)
                }
            }
        } else {
            listOf(segment)
        }
    }
}

/**
 * Extracts <!--WEATHER_DATA:{...}--> markers from text into WEATHER segments.
 */
private fun extractWeatherSegments(text: String): List<ContentSegment> {
    val regex = Regex("""<!--WEATHER_DATA:(.*?)-->""")
    val results = mutableListOf<ContentSegment>()
    var lastIndex = 0

    regex.findAll(text).forEach { match ->
        if (match.range.first > lastIndex) {
            val before = text.substring(lastIndex, match.range.first).trim()
            if (before.isNotEmpty()) {
                results.add(ContentSegment(SegmentType.TEXT, before))
            }
        }
        val json = match.groupValues[1]
        // Use language field to store the weather type (current/forecast)
        val weatherType = try {
            org.json.JSONObject(json).optString("type", "current")
        } catch (_: Exception) { "current" }
        results.add(ContentSegment(SegmentType.WEATHER, json, language = weatherType))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            results.add(ContentSegment(SegmentType.TEXT, remaining))
        }
    }

    if (results.isEmpty() && text.isNotEmpty()) {
        results.add(ContentSegment(SegmentType.TEXT, text))
    }

    return results
}

/**
 * Extracts markdown tables from text, splitting into TABLE and TEXT segments.
 * A table is detected as consecutive lines starting with | that include a separator row (|---|).
 */
private fun extractTableSegments(text: String): List<ContentSegment> {
    val lines = text.split("\n")
    val results = mutableListOf<ContentSegment>()
    val textBuffer = mutableListOf<String>()
    val tableBuffer = mutableListOf<String>()

    fun flushText() {
        if (textBuffer.isNotEmpty()) {
            val content = textBuffer.joinToString("\n").trim()
            if (content.isNotEmpty()) {
                results.add(ContentSegment(SegmentType.TEXT, content))
            }
            textBuffer.clear()
        }
    }

    fun flushTable() {
        if (tableBuffer.isNotEmpty()) {
            // Validate: must have a separator row (|---|...) to be a real table
            val hasSeparator = tableBuffer.any { line ->
                line.trim().matches(Regex("""^\|[\s:]*-{2,}[\s:|-]*\|?$"""))
            }
            if (hasSeparator && tableBuffer.size >= 2) {
                flushText()
                results.add(ContentSegment(SegmentType.TABLE, tableBuffer.joinToString("\n")))
            } else {
                // Not a real table, treat as text
                textBuffer.addAll(tableBuffer)
            }
            tableBuffer.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("|") || (tableBuffer.isNotEmpty() && trimmed.matches(Regex("""^[\s:]*-{2,}[\s:|-]*$""")))) {
            if (tableBuffer.isEmpty()) {
                flushText()
            }
            tableBuffer.add(line)
        } else {
            flushTable()
            textBuffer.add(line)
        }
    }

    flushTable()
    flushText()

    if (results.isEmpty() && text.isNotEmpty()) {
        results.add(ContentSegment(SegmentType.TEXT, text))
    }

    return results
}

/**
 * Regex that matches:
 * 1. Markdown image syntax ![alt](url) — any URL is accepted since ![] already signals an image
 * 2. Bare image URLs (http/https ending in common image extensions)
 */
private val imageUrlRegex = Regex(
    """(?:!\[([^\]]*)\]\((https?://[^\s)]+)\))|(?<![(\["])(https?://[^\s)>\]"]+\.(?:jpg|jpeg|png|gif|webp|svg|bmp)(?:\?[^\s)>\]"]*)?)""",
    RegexOption.IGNORE_CASE
)

private fun extractImageSegments(text: String): List<ContentSegment> {
    val results = mutableListOf<ContentSegment>()
    var lastIndex = 0

    imageUrlRegex.findAll(text).forEach { match ->
        // Text before image URL
        if (match.range.first > lastIndex) {
            val before = text.substring(lastIndex, match.range.first).trim()
            if (before.isNotEmpty()) {
                results.add(ContentSegment(SegmentType.TEXT, before))
            }
        }
        // Markdown image: group 2 is the URL; bare URL: group 3
        val url = match.groupValues[2].ifEmpty { match.groupValues[0] }
        results.add(ContentSegment(SegmentType.IMAGE, url.trim()))
        lastIndex = match.range.last + 1
    }

    // Remaining text after last image
    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            results.add(ContentSegment(SegmentType.TEXT, remaining))
        }
    }

    // If no images found, return original text
    if (results.isEmpty() && text.isNotEmpty()) {
        results.add(ContentSegment(SegmentType.TEXT, text))
    }

    return results
}
