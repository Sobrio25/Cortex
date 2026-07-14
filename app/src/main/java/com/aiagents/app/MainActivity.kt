package com.aiagents.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aiagents.app.presentation.MainScreen
import com.aiagents.app.data.auth.GoogleWorkspaceOAuthManager
import com.aiagents.app.ui.theme.AIAgentsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var googleWorkspaceOAuthManager: GoogleWorkspaceOAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIAgentsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MainScreen()
                }
            }
        }
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
