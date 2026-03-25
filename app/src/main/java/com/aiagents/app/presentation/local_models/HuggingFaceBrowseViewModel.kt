package com.aiagents.app.presentation.local_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.CustomLocalModelDao
import com.aiagents.app.data.model.CustomLocalModelEntity
import com.aiagents.app.data.repository.HFBrowsableFile
import com.aiagents.app.data.repository.HFBrowsableModel
import com.aiagents.app.data.repository.HuggingFaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HuggingFaceBrowseViewModel @Inject constructor(
    private val huggingFaceRepository: HuggingFaceRepository,
    private val customLocalModelDao: CustomLocalModelDao
) : ViewModel() {

    private val _browseResults = MutableStateFlow<List<HFBrowsableModel>>(emptyList())
    val browseResults: StateFlow<List<HFBrowsableModel>> = _browseResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedAuthor = MutableStateFlow("litert-community")
    val selectedAuthor: StateFlow<String> = _selectedAuthor.asStateFlow()

    private val _customUrl = MutableStateFlow("")
    val customUrl: StateFlow<String> = _customUrl.asStateFlow()

    private val _addedMessage = MutableStateFlow<String?>(null)
    val addedMessage: StateFlow<String?> = _addedMessage.asStateFlow()

    fun selectAuthor(author: String) {
        _selectedAuthor.value = author
        _customUrl.value = ""
        _browseResults.value = emptyList()
        _error.value = null
        searchModels()
    }

    fun setCustomUrl(url: String) {
        _customUrl.value = url
    }

    fun searchModels() {
        val author = _selectedAuthor.value
        if (author.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            huggingFaceRepository.searchCompatibleModels(author)
                .onSuccess { models ->
                    _browseResults.value = models
                    if (models.isEmpty()) {
                        _error.value = "No se encontraron modelos compatibles (.task/.bin) para '$author'"
                    }
                }
                .onFailure { e ->
                    _error.value = "Error buscando modelos: ${e.message}"
                    _browseResults.value = emptyList()
                }

            _isLoading.value = false
        }
    }

    fun resolveUrl(url: String) {
        if (url.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            huggingFaceRepository.resolveRepoUrl(url)
                .onSuccess { model ->
                    _browseResults.value = listOf(model)
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Error resolviendo URL"
                    _browseResults.value = emptyList()
                }

            _isLoading.value = false
        }
    }

    fun addModelToLibrary(model: HFBrowsableModel, file: HFBrowsableFile) {
        viewModelScope.launch {
            val sanitizedRepoId = model.repoId.replace("/", "-").replace(" ", "-")
            val fileBaseName = file.fileName.substringBeforeLast(".")
            val id = "hf-${sanitizedRepoId}-${fileBaseName}"

            // Use sanitized name to avoid filename collisions
            val safeFileName = "${sanitizedRepoId}_${file.fileName}"

            val entity = CustomLocalModelEntity(
                id = id,
                name = "${model.repoName} (${file.fileName})",
                huggingFaceRepoId = model.repoId,
                huggingFaceUrl = file.downloadUrl,
                fileName = safeFileName,
                sizeBytes = file.sizeBytes,
                description = "De ${model.author}. ${model.downloads} descargas en HuggingFace.",
                contextLength = 4096,
                requiresLicense = false,
                requiresHFToken = model.gated
            )

            customLocalModelDao.insert(entity)
            _addedMessage.value = "${model.repoName} agregado a tu biblioteca"
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearAddedMessage() {
        _addedMessage.value = null
    }
}
