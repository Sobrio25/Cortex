package com.aiagents.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _autoListen = MutableStateFlow(preferences.getBoolean(KEY_AUTO_LISTEN, true))
    val autoListen: StateFlow<Boolean> = _autoListen.asStateFlow()

    private val _speakResponses = MutableStateFlow(preferences.getBoolean(KEY_SPEAK_RESPONSES, true))
    val speakResponses: StateFlow<Boolean> = _speakResponses.asStateFlow()

    private val _modelKey = MutableStateFlow(preferences.getString(KEY_MODEL, "").orEmpty())
    val modelKey: StateFlow<String> = _modelKey.asStateFlow()

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

    companion object {
        private const val FILE_NAME = "cortex_assistant_preferences"
        private const val KEY_AUTO_LISTEN = "auto_listen"
        private const val KEY_SPEAK_RESPONSES = "speak_responses"
        private const val KEY_MODEL = "assistant_model"
    }
}
