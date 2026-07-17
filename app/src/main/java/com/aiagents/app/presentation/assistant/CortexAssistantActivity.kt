package com.aiagents.app.presentation.assistant

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.local.AssistantPreferences
import com.aiagents.app.data.local.VoicePreferences
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.data.speech.AndroidTextToSpeechManager
import com.aiagents.app.data.terminal.AssistantActionCoordinator
import com.aiagents.app.domain.model.Workspace
import com.aiagents.app.ui.theme.AIAgentsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class CortexAssistantActivity : AppCompatActivity() {
    @Inject lateinit var repository: AgentRepository
    @Inject lateinit var textToSpeech: AndroidTextToSpeechManager
    @Inject lateinit var securePreferences: SecurePreferences
    @Inject lateinit var assistantPreferences: AssistantPreferences
    @Inject lateinit var voicePreferences: VoicePreferences
    @Inject lateinit var assistantActionCoordinator: AssistantActionCoordinator

    private var workspaceId by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) assistantActionCoordinator.reset()
        enableEdgeToEdge()
        configureGlassWindow()

        setContent {
            AIAgentsTheme(
                darkTheme = true,
                dynamicColor = false,
                transparentSystemBars = true,
                applyBackdrop = false
            ) {
                if (workspaceId > 0L) {
                    CortexAssistantScreen(
                        workspaceId = workspaceId,
                        cortexName = securePreferences.getAssistantName() ?: "Assistant",
                        assistantLanguageTag = securePreferences.getAppLanguage()
                            .ifBlank { Locale.getDefault().toLanguageTag() },
                        textToSpeech = textToSpeech,
                        preferences = assistantPreferences,
                        voicePreferences = voicePreferences,
                        assistantActionCoordinator = assistantActionCoordinator,
                        onDismiss = ::finish
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        lifecycleScope.launch {
            val id = resolveGlobalWorkspaceId()
            // The WorkspaceDetailViewModel reads this key from its SavedStateHandle.
            intent.putExtra(EXTRA_WORKSPACE_ID, id)
            workspaceId = id
        }
    }

    override fun onDestroy() {
        textToSpeech.stop()
        super.onDestroy()
    }

    private suspend fun resolveGlobalWorkspaceId(): Long {
        repository.getAllWorkspaces().first()
            .firstOrNull { it.name == GLOBAL_WORKSPACE_NAME }
            ?.let { return it.id }
        return repository.createWorkspace(
            Workspace(name = GLOBAL_WORKSPACE_NAME, description = "")
        )
    }

    private fun configureGlassWindow() {
        window.setDimAmount(0.18f)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val attributes = window.attributes
            attributes.blurBehindRadius = 42
            window.attributes = attributes
        }
    }

    companion object {
        const val EXTRA_WORKSPACE_ID = "workspaceId"
        private const val GLOBAL_WORKSPACE_NAME = "__global__"
    }
}
