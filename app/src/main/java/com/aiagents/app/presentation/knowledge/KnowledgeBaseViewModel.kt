package com.aiagents.app.presentation.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.knowledge.EmbeddingModelDownloader
import com.aiagents.app.data.knowledge.KnowledgeRepository
import com.aiagents.app.data.model.KnowledgeDocumentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KnowledgeBaseUiState(
    val documents: List<KnowledgeDocumentEntity> = emptyList(),
    val modelReady: Boolean = false,
    val downloadProgress: Float? = null,
    val isDownloading: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class KnowledgeBaseViewModel @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository,
    private val modelDownloader: EmbeddingModelDownloader
) : ViewModel() {

    private val modelReady = MutableStateFlow(false)
    private val isDownloading = MutableStateFlow(false)
    private val isBusy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<KnowledgeBaseUiState> = combine(
        combine(
            knowledgeRepository.observeDocuments(),
            modelReady
        ) { documents, ready -> documents to ready },
        modelDownloader.progress,
        isDownloading,
        isBusy,
        message
    ) { (documents, ready), progress, downloading, busy, msg ->
        KnowledgeBaseUiState(
            documents = documents,
            modelReady = ready,
            downloadProgress = progress,
            isDownloading = downloading,
            isBusy = busy,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KnowledgeBaseUiState())

    init {
        viewModelScope.launch {
            modelReady.value = knowledgeRepository.isModelReady()
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            isDownloading.value = true
            message.value = null
            val result = modelDownloader.downloadIfNeeded()
            modelReady.value = knowledgeRepository.isModelReady()
            isDownloading.value = false
            message.value = result.fold(
                onSuccess = { "Modelo de embeddings descargado ✓" },
                onFailure = { "No se pudo descargar el modelo: ${it.message}" }
            )
        }
    }

    fun addDocument(title: String, text: String) {
        viewModelScope.launch {
            isBusy.value = true
            message.value = null
            val result = knowledgeRepository.addDocument(title, text)
            isBusy.value = false
            message.value = result.fold(
                onSuccess = { chunks -> "Documento añadido ($chunks fragmentos indexados) ✓" },
                onFailure = { "No se pudo añadir: ${it.message}" }
            )
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            knowledgeRepository.deleteDocument(documentId)
            message.value = "Documento eliminado."
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
