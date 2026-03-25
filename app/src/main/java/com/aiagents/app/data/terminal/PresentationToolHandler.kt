package com.aiagents.app.data.terminal

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class PresentationToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

/**
 * Generates PPTX files programmatically using raw Open XML (no Apache POI needed).
 * PPTX is a ZIP archive containing XML files following the OOXML standard.
 */
@Singleton
class PresentationToolHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "PresentationToolHandler"

        // EMU = English Metric Unit. 1 inch = 914400 EMU
        private const val EMU_PER_INCH = 914400L
        // Default slide size 13.333" x 7.5" (widescreen 16:9)
        private const val DEFAULT_WIDTH_EMU = 12192000L  // 13.333 inches
        private const val DEFAULT_HEIGHT_EMU = 6858000L  // 7.5 inches

        const val TOOL_CREATE = "pptx_create"
        const val TOOL_ADD_SLIDE = "pptx_add_slide"
        const val TOOL_ADD_TEXT = "pptx_add_text"
        const val TOOL_ADD_IMAGE = "pptx_add_image"
        const val TOOL_ADD_SHAPE = "pptx_add_shape"
        const val TOOL_SET_BACKGROUND = "pptx_set_background"
        const val TOOL_SAVE = "pptx_save"
        const val TOOL_LIST = "pptx_list"

        val ALL_TOOL_NAMES = setOf(
            TOOL_CREATE, TOOL_ADD_SLIDE, TOOL_ADD_TEXT,
            TOOL_ADD_IMAGE, TOOL_ADD_SHAPE, TOOL_SET_BACKGROUND,
            TOOL_SAVE, TOOL_LIST
        )

        private fun param(type: String, desc: String) = mapOf("type" to type, "description" to desc)

        private fun toolDef(name: String, desc: String, props: Map<String, Map<String, String>>, required: List<String>) = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to name,
                "description" to desc,
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to props,
                    "required" to required
                )
            )
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            toolDef(TOOL_CREATE,
                "Crea una nueva presentacion PPTX en blanco. Retorna un ID de sesion para agregar slides y contenido. Siempre llama esto primero antes de agregar slides.",
                mapOf(
                    "title" to param("string", "Titulo de la presentacion"),
                    "width_inches" to param("number", "Ancho en pulgadas (default: 13.333 = widescreen 16:9)"),
                    "height_inches" to param("number", "Alto en pulgadas (default: 7.5 = widescreen 16:9)")
                ),
                listOf("title")
            ),
            toolDef(TOOL_ADD_SLIDE,
                "Agrega un nuevo slide a la presentacion. Retorna el indice del slide creado (0-based).",
                mapOf(
                    "session_id" to param("string", "ID de sesion de la presentacion")
                ),
                listOf("session_id")
            ),
            toolDef(TOOL_ADD_TEXT,
                "Agrega una caja de texto a un slide con control total de posicion, fuente, color y alineacion. Coordenadas en pulgadas desde esquina superior izquierda.",
                mapOf(
                    "session_id" to param("string", "ID de sesion de la presentacion"),
                    "slide_index" to param("integer", "Indice del slide (0-based)"),
                    "text" to param("string", "Texto a agregar. Usa \\n para saltos de linea."),
                    "x" to param("number", "Posicion X en pulgadas (default: 0.5)"),
                    "y" to param("number", "Posicion Y en pulgadas (default: 0.5)"),
                    "width" to param("number", "Ancho en pulgadas (default: 12)"),
                    "height" to param("number", "Alto en pulgadas (default: 1)"),
                    "font_size" to param("integer", "Tamano de fuente en puntos (default: 18)"),
                    "font_family" to param("string", "Nombre de la fuente (default: 'Calibri')"),
                    "font_color" to param("string", "Color del texto en hex sin #, ej: 'FFFFFF' (default: '000000')"),
                    "bold" to param("string", "'true' para negrita (default: false)"),
                    "italic" to param("string", "'true' para italica (default: false)"),
                    "align" to param("string", "Alineacion: 'l' (left), 'ctr' (center), 'r' (right) (default: l)"),
                    "valign" to param("string", "Alineacion vertical: 't' (top), 'ctr' (middle), 'b' (bottom) (default: t)")
                ),
                listOf("session_id", "slide_index", "text")
            ),
            toolDef(TOOL_ADD_IMAGE,
                "Agrega una imagen a un slide desde una URL publica. Se descarga e incrusta en el PPTX.",
                mapOf(
                    "session_id" to param("string", "ID de sesion de la presentacion"),
                    "slide_index" to param("integer", "Indice del slide (0-based)"),
                    "url" to param("string", "URL publica de la imagen (PNG, JPG)"),
                    "x" to param("number", "Posicion X en pulgadas (default: 1)"),
                    "y" to param("number", "Posicion Y en pulgadas (default: 1)"),
                    "width" to param("number", "Ancho en pulgadas (default: 4)"),
                    "height" to param("number", "Alto en pulgadas (default: 3)")
                ),
                listOf("session_id", "slide_index", "url")
            ),
            toolDef(TOOL_ADD_SHAPE,
                "Agrega una forma geometrica a un slide (rectangulo, elipse, rectangulo redondeado, triangulo, flecha).",
                mapOf(
                    "session_id" to param("string", "ID de sesion de la presentacion"),
                    "slide_index" to param("integer", "Indice del slide (0-based)"),
                    "shape_type" to param("string", "Tipo: 'rect', 'roundRect', 'ellipse', 'triangle', 'rightArrow', 'line' (default: rect)"),
                    "x" to param("number", "Posicion X en pulgadas (default: 1)"),
                    "y" to param("number", "Posicion Y en pulgadas (default: 1)"),
                    "width" to param("number", "Ancho en pulgadas (default: 4)"),
                    "height" to param("number", "Alto en pulgadas (default: 2)"),
                    "fill_color" to param("string", "Color de relleno hex sin #, ej: '3498DB' (opcional)"),
                    "line_color" to param("string", "Color del borde hex sin # (default: '000000')"),
                    "line_width" to param("integer", "Grosor del borde en EMU x 12700 (default: 12700 = 1pt)"),
                    "text" to param("string", "Texto dentro de la forma (opcional)"),
                    "text_color" to param("string", "Color del texto hex sin # (default: 'FFFFFF')"),
                    "text_size" to param("integer", "Tamano de fuente en puntos (default: 14)")
                ),
                listOf("session_id", "slide_index")
            ),
            toolDef(TOOL_SET_BACKGROUND,
                "Establece el fondo de un slide con un color solido.",
                mapOf(
                    "session_id" to param("string", "ID de sesion de la presentacion"),
                    "slide_index" to param("integer", "Indice del slide (0-based)"),
                    "color" to param("string", "Color de fondo hex sin #, ej: '1A1A2E'")
                ),
                listOf("session_id", "slide_index", "color")
            ),
            toolDef(TOOL_SAVE,
                "Guarda la presentacion como archivo PPTX. Retorna la ruta del archivo. Puedes importarlo a Canva con canva_import_design despues.",
                mapOf(
                    "session_id" to param("string", "ID de sesion de la presentacion"),
                    "filename" to param("string", "Nombre del archivo sin extension (default: titulo de la presentacion)")
                ),
                listOf("session_id")
            ),
            toolDef(TOOL_LIST,
                "Lista las presentaciones activas en sesion y los archivos PPTX guardados.",
                emptyMap(),
                emptyList()
            )
        )
    }

    // ── Session management ─────────────────────────────────────────────────

    private data class SlideData(
        val elements: MutableList<SlideElement> = mutableListOf(),
        var bgColor: String? = null
    )

    private sealed class SlideElement {
        data class TextBox(
            val text: String, val x: Long, val y: Long, val cx: Long, val cy: Long,
            val fontSize: Int, val fontFamily: String, val fontColor: String,
            val bold: Boolean, val italic: Boolean, val align: String, val valign: String
        ) : SlideElement()

        data class Image(
            val imageBytes: ByteArray, val contentType: String,
            val x: Long, val y: Long, val cx: Long, val cy: Long
        ) : SlideElement()

        data class Shape(
            val shapeType: String, val x: Long, val y: Long, val cx: Long, val cy: Long,
            val fillColor: String?, val lineColor: String, val lineWidth: Int,
            val text: String?, val textColor: String, val textSize: Int
        ) : SlideElement()
    }

    private data class PresentationSession(
        val id: String,
        val title: String,
        val widthEmu: Long,
        val heightEmu: Long,
        val slides: MutableList<SlideData> = mutableListOf(),
        val images: MutableList<Pair<ByteArray, String>> = mutableListOf() // bytes, contentType
    )

    private val sessions = mutableMapOf<String, PresentationSession>()

    // ── Entry point ────────────────────────────────────────────────────────

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String): PresentationToolResult {
        return try {
            val args = if (arguments.isBlank() || arguments == "{}") {
                com.google.gson.JsonObject()
            } else {
                JsonParser.parseString(arguments).asJsonObject
            }
            when (toolName) {
                TOOL_CREATE -> createPresentation(toolCallId, args)
                TOOL_ADD_SLIDE -> addSlide(toolCallId, args)
                TOOL_ADD_TEXT -> addText(toolCallId, args)
                TOOL_ADD_IMAGE -> addImage(toolCallId, args)
                TOOL_ADD_SHAPE -> addShape(toolCallId, args)
                TOOL_SET_BACKGROUND -> setBackground(toolCallId, args)
                TOOL_SAVE -> savePresentation(toolCallId, args)
                TOOL_LIST -> listPresentations(toolCallId)
                else -> PresentationToolResult(toolCallId, false, "Herramienta desconocida: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando $toolName", e)
            PresentationToolResult(toolCallId, false, "Error: ${e.message}")
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun inchesToEmu(inches: Double): Long = (inches * EMU_PER_INCH).toLong()

    private fun getSession(args: com.google.gson.JsonObject): PresentationSession? {
        val sessionId = args.get("session_id")?.asString ?: return null
        return sessions[sessionId]
    }

    private fun cleanHex(hex: String): String = hex.removePrefix("#").uppercase()

    private suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) response.body?.bytes() else null
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image: $url", e)
            null
        }
    }

    private fun getContentType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".png") -> "image/png"
            lower.contains(".gif") -> "image/gif"
            lower.contains(".bmp") -> "image/bmp"
            else -> "image/jpeg"
        }
    }

    private fun getImageExtension(contentType: String): String = when (contentType) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/bmp" -> "bmp"
        else -> "jpeg"
    }

    private fun getOutputDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "AIAgents/Presentations"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ── Tool implementations ───────────────────────────────────────────────

    private fun createPresentation(id: String, args: com.google.gson.JsonObject): PresentationToolResult {
        val title = args.get("title")?.asString
            ?: return PresentationToolResult(id, false, "Parametro 'title' requerido")
        val widthInches = args.get("width_inches")?.asDouble ?: 13.333
        val heightInches = args.get("height_inches")?.asDouble ?: 7.5

        val sessionId = "pptx_${System.currentTimeMillis()}"
        sessions[sessionId] = PresentationSession(
            id = sessionId,
            title = title,
            widthEmu = inchesToEmu(widthInches),
            heightEmu = inchesToEmu(heightInches)
        )

        Log.d(TAG, "Presentacion creada: '$title', session=$sessionId")

        return PresentationToolResult(id, true, buildString {
            appendLine("Presentacion creada exitosamente")
            appendLine()
            appendLine("- **Titulo:** $title")
            appendLine("- **Session ID:** $sessionId")
            appendLine("- **Tamano:** ${widthInches}\" x ${heightInches}\"")
            appendLine()
            appendLine("Usa pptx_add_slide para agregar slides, luego pptx_add_text/pptx_add_image/pptx_add_shape para contenido. Finaliza con pptx_save.")
        }.trim())
    }

    private fun addSlide(id: String, args: com.google.gson.JsonObject): PresentationToolResult {
        val session = getSession(args)
            ?: return PresentationToolResult(id, false, "Session no encontrada. Usa pptx_create primero.")

        session.slides.add(SlideData())
        val slideIndex = session.slides.size - 1

        Log.d(TAG, "Slide agregado: index=$slideIndex")

        return PresentationToolResult(id, true, buildString {
            appendLine("Slide agregado: indice=$slideIndex")
            appendLine("Total slides: ${session.slides.size}")
            appendLine("Usa slide_index=$slideIndex para agregar contenido.")
        }.trim())
    }

    private fun addText(id: String, args: com.google.gson.JsonObject): PresentationToolResult {
        val session = getSession(args)
            ?: return PresentationToolResult(id, false, "Session no encontrada.")
        val slideIndex = args.get("slide_index")?.asInt
            ?: return PresentationToolResult(id, false, "Parametro 'slide_index' requerido")
        if (slideIndex < 0 || slideIndex >= session.slides.size)
            return PresentationToolResult(id, false, "slide_index=$slideIndex invalido. Hay ${session.slides.size} slides (0-${session.slides.size - 1}).")
        val text = args.get("text")?.asString
            ?: return PresentationToolResult(id, false, "Parametro 'text' requerido")

        val element = SlideElement.TextBox(
            text = text.replace("\\n", "\n"),
            x = inchesToEmu(args.get("x")?.asDouble ?: 0.5),
            y = inchesToEmu(args.get("y")?.asDouble ?: 0.5),
            cx = inchesToEmu(args.get("width")?.asDouble ?: 12.0),
            cy = inchesToEmu(args.get("height")?.asDouble ?: 1.0),
            fontSize = args.get("font_size")?.asInt ?: 18,
            fontFamily = args.get("font_family")?.asString ?: "Calibri",
            fontColor = cleanHex(args.get("font_color")?.asString ?: "000000"),
            bold = args.get("bold")?.asString == "true",
            italic = args.get("italic")?.asString == "true",
            align = args.get("align")?.asString ?: "l",
            valign = args.get("valign")?.asString ?: "t"
        )

        session.slides[slideIndex].elements.add(element)
        return PresentationToolResult(id, true, "Texto agregado al slide $slideIndex: \"${text.take(60)}${if (text.length > 60) "..." else ""}\"")
    }

    private suspend fun addImage(id: String, args: com.google.gson.JsonObject): PresentationToolResult {
        val session = getSession(args)
            ?: return PresentationToolResult(id, false, "Session no encontrada.")
        val slideIndex = args.get("slide_index")?.asInt
            ?: return PresentationToolResult(id, false, "Parametro 'slide_index' requerido")
        if (slideIndex < 0 || slideIndex >= session.slides.size)
            return PresentationToolResult(id, false, "slide_index=$slideIndex invalido.")
        val url = args.get("url")?.asString
            ?: return PresentationToolResult(id, false, "Parametro 'url' requerido")

        val imageBytes = downloadImage(url)
            ?: return PresentationToolResult(id, false, "Error descargando imagen: $url")
        val contentType = getContentType(url)

        // Store image data in session
        session.images.add(imageBytes to contentType)
        val imageIndex = session.images.size // 1-based for rId

        val element = SlideElement.Image(
            imageBytes = imageBytes,
            contentType = contentType,
            x = inchesToEmu(args.get("x")?.asDouble ?: 1.0),
            y = inchesToEmu(args.get("y")?.asDouble ?: 1.0),
            cx = inchesToEmu(args.get("width")?.asDouble ?: 4.0),
            cy = inchesToEmu(args.get("height")?.asDouble ?: 3.0)
        )

        session.slides[slideIndex].elements.add(element)
        return PresentationToolResult(id, true, "Imagen agregada al slide $slideIndex (${imageBytes.size / 1024} KB)")
    }

    private fun addShape(id: String, args: com.google.gson.JsonObject): PresentationToolResult {
        val session = getSession(args)
            ?: return PresentationToolResult(id, false, "Session no encontrada.")
        val slideIndex = args.get("slide_index")?.asInt
            ?: return PresentationToolResult(id, false, "Parametro 'slide_index' requerido")
        if (slideIndex < 0 || slideIndex >= session.slides.size)
            return PresentationToolResult(id, false, "slide_index=$slideIndex invalido.")

        val element = SlideElement.Shape(
            shapeType = args.get("shape_type")?.asString ?: "rect",
            x = inchesToEmu(args.get("x")?.asDouble ?: 1.0),
            y = inchesToEmu(args.get("y")?.asDouble ?: 1.0),
            cx = inchesToEmu(args.get("width")?.asDouble ?: 4.0),
            cy = inchesToEmu(args.get("height")?.asDouble ?: 2.0),
            fillColor = args.get("fill_color")?.asString?.let { cleanHex(it) },
            lineColor = cleanHex(args.get("line_color")?.asString ?: "000000"),
            lineWidth = args.get("line_width")?.asInt ?: 12700,
            text = args.get("text")?.asString,
            textColor = cleanHex(args.get("text_color")?.asString ?: "FFFFFF"),
            textSize = args.get("text_size")?.asInt ?: 14
        )

        session.slides[slideIndex].elements.add(element)
        return PresentationToolResult(id, true, "Forma '${element.shapeType}' agregada al slide $slideIndex")
    }

    private fun setBackground(id: String, args: com.google.gson.JsonObject): PresentationToolResult {
        val session = getSession(args)
            ?: return PresentationToolResult(id, false, "Session no encontrada.")
        val slideIndex = args.get("slide_index")?.asInt
            ?: return PresentationToolResult(id, false, "Parametro 'slide_index' requerido")
        if (slideIndex < 0 || slideIndex >= session.slides.size)
            return PresentationToolResult(id, false, "slide_index=$slideIndex invalido.")
        val color = args.get("color")?.asString
            ?: return PresentationToolResult(id, false, "Parametro 'color' requerido")

        session.slides[slideIndex].bgColor = cleanHex(color)
        return PresentationToolResult(id, true, "Fondo del slide $slideIndex establecido a #${cleanHex(color)}")
    }

    private suspend fun savePresentation(id: String, args: com.google.gson.JsonObject): PresentationToolResult {
        val session = getSession(args)
            ?: return PresentationToolResult(id, false, "Session no encontrada.")

        if (session.slides.isEmpty())
            return PresentationToolResult(id, false, "La presentacion no tiene slides. Usa pptx_add_slide primero.")

        val filename = args.get("filename")?.asString
            ?: session.title.replace(Regex("[^a-zA-Z0-9áéíóúñÁÉÍÓÚÑ\\s_-]"), "").replace(" ", "_")

        val outputDir = getOutputDir()
        val file = File(outputDir, "$filename.pptx")

        return withContext(Dispatchers.IO) {
            try {
                writePptx(session, file)
                Log.d(TAG, "Presentacion guardada: ${file.absolutePath}")

                PresentationToolResult(id, true, buildString {
                    appendLine("Presentacion guardada exitosamente")
                    appendLine()
                    appendLine("- **Archivo:** ${file.name}")
                    appendLine("- **Ruta:** ${file.absolutePath}")
                    appendLine("- **Slides:** ${session.slides.size}")
                    appendLine("- **Tamano:** ${file.length() / 1024} KB")
                    appendLine()
                    appendLine("El archivo PPTX esta listo para abrir con PowerPoint, Google Slides, o importar a Canva con canva_import_design.")
                }.trim())
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando presentacion", e)
                PresentationToolResult(id, false, "Error al guardar: ${e.message}")
            }
        }
    }

    private fun listPresentations(id: String): PresentationToolResult {
        val formatted = buildString {
            if (sessions.isNotEmpty()) {
                appendLine("Sesiones activas (${sessions.size}):")
                appendLine()
                sessions.values.forEach { s ->
                    appendLine("- **${s.title}** (ID: ${s.id}, ${s.slides.size} slides)")
                }
                appendLine()
            }
            val outputDir = getOutputDir()
            val files = outputDir.listFiles()?.filter { it.extension == "pptx" }?.sortedByDescending { it.lastModified() }
            if (!files.isNullOrEmpty()) {
                appendLine("Archivos guardados (${files.size}):")
                appendLine()
                files.forEach { f ->
                    appendLine("- **${f.name}** (${f.length() / 1024} KB) — ${f.absolutePath}")
                }
            } else if (sessions.isEmpty()) {
                appendLine("No hay presentaciones activas ni archivos guardados.")
                appendLine("Usa pptx_create para crear una nueva.")
            }
        }
        return PresentationToolResult(id, true, formatted.trim())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PPTX Open XML writer
    // ═══════════════════════════════════════════════════════════════════════

    private fun writePptx(session: PresentationSession, file: File) {
        // Collect all images across all slides with global indices
        data class ImageRef(val globalIndex: Int, val bytes: ByteArray, val contentType: String)
        val allImages = mutableListOf<ImageRef>()
        var imgCounter = 0

        // Pre-scan to assign global image indices
        val slideImageMap = mutableMapOf<Int, MutableList<Pair<Int, SlideElement.Image>>>() // slideIdx -> list of (globalImgIdx, element)
        session.slides.forEachIndexed { si, slide ->
            slide.elements.filterIsInstance<SlideElement.Image>().forEach { img ->
                imgCounter++
                allImages.add(ImageRef(imgCounter, img.imageBytes, img.contentType))
                slideImageMap.getOrPut(si) { mutableListOf() }.add(imgCounter to img)
            }
        }

        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zip ->
                // [Content_Types].xml
                zip.writeEntry("[Content_Types].xml", buildContentTypes(session.slides.size, allImages))

                // _rels/.rels
                zip.writeEntry("_rels/.rels", RELS_DOT_RELS)

                // ppt/presentation.xml
                zip.writeEntry("ppt/presentation.xml", buildPresentation(session))

                // ppt/_rels/presentation.xml.rels
                zip.writeEntry("ppt/_rels/presentation.xml.rels", buildPresentationRels(session.slides.size))

                // ppt/slideMasters/slideMaster1.xml
                zip.writeEntry("ppt/slideMasters/slideMaster1.xml", SLIDE_MASTER)

                // ppt/slideMasters/_rels/slideMaster1.xml.rels
                zip.writeEntry("ppt/slideMasters/_rels/slideMaster1.xml.rels", SLIDE_MASTER_RELS)

                // ppt/slideLayouts/slideLayout1.xml
                zip.writeEntry("ppt/slideLayouts/slideLayout1.xml", SLIDE_LAYOUT)

                // ppt/slideLayouts/_rels/slideLayout1.xml.rels
                zip.writeEntry("ppt/slideLayouts/_rels/slideLayout1.xml.rels", SLIDE_LAYOUT_RELS)

                // ppt/theme/theme1.xml
                zip.writeEntry("ppt/theme/theme1.xml", THEME)

                // Each slide
                session.slides.forEachIndexed { index, slide ->
                    val slideImgs = slideImageMap[index] ?: emptyList()
                    zip.writeEntry("ppt/slides/slide${index + 1}.xml", buildSlideXml(slide, slideImgs, session))
                    zip.writeEntry("ppt/slides/_rels/slide${index + 1}.xml.rels", buildSlideRels(slideImgs))
                }

                // Image files
                allImages.forEach { img ->
                    val ext = getImageExtension(img.contentType)
                    zip.putNextEntry(ZipEntry("ppt/media/image${img.globalIndex}.$ext"))
                    zip.write(img.bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ── XML builders ───────────────────────────────────────────────────────

    private fun buildContentTypes(slideCount: Int, images: List<Any>): String {
        val slideOverrides = (1..slideCount).joinToString("\n") {
            """  <Override PartName="/ppt/slides/slide$it.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Default Extension="png" ContentType="image/png"/>
  <Default Extension="gif" ContentType="image/gif"/>
  <Default Extension="bmp" ContentType="image/bmp"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
$slideOverrides
</Types>"""
    }

    private fun buildPresentation(session: PresentationSession): String {
        val slideList = session.slides.indices.joinToString("\n") { i ->
            """    <p:sldId id="${256 + i}" r:id="rId${i + 2}"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
  xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldMasterIdLst>
    <p:sldMasterId id="2147483648" r:id="rId1"/>
  </p:sldMasterIdLst>
  <p:sldIdLst>
$slideList
  </p:sldIdLst>
  <p:sldSz cx="${session.widthEmu}" cy="${session.heightEmu}"/>
  <p:notesSz cx="${session.heightEmu}" cy="${session.widthEmu}"/>
</p:presentation>"""
    }

    private fun buildPresentationRels(slideCount: Int): String {
        val slideRels = (1..slideCount).joinToString("\n") { i ->
            """  <Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$i.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
$slideRels
  <Relationship Id="rId${slideCount + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
</Relationships>"""
    }

    private fun buildSlideXml(slide: SlideData, images: List<Pair<Int, SlideElement.Image>>, session: PresentationSession): String {
        var shapeId = 1
        val elementsXml = StringBuilder()

        // Background
        val bgXml = if (slide.bgColor != null) {
            """
  <p:bg>
    <p:bgPr>
      <a:solidFill><a:srgbClr val="${slide.bgColor}"/></a:solidFill>
      <a:effectLst/>
    </p:bgPr>
  </p:bg>"""
        } else ""

        // Build elements
        for (element in slide.elements) {
            shapeId++
            when (element) {
                is SlideElement.TextBox -> {
                    elementsXml.append(buildTextBoxXml(element, shapeId))
                }
                is SlideElement.Image -> {
                    val imgRef = images.find { it.second === element }
                    if (imgRef != null) {
                        elementsXml.append(buildImageXml(element, imgRef.first, shapeId))
                    }
                }
                is SlideElement.Shape -> {
                    elementsXml.append(buildShapeXml(element, shapeId))
                }
            }
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
  xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>$bgXml
    <p:spTree>
      <p:nvGrpSpPr>
        <p:cNvPr id="1" name=""/>
        <p:cNvGrpSpPr/>
        <p:nvPr/>
      </p:nvGrpSpPr>
      <p:grpSpPr>
        <a:xfrm>
          <a:off x="0" y="0"/>
          <a:ext cx="${session.widthEmu}" cy="${session.heightEmu}"/>
          <a:chOff x="0" y="0"/>
          <a:chExt cx="${session.widthEmu}" cy="${session.heightEmu}"/>
        </a:xfrm>
      </p:grpSpPr>
$elementsXml
    </p:spTree>
  </p:cSld>
</p:sld>"""
    }

    private fun buildTextBoxXml(tb: SlideElement.TextBox, shapeId: Int): String {
        val fontSizeHundredths = tb.fontSize * 100
        val boldAttr = if (tb.bold) """ b="1"""" else ""
        val italicAttr = if (tb.italic) """ i="1"""" else ""

        val paragraphs = tb.text.split("\n").joinToString("\n") { line ->
            val escaped = escapeXml(line)
            """        <a:p>
          <a:pPr algn="${tb.align}"/>
          <a:r>
            <a:rPr lang="es-MX" sz="$fontSizeHundredths"$boldAttr$italicAttr dirty="0">
              <a:solidFill><a:srgbClr val="${tb.fontColor}"/></a:solidFill>
              <a:latin typeface="${tb.fontFamily}"/>
              <a:cs typeface="${tb.fontFamily}"/>
            </a:rPr>
            <a:t>$escaped</a:t>
          </a:r>
        </a:p>"""
        }

        return """
      <p:sp>
        <p:nvSpPr>
          <p:cNvPr id="$shapeId" name="TextBox$shapeId"/>
          <p:cNvSpPr txBox="1"/>
          <p:nvPr/>
        </p:nvSpPr>
        <p:spPr>
          <a:xfrm>
            <a:off x="${tb.x}" y="${tb.y}"/>
            <a:ext cx="${tb.cx}" cy="${tb.cy}"/>
          </a:xfrm>
          <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
          <a:noFill/>
        </p:spPr>
        <p:txBody>
          <a:bodyPr anchor="${tb.valign}" wrap="square" rtlCol="0"/>
          <a:lstStyle/>
$paragraphs
        </p:txBody>
      </p:sp>
"""
    }

    private fun buildImageXml(img: SlideElement.Image, globalImgIndex: Int, shapeId: Int): String {
        return """
      <p:pic>
        <p:nvPicPr>
          <p:cNvPr id="$shapeId" name="Image$shapeId"/>
          <p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr>
          <p:nvPr/>
        </p:nvPicPr>
        <p:blipFill>
          <a:blip r:embed="rId${globalImgIndex + 1}"/>
          <a:stretch><a:fillRect/></a:stretch>
        </p:blipFill>
        <p:spPr>
          <a:xfrm>
            <a:off x="${img.x}" y="${img.y}"/>
            <a:ext cx="${img.cx}" cy="${img.cy}"/>
          </a:xfrm>
          <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
        </p:spPr>
      </p:pic>
"""
    }

    private fun buildShapeXml(shape: SlideElement.Shape, shapeId: Int): String {
        val fillXml = if (shape.fillColor != null) {
            """<a:solidFill><a:srgbClr val="${shape.fillColor}"/></a:solidFill>"""
        } else {
            """<a:noFill/>"""
        }

        val lineXml = """<a:ln w="${shape.lineWidth}"><a:solidFill><a:srgbClr val="${shape.lineColor}"/></a:solidFill></a:ln>"""

        val textXml = if (!shape.text.isNullOrBlank()) {
            val escaped = escapeXml(shape.text)
            val fontSizeHundredths = shape.textSize * 100
            """
        <p:txBody>
          <a:bodyPr anchor="ctr" wrap="square"/>
          <a:lstStyle/>
          <a:p>
            <a:pPr algn="ctr"/>
            <a:r>
              <a:rPr lang="es-MX" sz="$fontSizeHundredths" b="1" dirty="0">
                <a:solidFill><a:srgbClr val="${shape.textColor}"/></a:solidFill>
                <a:latin typeface="Calibri"/>
              </a:rPr>
              <a:t>$escaped</a:t>
            </a:r>
          </a:p>
        </p:txBody>"""
        } else ""

        return """
      <p:sp>
        <p:nvSpPr>
          <p:cNvPr id="$shapeId" name="Shape$shapeId"/>
          <p:cNvSpPr/>
          <p:nvPr/>
        </p:nvSpPr>
        <p:spPr>
          <a:xfrm>
            <a:off x="${shape.x}" y="${shape.y}"/>
            <a:ext cx="${shape.cx}" cy="${shape.cy}"/>
          </a:xfrm>
          <a:prstGeom prst="${shape.shapeType}"><a:avLst/></a:prstGeom>
          $fillXml
          $lineXml
        </p:spPr>$textXml
      </p:sp>
"""
    }

    private fun buildSlideRels(images: List<Pair<Int, SlideElement.Image>>): String {
        val imageRels = images.joinToString("\n") { (globalIdx, img) ->
            val ext = getImageExtension(img.contentType)
            """  <Relationship Id="rId${globalIdx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$globalIdx.$ext"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
$imageRels
</Relationships>"""
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // ── Static XML templates ───────────────────────────────────────────────

    private val RELS_DOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""

    private val SLIDE_MASTER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
  xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg><p:bgPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr/>
    </p:spTree>
  </p:cSld>
  <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2"
    accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
  <p:sldLayoutIdLst>
    <p:sldLayoutId id="2147483649" r:id="rId1"/>
  </p:sldLayoutIdLst>
</p:sldMaster>"""

    private val SLIDE_MASTER_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>"""

    private val SLIDE_LAYOUT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
  xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank">
  <p:cSld>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr/>
    </p:spTree>
  </p:cSld>
</p:sldLayout>"""

    private val SLIDE_LAYOUT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""

    private val THEME = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Default">
  <a:themeElements>
    <a:clrScheme name="Default">
      <a:dk1><a:srgbClr val="000000"/></a:dk1>
      <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
      <a:dk2><a:srgbClr val="44546A"/></a:dk2>
      <a:lt2><a:srgbClr val="E7E6E6"/></a:lt2>
      <a:accent1><a:srgbClr val="4472C4"/></a:accent1>
      <a:accent2><a:srgbClr val="ED7D31"/></a:accent2>
      <a:accent3><a:srgbClr val="A5A5A5"/></a:accent3>
      <a:accent4><a:srgbClr val="FFC000"/></a:accent4>
      <a:accent5><a:srgbClr val="5B9BD5"/></a:accent5>
      <a:accent6><a:srgbClr val="70AD47"/></a:accent6>
      <a:hlink><a:srgbClr val="0563C1"/></a:hlink>
      <a:folHlink><a:srgbClr val="954F72"/></a:folHlink>
    </a:clrScheme>
    <a:fontScheme name="Default">
      <a:majorFont><a:latin typeface="Calibri Light"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont>
      <a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont>
    </a:fontScheme>
    <a:fmtScheme name="Default">
      <a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst>
      <a:lnStyleLst><a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst>
      <a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>
      <a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst>
    </a:fmtScheme>
  </a:themeElements>
</a:theme>"""
}
