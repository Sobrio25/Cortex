package com.aiagents.app.data.speech

import android.content.Context
import android.content.Intent
import android.os.Build
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
 * Free and API-keyless. When [onDeviceOnly] is true it never selects the
 * network recognizer and is therefore suitable for the Cortex assistant.
 * Does NOT extend BaseSTTService because SpeechRecognizer manages its own audio.
 */
class AndroidSpeechRecognizerSTTService(
    private val context: Context,
    private val onDeviceOnly: Boolean = false
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
            val isAvailable = if (onDeviceOnly) {
                isOnDeviceRecognitionAvailable(context)
            } else {
                SpeechRecognizer.isRecognitionAvailable(context)
            }
            if (!isAvailable) {
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
            speechRecognizer = if (onDeviceOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

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
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Idioma no compatible"
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "Idioma no descargado; instala el paquete de Android o el respaldo Vosk"
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
                if (onDeviceOnly) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            speechRecognizer?.startListening(intent)
        }
    }

    override suspend fun stopListening() {
        withContext(Dispatchers.Main) {
            if (!_isListening.value) return@withContext
            try {
                // Let the recognizer produce its final result. onResults/onError owns the
                // isListening transition; cancelling here would discard the last phrase.
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener", e)
                _isListening.value = false
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

        fun isOnDeviceRecognitionAvailable(context: Context): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        }
    }
}
