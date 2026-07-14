package com.aiagents.app.presentation.local_models

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.aiagents.app.data.local.CustomLocalModelDao
import com.aiagents.app.data.local.LocalModelRepository
import com.aiagents.app.data.local.ModelDownloadWorker
import com.aiagents.app.data.model.CustomLocalModelEntity
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.domain.model.LocalModel
import com.aiagents.app.domain.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LocalModelsViewModel @Inject constructor(
    private val modelRepository: LocalModelRepository,
    private val agentRepository: AgentRepository,
    private val customLocalModelDao: CustomLocalModelDao,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _models = MutableStateFlow<List<LocalModel>>(emptyList())
    val models: StateFlow<List<LocalModel>> = _models.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isDownloading: StateFlow<Map<String, Boolean>> = _isDownloading.asStateFlow()

    private val _availableStorage = MutableStateFlow(0L)
    val availableStorage: StateFlow<Long> = _availableStorage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _huggingFaceToken = MutableStateFlow(modelRepository.getHuggingFaceToken() ?: "")
    val huggingFaceToken: StateFlow<String> = _huggingFaceToken.asStateFlow()

    val hasHuggingFaceToken: Boolean get() = modelRepository.hasHuggingFaceToken()

    val customModels: StateFlow<List<CustomLocalModelEntity>> = customLocalModelDao.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadModels()
        loadStorageInfo()
        observeDownloadProgress()
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(ModelDownloadWorker.WORK_TAG).collect { workInfos ->
                val progressMap = mutableMapOf<String, Float>()
                val downloadingMap = mutableMapOf<String, Boolean>()
                val knownModelIds = _models.value.mapTo(mutableSetOf()) { it.id }

                val workByModel = workInfos.mapNotNull { workInfo ->
                    val modelId = ModelDownloadWorker.modelIdFromTags(workInfo.tags)
                        ?: workInfo.tags.firstOrNull { it in knownModelIds }
                    modelId?.let {
                        modelId to workInfo
                    }
                }.groupBy({ it.first }, { it.second })

                for ((modelId, modelWorkInfos) in workByModel) {
                    val activeWork = modelWorkInfos
                        .filterNot { it.state.isFinished }
                        .maxByOrNull { workInfo ->
                            when (workInfo.state) {
                                WorkInfo.State.RUNNING -> 3
                                WorkInfo.State.ENQUEUED -> 2
                                WorkInfo.State.BLOCKED -> 1
                                else -> 0
                            }
                        }
                        ?: continue

                    when (activeWork.state) {
                        WorkInfo.State.RUNNING -> {
                            downloadingMap[modelId] = true
                            val progress = activeWork.progress.getInt(ModelDownloadWorker.PROGRESS_KEY, 0)
                            progressMap[modelId] = progress / 100f
                        }
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.BLOCKED -> {
                            downloadingMap[modelId] = true
                            progressMap[modelId] = activeWork.progress
                                .getInt(ModelDownloadWorker.PROGRESS_KEY, 0) / 100f
                        }
                        else -> Unit
                    }
                }

                _downloadProgress.value = progressMap
                _isDownloading.value = downloadingMap
            }
        }
    }

    fun loadModels() {
        _models.value = modelRepository.getAvailableModels()
    }

    fun loadStorageInfo() {
        _availableStorage.value = modelRepository.getAvailableStorage()
    }

    fun saveHuggingFaceToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            modelRepository.removeHuggingFaceToken()
            _huggingFaceToken.value = ""
        } else {
            modelRepository.saveHuggingFaceToken(trimmed)
            _huggingFaceToken.value = trimmed
        }
    }

    fun removeHuggingFaceToken() {
        modelRepository.removeHuggingFaceToken()
        _huggingFaceToken.value = ""
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun downloadModel(model: LocalModel) {
        if (model.huggingFaceUrl.isBlank()) {
            _errorMessage.value = "Este modelo no tiene URL de descarga. Usa 'Importar Modelo' para añadirlo manualmente."
            return
        }

        if (!isNetworkAvailable()) {
            _errorMessage.value = "No hay conexión a internet. Conéctate a una red WiFi para descargar modelos."
            return
        }

        if (model.requiresHFToken && !modelRepository.hasHuggingFaceToken()) {
            _errorMessage.value = "Este modelo requiere un token de HuggingFace. Configura tu token en la sección de arriba antes de descargar."
            return
        }

        if (model.sizeBytes > 0) {
            val requiredSpace = (model.sizeBytes * 1.1).toLong()
            if (requiredSpace > _availableStorage.value) {
                _errorMessage.value = "Espacio insuficiente. Necesitas al menos ${formatBytes(requiredSpace)} libres."
                return
            }
        }

        val inputData = Data.Builder()
            .putString(ModelDownloadWorker.KEY_MODEL_ID, model.id)
            .putString(ModelDownloadWorker.KEY_MODEL_NAME, model.name)
            .putString(ModelDownloadWorker.KEY_MODEL_URL, model.huggingFaceUrl)
            .putString(ModelDownloadWorker.KEY_FILE_NAME, model.fileName)
            .putLong(ModelDownloadWorker.KEY_SIZE_BYTES, model.sizeBytes)
            .putBoolean(
                ModelDownloadWorker.KEY_EXACT_SIZE,
                model.sizeBytes > 0 && (
                    model.id.startsWith("hf-") || customModels.value.any { it.id == model.id }
                )
            )
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(ModelDownloadWorker.WORK_TAG)
            .addTag(ModelDownloadWorker.modelTag(model.id))
            .build()

        workManager.enqueueUniqueWork(
            "download_${model.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        observeWorkCompletion(workRequest.id, model)
    }

    private fun observeWorkCompletion(workId: java.util.UUID, model: LocalModel) {
        viewModelScope.launch {
            val workInfo = workManager.getWorkInfoByIdFlow(workId)
                .filterNotNull()
                .first { it.state.isFinished }

            when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        loadModels()
                        loadStorageInfo()
                        if (agentRepository.getActiveProvider() == null ||
                            modelRepository.getDownloadedModels().size == 1) {
                            agentRepository.setActiveProvider(ProviderType.LOCAL)
                        }
                        agentRepository.addSelectedModel(ProviderType.LOCAL, model.id)
                        _successMessage.value = "✓ ${model.name} descargado correctamente. Proveedor LOCAL activado."
                    }
                    WorkInfo.State.FAILED -> {
                        val detail = workInfo.outputData
                            .getString(ModelDownloadWorker.OUTPUT_ERROR_MESSAGE)
                            ?: "Verifica tu conexión e intenta de nuevo"
                        _errorMessage.value = "Error descargando ${model.name}: $detail"
                    }
                    WorkInfo.State.CANCELLED -> {
                        _downloadProgress.value = _downloadProgress.value - model.id
                        _isDownloading.value = _isDownloading.value - model.id
                    }
                    else -> Unit
            }
        }
    }

    fun deleteModel(model: LocalModel) {
        val success = modelRepository.deleteModel(model)
        if (success) {
            loadModels()
            loadStorageInfo()
        }
    }

    fun removeCustomModel(id: String) {
        viewModelScope.launch {
            modelRepository.removeCustomModel(id)
            loadModels()
        }
    }

    fun importModel(sourceFile: File, modelName: String): Boolean {
        val normalizedName = modelName.lowercase()
        val targetFileName = if (
            normalizedName.endsWith(".task") ||
            normalizedName.endsWith(".bin") ||
            normalizedName.endsWith(".litertlm")
        ) {
            modelName
        } else {
            "$modelName.task"
        }

        return modelRepository.importModelFromFile(sourceFile, targetFileName).fold(
            onSuccess = {
                loadModels()
                loadStorageInfo()
                if (agentRepository.getActiveProvider() == null) {
                    agentRepository.setActiveProvider(ProviderType.LOCAL)
                }
                val modelId = targetFileName.substringBeforeLast(".")
                agentRepository.addSelectedModel(ProviderType.LOCAL, modelId)
                _successMessage.value = "✓ Modelo importado correctamente."
                true
            },
            onFailure = { e ->
                _errorMessage.value = "Error importando modelo: ${e.message}"
                false
            }
        )
    }

    fun cancelDownload(modelId: String) {
        workManager.cancelUniqueWork("download_$modelId")
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSuccess() {
        _successMessage.value = null
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes.toFloat() / (1024 * 1024 * 1024)
        val mb = bytes.toFloat() / (1024 * 1024)
        return when {
            gb >= 1 -> "%.1f GB".format(gb)
            mb >= 1 -> "%.0f MB".format(mb)
            else -> "$bytes B"
        }
    }
}
