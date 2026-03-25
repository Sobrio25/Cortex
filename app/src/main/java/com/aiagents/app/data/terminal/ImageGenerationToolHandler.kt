package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class ImageGenerationResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class ImageGenerationToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "ImageGenerationToolHandler"
        const val TOOL_NAME_DALLE = "generate_image_dalle"
        const val TOOL_NAME_GOOGLE_IMAGEN = "generate_image_google"
        const val TOOL_NAME_EDIT_IMAGE = "edit_image_dalle"
        const val TOOL_NAME_VARIATION = "image_variation_dalle"

        val ALL_TOOL_NAMES = setOf(TOOL_NAME_DALLE, TOOL_NAME_GOOGLE_IMAGEN, TOOL_NAME_EDIT_IMAGE, TOOL_NAME_VARIATION)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME_DALLE,
                    "description" to "Generate an image using DALL-E 3. Creates high-quality images from text descriptions. Use for: illustrations, concept art, diagrams, creative images, visual content. IMPORTANT: After generation, display the image using Markdown format: ![description](url)",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "prompt" to mapOf(
                                "type" to "string",
                                "description" to "Detailed description of the image to generate. Be specific about style, colors, composition, lighting, and subject. Max 4000 characters."
                            ),
                            "size" to mapOf(
                                "type" to "string",
                                "description" to "Image size",
                                "enum" to listOf("1024x1024", "1792x1024", "1024x1792")
                            ),
                            "quality" to mapOf(
                                "type" to "string",
                                "description" to "Image quality. 'hd' creates finer details and better consistency for faces and text.",
                                "enum" to listOf("standard", "hd")
                            ),
                            "style" to mapOf(
                                "type" to "string",
                                "description" to "Visual style of the image",
                                "enum" to listOf("vivid", "natural")
                            )
                        ),
                        "required" to listOf("prompt")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME_GOOGLE_IMAGEN,
                    "description" to "Generate an image using Gemini 3.1 Flash Image Preview (Nano Banana 2). Google's high-efficiency image generation model, optimized for speed and high-volume use cases. Use for: illustrations, concept art, diagrams, creative images, photorealistic scenes. IMPORTANT: After generation, display the image using Markdown format: ![description](url)",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "prompt" to mapOf(
                                "type" to "string",
                                "description" to "Detailed description of the image to generate. Be specific about style, composition, and subject."
                            )
                        ),
                        "required" to listOf("prompt")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME_EDIT_IMAGE,
                    "description" to "Edit an existing image using DALL-E. Requires the original image URL and a mask indicating which areas to edit. Use for: modifying parts of an image, inpainting, changing specific elements.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "image_url" to mapOf(
                                "type" to "string",
                                "description" to "URL of the image to edit"
                            ),
                            "prompt" to mapOf(
                                "type" to "string",
                                "description" to "Description of the desired edit. Should describe the entire new image, not just the changed area."
                            ),
                            "size" to mapOf(
                                "type" to "string",
                                "description" to "Output image size",
                                "enum" to listOf("1024x1024", "1792x1024", "1024x1792")
                            )
                        ),
                        "required" to listOf("image_url", "prompt")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME_VARIATION,
                    "description" to "Create variations of an existing image. Generates similar images with differences in composition, style, or details. Use for: exploring alternatives of a generated image.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "image_url" to mapOf(
                                "type" to "string",
                                "description" to "URL of the source image"
                            ),
                            "n" to mapOf(
                                "type" to "integer",
                                "description" to "Number of variations (1-4, default 2)"
                            ),
                            "size" to mapOf(
                                "type" to "string",
                                "description" to "Output image size",
                                "enum" to listOf("1024x1024", "1792x1024", "1024x1792")
                            )
                        ),
                        "required" to listOf("image_url")
                    )
                )
            )
        )
    }

    suspend fun executeGenerateImage(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): ImageGenerationResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val prompt = args.get("prompt")?.asString
                ?: return ImageGenerationResult(toolCallId, false, "Error: parámetro 'prompt' requerido")
            val size = args.get("size")?.asString ?: "1024x1024"
            val quality = args.get("quality")?.asString ?: "standard"
            val style = args.get("style")?.asString ?: "vivid"

            Log.d(TAG, "Generando imagen: prompt='${prompt.take(50)}...', size=$size")

            if (prompt.length > 4000) {
                return ImageGenerationResult(toolCallId, false, "Error: El prompt excede los 4000 caracteres")
            }

            val requestBody = JsonObject().apply {
                addProperty("model", "dall-e-3")
                addProperty("prompt", prompt)
                addProperty("n", 1)
                addProperty("size", size)
                addProperty("quality", quality)
                addProperty("style", style)
                addProperty("response_format", "url")
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/images/generations")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val (responseCode, body) = withContext(Dispatchers.IO) {
                val resp = okHttpClient.newCall(request).execute()
                resp.code to (resp.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                Log.e(TAG, "DALL-E error: $responseCode - $body")
                val errorMsg = parseOpenAIError(body)
                return ImageGenerationResult(toolCallId, false, "Error al generar imagen: $errorMsg")
            }

            val json = JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonArray("data")

            if (data == null || data.size() == 0) {
                return ImageGenerationResult(toolCallId, false, "No se recibió imagen de la API")
            }

            val imageData = data.get(0).asJsonObject
            val imageUrl = imageData.get("url")?.asString
                ?: return ImageGenerationResult(toolCallId, false, "URL de imagen no encontrada")
            val revisedPrompt = imageData.get("revised_prompt")?.asString

            val formatted = buildString {
                appendLine("🎨 **Imagen generada con DALL-E 3**")
                appendLine()
                appendLine("**Prompt original:**")
                appendLine(prompt)
                if (revisedPrompt != null && revisedPrompt != prompt) {
                    appendLine()
                    appendLine("**Prompt mejorado por DALL-E:**")
                    appendLine(revisedPrompt)
                }
                appendLine()
                appendLine("**Configuración:**")
                appendLine("• Tamaño: $size")
                appendLine("• Calidad: $quality")
                appendLine("• Estilo: $style")
                appendLine()
                appendLine("![Imagen generada: ${prompt.take(100)}]($imageUrl)")
            }

            ImageGenerationResult(toolCallId, true, formatted.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error generando imagen", e)
            ImageGenerationResult(toolCallId, false, "Error al generar imagen: ${e.message}")
        }
    }

    suspend fun executeGenerateImageGoogle(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): ImageGenerationResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val prompt = args.get("prompt")?.asString
                ?: return ImageGenerationResult(toolCallId, false, "Error: parámetro 'prompt' requerido")

            Log.d(TAG, "Generando imagen con Gemini 3.1 Flash Image Preview: prompt='${prompt.take(50)}...'")

            // Gemini 3.1 Flash Image Preview (Nano Banana 2)
            // Modelo de alta eficiencia para generación de imágenes
            val requestBody = JsonObject().apply {
                val contents = com.google.gson.JsonArray()
                val content = JsonObject().apply {
                    val parts = com.google.gson.JsonArray()
                    val textPart = JsonObject().apply {
                        addProperty("text", prompt)
                    }
                    parts.add(textPart)
                    add("parts", parts)
                    addProperty("role", "user")
                }
                contents.add(content)
                add("contents", contents)

                val generationConfig = JsonObject().apply {
                    addProperty("responseModalities", "[Text, Image]")
                }
                add("generationConfig", generationConfig)
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val (responseCode, body) = withContext(Dispatchers.IO) {
                val resp = okHttpClient.newCall(request).execute()
                resp.code to (resp.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                Log.e(TAG, "Gemini API error: $responseCode - $body")
                return ImageGenerationResult(toolCallId, false, "Error HTTP $responseCode al generar imagen. Verifica tu API key y que tengas acceso a Gemini 3.1 Flash Image Preview.")
            }

            parseGeminiImageResponse(toolCallId, prompt, body)
        } catch (e: Exception) {
            Log.e(TAG, "Error generando imagen con Gemini", e)
            ImageGenerationResult(toolCallId, false, "Error al generar imagen: ${e.message}")
        }
    }

    private fun parseGeminiImageResponse(
        toolCallId: String,
        prompt: String,
        body: String
    ): ImageGenerationResult {
        val json = JsonParser.parseString(body).asJsonObject

        // La respuesta de Gemini incluye la imagen en candidates[0].content.parts
        val candidates = json.getAsJsonArray("candidates")
            ?: return ImageGenerationResult(toolCallId, false, "No se recibieron candidatos de la API")

        if (candidates.size() == 0) {
            return ImageGenerationResult(toolCallId, false, "No se generó ninguna imagen")
        }

        val candidate = candidates.get(0).asJsonObject
        val content = candidate.getAsJsonObject("content")
        val parts = content?.getAsJsonArray("parts")

        if (parts == null || parts.size() == 0) {
            return ImageGenerationResult(toolCallId, false, "No se encontró contenido en la respuesta")
        }

        // Buscar la parte que contiene la imagen inline
        var imageBase64: String? = null
        var responseText: String? = null

        for (i in 0 until parts.size()) {
            val part = parts.get(i).asJsonObject
            if (part.has("inlineData")) {
                val inlineData = part.getAsJsonObject("inlineData")
                imageBase64 = inlineData?.get("data")?.asString
            }
            if (part.has("text")) {
                responseText = part.get("text")?.asString
            }
        }

        if (imageBase64 == null) {
            return ImageGenerationResult(toolCallId, false, "No se encontró imagen en la respuesta. Respuesta: $responseText")
        }

        // Crear data URL
        val mimeType = "image/png"
        val dataUrl = "data:$mimeType;base64,$imageBase64"

        val formatted = buildString {
            appendLine("🎨 **Imagen generada con Gemini 3.1 Flash Image Preview (Nano Banana 2)**")
            appendLine()
            appendLine("**Prompt:**")
            appendLine(prompt)
            if (!responseText.isNullOrBlank()) {
                appendLine()
                appendLine("**Descripción:**")
                appendLine(responseText)
            }
            appendLine()
            appendLine("![Imagen generada: ${prompt.take(100)}]($dataUrl)")
        }

        return ImageGenerationResult(toolCallId, true, formatted.trim())
    }

    suspend fun executeEditImage(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): ImageGenerationResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val imageUrl = args.get("image_url")?.asString
                ?: return ImageGenerationResult(toolCallId, false, "Error: parámetro 'image_url' requerido")
            val prompt = args.get("prompt")?.asString
                ?: return ImageGenerationResult(toolCallId, false, "Error: parámetro 'prompt' requerido")
            val size = args.get("size")?.asString ?: "1024x1024"

            Log.d(TAG, "Editando imagen: url='$imageUrl', prompt='${prompt.take(50)}...'")

            // Para editar, necesitamos descargar la imagen primero
            // Nota: La API de edición requiere enviar la imagen como multipart/form-data
            // Esto es una versión simplificada que usa la API de variaciones si no hay máscara

            return ImageGenerationResult(
                toolCallId,
                false,
                "La edición de imágenes requiere descargar y procesar la imagen localmente. " +
                "Usa 'generate_image_dalle' para crear una nueva versión con el prompt modificado, " +
                "o describe los cambios que quieres hacer y generaré una nueva imagen."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error editando imagen", e)
            ImageGenerationResult(toolCallId, false, "Error al editar imagen: ${e.message}")
        }
    }

    suspend fun executeImageVariation(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): ImageGenerationResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val imageUrl = args.get("image_url")?.asString
                ?: return ImageGenerationResult(toolCallId, false, "Error: parámetro 'image_url' requerido")
            val n = args.get("n")?.asInt?.coerceIn(1, 4) ?: 2
            val size = args.get("size")?.asString ?: "1024x1024"

            Log.d(TAG, "Creando variaciones: url='$imageUrl', n=$n")

            // Nota: La API de variaciones requiere la imagen local
            // Por ahora, informamos al usuario sobre la limitación
            return ImageGenerationResult(
                toolCallId,
                false,
                "La generación de variaciones requiere la imagen en formato local. " +
                "Como alternativa, puedo generar nuevas imágenes con prompts similares. " +
                "Por favor, describe qué aspectos te gustaría variar."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creando variaciones", e)
            ImageGenerationResult(toolCallId, false, "Error al crear variaciones: ${e.message}")
        }
    }

    private fun parseOpenAIError(body: String): String {
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            json.getAsJsonObject("error")?.get("message")?.asString
                ?: json.get("error")?.asString
                ?: "Error desconocido de la API"
        } catch (e: Exception) {
            body.take(200)
        }
    }
}
