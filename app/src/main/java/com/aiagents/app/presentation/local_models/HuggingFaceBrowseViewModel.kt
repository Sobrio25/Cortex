package com.aiagents.app.presentation.local_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.CustomLocalModelDao
import com.aiagents.app.data.model.CustomLocalModelEntity
import com.aiagents.app.data.repository.HFBrowsableFile
import com.aiagents.app.data.repository.HFBrowsableModel
import com.aiagents.app.data.repository.HFSearchPage
import com.aiagents.app.data.repository.HuggingFaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class HuggingFaceBrowseViewModel @Inject constructor(
    private val huggingFaceRepository: HuggingFaceRepository,
    private val customLocalModelDao: CustomLocalModelDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _browseResults = MutableStateFlow<List<HFBrowsableModel>>(emptyList())
    val browseResults: StateFlow<List<HFBrowsableModel>> = _browseResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private val _loadingFileRepos = MutableStateFlow<Set<String>>(emptySet())
    val loadingFileRepos: StateFlow<Set<String>> = _loadingFileRepos.asStateFlow()

    private val _fileErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val fileErrors: StateFlow<Map<String, String>> = _fileErrors.asStateFlow()

    private val _addedFileKeys = MutableStateFlow<Set<String>>(emptySet())
    val addedFileKeys: StateFlow<Set<String>> = _addedFileKeys.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _addedMessage = MutableStateFlow<String?>(null)
    val addedMessage: StateFlow<String?> = _addedMessage.asStateFlow()

    private var nextPageUrl: String? = null
    private var activeQuery: String = ""
    private var searchGeneration = 0
    private var debounceJob: Job? = null
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            _addedFileKeys.value = customLocalModelDao.getAllSync()
                .mapNotNull { entity ->
                    entity.id.takeIf { it.startsWith("hf-") }?.removePrefix("hf-")
                }
                .toSet()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        debounceJob?.cancel()

        val normalized = query.trim()
        if (normalized.isBlank()) {
            clearSearchResults()
            return
        }

        debounceJob = viewModelScope.launch {
            delay(450)
            launchInitialSearch(normalized)
        }
    }

    fun submitSearch() {
        debounceJob?.cancel()
        val query = _searchQuery.value.trim()
        if (query.isBlank()) return
        launchInitialSearch(query)
    }

    fun selectSuggestion(query: String) {
        _searchQuery.value = query
        debounceJob?.cancel()
        launchInitialSearch(query)
    }

    fun clearSearch() {
        _searchQuery.value = ""
        debounceJob?.cancel()
        clearSearchResults()
    }

    fun loadNextPage() {
        val pageUrl = nextPageUrl ?: return
        if (_isLoadingMore.value || _isLoading.value) return

        val generation = searchGeneration
        val query = activeQuery
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _isLoadingMore.value = true
            _error.value = null

            huggingFaceRepository.searchModels(query, pageUrl)
                .onSuccess { page ->
                    if (generation != searchGeneration) return@onSuccess
                    _browseResults.value = (_browseResults.value + page.models)
                        .distinctBy { it.repoId }
                    nextPageUrl = page.nextPageUrl
                    _hasMore.value = page.nextPageUrl != null
                }
                .onFailure { throwable ->
                    if (generation == searchGeneration) {
                        _error.value = throwable.userFacingMessage("No se pudo cargar la siguiente página")
                    }
                }

            if (generation == searchGeneration) {
                _isLoadingMore.value = false
            }
        }
    }

    fun loadModelFiles(model: HFBrowsableModel) {
        if (model.repoId in _loadingFileRepos.value) return
        if (model.files.isNotEmpty() && model.files.all { it.sizeBytes > 0 }) return

        viewModelScope.launch {
            _loadingFileRepos.value = _loadingFileRepos.value + model.repoId
            _fileErrors.value = _fileErrors.value - model.repoId

            huggingFaceRepository.loadCompatibleFiles(model)
                .onSuccess { files ->
                    _browseResults.value = _browseResults.value.map { current ->
                        if (current.repoId == model.repoId) current.copy(files = files) else current
                    }
                }
                .onFailure { throwable ->
                    _fileErrors.value = _fileErrors.value + (
                        model.repoId to throwable.userFacingMessage("No se pudieron consultar los archivos")
                    )
                }

            _loadingFileRepos.value = _loadingFileRepos.value - model.repoId
        }
    }

    fun addModelToLibrary(model: HFBrowsableModel, file: HFBrowsableFile) {
        viewModelScope.launch {
            try {
                val key = fileKey(model, file)
                val sourceName = file.fileName.substringAfterLast('/')
                val safeSourceName = sourceName
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .take(96)
                    .ifBlank { "model.${file.fileName.substringAfterLast('.', "bin")}" }
                val safeFileName = "hf_${key}_$safeSourceName"

                val entity = CustomLocalModelEntity(
                    id = "hf-$key",
                    name = "${model.repoName} ($sourceName)",
                    huggingFaceRepoId = model.repoId,
                    huggingFaceUrl = file.downloadUrl,
                    fileName = safeFileName,
                    sizeBytes = file.sizeBytes,
                    description = buildString {
                        append(file.format.displayName)
                        append(" de ")
                        append(model.author)
                        if (model.downloads > 0) {
                            append(" · ")
                            append(formatDownloads(model.downloads))
                            append(" descargas")
                        }
                        if (model.revisionSha != null) append(" · revisión fijada")
                    },
                    contextLength = 4096,
                    requiresLicense = model.gated,
                    requiresHFToken = model.gated || model.isPrivate
                )

                customLocalModelDao.insert(entity)
                _addedFileKeys.value = _addedFileKeys.value + key
                _addedMessage.value = "${model.repoName} se agregó a tu biblioteca"
            } catch (e: Exception) {
                _error.value = e.userFacingMessage("No se pudo agregar el modelo")
            }
        }
    }

    fun fileKey(model: HFBrowsableModel, file: HFBrowsableFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${model.repoId}:${file.fileName}".toByteArray())
        val hex = "0123456789abcdef"
        return buildString(16) {
            digest.take(8).forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearAddedMessage() {
        _addedMessage.value = null
    }

    private fun launchInitialSearch(query: String) {
        searchGeneration += 1
        val generation = searchGeneration
        searchJob?.cancel()
        loadMoreJob?.cancel()

        searchJob = viewModelScope.launch {
            activeQuery = query
            nextPageUrl = null
            _isLoading.value = true
            _isLoadingMore.value = false
            _hasMore.value = false
            _hasSearched.value = true
            _error.value = null
            _fileErrors.value = emptyMap()
            _browseResults.value = emptyList()

            val result = if (query.contains("huggingface.co/", ignoreCase = true)) {
                huggingFaceRepository.resolveRepoUrl(query).map { model ->
                    HFSearchPage(listOf(model), nextPageUrl = null)
                }
            } else {
                huggingFaceRepository.searchModels(query)
            }

            result
                .onSuccess { page ->
                    if (generation != searchGeneration) return@onSuccess
                    _browseResults.value = page.models.distinctBy { it.repoId }
                    nextPageUrl = page.nextPageUrl
                    _hasMore.value = page.nextPageUrl != null
                }
                .onFailure { throwable ->
                    if (generation != searchGeneration) return@onFailure
                    _browseResults.value = emptyList()
                    _error.value = throwable.userFacingMessage("No se pudieron buscar modelos")
                }

            if (generation == searchGeneration) {
                _isLoading.value = false
            }
        }
    }

    private fun clearSearchResults() {
        searchGeneration += 1
        searchJob?.cancel()
        loadMoreJob?.cancel()
        activeQuery = ""
        nextPageUrl = null
        _browseResults.value = emptyList()
        _isLoading.value = false
        _isLoadingMore.value = false
        _hasMore.value = false
        _hasSearched.value = false
        _error.value = null
        _fileErrors.value = emptyMap()
    }

    private fun Throwable.userFacingMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback

    private fun formatDownloads(downloads: Long): String = when {
        downloads >= 1_000_000 -> "%.1f M".format(downloads / 1_000_000f)
        downloads >= 1_000 -> "%.1f mil".format(downloads / 1_000f)
        else -> downloads.toString()
    }
}
