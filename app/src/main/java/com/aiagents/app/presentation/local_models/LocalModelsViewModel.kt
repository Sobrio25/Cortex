package com.aiagents.app.presentation.local_models

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.CustomLocalModelDao
import com.aiagents.app.data.local.LocalModelRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LocalModelsViewModel @Inject constructor(
    private val modelRepository: LocalModelRepository,
    private val agentRepository: AgentRepository,
    private val customLocalModelDao: CustomLocalModelDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

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

        viewModelScope.launch {
            // Verificar conexión a internet
            if (!isNetworkAvailable()) {
                _errorMessage.value = "No hay conexión a internet. Conéctate a una red WiFi para descargar modelos."
                return@launch
            }

            // Advertir si el modelo requiere token pero no está configurado
            if (model.requiresHFToken && !modelRepository.hasHuggingFaceToken()) {
                _errorMessage.value = "Este modelo requiere un token de HuggingFace. Configura tu token en la sección de arriba antes de descargar."
                return@launch
            }

            // Verificar espacio disponible (con margen de seguridad del 10%)
            if (model.sizeBytes > 0) {
                val requiredSpace = (model.sizeBytes * 1.1).toLong()
                if (requiredSpace > _availableStorage.value) {
                    _errorMessage.value = "Espacio insuficiente. Necesitas al menos ${formatBytes(requiredSpace)} libres."
                    return@launch
                }
            }

            _isDownloading.value = _isDownloading.value + (model.id to true)

            modelRepository.downloadModel(model) { progress ->
                _downloadProgress.value = _downloadProgress.value + (model.id to progress)
            }.onSuccess { path ->
                loadModels()
                loadStorageInfo()
                // Activar automáticamente el proveedor LOCAL al descargar el primer modelo
                if (agentRepository.getActiveProvider() == null ||
                    modelRepository.getDownloadedModels().size == 1) {
                    agentRepository.setActiveProvider(ProviderType.LOCAL)
                }
                // Agregar el modelo a la lista de modelos seleccionados para que aparezca en el chat
                agentRepository.addSelectedModel(ProviderType.LOCAL, model.id)
                _successMessage.value = "✓ ${model.name} descargado correctamente. Proveedor LOCAL activado."
            }.onFailure { error ->
                val errorMsg = when {
                    error.message?.contains("HTTP 401") == true ->
                        "Token inválido o expirado. Verifica tu token de HuggingFace en huggingface.co/settings/tokens"
                    error.message?.contains("HTTP 403") == true ->
                        "Acceso denegado. Necesitas:\n1. Aceptar la licencia en huggingface.co/google/gemma-2b-it\n2. Configurar tu token de HuggingFace arriba"
                    error.message?.contains("HTTP 404") == true ->
                        "Modelo no encontrado. La URL puede haber cambiado."
                    error.message?.contains("HTTP 429") == true ->
                        "Demasiadas solicitudes. Espera unos minutos e intenta de nuevo."
                    error.message?.contains("timeout", ignoreCase = true) == true ->
                        "Tiempo de espera agotado. Intenta con una conexión WiFi más estable."
                    error.message?.contains("Unable to resolve host") == true ->
                        "No se pudo conectar con HuggingFace. Verifica tu conexión a internet."
                    error.message?.contains("URL de descarga") == true ->
                        error.message ?: "Error desconocido"
                    else -> "Error: ${error.message ?: "Error desconocido"}"
                }
                _errorMessage.value = "Error descargando ${model.name}:\n$errorMsg"
            }

            _isDownloading.value = _isDownloading.value - model.id
            _downloadProgress.value = _downloadProgress.value - model.id
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
        val targetFileName = if (modelName.endsWith(".task") || modelName.endsWith(".bin")) {
            modelName
        } else {
            "$modelName.task"
        }

        return modelRepository.importModelFromFile(sourceFile, targetFileName).fold(
            onSuccess = {
                loadModels()
                loadStorageInfo()
                // Activar LOCAL al importar el primer modelo
                if (agentRepository.getActiveProvider() == null) {
                    agentRepository.setActiveProvider(ProviderType.LOCAL)
                }
                // Agregar el modelo a la lista de modelos seleccionados
                // Extraer el id del modelo del nombre del archivo (sin extensión)
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
