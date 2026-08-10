package com.aiagents.app.data.knowledge

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device semantic embeddings via MediaPipe Text Embedder
 * (Universal Sentence Encoder, multilingual — 16 languages incl. Spanish).
 *
 * The model file is downloaded once to filesDir/models/text_embedder.tflite;
 * see [EmbeddingModelDownloader]. All embedding calls are thread-safe and
 * lazily initialize the native task.
 */
@Singleton
class TextEmbedderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var embedder: TextEmbedder? = null

    fun modelFile(): File = File(context.filesDir, "models/text_embedder.tflite")

    fun isModelReady(): Boolean = modelFile().exists()

    @Synchronized
    fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val task = ensureEmbedder() ?: throw IllegalStateException(
            "Embedding model is not downloaded. Open Settings > Knowledge Base and download it."
        )
        return texts.map { text ->
            task.embed(text).embeddingResult().embeddingAt(0).embeddingAsFloatArray()
        }
    }

    @Synchronized
    private fun ensureEmbedder(): TextEmbedder? {
        embedder?.let { return it }
        if (!isModelReady()) return null
        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(modelFile().absolutePath)
                    .build()
            )
            .build()
        return try {
            TextEmbedder.createFromOptions(context, options).also { embedder = it }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to create TextEmbedder", e)
            null
        }
    }

    private companion object {
        const val TAG = "TextEmbedderManager"
    }
}
