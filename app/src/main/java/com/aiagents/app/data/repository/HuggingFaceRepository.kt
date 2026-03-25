package com.aiagents.app.data.repository

import android.util.Log
import com.aiagents.app.data.remote.HFFileInfo
import com.aiagents.app.data.remote.HFModelInfo
import com.aiagents.app.data.remote.HuggingFaceApiService
import com.aiagents.app.domain.model.RecommendedModels
import javax.inject.Inject
import javax.inject.Singleton

data class HFBrowsableFile(
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String
)

data class HFBrowsableModel(
    val repoId: String,
    val repoName: String,
    val author: String,
    val files: List<HFBrowsableFile>,
    val downloads: Int,
    val gated: Boolean,
    val tags: List<String>
)

@Singleton
class HuggingFaceRepository @Inject constructor(
    private val apiService: HuggingFaceApiService
) {
    private val TAG = "HuggingFaceRepository"

    // In-memory cache: author -> (timestamp, results)
    private val cache = mutableMapOf<String, Pair<Long, List<HFBrowsableModel>>>()
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    // Repo IDs already in RecommendedModels (to exclude from browse results)
    private val recommendedRepoIds: Set<String> by lazy {
        RecommendedModels.MODELS.mapNotNull { model ->
            extractRepoId(model.huggingFaceUrl)
        }.toSet()
    }

    suspend fun searchCompatibleModels(author: String): Result<List<HFBrowsableModel>> {
        // Check cache
        cache[author]?.let { (timestamp, results) ->
            if (System.currentTimeMillis() - timestamp < CACHE_TTL_MS) {
                return Result.success(results)
            }
        }

        return try {
            val models = apiService.searchModels(author = author)
            val browsableModels = models.mapNotNull { model ->
                try {
                    val files = apiService.getModelFiles(model.id)
                    val compatibleFiles = files.filter { file ->
                        file.type == "file" &&
                            (file.filename.endsWith(".task") || file.filename.endsWith(".bin") || file.filename.endsWith(".litertlm"))
                    }
                    if (compatibleFiles.isEmpty()) return@mapNotNull null

                    // Exclude models already in RecommendedModels
                    if (model.id in recommendedRepoIds) return@mapNotNull null

                    val parts = model.id.split("/", limit = 2)
                    HFBrowsableModel(
                        repoId = model.id,
                        repoName = parts.getOrElse(1) { model.id },
                        author = parts.getOrElse(0) { author },
                        files = compatibleFiles.map { file ->
                            HFBrowsableFile(
                                fileName = file.filename,
                                sizeBytes = file.size,
                                downloadUrl = "https://huggingface.co/${model.id}/resolve/main/${file.filename}"
                            )
                        },
                        downloads = model.downloads,
                        gated = model.isGated,
                        tags = model.tags
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get files for ${model.id}: ${e.message}")
                    null
                }
            }.sortedByDescending { it.downloads }

            // Update cache
            cache[author] = System.currentTimeMillis() to browsableModels
            Result.success(browsableModels)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching models for author=$author", e)
            Result.failure(e)
        }
    }

    suspend fun resolveRepoUrl(url: String): Result<HFBrowsableModel> {
        return try {
            val repoId = parseRepoIdFromUrl(url)
                ?: return Result.failure(IllegalArgumentException("URL no válida de HuggingFace. Formato esperado: huggingface.co/org/model"))

            val models = apiService.searchModels(author = repoId.split("/").first())
            val modelInfo = models.find { it.id == repoId }

            val files = apiService.getModelFiles(repoId)
            val compatibleFiles = files.filter { file ->
                file.type == "file" &&
                    (file.filename.endsWith(".task") || file.filename.endsWith(".bin") || file.filename.endsWith(".litertlm"))
            }

            if (compatibleFiles.isEmpty()) {
                return Result.failure(IllegalArgumentException("No se encontraron archivos .task o .bin en este repositorio. MediaPipe solo soporta estos formatos."))
            }

            val parts = repoId.split("/", limit = 2)
            Result.success(
                HFBrowsableModel(
                    repoId = repoId,
                    repoName = parts.getOrElse(1) { repoId },
                    author = parts.getOrElse(0) { "" },
                    files = compatibleFiles.map { file ->
                        HFBrowsableFile(
                            fileName = file.filename,
                            sizeBytes = file.size,
                            downloadUrl = "https://huggingface.co/$repoId/resolve/main/${file.filename}"
                        )
                    },
                    downloads = modelInfo?.downloads ?: 0,
                    gated = modelInfo?.isGated ?: false,
                    tags = modelInfo?.tags ?: emptyList()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URL: $url", e)
            Result.failure(e)
        }
    }

    private fun parseRepoIdFromUrl(url: String): String? {
        // Support formats:
        // huggingface.co/org/repo
        // huggingface.co/org/repo/tree/main
        // huggingface.co/org/repo/resolve/main/file.task
        // https://huggingface.co/org/repo
        val cleaned = url
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("huggingface.co/")

        val parts = cleaned.split("/")
        if (parts.size < 2) return null
        return "${parts[0]}/${parts[1]}"
    }

    private fun extractRepoId(url: String): String? {
        if (url.isBlank()) return null
        return parseRepoIdFromUrl(url)
    }
}
