package com.aiagents.app.presentation.workspace_detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.data.model.PermissionLevel
import com.aiagents.app.data.events.AgentChangeEvent
import com.aiagents.app.data.events.AgentChangeKind
import com.aiagents.app.data.model.SubagentExecutionEntity
import com.aiagents.app.data.repository.ContextCompactionPolicy
import com.aiagents.app.data.terminal.CommandRiskLevel
import com.aiagents.app.data.terminal.PermissionRequest
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.AgentFile
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.Workspace
import com.aiagents.app.ui.theme.ShapeTokens
import com.aiagents.app.ui.theme.CortexColors
import com.aiagents.app.ui.theme.CortexMark
import com.aiagents.app.ui.theme.CortexTheme
import com.aiagents.app.ui.theme.cortexGlass
import java.io.File
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import com.aiagents.app.ui.components.CodeBlock
import com.aiagents.app.ui.components.MarkdownTable
import com.aiagents.app.ui.components.ContentSegment
import com.aiagents.app.ui.components.OptionsSelectionCard
import com.aiagents.app.ui.components.SegmentType
import com.aiagents.app.ui.components.WebPreviewDialog
import com.aiagents.app.ui.components.parseMessageWithCodeBlocks
import com.aiagents.app.presentation.stt.STTViewModel
import com.aiagents.app.presentation.stt.STTSettingsDialog
import com.aiagents.app.presentation.stt.STTSettingsUiState
import com.aiagents.app.presentation.stt.VoiceInputButton
import com.aiagents.app.presentation.stt.VoiceInputOverlay
// TranscriptionPreview removed — transcription is now auto-sent
import com.aiagents.app.data.model.STTMode
import com.aiagents.app.data.model.CloudSTTProvider
import com.aiagents.app.data.model.LocalModelType
import com.aiagents.app.data.model.LocalSTTEngine
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.layout.ContentScale
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkspaceDetailScreen(
    onBack: () -> Unit,
    isDrawerMode: Boolean = false,
    onOpenDrawer: (() -> Unit)? = null,
    onNewChat: (() -> Unit)? = null,
    conversationId: Long? = null,
    viewModel: WorkspaceDetailViewModel = hiltViewModel(),
    sttViewModel: STTViewModel = hiltViewModel()
) {
    val workspace by viewModel.workspace.collectAsState()
    val activeAgent by viewModel.activeAgent.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val files by viewModel.files.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val contextInfo by viewModel.contextInfo.collectAsState()
    val workingAgents by viewModel.workingAgents.collectAsState()
    val agentStatuses by viewModel.agentStatuses.collectAsState()
    val subagentExecutions by viewModel.subagentExecutions.collectAsState()
    val context = LocalContext.current

    // Sync conversationId from navigation into ViewModel
    LaunchedEffect(conversationId) {
        viewModel.setConversationId(conversationId)
    }

    // STT state
    val isSTTEnabled by sttViewModel.isSTTEnabled.collectAsState()
    val isListening by sttViewModel.isListening.collectAsState()
    val sttTranscription by sttViewModel.transcription.collectAsState()
    val pendingTranscription by sttViewModel.pendingTranscription.collectAsState()
    val isSTTProcessing by sttViewModel.isProcessing.collectAsState()
    val sttSettings by sttViewModel.currentSettings.collectAsState()
    val sttError by sttViewModel.error.collectAsState()
    val isSTTDownloading by sttViewModel.isDownloading.collectAsState()
    val sttDownloadProgress by sttViewModel.downloadProgress.collectAsState()
    val isVoskModelDownloaded by sttViewModel.isVoskModelDownloaded.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSTTSettingsDialog by remember { mutableStateOf(false) }
    var visibleAgentChange by remember { mutableStateOf<AgentChangeEvent?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.agentChangeEvents.collect { event ->
            visibleAgentChange = event
            delay(3_200)
            if (visibleAgentChange == event) visibleAgentChange = null
            delay(180)
        }
    }

    val chatFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val fileName = getFileName(context, uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val size = getFileSize(context, uri)
            viewModel.addAttachedFile(
                AttachedFile(
                    uri = uri,
                    name = fileName,
                    mimeType = mimeType,
                    size = size
                )
            )
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            viewModel.importFileToWorkspace(context, uri)
        }
    }

    // Launcher para permisos de calendario
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.onCalendarPermissionGranted()
        } else {
            viewModel.onCalendarPermissionDenied()
        }
    }

    // Launcher para permiso de cámara
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onCameraPermissionGranted() else viewModel.onCameraPermissionDenied()
    }

    // Estado y launcher para tomar foto
    var photoDestinationPath by remember { mutableStateOf<String?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.onPhotoCaptured(if (success) photoDestinationPath else null)
        photoDestinationPath = null
    }

    // Launcher para permiso de ubicacion
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val anyGranted = permissions.entries.any { it.value }
        if (anyGranted) viewModel.onLocationPermissionGranted() else viewModel.onLocationPermissionDenied()
    }

    // Launcher para permiso de microfono (STT)
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sttViewModel.startListening()
        }
    }

    // Inicializar STT al cargar workspace
    LaunchedEffect(workspace?.id) {
        workspace?.id?.let { id ->
            sttViewModel.loadSettingsForWorkspace(id)
        }
    }

    // Manejar errores de STT
    LaunchedEffect(sttError) {
        sttError?.let { error ->
            snackbarHostState.showSnackbar(error)
            sttViewModel.dismissError()
        }
    }

    // Verificar si tenemos permiso de camara
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Verificar si tenemos permisos de calendario
    fun hasCalendarPermissions(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    // Verificar si tenemos permiso de ubicacion
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Efecto para manejar solicitud de permiso de ubicacion
    LaunchedEffect(uiState.pendingLocationPermission) {
        if (uiState.pendingLocationPermission) {
            if (hasLocationPermission()) {
                viewModel.onLocationPermissionGranted()
            }
        }
    }

    // Efecto para manejar solicitud de permisos de calendario
    LaunchedEffect(uiState.pendingCalendarPermission) {
        if (uiState.pendingCalendarPermission) {
            if (hasCalendarPermissions()) {
                // Ya tenemos permisos, proceder directamente
                viewModel.onCalendarPermissionGranted()
            }
            // Si no tenemos permisos, se mostrará el diálogo
        }
    }

    // Efecto para manejar solicitud de permiso de cámara
    LaunchedEffect(uiState.pendingCameraPermission) {
        if (uiState.pendingCameraPermission) {
            if (hasCameraPermission()) {
                viewModel.onCameraPermissionGranted()
            }
            // Si no tenemos permiso, se mostrará CameraPermissionDialog
        }
    }

    // Efecto para lanzar la cámara cuando hay permiso
    LaunchedEffect(uiState.pendingCameraCapture) {
        if (uiState.pendingCameraCapture) {
            val toolCall = uiState.pendingCameraToolCall
            val filenameArg = try {
                if (toolCall != null) {
                    val json = JsonParser.parseString(toolCall.function.arguments).asJsonObject
                    json.get("filename")?.asString
                } else null
            } catch (e: Exception) { null }
            val filename = filenameArg ?: "photo_${System.currentTimeMillis()}"
            val photoFile = File(viewModel.getWorkspacePath(), "$filename.jpg")
            photoDestinationPath = photoFile.absolutePath
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            takePictureLauncher.launch(uri)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (workspace?.name == "__global__") "Cortex" else workspace?.name ?: "Chat",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            if (workingAgents.isNotEmpty()) {
                                workingAgents.forEach { agentName ->
                                    AgentWorkingChip(agentName = agentName)
                                }
                                if (selectedModel.isNotEmpty()) {
                                    Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (selectedModel.isNotEmpty()) {
                                Text(
                                    text = if ("|" in selectedModel) selectedModel.substringAfter("|") else selectedModel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            if (workingAgents.isEmpty()) {
                                activeAgent?.let { agent ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(agentColor(agent.name))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = agent.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = agentColor(agent.name)
                                        )
                                    }
                                } ?: Text(
                                    text = "Sin agente",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (isDrawerMode) {
                        IconButton(onClick = { onOpenDrawer?.invoke() }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    if (isDrawerMode && onNewChat != null) {
                        IconButton(onClick = onNewChat) {
                            Icon(Icons.Default.EditNote, contentDescription = "Nuevo chat")
                        }
                    }
                    // Mic toggle / STT settings
                    IconButton(
                        onClick = {
                            if (isSTTEnabled) {
                                showSTTSettingsDialog = true
                            } else {
                                workspace?.id?.let { id ->
                                    sttViewModel.toggleSTTEnabled(id, true)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSTTEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (isSTTEnabled) "Configurar STT" else "Activar STT",
                            tint = if (isSTTEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuracion")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = uiState.activeTab.ordinal,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .cortexGlass(
                        shape = RoundedCornerShape(22.dp),
                        tint = CortexTheme.colors.glass
                    ),
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = uiState.activeTab == WorkspaceTab.Chat,
                    onClick = { viewModel.setActiveTab(WorkspaceTab.Chat) },
                    text = { Text("Chat") },
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) }
                )
                Tab(
                    selected = uiState.activeTab == WorkspaceTab.Files,
                    onClick = { viewModel.setActiveTab(WorkspaceTab.Files) },
                    text = { Text("Archivos (${files.size})") },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                )
            }

            when (uiState.activeTab) {
                WorkspaceTab.Chat -> {
                    ChatContent(
                        messages = ContextCompactionPolicy.visibleHistory(
                            messages,
                            includeInternalActions = uiState.showCommands
                        ),
                        inputText = uiState.inputText,
                        isLoading = uiState.isLoading,
                        attachedFiles = uiState.attachedFiles,
                        contextInfo = contextInfo,
                        executingCommand = uiState.executingCommand,
                        showReasoning = uiState.showReasoning,
                        showCommands = uiState.showCommands,
                        currentReasoning = uiState.currentReasoning,
                        streamingContent = uiState.streamingContent,
                        streamingReasoning = uiState.streamingReasoning,
                        isSTTEnabled = isSTTEnabled,
                        isListening = isListening,
                        sttTranscription = sttTranscription,
                        pendingTranscription = pendingTranscription,
                        isSTTProcessing = isSTTProcessing,
                        workingAgents = workingAgents,
                        agentStatuses = agentStatuses,
                        subagentExecutions = subagentExecutions.filter {
                            it.status in setOf("QUEUED", "RUNNING", "WAITING_PERMISSION")
                        },
                        todos = uiState.todos,
                        onInputChange = { viewModel.updateInputText(it) },
                        onSend = { viewModel.sendMessage() },
                        onStop = { viewModel.stopAgent() },
                        onCancelSubagent = viewModel::cancelSubagent,
                        onAttachFiles = {
                            val supportedTypes = viewModel.getSupportedFileTypes()
                            if (supportedTypes.isNotEmpty()) {
                                chatFileLauncher.launch(supportedTypes.toTypedArray())
                            } else {
                                chatFileLauncher.launch(arrayOf("*/*"))
                            }
                        },
                        onRemoveAttachedFile = { viewModel.removeAttachedFile(it) },
                        onStartListening = {
                            if (sttViewModel.hasRecordAudioPermission()) {
                                sttViewModel.startListening()
                            } else {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onStopListening = { sttViewModel.stopListening() },
                        onAcceptTranscription = {
                            sttViewModel.acceptTranscription()?.let { text ->
                                viewModel.updateInputText(
                                    if (uiState.inputText.isBlank()) text
                                    else uiState.inputText + " " + text
                                )
                            }
                        },
                        onCancelTranscription = { sttViewModel.cancelTranscription() },
                        modifier = Modifier.weight(1f)
                    )
                }
                WorkspaceTab.Files -> {
                    FilesContent(
                        files = files,
                        workspaceId = workspace?.id ?: 0,
                        onDeleteFile = { fileId, fileName -> viewModel.deleteFile(fileId, fileName) },
                        onOpenFile = { file -> openFile(context, file) },
                        onImportFiles = {
                            importFileLauncher.launch(arrayOf("*/*"))
                        },
                        onRefreshFiles = { viewModel.refreshFiles() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

        AnimatedVisibility(
            visible = visibleAgentChange != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp, start = 16.dp, end = 16.dp),
            enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut()
        ) {
            visibleAgentChange?.let { event ->
                AgentChangeIndicator(event)
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            agents = agents,
            activeAgent = activeAgent,
            selectedModel = selectedModel,
            allModels = viewModel.getAllModels(),
            contextInfo = contextInfo,
            showReasoning = uiState.showReasoning,
            showCommands = uiState.showCommands,
            onAgentSelected = { viewModel.setActiveAgent(it) },
            onModelSelected = { viewModel.setSelectedModel(it) },
            onShowReasoningChanged = { viewModel.setShowReasoning(it) },
            onShowCommandsChanged = { viewModel.setShowCommands(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }


    uiState.pendingPermissionRequest?.let { permissionRequest ->
        CommandPermissionDialog(
            request = permissionRequest,
            onAllowOnce = { viewModel.grantPermissionAndExecute(PermissionLevel.ALLOWED_ONCE) },
            onAllowAlways = { viewModel.grantPermissionAndExecute(PermissionLevel.ALLOWED_ALWAYS) },
            onDeny = { viewModel.denyPermission() }
        )
    }

    // Diálogo de permisos de calendario
    if (uiState.pendingCalendarPermission && !hasCalendarPermissions()) {
        CalendarPermissionDialog(
            onAllow = {
                calendarPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                    )
                )
            },
            onDeny = {
                viewModel.onCalendarPermissionDenied()
            }
        )
    }

    // Diálogo de permiso de cámara
    if (uiState.pendingCameraPermission && !hasCameraPermission()) {
        CameraPermissionDialog(
            onAllow = {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDeny = {
                viewModel.onCameraPermissionDenied()
            }
        )
    }

    // Dialogo de permiso de ubicacion
    if (uiState.pendingLocationPermission && !hasLocationPermission()) {
        AlertDialog(
            onDismissRequest = { viewModel.onLocationPermissionDenied() },
            title = { Text("Permiso de ubicacion") },
            text = { Text("El agente necesita acceder a tu ubicacion para responder esta consulta. Solo se usara para esta peticion.") },
            confirmButton = {
                TextButton(onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) { Text("Permitir") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onLocationPermissionDenied() }) {
                    Text("Denegar")
                }
            }
        )
    }

    // Selector de opciones del agente
    uiState.pendingOptionSelection?.let { request ->
        OptionsSelectionCard(
            request = request,
            onOptionSelected = { viewModel.selectOption(it) },
            onDismiss = { viewModel.dismissOptionSelection() },
            progress = uiState.pendingQuestionQueue?.progress
        )
    }

    // Context compaction in-progress indicator
    if (uiState.isCompacting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            },
            title = {
                Text("Compacting context...", textAlign = TextAlign.Center)
            },
            text = {
                Text(
                    "Summarizing conversation to free up context window space. This may take a moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        )
    }

    // Context compaction dialog
    if (uiState.showContextCompactionDialog) {
        ContextCompactionDialog(
            contextInfo = contextInfo,
            onAccept = { viewModel.acceptContextCompaction() },
            onDismiss = { viewModel.dismissContextCompaction() }
        )
    }

    // STT Settings dialog
    if (showSTTSettingsDialog) {
        STTSettingsDialog(
            currentSettings = sttSettings?.let { s ->
                STTSettingsUiState(
                    mode = try { STTMode.valueOf(s.mode) } catch (e: Exception) { STTMode.LOCAL },
                    cloudProvider = try { CloudSTTProvider.valueOf(s.cloudProvider) } catch (e: Exception) { CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER },
                    apiKey = s.apiKey,
                    localModelType = try { LocalModelType.valueOf(s.localModelType) } catch (e: Exception) { LocalModelType.AUTO },
                    localEngine = try { LocalSTTEngine.valueOf(s.localEngine) } catch (e: Exception) { LocalSTTEngine.AUTO },
                    language = s.language
                )
            } ?: STTSettingsUiState(),
            onSave = { uiState ->
                workspace?.id?.let { id ->
                    sttViewModel.saveSettings(id, uiState)
                }
                showSTTSettingsDialog = false
            },
            onDismiss = { showSTTSettingsDialog = false },
            onDownloadModel = { sttViewModel.downloadModel() },
            isDownloading = isSTTDownloading,
            downloadProgress = sttDownloadProgress,
            isVoskModelDownloaded = isVoskModelDownloaded
        )
    }

    // Web Preview dialog — inline HTML mode or localhost URL mode
    if (uiState.webPreviewHtml != null || uiState.webPreviewUrl != null) {
        WebPreviewDialog(
            html = uiState.webPreviewHtml ?: "",
            url = uiState.webPreviewUrl,
            title = uiState.webPreviewTitle,
            onDismiss = { viewModel.dismissWebPreview() }
        )
    }
}

@Composable
private fun AgentChangeIndicator(event: AgentChangeEvent) {
    val (containerColor, contentColor) = when (event.kind) {
        AgentChangeKind.MEMORY_SAVED -> {
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
        AgentChangeKind.SKILL_CREATED -> {
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }
        AgentChangeKind.SKILL_UPDATED -> {
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        }
    }
    val icon = when (event.kind) {
        AgentChangeKind.MEMORY_SAVED -> Icons.Default.Memory
        AgentChangeKind.SKILL_CREATED -> Icons.Default.AutoAwesome
        AgentChangeKind.SKILL_UPDATED -> Icons.Default.EditNote
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = event.detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Cambio guardado",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSelectorCompact(
    agents: List<Agent>,
    activeAgent: Agent?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAgentSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = activeAgent?.name ?: "Agente",
            onValueChange = {},
            readOnly = true,
            leadingIcon = {
                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            agents.forEach { agent ->
                DropdownMenuItem(
                    text = { Text(agent.name) },
                    leadingIcon = {
                        Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        onAgentSelected(agent.id)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorCompact(
    selectedModel: String,
    allModels: List<ModelInfo>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = (if ("|" in selectedModel) selectedModel.substringAfter("|") else selectedModel).ifEmpty { "Modelo" },
            onValueChange = {},
            readOnly = true,
            leadingIcon = {
                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            if (allModels.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Configura un proveedor primero") },
                    onClick = { onExpandedChange(false) }
                )
            } else {
                allModels.forEach { modelInfo ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    modelInfo.modelId,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    if (modelInfo.provider == com.aiagents.app.domain.model.ProviderType.MANAGED) "Plan" else modelInfo.provider.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            onModelSelected(modelInfo.fullKey)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatContent(
    messages: List<Message>,
    inputText: String,
    isLoading: Boolean,
    attachedFiles: List<AttachedFile>,
    contextInfo: com.aiagents.app.data.repository.ContextInfo,
    executingCommand: String?,
    showReasoning: Boolean,
    showCommands: Boolean = false,
    currentReasoning: String? = null,
    streamingContent: String? = null,
    streamingReasoning: String? = null,
    isSTTEnabled: Boolean = false,
    isListening: Boolean = false,
    sttTranscription: String = "",
    pendingTranscription: String? = null,
    isSTTProcessing: Boolean = false,
    workingAgents: List<String> = emptyList(),
    agentStatuses: Map<String, String> = emptyMap(),
    subagentExecutions: List<SubagentExecutionEntity> = emptyList(),
    todos: List<com.aiagents.app.data.model.TodoEntity> = emptyList(),
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit = {},
    onCancelSubagent: (String) -> Unit = {},
    onAttachFiles: () -> Unit,
    onRemoveAttachedFile: (Int) -> Unit,
    onStartListening: () -> Unit = {},
    onStopListening: () -> Unit = {},
    onAcceptTranscription: () -> Unit = {},
    onCancelTranscription: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var isFirstLoad by remember { mutableStateOf(true) }

    // Scroll inicial inmediato al último mensaje (sin animación) al entrar al chat
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (isFirstLoad) {
                // Primera carga: scroll instantáneo sin animación
                listState.scrollToItem(messages.size - 1)
                isFirstLoad = false
            } else {
                // Mensajes nuevos: scroll animado suave
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    val imeInsets = WindowInsets.ime
    val isImeVisible = imeInsets.getBottom(LocalDensity.current) > 0

    // Hacer scroll al último mensaje cuando aparece el teclado
    LaunchedEffect(isImeVisible) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Animated glow border when agents are working
    val agentColors = remember(isLoading, workingAgents) {
        workingAgentGlowColors(isLoading = isLoading, workingAgents = workingAgents)
    }
    val glowTransition = rememberInfiniteTransition(label = "glowBorder")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val inputGlowModifier = if (agentColors.isNotEmpty()) {
        Modifier.drawWithContent {
            drawContent()
            val cornerRadius = 24.dp.toPx()
            val colors = if (agentColors.size == 1) {
                val color = agentColors.first()
                listOf(
                    color.copy(alpha = glowAlpha * 0.42f),
                    color.copy(alpha = glowAlpha),
                    CortexColors.Blue.copy(alpha = glowAlpha * 0.72f),
                    color.copy(alpha = glowAlpha * 0.42f)
                )
            } else {
                agentColors.flatMap { color ->
                    listOf(
                        color.copy(alpha = glowAlpha * 0.52f),
                        color.copy(alpha = glowAlpha)
                    )
                } + agentColors.first().copy(alpha = glowAlpha * 0.52f)
            }
            val brush = Brush.sweepGradient(colors = colors)
            drawRoundRect(
                brush = brush,
                cornerRadius = CornerRadius(cornerRadius),
                style = Stroke(width = 7.dp.toPx())
            )
            drawRoundRect(
                brush = brush,
                cornerRadius = CornerRadius(cornerRadius),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    } else {
        Modifier
    }

    Column(modifier = modifier) {
        if (messages.isEmpty()) {
            EmptyChatState(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            // Todo dock — shows above messages when agents have an active plan
            if (todos.isNotEmpty()) {
                com.aiagents.app.ui.components.TodoDock(
                    todos = todos,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(
                        message = message,
                        showReasoning = showReasoning,
                        showCommands = showCommands,
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    )
                }
                if (isLoading) {
                    // Show streaming content as it arrives
                    if (!streamingContent.isNullOrBlank()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp)
                            ) {
                                // Show live reasoning above streaming content
                                if (showReasoning && !streamingReasoning.isNullOrBlank()) {
                                    LiveReasoningBlock(
                                        reasoning = streamingReasoning,
                                        modifier = Modifier.fillMaxWidth(0.90f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                // Streaming message bubble
                                MessageBubble(
                                    message = Message(
                                        role = MessageRole.ASSISTANT,
                                        content = streamingContent + " ▍"
                                    ),
                                    showReasoning = false,
                                    showCommands = showCommands
                                )
                            }
                        }
                    } else {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp)
                            ) {
                                if (subagentExecutions.isNotEmpty()) {
                                    SubagentExecutionIndicator(
                                        executions = subagentExecutions,
                                        onCancel = onCancelSubagent
                                    )
                                } else if (showCommands && executingCommand != null) {
                                    TerminalBlock(
                                        command = executingCommand,
                                        isExecuting = true
                                    )
                                } else if (agentStatuses.isNotEmpty()) {
                                    AgentActivityIndicator(agentStatuses = agentStatuses)
                                } else {
                                    LoadingIndicator()
                                }
                                // Show live reasoning while agent is thinking
                                if (showReasoning && !currentReasoning.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LiveReasoningBlock(
                                        reasoning = currentReasoning,
                                        modifier = Modifier.fillMaxWidth(0.90f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Voice input overlay (shown while recording)
        VoiceInputOverlay(
            isVisible = isListening,
            transcription = sttTranscription,
            onDismiss = onStopListening
        )

        // Auto-send transcription: when pendingTranscription arrives, insert into input and send
        LaunchedEffect(pendingTranscription) {
            pendingTranscription?.let {
                onAcceptTranscription()
                onSend()
            }
        }

        AnimatedVisibility(
            visible = attachedFiles.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            AttachedFilesRow(
                files = attachedFiles,
                onRemove = onRemoveAttachedFile,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        ChatInput(
            inputText = inputText,
            isLoading = isLoading,
            supportsFiles = contextInfo.supportsVision || contextInfo.supportsDocuments,
            isSTTEnabled = isSTTEnabled,
            isListening = isListening,
            onInputChange = onInputChange,
            onSend = onSend,
            onStop = onStop,
            onAttachFiles = onAttachFiles,
            onStartListening = onStartListening,
            onStopListening = onStopListening,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .then(inputGlowModifier)
        )
    }
}

@Composable
fun AttachedFilesRow(
    files: List<AttachedFile>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.heightIn(max = 120.dp)
    ) {
        items(files.size) { index ->
            val file = files[index]
            InputChip(
                selected = false,
                onClick = {},
                label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(index) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        when {
                            file.mimeType.startsWith("image") -> Icons.Default.Image
                            file.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Card(
        shape = ShapeTokens.MessageAssistant,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pensando...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun AgentActivityIndicator(
    agentStatuses: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = ShapeTokens.MessageAssistant,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            agentStatuses.forEach { (agentName, status) ->
                val color = agentColor(agentName)
                val infiniteTransition = rememberInfiniteTransition(label = "activity_$agentName")
                val dotAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dotAlpha_$agentName"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = dotAlpha))
                    )
                    Text(
                        text = agentName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubagentExecutionIndicator(
    executions: List<SubagentExecutionEntity>,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = ShapeTokens.MessageAssistant,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Subagentes",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            executions.sortedBy { it.createdAt }.forEach { execution ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ((execution.depth - 1).coerceAtLeast(0) * 10).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (execution.status == "RUNNING") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${execution.agentName} · ${execution.taskId.take(4)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = execution.goal,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onCancel(execution.taskId) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar ${execution.agentName}",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MessageBubble(
    message: Message,
    showReasoning: Boolean = false,
    showCommands: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasSubConversation = message.subConversationId != null
    var showSubConversation by remember { mutableStateOf(false) }
    val isUser = message.role == MessageRole.USER
    val isTool = message.role == MessageRole.TOOL
    val shape = when {
        isUser -> ShapeTokens.MessageUser
        isTool -> MaterialTheme.shapes.small
        else -> ShapeTokens.MessageAssistant
    }

    val context = LocalContext.current
    var showCopiedToast by remember { mutableStateOf(false) }

    // Estado para controlar si se muestra el reasoning
    var showReasoningExpanded by remember { mutableStateOf(false) }
    val hasReasoning = !message.reasoning.isNullOrBlank()

    // Estado para controlar si se muestran los comandos y salidas
    var showCommandsExpanded by remember { mutableStateOf(false) }
    val hasCommands = message.toolCalls.isNotEmpty() || message.toolResults.isNotEmpty()
    val hasWeatherWidget = message.toolResults.any { toolResult ->
        toolResult.name in com.aiagents.app.data.terminal.WeatherToolHandler.ALL_TOOL_NAMES &&
            com.aiagents.app.ui.components.extractWeatherDataJson(toolResult.content) != null
    }

    // Estado para controlar si se muestra el contenido de tool messages
    var showToolContentExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedToast) {
        if (showCopiedToast) {
            Toast.makeText(context, "Mensaje copiado", Toast.LENGTH_SHORT).show()
            showCopiedToast = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Botón de mostrar pensamiento (mientras está cargando o si hay reasoning)
        if (!isUser && !isTool && showReasoning && hasReasoning) {
            TextButton(
                onClick = { showReasoningExpanded = !showReasoningExpanded },
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = if (showReasoningExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showReasoningExpanded) "Ocultar pensamiento" else "Mostrar pensamiento",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            // Contenido del reasoning expandible
            AnimatedVisibility(
                visible = showReasoningExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                ReasoningBlock(
                    reasoning = message.reasoning ?: "",
                    modifier = Modifier.fillMaxWidth(0.90f)
                )
            }
        }

        // Weather cards always visible (outside collapsible block)
        if (hasCommands) {
            message.toolResults.forEach { toolResult ->
                if (toolResult.name in com.aiagents.app.data.terminal.WeatherToolHandler.ALL_TOOL_NAMES) {
                    val weatherJson = com.aiagents.app.ui.components.extractWeatherDataJson(toolResult.content)
                    if (weatherJson != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        com.aiagents.app.ui.components.WeatherResultCard(
                            weatherJson = weatherJson,
                            modifier = Modifier.fillMaxWidth(0.90f)
                        )
                    }
                }
            }
        }

        // Botón de mostrar comandos cuando hay toolCalls o toolResults y showCommands está activado
        if (hasCommands && showCommands) {
            TextButton(
                onClick = { showCommandsExpanded = !showCommandsExpanded },
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = if (showCommandsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showCommandsExpanded) "Ocultar comandos" else "Mostrar comandos",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Contenido de comandos expandible
            AnimatedVisibility(
                visible = showCommandsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.toolCalls.forEach { toolCall ->
                        TerminalBlock(
                            command = extractCommandFromToolCall(toolCall),
                            isExecuting = false,
                            modifier = Modifier.fillMaxWidth(0.90f)
                        )
                    }

                    message.toolResults.forEach { toolResult ->
                        // Skip weather results that have a card (they're rendered above)
                        val isWeatherWithCard = toolResult.name in com.aiagents.app.data.terminal.WeatherToolHandler.ALL_TOOL_NAMES &&
                            toolResult.content.contains("<!--WEATHER_DATA:")
                        if (!isWeatherWithCard) {
                            ToolResultBlock(
                                content = toolResult.content,
                                modifier = Modifier.fillMaxWidth(0.90f)
                            )
                        }
                    }
                }
            }
        }

        // Ocultar mensajes TOOL completamente si showCommands está desactivado
        val shouldShowMessage = (message.content.isNotEmpty() || message.attachedFiles.isNotEmpty()) &&
            (showCommands || !isTool) &&
            !hasWeatherWidget

        if (shouldShowMessage) {
            val messageSurface = when {
                isUser -> Modifier
                    .clip(shape)
                    .background(CortexColors.UserMessageBrush)
                    .border(1.dp, Color.White.copy(alpha = 0.20f), shape)
                isTool -> Modifier.cortexGlass(
                    shape = shape,
                    tint = Color(0xD9111016),
                    borderAlpha = 0.65f
                )
                else -> Modifier.cortexGlass(
                    shape = shape,
                    tint = CortexTheme.colors.glassSoft
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .then(messageSurface)
                    .then(
                        if (!isUser) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                                    showCopiedToast = true
                                }
                            )
                        } else Modifier
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.attachedFiles.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isUser)
                                    Color.White.copy(alpha = 0.78f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = message.attachedFiles.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser)
                                    Color.White.copy(alpha = 0.78f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    // Para mensajes TOOL, mostrar toggle para expandir/colapsar contenido solo si showCommands está activado
                    if (isTool && showCommands) {
                        TextButton(
                            onClick = { showToolContentExpanded = !showToolContentExpanded },
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (showToolContentExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showToolContentExpanded) "Ocultar salida" else "Mostrar salida",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00FF00)
                            )
                        }

                        AnimatedVisibility(
                            visible = showToolContentExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            MessageContent(
                                content = message.content,
                                isUser = isUser,
                                isTool = isTool
                            )
                        }
                    } else if (!isTool) {
                        MessageContent(
                            content = message.content,
                            isUser = isUser,
                            isTool = isTool
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser)
                            Color.White.copy(alpha = 0.72f)
                        else if (isTool)
                            Color(0xFF00FF00).copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Sub-conversation expansion button
        if (hasSubConversation && !isUser) {
            TextButton(
                onClick = { showSubConversation = !showSubConversation },
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = if (showSubConversation) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showSubConversation) "Ocultar trabajo del agente" else "Ver trabajo del agente",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = showSubConversation,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                SubConversationViewer(
                    subConversationId = message.subConversationId!!,
                    modifier = Modifier.fillMaxWidth(0.90f)
                )
            }
        }
    }
}

@Composable
fun SubConversationViewer(
    subConversationId: Long,
    modifier: Modifier = Modifier
) {
    val viewModel: WorkspaceDetailViewModel = hiltViewModel()
    val subMessages by viewModel.getSubConversationMessages(subConversationId)
        .collectAsState(initial = emptyList())

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.padding(top = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Trabajo del agente (${subMessages.size} mensajes)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            subMessages.forEach { msg ->
                val roleLabel = when (msg.role) {
                    MessageRole.USER -> "Tarea"
                    MessageRole.ASSISTANT -> "Agente"
                    MessageRole.TOOL -> "Tool"
                    MessageRole.SYSTEM -> "Sistema"
                }
                val roleColor = when (msg.role) {
                    MessageRole.USER -> MaterialTheme.colorScheme.primary
                    MessageRole.ASSISTANT -> MaterialTheme.colorScheme.secondary
                    MessageRole.TOOL -> Color(0xFF4CAF50)
                    MessageRole.SYSTEM -> MaterialTheme.colorScheme.tertiary
                }
                val bgColor = when (msg.role) {
                    MessageRole.TOOL -> Color(0xFF1E1E1E)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(
                            text = roleLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = roleColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = msg.content.take(500) + if (msg.content.length > 500) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.role == MessageRole.TOOL) Color(0xFF00FF00) else MaterialTheme.colorScheme.onSurface
                        )
                        if (msg.toolCalls.isNotEmpty()) {
                            Text(
                                text = "Tools: ${msg.toolCalls.joinToString(", ") { it.function.name }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFC107)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalBlock(
    command: String,
    isExecuting: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        onClick = { expanded = !expanded }
    ) {
        if (expanded) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = if (isExecuting) Color(0xFFFFC107) else Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isExecuting) "Ejecutando..." else "Comando ejecutado",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isExecuting) Color(0xFFFFC107) else Color(0xFF4CAF50)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$ $command",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = Color(0xFF00FF00)
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFFFFC107),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = if (isExecuting) "Ejecutando comando..." else "Comando ejecutado",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isExecuting) Color(0xFFFFC107) else Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun ToolResultBlock(
    content: String,
    modifier: Modifier = Modifier
) {
    // Extract image URLs from tool result content
    val imageUrls = remember(content) {
        val mdRegex = Regex("""!\[[^\]]*\]\((https?://[^\s)]+)\)""", RegexOption.IGNORE_CASE)
        val urls = mdRegex.findAll(content).map { it.groupValues[1] }
            .filter { url ->
                url.isNotBlank() && url.length > 10 && !url.endsWith("...")
            }
            .toList()
        android.util.Log.d("ToolResultBlock", "Extracted ${urls.size} image URLs from tool result (content length: ${content.length})")
        urls.forEachIndexed { i, url -> android.util.Log.d("ToolResultBlock", "  Image [$i]: $url") }
        if (urls.isEmpty()) {
            android.util.Log.d("ToolResultBlock", "Content preview: ${content.take(300)}")
        }
        urls
    }
    // Text without image markdown for the collapsed view
    val textContent = remember(content) {
        val mdRegex = Regex("""!\[[^\]]*\]\(https?://[^\s)]+\)""", RegexOption.IGNORE_CASE)
        mdRegex.replace(content, "").trim()
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        onClick = { expanded = !expanded }
    ) {
        if (expanded) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Salida:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (textContent.isNotBlank()) {
                    val displayText = textContent.take(500) + if (textContent.length > 500) "\n..." else ""
                    LinkableText(
                        text = displayText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFB0BEC5)
                        ),
                        linkColor = Color(0xFF64B5F6),
                        maxLines = 15
                    )
                }
                if (imageUrls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ImageCarousel(imageUrls = imageUrls)
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Salida del comando",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E)
                )
            }
        }
    }
}

@Composable
fun ReasoningBlock(
    reasoning: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A237E).copy(alpha = 0.15f)
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFF5C6BC0).copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF5C6BC0),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Proceso de pensamiento",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF5C6BC0)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reasoning,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = Color(0xFF7986CB)
            )
        }
    }
}

@Composable
fun LiveReasoningBlock(
    reasoning: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when reasoning updates
    LaunchedEffect(reasoning) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A237E).copy(alpha = 0.12f)
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFF5C6BC0).copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF5C6BC0),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Pensando...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF5C6BC0)
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Color(0xFF5C6BC0)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reasoning,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = Color(0xFF7986CB),
                modifier = Modifier
                    .heightIn(max = 200.dp)
                    .verticalScroll(scrollState)
            )
        }
    }
}

private fun extractCommandFromToolCall(toolCall: com.aiagents.app.domain.model.ToolCall): String {
    return try {
        val args = com.google.gson.Gson().fromJson(
            toolCall.function.arguments,
            Map::class.java
        )
        args?.get("command")?.toString() ?: toolCall.function.arguments
    } catch (e: Exception) {
        toolCall.function.arguments
    }
}

/**
 * Renders text with clickable URLs that open in the browser.
 */
@Composable
fun LinkableText(
    text: String,
    style: TextStyle,
    linkColor: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    val uriHandler = LocalUriHandler.current

    val annotatedText = remember(text, style) {
        buildMarkdownAnnotatedString(text, style, linkColor)
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotatedText,
        style = style,
        maxLines = maxLines,
        modifier = modifier,
        onClick = { offset ->
            annotatedText.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (_: Exception) { }
                }
        }
    )
}

private fun buildMarkdownAnnotatedString(
    text: String,
    baseStyle: TextStyle,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { lineIndex, rawLine ->
            if (lineIndex > 0) append("\n")

            // Horizontal rule: ---, ***, ___  (3+ of same char, optionally spaced)
            if (rawLine.trim().matches(Regex("""^[-*_]{3,}$"""))) {
                append("─".repeat(20))
                return@forEachIndexed
            }

            // Headers: # to ###### → bold + scaled size
            val headerMatch = Regex("""^(#{1,6})\s+(.+)$""").find(rawLine)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val headerText = headerMatch.groupValues[2]
                val fontSize = when (level) {
                    1 -> (baseStyle.fontSize.value * 1.5f).sp
                    2 -> (baseStyle.fontSize.value * 1.3f).sp
                    3 -> (baseStyle.fontSize.value * 1.15f).sp
                    else -> (baseStyle.fontSize.value * 1.05f).sp
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = fontSize)) {
                    appendInlineMarkdown(headerText, linkColor)
                }
                return@forEachIndexed
            }

            // Blockquote: > text
            val blockquoteMatch = Regex("""^>\s*(.*)$""").find(rawLine)
            if (blockquoteMatch != null) {
                val quoteText = blockquoteMatch.groupValues[1]
                append("┃ ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineMarkdown(quoteText, linkColor)
                }
                return@forEachIndexed
            }

            // Bullet lists: - item or * item (but not ** or ***)
            val bulletMatch = Regex("""^(\s*)[-*](?!\*)\s+(.+)$""").find(rawLine)
            if (bulletMatch != null) {
                val indent = bulletMatch.groupValues[1]
                val bulletText = bulletMatch.groupValues[2]
                append("${indent}\u2022 ")
                appendInlineMarkdown(bulletText, linkColor)
                return@forEachIndexed
            }

            // Numbered lists: 1. item
            val numberedMatch = Regex("""^(\s*)(\d+)\.\s+(.+)$""").find(rawLine)
            if (numberedMatch != null) {
                val indent = numberedMatch.groupValues[1]
                val number = numberedMatch.groupValues[2]
                val itemText = numberedMatch.groupValues[3]
                append("${indent}$number. ")
                appendInlineMarkdown(itemText, linkColor)
                return@forEachIndexed
            }

            // Regular line with inline markdown
            appendInlineMarkdown(rawLine, linkColor)
        }
    }
}

private enum class InlineToken {
    BOLD_ITALIC_STAR,   // ***text***
    BOLD_ITALIC_UNDER,  // ___text___
    BOLD_STAR,          // **text**
    BOLD_UNDER,         // __text__
    ITALIC_STAR,        // *text*
    ITALIC_UNDER,       // _text_
    STRIKETHROUGH,      // ~~text~~
    CODE,               // `text`
    LINK,               // [text](url)
    RAW_URL             // https://...
}

private data class InlineMatch(
    val token: InlineToken,
    val range: IntRange,
    val content: String,
    val url: String? = null
)

private fun findInlineMatches(text: String): List<InlineMatch> {
    val matches = mutableListOf<InlineMatch>()

    // Each pattern with its token type - order defines priority for overlapping matches
    val patterns = listOf(
        InlineToken.BOLD_ITALIC_STAR to Regex("""\*\*\*(.+?)\*\*\*"""),
        InlineToken.BOLD_ITALIC_UNDER to Regex("""___(.+?)___"""),
        InlineToken.BOLD_STAR to Regex("""\*\*(.+?)\*\*"""),
        InlineToken.BOLD_UNDER to Regex("""__(.+?)__"""),
        InlineToken.ITALIC_STAR to Regex("""\*(.+?)\*"""),
        InlineToken.ITALIC_UNDER to Regex("""(?<=\s|^)_(.+?)_(?=\s|$|[.,;:!?])"""),
        InlineToken.STRIKETHROUGH to Regex("""~~(.+?)~~"""),
        InlineToken.CODE to Regex("""`([^`]+?)`"""),
        InlineToken.LINK to Regex("""\[([^\]]+?)\]\((https?://[^\s)]+)\)"""),
        InlineToken.RAW_URL to Regex("""(https?://[^\s)\]>]+)""")
    )

    // Collect all candidates
    val candidates = mutableListOf<InlineMatch>()
    for ((token, regex) in patterns) {
        regex.findAll(text).forEach { m ->
            val content = m.groupValues.getOrElse(1) { "" }
            val url = if (token == InlineToken.LINK) m.groupValues.getOrElse(2) { null } else null
            candidates.add(InlineMatch(token, m.range, content, url))
        }
    }

    // Sort by start position, then by longest match (higher priority patterns consume more)
    candidates.sortWith(compareBy({ it.range.first }, { -it.range.count() }))

    // Greedily pick non-overlapping matches
    var lastEnd = -1
    for (candidate in candidates) {
        if (candidate.range.first > lastEnd) {
            matches.add(candidate)
            lastEnd = candidate.range.last
        }
    }

    return matches
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    linkColor: Color
) {
    val matches = findInlineMatches(text)

    var lastIndex = 0
    for (m in matches) {
        // Append text before this match
        if (m.range.first > lastIndex) {
            append(text.substring(lastIndex, m.range.first))
        }

        when (m.token) {
            InlineToken.BOLD_ITALIC_STAR, InlineToken.BOLD_ITALIC_UNDER -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(m.content)
                }
            }
            InlineToken.BOLD_STAR, InlineToken.BOLD_UNDER -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineMarkdown(m.content, linkColor)
                }
            }
            InlineToken.ITALIC_STAR, InlineToken.ITALIC_UNDER -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(m.content)
                }
            }
            InlineToken.STRIKETHROUGH -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(m.content)
                }
            }
            InlineToken.CODE -> {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x33888888)
                )) {
                    append(m.content)
                }
            }
            InlineToken.LINK -> {
                pushStringAnnotation("URL", m.url!!)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(m.content)
                }
                pop()
            }
            InlineToken.RAW_URL -> {
                val url = m.content.trimEnd('.', ',', ';', ':', '!', '?')
                pushStringAnnotation("URL", url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(url)
                }
                pop()
                val trimmedChars = m.content.length - url.length
                if (trimmedChars > 0) {
                    append(m.content.takeLast(trimmedChars))
                }
            }
        }
        lastIndex = m.range.last + 1
    }

    // Append remaining text
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

/**
 * Renderiza el contenido de un mensaje, mostrando bloques de código
 * con syntax highlighting cuando corresponda.
 */
@Composable
fun MessageContent(
    content: String,
    isUser: Boolean,
    isTool: Boolean,
    modifier: Modifier = Modifier
) {
    val segments = try {
        parseMessageWithCodeBlocks(content)
    } catch (e: Exception) {
        listOf(ContentSegment(SegmentType.TEXT, content))
    }

    val textAndCodeSegments = segments.filter { it.type != SegmentType.IMAGE }
    val imageUrls = segments
        .filter { it.type == SegmentType.IMAGE }
        .map { it.content }
        .filter { url ->
            url.isNotBlank() &&
            (url.startsWith("http://") || url.startsWith("https://")) &&
            url.length > 10 &&
            !url.endsWith("...")
        }

    val context = LocalContext.current
    Column(modifier = modifier) {
        // Render text and code segments
        textAndCodeSegments.forEach { segment ->
            when (segment.type) {
                SegmentType.TEXT -> {
                    val textColor = when {
                        isUser -> Color.White
                        isTool -> Color(0xFF00FF00)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    LinkableText(
                        text = segment.content,
                        style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                        linkColor = Color(0xFF64B5F6)
                    )
                }
                SegmentType.CODE_BLOCK -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    CodeBlock(
                        code = segment.content,
                        language = segment.language ?: "text"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                SegmentType.TABLE -> {
                    val tableTextColor = when {
                        isUser -> Color.White
                        isTool -> Color(0xFF00FF00)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownTable(
                        tableContent = segment.content,
                        textColor = tableTextColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                SegmentType.WEATHER -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    com.aiagents.app.ui.components.WeatherResultCard(weatherJson = segment.content)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                else -> {}
            }
        }

        // Render images as carousel at the bottom
        if (imageUrls.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ImageCarousel(imageUrls = imageUrls)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCarousel(
    imageUrls: List<String>,
    modifier: Modifier = Modifier
) {
    if (imageUrls.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { imageUrls.size })
    var downloadingIndex by remember { mutableIntStateOf(-1) }
    // Track failed images to hide them visually
    val failedImages = remember { mutableStateMapOf<Int, Boolean>() }

    android.util.Log.d("ImageCarousel", "Rendering carousel with ${imageUrls.size} images")
    imageUrls.forEachIndexed { i, url ->
        android.util.Log.d("ImageCarousel", "  [$i] $url")
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 8.dp
            ) { page ->
                val url = imageUrls[page]
                val isFailed = failedImages[page] == true

                if (isFailed) {
                    // Show placeholder for failed images
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No se pudo cargar",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            when (state) {
                                is AsyncImagePainter.State.Error -> {
                                    android.util.Log.e("ImageCarousel", "Failed to load image [$page]: $url", state.result.throwable)
                                    failedImages[page] = true
                                }
                                is AsyncImagePainter.State.Success -> {
                                    android.util.Log.d("ImageCarousel", "Successfully loaded image [$page]: $url")
                                }
                                else -> {}
                            }
                        }
                    )
                }
            }

            // Page counter badge
            if (imageUrls.size > 1) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${imageUrls.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Download button (only for non-failed images)
            if (failedImages[pagerState.currentPage] != true) {
                val isDownloading = downloadingIndex == pagerState.currentPage
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clickable(enabled = !isDownloading) {
                            val currentPage = pagerState.currentPage
                            downloadingIndex = currentPage
                            scope.launch {
                                downloadImageToGallery(
                                    context,
                                    imageUrls[currentPage],
                                    "image_${System.currentTimeMillis()}"
                                )
                                downloadingIndex = -1
                            }
                        }
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Descargar imagen",
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        // Dot indicators
        if (imageUrls.size > 1) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                repeat(imageUrls.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val isFailed = failedImages[index] == true
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isFailed -> Color.Red.copy(alpha = 0.5f)
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                }
                            )
                    )
                }
            }
        }
    }
}

private suspend fun downloadImageToGallery(context: Context, url: String, fileName: String) {
    try {
        withContext(Dispatchers.IO) {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()
            val bytes = connection.inputStream.use { it.readBytes() }
            connection.disconnect()

            val contentType = connection.contentType ?: "image/jpeg"
            val extension = when {
                contentType.contains("png") -> "png"
                contentType.contains("webp") -> "webp"
                contentType.contains("gif") -> "gif"
                else -> "jpg"
            }
            val fullName = "$fileName.$extension"
            val mimeType = if (contentType.startsWith("image/")) contentType else "image/$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fullName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIAgents")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/AIAgents")
                dir.mkdirs()
                val file = File(dir, fullName)
                file.writeBytes(bytes)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Imagen guardada", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error al descargar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun EmptyChatState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp)
    ) {
        CortexMark(Modifier.size(82.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Cortex está listo",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Escribe, habla o reúne a tus agentes para comenzar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChatInput(
    inputText: String,
    isLoading: Boolean,
    supportsFiles: Boolean,
    isSTTEnabled: Boolean = false,
    isListening: Boolean = false,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit = {},
    onAttachFiles: () -> Unit,
    onStartListening: () -> Unit = {},
    onStopListening: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shape = ShapeTokens.TextField,
        modifier = modifier.cortexGlass(
            shape = ShapeTokens.TextField,
            tint = CortexTheme.colors.glassStrong
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp)
        ) {
            if (supportsFiles) {
                IconButton(
                    onClick = onAttachFiles,
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Adjuntar archivos")
                }
            }
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        if (isLoading) "Agrega contexto al agente..."
                        else "Escribe un mensaje..."
                    )
                },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 5
            )
            // Voice input button (between TextField and Send)
            if (isSTTEnabled) {
                VoiceInputButton(
                    isListening = isListening,
                    onStartListening = onStartListening,
                    onStopListening = onStopListening,
                    enabled = !isLoading
                )
            }
            // Stop button — visible only while agent is working
            if (isLoading) {
                FilledIconButton(
                    onClick = onStop,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Detener agente",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
            // Send button — always available when there's text
            FilledIconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank(),
                shape = CircleShape
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesContent(
    files: List<AgentFile>,
    workspaceId: Long,
    onDeleteFile: (Long, String) -> Unit,
    onOpenFile: (AgentFile) -> Unit,
    onImportFiles: () -> Unit,
    onRefreshFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            // Botón de refrescar
            OutlinedIconButton(
                onClick = onRefreshFiles
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refrescar archivos")
            }
            // Botón de importar
            FilledTonalButton(
                onClick = onImportFiles,
                shape = ShapeTokens.Button
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Importar archivo")
            }
        }

        if (files.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sin archivos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Importa archivos del teléfono o los generados por el agente aparecerán aquí",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = files,
                    key = { "${it.id}_${it.name}" }  // Key única combinando id y nombre
                ) { file ->
                    FileItem(
                        file = file,
                        onOpen = { onOpenFile(file) },
                        onDelete = { onDeleteFile(file.id, file.name) },
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(
    file: AgentFile,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showActions by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { showActions = !showActions }
            ),
        shape = ShapeTokens.Card
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = when {
                    file.generatedByAI -> Icons.Default.Code
                    file.mimeType.startsWith("image") -> Icons.Default.Image
                    file.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
                    else -> Icons.Outlined.Description
                },
                contentDescription = null,
                tint = when {
                    file.generatedByAI -> MaterialTheme.colorScheme.tertiary
                    file.mimeType.startsWith("image") -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (file.generatedByAI) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text("AI", fontSize = 10.sp) },
                            modifier = Modifier.height(20.dp)
                        )
                    }
                    // Indicador de archivo escaneado en tiempo real (no registrado en BD)
                    if (file.id == 0L) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text("NUEVO", fontSize = 10.sp) },
                            modifier = Modifier.height(20.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
            
            AnimatedVisibility(visible = showActions) {
                Row {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Abrir")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    agents: List<Agent>,
    activeAgent: Agent?,
    selectedModel: String,
    allModels: List<ModelInfo>,
    contextInfo: com.aiagents.app.data.repository.ContextInfo,
    showReasoning: Boolean,
    showCommands: Boolean,
    onAgentSelected: (Long?) -> Unit,
    onModelSelected: (String) -> Unit,
    onShowReasoningChanged: (Boolean) -> Unit,
    onShowCommandsChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        title = { Text("Configuración") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                // Selector de Agente
                Text(
                    text = "Agente",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                var agentExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = agentExpanded,
                    onExpandedChange = { agentExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = activeAgent?.name ?: "Sin agente",
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = agentExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = agentExpanded,
                        onDismissRequest = { agentExpanded = false }
                    ) {
                        agents.forEach { agent ->
                            DropdownMenuItem(
                                text = { Text(agent.name) },
                                leadingIcon = {
                                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    onAgentSelected(agent.id)
                                    agentExpanded = false
                                }
                            )
                        }
                    }
                }
                
                HorizontalDivider()
                
                // Selector de Modelo
                Text(
                    text = "Modelo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                var modelExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = (if ("|" in selectedModel) selectedModel.substringAfter("|") else selectedModel).ifEmpty { "Seleccionar modelo" },
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        if (allModels.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Configura un proveedor primero") },
                                onClick = { modelExpanded = false }
                            )
                        } else {
                            allModels.forEach { modelInfo ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                modelInfo.modelId,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                if (modelInfo.provider == com.aiagents.app.domain.model.ProviderType.MANAGED) "Plan" else modelInfo.provider.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        onModelSelected(modelInfo.fullKey)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                
                HorizontalDivider()
                
                // Información de Contexto
                Text(
                    text = "Información del Contexto",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tokens",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${contextInfo.currentTokens.formatNumber()} / ${contextInfo.maxTokens.formatNumber()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val progress = contextInfo.usagePercentage / 100f
                        val progressColor = when {
                            progress > 0.9f -> MaterialTheme.colorScheme.error
                            progress > 0.7f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        
                        LinearProgressIndicator(
                            progress = { progress },
                            color = progressColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "${String.format("%.1f", contextInfo.usagePercentage)}% usado • ${contextInfo.availableTokens.formatNumber()} disponibles",
                            style = MaterialTheme.typography.bodySmall,
                            color = progressColor
                        )
                    }
                }
                
                // Capacidades del modelo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (contextInfo.supportsVision) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Imágenes") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    if (contextInfo.supportsDocuments) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Documentos") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    if (!contextInfo.supportsVision && !contextInfo.supportsDocuments) {
                        Text(
                            text = "Sin soporte de archivos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider()
                
                // Opción de ver razonamiento
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ver razonamiento",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Mostrar el proceso de pensamiento de los modelos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = showReasoning,
                        onCheckedChange = onShowReasoningChanged
                    )
                }

                HorizontalDivider()

                // Opción de ver comandos ejecutados
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ver comandos ejecutados",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Mostrar comandos y salidas en el chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = showCommands,
                        onCheckedChange = onShowCommandsChanged
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

fun Int.formatNumber(): String {
    return String.format("%,d", this)
}

@Composable
private fun ContextCompactionDialog(
    contextInfo: com.aiagents.app.data.repository.ContextInfo,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    val usageColor = when {
        contextInfo.usagePercentage >= 95f -> Color(0xFFE53935)
        contextInfo.usagePercentage >= 85f -> Color(0xFFFF9800)
        else -> Color(0xFFFFC107)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Compress,
                contentDescription = null,
                tint = usageColor,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Context Window Almost Full",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Usage bar
                LinearProgressIndicator(
                    progress = { (contextInfo.usagePercentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = usageColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = "${String.format("%.0f", contextInfo.usagePercentage)}% used — ${contextInfo.availableTokens.formatNumber()} tokens remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = usageColor,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "The conversation is approaching the model's context limit. Would you like to create a context checkpoint?\n\nYour complete chat will remain visible. Cortex will send the model a concise summary plus the latest messages, without deleting the transcript.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compact")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    )
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private val agentColorPalette = listOf(
    CortexColors.Violet, // Cortex
    Color(0xFF00BCD4), // Cyan
    Color(0xFF4CAF50), // Green
    Color(0xFFFFC107), // Amber
    Color(0xFFFF5722), // Deep Orange
    Color(0xFFE91E63), // Pink
    Color(0xFF009688), // Teal
    Color(0xFF2196F3), // Blue
    Color(0xFF9C27B0), // Violet
    Color(0xFF795548)  // Brown
)

fun agentColor(name: String): Color {
    if (name.equals("Cortex", ignoreCase = true)) return agentColorPalette[0]
    val stableHash = name.lowercase(Locale.ROOT).hashCode() and Int.MAX_VALUE
    val index = (stableHash % (agentColorPalette.size - 1)) + 1
    return agentColorPalette[index]
}

fun workingAgentGlowColors(
    isLoading: Boolean,
    workingAgents: List<String>
): List<Color> = when {
    workingAgents.isNotEmpty() -> workingAgents
        .distinctBy { it.lowercase(Locale.ROOT) }
        .map(::agentColor)
    isLoading -> listOf(CortexColors.Violet)
    else -> emptyList()
}

@Composable
fun AgentWorkingChip(agentName: String) {
    val color = agentColor(agentName)
    val infiniteTransition = rememberInfiniteTransition(label = "agentPulse_$agentName")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha_$agentName"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 150.dp)
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = pulseAlpha))
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = agentName,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var name = "file"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}

fun getFileSize(context: Context, uri: Uri): Long {
    var size = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && sizeIndex >= 0) {
            size = cursor.getLong(sizeIndex)
        }
    }
    return size
}

fun openFile(context: Context, file: AgentFile) {
    try {
        val isContentUri = file.path.startsWith("content://")
        val uri = if (isContentUri) {
            // External storage (SAF) — path is already a content URI
            android.net.Uri.parse(file.path)
        } else {
            val fileToOpen = File(file.path)
            if (!fileToOpen.exists()) {
                Toast.makeText(context, "Archivo no encontrado", Toast.LENGTH_SHORT).show()
                return
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileToOpen
            )
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Abrir con")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Error al abrir archivo: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun TerminalContent(
    history: List<TerminalEntry>,
    inputText: String,
    isExecuting: Boolean,
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size - 1)
    }

    Column(
        modifier = modifier.background(Color(0xFF0D1117))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Terminal",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8B949E),
                fontFamily = FontFamily.Monospace
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Limpiar", color = Color(0xFF8B949E), fontSize = 12.sp)
                }
            }
        }

        HorizontalDivider(color = Color(0xFF30363D))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(history) { entry ->
                TerminalHistoryEntry(entry)
            }
            if (isExecuting) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$ ", color = Color(0xFF3FB950), fontFamily = FontFamily.Monospace)
                        Text(inputText, color = Color(0xFFE6EDF3), fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(4.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = Color(0xFF3FB950)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF30363D))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$ ",
                color = Color(0xFF3FB950),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = Color(0xFFE6EDF3),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(Color(0xFF3FB950)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onExecute() }),
                singleLine = true,
                decorationBox = { inner ->
                    if (inputText.isEmpty()) {
                        Text(
                            "ingresa un comando...",
                            color = Color(0xFF484F58),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                    inner()
                }
            )
            if (inputText.isNotEmpty() && !isExecuting) {
                IconButton(onClick = onExecute, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Ejecutar",
                        tint = Color(0xFF3FB950),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalHistoryEntry(entry: TerminalEntry) {
    Column {
        Row {
            Text("$ ", color = Color(0xFF3FB950), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text(entry.command, color = Color(0xFFE6EDF3), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        if (entry.output.isNotBlank()) {
            Text(
                entry.output,
                color = if (entry.isSuccess) Color(0xFFC9D1D9) else Color(0xFFF85149),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
        Text(
            "${entry.executionTimeMs}ms",
            color = Color(0xFF484F58),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun CommandPermissionDialog(
    request: PermissionRequest,
    onAllowOnce: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit
) {
    val riskColor = when (request.riskLevel) {
        CommandRiskLevel.SAFE -> Color(0xFF4CAF50)
        CommandRiskLevel.MODERATE -> Color(0xFFFF9800)
        CommandRiskLevel.BLOCKED -> Color(0xFFF44336)
    }
    
    val riskLabel = when (request.riskLevel) {
        CommandRiskLevel.SAFE -> "Seguro"
        CommandRiskLevel.MODERATE -> "Moderado"
        CommandRiskLevel.BLOCKED -> "Bloqueado"
    }

    AlertDialog(
        onDismissRequest = onDeny,
        icon = { 
            Icon(
                Icons.Default.Terminal, 
                contentDescription = null,
                tint = riskColor
            ) 
        },
        title = { Text("Permisos de Terminal") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "El agente quiere ejecutar:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1E1E)
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = request.command,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00FF00)
                        ),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Nivel de riesgo:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text(riskLabel) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = riskColor.copy(alpha = 0.2f),
                            labelColor = riskColor
                        )
                    )
                }
                
                if (request.riskLevel == CommandRiskLevel.MODERATE) {
                    Text(
                        text = "Este comando puede modificar archivos. Revisa antes de aprobar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDeny) {
                    Text("Rechazar")
                }
                FilledTonalButton(onClick = onAllowOnce) {
                    Text("Una vez")
                }
                Button(onClick = onAllowAlways) {
                    Text("Siempre")
                }
            }
        }
    )
}

@Composable
fun CalendarPermissionDialog(
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Acceso al Calendario") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cortex necesita acceder a tu calendario para:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Leer tus eventos y citas",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Agregar nuevos eventos",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    text = "Los datos del calendario solo se procesan localmente en tu dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDeny) {
                    Text("Denegar")
                }
                Button(onClick = onAllow) {
                    Text("Permitir")
                }
            }
        }
    )
}

@Composable
fun CameraPermissionDialog(
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Acceso a la Cámara") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cortex necesita acceder a tu cámara para:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tomar fotos y guardarlas en el workspace",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ImageSearch,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Analizar imágenes capturadas",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text(
                    text = "Las fotos se guardan localmente en el workspace actual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDeny) {
                    Text("Denegar")
                }
                Button(onClick = onAllow) {
                    Text("Permitir")
                }
            }
        }
    )
}
