package com.aiagents.app.data.local

import android.content.Context
import com.aiagents.app.data.speech.AssistantSttMode
import com.aiagents.app.data.speech.AssistantTtsMode
import com.aiagents.app.data.speech.Qwen3TtsVoiceSkill
import com.aiagents.app.data.speech.RemoteSttConfig
import com.aiagents.app.data.speech.RemoteTtsAudioMode
import com.aiagents.app.data.speech.RemoteTtsApiFlavor
import com.aiagents.app.data.speech.RemoteTtsConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantPreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val securePreferences: SecurePreferences
) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val initialSttMode = AssistantSttMode.fromId(preferences.getString(KEY_STT_MODE, null))
    private val initialTtsMode = AssistantTtsMode.fromId(preferences.getString(KEY_TTS_MODE, null))

    private val _autoListen = MutableStateFlow(
        initialSttMode != AssistantSttMode.NONE && preferences.getBoolean(KEY_AUTO_LISTEN, false)
    )
    val autoListen: StateFlow<Boolean> = _autoListen.asStateFlow()

    private val _speakResponses = MutableStateFlow(
        initialTtsMode != AssistantTtsMode.NONE && preferences.getBoolean(KEY_SPEAK_RESPONSES, false)
    )
    val speakResponses: StateFlow<Boolean> = _speakResponses.asStateFlow()

    private val _modelKey = MutableStateFlow(preferences.getString(KEY_MODEL, "").orEmpty())
    val modelKey: StateFlow<String> = _modelKey.asStateFlow()

    private val _sttMode = MutableStateFlow(
        initialSttMode
    )
    val sttMode: StateFlow<AssistantSttMode> = _sttMode.asStateFlow()

    private val _ttsMode = MutableStateFlow(
        initialTtsMode
    )
    val ttsMode: StateFlow<AssistantTtsMode> = _ttsMode.asStateFlow()

    private val _googleVoiceId = MutableStateFlow(
        preferences.getString(KEY_GOOGLE_VOICE_ID, "").orEmpty()
    )
    val googleVoiceId: StateFlow<String> = _googleVoiceId.asStateFlow()

    private val _remoteSttConfig = MutableStateFlow(
        RemoteSttConfig(
            endpointUrl = preferences.getString(KEY_REMOTE_STT_ENDPOINT, "").orEmpty(),
            model = preferences.getString(KEY_REMOTE_STT_MODEL, DEFAULT_REMOTE_STT_MODEL)
                .orEmpty().ifBlank { DEFAULT_REMOTE_STT_MODEL },
            apiKey = securePreferences.getAssistantRemoteSttApiKey()
        )
    )
    val remoteSttConfig: StateFlow<RemoteSttConfig> = _remoteSttConfig.asStateFlow()

    private val _remoteTtsConfig = MutableStateFlow(
        RemoteTtsConfig(
            endpointUrl = preferences.getString(KEY_REMOTE_TTS_ENDPOINT, "").orEmpty(),
            model = preferences.getString(KEY_REMOTE_TTS_MODEL, DEFAULT_REMOTE_TTS_MODEL)
                .orEmpty().ifBlank { DEFAULT_REMOTE_TTS_MODEL },
            voice = preferences.getString(KEY_REMOTE_TTS_VOICE, DEFAULT_REMOTE_TTS_VOICE)
                .orEmpty().ifBlank { DEFAULT_REMOTE_TTS_VOICE },
            apiKey = securePreferences.getAssistantRemoteTtsApiKey(),
            apiFlavor = RemoteTtsApiFlavor.fromId(
                preferences.getString(KEY_REMOTE_TTS_API_FLAVOR, null)
            ),
            language = preferences.getString(KEY_REMOTE_TTS_LANGUAGE, DEFAULT_REMOTE_TTS_LANGUAGE)
                .orEmpty().ifBlank { DEFAULT_REMOTE_TTS_LANGUAGE },
            voiceDescription = preferences.getString(
                KEY_REMOTE_TTS_VOICE_DESCRIPTION,
                Qwen3TtsVoiceSkill.DEFAULT_VOICE_DESCRIPTION
            ).orEmpty().ifBlank {
                Qwen3TtsVoiceSkill.DEFAULT_VOICE_DESCRIPTION
            },
            adaptiveStyle = preferences.getBoolean(KEY_REMOTE_TTS_ADAPTIVE_STYLE, true),
            audioMode = RemoteTtsAudioMode.fromId(
                preferences.getString(KEY_REMOTE_TTS_AUDIO_MODE, null)
            ),
            pcmSampleRate = preferences.getInt(KEY_REMOTE_TTS_PCM_SAMPLE_RATE, 24_000)
        )
    )
    val remoteTtsConfig: StateFlow<RemoteTtsConfig> = _remoteTtsConfig.asStateFlow()

    init {
        if (initialSttMode == AssistantSttMode.NONE || initialTtsMode == AssistantTtsMode.NONE) {
            preferences.edit().apply {
                if (initialSttMode == AssistantSttMode.NONE) putBoolean(KEY_AUTO_LISTEN, false)
                if (initialTtsMode == AssistantTtsMode.NONE) putBoolean(KEY_SPEAK_RESPONSES, false)
            }.apply()
        }
    }

    fun setAutoListen(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_LISTEN, enabled).apply()
        _autoListen.value = enabled
    }

    fun setSpeakResponses(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SPEAK_RESPONSES, enabled).apply()
        _speakResponses.value = enabled
    }

    fun setModel(modelKey: String) {
        preferences.edit().putString(KEY_MODEL, modelKey).apply()
        _modelKey.value = modelKey
    }

    fun setSttMode(mode: AssistantSttMode) {
        preferences.edit().putString(KEY_STT_MODE, mode.id).apply()
        _sttMode.value = mode
    }

    fun setTtsMode(mode: AssistantTtsMode) {
        preferences.edit().putString(KEY_TTS_MODE, mode.id).apply()
        _ttsMode.value = mode
    }

    fun setGoogleVoiceId(voiceId: String) {
        preferences.edit().putString(KEY_GOOGLE_VOICE_ID, voiceId).apply()
        _googleVoiceId.value = voiceId
    }

    fun setRemoteSttConfig(config: RemoteSttConfig) {
        val normalized = config.copy(
            endpointUrl = config.endpointUrl.trim(),
            model = config.model.trim().ifBlank { DEFAULT_REMOTE_STT_MODEL },
            apiKey = config.apiKey.trim()
        )
        preferences.edit()
            .putString(KEY_REMOTE_STT_ENDPOINT, normalized.endpointUrl)
            .putString(KEY_REMOTE_STT_MODEL, normalized.model)
            .apply()
        securePreferences.saveAssistantRemoteSttApiKey(normalized.apiKey)
        _remoteSttConfig.value = normalized
    }

    fun setRemoteTtsConfig(config: RemoteTtsConfig) {
        val normalized = config.copy(
            endpointUrl = config.endpointUrl.trim(),
            model = config.model.trim().ifBlank { DEFAULT_REMOTE_TTS_MODEL },
            voice = config.voice.trim().ifBlank { DEFAULT_REMOTE_TTS_VOICE },
            apiKey = config.apiKey.trim(),
            language = config.language.trim().ifBlank { DEFAULT_REMOTE_TTS_LANGUAGE },
            voiceDescription = config.voiceDescription.trim().ifBlank {
                Qwen3TtsVoiceSkill.DEFAULT_VOICE_DESCRIPTION
            },
            pcmSampleRate = config.pcmSampleRate.coerceIn(8_000, 96_000)
        )
        preferences.edit()
            .putString(KEY_REMOTE_TTS_ENDPOINT, normalized.endpointUrl)
            .putString(KEY_REMOTE_TTS_MODEL, normalized.model)
            .putString(KEY_REMOTE_TTS_VOICE, normalized.voice)
            .putString(KEY_REMOTE_TTS_API_FLAVOR, normalized.apiFlavor.id)
            .putString(KEY_REMOTE_TTS_LANGUAGE, normalized.language)
            .putString(KEY_REMOTE_TTS_VOICE_DESCRIPTION, normalized.voiceDescription)
            .putBoolean(KEY_REMOTE_TTS_ADAPTIVE_STYLE, normalized.adaptiveStyle)
            .putString(KEY_REMOTE_TTS_AUDIO_MODE, normalized.audioMode.id)
            .putInt(KEY_REMOTE_TTS_PCM_SAMPLE_RATE, normalized.pcmSampleRate)
            .apply()
        securePreferences.saveAssistantRemoteTtsApiKey(normalized.apiKey)
        _remoteTtsConfig.value = normalized
    }

    companion object {
        private const val FILE_NAME = "cortex_assistant_preferences"
        private const val KEY_AUTO_LISTEN = "auto_listen"
        private const val KEY_SPEAK_RESPONSES = "speak_responses"
        private const val KEY_MODEL = "assistant_model"
        private const val KEY_STT_MODE = "assistant_stt_mode"
        private const val KEY_TTS_MODE = "assistant_tts_mode"
        private const val KEY_GOOGLE_VOICE_ID = "assistant_google_voice_id"
        private const val KEY_REMOTE_STT_ENDPOINT = "assistant_remote_stt_endpoint"
        private const val KEY_REMOTE_STT_MODEL = "assistant_remote_stt_model"
        private const val KEY_REMOTE_TTS_ENDPOINT = "assistant_remote_tts_endpoint"
        private const val KEY_REMOTE_TTS_MODEL = "assistant_remote_tts_model"
        private const val KEY_REMOTE_TTS_VOICE = "assistant_remote_tts_voice"
        private const val KEY_REMOTE_TTS_API_FLAVOR = "assistant_remote_tts_api_flavor"
        private const val KEY_REMOTE_TTS_LANGUAGE = "assistant_remote_tts_language"
        private const val KEY_REMOTE_TTS_VOICE_DESCRIPTION =
            "assistant_remote_tts_voice_description"
        private const val KEY_REMOTE_TTS_ADAPTIVE_STYLE = "assistant_remote_tts_adaptive_style"
        private const val KEY_REMOTE_TTS_AUDIO_MODE = "assistant_remote_tts_audio_mode"
        private const val KEY_REMOTE_TTS_PCM_SAMPLE_RATE = "assistant_remote_tts_pcm_sample_rate"
        private const val DEFAULT_REMOTE_STT_MODEL = "whisper-1"
        private const val DEFAULT_REMOTE_TTS_MODEL = "tts-1"
        private const val DEFAULT_REMOTE_TTS_VOICE = "alloy"
        private const val DEFAULT_REMOTE_TTS_LANGUAGE = "Auto"
    }
}
