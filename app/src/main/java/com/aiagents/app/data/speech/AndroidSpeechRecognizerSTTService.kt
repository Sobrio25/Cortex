package com.aiagents.app.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.aiagents.app.domain.service.STTService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * STT service using Android's built-in SpeechRecognizer.
 * Free, no API key required. Uses Google's servers when online.
 * Does NOT extend BaseSTTService because SpeechRecognizer manages its own audio.
 */
class AndroidSpeechRecognizerSTTService(
    private val context: Context
) : STTService {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _transcription = MutableStateFlow("")
    override val transcription: StateFlow<String> = _transcription.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun mapLanguageCode(language: String): String {
        return when (language.lowercase()) {
            "es" -> "es-ES"
            "en" -> "en-US"
            "pt" -> "pt-BR"
            "fr" -> "fr-FR"
            "de" -> "de-DE"
            "it" -> "it-IT"
            "auto" -> "" // let the system decide
            else -> language
        }
    }

    override suspend fun startListening(language: String) {
        withContext(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _transcription.value = "Error: Reconocimiento de voz no disponible en este dispositivo"
                return@withContext
            }

            _transcription.value = ""
            _isListening.value = true

            // Destroy previous instance and wait for the system to release it
            speechRecognizer?.let {
                it.stopListening()
                it.cancel()
                it.destroy()
                speechRecognizer = null
                delay(150)
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Listo para escuchar")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Inicio de habla detectado")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Fin de habla detectado")
                }

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                        SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso denegado"
                        SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No se reconocio el habla"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                        SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout - no se detecto habla"
                        else -> "Error desconocido ($error)"
                    }
                    Log.e(TAG, "Error: $errorMessage")
                    if (_transcription.value.isBlank()) {
                        _transcription.value = "Error: $errorMessage"
                    }
                    _isListening.value = false
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val finalText = matches?.firstOrNull() ?: ""
                    if (finalText.isNotBlank()) {
                        _transcription.value = finalText
                    }
                    _isListening.value = false
                    Log.d(TAG, "Resultado final: $finalText")
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = partial?.firstOrNull() ?: ""
                    if (partialText.isNotBlank()) {
                        _transcription.value = partialText
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val langCode = mapLanguageCode(language)
                if (langCode.isNotBlank()) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
                }
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
        }
    }

    override suspend fun stopListening() {
        withContext(Dispatchers.Main) {
            _isListening.value = false
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener", e)
            }
        }
    }

    override suspend fun transcribeAudio(audioData: ByteArray): Result<String> {
        // AndroidSpeechRecognizer does not support offline audio transcription
        return Result.failure(UnsupportedOperationException("AndroidSpeechRecognizer no soporta transcripcion de audio pregrabado"))
    }

    override fun release() {
        serviceScope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error al liberar", e)
            }
            speechRecognizer = null
        }
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AndroidSpeechSTT"
    }
}
