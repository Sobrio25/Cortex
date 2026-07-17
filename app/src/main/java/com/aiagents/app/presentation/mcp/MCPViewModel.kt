package com.aiagents.app.presentation.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.local.MCPDao
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.model.BraveSearchConfig
import com.aiagents.app.data.model.SerpApiConfig
import com.aiagents.app.data.model.MCPServerEntity
import com.aiagents.app.domain.model.WebSearchProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MCPUiState(
    val webSearchProvider: WebSearchProvider = WebSearchProvider.NATIVE,
    val braveApiKey: String = "",
    val braveIsConfigured: Boolean = false,
    val googleMapsApiKey: String = "",
    val googleMapsIsConfigured: Boolean = false,
    val serpApiKey: String = "",
    val serpApiIsConfigured: Boolean = false,
    val canvaAccessToken: String = "",
    val canvaIsConfigured: Boolean = false,
    val showCanvaConfigDialog: Boolean = false,
    val pubMedIsEnabled: Boolean = false,
    val financeIsEnabled: Boolean = false,
    val obsidianVaultPath: String = "",
    val obsidianIsConfigured: Boolean = false,
    val showObsidianConfigDialog: Boolean = false,
    val gitHubToken: String = "",
    val gitHubIsConfigured: Boolean = false,
    val showGitHubConfigDialog: Boolean = false,
    val notionToken: String = "",
    val notionIsConfigured: Boolean = false,
    val showNotionConfigDialog: Boolean = false,
    val slackToken: String = "",
    val slackIsConfigured: Boolean = false,
    val showSlackConfigDialog: Boolean = false,
    val googleImagenApiKey: String = "",
    val googleImagenIsConfigured: Boolean = false,
    val showGoogleImagenConfigDialog: Boolean = false,
    val dalleApiKey: String = "",
    val dalleIsConfigured: Boolean = false,
    val showDalleConfigDialog: Boolean = false,
    val isLoading: Boolean = false,
    val showBraveConfigDialog: Boolean = false,
    val showGoogleMapsConfigDialog: Boolean = false,
    val showSerpApiConfigDialog: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class MCPViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val mcpDao: MCPDao,
    private val errorReporter: AppErrorReporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(MCPUiState())
    val uiState: StateFlow<MCPUiState> = _uiState.asStateFlow()

    init {
        loadWebSearchProvider()
        loadBraveConfig()
        loadGoogleMapsConfig()
        loadSerpApiConfig()
        loadCanvaConfig()
        loadPubMedConfig()
        loadFinanceConfig()
        loadObsidianConfig()
        loadGitHubConfig()
        loadNotionConfig()
        loadSlackConfig()
        loadGoogleImagenConfig()
        loadDalleConfig()
        initializeMCPServers()
    }

    private fun loadWebSearchProvider() {
        val stored = securePreferences.getWebSearchProvider()
        val available = when (stored) {
            WebSearchProvider.NATIVE -> true
            WebSearchProvider.BRAVE -> securePreferences.hasBraveApiKey()
            WebSearchProvider.SERPAPI -> securePreferences.hasSerpApiKey()
        }
        val selected = stored.takeIf { available } ?: WebSearchProvider.NATIVE
        if (selected != stored) securePreferences.setWebSearchProvider(selected)
        _uiState.value = _uiState.value.copy(webSearchProvider = selected)
    }

    fun selectWebSearchProvider(provider: WebSearchProvider) {
        val available = when (provider) {
            WebSearchProvider.NATIVE -> true
            WebSearchProvider.BRAVE -> _uiState.value.braveIsConfigured
            WebSearchProvider.SERPAPI -> _uiState.value.serpApiIsConfigured
        }
        if (!available) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Configura la API key de ${if (provider == WebSearchProvider.BRAVE) "Brave" else "SerpAPI"} antes de seleccionarlo."
            )
            return
        }
        securePreferences.setWebSearchProvider(provider)
        _uiState.value = _uiState.value.copy(webSearchProvider = provider, errorMessage = null)
    }

    private fun initializeMCPServers() {
        viewModelScope.launch {
            // Inicializar Brave Search en la base de datos si no existe
            val braveServer = mcpDao.getServerById("brave_search")
            if (braveServer == null) {
                mcpDao.insertServer(
                    MCPServerEntity(
                        id = "brave_search",
                        name = "Brave Search",
                        description = "Busqueda web privada y gratuita",
                        isEnabled = false
                    )
                )
            }

            // Inicializar Google Maps en la base de datos si no existe
            val googleMapsServer = mcpDao.getServerById("google_maps")
            if (googleMapsServer == null) {
                mcpDao.insertServer(
                    MCPServerEntity(
                        id = "google_maps",
                        name = "Google Maps",
                        description = "Servicios de geocodificacion, rutas, lugares y elevacion",
                        isEnabled = false
                    )
                )
            }

            // Inicializar SerpAPI en la base de datos si no existe
            val serpApiServer = mcpDao.getServerById("serpapi")
            if (serpApiServer == null) {
                mcpDao.insertServer(
                    MCPServerEntity(
                        id = "serpapi",
                        name = "SerpAPI",
                        description = "Busquedas en Google, Google Maps, Google Flights, Shopping y mas",
                        isEnabled = false
                    )
                )
            }

            // Inicializar PubMed en la base de datos si no existe
            val pubmedServer = mcpDao.getServerById("pubmed")
            if (pubmedServer == null) {
                mcpDao.insertServer(
                    MCPServerEntity(
                        id = "pubmed",
                        name = "PubMed",
                        description = "Busqueda de articulos cientificos y medicos (gratuito)",
                        isEnabled = false
                    )
                )
            }

            // Inicializar Obsidian en la base de datos si no existe
            val obsidianServer = mcpDao.getServerById("obsidian")
            if (obsidianServer == null) {
                mcpDao.insertServer(
                    MCPServerEntity(
                        id = "obsidian",
                        name = "Obsidian",
                        description = "Acceso directo al vault de Obsidian (leer, escribir, buscar notas)",
                        isEnabled = false
                    )
                )
            }

            // Inicializar Canva en la base de datos si no existe
            if (mcpDao.getServerById("canva") == null) {
                mcpDao.insertServer(
                    MCPServerEntity(
                        id = "canva",
                        name = "Canva",
                        description = "Diseno grafico profesional: posters, presentaciones, redes sociales",
                        isEnabled = false
                    )
                )
            }

            // Inicializar GitHub
            if (mcpDao.getServerById("github") == null) {
                mcpDao.insertServer(MCPServerEntity(
                    id = "github", name = "GitHub",
                    description = "Buscar repos, leer codigo, crear issues, ver PRs",
                    isEnabled = false
                ))
            }

            // Inicializar Notion
            if (mcpDao.getServerById("notion") == null) {
                mcpDao.insertServer(MCPServerEntity(
                    id = "notion", name = "Notion",
                    description = "Buscar paginas, leer contenido, crear paginas, listar databases",
                    isEnabled = false
                ))
            }

            // Inicializar Slack
            if (mcpDao.getServerById("slack") == null) {
                mcpDao.insertServer(MCPServerEntity(
                    id = "slack", name = "Slack",
                    description = "Send messages, read channels, search, list users",
                    isEnabled = false
                ))
            }

            // Inicializar Finance
            if (mcpDao.getServerById("finance") == null) {
                mcpDao.insertServer(MCPServerEntity(
                    id = "finance", name = "Finanzas Personales",
                    description = "Registro local de gastos, ingresos e inversiones",
                    isEnabled = false
                ))
            }

            // Inicializar Google Drive
            if (mcpDao.getServerById("google_drive") == null) {
                mcpDao.insertServer(MCPServerEntity(
                    id = "google_drive", name = "Google Drive",
                    description = "Listar archivos, leer y crear documentos en Google Drive/Docs",
                    isEnabled = false
                ))
            }
        }
    }

    private fun loadBraveConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val apiKey = securePreferences.getBraveApiKey() ?: ""
            val isConfigured = apiKey.isNotBlank()

            _uiState.value = _uiState.value.copy(
                braveApiKey = apiKey,
                braveIsConfigured = isConfigured,
                isLoading = false
            )
        }
    }

    private fun loadGoogleMapsConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val apiKey = securePreferences.getGoogleMapsApiKey() ?: ""
            val isConfigured = apiKey.isNotBlank()

            _uiState.value = _uiState.value.copy(
                googleMapsApiKey = apiKey,
                googleMapsIsConfigured = isConfigured,
                isLoading = false
            )
        }
    }

    private fun loadSerpApiConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val apiKey = securePreferences.getSerpApiKey() ?: ""
            val isConfigured = apiKey.isNotBlank()

            _uiState.value = _uiState.value.copy(
                serpApiKey = apiKey,
                serpApiIsConfigured = isConfigured,
                isLoading = false
            )
        }
    }

    private fun loadPubMedConfig() {
        _uiState.value = _uiState.value.copy(
            pubMedIsEnabled = securePreferences.isPubMedEnabled()
        )
    }

    fun togglePubMed() {
        viewModelScope.launch {
            val newState = !_uiState.value.pubMedIsEnabled
            securePreferences.setPubMedEnabled(newState)
            mcpDao.setServerEnabled("pubmed", newState)
            _uiState.value = _uiState.value.copy(pubMedIsEnabled = newState)
        }
    }

    // Finance
    private fun loadFinanceConfig() {
        _uiState.value = _uiState.value.copy(
            financeIsEnabled = securePreferences.isFinanceEnabled()
        )
    }

    fun toggleFinance() {
        viewModelScope.launch {
            val newState = !_uiState.value.financeIsEnabled
            securePreferences.setFinanceEnabled(newState)
            mcpDao.setServerEnabled("finance", newState)
            _uiState.value = _uiState.value.copy(financeIsEnabled = newState)
        }
    }

    // Obsidian
    private fun loadObsidianConfig() {
        val path = securePreferences.getObsidianVaultPath() ?: ""
        _uiState.value = _uiState.value.copy(
            obsidianVaultPath = path,
            obsidianIsConfigured = path.isNotBlank()
        )
    }

    fun showObsidianConfigDialog() {
        _uiState.value = _uiState.value.copy(showObsidianConfigDialog = true)
    }

    fun hideObsidianConfigDialog() {
        _uiState.value = _uiState.value.copy(showObsidianConfigDialog = false)
    }

    fun saveObsidianVaultPath(path: String) {
        viewModelScope.launch {
            if (path.isBlank()) {
                securePreferences.clearObsidianVaultPath()
                mcpDao.setServerEnabled("obsidian", false)
                _uiState.value = _uiState.value.copy(
                    obsidianVaultPath = "",
                    obsidianIsConfigured = false,
                    showObsidianConfigDialog = false
                )
            } else {
                securePreferences.saveObsidianVaultPath(path)
                mcpDao.updateServerConfig(
                    id = "obsidian",
                    configJson = """{"vaultPath":"$path","isConfigured":true}"""
                )
                mcpDao.setServerEnabled("obsidian", true)
                _uiState.value = _uiState.value.copy(
                    obsidianVaultPath = path,
                    obsidianIsConfigured = true,
                    showObsidianConfigDialog = false
                )
            }
        }
    }

    // Brave Search Dialog
    fun showBraveConfigDialog() {
        _uiState.value = _uiState.value.copy(showBraveConfigDialog = true)
    }

    fun hideBraveConfigDialog() {
        _uiState.value = _uiState.value.copy(showBraveConfigDialog = false)
    }

    // Google Maps Dialog
    fun showGoogleMapsConfigDialog() {
        _uiState.value = _uiState.value.copy(showGoogleMapsConfigDialog = true)
    }

    fun hideGoogleMapsConfigDialog() {
        _uiState.value = _uiState.value.copy(showGoogleMapsConfigDialog = false)
    }

    // SerpAPI Dialog
    fun showSerpApiConfigDialog() {
        _uiState.value = _uiState.value.copy(showSerpApiConfigDialog = true)
    }

    fun hideSerpApiConfigDialog() {
        _uiState.value = _uiState.value.copy(showSerpApiConfigDialog = false)
    }

    // Google Imagen Dialog
    fun showGoogleImagenConfigDialog() {
        _uiState.value = _uiState.value.copy(showGoogleImagenConfigDialog = true)
    }

    fun hideGoogleImagenConfigDialog() {
        _uiState.value = _uiState.value.copy(showGoogleImagenConfigDialog = false)
    }

    // DALL-E Dialog
    fun showDalleConfigDialog() {
        _uiState.value = _uiState.value.copy(showDalleConfigDialog = true)
    }

    fun hideDalleConfigDialog() {
        _uiState.value = _uiState.value.copy(showDalleConfigDialog = false)
    }

    // Brave Search
    fun saveBraveApiKey(apiKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                if (apiKey.isBlank()) {
                    // Eliminar configuracion
                    securePreferences.removeBraveApiKey()
                    mcpDao.setServerEnabled("brave_search", false)
                    val selectedProvider = if (_uiState.value.webSearchProvider == WebSearchProvider.BRAVE) {
                        WebSearchProvider.NATIVE.also(securePreferences::setWebSearchProvider)
                    } else {
                        _uiState.value.webSearchProvider
                    }

                    _uiState.value = _uiState.value.copy(
                        webSearchProvider = selectedProvider,
                        braveApiKey = "",
                        braveIsConfigured = false,
                        isLoading = false,
                        showBraveConfigDialog = false
                    )
                } else {
                    // Guardar configuracion
                    securePreferences.saveBraveApiKey(apiKey)

                    val config = BraveSearchConfig(
                        apiKey = apiKey,
                        isConfigured = true
                    )

                    mcpDao.updateServerConfig(
                        id = "brave_search",
                        configJson = """{"apiKey":"${apiKey.take(4)}...","isConfigured":true}"""
                    )
                    mcpDao.setServerEnabled("brave_search", true)

                    _uiState.value = _uiState.value.copy(
                        braveApiKey = apiKey,
                        braveIsConfigured = true,
                        isLoading = false,
                        showBraveConfigDialog = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = mcpError(e, "brave_configuration")
                )
            }
        }
    }

    fun saveGoogleMapsApiKey(apiKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                if (apiKey.isBlank()) {
                    // Eliminar configuracion
                    securePreferences.removeGoogleMapsApiKey()
                    mcpDao.setServerEnabled("google_maps", false)

                    _uiState.value = _uiState.value.copy(
                        googleMapsApiKey = "",
                        googleMapsIsConfigured = false,
                        isLoading = false,
                        showGoogleMapsConfigDialog = false
                    )
                } else {
                    // Guardar configuracion
                    securePreferences.saveGoogleMapsApiKey(apiKey)

                    mcpDao.updateServerConfig(
                        id = "google_maps",
                        configJson = """{"apiKey":"${apiKey.take(4)}...","isConfigured":true}"""
                    )
                    mcpDao.setServerEnabled("google_maps", true)

                    _uiState.value = _uiState.value.copy(
                        googleMapsApiKey = apiKey,
                        googleMapsIsConfigured = true,
                        isLoading = false,
                        showGoogleMapsConfigDialog = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = mcpError(e, "maps_configuration")
                )
            }
        }
    }

    // Canva
    private fun loadCanvaConfig() {
        val token = securePreferences.getCanvaAccessToken() ?: ""
        _uiState.value = _uiState.value.copy(
            canvaAccessToken = token,
            canvaIsConfigured = token.isNotBlank()
        )
    }

    fun showCanvaConfigDialog() {
        _uiState.value = _uiState.value.copy(showCanvaConfigDialog = true)
    }

    fun hideCanvaConfigDialog() {
        _uiState.value = _uiState.value.copy(showCanvaConfigDialog = false)
    }

    fun saveCanvaAccessToken(token: String) {
        viewModelScope.launch {
            if (token.isBlank()) {
                securePreferences.removeCanvaAccessToken()
                mcpDao.setServerEnabled("canva", false)
                _uiState.value = _uiState.value.copy(
                    canvaAccessToken = "",
                    canvaIsConfigured = false,
                    showCanvaConfigDialog = false
                )
            } else {
                securePreferences.saveCanvaAccessToken(token)
                mcpDao.updateServerConfig("canva", """{"isConfigured":true}""")
                mcpDao.setServerEnabled("canva", true)
                _uiState.value = _uiState.value.copy(
                    canvaAccessToken = token,
                    canvaIsConfigured = true,
                    showCanvaConfigDialog = false
                )
            }
        }
    }

    // GitHub
    private fun loadGitHubConfig() {
        val token = securePreferences.getGitHubToken() ?: ""
        _uiState.value = _uiState.value.copy(
            gitHubToken = token,
            gitHubIsConfigured = token.isNotBlank()
        )
    }

    fun showGitHubConfigDialog() {
        _uiState.value = _uiState.value.copy(showGitHubConfigDialog = true)
    }

    fun hideGitHubConfigDialog() {
        _uiState.value = _uiState.value.copy(showGitHubConfigDialog = false)
    }

    fun saveGitHubToken(token: String) {
        viewModelScope.launch {
            if (token.isBlank()) {
                securePreferences.removeGitHubToken()
                mcpDao.setServerEnabled("github", false)
                _uiState.value = _uiState.value.copy(
                    gitHubToken = "", gitHubIsConfigured = false, showGitHubConfigDialog = false
                )
            } else {
                securePreferences.saveGitHubToken(token)
                mcpDao.updateServerConfig("github", """{"isConfigured":true}""")
                mcpDao.setServerEnabled("github", true)
                _uiState.value = _uiState.value.copy(
                    gitHubToken = token, gitHubIsConfigured = true, showGitHubConfigDialog = false
                )
            }
        }
    }

    // Notion
    private fun loadNotionConfig() {
        val token = securePreferences.getNotionToken() ?: ""
        _uiState.value = _uiState.value.copy(
            notionToken = token,
            notionIsConfigured = token.isNotBlank()
        )
    }

    fun showNotionConfigDialog() {
        _uiState.value = _uiState.value.copy(showNotionConfigDialog = true)
    }

    fun hideNotionConfigDialog() {
        _uiState.value = _uiState.value.copy(showNotionConfigDialog = false)
    }

    fun saveNotionToken(token: String) {
        viewModelScope.launch {
            if (token.isBlank()) {
                securePreferences.removeNotionToken()
                mcpDao.setServerEnabled("notion", false)
                _uiState.value = _uiState.value.copy(
                    notionToken = "", notionIsConfigured = false, showNotionConfigDialog = false
                )
            } else {
                securePreferences.saveNotionToken(token)
                mcpDao.updateServerConfig("notion", """{"isConfigured":true}""")
                mcpDao.setServerEnabled("notion", true)
                _uiState.value = _uiState.value.copy(
                    notionToken = token, notionIsConfigured = true, showNotionConfigDialog = false
                )
            }
        }
    }

    // Slack
    private fun loadSlackConfig() {
        val token = securePreferences.getSlackToken() ?: ""
        _uiState.value = _uiState.value.copy(
            slackToken = token,
            slackIsConfigured = token.isNotBlank()
        )
    }

    fun showSlackConfigDialog() {
        _uiState.value = _uiState.value.copy(showSlackConfigDialog = true)
    }

    fun hideSlackConfigDialog() {
        _uiState.value = _uiState.value.copy(showSlackConfigDialog = false)
    }

    fun saveSlackToken(token: String) {
        viewModelScope.launch {
            if (token.isBlank()) {
                securePreferences.removeSlackToken()
                mcpDao.setServerEnabled("slack", false)
                _uiState.value = _uiState.value.copy(
                    slackToken = "", slackIsConfigured = false, showSlackConfigDialog = false
                )
            } else {
                securePreferences.saveSlackToken(token)
                mcpDao.updateServerConfig("slack", """{"isConfigured":true}""")
                mcpDao.setServerEnabled("slack", true)
                _uiState.value = _uiState.value.copy(
                    slackToken = token, slackIsConfigured = true, showSlackConfigDialog = false
                )
            }
        }
    }

    private fun loadGoogleImagenConfig() {
        val apiKey = securePreferences.getGoogleImagenApiKey() ?: ""
        _uiState.value = _uiState.value.copy(
            googleImagenApiKey = apiKey,
            googleImagenIsConfigured = apiKey.isNotBlank()
        )
    }

    fun saveGoogleImagenApiKey(apiKey: String) {
        viewModelScope.launch {
            if (apiKey.isBlank()) {
                securePreferences.removeGoogleImagenApiKey()
                _uiState.value = _uiState.value.copy(
                    googleImagenApiKey = "",
                    googleImagenIsConfigured = false,
                    showGoogleImagenConfigDialog = false
                )
            } else {
                securePreferences.saveGoogleImagenApiKey(apiKey)
                _uiState.value = _uiState.value.copy(
                    googleImagenApiKey = apiKey,
                    googleImagenIsConfigured = true,
                    showGoogleImagenConfigDialog = false
                )
            }
        }
    }

    private fun loadDalleConfig() {
        val apiKey = securePreferences.getOpenAIApiKey() ?: ""
        _uiState.value = _uiState.value.copy(
            dalleApiKey = apiKey,
            dalleIsConfigured = apiKey.isNotBlank()
        )
    }

    fun saveDalleApiKey(apiKey: String) {
        viewModelScope.launch {
            if (apiKey.isBlank()) {
                securePreferences.removeOpenAIApiKey()
                _uiState.value = _uiState.value.copy(
                    dalleApiKey = "",
                    dalleIsConfigured = false,
                    showDalleConfigDialog = false
                )
            } else {
                securePreferences.saveOpenAIApiKey(apiKey)
                _uiState.value = _uiState.value.copy(
                    dalleApiKey = apiKey,
                    dalleIsConfigured = true,
                    showDalleConfigDialog = false
                )
            }
        }
    }

    fun saveSerpApiKey(apiKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                if (apiKey.isBlank()) {
                    // Eliminar configuracion
                    securePreferences.removeSerpApiKey()
                    mcpDao.setServerEnabled("serpapi", false)
                    val selectedProvider = if (_uiState.value.webSearchProvider == WebSearchProvider.SERPAPI) {
                        WebSearchProvider.NATIVE.also(securePreferences::setWebSearchProvider)
                    } else {
                        _uiState.value.webSearchProvider
                    }

                    _uiState.value = _uiState.value.copy(
                        webSearchProvider = selectedProvider,
                        serpApiKey = "",
                        serpApiIsConfigured = false,
                        isLoading = false,
                        showSerpApiConfigDialog = false
                    )
                } else {
                    // Guardar configuracion
                    securePreferences.saveSerpApiKey(apiKey)

                    val config = SerpApiConfig(
                        apiKey = apiKey,
                        isConfigured = true
                    )

                    mcpDao.updateServerConfig(
                        id = "serpapi",
                        configJson = """{"apiKey":"${apiKey.take(4)}...","isConfigured":true}"""
                    )
                    mcpDao.setServerEnabled("serpapi", true)

                    _uiState.value = _uiState.value.copy(
                        serpApiKey = apiKey,
                        serpApiIsConfigured = true,
                        isLoading = false,
                        showSerpApiConfigDialog = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = mcpError(e, "serpapi_configuration")
                )
            }
        }
    }

    private fun mcpError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(component = "mcp_settings", operation = operation)
        ).displayMessage
}
