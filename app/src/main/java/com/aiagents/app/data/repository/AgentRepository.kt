package com.aiagents.app.data.repository

import android.util.Log
import com.aiagents.app.data.local.AgentDao
import com.aiagents.app.data.local.CommandPermissionDao
import com.aiagents.app.data.local.ConversationDao
import com.aiagents.app.data.local.FileDao
import com.aiagents.app.data.local.MessageDao
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.local.WorkspaceDao
import com.aiagents.app.data.model.AgentEntity
import com.aiagents.app.data.model.CommandPermissionEntity
import com.aiagents.app.data.model.ConversationEntity
import com.aiagents.app.data.model.FileEntity
import com.aiagents.app.data.model.MessageEntity
import com.aiagents.app.data.model.PermissionLevel
import com.aiagents.app.data.model.WorkspaceEntity
import com.aiagents.app.data.remote.AIClientFactory
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.data.remote.ChatResponseWithTools
import com.aiagents.app.data.remote.StreamingChunk
import com.aiagents.app.data.terminal.AgentCreatorToolHandler
import com.aiagents.app.data.terminal.AgentSelectionToolHandler
import com.aiagents.app.data.terminal.BraveSearchToolHandler
import com.aiagents.app.data.terminal.DuckDuckGoSearchToolHandler
import com.aiagents.app.data.terminal.GoogleMapsToolHandler
import com.aiagents.app.data.terminal.SerpAPIToolHandler
import com.aiagents.app.data.terminal.CanvaToolHandler
import com.aiagents.app.data.terminal.PubMedToolHandler
import com.aiagents.app.data.terminal.LocationToolHandler
import com.aiagents.app.data.terminal.ObsidianToolHandler
import com.aiagents.app.data.terminal.GitHubToolHandler
import com.aiagents.app.data.terminal.PresentationToolHandler
import com.aiagents.app.data.terminal.NotionToolHandler
import com.aiagents.app.data.terminal.SlackToolHandler
import com.aiagents.app.data.terminal.GoogleDriveToolHandler
import com.aiagents.app.data.terminal.GoogleWorkspaceToolHandler
import com.aiagents.app.data.terminal.ReminderToolHandler
import com.aiagents.app.data.terminal.CodeExecutionHandler
import com.aiagents.app.data.terminal.FinanceToolHandler
import com.aiagents.app.data.terminal.MemoryToolHandler
import com.aiagents.app.data.terminal.AcademicSearchToolHandler
import com.aiagents.app.data.terminal.WeatherToolHandler
import com.aiagents.app.data.terminal.ImageGenerationToolHandler
import com.aiagents.app.data.auth.GoogleDriveOAuthManager
import com.aiagents.app.data.auth.GoogleWorkspaceOAuthManager
import com.aiagents.app.data.terminal.CalendarToolHandler
import com.aiagents.app.data.terminal.FileToolHandler
import com.aiagents.app.data.terminal.SystemAppToolHandler
import com.aiagents.app.data.terminal.ShellExecutor
import com.aiagents.app.data.terminal.ToolHandler
import com.aiagents.app.data.terminal.AppControlToolHandler
import com.aiagents.app.data.terminal.ScheduledTaskToolHandler
import com.aiagents.app.data.terminal.TodoToolHandler
import com.aiagents.app.data.terminal.SubtaskToolHandler
import com.aiagents.app.data.terminal.DelegationToolHandler
import com.aiagents.app.data.terminal.ToolSearchHandler
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.AgentFile
import com.aiagents.app.domain.model.Conversation
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolResult
import com.aiagents.app.domain.model.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRepository @Inject constructor(
    private val agentDao: AgentDao,
    private val messageDao: MessageDao,
    private val fileDao: FileDao,
    private val workspaceDao: WorkspaceDao,
    private val conversationDao: ConversationDao,
    private val commandPermissionDao: CommandPermissionDao,
    private val securePreferences: SecurePreferences,
    private val aiClientFactory: AIClientFactory,
    private val shellExecutor: ShellExecutor,
    private val toolHandler: ToolHandler,
    private val fileToolHandler: FileToolHandler,
    private val agentSelectionToolHandler: AgentSelectionToolHandler,
    private val agentCreatorToolHandler: AgentCreatorToolHandler,
    private val calendarToolHandler: CalendarToolHandler,
    private val systemAppToolHandler: SystemAppToolHandler,
    private val duckDuckGoSearchToolHandler: DuckDuckGoSearchToolHandler,
    private val braveSearchToolHandler: BraveSearchToolHandler,
    private val googleMapsToolHandler: GoogleMapsToolHandler,
    private val serpAPIToolHandler: SerpAPIToolHandler,
    private val canvaToolHandler: CanvaToolHandler,
    private val pubMedToolHandler: PubMedToolHandler,
    private val locationToolHandler: LocationToolHandler,
    private val obsidianToolHandler: ObsidianToolHandler,
    private val gitHubToolHandler: GitHubToolHandler,
    private val notionToolHandler: NotionToolHandler,
    private val slackToolHandler: SlackToolHandler,
    private val googleDriveToolHandler: GoogleDriveToolHandler,
    private val googleDriveOAuthManager: GoogleDriveOAuthManager,
    private val googleWorkspaceToolHandler: GoogleWorkspaceToolHandler,
    private val googleWorkspaceOAuthManager: GoogleWorkspaceOAuthManager,
    private val reminderToolHandler: ReminderToolHandler,
    private val memoryToolHandler: MemoryToolHandler,
    private val presentationToolHandler: PresentationToolHandler,
    private val financeToolHandler: FinanceToolHandler,
    private val toolSearchHandler: ToolSearchHandler,
    private val subtaskToolHandler: SubtaskToolHandler,
    private val academicSearchToolHandler: AcademicSearchToolHandler,
    private val weatherToolHandler: WeatherToolHandler,
    private val imageGenerationToolHandler: ImageGenerationToolHandler,
    private val appControlToolHandler: AppControlToolHandler,
    private val todoToolHandler: TodoToolHandler,
    private val scheduledTaskToolHandler: ScheduledTaskToolHandler,
    private val localModelRepository: com.aiagents.app.data.local.LocalModelRepository? = null
) {
    fun getAllAgents(): Flow<List<Agent>> {
        return agentDao.getAllAgents().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getAgentById(id: Long): Agent? {
        return agentDao.getAgentById(id)?.toDomain()
    }

    suspend fun createAgent(agent: Agent): Long {
        return agentDao.insertAgent(AgentEntity.fromDomain(agent))
    }

    suspend fun updateAgent(agent: Agent) {
        agentDao.updateAgent(AgentEntity.fromDomain(agent))
    }

    suspend fun deleteAgent(id: Long) {
        agentDao.deleteAgentById(id)
    }

    fun getAllWorkspaces(): Flow<List<Workspace>> {
        return workspaceDao.getAllWorkspaces().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getWorkspaceById(id: Long): Workspace? {
        return workspaceDao.getWorkspaceById(id)?.toDomain()
    }

    suspend fun createWorkspace(workspace: Workspace): Long {
        return workspaceDao.insertWorkspace(WorkspaceEntity.fromDomain(workspace))
    }

    suspend fun updateWorkspace(workspace: Workspace) {
        workspaceDao.updateWorkspace(WorkspaceEntity.fromDomain(workspace))
    }

    suspend fun deleteWorkspace(id: Long) {
        workspaceDao.deleteWorkspaceById(id)
    }

    suspend fun setActiveAgent(workspaceId: Long, agentId: Long?) {
        workspaceDao.setActiveAgent(workspaceId, agentId)
    }

    suspend fun setSelectedModel(workspaceId: Long, model: String) {
        workspaceDao.setSelectedModel(workspaceId, model)
    }

    suspend fun setExternalStorageUri(workspaceId: Long, uri: String?) {
        workspaceDao.setExternalStorageUri(workspaceId, uri)
    }

    suspend fun getAvailableModelsForProvider(providerType: ProviderType): List<String> {
        // Para proveedor LOCAL, usar modelos descargados
        if (providerType == ProviderType.LOCAL) {
            return localModelRepository?.getDownloadedModels()?.map { it.id } ?: emptyList()
        }

        // Manejar Moonshot con múltiples endpoints
        if (providerType == ProviderType.MOONSHOT) {
            val (apiKey, baseUrl) = getResolvedMoonshotPair() ?: return emptyList()
            val client = aiClientFactory.createClient(providerType, apiKey, baseUrl)
            return client.getAvailableModels().getOrDefault(emptyList())
        }

        // Ollama no requiere API key — solo necesita la URL base
        val apiKey = if (providerType == ProviderType.OLLAMA) ""
                     else securePreferences.getApiKey(providerType) ?: return emptyList()
        val baseUrl = securePreferences.getBaseUrl(providerType)
        val client = aiClientFactory.createClient(providerType, apiKey, baseUrl)
        return client.getAvailableModels().getOrDefault(emptyList())
    }

    suspend fun getAllAvailableModels(): Map<ProviderType, List<String>> {
        return ProviderType.entries.associateWith { provider ->
            getAvailableModelsForProvider(provider)
        }
    }

    fun getMessagesForWorkspace(workspaceId: Long): Flow<List<Message>> {
        return messageDao.getMessagesForWorkspace(workspaceId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun addMessage(workspaceId: Long, message: Message, agentId: Long? = null): Long {
        return messageDao.insertMessage(MessageEntity.fromDomain(message, workspaceId, agentId))
    }

    suspend fun addMessage(workspaceId: Long, conversationId: Long?, message: Message, agentId: Long? = null): Long {
        return messageDao.insertMessage(MessageEntity.fromDomain(message, workspaceId, agentId, conversationId))
    }

    suspend fun clearMessages(workspaceId: Long) {
        messageDao.deleteMessagesForWorkspace(workspaceId)
    }

    // ── Conversation operations ──────────────────────────────
    fun getConversationsForWorkspace(workspaceId: Long): Flow<List<Conversation>> {
        return conversationDao.getConversationsForWorkspace(workspaceId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getConversationById(id: Long): Conversation? {
        return conversationDao.getConversationById(id)?.toDomain()
    }

    suspend fun createConversation(conversation: Conversation): Long {
        return conversationDao.insertConversation(ConversationEntity.fromDomain(conversation))
    }

    suspend fun deleteConversation(id: Long) {
        conversationDao.deleteConversationById(id)
    }

    suspend fun updateConversationTitle(id: Long, title: String) {
        conversationDao.updateTitle(id, title)
    }

    suspend fun touchConversation(id: Long) {
        conversationDao.touchConversation(id)
    }

    suspend fun getLatestConversation(workspaceId: Long): Conversation? {
        return conversationDao.getLatestConversation(workspaceId)?.toDomain()
    }

    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun clearConversation(conversationId: Long) {
        messageDao.deleteMessagesForConversation(conversationId)
    }

    fun getFilesForWorkspace(workspaceId: Long): Flow<List<AgentFile>> {
        return fileDao.getFilesForWorkspace(workspaceId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun addFile(file: AgentFile): Long {
        return fileDao.insertFile(FileEntity.fromDomain(file))
    }

    suspend fun deleteFile(id: Long) {
        fileDao.deleteFileById(id)
    }

    suspend fun chat(
        agent: Agent,
        messages: List<Message>,
        overrideModel: String? = null,
        overrideProvider: ProviderType? = null
    ): Result<String> {
        val activeProvider = overrideProvider ?: getActiveProvider() ?: getFirstConfiguredProvider()
        if (activeProvider == null) {
            Log.e("AgentRepository", "No provider configured")
            return Result.failure(Exception("No hay ningún proveedor configurado. Ve a Proveedores para configurar uno."))
        }
        
        val modelToUse = overrideModel ?: ""
        if (modelToUse.isEmpty()) {
            Log.e("AgentRepository", "No model selected")
            return Result.failure(Exception("Selecciona un modelo primero."))
        }
        
        Log.d("AgentRepository", "Chat request - Provider: $activeProvider, Model: $modelToUse")

        // Obtener API key y base URL según el proveedor
        val (apiKey, baseUrl) = when {
            activeProvider == ProviderType.LOCAL || activeProvider == ProviderType.OLLAMA -> "" to null
            activeProvider == ProviderType.MOONSHOT -> {
                getResolvedMoonshotPair() ?: run {
                    Log.e("AgentRepository", "API Key not found for any Moonshot endpoint")
                    return Result.failure(Exception("API Key no configurada para Moonshot"))
                }
            }
            else -> {
                val key = securePreferences.getApiKey(activeProvider)
                if (key == null) {
                    Log.e("AgentRepository", "API Key not found for provider: $activeProvider")
                    return Result.failure(Exception("API Key no configurada para $activeProvider"))
                }
                key to securePreferences.getBaseUrl(activeProvider)
            }
        }

        if (activeProvider != ProviderType.LOCAL) {
            Log.d("AgentRepository", "API Key found: ${apiKey.take(8)}...")
        }
        Log.d("AgentRepository", "Base URL: $baseUrl")
        
        val client = aiClientFactory.createClient(activeProvider, apiKey, baseUrl)
        Log.d("AgentRepository", "Client created: ${client::class.simpleName}")
        
        val chatMessages = messages.map { msg ->
            ChatMessage(
                role = msg.role.name.lowercase(),
                content = msg.content
            )
        }
        
        Log.d("AgentRepository", "Sending ${chatMessages.size} messages to API")
        return client.chat(
            model = modelToUse,
            messages = chatMessages,
            systemPrompt = agent.systemPrompt,
            temperature = agent.temperature,
            maxTokens = agent.maxTokens
        )
    }

    /**
     * Simple chat call with raw parameters (no Agent needed).
     * Used for internal operations like context summarization.
     */
    suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        provider: ProviderType?
    ): Result<String> {
        val activeProvider = provider ?: getActiveProvider() ?: getFirstConfiguredProvider()
            ?: return Result.failure(Exception("No provider configured"))
        val (apiKey, baseUrl) = when {
            activeProvider == ProviderType.LOCAL || activeProvider == ProviderType.OLLAMA -> "" to null
            activeProvider == ProviderType.MOONSHOT -> {
                getResolvedMoonshotPair() ?: return Result.failure(Exception("API Key no configurada para Moonshot"))
            }
            else -> {
                val key = securePreferences.getApiKey(activeProvider) ?: return Result.failure(Exception("No API key for $activeProvider"))
                key to securePreferences.getBaseUrl(activeProvider)
            }
        }
        val client = aiClientFactory.createClient(activeProvider, apiKey, baseUrl)
        return client.chat(model, messages, systemPrompt, temperature, maxTokens)
    }

    suspend fun getAvailableModels(providerType: ProviderType): Result<List<String>> {
        if (providerType == ProviderType.LOCAL) {
            val models = localModelRepository?.getDownloadedModels()?.map { it.id } ?: emptyList()
            return Result.success(models)
        }
        // Moonshot usa API keys por endpoint, no la key genérica
        if (providerType == ProviderType.MOONSHOT) {
            val (key, baseUrl) = getResolvedMoonshotPair() ?: return Result.success(emptyList())
            val client = aiClientFactory.createClient(providerType, key, baseUrl)
            return client.getAvailableModels()
        }
        // Ollama no requiere API key — solo necesita la URL base
        val apiKey = if (providerType == ProviderType.OLLAMA) ""
                     else securePreferences.getApiKey(providerType) ?: return Result.success(emptyList())
        val baseUrl = securePreferences.getBaseUrl(providerType)
        val client = aiClientFactory.createClient(providerType, apiKey, baseUrl)
        return client.getAvailableModels()
    }

    suspend fun getAvailableModelsForActiveProvider(): Result<List<String>> {
        val activeProvider = getActiveProvider() ?: getFirstConfiguredProvider()
        if (activeProvider == null) {
            return Result.success(emptyList())
        }
        return getAvailableModels(activeProvider)
    }

    // ── Modelos seleccionados multi-proveedor ──────────────────────────────
    val selectedModelsFlow = securePreferences.selectedModelsChanged
    fun getSelectedModels(): Set<String> = securePreferences.getSelectedModels()
    fun addSelectedModel(provider: ProviderType, modelId: String) =
        securePreferences.addSelectedModel(provider, modelId)
    fun removeSelectedModel(provider: ProviderType, modelId: String) =
        securePreferences.removeSelectedModel(provider, modelId)
    fun isModelSelected(provider: ProviderType, modelId: String): Boolean =
        securePreferences.isModelSelected(provider, modelId)

    fun saveApiKey(provider: ProviderType, apiKey: String) {
        securePreferences.saveApiKey(provider, apiKey)
    }

    fun getApiKey(provider: ProviderType): String? {
        return securePreferences.getApiKey(provider)
    }

    fun saveBaseUrl(provider: ProviderType, baseUrl: String) {
        securePreferences.saveBaseUrl(provider, baseUrl)
    }

    fun getBaseUrl(provider: ProviderType): String? {
        return securePreferences.getBaseUrl(provider)
    }

    fun hasApiKey(provider: ProviderType): Boolean {
        // Para proveedor LOCAL, verificar si hay modelos descargados
        if (provider == ProviderType.LOCAL) {
            return localModelRepository?.getDownloadedModels()?.isNotEmpty() == true
        }
        // Ollama no requiere API key — siempre está "configurado" (usa URL por defecto si no hay una guardada)
        if (provider == ProviderType.OLLAMA) return true
        // Moonshot puede estar configurado con cualquiera de sus 3 endpoints
        if (provider == ProviderType.MOONSHOT) return isAnyMoonshotEndpointConfigured()
        return securePreferences.hasApiKey(provider)
    }

    fun setActiveProvider(provider: ProviderType) {
        securePreferences.setActiveProvider(provider)
    }

    fun getActiveProvider(): ProviderType? {
        return securePreferences.getActiveProvider()
    }

    fun getFirstConfiguredProvider(): ProviderType? {
        return ProviderType.entries.find { hasApiKey(it) }
    }

    suspend fun chatWithTools(
        agent: Agent,
        messages: List<Message>,
        overrideModel: String? = null,
        overrideProvider: ProviderType? = null,
        enableTerminal: Boolean = false,
        workspaceFolderPath: String? = null
    ): Result<ChatResponseWithTools> {
        val activeProvider = overrideProvider ?: getActiveProvider() ?: getFirstConfiguredProvider()
        if (activeProvider == null) {
            Log.e("AgentRepository", "No provider configured")
            return Result.failure(Exception("No hay ningún proveedor configurado. Ve a Proveedores para configurar uno."))
        }
        
        val modelToUse = overrideModel ?: ""
        if (modelToUse.isEmpty()) {
            Log.e("AgentRepository", "No model selected")
            return Result.failure(Exception("Selecciona un modelo primero."))
        }
        
        Log.d("AgentRepository", "Chat request - Provider: $activeProvider, Model: $modelToUse, Terminal: $enableTerminal")

        // Obtener API key y base URL según el proveedor
        val (apiKey, baseUrl) = when {
            activeProvider == ProviderType.LOCAL || activeProvider == ProviderType.OLLAMA -> "" to null
            activeProvider == ProviderType.MOONSHOT -> {
                getResolvedMoonshotPair() ?: run {
                    Log.e("AgentRepository", "API Key not found for any Moonshot endpoint")
                    return Result.failure(Exception("API Key no configurada para Moonshot"))
                }
            }
            else -> {
                val key = securePreferences.getApiKey(activeProvider)
                if (key == null) {
                    Log.e("AgentRepository", "API Key not found for provider: $activeProvider")
                    return Result.failure(Exception("API Key no configurada para $activeProvider"))
                }
                key to securePreferences.getBaseUrl(activeProvider)
            }
        }

        val client = aiClientFactory.createClient(activeProvider, apiKey, baseUrl)
        
        val chatMessages = messages.map { msg ->
            val toolResult = msg.toolResults.firstOrNull()
            val toolResultContent = toolResult?.content
            val isImageResult = toolResultContent?.startsWith("data:image/") == true
            // Si el mensaje tiene imageDataUris adjuntos (imágenes del usuario), usarlos
            val userImageUri = msg.imageDataUris.firstOrNull()
            
            // NOTA: Los mensajes TOOL no pueden tener imágenes en la API de OpenAI.
            // El resultado de read_image_file es un data URI que no debe enviarse como imageDataUri
            // en mensajes TOOL porque causaría un error de formato.
            // En su lugar, el contenido del mensaje TOOL debe describir que se leyó una imagen.
            val isToolMessage = msg.role.name.lowercase() == "tool"
            
            ChatMessage(
                role = msg.role.name.lowercase(),
                content = msg.content,
                toolCalls = msg.toolCalls.ifEmpty { null },
                toolCallId = toolResult?.toolCallId,
                name = toolResult?.name,
                imageDataUri = if (!isToolMessage) (if (isImageResult) toolResultContent else userImageUri) else null,
                // Imagen de resultado de tool: solo AnthropicClient la usa para el bloque tool_result
                toolResultImageUri = if (isToolMessage && isImageResult) toolResultContent else null
            )
        }
        
        val tools = buildToolDefinitions(agent, enableTerminal, workspaceFolderPath)

        val sanitized = sanitizeToolCallHistory(chatMessages)
        Log.d("AgentRepository", "Sending ${sanitized.size} messages to API with ${tools.size} tools")
        return client.chatWithTools(
            model = modelToUse,
            messages = sanitized,
            systemPrompt = agent.systemPrompt,
            temperature = agent.temperature,
            maxTokens = agent.maxTokens,
            tools = tools
        )
    }

    /**
     * Streaming version of chatWithTools. Returns a Flow of StreamingChunks.
     */
    fun chatWithToolsStreaming(
        agent: Agent,
        messages: List<Message>,
        overrideModel: String? = null,
        overrideProvider: ProviderType? = null,
        enableTerminal: Boolean = false,
        workspaceFolderPath: String? = null
    ): Flow<StreamingChunk> {
        val activeProvider = overrideProvider ?: getActiveProvider() ?: getFirstConfiguredProvider()
        if (activeProvider == null) {
            return kotlinx.coroutines.flow.flow {
                emit(StreamingChunk(error = "No hay ningún proveedor configurado."))
            }
        }

        val modelToUse = overrideModel ?: ""
        if (modelToUse.isEmpty()) {
            return kotlinx.coroutines.flow.flow {
                emit(StreamingChunk(error = "Selecciona un modelo primero."))
            }
        }

        val (apiKey, baseUrl) = when {
            activeProvider == ProviderType.LOCAL || activeProvider == ProviderType.OLLAMA -> "" to null
            activeProvider == ProviderType.MOONSHOT -> {
                getResolvedMoonshotPair() ?: return kotlinx.coroutines.flow.flow {
                    emit(StreamingChunk(error = "API Key no configurada para Moonshot"))
                }
            }
            else -> {
                val key = securePreferences.getApiKey(activeProvider)
                if (key == null) {
                    return kotlinx.coroutines.flow.flow {
                        emit(StreamingChunk(error = "API Key no configurada para $activeProvider"))
                    }
                }
                key to securePreferences.getBaseUrl(activeProvider)
            }
        }

        val client = aiClientFactory.createClient(activeProvider, apiKey, baseUrl)

        val chatMessages = messages.map { msg ->
            val toolResult = msg.toolResults.firstOrNull()
            val toolResultContent = toolResult?.content
            val isImageResult = toolResultContent?.startsWith("data:image/") == true
            val userImageUri = msg.imageDataUris.firstOrNull()
            val isToolMessage = msg.role.name.lowercase() == "tool"

            ChatMessage(
                role = msg.role.name.lowercase(),
                content = msg.content,
                toolCalls = msg.toolCalls.ifEmpty { null },
                toolCallId = toolResult?.toolCallId,
                name = toolResult?.name,
                imageDataUri = if (!isToolMessage) (if (isImageResult) toolResultContent else userImageUri) else null,
                toolResultImageUri = if (isToolMessage && isImageResult) toolResultContent else null
            )
        }

        val tools = buildToolDefinitions(agent, enableTerminal, workspaceFolderPath)

        val sanitized = sanitizeToolCallHistory(chatMessages)
        Log.d("AgentRepository", "Streaming ${sanitized.size} messages to API with ${tools.size} tools")
        return client.chatWithToolsStreaming(
            model = modelToUse,
            messages = sanitized,
            systemPrompt = agent.systemPrompt,
            temperature = agent.temperature,
            maxTokens = agent.maxTokens,
            tools = tools
        )
    }

    /**
     * Chat con soporte para imágenes (vision).
     * Envía las imágenes como contenido adicional en el mensaje del usuario.
     */
    suspend fun chatWithImages(
        agent: Agent,
        messages: List<Message>,
        overrideModel: String? = null,
        overrideProvider: ProviderType? = null,
        enableTerminal: Boolean = false,
        workspaceFolderPath: String? = null,
        imageDataUris: List<String> = emptyList()
    ): Result<ChatResponseWithTools> {
        val activeProvider = overrideProvider ?: getActiveProvider() ?: getFirstConfiguredProvider()
        if (activeProvider == null) {
            Log.e("AgentRepository", "No provider configured")
            return Result.failure(Exception("No hay ningún proveedor configurado. Ve a Proveedores para configurar uno."))
        }
        
        val modelToUse = overrideModel ?: ""
        if (modelToUse.isEmpty()) {
            Log.e("AgentRepository", "No model selected")
            return Result.failure(Exception("Selecciona un modelo primero."))
        }
        
        Log.d("AgentRepository", "Chat with images - Provider: $activeProvider, Model: $modelToUse, Images: ${imageDataUris.size}")

        // Obtener API key y base URL según el proveedor
        val (apiKey, baseUrl) = when {
            activeProvider == ProviderType.LOCAL || activeProvider == ProviderType.OLLAMA -> "" to null
            activeProvider == ProviderType.MOONSHOT -> {
                getResolvedMoonshotPair() ?: run {
                    Log.e("AgentRepository", "API Key not found for any Moonshot endpoint")
                    return Result.failure(Exception("API Key no configurada para Moonshot"))
                }
            }
            else -> {
                val key = securePreferences.getApiKey(activeProvider)
                if (key == null) {
                    Log.e("AgentRepository", "API Key not found for provider: $activeProvider")
                    return Result.failure(Exception("API Key no configurada para $activeProvider"))
                }
                key to securePreferences.getBaseUrl(activeProvider)
            }
        }

        val client = aiClientFactory.createClient(activeProvider, apiKey, baseUrl)
        
        val chatMessages = messages.map { msg ->
            val toolResult = msg.toolResults.firstOrNull()
            val toolResultContent = toolResult?.content
            val isImageResult = toolResultContent?.startsWith("data:image/") == true
            // Usar la primera imagen del mensaje si está disponible
            val userImageUri = msg.imageDataUris.firstOrNull()
            
            // NOTA: Los mensajes TOOL no pueden tener imágenes en la API de OpenAI.
            val isToolMessage = msg.role.name.lowercase() == "tool"
            
            ChatMessage(
                role = msg.role.name.lowercase(),
                content = msg.content,
                toolCalls = msg.toolCalls.ifEmpty { null },
                toolCallId = toolResult?.toolCallId,
                name = toolResult?.name,
                imageDataUri = if (!isToolMessage) (if (isImageResult) toolResultContent else userImageUri) else null,
                toolResultImageUri = if (isToolMessage && isImageResult) toolResultContent else null
            )
        }
        
        val tools = buildToolDefinitions(agent, enableTerminal, workspaceFolderPath)

        val sanitized = sanitizeToolCallHistory(chatMessages)
        Log.d("AgentRepository", "Sending ${sanitized.size} messages with ${imageDataUris.size} images")
        return client.chatWithTools(
            model = modelToUse,
            messages = sanitized,
            systemPrompt = agent.systemPrompt,
            temperature = agent.temperature,
            maxTokens = agent.maxTokens,
            tools = tools
        )
    }

    fun getShellExecutor(): ShellExecutor = shellExecutor

    fun getToolHandler(): ToolHandler = toolHandler

    fun getFileToolHandler(): FileToolHandler = fileToolHandler

    fun getAgentSelectionToolHandler(): AgentSelectionToolHandler = agentSelectionToolHandler
    fun getAgentCreatorToolHandler(): AgentCreatorToolHandler = agentCreatorToolHandler

    fun getCalendarToolHandler(): CalendarToolHandler = calendarToolHandler

    fun getSystemAppToolHandler(): SystemAppToolHandler = systemAppToolHandler

    fun getDuckDuckGoSearchToolHandler(): DuckDuckGoSearchToolHandler = duckDuckGoSearchToolHandler

    fun getBraveSearchToolHandler(): BraveSearchToolHandler = braveSearchToolHandler

    fun getGoogleMapsToolHandler(): GoogleMapsToolHandler = googleMapsToolHandler

    fun getGoogleMapsApiKey(): String = securePreferences.getGoogleMapsApiKey() ?: ""

    fun getBraveApiKey(): String = securePreferences.getBraveApiKey() ?: ""

    fun getSerpAPIToolHandler(): SerpAPIToolHandler = serpAPIToolHandler

    fun getSerpApiKey(): String = securePreferences.getSerpApiKey() ?: ""

    fun getCanvaToolHandler(): CanvaToolHandler = canvaToolHandler

    fun getCanvaAccessToken(): String = securePreferences.getCanvaAccessToken() ?: ""

    fun getPubMedToolHandler(): PubMedToolHandler = pubMedToolHandler

    fun getLocationToolHandler(): LocationToolHandler = locationToolHandler

    fun getObsidianToolHandler(): ObsidianToolHandler = obsidianToolHandler
    fun getGitHubToolHandler(): GitHubToolHandler = gitHubToolHandler
    fun getNotionToolHandler(): NotionToolHandler = notionToolHandler
    fun getSlackToolHandler(): SlackToolHandler = slackToolHandler
    fun getGoogleDriveToolHandler(): GoogleDriveToolHandler = googleDriveToolHandler
    fun getGoogleDriveOAuthManager(): GoogleDriveOAuthManager = googleDriveOAuthManager
    fun getGoogleWorkspaceToolHandler(): GoogleWorkspaceToolHandler = googleWorkspaceToolHandler
    fun getGoogleWorkspaceOAuthManager(): GoogleWorkspaceOAuthManager = googleWorkspaceOAuthManager
    fun getReminderToolHandler(): ReminderToolHandler = reminderToolHandler
    fun getMemoryToolHandler(): MemoryToolHandler = memoryToolHandler

    /** Returns compact string of important memories (importance >= 7) for prompt injection. */
    suspend fun getImportantMemoriesCompact(): String? {
        return try {
            val memories = memoryToolHandler.getImportantMemories()
            if (memories.isEmpty()) null
            else "Known: ${memories.joinToString(", ") { it.content }}"
        } catch (_: Exception) { null }
    }
    fun getToolSearchHandler(): ToolSearchHandler = toolSearchHandler
    fun getSubtaskToolHandler(): SubtaskToolHandler = subtaskToolHandler
    fun getPresentationToolHandler(): PresentationToolHandler = presentationToolHandler
    suspend fun getValidGoogleDriveToken(): String = googleDriveOAuthManager.getValidAccessToken() ?: ""

    fun isPubMedEnabled(): Boolean = securePreferences.isPubMedEnabled()

    fun getFinanceToolHandler(): FinanceToolHandler = financeToolHandler
    fun isFinanceEnabled(): Boolean = securePreferences.isFinanceEnabled()

    fun getAcademicSearchToolHandler(): AcademicSearchToolHandler = academicSearchToolHandler
    fun getWeatherToolHandler(): WeatherToolHandler = weatherToolHandler
    fun getImageGenerationToolHandler(): ImageGenerationToolHandler = imageGenerationToolHandler
    fun getOpenWeatherApiKey(): String = securePreferences.getOpenWeatherApiKey() ?: ""
    fun getOpenAIApiKey(): String = securePreferences.getOpenAIApiKey() ?: ""
    fun getGoogleImagenApiKey(): String = securePreferences.getGoogleImagenApiKey() ?: ""
    fun isWeatherEnabled(): Boolean = securePreferences.isWeatherEnabled()
    fun isImageGenerationEnabled(): Boolean = securePreferences.isImageGenerationEnabled()
    fun getAppControlToolHandler(): AppControlToolHandler = appControlToolHandler
    fun getTodoToolHandler(): TodoToolHandler = todoToolHandler
    fun getScheduledTaskToolHandler(): ScheduledTaskToolHandler = scheduledTaskToolHandler

    /**
     * Builds the full tool definition list for a given agent, respecting enabledTools filter.
     * Supports deferred mode: when there are many tools (> threshold), only sends core tools
     * plus a search_tools meta-tool. The model discovers additional tools via search_tools.
     */
    private var cachedAllTools: List<Map<String, Any>>? = null
    private var cachedAllToolsKey: String? = null
    private val activatedToolNames = mutableSetOf<String>()

    /** Add tool names discovered via search_tools to the active set */
    fun activateTools(names: Set<String>) {
        activatedToolNames.addAll(names)
        Log.d("AgentRepository", "Activated ${names.size} tools: $names (total active: ${activatedToolNames.size})")
    }

    /** Reset activated tools (call at the start of each new user message) */
    fun resetActivatedTools() {
        if (activatedToolNames.isNotEmpty()) {
            Log.d("AgentRepository", "Reset ${activatedToolNames.size} activated tools")
            activatedToolNames.clear()
        }
    }

    /** Get the full list of all available tool names for search filtering */
    fun getAllAvailableToolNames(agent: Agent, enableTerminal: Boolean, workspaceFolderPath: String?): Set<String> {
        val allTools = buildAllToolDefinitions(agent, enableTerminal, workspaceFolderPath)
        return allTools.mapNotNull { tool ->
            @Suppress("UNCHECKED_CAST")
            val funcMap = tool["function"] as? Map<String, Any>
            funcMap?.get("name") as? String
        }.toSet()
    }

    /**
     * Sanitize message history so every assistant tool_call has a matching tool response.
     * Strips orphaned tool_calls to prevent 400 errors from APIs.
     */
    private fun sanitizeToolCallHistory(messages: List<ChatMessage>): List<ChatMessage> {
        // Collect all tool_call_ids that have a tool response
        val respondedToolCallIds = messages
            .filter { it.role == "tool" && it.toolCallId != null }
            .map { it.toolCallId }
            .toSet()

        return messages.map { msg ->
            if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
                val validCalls = msg.toolCalls.filter { it.id in respondedToolCallIds }
                if (validCalls.size == msg.toolCalls.size) {
                    msg // all tool calls have responses
                } else if (validCalls.isEmpty()) {
                    // No valid tool calls — strip them entirely
                    msg.copy(toolCalls = null)
                } else {
                    msg.copy(toolCalls = validCalls)
                }
            } else {
                msg
            }
        }.filter { msg ->
            // Also remove tool responses whose tool_call_id has no matching assistant tool_call
            if (msg.role == "tool" && msg.toolCallId != null) {
                val allToolCallIds = messages
                    .filter { it.role == "assistant" }
                    .flatMap { it.toolCalls ?: emptyList() }
                    .map { it.id }
                    .toSet()
                msg.toolCallId in allToolCallIds
            } else {
                true
            }
        }
    }

    private fun buildToolDefinitions(
        agent: Agent,
        enableTerminal: Boolean,
        workspaceFolderPath: String?
    ): List<Map<String, Any>> {
        val allTools = buildAllToolDefinitions(agent, enableTerminal, workspaceFolderPath)

        // If below threshold, send all tools (no deferred mode)
        if (allTools.size <= ToolSearchHandler.DEFERRED_THRESHOLD) {
            Log.d("AgentRepository", "Using full mode: ${allTools.size} tools for '${agent.name}'")
            return allTools
        }

        // Deferred mode: core tools + activated tools + search_tools
        val includedNames = ToolSearchHandler.CORE_TOOL_NAMES + activatedToolNames

        val filteredTools = allTools.filter { tool ->
            @Suppress("UNCHECKED_CAST")
            val funcMap = tool["function"] as? Map<String, Any>
            val name = funcMap?.get("name") as? String
            name != null && name in includedNames
        }

        val deferredTools = ToolSearchHandler.getToolDefinitionsJson() + filteredTools
        val deferredCount = allTools.size - filteredTools.size
        Log.d("AgentRepository", "Using deferred mode: ${deferredTools.size} tools sent (${deferredCount} deferred) for '${agent.name}'")
        return deferredTools
    }

    /**
     * Builds ALL tool definitions without deferred filtering. Used as the source of truth.
     */
    private fun buildAllToolDefinitions(
        agent: Agent,
        enableTerminal: Boolean,
        workspaceFolderPath: String?
    ): List<Map<String, Any>> {
        val enabledSet = if (agent.enabledTools.isBlank()) null
                         else agent.enabledTools.split(",").map { it.trim() }.toSet()

        val cacheKey = "${agent.name}|${agent.enabledTools}|$enableTerminal|${workspaceFolderPath}" +
            "|${securePreferences.hasBraveApiKey()}|${securePreferences.hasGoogleMapsApiKey()}" +
            "|${securePreferences.hasSerpApiKey()}|${securePreferences.hasObsidianVaultPath()}" +
            "|${securePreferences.hasGitHubToken()}|${securePreferences.hasNotionToken()}" +
            "|${securePreferences.hasGoogleDriveConfig()}" +
            "|${securePreferences.isFinanceEnabled()}"

        cachedAllTools?.let { if (cachedAllToolsKey == cacheKey) return it }

        val baseTools = if (enableTerminal) {
            ToolHandler.getAllToolDefinitionsJson(
                workspacePath = workspaceFolderPath,
                includeTerminal = true
            ) + DuckDuckGoSearchToolHandler.getToolDefinitionsJson()
        } else {
            FileToolHandler.getToolDefinitionsJson(workspaceFolderPath) +
            AgentSelectionToolHandler.getToolDefinitionsJson() +
            CalendarToolHandler.getToolDefinitionsJson() +
            SystemAppToolHandler.getToolDefinitionsJson() +
            DuckDuckGoSearchToolHandler.getToolDefinitionsJson()
        }

        val mcpTools = buildList {
            if (enabledSet == null || "brave_search" in enabledSet) {
                if (securePreferences.hasBraveApiKey()) addAll(BraveSearchToolHandler.getToolDefinitionsJson())
            }
            if (enabledSet == null || "google_maps" in enabledSet) {
                if (securePreferences.hasGoogleMapsApiKey()) addAll(GoogleMapsToolHandler.getToolDefinitionsJson())
            }
            if (enabledSet == null || "serpapi" in enabledSet) {
                if (securePreferences.hasSerpApiKey()) addAll(SerpAPIToolHandler.getToolDefinitionsJson())
            }
            if (enabledSet == null || "canva" in enabledSet) {
                if (securePreferences.hasCanvaAccessToken()) addAll(CanvaToolHandler.getToolDefinitionsJson())
            }
            val isHealthAgent = agent.name.equals("Health Advisor", ignoreCase = true)
            if (isHealthAgent || enabledSet == null || "pubmed" in enabledSet) {
                if (isHealthAgent || securePreferences.isPubMedEnabled()) addAll(PubMedToolHandler.getToolDefinitionsJson())
            }
            if (enabledSet == null || "obsidian" in enabledSet) {
                if (securePreferences.hasObsidianVaultPath()) addAll(ObsidianToolHandler.getToolDefinitionsJson())
            }
            if (enabledSet == null || "github" in enabledSet) {
                if (securePreferences.hasGitHubToken()) addAll(GitHubToolHandler.getToolDefinitionsJson())
            }
            if (enabledSet == null || "notion" in enabledSet) {
                if (securePreferences.hasNotionToken()) addAll(NotionToolHandler.getToolDefinitionsJson())
            }
            if (enabledSet == null || "slack" in enabledSet) {
                if (securePreferences.hasSlackToken()) addAll(SlackToolHandler.getToolDefinitionsJson())
            }
            if (enabledSet == null || "gdrive" in enabledSet) {
                if (securePreferences.hasGoogleDriveConfig()) addAll(GoogleDriveToolHandler.getToolDefinitionsJson())
            }
            // Google Workspace tools always available when authenticated
            if (securePreferences.hasGoogleWorkspaceConfig() || securePreferences.hasGoogleDriveConfig()) {
                addAll(GoogleWorkspaceToolHandler.getToolDefinitionsJson())
            }
            // Finance (local, toggle-gated)
            if (enabledSet == null || "finance" in enabledSet) {
                if (securePreferences.isFinanceEnabled()) addAll(FinanceToolHandler.getToolDefinitionsJson())
            }
            // Always available tools (no config needed)
            addAll(AgentCreatorToolHandler.getToolDefinitionsJson())
            addAll(LocationToolHandler.getToolDefinitionsJson())
            addAll(ReminderToolHandler.getToolDefinitionsJson())
            addAll(MemoryToolHandler.getToolDefinitionsJson())
            addAll(CodeExecutionHandler.getToolDefinitionsJson())
            addAll(PresentationToolHandler.getToolDefinitionsJson())
            addAll(SubtaskToolHandler.getToolDefinitionsJson())
            addAll(AppControlToolHandler.getToolDefinitionsJson())
            addAll(TodoToolHandler.getToolDefinitionsJson())
            addAll(ScheduledTaskToolHandler.getToolDefinitionsJson())
            // Delegation tool — only for orchestrator agents (Cortex)
            if (agent.role == "Agent Orchestrator" || agent.name == "Cortex") {
                addAll(DelegationToolHandler.getToolDefinitionsJson())
            }
        }

        val tools = baseTools + mcpTools
        cachedAllTools = tools
        cachedAllToolsKey = cacheKey
        Log.d("AgentRepository", "Built ${tools.size} total tools for agent '${agent.name}'")
        return tools
    }

    fun getConfiguredMcpTools(): List<Pair<String, String>> {
        return buildList {
            if (securePreferences.hasBraveApiKey()) add("brave_search" to "Brave Search")
            if (securePreferences.hasGoogleMapsApiKey()) add("google_maps" to "Google Maps")
            if (securePreferences.hasSerpApiKey()) add("serpapi" to "SerpAPI")
            if (securePreferences.hasCanvaAccessToken()) add("canva" to "Canva")
            if (securePreferences.isPubMedEnabled()) add("pubmed" to "PubMed")
            if (securePreferences.isFinanceEnabled()) add("finance" to "Finanzas Personales")
            add("location" to "Ubicacion GPS")
            if (securePreferences.hasObsidianVaultPath()) add("obsidian" to "Obsidian Vault")
            if (securePreferences.hasGitHubToken()) add("github" to "GitHub")
            if (securePreferences.hasNotionToken()) add("notion" to "Notion")
            if (securePreferences.hasSlackToken()) add("slack" to "Slack")
            if (securePreferences.hasGoogleDriveConfig()) add("gdrive" to "Google Drive")
            add("reminders" to "Recordatorios")
            add("presentations" to "Presentaciones PPTX")
        }
    }

    /**
     * Builds a compact capabilities summary for injection into Cortex's system prompt.
     * Only includes what's actually configured — no wasted tokens on unavailable tools.
     * ~150-200 tokens total.
     */
    fun buildCapabilitiesSummary(enableTerminal: Boolean): String = buildString {
        appendLine("## ENVIRONMENT")
        appendLine("You run on an Android device. You have direct control over hardware, apps, files, and external services through your tools.")
        appendLine("When you need a tool not in your current list, call search_tools to discover it.")
        appendLine()
        appendLine("## CAPABILITIES")
        // Core — always available
        if (enableTerminal) append("Shell: execute_command | ")
        appendLine("Files: read/write/list | Search: duckduckgo_search | Code: run_code, preview_web")
        appendLine("Device: device_control (apps, camera, volume, brightness, flashlight, Spotify, settings)")
        appendLine("Calendar: read/add events | Reminders: set/list/cancel | Location: GPS")
        appendLine("Memory: search/save/update/delete/list/link | Agents: select, create, subtask")
        appendLine("Planning: todo_write/todo_read (show progress to user for complex tasks)")
        appendLine("Scheduling: schedule_task (cron jobs — execute agent prompts at specific times)")
        appendLine("App: app_control (change model, toggle services, configure agents, display settings)")
        appendLine("Academic: wikipedia, arxiv | Weather: current, forecast, air quality")
        appendLine("Images: generate (DALL-E, Google Imagen) | Presentations: PPTX builder")
        // Dynamic — only show if configured
        val extras = buildList {
            if (securePreferences.hasBraveApiKey()) add("Brave Search")
            if (securePreferences.hasSerpApiKey()) add("SerpAPI (Google, Flights, Hotels, YouTube)")
            if (securePreferences.hasGoogleMapsApiKey()) add("Google Maps (places, directions, distance)")
            if (securePreferences.hasGitHubToken()) add("GitHub (repos, issues, PRs, actions, releases)")
            if (securePreferences.hasNotionToken()) add("Notion (pages, databases)")
            if (securePreferences.hasSlackToken()) add("Slack (channels, messages, threads)")
            if (securePreferences.hasGoogleDriveConfig() || securePreferences.hasGoogleWorkspaceConfig()) add("Google Workspace (Gmail, Drive, Calendar, Sheets, Docs, Slides)")
            if (securePreferences.hasObsidianVaultPath()) add("Obsidian (notes, search)")
            if (securePreferences.hasCanvaAccessToken()) add("Canva (designs, templates, export)")
            if (securePreferences.isFinanceEnabled()) add("Finance (transactions, balances)")
            if (securePreferences.isPubMedEnabled()) add("PubMed (medical research)")
        }
        if (extras.isNotEmpty()) {
            appendLine("Services: ${extras.joinToString(" | ")}")
        }
    }

    fun isCommandBlocked(command: String): Boolean {
        return shellExecutor.isCommandBlocked(command)
    }
    
    fun getCommandRiskLevel(command: String) = shellExecutor.getCommandRiskLevel(command)

    suspend fun getAgentByName(name: String): Agent? {
        return agentDao.getAgentByName(name)?.toDomain()
    }

    suspend fun getOrchestratorAgent(): Agent? {
        return agentDao.getAgentByRole("Agent Orchestrator")?.toDomain()
    }

    suspend fun getAllAgentsOnce(): List<Agent> {
        return agentDao.getAllAgentsOnce().map { it.toDomain() }
    }

    // Preferencias de mostrar razonamiento
    fun setShowReasoning(enabled: Boolean) {
        securePreferences.setShowReasoning(enabled)
    }

    fun getShowReasoning(): Boolean {
        return securePreferences.getShowReasoning()
    }

    // Preferencias de mostrar comandos ejecutados
    fun setShowCommands(enabled: Boolean) {
        securePreferences.setShowCommands(enabled)
    }

    fun getShowCommands(): Boolean {
        return securePreferences.getShowCommands()
    }

    // ── Moonshot multi-endpoint support ────────────────────────────────────
    fun saveMoonshotApiKey(endpointType: com.aiagents.app.domain.model.MoonshotEndpointType, apiKey: String) {
        securePreferences.saveMoonshotApiKey(endpointType, apiKey)
    }

    fun getMoonshotApiKey(endpointType: com.aiagents.app.domain.model.MoonshotEndpointType): String? {
        return securePreferences.getMoonshotApiKey(endpointType)
    }

    fun hasMoonshotApiKey(endpointType: com.aiagents.app.domain.model.MoonshotEndpointType): Boolean {
        return securePreferences.hasMoonshotApiKey(endpointType)
    }

    fun isAnyMoonshotEndpointConfigured(): Boolean {
        return com.aiagents.app.domain.model.MoonshotEndpointType.entries.any { hasMoonshotApiKey(it) }
    }

    fun getActiveMoonshotEndpoint(): com.aiagents.app.domain.model.MoonshotEndpointType {
        return securePreferences.getActiveMoonshotEndpoint()
    }

    fun setActiveMoonshotEndpoint(endpointType: com.aiagents.app.domain.model.MoonshotEndpointType) {
        securePreferences.setActiveMoonshotEndpoint(endpointType)
    }

    fun getActiveMoonshotApiKey(): String? {
        return getMoonshotApiKey(getActiveMoonshotEndpoint())
    }

    /**
     * Returns the best available Moonshot (apiKey, baseUrl) pair.
     * Tries the saved active endpoint first; if its key is missing or blank,
     * falls back to any other endpoint that has a key configured.
     * Returns null if no endpoint has a key.
     */
    fun getResolvedMoonshotPair(): Pair<String, String>? {
        val active = getActiveMoonshotEndpoint()
        val activeKey = getMoonshotApiKey(active)?.takeIf { it.isNotBlank() }
        if (activeKey != null) return activeKey to active.baseUrl

        // Fallback: try all other endpoints
        for (endpoint in com.aiagents.app.domain.model.MoonshotEndpointType.entries) {
            if (endpoint == active) continue
            val key = getMoonshotApiKey(endpoint)?.takeIf { it.isNotBlank() } ?: continue
            Log.w("AgentRepository", "Active Moonshot endpoint $active has no key, falling back to $endpoint")
            securePreferences.setActiveMoonshotEndpoint(endpoint) // update active for next time
            return key to endpoint.baseUrl
        }
        return null
    }

    fun getActiveMoonshotBaseUrl(): String {
        return getActiveMoonshotEndpoint().baseUrl
    }

    // ── Auto-creación de agentes via Agent Architect ────────────────────────
    suspend fun autoCreateAgent(description: String): Result<String> {
        // 1. Get the Agent Architect's system prompt
        val architect = agentDao.getAgentByName("Agent Architect")
            ?: return Result.failure(Exception("Agent Architect no encontrado en la base de datos"))

        // 2. Get a configured provider + model
        val selectedModels = securePreferences.getSelectedModels()
        if (selectedModels.isEmpty()) {
            return Result.failure(Exception("No hay ningún modelo seleccionado. Configura un proveedor y selecciona un modelo primero."))
        }

        val modelKey = selectedModels.first() // format: "PROVIDER|modelId"
        val parts = modelKey.split("|", limit = 2)
        if (parts.size != 2) {
            return Result.failure(Exception("Formato de modelo inválido"))
        }
        val providerType = try {
            ProviderType.valueOf(parts[0])
        } catch (_: Exception) {
            return Result.failure(Exception("Proveedor desconocido: ${parts[0]}"))
        }
        val modelId = parts[1]

        // 3. Build API key / base URL
        val (apiKey, baseUrl) = when {
            providerType == ProviderType.LOCAL || providerType == ProviderType.OLLAMA -> "" to null
            providerType == ProviderType.MOONSHOT -> {
                getResolvedMoonshotPair()
                    ?: return Result.failure(Exception("API Key no configurada para Moonshot"))
            }
            else -> {
                val key = securePreferences.getApiKey(providerType)
                    ?: return Result.failure(Exception("API Key no configurada para $providerType"))
                key to securePreferences.getBaseUrl(providerType)
            }
        }

        val client = aiClientFactory.createClient(providerType, apiKey, baseUrl)

        // 4. Call the AI with the Agent Architect's prompt and create_agent tool
        val tools = AgentCreatorToolHandler.getToolDefinitionsJson()
        val enhancedPrompt = architect.systemPrompt + "\n\n" +
            "## CRITICAL INSTRUCTION\n" +
            "You have access to the `create_agent` function tool. You MUST call it to create the agent. " +
            "Do NOT just describe the agent — you MUST invoke the create_agent tool with all required parameters."
        val messages = listOf(
            ChatMessage(role = "user", content = "Crea un agente basado en esta descripción del usuario: $description\n\nIMPORTANT: Use the create_agent tool to create it.")
        )

        val response = client.chatWithTools(
            model = modelId,
            messages = messages,
            systemPrompt = enhancedPrompt,
            temperature = architect.temperature,
            maxTokens = architect.maxTokens,
            tools = tools
        ).getOrElse { return Result.failure(it) }

        // 5. Process tool calls
        val toolCalls = response.toolCalls
        if (toolCalls.isNullOrEmpty()) {
            return Result.success(response.content ?: "El agente fue diseñado pero no se ejecutó la herramienta de creación.")
        }

        val results = mutableListOf<String>()
        for (tc in toolCalls) {
            if (tc.function.name in AgentCreatorToolHandler.ALL_TOOL_NAMES) {
                val result = agentCreatorToolHandler.executeTool(tc.id, tc.function.name, tc.function.arguments)
                results.add(result.content)
                if (!result.success) {
                    return Result.failure(Exception(result.content))
                }
            }
        }

        return Result.success(results.joinToString("\n"))
    }
}
