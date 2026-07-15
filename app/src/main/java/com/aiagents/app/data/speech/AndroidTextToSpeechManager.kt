package com.aiagents.app.data.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API-keyless speech synthesis backed by an embedded Android TTS voice.
 * Network-only voices are deliberately excluded so assistant replies stay local.
 */
@Singleton
class AndroidTextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _offlineVoiceAvailable = MutableStateFlow(false)
    val offlineVoiceAvailable: StateFlow<Boolean> = _offlineVoiceAvailable.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var engine: TextToSpeech? = null
    private var selectedLocale: Locale = Locale.getDefault()
    private var pendingSpeech: Pair<String, Locale>? = null
    private var finalUtteranceId: String? = null

    init {
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureVoice(selectedLocale)
                pendingSpeech?.also { (text, locale) ->
                    pendingSpeech = null
                    speak(text, locale)
                }
            } else {
                _error.value = "No se pudo iniciar la voz de Android"
            }
        }
    }

    @Synchronized
    fun speak(text: String, locale: Locale = Locale.getDefault()) {
        if (text.isBlank()) return
        val tts = engine
        if (tts == null || !_isReady.value) {
            selectedLocale = locale
            pendingSpeech = text to locale
            return
        }

        if (!sameLanguage(selectedLocale, locale)) {
            configureVoice(locale)
        }
        if (!_offlineVoiceAvailable.value) {
            _error.value = "Descarga una voz sin conexión para escuchar a Cortex"
            return
        }

        val maxLength = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(500)
        val chunks = SpokenTextFormatter.chunk(text, maxLength)
        if (chunks.isEmpty()) return

        tts.stop()
        finalUtteranceId = null
        chunks.forEachIndexed { index, chunk ->
            val id = "cortex-${UUID.randomUUID()}-$index"
            if (index == chunks.lastIndex) finalUtteranceId = id
            tts.speak(
                chunk,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                id
            )
        }
    }

    fun stop() {
        pendingSpeech = null
        engine?.stop()
        _isSpeaking.value = false
    }

    fun createInstallVoiceIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

    fun refreshVoice(locale: Locale = Locale.getDefault()) {
        configureVoice(locale)
    }

    @Synchronized
    private fun configureVoice(locale: Locale) {
        val tts = engine ?: return
        val embeddedVoice = chooseEmbeddedVoice(tts.voices.orEmpty(), locale)
        if (embeddedVoice != null) {
            tts.voice = embeddedVoice
            selectedLocale = embeddedVoice.locale
            _offlineVoiceAvailable.value = true
            _isReady.value = true
            _error.value = null
        } else {
            selectedLocale = locale
            _offlineVoiceAvailable.value = false
            _isReady.value = true
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == finalUtteranceId) {
                    _isSpeaking.value = false
                }
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _error.value = "No se pudo reproducir la respuesta"
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onError(utteranceId)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                _isSpeaking.value = false
            }
        })
    }

    private fun chooseEmbeddedVoice(voices: Set<Voice>, locale: Locale): Voice? {
        return voices
            .filterNot(Voice::isNetworkConnectionRequired)
            .filter { sameLanguage(it.locale, locale) }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.latency })
            .firstOrNull()
    }

    private fun sameLanguage(first: Locale, second: Locale): Boolean =
        first.language.equals(second.language, ignoreCase = true)
}
