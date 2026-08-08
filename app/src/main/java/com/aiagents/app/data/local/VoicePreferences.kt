package com.aiagents.app.data.local

import android.content.Context
import com.aiagents.app.data.speech.AssistantSttMode
import com.aiagents.app.data.speech.AssistantTtsMode
import com.aiagents.app.data.speech.GROQ_TTS_DEFAULT_MODEL
import com.aiagents.app.data.speech.GROQ_TTS_DEFAULT_VOICE
import com.aiagents.app.data.speech.GroqTtsConfig
import com.aiagents.app.data.speech.OnDemandVoiceFeatureLoader
import com.aiagents.app.data.speech.Qwen3TtsVoiceSkill
import com.aiagents.app.data.speech.RemoteSttConfig
import com.aiagents.app.data.speech.RemoteTtsApiFlavor
import com.aiagents.app.data.speech.RemoteTtsAudioMode
import com.aiagents.app.data.speech.RemoteTtsConfig
import com.aiagents.app.data.speech.SelfHostedVoiceApi
import com.aiagents.app.data.speech.VoiceCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class GlobalSttSettings(
    val mode: AssistantSttMode,
    val language: String,
    val apiKey: String,
    val remoteConfig: RemoteSttConfig
)

data class LegacyWorkspaceVoiceSettings(
    val enabled: Boolean,
    val mode: String,
    val localEngine: String,
    val cloudProvider: String,
    val apiKey: String,
    val language: String
)

fun interface LegacyVoiceSettingsImporter {
    /** Returns only after the imported settings have been durably persisted. */
    fun import(settings: LegacyWorkspaceVoiceSettings?): Boolean
}

/** Single source of truth for voice engines used by chat and the Android assistant. */
@Singleton
class VoicePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) : LegacyVoiceSettingsImporter {
    // Keep the existing file and keys so assistant voice selections survive the split.
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _sttMode = MutableStateFlow(
        AssistantSttMode.fromId(preferences.getString(KEY_STT_MODE, null))
    )
    val sttMode: StateFlow<AssistantSttMode> = _sttMode.asStateFlow()

    private val _sttLanguage = MutableStateFlow(
        preferences.getString(KEY_STT_LANGUAGE, securePreferences.getAppLanguage())
            .orEmpty()
            .ifBlank { DEFAULT_STT_LANGUAGE }
    )
    val sttLanguage: StateFlow<String> = _sttLanguage.asStateFlow()

    private val _ttsMode = MutableStateFlow(
        AssistantTtsMode.fromId(preferences.getString(KEY_TTS_MODE, null))
    )
    val ttsMode: StateFlow<AssistantTtsMode> = _ttsMode.asStateFlow()

    private val _googleVoiceId = MutableStateFlow(
        preferences.getString(KEY_GOOGLE_VOICE_ID, "").orEmpty()
    )
    val googleVoiceId: StateFlow<String> = _googleVoiceId.asStateFlow()

    private val _remoteSttConfig = MutableStateFlow(readRemoteSttConfig())
    val remoteSttConfig: StateFlow<RemoteSttConfig> = _remoteSttConfig.asStateFlow()

    private val _remoteTtsConfig = MutableStateFlow(readRemoteTtsConfig())
    val remoteTtsConfig: StateFlow<RemoteTtsConfig> = _remoteTtsConfig.asStateFlow()

    private val _groqTtsConfig = MutableStateFlow(readGroqTtsConfig())
    val groqTtsConfig: StateFlow<GroqTtsConfig> = _groqTtsConfig.asStateFlow()

    private val _sttSettings = MutableStateFlow(buildSttSettings())
    val sttSettings: StateFlow<GlobalSttSettings> = _sttSettings.asStateFlow()

    fun setSttMode(mode: AssistantSttMode) {
        preferences.edit().putString(KEY_STT_MODE, mode.id).apply()
        _sttMode.value = mode
        refreshSttSettings()
    }

    fun setSttLanguage(language: String) {
        val normalized = language.trim().ifBlank { DEFAULT_STT_LANGUAGE }
        preferences.edit().putString(KEY_STT_LANGUAGE, normalized).apply()
        _sttLanguage.value = normalized
        refreshSttSettings()
    }

    fun setTtsMode(mode: AssistantTtsMode) {
        preferences.edit()
            .putString(KEY_TTS_MODE, mode.id)
            .also { editor ->
                if (mode != AssistantTtsMode.NONE) {
                    editor.putString(KEY_LAST_ENABLED_TTS_MODE, mode.id)
                }
            }
            .apply()
        _ttsMode.value = mode
    }

    fun toggleTts() {
        val nextMode = if (_ttsMode.value == AssistantTtsMode.NONE) {
            AssistantTtsMode.fromId(
                preferences.getString(KEY_LAST_ENABLED_TTS_MODE, AssistantTtsMode.GOOGLE.id)
            ).takeUnless { it == AssistantTtsMode.NONE } ?: AssistantTtsMode.GOOGLE
        } else {
            AssistantTtsMode.NONE
        }
        setTtsMode(nextMode)
    }

    fun setGoogleVoiceId(voiceId: String) {
        preferences.edit().putString(KEY_GOOGLE_VOICE_ID, voiceId).apply()
        _googleVoiceId.value = voiceId
    }

    fun setSttApiKey(mode: AssistantSttMode, apiKey: String) {
        securePreferences.saveVoiceSttApiKey(mode.id, apiKey)
        if (_sttMode.value == mode) refreshSttSettings()
    }

    fun getSttApiKey(mode: AssistantSttMode): String =
        securePreferences.getVoiceSttApiKey(mode.id)

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
        refreshSttSettings()
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

    fun setGroqTtsConfig(config: GroqTtsConfig) {
        val normalized = config.copy(
            apiKey = config.apiKey.trim(),
            voice = config.voice.trim().ifBlank { GROQ_TTS_DEFAULT_VOICE },
            model = config.model.trim().ifBlank { GROQ_TTS_DEFAULT_MODEL }
        )
        preferences.edit()
            .putString(KEY_GROQ_TTS_VOICE, normalized.voice)
            .putString(KEY_GROQ_TTS_MODEL, normalized.model)
            .apply()
        securePreferences.saveVoiceTtsApiKey(AssistantTtsMode.GROQ.id, normalized.apiKey)
        _groqTtsConfig.value = normalized
    }

    override fun import(settings: LegacyWorkspaceVoiceSettings?): Boolean {
        val storedModeId = preferences.getString(KEY_STT_MODE, null)
        val storedMode = storedModeId
            ?.takeUnless { it == LEGACY_DISABLED_STT_MODE }
            ?.let(AssistantSttMode::fromId)

        val importedMode = storedMode ?: settings.toUsableGlobalMode()
        val importedLanguage = if (storedMode != null) {
            _sttLanguage.value
        } else {
            settings?.language?.takeIf(String::isNotBlank) ?: _sttLanguage.value
        }

        if (storedMode == null && settings != null && importedMode.isApiProvider) {
            if (!securePreferences.saveVoiceSttApiKeySync(importedMode.id, settings.apiKey)) {
                return false
            }
        }

        val committed = preferences.edit()
            .putString(KEY_STT_MODE, importedMode.id)
            .putString(KEY_STT_LANGUAGE, importedLanguage)
            .remove(KEY_AUTO_LISTEN)
            .commit()
        if (!committed) return false

        _sttMode.value = importedMode
        _sttLanguage.value = importedLanguage
        refreshSttSettings()
        return true
    }

    private fun LegacyWorkspaceVoiceSettings?.toUsableGlobalMode(): AssistantSttMode {
        if (this == null || !enabled || mode == "OFF") return AssistantSttMode.ANDROID
        return when {
            mode == "LOCAL" && localEngine == "SHERPA_ONNX" -> {
                if (OnDemandVoiceFeatureLoader.isAssetReady(context, VoiceCatalog.WHISPER_TINY_ID)) {
                    AssistantSttMode.WHISPER_TINY
                } else {
                    AssistantSttMode.ANDROID
                }
            }
            mode == "LOCAL" -> AssistantSttMode.ANDROID
            cloudProvider == "ANDROID_SPEECH_RECOGNIZER" -> AssistantSttMode.ANDROID
            cloudProvider == "SELF_HOSTED" || cloudProvider == "FASTER_WHISPER" -> {
                if (SelfHostedVoiceApi.isConfigured(_remoteSttConfig.value)) {
                    AssistantSttMode.REMOTE_SERVER
                } else {
                    AssistantSttMode.ANDROID
                }
            }
            cloudProvider == "WHISPER_API" && apiKey.isNotBlank() ->
                AssistantSttMode.OPENAI_WHISPER
            cloudProvider == "ASSEMBLY_AI" && apiKey.isNotBlank() ->
                AssistantSttMode.ASSEMBLY_AI
            cloudProvider == "DEEPGRAM" && apiKey.isNotBlank() ->
                AssistantSttMode.DEEPGRAM
            cloudProvider == "GOOGLE_FREE" && apiKey.isNotBlank() ->
                AssistantSttMode.GOOGLE_CLOUD
            else -> AssistantSttMode.ANDROID
        }
    }

    private fun buildSttSettings(): GlobalSttSettings {
        val mode = _sttMode.value
        return GlobalSttSettings(
            mode = mode,
            language = _sttLanguage.value,
            apiKey = if (mode.isApiProvider) getSttApiKey(mode) else "",
            remoteConfig = _remoteSttConfig.value
        )
    }

    private fun refreshSttSettings() {
        _sttSettings.value = buildSttSettings()
    }

    private fun readRemoteSttConfig() = RemoteSttConfig(
        endpointUrl = preferences.getString(KEY_REMOTE_STT_ENDPOINT, "").orEmpty(),
        model = preferences.getString(KEY_REMOTE_STT_MODEL, DEFAULT_REMOTE_STT_MODEL)
            .orEmpty().ifBlank { DEFAULT_REMOTE_STT_MODEL },
        apiKey = securePreferences.getAssistantRemoteSttApiKey()
    )

    private fun readRemoteTtsConfig() = RemoteTtsConfig(
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
        ).orEmpty().ifBlank { Qwen3TtsVoiceSkill.DEFAULT_VOICE_DESCRIPTION },
        adaptiveStyle = preferences.getBoolean(KEY_REMOTE_TTS_ADAPTIVE_STYLE, true),
        audioMode = RemoteTtsAudioMode.fromId(
            preferences.getString(KEY_REMOTE_TTS_AUDIO_MODE, null)
        ),
        pcmSampleRate = preferences.getInt(KEY_REMOTE_TTS_PCM_SAMPLE_RATE, 24_000)
    )

    private fun readGroqTtsConfig() = GroqTtsConfig(
        apiKey = securePreferences.getVoiceTtsApiKey(AssistantTtsMode.GROQ.id),
        voice = preferences.getString(KEY_GROQ_TTS_VOICE, GROQ_TTS_DEFAULT_VOICE)
            .orEmpty().ifBlank { GROQ_TTS_DEFAULT_VOICE },
        model = preferences.getString(KEY_GROQ_TTS_MODEL, GROQ_TTS_DEFAULT_MODEL)
            .orEmpty().ifBlank { GROQ_TTS_DEFAULT_MODEL }
    )

    companion object {
        private const val FILE_NAME = "cortex_assistant_preferences"
        private const val KEY_AUTO_LISTEN = "auto_listen"
        private const val KEY_STT_MODE = "assistant_stt_mode"
        private const val KEY_STT_LANGUAGE = "voice_stt_language"
        private const val KEY_TTS_MODE = "assistant_tts_mode"
        private const val KEY_LAST_ENABLED_TTS_MODE = "assistant_last_enabled_tts_mode"
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
        private const val KEY_GROQ_TTS_VOICE = "assistant_groq_tts_voice"
        private const val KEY_GROQ_TTS_MODEL = "assistant_groq_tts_model"
        private const val LEGACY_DISABLED_STT_MODE = "none"
        private const val DEFAULT_STT_LANGUAGE = "es"
        private const val DEFAULT_REMOTE_STT_MODEL = "whisper-1"
        private const val DEFAULT_REMOTE_TTS_MODEL = "tts-1"
        private const val DEFAULT_REMOTE_TTS_VOICE = "alloy"
        private const val DEFAULT_REMOTE_TTS_LANGUAGE = "Auto"
    }
}

val AssistantSttMode.isApiProvider: Boolean
    get() = this == AssistantSttMode.OPENAI_WHISPER ||
        this == AssistantSttMode.ASSEMBLY_AI ||
        this == AssistantSttMode.DEEPGRAM ||
        this == AssistantSttMode.GOOGLE_CLOUD
