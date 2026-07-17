package com.aiagents.app.presentation.assistant

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aiagents.app.data.repository.ContextCompactionPolicy
import com.aiagents.app.data.local.AssistantPreferences
import com.aiagents.app.data.local.VoicePreferences
import com.aiagents.app.data.speech.AndroidTextToSpeechManager
import com.aiagents.app.data.speech.AssistantTtsMode
import com.aiagents.app.data.terminal.AssistantActionCoordinator
import com.aiagents.app.domain.model.AssistantActionStatus
import com.aiagents.app.domain.model.ContactActionPurpose
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.PendingAssistantAction
import com.aiagents.app.presentation.tool_results.DirectToolResultPayload
import com.aiagents.app.presentation.stt.STTViewModel
import com.aiagents.app.presentation.workspace_detail.WorkspaceDetailViewModel
import com.aiagents.app.presentation.workspace_detail.LinkableText
import com.aiagents.app.ui.components.DirectToolResultCard
import com.aiagents.app.ui.theme.CortexColors
import com.aiagents.app.ui.theme.CortexBubbleMark
import com.aiagents.app.ui.theme.CortexTheme
import com.aiagents.app.ui.theme.cortexGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import coil.compose.AsyncImage

private enum class AssistantStage {
    READY,
    LISTENING,
    TRANSCRIBING,
    THINKING,
    SPEAKING,
    RESULT,
    ERROR
}

@Composable
fun CortexAssistantScreen(
    workspaceId: Long,
    cortexName: String,
    assistantLanguageTag: String,
    textToSpeech: AndroidTextToSpeechManager,
    preferences: AssistantPreferences,
    voicePreferences: VoicePreferences,
    assistantActionCoordinator: AssistantActionCoordinator,
    onDismiss: () -> Unit,
    cortexViewModel: WorkspaceDetailViewModel = hiltViewModel(),
    sttViewModel: STTViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val normalizedLanguageTag = remember(assistantLanguageTag) {
        CortexAssistantPrompt.normalizeLanguageTag(assistantLanguageTag)
    }
    val assistantLocale = remember(normalizedLanguageTag) {
        Locale.forLanguageTag(normalizedLanguageTag)
    }
    val invocationStartedAt = remember { System.currentTimeMillis() }
    val uiState by cortexViewModel.uiState.collectAsState()
    val messages by cortexViewModel.messages.collectAsState()
    val sttConfig by sttViewModel.currentConfig.collectAsState()
    val isListening by sttViewModel.isListening.collectAsState()
    val transcription by sttViewModel.transcription.collectAsState()
    val pendingTranscription by sttViewModel.pendingTranscription.collectAsState()
    val isTranscribing by sttViewModel.isProcessing.collectAsState()
    val sttError by sttViewModel.error.collectAsState()
    val isSpeaking by textToSpeech.isSpeaking.collectAsState()
    val ttsError by textToSpeech.error.collectAsState()
    val speakResponses by preferences.speakResponses.collectAsState()
    val ttsMode by voicePreferences.ttsMode.collectAsState()
    val assistantModel by preferences.modelKey.collectAsState()
    val pendingAssistantAction by assistantActionCoordinator.pendingAction.collectAsState()

    // Every invocation starts compact and gets a fresh opportunity to listen. These are
    // deliberately not saveable across assistant activity recreation/restoration.
    var expanded by remember { mutableStateOf(false) }
    var initialListeningRequested by remember { mutableStateOf(false) }
    var voiceLoopActive by remember { mutableStateOf(false) }
    var voiceReplyExpected by remember { mutableStateOf(false) }
    var microphonePermissionDenied by remember { mutableStateOf(false) }
    var activityResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var lastHandledResponseKey by remember { mutableStateOf("") }

    DisposableEffect(cortexViewModel, assistantModel, normalizedLanguageTag) {
        cortexViewModel.setAssistantMode(true, assistantModel, normalizedLanguageTag)
        onDispose { cortexViewModel.setAssistantMode(false) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> activityResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> activityResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val visibleMessages = remember(messages, assistantLocale) {
        CortexAssistantResponsePolicy.prepareVisible(
            messages = ContextCompactionPolicy.visibleHistory(
                messages,
                includeInternalActions = true
            ).filter {
                it.role == MessageRole.USER ||
                    (it.role == MessageRole.ASSISTANT && it.toolCalls.isEmpty()) ||
                    CortexAssistantResponsePolicy.isDirectResult(it)
            },
            locale = assistantLocale
        )
    }
    val latestAssistantMessage = visibleMessages.lastOrNull {
        it.role == MessageRole.ASSISTANT && it.content.isNotBlank()
    }
    val latestDirectResult = visibleMessages.lastOrNull(CortexAssistantResponsePolicy::isDirectResult)
    val latestResultMessage = listOfNotNull(latestAssistantMessage, latestDirectResult)
        .maxByOrNull(Message::timestamp)
    val settledResponse = latestResultMessage?.content.orEmpty()
    val settledSpokenResponse = latestResultMessage
        ?.let { CortexAssistantResponsePolicy.spokenResponse(it, assistantLocale) }
        .orEmpty()
    val settledDirectPayload = latestResultMessage
        ?.takeIf(CortexAssistantResponsePolicy::isDirectResult)
        ?.let { CortexAssistantResponsePolicy.payload(it, assistantLocale) }
    val latestResultKey = latestResultMessage?.let { message ->
        "${message.id}:${message.timestamp}:${settledResponse.hashCode()}"
    }
    val displayResponse = uiState.streamingContent
        ?.takeIf { it.isNotBlank() }
        ?: settledResponse
    val error = uiState.error ?: sttError?.takeIf { displayResponse.isBlank() }
        ?: if (microphonePermissionDenied && displayResponse.isBlank()) {
            "Activa el permiso de microfono para hablar con Cortex. Tambien puedes escribir."
        } else {
            null
        }
    val stage = when {
        error != null -> AssistantStage.ERROR
        isListening -> AssistantStage.LISTENING
        isTranscribing -> AssistantStage.TRANSCRIBING
        uiState.isLoading -> AssistantStage.THINKING
        isSpeaking -> AssistantStage.SPEAKING
        displayResponse.isNotBlank() -> AssistantStage.RESULT
        else -> AssistantStage.READY
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
        microphonePermissionDenied = !granted
        if (granted) sttViewModel.startListening()
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) cortexViewModel.onLocationPermissionGranted()
        else cortexViewModel.onLocationPermissionDenied()
    }
    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) cortexViewModel.onCalendarPermissionGranted()
        else cortexViewModel.onCalendarPermissionDenied()
    }
    val assistantActionPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.isNotEmpty() && permissions.values.all { it }) {
            cortexViewModel.onAssistantActionPermissionsGranted()
        } else {
            cortexViewModel.onAssistantActionPermissionsDenied()
        }
    }

    fun requestListening() {
        initialListeningRequested = true
        voiceLoopActive = true
        voiceReplyExpected = true
        textToSpeech.stop()
        sttViewModel.dismissError()
        if (hasRecordPermission) {
            microphonePermissionDenied = false
            sttViewModel.startListening()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun toggleListening() {
        if (isListening) {
            voiceLoopActive = false
            sttViewModel.stopListening()
        } else {
            requestListening()
        }
    }

    fun interceptPendingConfirmation(text: String): Boolean {
        val draft = pendingAssistantAction as? PendingAssistantAction.WhatsAppDraft
            ?: return false
        if (draft.status != AssistantActionStatus.DRAFT) return false
        return when (AssistantConfirmationParser.parse(text)) {
            AssistantConfirmationDecision.CONFIRM -> {
                assistantActionCoordinator.confirmWhatsApp(draft.id)
                cortexViewModel.updateInputText("")
                true
            }
            AssistantConfirmationDecision.CANCEL -> {
                assistantActionCoordinator.cancel(draft.id)
                cortexViewModel.updateInputText("")
                true
            }
            AssistantConfirmationDecision.UNKNOWN -> false
        }
    }

    fun submitText() {
        val text = uiState.inputText.trim()
        if (text.isBlank() || uiState.isLoading) return
        voiceReplyExpected = false
        textToSpeech.stop()
        sttViewModel.dismissError()
        if (!interceptPendingConfirmation(text)) {
            assistantActionCoordinator.reset()
            cortexViewModel.sendMessage()
        }
    }

    LaunchedEffect(
        sttConfig,
        initialListeningRequested,
        activityResumed,
        uiState.isLoading,
        isTranscribing,
        isSpeaking
    ) {
        if (sttConfig == null || initialListeningRequested ||
            !activityResumed || uiState.isLoading || isTranscribing || isSpeaking
        ) return@LaunchedEffect

        // SpeechRecognizer is unreliable if started before the assistant activity reaches RESUMED.
        delay(450)
        if (activityResumed && !uiState.isLoading && !isSpeaking) requestListening()
    }

    LaunchedEffect(pendingTranscription) {
        val accepted = pendingTranscription?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        sttViewModel.acceptTranscription()
        cortexViewModel.updateInputText(accepted)
        if (!interceptPendingConfirmation(accepted)) {
            assistantActionCoordinator.reset()
            cortexViewModel.sendMessage()
        }
    }

    LaunchedEffect(uiState.pendingLocationPermission) {
        if (!uiState.pendingLocationPermission) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            cortexViewModel.onLocationPermissionGranted()
        } else {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(uiState.pendingCalendarPermission) {
        if (!uiState.pendingCalendarPermission) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            cortexViewModel.onCalendarPermissionGranted()
        } else {
            calendarPermission.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    LaunchedEffect(uiState.pendingAssistantActionPermissions) {
        val permissions = uiState.pendingAssistantActionPermissions
        if (permissions.isEmpty()) return@LaunchedEffect
        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            cortexViewModel.onAssistantActionPermissionsGranted()
        } else {
            assistantActionPermission.launch(permissions.toTypedArray())
        }
    }

    LaunchedEffect(pendingAssistantAction?.id, pendingAssistantAction?.expiresAt) {
        val action = pendingAssistantAction ?: return@LaunchedEffect
        val remaining = (action.expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
        delay(remaining)
        assistantActionCoordinator.expire(action.id)
    }

    LaunchedEffect(
        latestResultKey,
        uiState.isLoading,
        speakResponses,
        voiceLoopActive,
        voiceReplyExpected
    ) {
        val response = settledSpokenResponse.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val resultMessage = latestResultMessage ?: return@LaunchedEffect
        if (resultMessage.timestamp < invocationStartedAt) return@LaunchedEffect
        if (uiState.isLoading) return@LaunchedEffect
        val key = latestResultKey ?: return@LaunchedEffect
        if (key == lastHandledResponseKey) return@LaunchedEffect
        lastHandledResponseKey = key

        // A voice-initiated turn should answer by voice. The preference still controls automatic
        // reading for typed turns, while voiceLoopActive captures the assistant interaction model.
        if (ttsMode != AssistantTtsMode.NONE && (speakResponses || voiceReplyExpected)) {
            textToSpeech.speak(response, assistantLocale)
            val playbackStarted = withTimeoutOrNull(2_500) {
                textToSpeech.isSpeaking.first { it }
            } != null
            if (playbackStarted) {
                textToSpeech.isSpeaking.first { !it }
            }
        }
        voiceReplyExpected = false

        if (voiceLoopActive) {
            // Avoid capturing the final syllable of Cortex's own TTS response.
            delay(420)
            if (activityResumed && !uiState.isLoading && !sttViewModel.isListening.value) {
                requestListening()
            }
        }
    }

    BackHandler {
        if (expanded) expanded = false else onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (expanded) 0.18f else 0.06f))
    ) {
        AnimatedContent(
            targetState = expanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            label = "assistant_size"
        ) { isExpanded ->
            if (isExpanded) {
                ExpandedAssistantPanel(
                    cortexName = cortexName,
                    stage = stage,
                    messages = visibleMessages,
                    streamingText = uiState.streamingContent,
                    inputText = uiState.inputText,
                    isListening = isListening,
                    isLoading = uiState.isLoading,
                    isSpeaking = isSpeaking,
                    transcription = transcription,
                    error = error ?: ttsError,
                    onInputChange = cortexViewModel::updateInputText,
                    onSend = ::submitText,
                    onMic = ::toggleListening,
                    onStopSpeaking = textToSpeech::stop,
                    onCollapse = { expanded = false },
                    onDismiss = onDismiss
                )
            } else {
                CompactAssistantOverlay(
                    cortexName = cortexName,
                    stage = stage,
                    response = displayResponse,
                    resultPayload = settledDirectPayload.takeIf {
                        uiState.streamingContent.isNullOrBlank()
                    },
                    transcription = transcription,
                    error = error,
                    isListening = isListening,
                    pendingAction = pendingAssistantAction,
                    actionCoordinator = assistantActionCoordinator,
                    onMic = ::toggleListening,
                    onExpand = { expanded = true },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun CompactAssistantOverlay(
    cortexName: String,
    stage: AssistantStage,
    response: String,
    resultPayload: DirectToolResultPayload?,
    transcription: String,
    error: String?,
    isListening: Boolean,
    pendingAction: PendingAssistantAction?,
    actionCoordinator: AssistantActionCoordinator,
    onMic: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit
) {
    val contentKey = "${response.hashCode()}:${error.hashCode()}:${resultPayload.hashCode()}:${pendingAction?.id}:${pendingAction?.status}"
    val hasPanelContent = response.isNotBlank() || error != null || resultPayload != null || pendingAction != null
    var panelVisible by remember { mutableStateOf(hasPanelContent) }
    LaunchedEffect(contentKey, hasPanelContent) {
        if (hasPanelContent) panelVisible = true
    }

    BoxWithConstraints(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp)
            .fillMaxWidth()
    ) {
        val panelMaxHeight = minOf(480.dp, maxHeight * 0.56f)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = hasPanelContent && panelVisible,
                enter = fadeIn(tween(180)) + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut(tween(140)) + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                AssistantResponsePanel(
                    cortexName = cortexName,
                    response = response,
                    resultPayload = resultPayload,
                    pendingAction = pendingAction,
                    error = error,
                    dimmed = isListening,
                    maxHeight = panelMaxHeight,
                    actionCoordinator = actionCoordinator,
                    onExpand = onExpand,
                    onHide = { panelVisible = false }
                )
            }

            AssistantPill(
                cortexName = cortexName,
                stage = stage,
                transcription = transcription,
                error = error,
                isListening = isListening,
                hasPanelContent = hasPanelContent,
                panelVisible = panelVisible,
                onShowPanel = { panelVisible = true },
                onMic = onMic,
                onExpand = onExpand,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun AssistantResponsePanel(
    cortexName: String,
    response: String,
    resultPayload: DirectToolResultPayload?,
    pendingAction: PendingAssistantAction?,
    error: String?,
    dimmed: Boolean,
    maxHeight: androidx.compose.ui.unit.Dp,
    actionCoordinator: AssistantActionCoordinator,
    onExpand: () -> Unit,
    onHide: () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val showJumpToBottom by remember {
        derivedStateOf { scrollState.maxValue - scrollState.value > 32 }
    }
    LaunchedEffect(response) {
        if (!showJumpToBottom) {
            delay(16)
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
    var panelDrag by remember { mutableStateOf(0f) }
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .animateContentSize()
            .graphicsLayer { alpha = if (dimmed) 0.72f else 1f }
            .semantics {
                paneTitle = "Respuesta de $cortexName"
                liveRegion = LiveRegionMode.Polite
            },
        shape = RoundedCornerShape(28.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            Row(
                modifier = Modifier.pointerInput(onHide) {
                    detectVerticalDragGestures(
                        onDragStart = { panelDrag = 0f },
                        onVerticalDrag = { _, amount -> panelDrag += amount },
                        onDragEnd = {
                            if (panelDrag > 56.dp.toPx()) onHide()
                            panelDrag = 0f
                        },
                        onDragCancel = { panelDrag = 0f }
                    )
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cortexName,
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onExpand, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.OpenInFull,
                        contentDescription = "Abrir conversación",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onHide, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Ocultar respuesta",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            when {
                pendingAction != null -> PendingAssistantActionCard(
                    action = pendingAction,
                    coordinator = actionCoordinator
                )
                resultPayload != null -> DirectToolResultCard(
                    payload = resultPayload,
                    modifier = Modifier.fillMaxWidth()
                )
                error != null -> Text(
                    text = error,
                    color = Color(0xFFFFB4AB),
                    style = MaterialTheme.typography.bodyMedium
                )
                response.isNotBlank() -> LinkableText(
                    text = response,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White.copy(alpha = 0.90f)
                    ),
                    linkColor = CortexColors.Blue
                )
            }
            }
            if (showJumpToBottom) {
                TextButton(
                    onClick = { coroutineScope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text("Ir al final")
                }
            }
        }
    }
}

@Composable
private fun AssistantPill(
    cortexName: String,
    stage: AssistantStage,
    transcription: String,
    error: String?,
    isListening: Boolean,
    hasPanelContent: Boolean,
    panelVisible: Boolean,
    onShowPanel: () -> Unit,
    onMic: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                paneTitle = "Asistente $cortexName"
                stateDescription = accessibleStageDescription(stage, isListening, error)
            },
        shape = RoundedCornerShape(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CortexOrb(stage = stage, modifier = Modifier.size(58.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (hasPanelContent && !panelVisible) onShowPanel() else onExpand()
                    },
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stageLabel(stage, cortexName),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Text(
                    text = when {
                        isListening && transcription.isNotBlank() -> transcription
                        isListening -> "Te escucho…"
                        stage == AssistantStage.TRANSCRIBING -> "Enviando audio al servidor…"
                        hasPanelContent && !panelVisible -> "Toca para ver la respuesta"
                        else -> "Habla o abre la conversación"
                    },
                    color = Color.White.copy(alpha = 0.66f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onExpand, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.OpenInFull,
                    contentDescription = "Abrir conversación",
                    tint = Color.White.copy(alpha = 0.76f),
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(onClick = onMic, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isListening) "Dejar de escuchar" else "Hablar",
                    tint = if (isListening) CortexColors.Pink else Color.White
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White.copy(0.70f))
            }
        }
    }
}

@Composable
private fun PendingAssistantActionCard(
    action: PendingAssistantAction,
    coordinator: AssistantActionCoordinator
) {
    when (action) {
        is PendingAssistantAction.WhatsAppDraft -> WhatsAppDraftCard(action, coordinator)
        is PendingAssistantAction.ContactSelection -> ContactSelectionCard(action, coordinator)
    }
}

@Composable
private fun WhatsAppDraftCard(
    draft: PendingAssistantAction.WhatsAppDraft,
    coordinator: AssistantActionCoordinator
) {
    var message by remember(draft.id, draft.message) { mutableStateOf(draft.message) }
    val active = draft.status == AssistantActionStatus.DRAFT
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (draft.contact.photoUri != null) {
                AsyncImage(
                    model = draft.contact.photoUri,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(CortexColors.Mint, CortexColors.Blue))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        draft.contact.displayName.take(1).uppercase(),
                        color = Color(0xFF071A17),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    draft.contact.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    listOfNotNull(draft.contact.label, draft.contact.phoneNumber).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        TextField(
            value = message,
            onValueChange = {
                message = it
                coordinator.updateWhatsAppMessage(draft.id, it)
            },
            enabled = active,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mensaje de WhatsApp") },
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.055f),
                disabledContainerColor = Color.White.copy(alpha = 0.04f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.65f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
        if (active) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { coordinator.cancel(draft.id) },
                    modifier = Modifier.weight(1f)
                ) { Text("Cancelar") }
                Button(
                    onClick = { coordinator.confirmWhatsApp(draft.id, message) },
                    enabled = message.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Enviar a WhatsApp") }
            }
            Text(
                "WhatsApp se abrirá con el texto preparado; el envío final se confirma allí.",
                color = Color.White.copy(alpha = 0.56f),
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Text(
                text = when (draft.status) {
                    AssistantActionStatus.HANDED_OFF -> "Mensaje entregado a WhatsApp para revisión."
                    AssistantActionStatus.CANCELLED -> "Borrador cancelado."
                    AssistantActionStatus.EXPIRED -> "El borrador expiró."
                    AssistantActionStatus.FAILED -> draft.failureMessage ?: "No se pudo abrir WhatsApp."
                    AssistantActionStatus.DRAFT -> ""
                },
                color = if (draft.status == AssistantActionStatus.FAILED) Color(0xFFFFB4AB)
                else CortexColors.Mint,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ContactSelectionCard(
    selection: PendingAssistantAction.ContactSelection,
    coordinator: AssistantActionCoordinator
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (selection.purpose == ContactActionPurpose.CALL) "¿A qué número llamo?"
            else "¿A qué contacto de WhatsApp?",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (selection.status == AssistantActionStatus.DRAFT) {
            selection.candidates.forEach { candidate ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { coordinator.selectContact(selection.id, candidate.id) }
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(candidate.displayName, color = Color.White, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(candidate.label, candidate.phoneNumber).joinToString(" · "),
                            color = Color.White.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { coordinator.cancel(selection.id) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancelar") }
        } else {
            Text(
                if (selection.status == AssistantActionStatus.EXPIRED) "La selección expiró."
                else "Selección cancelada.",
                color = Color.White.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun ExpandedAssistantPanel(
    cortexName: String,
    stage: AssistantStage,
    messages: List<Message>,
    streamingText: String?,
    inputText: String,
    isListening: Boolean,
    isLoading: Boolean,
    isSpeaking: Boolean,
    transcription: String,
    error: String?,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onStopSpeaking: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(messages.size, streamingText) {
        val extra = if (streamingText.isNullOrBlank()) 0 else 1
        val target = messages.size + extra - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }

    GlassPanel(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp)
            .padding(bottom = maxOf(imeBottom, navigationBottom) + 8.dp)
            .fillMaxSize()
            .semantics {
                paneTitle = "Conversación con $cortexName"
                stateDescription = accessibleStageDescription(stage, isListening, error)
            },
        shape = RoundedCornerShape(34.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CortexOrb(stage = stage, modifier = Modifier.size(38.dp))
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(
                        cortexName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stageLabel(stage, cortexName),
                        color = CortexColors.Mint.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                if (isSpeaking) {
                    IconButton(onClick = onStopSpeaking) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Detener voz",
                            tint = Color.White
                        )
                    }
                }
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Contraer", tint = Color.White.copy(0.75f))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White.copy(0.75f))
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CortexTheme.colors.outline)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp,
                    vertical = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty() && streamingText.isNullOrBlank()) {
                    item {
                        EmptyAssistantConversation(cortexName)
                    }
                }
                items(messages, key = { "${it.id}-${it.timestamp}-${it.role}" }) { message ->
                    AssistantMessage(message)
                }
                if (!streamingText.isNullOrBlank()) {
                    item(key = "streaming") {
                        AssistantMessage(
                            Message(role = MessageRole.ASSISTANT, content = streamingText)
                        )
                    }
                } else if (isLoading || stage == AssistantStage.TRANSCRIBING) {
                    item(key = "thinking") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CortexColors.Mint,
                                strokeWidth = 2.dp
                            )
                            Text(
                                if (stage == AssistantStage.TRANSCRIBING) "Transcribiendo…" else "Pensando…",
                                color = Color.White.copy(alpha = 0.68f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isListening || stage == AssistantStage.TRANSCRIBING || error != null
            ) {
                val notice = when {
                    error != null -> error
                    stage == AssistantStage.TRANSCRIBING -> "Procesando tu voz en el servidor…"
                    transcription.isNotBlank() -> transcription
                    else -> "Te escucho…"
                }
                Text(
                    text = notice,
                    color = if (error != null) Color(0xFFFFB4AB) else CortexColors.Mint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        .semantics {
                            liveRegion = if (error != null) {
                                LiveRegionMode.Assertive
                            } else {
                                LiveRegionMode.Polite
                            }
                        }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onMic,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isListening) CortexColors.Pink.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.08f),
                            CircleShape
                        )
                ) {
                    Icon(
                        if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Dejar de escuchar" else "Hablar",
                        tint = if (isListening) CortexColors.Pink else Color.White
                    )
                }
                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Mensaje para $cortexName" },
                    placeholder = { Text("Escribe a $cortexName…", color = Color.White.copy(0.46f)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.055f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = CortexColors.Mint
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        onSend()
                        focusManager.clearFocus()
                    })
                )
                IconButton(
                    onClick = {
                        onSend()
                        focusManager.clearFocus()
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (inputText.isNotBlank() && !isLoading) CortexColors.Violet
                            else Color.White.copy(alpha = 0.06f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = if (inputText.isNotBlank() && !isLoading) Color.White
                        else Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

private fun accessibleStageDescription(
    stage: AssistantStage,
    isListening: Boolean,
    error: String?
): String = when {
    error != null -> "Error: $error"
    isListening -> "Escuchando"
    stage == AssistantStage.TRANSCRIBING -> "Transcribiendo el audio"
    stage == AssistantStage.THINKING -> "Procesando la solicitud"
    stage == AssistantStage.SPEAKING -> "Reproduciendo la respuesta"
    stage == AssistantStage.RESULT -> "Respuesta disponible"
    else -> "Listo"
}

@Composable
private fun AssistantMessage(message: Message) {
    val locale = Locale.getDefault()
    val directPayloads = remember(message.toolResults, locale) {
        CortexAssistantResponsePolicy.payloads(message, locale)
    }
    if (directPayloads.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            directPayloads.forEach { payload ->
                DirectToolResultCard(payload = payload, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }

    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.84f else 0.94f)
                .clip(
                    RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                        bottomStart = if (isUser) 22.dp else 7.dp,
                        bottomEnd = if (isUser) 7.dp else 22.dp
                    )
                )
                .background(
                    if (isUser) Brush.linearGradient(
                        listOf(CortexColors.Violet.copy(0.86f), CortexColors.Blue.copy(0.66f))
                    ) else Brush.linearGradient(
                        listOf(Color.White.copy(0.10f), Color.White.copy(0.055f))
                    )
                )
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            if (isUser) {
                Text(
                    text = message.content,
                    color = Color.White.copy(alpha = 0.98f),
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 23.sp
                )
            } else {
                LinkableText(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White.copy(alpha = 0.88f),
                        lineHeight = 23.sp
                    ),
                    linkColor = CortexColors.Blue
                )
            }
        }
    }
}

@Composable
private fun EmptyAssistantConversation(cortexName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CortexOrb(stage = AssistantStage.READY, modifier = Modifier.size(78.dp))
        Text(
            "Hola, soy $cortexName",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Puedes hablarme o escribir. Usaré el modelo de voz que configuraste.",
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CortexOrb(stage: AssistantStage, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cortex_orb")
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (stage) {
                    AssistantStage.LISTENING -> 4_800
                    AssistantStage.TRANSCRIBING -> 3_200
                    AssistantStage.THINKING -> 2_800
                    AssistantStage.SPEAKING -> 3_600
                    AssistantStage.RESULT -> 8_000
                    else -> 14_000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = if (stage == AssistantStage.LISTENING) 0.96f else 0.985f,
        targetValue = if (stage == AssistantStage.LISTENING) 1.06f else 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (stage == AssistantStage.LISTENING) 620 else 1_300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val visiblePulse = if (animationsEnabled) pulse else 1f
    val visiblePhase = if (animationsEnabled) rotation else 28f
    val activityColor = when (stage) {
        AssistantStage.LISTENING -> CortexColors.Mint
        AssistantStage.TRANSCRIBING -> CortexColors.Blue
        AssistantStage.THINKING -> CortexColors.Blue
        AssistantStage.SPEAKING -> CortexColors.Pink
        AssistantStage.ERROR -> Color(0xFFFF817A)
        else -> CortexColors.Violet
    }
    Box(modifier = modifier.graphicsLayer { scaleX = visiblePulse; scaleY = visiblePulse }) {
        CortexBubbleMark(
            modifier = Modifier.fillMaxSize(),
            phaseDegrees = visiblePhase,
            activityColor = activityColor,
            error = stage == AssistantStage.ERROR
        )
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier,
    shape: RoundedCornerShape,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .cortexGlass(shape = shape, tint = CortexTheme.colors.glassStrong)
    ) {
        content()
    }
}

private fun stageLabel(stage: AssistantStage, cortexName: String): String = when (stage) {
    AssistantStage.READY -> "$cortexName está listo"
    AssistantStage.LISTENING -> "Te escucho"
    AssistantStage.TRANSCRIBING -> "Transcribiendo"
    AssistantStage.THINKING -> "Pensando"
    AssistantStage.SPEAKING -> "Respondiendo"
    AssistantStage.RESULT -> "Respuesta de $cortexName"
    AssistantStage.ERROR -> "Necesito tu atención"
}

@Composable
fun CortexAssistantPreview(modifier: Modifier = Modifier) {
    CortexOrb(stage = AssistantStage.READY, modifier = modifier)
}
