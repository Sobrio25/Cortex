package com.aiagents.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aiagents.app.presentation.MainScreen
import com.aiagents.app.data.auth.GoogleWorkspaceOAuthManager
import com.aiagents.app.data.diagnostics.AppHealthMonitor
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.scheduling.ScheduledTaskWorker
import com.aiagents.app.ui.theme.AIAgentsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private data class ScheduledTaskDestination(
        val workspaceId: Long,
        val conversationId: Long
    )

    @Inject
    lateinit var googleWorkspaceOAuthManager: GoogleWorkspaceOAuthManager

    @Inject
    lateinit var securePreferences: SecurePreferences

    @Inject
    lateinit var appHealthMonitor: AppHealthMonitor

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var scheduledTaskDestination by mutableStateOf<ScheduledTaskDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        scheduledTaskDestination = consumeScheduledTaskDestination(intent)
        setContent {
            AIAgentsTheme(transparentSystemBars = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    MainScreen(
                        scheduledTaskWorkspaceId = scheduledTaskDestination?.workspaceId,
                        scheduledTaskConversationId = scheduledTaskDestination?.conversationId,
                        onScheduledTaskDestinationHandled = {
                            scheduledTaskDestination = null
                        }
                    )
                }
            }
        }
        window.decorView.doOnPreDraw {
            appHealthMonitor.recordFirstDraw(SystemClock.elapsedRealtime())
            reportFullyDrawn()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scheduledTaskDestination = consumeScheduledTaskDestination(intent)
    }

    private fun consumeScheduledTaskDestination(intent: Intent?): ScheduledTaskDestination? {
        if (intent == null) return null
        val workspaceId = intent.getLongExtra(ScheduledTaskWorker.EXTRA_WORKSPACE_ID, -1L)
        val conversationId = intent.getLongExtra(ScheduledTaskWorker.EXTRA_CONVERSATION_ID, -1L)
        intent.removeExtra(ScheduledTaskWorker.EXTRA_WORKSPACE_ID)
        intent.removeExtra(ScheduledTaskWorker.EXTRA_CONVERSATION_ID)
        return if (workspaceId > 0 && conversationId > 0) {
            ScheduledTaskDestination(workspaceId, conversationId)
        } else {
            null
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        // Do not front-load a permission prompt before the user has even chosen an app mode.
        if (!securePreferences.isOnboardingCompleted()) return
        if (!securePreferences.isTaskNotificationsEnabled()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onStart() {
        super.onStart()
        if (googleWorkspaceOAuthManager.isAuthenticated()) {
            lifecycleScope.launch {
                googleWorkspaceOAuthManager.refreshIfPossible(this@MainActivity)
            }
        }
    }
}
