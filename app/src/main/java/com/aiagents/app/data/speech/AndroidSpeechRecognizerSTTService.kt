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
import java.util.Locale

/**
 * STT service using Android's built-in SpeechRecognizer.
 * Free and API-keyless. When [onDeviceOnly] is true it starts with the local recognizer.
 * If that recognizer reports a missing language pack, [fallbackToSystemRecognizer] keeps the
 * current interaction working with Android's regular recognizer while scheduling the pack.
 * Does NOT extend BaseSTTService because SpeechRecognizer manages its own audio.
 */
class AndroidSpeechRecognizerSTTService(
    private val context: Context,
    private val onDeviceOnly: Boolean = false,
    private val fallbackToSystemRecognizer: Boolean = true
) : STTService {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _transcription = MutableStateFlow("")
    override val transcription: StateFlow<String> = _transcription.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognitionGeneration = 0

    private fun mapLanguageCode(language: String): String {
        val normalized = language.lowercase()
        val deviceLocale = Locale.getDefault()
        if (normalized == deviceLocale.language.lowercase()) {
            return deviceLocale.toLanguageTag()
        }
        return when (normalized) {
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
                _isListening.value = false
                return@withContext
            }

            _transcription.value = ""
            _isListening.value = true
            recognitionGeneration++
            destroyRecognizer(waitForRelease = true)
            beginRecognition(language, useOnDevice = onDeviceOnly)
        }
    }

    private suspend fun beginRecognition(language: String, useOnDevice: Boolean) {
        val generation = ++recognitionGeneration
        val recognizer = try {
            if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear el reconocedor", e)
            _transcription.value = "Error: Reconocimiento de voz no disponible en este dispositivo"
            _isListening.value = false
            return
        }
        speechRecognizer = recognizer
        val recognitionIntent = createRecognitionIntent(language, preferOffline = useOnDevice)

        recognizer.setRecognitionListener(object : RecognitionListener {
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
                if (generation != recognitionGeneration) return
                val languageMissing = error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                if (useOnDevice && fallbackToSystemRecognizer && languageMissing) {
                    retryWithSystemRecognizer(generation, language, recognitionIntent)
                    return
                }

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
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Idioma no disponible"
                    else -> "Error desconocido ($error)"
                }
                Log.e(TAG, "Error: $errorMessage")
                if (_transcription.value.isBlank()) {
                    _transcription.value = "Error: $errorMessage"
                }
                _isListening.value = false
            }

            override fun onResults(results: Bundle?) {
                if (generation != recognitionGeneration) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalText = matches?.firstOrNull() ?: ""
                if (finalText.isNotBlank()) {
                    _transcription.value = finalText
                }
                _isListening.value = false
                Log.d(TAG, "Resultado final: $finalText")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (generation != recognitionGeneration) return
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = partial?.firstOrNull() ?: ""
                if (partialText.isNotBlank()) {
                    _transcription.value = partialText
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer.startListening(recognitionIntent)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar el reconocedor", e)
            if (generation == recognitionGeneration) {
                _transcription.value = "Error: No se pudo iniciar el reconocimiento de voz"
                _isListening.value = false
            }
        }
    }

    private fun createRecognitionIntent(language: String, preferOffline: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val langCode = mapLanguageCode(language)
            if (langCode.isNotBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
    }

    private fun retryWithSystemRecognizer(
        generation: Int,
        language: String,
        localIntent: Intent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                speechRecognizer?.triggerModelDownload(localIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Android no acepto la descarga automatica del idioma", e)
            }
        }
        serviceScope.launch {
            if (generation != recognitionGeneration || !_isListening.value) return@launch
            Log.i(TAG, "Idioma local ausente; reintentando con el reconocedor del sistema")
            recognitionGeneration++
            destroyRecognizer(waitForRelease = true)
            if (!_isListening.value) return@launch
            _transcription.value = ""
            beginRecognition(language, useOnDevice = false)
        }
    }

    private suspend fun destroyRecognizer(waitForRelease: Boolean) {
        speechRecognizer?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo liberar el reconocedor anterior", e)
            }
            speechRecognizer = null
            if (waitForRelease) delay(RECOGNIZER_RELEASE_DELAY_MS)
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
        recognitionGeneration++
        _isListening.value = false
        serviceScope.launch(Dispatchers.Main) {
            destroyRecognizer(waitForRelease = false)
            serviceScope.cancel()
        }
    }

    companion object {
        private const val TAG = "AndroidSpeechSTT"
        private const val RECOGNIZER_RELEASE_DELAY_MS = 150L

        fun isOnDeviceRecognitionAvailable(context: Context): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        }

        fun isSystemRecognitionAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)
    }
}
