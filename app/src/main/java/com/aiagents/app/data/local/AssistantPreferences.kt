package com.aiagents.app.data.local

import android.content.Context
import com.aiagents.app.data.speech.AssistantTtsMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantPreferences @Inject constructor(
    @ApplicationContext context: Context,
    voicePreferences: VoicePreferences
) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val initialTtsMode = voicePreferences.ttsMode.value

    private val _speakResponses = MutableStateFlow(
        initialTtsMode != AssistantTtsMode.NONE && preferences.getBoolean(KEY_SPEAK_RESPONSES, false)
    )
    val speakResponses: StateFlow<Boolean> = _speakResponses.asStateFlow()

    private val _modelKey = MutableStateFlow(preferences.getString(KEY_MODEL, "").orEmpty())
    val modelKey: StateFlow<String> = _modelKey.asStateFlow()

    init {
        if (initialTtsMode == AssistantTtsMode.NONE) {
            preferences.edit().putBoolean(KEY_SPEAK_RESPONSES, false).apply()
        }
    }

    fun setSpeakResponses(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SPEAK_RESPONSES, enabled).apply()
        _speakResponses.value = enabled
    }

    fun setModel(modelKey: String) {
        preferences.edit().putString(KEY_MODEL, modelKey).apply()
        _modelKey.value = modelKey
    }

    companion object {
        private const val FILE_NAME = "cortex_assistant_preferences"
        private const val KEY_SPEAK_RESPONSES = "speak_responses"
        private const val KEY_MODEL = "assistant_model"
    }
}
