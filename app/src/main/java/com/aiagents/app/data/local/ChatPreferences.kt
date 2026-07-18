package com.aiagents.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Preferences shared by every standard chat, kept separate from the voice assistant. */
@Singleton
class ChatPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _defaultModel = MutableStateFlow(
        preferences.getString(KEY_DEFAULT_MODEL, "").orEmpty()
    )
    val defaultModel: StateFlow<String> = _defaultModel.asStateFlow()

    fun setDefaultModel(modelKey: String) {
        val normalized = modelKey.trim()
        preferences.edit().putString(KEY_DEFAULT_MODEL, normalized).apply()
        _defaultModel.value = normalized
    }

    companion object {
        private const val FILE_NAME = "cortex_chat_preferences"
        private const val KEY_DEFAULT_MODEL = "default_chat_model"
    }
}
