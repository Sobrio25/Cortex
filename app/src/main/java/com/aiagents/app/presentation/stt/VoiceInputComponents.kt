package com.aiagents.app.presentation.stt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aiagents.app.domain.service.STTService

@Composable
fun VoiceInputButton(
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    enabled: Boolean = true
) {
    Box(contentAlignment = Alignment.Center) {
        // Animación de ondas cuando está escuchando
        if (isListening) {
            VoiceWavesAnimation()
        }

        FilledIconButton(
            onClick = {
                if (isListening) {
                    onStopListening()
                } else {
                    onStartListening()
                }
            },
            enabled = enabled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isListening) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.primary,
                contentColor = if (isListening) 
                    MaterialTheme.colorScheme.onError 
                else 
                    MaterialTheme.colorScheme.onPrimary
            ),
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Detener" else "Hablar"
            )
        }
    }
}

@Composable
fun VoiceWavesAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "waves")
    
    val scales = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, delayMillis = index * 200),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_$index"
        )
    }
    
    val alphas = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, delayMillis = index * 200),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha_$index"
        )
    }

    Box(contentAlignment = Alignment.Center) {
        scales.forEachIndexed { index, scale ->
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(scale.value)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alphas[index].value),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun VoiceInputOverlay(
    isVisible: Boolean,
    transcription: String,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error,
                                shape = CircleShape
                            )
                    )
                    Text(
                        "Escuchando...",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (transcription.isNotBlank()) {
                    Text(
                        text = transcription,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "Habla ahora",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
fun TranscriptionPreview(
    text: String,
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Transcripción:",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Descartar")
                    }
                    Button(onClick = onAccept) {
                        Text("Usar")
                    }
                }
            }
        }
    }
}
