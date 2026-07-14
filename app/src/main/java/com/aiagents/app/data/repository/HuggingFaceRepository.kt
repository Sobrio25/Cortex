package com.aiagents.app.data.repository

import android.util.Log
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.remote.HFFileInfo
import com.aiagents.app.data.remote.HFModelInfo
import com.aiagents.app.data.remote.HuggingFaceApiService
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

enum class HFLocalModelFormat(val displayName: String) {
    LITERT_LM("LiteRT-LM"),
    MEDIAPIPE_TASK("MediaPipe Task"),
    MEDIAPIPE_BIN("MediaPipe Bin")
}

data class HFBrowsableFile(
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val format: HFLocalModelFormat,
    val sha256: String? = null,
    val deviceHint: String? = null
)

data class HFBrowsableModel(
    val repoId: String,
    val repoName: String,
    val author: String,
    val files: List<HFBrowsableFile>,
    val downloads: Long,
    val likes: Int,
    val gated: Boolean,
    val isPrivate: Boolean,
    val tags: List<String>,
    val libraryName: String?,
    val revisionSha: String?,
    val lastModified: String?
) {
    val isLocallyCompatible: Boolean get() = files.isNotEmpty()
}

data class HFSearchPage(
    val models: List<HFBrowsableModel>,
    val nextPageUrl: String?
)

@Singleton
class HuggingFaceRepository @Inject constructor(
    private val apiService: HuggingFaceApiService,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "HuggingFaceRepository"
        private const val PAGE_SIZE = 20
        private const val MAX_TREE_PAGES = 10
        private const val FILE_CACHE_TTL_MS = 10 * 60 * 1000L

        private val NEXT_LINK_REGEX = Regex(
            pattern = """<([^>]+)>\s*;\s*rel\s*=\s*[\"']?next[\"']?""",
            option = RegexOption.IGNORE_CASE
        )

        private val REPO_SEGMENT_REGEX = Regex("^[A-Za-z0-9._-]+$")

        private val LLM_MARKERS = listOf(
            "text-generation",
            "text_generation",
            "causal-lm",
            "causal_lm",
            "litert-lm",
            "litertlm",
            "llm-inference",
            "llm_inference",
            "gemma",
            "llama",
            "qwen",
            "mistral",
            "phi-",
            "phi_",
            "smollm",
            "deepseek",
            "tinyllama"
        )

        private val ANDROID_RUNTIME_MARKERS = listOf(
            "mediapipe",
            "tflite",
            "lite-rt",
            "litert",
            "llm-inference",
            "llm_inference"
        )

        private val UNSUPPORTED_BIN_NAMES = listOf(
            "pytorch_model",
            "training_args",
            "optimizer",
            "model-0000",
            "consolidated."
        )

        internal fun parseNextPageUrl(linkHeader: String?): String? {
            val candidate = linkHeader
                ?.let { NEXT_LINK_REGEX.find(it)?.groupValues?.getOrNull(1) }
                ?: return null

            return runCatching { URI(candidate) }
                .getOrNull()
                ?.takeIf { uri ->
                    uri.scheme.equals("https", ignoreCase = true) &&
                        uri.host.equals("huggingface.co", ignoreCase = true) &&
                        uri.path.startsWith("/api/models")
                }
                ?.toString()
        }

        internal fun parseRepoIdFromUrl(value: String): String? {
            val input = value.trim()
            if (input.isBlank()) return null

            val path = when {
                input.startsWith("https://", ignoreCase = true) ||
                    input.startsWith("http://", ignoreCase = true) -> {
                    val uri = runCatching { URI(input) }.getOrNull() ?: return null
                    if (!uri.host.equals("huggingface.co", ignoreCase = true) &&
                        !uri.host.equals("www.huggingface.co", ignoreCase = true)
                    ) {
                        return null
                    }
                    uri.path.orEmpty()
                }

                input.startsWith("huggingface.co/", ignoreCase = true) ->
                    input.substringAfter("/", "")

                else -> input.substringBefore('?').substringBefore('#')
            }

            val parts = path.trim('/').split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            if (!parts[0].matches(REPO_SEGMENT_REGEX) || !parts[1].matches(REPO_SEGMENT_REGEX)) {
                return null
            }
            return "${parts[0]}/${parts[1]}"
        }

        internal fun detectLocalFormat(
            repoId: String,
            fileName: String,
            tags: List<String>,
            libraryName: String?
        ): HFLocalModelFormat? {
            val lowerFileName = fileName.lowercase()
            val searchableMetadata = buildString {
                append(repoId.lowercase())
                append(' ')
                append(fileName.lowercase())
                append(' ')
                append(libraryName.orEmpty().lowercase())
                append(' ')
                append(tags.joinToString(" ").lowercase())
            }
            val hasLlmSignal = LLM_MARKERS.any(searchableMetadata::contains)

            return when {
                lowerFileName.endsWith(".litertlm") -> HFLocalModelFormat.LITERT_LM

                lowerFileName.endsWith(".task") &&
                    !lowerFileName.endsWith("-web.task") &&
                    !lowerFileName.endsWith("_web.task") &&
                    hasLlmSignal -> HFLocalModelFormat.MEDIAPIPE_TASK

                lowerFileName.endsWith(".bin") &&
                    hasLlmSignal &&
                    ANDROID_RUNTIME_MARKERS.any(searchableMetadata::contains) &&
                    UNSUPPORTED_BIN_NAMES.none(lowerFileName::contains) ->
                    HFLocalModelFormat.MEDIAPIPE_BIN

                else -> null
            }
        }

        private fun buildDownloadUrl(repoId: String, revision: String, fileName: String): String {
            fun encodePathSegment(segment: String): String = URLEncoder
                .encode(segment, StandardCharsets.UTF_8.name())
                .replace("+", "%20")

            val encodedRepo = repoId.split('/').joinToString("/") { encodePathSegment(it) }
            val encodedFile = fileName.split('/').joinToString("/") { encodePathSegment(it) }
            return "https://huggingface.co/$encodedRepo/resolve/${encodePathSegment(revision)}/$encodedFile"
        }

        private fun inferDeviceHint(fileName: String): String? {
            val value = fileName.lowercase()
            return when {
                Regex("(?:sm|snapdragon)[-_]?(?:8|7|6)\\d{2,3}").containsMatchIn(value) ->
                    "Optimizado para un Snapdragon específico"

                Regex("(?:mt|dimensity)[-_]?\\d{4}").containsMatchIn(value) ->
                    "Optimizado para un MediaTek específico"

                value.contains("tensor") || value.contains("pixel") ->
                    "Optimizado para Google Tensor/Pixel"

                value.contains("web") -> "Variante web; no recomendada para Android nativo"
                else -> null
            }
        }
    }

    private val fileCache = mutableMapOf<String, Pair<Long, List<HFBrowsableFile>>>()

    suspend fun searchModels(query: String, nextPageUrl: String? = null): Result<HFSearchPage> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return Result.failure(IllegalArgumentException("Escribe un nombre de modelo para buscar"))
        }

        return try {
            val response = if (nextPageUrl == null) {
                apiService.searchModels(
                    search = normalizedQuery,
                    limit = PAGE_SIZE,
                    authorization = authorizationHeader()
                )
            } else {
                val trustedPageUrl = parseNextPageUrl("<$nextPageUrl>; rel=\"next\"")
                    ?: return Result.failure(IllegalArgumentException("Página de Hugging Face no válida"))
                apiService.getModelsPage(trustedPageUrl, authorizationHeader())
            }

            val modelInfos = response.requireBody("buscar modelos")
            Result.success(
                HFSearchPage(
                    models = modelInfos.map(::toBrowsableModel),
                    nextPageUrl = parseNextPageUrl(response.headers()["Link"])
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Hugging Face models for query=$normalizedQuery", e)
            Result.failure(e)
        }
    }

    suspend fun resolveRepoUrl(value: String): Result<HFBrowsableModel> {
        val repoId = parseRepoIdFromUrl(value)
            ?: return Result.failure(
                IllegalArgumentException(
                    "URL o repositorio no válido. Usa el formato organización/modelo"
                )
            )

        return try {
            val response = apiService.getModelInfo(repoId, authorizationHeader())
            val model = toBrowsableModel(response.requireBody("consultar el repositorio"))
            val detailedFiles = loadCompatibleFiles(model).getOrElse { model.files }
            Result.success(model.copy(files = detailedFiles))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving Hugging Face repository $repoId", e)
            Result.failure(e)
        }
    }

    suspend fun loadCompatibleFiles(model: HFBrowsableModel): Result<List<HFBrowsableFile>> {
        fileCache[model.repoId]?.let { (timestamp, files) ->
            if (System.currentTimeMillis() - timestamp < FILE_CACHE_TTL_MS) {
                return Result.success(files)
            }
        }

        return try {
            val revision = model.revisionSha ?: "main"
            val collected = mutableListOf<HFFileInfo>()
            var response = apiService.getModelFiles(
                repoId = model.repoId,
                revision = revision,
                authorization = authorizationHeader()
            )
            var pageCount = 0

            while (true) {
                collected += response.requireBody("consultar los archivos del modelo")
                pageCount += 1
                val nextPageUrl = parseNextPageUrl(response.headers()["Link"])
                if (nextPageUrl == null || pageCount >= MAX_TREE_PAGES) break
                response = apiService.getModelFilesPage(nextPageUrl, authorizationHeader())
            }

            val files = collected
                .asSequence()
                .filter { it.type == "file" }
                .mapNotNull { file ->
                    val format = detectLocalFormat(
                        repoId = model.repoId,
                        fileName = file.fileName,
                        tags = model.tags,
                        libraryName = model.libraryName
                    ) ?: return@mapNotNull null

                    HFBrowsableFile(
                        fileName = file.fileName,
                        sizeBytes = file.resolvedSize,
                        downloadUrl = buildDownloadUrl(model.repoId, revision, file.fileName),
                        format = format,
                        sha256 = file.lfs?.oid,
                        deviceHint = inferDeviceHint(file.fileName)
                    )
                }
                .distinctBy { it.fileName }
                .sortedBy { it.fileName }
                .toList()

            fileCache[model.repoId] = System.currentTimeMillis() to files
            Result.success(files)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error loading files for ${model.repoId}", e)
            Result.failure(e)
        }
    }

    private fun toBrowsableModel(model: HFModelInfo): HFBrowsableModel {
        val parts = model.id.split('/', limit = 2)
        val author = model.author ?: parts.firstOrNull().orEmpty()
        val revision = model.sha?.takeIf { it.isNotBlank() } ?: "main"
        val files = model.siblings.mapNotNull { sibling ->
            val format = detectLocalFormat(
                repoId = model.id,
                fileName = sibling.fileName,
                tags = model.tags,
                libraryName = model.libraryName
            ) ?: return@mapNotNull null

            HFBrowsableFile(
                fileName = sibling.fileName,
                sizeBytes = 0,
                downloadUrl = buildDownloadUrl(model.id, revision, sibling.fileName),
                format = format,
                deviceHint = inferDeviceHint(sibling.fileName)
            )
        }

        return HFBrowsableModel(
            repoId = model.id,
            repoName = parts.getOrElse(1) { model.id },
            author = author,
            files = files,
            downloads = model.downloads,
            likes = model.likes,
            gated = model.isGated,
            isPrivate = model.isPrivate,
            tags = model.tags,
            libraryName = model.libraryName,
            revisionSha = model.sha,
            lastModified = model.lastModified
        )
    }

    private fun authorizationHeader(): String? = securePreferences
        .getHuggingFaceToken()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { "Bearer $it" }

    private fun <T> Response<T>.requireBody(action: String): T {
        if (!isSuccessful) {
            val message = when (code()) {
                401 -> "El token de Hugging Face es inválido o expiró"
                403 -> "No tienes acceso. Acepta la licencia del modelo y revisa tu token"
                404 -> "El modelo ya no existe o el repositorio no es accesible"
                429 -> "Hugging Face limitó temporalmente las búsquedas. Intenta más tarde"
                in 500..599 -> "Hugging Face no está disponible temporalmente"
                else -> "Hugging Face respondió HTTP ${code()} al $action"
            }
            throw IOException(message)
        }
        return body() ?: throw IOException("Hugging Face devolvió una respuesta vacía al $action")
    }
}
