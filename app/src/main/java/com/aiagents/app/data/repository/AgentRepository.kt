package com.aiagents.app.data.repository

import android.util.Log
import com.aiagents.app.data.auth.OpenAIEndpointPolicy
import com.aiagents.app.data.auth.ProviderCredentialResolver
import com.aiagents.app.data.local.AgentDao
import com.aiagents.app.data.local.CommandPermissionDao
import com.aiagents.app.data.local.ConversationDao
import com.aiagents.app.data.local.FileDao
import com.aiagents.app.data.local.MessageDao
import com.aiagents.app.data.local.ProviderModelCatalogCache
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
import com.aiagents.app.data.remote.RemoteModelInfo
import com.aiagents.app.data.remote.flattenToolHistoryForCompatibility
import com.aiagents.app.data.remote.isHttp400
import com.aiagents.app.data.runtime.RuntimeContextProvider
import com.aiagents.app.data.skills.SkillReviewScheduler
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
import com.aiagents.app.data.terminal.CortexMemoryToolHandler
import com.aiagents.app.data.terminal.AcademicSearchToolHandler
import com.aiagents.app.data.terminal.WeatherToolHandler
import com.aiagents.app.data.terminal.ImageGenerationToolHandler
import com.aiagents.app.data.auth.GoogleWorkspaceOAuthManager
import com.aiagents.app.data.terminal.CalendarToolHandler
import com.aiagents.app.data.terminal.FileToolHandler
import com.aiagents.app.data.terminal.SystemAppToolHandler
import com.aiagents.app.data.terminal.ShellExecutor
import com.aiagents.app.data.terminal.ToolHandler
import com.aiagents.app.data.terminal.AppControlToolHandler
import com.aiagents.app.data.terminal.AssistantIdentityToolHandler
import com.aiagents.app.data.terminal.ScheduledTaskToolHandler
import com.aiagents.app.data.terminal.TodoToolHandler
import com.aiagents.app.data.terminal.DelegationToolHandler
import com.aiagents.app.data.terminal.ToolSearchHandler
import com.aiagents.app.data.terminal.UnifiedWebToolHandler
import com.aiagents.app.data.terminal.SkillToolHandler
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.AgentRoles
import com.aiagents.app.domain.model.isOrchestrator
import com.aiagents.app.domain.model.AgentFile
import com.aiagents.app.domain.model.Conversation
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.OpenCodeVariantType
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolResult
import com.aiagents.app.domain.model.WebSearchProvider
import com.aiagents.app.domain.model.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.Gson
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
    private val providerModelCatalogCache: ProviderModelCatalogCache,
    private val providerCredentialResolver: ProviderCredentialResolver,
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
    private val googleWorkspaceToolHandler: GoogleWorkspaceToolHandler,
    private val googleWorkspaceOAuthManager: GoogleWorkspaceOAuthManager,
    private val reminderToolHandler: ReminderToolHandler,
    private val memoryToolHandler: MemoryToolHandler,
    private val cortexMemoryToolHandler: CortexMemoryToolHandler,
    private val presentationToolHandler: PresentationToolHandler,
    private val financeToolHandler: FinanceToolHandler,
    private val toolSearchHandler: ToolSearchHandler,
    private val unifiedWebToolHandler: UnifiedWebToolHandler,
    private val skillToolHandler: SkillToolHandler,
    private val academicSearchToolHandler: AcademicSearchToolHandler,
    private val weatherToolHandler: WeatherToolHandler,
    private val imageGenerationToolHandler: ImageGenerationToolHandler,
    private val appControlToolHandler: AppControlToolHandler,
    private val assistantIdentityToolHandler: AssistantIdentityToolHandler,
    private val todoToolHandler: TodoToolHandler,
    private val scheduledTaskToolHandler: ScheduledTaskToolHandler,
    private val runtimeContextProvider: RuntimeContextProvider,
    private val skillReviewScheduler: SkillReviewScheduler,
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
        return fetchAvailableModelInfos(providerType)
            .getOrDefault(emptyList())
            .map { it.id }
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
        val id = messageDao.insertMessage(MessageEntity.fromDomain(message, workspaceId, agentId))
        scheduleSelfImprovementIfEligible(workspaceId, null, message, agentId)
        return id
    }

    suspend fun addMessage(workspaceId: Long, conversationId: Long?, message: Message, agentId: Long? = null): Long {
        val id = messageDao.insertMessage(MessageEntity.fromDomain(message, workspaceId, agentId, conversationId))
        scheduleSelfImprovementIfEligible(workspaceId, conversationId, message, agentId)
        return id
    }

    private suspend fun scheduleSelfImprovementIfEligible(
        workspaceId: Long,
        conversationId: Long?,
        message: Message,
        agentId: Long?
    ) {
        if (message.role != com.aiagents.app.domain.model.MessageRole.ASSISTANT) return
        // Assistant turns with tool calls are intermediate iterations, not the response boundary.
        if (message.toolCalls.isNotEmpty()) return
        // Delegated sub-conversation prompts are synthetic and must not count as user messages.
        if (conversationId != null && conversationDao.getConversationById(conversationId)?.parentConversationId != null) return
        runCatching {
            val workspace = workspaceDao.getWorkspaceById(workspaceId) ?: return@runCatching
            val respondingAgentId = agentId ?: workspace.activeAgentId ?: return@runCatching
            val respondingAgent = agentDao.getAgentById(respondingAgentId)?.toDomain() ?: return@runCatching
            if (!respondingAgent.isOrchestrator) return@runCatching
            val recent = if (conversationId != null) {
                messageDao.getRecentConversationMessages(conversationId, 80)
            } else {
                messageDao.getRecentConversationMessagesForWorkspace(workspaceId, 80)
            }.asReversed().map { it.toDomain() }
            skillReviewScheduler.recordCompletedTurn(
                scopeId = workspaceId,
                recentTranscript = recent,
                modelKey = workspace.selectedModel
            )
        }.onFailure {
            Log.w("AgentRepository", "Could not schedule background self-improvement", it)
        }
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

    suspend fun saveContextCheckpoint(
        workspaceId: Long,
        conversationId: Long?,
        summary: Message,
        agentId: Long?
    ) {
        messageDao.saveContextCheckpoint(
            workspaceId = workspaceId,
            conversationId = conversationId,
            checkpointPrefix = ContextCompactionPolicy.CHECKPOINT_PREFIX,
            checkpoint = MessageEntity.fromDomain(summary, workspaceId, agentId, conversationId)
        )
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
        overrideProvider: ProviderType? = null,
        memorySessionKey: String? = null
    ): Result<String> {
        runtimeContextProvider.refreshIdentityFromMemory()
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

        val credentials = providerCredentialResolver.resolve(activeProvider)
            ?: return Result.failure(Exception(missingCredentialsMessage(activeProvider)))
        val (apiKey, baseUrl) = credentials.apiKey to credentials.baseUrl

        val client = aiClientFactory.createClient(activeProvider, apiKey, baseUrl)
        Log.d("AgentRepository", "Client created: ${client::class.simpleName}")
        
        val contextMessages = ContextCompactionPolicy.modelHistory(messages)
        val chatMessages = contextMessages.map { msg ->
            ChatMessage(
                role = msg.role.name.lowercase(),
                content = msg.content
            )
        }
        
        Log.d("AgentRepository", "Sending ${chatMessages.size} messages to API")
        return client.chat(
            model = modelToUse,
            messages = chatMessages,
            systemPrompt = runtimeContextProvider.enrich(
                basePrompt = agent.systemPrompt,
                agentName = agent.name,
                agentRole = agent.role,
                memorySessionKey = memorySessionKey ?: deriveMemorySessionKey(agent, messages)
            ),
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
        runtimeContextProvider.refreshIdentityFromMemory()
        val activeProvider = provider ?: getActiveProvider() ?: getFirstConfiguredProvider()
            ?: return Result.failure(Exception("No provider configured"))
        val credentials = providerCredentialResolver.resolve(activeProvider)
            ?: return Result.failure(Exception(missingCredentialsMessage(activeProvider)))
        val (apiKey, baseUrl) = credentials.apiKey to credentials.baseUrl
        val client = aiClientFactory.createClient(activeProvider, apiKey, baseUrl)
        return client.chat(
            model,
            messages,
            runtimeContextProvider.enrich(
                basePrompt = systemPrompt,
                agentName = "Internal agent",
                agentRole = "Internal operation"
            ),
            temperature,
            maxTokens
        )
    }

    suspend fun getAvailableModels(providerType: ProviderType): Result<List<String>> {
        return fetchAvailableModelInfos(providerType).map { models -> models.map { it.id } }
    }

    private suspend fun fetchAvailableModelInfos(providerType: ProviderType): Result<List<RemoteModelInfo>> {
        if (providerType == ProviderType.LOCAL) {
            val models = localModelRepository?.getDownloadedModels().orEmpty().map { model ->
                RemoteModelInfo(model.id, model.contextLength)
            }
            return Result.success(models)
        }
        val credentials = providerCredentialResolver.resolve(providerType)
            ?: return Result.success(emptyList())
        val contextMetadataScope = providerContextMetadataScope(providerType)
        val client = aiClientFactory.createClient(
            providerType,
            credentials.apiKey,
            credentials.baseUrl
        )
        return client.getAvailableModelInfos().onSuccess { models ->
            providerModelCatalogCache.writeContextWindows(
                providerType,
                models.mapNotNull { model ->
                    model.contextWindow?.let { contextWindow -> model.id to contextWindow }
                }.toMap(),
                scope = contextMetadataScope
            )
        }
    }

    private fun providerContextMetadataScope(providerType: ProviderType): String? = when (providerType) {
        ProviderType.OPENCODE -> getActiveOpenCodeVariant().name
        ProviderType.MOONSHOT -> getActiveMoonshotEndpoint().name
        ProviderType.ZAI -> getActiveZAIPlan().name
        else -> null
    }

    fun getContextWindowForModel(modelKey: String): Int {
        val parts = modelKey.split('|', limit = 2)
        val provider = parts.firstOrNull()
            ?.let { runCatching { ProviderType.valueOf(it) }.getOrNull() }
        val modelId = parts.getOrElse(1) { modelKey }
        val reportedContextWindow = when (provider) {
            ProviderType.LOCAL -> localModelRepository
                ?.getAvailableModels()
                ?.firstOrNull { it.id == modelId }
                ?.contextLength
            null -> null
            else -> providerModelCatalogCache.readContextWindow(
                provider,
                modelId,
                scope = providerContextMetadataScope(provider)
            )
        }
        return TokenCounter.getContextWindowForModel(modelId, provider, reportedContextWindow)
    }

    suspend fun refreshContextWindowForModel(modelKey: String): Int {
        val provider = modelKey.substringBefore('|', missingDelimiterValue = "")
            .let { runCatching { ProviderType.valueOf(it) }.getOrNull() }
        if (provider != null && provider != ProviderType.LOCAL) {
            fetchAvailableModelInfos(provider)
        }
        return getContextWindowForModel(modelKey)
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

    suspend fun clearSelectedModelsForProvider(provider: ProviderType) {
        securePreferences.clearSelectedModels(provider)
        workspaceDao.clearSelectedModelsForProvider("${provider.name}|")
    }

    fun getCredentialScope(provider: ProviderType, credential: String, destination: String): String =
        securePreferences.credentialScope(provider, credential, destination)

    fun getStoredCredentialScope(provider: ProviderType): String? =
        providerCredentialResolver.resolve(provider)?.let { credentials ->
            securePreferences.credentialScope(
                provider,
                credentials.apiKey,
                credentials.baseUrl.orEmpty()
            )
        }

    fun saveApiKey(provider: ProviderType, apiKey: String) {
        securePreferences.saveApiKey(provider, apiKey)
    }

    fun getApiKey(provider: ProviderType): String? {
        return securePreferences.getApiKey(provider)
    }

    fun saveBaseUrl(provider: ProviderType, baseUrl: String) {
        require(provider != ProviderType.OPENAI) {
            "OpenAI usa exclusivamente la URL oficial"
        }
        securePreferences.saveBaseUrl(provider, baseUrl)
    }

    fun getBaseUrl(provider: ProviderType): String? {
        if (provider != ProviderType.OPENAI) return securePreferences.getBaseUrl(provider)
        return OpenAIEndpointPolicy.OFFICIAL_API_BASE_URL
    }

    fun hasApiKey(provider: ProviderType): Boolean {
        if (provider == ProviderType.MANAGED) {
            return securePreferences.isManagedPrivacyAccepted()
        }
        // Para proveedor LOCAL, verificar si hay modelos descargados
        if (provider == ProviderType.LOCAL) {
            return localModelRepository?.getDownloadedModels()?.isNotEmpty() == true
        }
        return providerCredentialResolver.resolve(provider) != null
    }

    fun saveOpenAIProviderApiKey(apiKey: String) = securePreferences.saveOpenAIProviderApiKey(apiKey)

    fun setActiveProvider(provider: ProviderType) {
        securePreferences.setActiveProvider(provider)
    }

    fun getActiveProvider(): ProviderType? {
        return securePreferences.getActiveProvider()
    }

    fun getFirstConfiguredProvider(): ProviderType? {
        return ProviderType.entries.find { hasApiKey(it) }
    }

    private fun missingCredentialsMessage(provider: ProviderType): String =
        "Credenciales no configuradas para $provider"

    suspend fun chatWithTools(
        agent: Agent,
        messages: List<Message>,
        overrideModel: String? = null,
        overrideProvider: ProviderType? = null,
        enableTerminal: Boolean = false,
        workspaceFolderPath: String? = null,
        allowedToolNames: Set<String>? = null,
        memorySessionKey: String? = null
    ): Result<ChatResponseWithTools> {
        runtimeContextProvider.refreshIdentityFromMemory()
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

        val credentials = providerCredentialResolver.resolve(activeProvider)
            ?: return Result.failure(Exception(missingCredentialsMessage(activeProvider)))
        val (apiKey, baseUrl) = credentials.apiKey to credentials.baseUrl

        val client = aiClientFactory.createClient(activeProvider, apiKey, baseUrl)
        
        val contextMessages = ContextCompactionPolicy.modelHistory(messages)
        val chatMessages = contextMessages.map { msg ->
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
                clientMessageId = if (msg.role == MessageRole.USER && msg.id > 0) {
                    "${msg.id}:${msg.timestamp}"
                } else null,
                toolCalls = msg.toolCalls.ifEmpty { null },
                toolCallId = toolResult?.toolCallId,
                name = toolResult?.name,
                imageDataUri = if (!isToolMessage) (if (isImageResult) toolResultContent else userImageUri) else null,
                // Imagen de resultado de tool: solo AnthropicClient la usa para el bloque tool_result
                toolResultImageUri = if (isToolMessage && isImageResult) toolResultContent else null
            )
        }
        
        val tools = buildToolDefinitions(agent, enableTerminal, workspaceFolderPath, allowedToolNames)

        val sanitized = sanitizeToolCallHistory(chatMessages)
        val runtimePrompt = buildRuntimeSystemPrompt(
            agent,
            messages,
            tools,
            enableTerminal,
            workspaceFolderPath,
            allowedToolNames,
            memorySessionKey
        )
        val requiresTextToolHistory = activeProvider == ProviderType.KILO &&
            modelToUse.startsWith("tencent/hy3", ignoreCase = true) &&
            sanitized.any { it.role == "tool" }
        val firstAttemptMessages = if (requiresTextToolHistory) {
            Log.d(
                "AgentRepository",
                "Using text-compatible tool history for Kilo model $modelToUse"
            )
            flattenToolHistoryForCompatibility(sanitized)
        } else {
            sanitized
        }

        Log.d("AgentRepository", "Sending ${firstAttemptMessages.size} messages to API with ${tools.size} tools")
        val firstAttempt = client.chatWithTools(
            model = modelToUse,
            messages = firstAttemptMessages,
            systemPrompt = runtimePrompt,
            temperature = agent.temperature,
            maxTokens = agent.maxTokens,
            tools = tools
        )

        val shouldRetryWithTextToolHistory = !requiresTextToolHistory &&
            activeProvider == ProviderType.KILO &&
            firstAttempt.exceptionOrNull()?.isHttp400() == true &&
            sanitized.any { it.role == "tool" }
        if (!shouldRetryWithTextToolHistory) return firstAttempt

        Log.w(
            "AgentRepository",
            "Kilo rejected native tool history with HTTP 400; retrying once with text-compatible history"
        )
        return client.chatWithTools(
            model = modelToUse,
            messages = flattenToolHistoryForCompatibility(sanitized),
            systemPrompt = runtimePrompt,
            temperature = agent.temperature,
            maxTokens = agent.maxTokens,
            tools = tools
        )
    }

    /**
     * Streaming version of chatWithTools. Returns a Flow of StreamingChunks.
     */
    suspend fun chatWithToolsStreaming(
        agent: Agent,
        messages: List<Message>,
        overrideModel: String? = null,
        overrideProvider: ProviderType? = null,
        enableTerminal: Boolean = false,
        workspaceFolderPath: String? = null,
        allowedToolNames: Set<String>? = null,
        memorySessionKey: String? = null
    ): Flow<StreamingChunk> {
        runtimeContextProvider.refreshIdentityFromMemory()
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

        val credentials = providerCredentialResolver.resolve(activeProvider)
            ?: return kotlinx.coroutines.flow.flow {
                emit(StreamingChunk(error = missingCredentialsMessage(activeProvider)))
            }
        val (apiKey, baseUrl) = credentials.apiKey to credentials.baseUrl

        val client = aiClientFactory.createClient(activeProvider, apiKey, baseUrl)

        val contextMessages = ContextCompactionPolicy.modelHistory(messages)
        val chatMessages = contextMessages.map { msg ->
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

        val tools = buildToolDefinitions(agent, enableTerminal, workspaceFolderPath, allowedToolNames)

        val sanitized = sanitizeToolCallHistory(chatMessages)
        Log.d("AgentRepository", "Streaming ${sanitized.size} messages to API with ${tools.size} tools")
        return client.chatWithToolsStreaming(
            model = modelToUse,
            messages = sanitized,
            systemPrompt = buildRuntimeSystemPrompt(
                agent,
                messages,
                tools,
                enableTerminal,
                workspaceFolderPath,
                allowedToolNames,
                memorySessionKey
            ),
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
        imageDataUris: List<String> = emptyList(),
        allowedToolNames: Set<String>? = null,
        memorySessionKey: String? = null
    ): Result<ChatResponseWithTools> {
        runtimeContextProvider.refreshIdentityFromMemory()
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

        val credentials = providerCredentialResolver.resolve(activeProvider)
            ?: return Result.failure(Exception(missingCredentialsMessage(activeProvider)))
        val (apiKey, baseUrl) = credentials.apiKey to credentials.baseUrl

        val client = aiClientFactory.createClient(activeProvider, apiKey, baseUrl)
        
        val contextMessages = ContextCompactionPolicy.modelHistory(messages)
        val chatMessages = contextMessages.map { msg ->
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
        
        val tools = buildToolDefinitions(agent, enableTerminal, workspaceFolderPath, allowedToolNames)

        val sanitized = sanitizeToolCallHistory(chatMessages)
        Log.d("AgentRepository", "Sending ${sanitized.size} messages with ${imageDataUris.size} images")
        return client.chatWithTools(
            model = modelToUse,
            messages = sanitized,
            systemPrompt = buildRuntimeSystemPrompt(
                agent,
                messages,
                tools,
                enableTerminal,
                workspaceFolderPath,
                allowedToolNames,
                memorySessionKey
            ),
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
    fun getGoogleWorkspaceToolHandler(): GoogleWorkspaceToolHandler = googleWorkspaceToolHandler
    fun getGoogleWorkspaceOAuthManager(): GoogleWorkspaceOAuthManager = googleWorkspaceOAuthManager
    fun getReminderToolHandler(): ReminderToolHandler = reminderToolHandler
    fun getMemoryToolHandler(): MemoryToolHandler = memoryToolHandler
    fun getCortexMemoryToolHandler(): CortexMemoryToolHandler = cortexMemoryToolHandler

    fun getToolSearchHandler(): ToolSearchHandler = toolSearchHandler
    fun getUnifiedWebToolHandler(): UnifiedWebToolHandler = unifiedWebToolHandler
    fun getSkillToolHandler(): SkillToolHandler = skillToolHandler
    fun getPresentationToolHandler(): PresentationToolHandler = presentationToolHandler
    suspend fun getValidGoogleDriveToken(): String =
        googleWorkspaceOAuthManager.getValidAccessToken().getOrNull().orEmpty()

    fun isPubMedEnabled(): Boolean = securePreferences.isPubMedEnabled()

    fun getFinanceToolHandler(): FinanceToolHandler = financeToolHandler
    fun isFinanceEnabled(): Boolean = securePreferences.isFinanceEnabled()

    fun getAcademicSearchToolHandler(): AcademicSearchToolHandler = academicSearchToolHandler
    fun getWeatherToolHandler(): WeatherToolHandler = weatherToolHandler
    fun getImageGenerationToolHandler(): ImageGenerationToolHandler = imageGenerationToolHandler
    fun getOpenAIApiKey(): String = securePreferences.getOpenAIApiKey() ?: ""
    fun getGoogleImagenApiKey(): String = securePreferences.getGoogleImagenApiKey() ?: ""
    fun isWeatherEnabled(): Boolean = securePreferences.isWeatherEnabled()
    fun isImageGenerationEnabled(): Boolean = securePreferences.isImageGenerationEnabled()
    fun getAppControlToolHandler(): AppControlToolHandler = appControlToolHandler

    fun getAssistantIdentityToolHandler(): AssistantIdentityToolHandler = assistantIdentityToolHandler
    fun getTodoToolHandler(): TodoToolHandler = todoToolHandler
    fun getScheduledTaskToolHandler(): ScheduledTaskToolHandler = scheduledTaskToolHandler

    /**
     * Builds the full tool definition list for a given agent, respecting enabledTools filter.
     * Supports deferred mode: when there are many tools (> threshold), only sends core tools
     * plus a search_tools meta-tool. The model discovers additional tools via search_tools.
     */
    private var cachedAllTools: List<Map<String, Any>>? = null
    private var cachedAllToolsKey: String? = null
    private val activatedToolNamesByScope = ConcurrentHashMap<String, MutableSet<String>>()

    /** Add tool names discovered via search_tools to the active set */
    fun activateTools(agent: Agent, workspaceFolderPath: String?, names: Set<String>) {
        val active = activatedToolNamesByScope.computeIfAbsent(
            toolActivationScope(agent, workspaceFolderPath)
        ) { ConcurrentHashMap.newKeySet() }
        active.addAll(names)
        Log.d("AgentRepository", "Activated ${names.size} tools for agent ${agent.id}")
    }

    /** Reset activated tools (call at the start of each new user message) */
    fun resetActivatedTools(agent: Agent, workspaceFolderPath: String?) {
        activatedToolNamesByScope.remove(toolActivationScope(agent, workspaceFolderPath))
    }

    private fun toolActivationScope(agent: Agent, workspaceFolderPath: String?): String =
        "${agent.id}|${workspaceFolderPath.orEmpty()}"

    /** Get the full list of all available tool names for search filtering */
    fun getAllAvailableToolNames(agent: Agent, enableTerminal: Boolean, workspaceFolderPath: String?): Set<String> {
        val allTools = buildAllToolDefinitions(agent, enableTerminal, workspaceFolderPath)
        return extractToolNames(allTools).toSet()
    }

    /** Exact system-prompt and tool-schema overhead used by the context meter. */
    fun estimateRequestOverheadTokens(
        agent: Agent,
        messages: List<Message>,
        enableTerminal: Boolean,
        workspaceFolderPath: String?
    ): Int {
        val tools = buildToolDefinitions(agent, enableTerminal, workspaceFolderPath)
        val systemPrompt = buildRuntimeSystemPrompt(
            agent = agent,
            messages = messages,
            exposedTools = tools,
            enableTerminal = enableTerminal,
            workspaceFolderPath = workspaceFolderPath
        )
        return TokenCounter.estimateTokens(systemPrompt) +
            TokenCounter.estimateTokens(Gson().toJson(tools))
    }

    private fun buildRuntimeSystemPrompt(
        agent: Agent,
        messages: List<Message>,
        exposedTools: List<Map<String, Any>>,
        enableTerminal: Boolean,
        workspaceFolderPath: String?,
        allowedToolNames: Set<String>? = null,
        memorySessionKey: String? = null
    ): String = runtimeContextProvider.enrich(
        basePrompt = agent.systemPrompt,
        agentName = agent.name,
        agentRole = agent.role,
        exposedToolNames = extractToolNames(exposedTools),
        allAvailableToolNames = getAllAvailableToolNames(agent, enableTerminal, workspaceFolderPath)
            .let { names -> allowedToolNames?.let(names::intersect) ?: names },
        memorySessionKey = memorySessionKey ?: deriveMemorySessionKey(agent, messages)
    )

    /** Stable for every continuation/tool round in one conversation, without storing content. */
    private fun deriveMemorySessionKey(agent: Agent, messages: List<Message>): String? {
        if (!agent.isOrchestrator) return null
        val firstMessage = messages.firstOrNull() ?: return null
        return "${agent.id}:${firstMessage.timestamp}:${firstMessage.role.name}"
    }

    private fun extractToolNames(tools: List<Map<String, Any>>): List<String> = tools.mapNotNull { tool ->
        @Suppress("UNCHECKED_CAST")
        val function = tool["function"] as? Map<String, Any>
        function?.get("name") as? String
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
        workspaceFolderPath: String?,
        allowedToolNames: Set<String>? = null
    ): List<Map<String, Any>> {
        val definitions = buildList {
            addAll(buildAllToolDefinitions(agent, enableTerminal, workspaceFolderPath))
            // A child explicitly created with role=orchestrator may spawn bounded children even
            // when its specialized Agent record is not Cortex itself.
            if (allowedToolNames?.contains(DelegationToolHandler.TOOL_NAME) == true &&
                none { extractToolNames(listOf(it)).firstOrNull() == DelegationToolHandler.TOOL_NAME }
            ) {
                addAll(DelegationToolHandler.getToolDefinitionsJson())
            }
        }
        val allTools = definitions.let { availableDefinitions ->
            if (allowedToolNames == null) availableDefinitions
            else availableDefinitions.filter { definition ->
                extractToolNames(listOf(definition)).firstOrNull() in allowedToolNames
            }
        }

        // Restricted execution profiles are intentionally small and have no search_tools indirection.
        if (allowedToolNames != null) return allTools

        // If below threshold, send all tools (no deferred mode)
        if (allTools.size <= ToolSearchHandler.DEFERRED_THRESHOLD) {
            Log.d("AgentRepository", "Using full mode: ${allTools.size} tools for '${agent.name}'")
            return allTools
        }

        // Deferred mode: core tools + activated tools + search_tools
        val includedNames = ToolSearchHandler.CORE_TOOL_NAMES +
            activatedToolNamesByScope[toolActivationScope(agent, workspaceFolderPath)].orEmpty()

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
        val webSearchProvider = unifiedWebToolHandler.selectedProvider()

        val cacheKey = "${agent.id}|${agent.name}|${agent.role}|${agent.enabledTools}|$enableTerminal|${workspaceFolderPath}" +
            "|${securePreferences.hasBraveApiKey()}|${securePreferences.hasGoogleMapsApiKey()}" +
            "|${securePreferences.hasSerpApiKey()}|${securePreferences.hasObsidianVaultPath()}" +
            "|${securePreferences.hasCanvaAccessToken()}|${securePreferences.isPubMedEnabled()}" +
            "|${securePreferences.hasGitHubToken()}|${securePreferences.hasNotionToken()}|${securePreferences.hasSlackToken()}" +
            "|${securePreferences.hasGoogleWorkspaceConfig()}" +
            "|${securePreferences.isFinanceEnabled()}|${securePreferences.isWeatherEnabled()}" +
            "|${securePreferences.isImageGenerationEnabled()}" +
            "|web=${webSearchProvider.name}:${unifiedWebToolHandler.isSearchConfigured()}"

        cachedAllTools?.let { if (cachedAllToolsKey == cacheKey) return it }

        val baseTools = buildList {
            if (enableTerminal) addAll(ToolHandler.getToolDefinitionsJson(workspaceFolderPath))
            addAll(
                FileToolHandler.getToolDefinitionsJson(workspaceFolderPath) +
            AgentSelectionToolHandler.getToolDefinitionsJson() +
            CalendarToolHandler.getToolDefinitionsJson() +
            SystemAppToolHandler.getToolDefinitionsJson()
            )
            UnifiedWebToolHandler.getToolDefinitionsJson().forEach { definition ->
                val name = extractToolNames(listOf(definition)).firstOrNull() ?: return@forEach
                val allowed = enabledSet == null || "web" in enabledSet || name in enabledSet ||
                    (name == UnifiedWebToolHandler.TOOL_SEARCH && "serpapi" in enabledSet)
                val configured = name != UnifiedWebToolHandler.TOOL_SEARCH ||
                    unifiedWebToolHandler.isSearchConfigured()
                if (allowed && configured) add(definition)
            }
        }

        val mcpTools = buildList {
            if (webSearchProvider == WebSearchProvider.BRAVE &&
                (enabledSet == null || "web" in enabledSet || "brave_search" in enabledSet)
            ) {
                if (securePreferences.hasBraveApiKey()) addAll(BraveSearchToolHandler.getToolDefinitionsJson())
            }
            if (enabledSet == null || "google_maps" in enabledSet) {
                if (securePreferences.hasGoogleMapsApiKey()) addAll(GoogleMapsToolHandler.getToolDefinitionsJson())
            }
            if (webSearchProvider == WebSearchProvider.SERPAPI &&
                (enabledSet == null || "web" in enabledSet || "serpapi" in enabledSet)
            ) {
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
                if (securePreferences.hasGoogleWorkspaceConfig()) addAll(GoogleDriveToolHandler.getToolDefinitionsJson())
            }
            // Google Workspace tools always available when authenticated
            if (securePreferences.hasGoogleWorkspaceConfig()) {
                addAll(GoogleWorkspaceToolHandler.getToolDefinitionsJson())
            }
            // Finance (local, toggle-gated)
            if (enabledSet == null || "finance" in enabledSet) {
                if (securePreferences.isFinanceEnabled()) addAll(FinanceToolHandler.getToolDefinitionsJson())
            }
            if (enabledSet == null || "academic" in enabledSet) {
                addAll(AcademicSearchToolHandler.getToolDefinitionsJson())
            }
            if ((enabledSet == null || "weather" in enabledSet) && securePreferences.isWeatherEnabled()) {
                addAll(WeatherToolHandler.getToolDefinitionsJson())
            }
            if ((enabledSet == null || "image_generation" in enabledSet) && securePreferences.isImageGenerationEnabled()) {
                addAll(ImageGenerationToolHandler.getToolDefinitionsJson())
            }
            // Always available tools (no config needed)
            addAll(AgentCreatorToolHandler.getToolDefinitionsJson())
            addAll(LocationToolHandler.getToolDefinitionsJson())
            addAll(ReminderToolHandler.getToolDefinitionsJson())
            // Room is the searchable secondary tier. The orchestrator's single memory tool curates
            // both active Markdown and lower-priority/demoted SQLite entries.
            addAll(MemoryToolHandler.getReadToolDefinitionsJson())
            if (agent.isOrchestrator) {
                addAll(CortexMemoryToolHandler.getToolDefinitionsJson())
            }
            addAll(CodeExecutionHandler.getToolDefinitionsJson())
            addAll(PresentationToolHandler.getToolDefinitionsJson())
            addAll(AppControlToolHandler.getToolDefinitionsJson())
            if (agent.isOrchestrator) {
                addAll(AssistantIdentityToolHandler.getToolDefinitionsJson())
            }
            addAll(TodoToolHandler.getToolDefinitionsJson())
            addAll(ScheduledTaskToolHandler.getToolDefinitionsJson())
            addAll(SkillToolHandler.getToolDefinitionsJson())
            // Delegation tool — only for the stable orchestrator role (its name is customizable).
            if (agent.isOrchestrator) {
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
            if (securePreferences.hasGoogleWorkspaceConfig()) add("gdrive" to "Google Workspace")
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
        appendLine("You run inside a native Android app.")
        appendLine("The app adds an authoritative RUNTIME_CONTEXT to every model request with the current date/time, user identity, device and exact available tools.")
        appendLine("Never claim a capability that is absent from that runtime list.")
        if (enableTerminal) appendLine("This agent is permitted to request terminal execution; commands still pass through the permission manager.")
        append("Use search_tools when RUNTIME_CONTEXT says an available tool is deferred.")
    }

    fun isCommandBlocked(command: String): Boolean {
        return shellExecutor.isCommandBlocked(command)
    }
    
    fun getCommandRiskLevel(command: String) = shellExecutor.getCommandRiskLevel(command)

    suspend fun getAgentByName(name: String): Agent? {
        return agentDao.getAgentByName(name)?.toDomain()
    }

    suspend fun getOrchestratorAgent(): Agent? {
        return agentDao.getAgentByRole(AgentRoles.ORCHESTRATOR)?.toDomain()
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

    // ── Z.AI multi-plan support ────────────────────────────────────────────
    fun saveZAIApiKey(planType: com.aiagents.app.domain.model.ZAIPlanType, apiKey: String) {
        securePreferences.saveZAIApiKey(planType, apiKey)
    }

    fun getZAIApiKey(planType: com.aiagents.app.domain.model.ZAIPlanType): String? {
        return securePreferences.getZAIApiKey(planType)
    }

    fun hasZAIApiKey(planType: com.aiagents.app.domain.model.ZAIPlanType): Boolean {
        return securePreferences.hasZAIApiKey(planType)
    }

    fun isAnyZAIPlanConfigured(): Boolean {
        return com.aiagents.app.domain.model.ZAIPlanType.entries.any { hasZAIApiKey(it) }
    }

    fun getActiveZAIPlan(): com.aiagents.app.domain.model.ZAIPlanType {
        return securePreferences.getActiveZAIPlan()
    }

    fun setActiveZAIPlan(planType: com.aiagents.app.domain.model.ZAIPlanType) {
        securePreferences.setActiveZAIPlan(planType)
    }

    fun getActiveZAIApiKey(): String? {
        return getZAIApiKey(getActiveZAIPlan())
    }

    /**
     * Returns the best available Z.AI (apiKey, baseUrl) pair.
     * Tries the saved active plan first; if its key is missing or blank,
     * falls back to any other plan that has a key configured.
     * Returns null if no plan has a key.
     */
    fun getResolvedZAIPair(): Pair<String, String>? {
        val active = getActiveZAIPlan()
        val activeKey = getZAIApiKey(active)?.takeIf { it.isNotBlank() }
        if (activeKey != null) return activeKey to active.baseUrl

        // Fallback: try all other plans
        for (plan in com.aiagents.app.domain.model.ZAIPlanType.entries) {
            if (plan == active) continue
            val key = getZAIApiKey(plan)?.takeIf { it.isNotBlank() } ?: continue
            Log.w("AgentRepository", "Active Z.AI plan $active has no key, falling back to $plan")
            securePreferences.setActiveZAIPlan(plan) // update active for next time
            return key to plan.baseUrl
        }
        return null
    }

    fun getActiveZAIBaseUrl(): String {
        return getActiveZAIPlan().baseUrl
    }

    // ── OpenCode multi-variant support ─────────────────────────────────────
    fun saveOpenCodeApiKey(variantType: com.aiagents.app.domain.model.OpenCodeVariantType, apiKey: String) {
        securePreferences.saveOpenCodeApiKey(variantType, apiKey)
    }

    fun getOpenCodeApiKey(variantType: com.aiagents.app.domain.model.OpenCodeVariantType): String? {
        return securePreferences.getOpenCodeApiKey(variantType)
    }

    fun hasOpenCodeApiKey(variantType: com.aiagents.app.domain.model.OpenCodeVariantType): Boolean {
        return securePreferences.hasOpenCodeApiKey(variantType)
    }

    fun isAnyOpenCodeVariantConfigured(): Boolean {
        return com.aiagents.app.domain.model.OpenCodeVariantType.entries.any { hasOpenCodeApiKey(it) }
    }

    fun getActiveOpenCodeVariant(): com.aiagents.app.domain.model.OpenCodeVariantType {
        return securePreferences.getActiveOpenCodeVariant()
    }

    fun setActiveOpenCodeVariant(variantType: com.aiagents.app.domain.model.OpenCodeVariantType) {
        securePreferences.setActiveOpenCodeVariant(variantType)
    }

    fun getActiveOpenCodeApiKey(): String? {
        return getOpenCodeApiKey(getActiveOpenCodeVariant())
    }

    /** Validates a key against the exact Zen/Go destination before persisting it. */
    suspend fun validateOpenCodeApiKey(
        variantType: com.aiagents.app.domain.model.OpenCodeVariantType,
        apiKey: String
    ): Result<List<String>> {
        val normalizedKey = apiKey.trim()
        if (normalizedKey.isEmpty()) return Result.failure(Exception("La API key está vacía"))
        return aiClientFactory.createClient(
            ProviderType.OPENCODE,
            normalizedKey,
            variantType.baseUrl
        ).getAvailableModels().mapCatching { models ->
            models.distinct().also {
                check(it.isNotEmpty()) { "OpenCode no devolvió modelos para ${variantType.displayName}" }
            }
        }
    }

    fun getResolvedOpenCodePair(): Pair<String, String>? {
        val active = getActiveOpenCodeVariant()
        val activeKey = getOpenCodeApiKey(active)?.takeIf { it.isNotBlank() }
        return activeKey?.let { it to active.baseUrl }
    }

    fun getActiveOpenCodeBaseUrl(): String {
        return getActiveOpenCodeVariant().baseUrl
    }

    // ── Explicit agent creation with an ephemeral designer ─────────────────
    suspend fun autoCreateAgent(description: String): Result<String> {
        runtimeContextProvider.refreshIdentityFromMemory()
        if (description.isBlank()) {
            return Result.failure(Exception("Describe el agente que quieres crear"))
        }
        val cortex = getOrchestratorAgent()
            ?: return Result.failure(Exception("El asistente principal no está disponible"))

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

        // 3. Resolve the credential and its destination as one inseparable value.
        val credentials = providerCredentialResolver.resolve(providerType)
            ?: return Result.failure(Exception(missingCredentialsMessage(providerType)))
        val (apiKey, baseUrl) = credentials.apiKey to credentials.baseUrl

        val client = aiClientFactory.createClient(providerType, apiKey, baseUrl)

        // The designer exists only for this request; it is never stored in the agents table.
        val tools = AgentCreatorToolHandler.getToolDefinitionsJson().filter { definition ->
            @Suppress("UNCHECKED_CAST")
            val function = definition["function"] as? Map<String, Any>
            function?.get("name") == AgentCreatorToolHandler.TOOL_CREATE
        }
        val enhancedPrompt = runtimeContextProvider.enrich(
            basePrompt = """
                Design one persistent custom agent from the user's explicit request and call create_agent exactly once.
                Provide a concise, task-specific system prompt that tells the agent to match the user's language, states its capabilities and limits, and avoids duplicating generic runtime/tool instructions.
                Choose safe defaults and never create additional agents.
            """.trimIndent(),
            agentName = "Temporary Agent Designer",
            agentRole = "Temporary Task Worker",
            exposedToolNames = listOf("create_agent")
        )
        val messages = listOf(
            ChatMessage(
                role = "user",
                content = "Crea un único agente persistente basado en esta descripción: ${description.take(8_000)}"
            )
        )

        val response = client.chatWithTools(
            model = modelId,
            messages = messages,
            systemPrompt = enhancedPrompt,
            temperature = 0.2f,
            maxTokens = cortex.maxTokens.coerceAtMost(4_096),
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
                val result = agentCreatorToolHandler.executeTool(
                    tc.id,
                    tc.function.name,
                    tc.function.arguments,
                    allowUserRequestedCreation = true
                )
                results.add(result.content)
                if (!result.success) {
                    return Result.failure(Exception(result.content))
                }
            }
        }

        return Result.success(results.joinToString("\n"))
    }

    // ── Anthropic OAuth credentials ──────────────────────────────────────
    fun saveAnthropicClientId(id: String) = securePreferences.saveAnthropicClientId(id)
    fun getAnthropicClientId(): String? = securePreferences.getAnthropicClientId()
    fun saveAnthropicClientSecret(secret: String) = securePreferences.saveAnthropicClientSecret(secret)
    fun getAnthropicClientSecret(): String? = securePreferences.getAnthropicClientSecret()
    fun hasAnthropicOAuthConfig(): Boolean = securePreferences.hasAnthropicOAuthConfig()
}
