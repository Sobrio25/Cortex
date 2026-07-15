package com.aiagents.app.presentation.assistant

import android.Manifest
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.data.repository.ContextCompactionPolicy
import com.aiagents.app.data.local.AssistantPreferences
import com.aiagents.app.data.speech.AndroidTextToSpeechManager
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.presentation.stt.STTViewModel
import com.aiagents.app.presentation.workspace_detail.WorkspaceDetailViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private val GlassBackground = Color(0xD61A1821)
private val GlassHighlight = Color(0x34FFFFFF)
private val GlassOutline = Color(0x4DFFFFFF)
private val CortexViolet = Color(0xFF9C7CFF)
private val CortexBlue = Color(0xFF56C7FF)
private val CortexPink = Color(0xFFFF72C6)
private val CortexMint = Color(0xFF62F5D2)

private enum class AssistantStage {
    READY,
    LISTENING,
    THINKING,
    SPEAKING,
    RESULT,
    ERROR
}

@Composable
fun CortexAssistantScreen(
    workspaceId: Long,
    cortexName: String,
    textToSpeech: AndroidTextToSpeechManager,
    preferences: AssistantPreferences,
    onDismiss: () -> Unit,
    cortexViewModel: WorkspaceDetailViewModel = hiltViewModel(),
    sttViewModel: STTViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by cortexViewModel.uiState.collectAsState()
    val messages by cortexViewModel.messages.collectAsState()
    val sttSettings by sttViewModel.currentSettings.collectAsState()
    val isListening by sttViewModel.isListening.collectAsState()
    val transcription by sttViewModel.transcription.collectAsState()
    val pendingTranscription by sttViewModel.pendingTranscription.collectAsState()
    val sttError by sttViewModel.error.collectAsState()
    val isSpeaking by textToSpeech.isSpeaking.collectAsState()
    val ttsError by textToSpeech.error.collectAsState()
    val autoListen by preferences.autoListen.collectAsState()
    val speakResponses by preferences.speakResponses.collectAsState()

    // Every invocation starts compact and gets a fresh opportunity to listen. These are
    // deliberately not saveable across assistant activity recreation/restoration.
    var expanded by remember { mutableStateOf(false) }
    var initialListeningRequested by remember { mutableStateOf(false) }
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var lastSpokenKey by remember { mutableStateOf("") }

    val visibleMessages = remember(messages) {
        ContextCompactionPolicy.visibleHistory(messages).filter {
            it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT
        }
    }
    val latestAssistantMessage = visibleMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
    val displayResponse = uiState.streamingContent
        ?.takeIf { it.isNotBlank() }
        ?: latestAssistantMessage?.content.orEmpty()
    val error = uiState.error ?: sttError?.takeIf { displayResponse.isBlank() }
    val stage = when {
        error != null -> AssistantStage.ERROR
        isListening -> AssistantStage.LISTENING
        uiState.isLoading -> AssistantStage.THINKING
        isSpeaking -> AssistantStage.SPEAKING
        displayResponse.isNotBlank() -> AssistantStage.RESULT
        else -> AssistantStage.READY
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
        if (granted) sttViewModel.startListening()
    }

    fun requestListening() {
        textToSpeech.stop()
        sttViewModel.dismissError()
        if (hasRecordPermission) {
            sttViewModel.startListening()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun submitText() {
        if (uiState.inputText.isBlank() || uiState.isLoading) return
        textToSpeech.stop()
        sttViewModel.dismissError()
        cortexViewModel.sendMessage()
    }

    LaunchedEffect(workspaceId) {
        sttViewModel.prepareOfflineAssistant(
            workspaceId = workspaceId,
            language = Locale.getDefault().language.ifBlank { "es" }
        )
    }

    LaunchedEffect(sttSettings, autoListen, initialListeningRequested) {
        if (sttSettings != null && autoListen && !initialListeningRequested) {
            initialListeningRequested = true
            delay(280)
            requestListening()
        }
    }

    LaunchedEffect(pendingTranscription) {
        val accepted = pendingTranscription?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        sttViewModel.acceptTranscription()
        cortexViewModel.updateInputText(accepted)
        cortexViewModel.sendMessage()
    }

    LaunchedEffect(
        latestAssistantMessage?.id,
        latestAssistantMessage?.content,
        uiState.isLoading,
        speakResponses
    ) {
        val response = latestAssistantMessage?.content?.takeIf { it.isNotBlank() }
            ?: return@LaunchedEffect
        if (uiState.isLoading) return@LaunchedEffect
        val key = "${latestAssistantMessage.id}:${latestAssistantMessage.timestamp}:${response.hashCode()}"
        if (speakResponses && key != lastSpokenKey) {
            lastSpokenKey = key
            textToSpeech.speak(response, Locale.getDefault())
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
                    onMic = {
                        if (isListening) sttViewModel.stopListening() else requestListening()
                    },
                    onStopSpeaking = textToSpeech::stop,
                    onCollapse = { expanded = false },
                    onDismiss = onDismiss
                )
            } else {
                CompactAssistantBubble(
                    cortexName = cortexName,
                    stage = stage,
                    response = displayResponse,
                    transcription = transcription,
                    error = error,
                    isListening = isListening,
                    onMic = {
                        if (isListening) sttViewModel.stopListening() else requestListening()
                    },
                    onExpand = { expanded = true },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun CompactAssistantBubble(
    cortexName: String,
    stage: AssistantStage,
    response: String,
    transcription: String,
    error: String?,
    isListening: Boolean,
    onMic: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    GlassPanel(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp)
            .fillMaxWidth()
            .animateContentSize(),
        shape = shape
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CortexOrb(stage = stage, modifier = Modifier.size(54.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onExpand),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stageLabel(stage, cortexName),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    val detail = when {
                        error != null -> error
                        isListening && transcription.isNotBlank() -> transcription
                        response.isNotBlank() -> response
                        else -> "Habla o toca para escribir"
                    }
                    Text(
                        text = detail,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (response.isBlank()) 1 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onMic, modifier = Modifier.size(42.dp)) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Dejar de escuchar" else "Hablar",
                        tint = if (isListening) CortexPink else Color.White
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White.copy(0.7f))
                }
            }

            AnimatedVisibility(
                visible = response.isNotBlank(),
                enter = fadeIn() + scaleIn(initialScale = 0.98f),
                exit = fadeOut() + scaleOut(targetScale = 0.98f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.055f))
                        .clickable(onClick = onExpand)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.OpenInFull,
                        contentDescription = null,
                        tint = CortexBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Abrir conversación",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 7.dp)
                    )
                }
            }
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
            .fillMaxSize(),
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
                        color = CortexMint.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (isSpeaking) {
                    IconButton(onClick = onStopSpeaking) {
                        Icon(Icons.Default.VolumeOff, contentDescription = "Silenciar", tint = Color.White)
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
                    .background(GlassOutline)
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
                } else if (isLoading) {
                    item(key = "thinking") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CortexMint,
                                strokeWidth = 2.dp
                            )
                            Text("Pensando…", color = Color.White.copy(alpha = 0.68f))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isListening || error != null) {
                val notice = when {
                    error != null -> error
                    transcription.isNotBlank() -> transcription
                    else -> "Te escucho…"
                }
                Text(
                    text = notice,
                    color = if (error != null) Color(0xFFFFB4AB) else CortexMint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
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
                            if (isListening) CortexPink.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.08f),
                            CircleShape
                        )
                ) {
                    Icon(
                        if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Dejar de escuchar" else "Hablar",
                        tint = if (isListening) CortexPink else Color.White
                    )
                }
                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
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
                        cursorColor = CortexMint
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
                            if (inputText.isNotBlank() && !isLoading) CortexViolet
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

@Composable
private fun AssistantMessage(message: Message) {
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
                        listOf(CortexViolet.copy(0.86f), CortexBlue.copy(0.66f))
                    ) else Brush.linearGradient(
                        listOf(Color.White.copy(0.10f), Color.White.copy(0.055f))
                    )
                )
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.content,
                color = Color.White.copy(alpha = if (isUser) 0.98f else 0.88f),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 23.sp
            )
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
            "Puedes hablarme o escribir. Tu voz se procesa en este dispositivo y no necesita API keys.",
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CortexOrb(stage: AssistantStage, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cortex_orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (stage) {
                    AssistantStage.LISTENING -> 1_600
                    AssistantStage.THINKING -> 2_100
                    else -> 5_200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (stage == AssistantStage.LISTENING) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (stage == AssistantStage.LISTENING) 620 else 1_300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    Box(modifier = modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(CortexViolet, CortexBlue, CortexMint, CortexPink, CortexViolet),
                    center = center
                ),
                radius = radius
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3A3350), Color(0xFF17131F)),
                    center = center + Offset(-radius * 0.18f, -radius * 0.2f),
                    radius = radius * 0.82f
                ),
                radius = radius * 0.72f
            )
            if (stage == AssistantStage.LISTENING || stage == AssistantStage.THINKING) {
                drawArc(
                    color = Color.White.copy(alpha = 0.82f),
                    startAngle = 8f,
                    sweepAngle = 72f,
                    useCenter = false,
                    topLeft = Offset(radius * 0.10f, radius * 0.10f),
                    size = androidx.compose.ui.geometry.Size(radius * 1.8f, radius * 1.8f),
                    style = Stroke(width = radius * 0.08f, cap = StrokeCap.Round)
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(0.34f), Color.Transparent),
                    center = center + Offset(-radius * 0.3f, -radius * 0.34f),
                    radius = radius * 0.7f
                ),
                radius = radius * 0.72f
            )
        }
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
            .clip(shape)
            .background(GlassBackground)
            .background(
                Brush.linearGradient(
                    colors = listOf(GlassHighlight, Color.Transparent, CortexViolet.copy(0.08f)),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(0.42f), CortexBlue.copy(0.28f), Color.White.copy(0.10f))
                ),
                shape = shape
            )
    ) {
        content()
    }
}

private fun stageLabel(stage: AssistantStage, cortexName: String): String = when (stage) {
    AssistantStage.READY -> "$cortexName está listo"
    AssistantStage.LISTENING -> "Te escucho"
    AssistantStage.THINKING -> "Pensando"
    AssistantStage.SPEAKING -> "Respondiendo"
    AssistantStage.RESULT -> "Respuesta de $cortexName"
    AssistantStage.ERROR -> "Necesito tu atención"
}

@Composable
fun CortexAssistantPreview(modifier: Modifier = Modifier) {
    CortexOrb(stage = AssistantStage.READY, modifier = modifier)
}
