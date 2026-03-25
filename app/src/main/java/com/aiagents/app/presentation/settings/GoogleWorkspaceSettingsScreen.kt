package com.aiagents.app.presentation.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.auth.GoogleWorkspaceOAuthManager
import com.aiagents.app.data.local.SecurePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoogleWorkspaceSettingsState(
    val isConnected: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val authorizedScopes: List<String> = emptyList(),
    val selectedServices: Set<String> = setOf("drive", "gmail", "calendar", "sheets", "docs", "slides"),
    val isLoading: Boolean = false,
    val authUrl: String? = null,
    val error: String? = null,
    val success: String? = null
)

@HiltViewModel
class GoogleWorkspaceSettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val oAuthManager: GoogleWorkspaceOAuthManager
) : ViewModel() {

    private val _state = MutableStateFlow(GoogleWorkspaceSettingsState())
    val state: StateFlow<GoogleWorkspaceSettingsState> = _state.asStateFlow()

    init {
        loadState()
    }

    private fun loadState() {
        _state.value = _state.value.copy(
            isConnected = oAuthManager.isAuthenticated(),
            clientId = securePreferences.getGoogleDriveClientId() ?: "",
            clientSecret = securePreferences.getGoogleDriveClientSecret() ?: "",
            authorizedScopes = oAuthManager.getAuthorizedScopes()
        )
    }

    fun updateClientId(id: String) {
        _state.value = _state.value.copy(clientId = id)
        securePreferences.saveGoogleDriveClientId(id)
    }

    fun updateClientSecret(secret: String) {
        _state.value = _state.value.copy(clientSecret = secret)
        securePreferences.saveGoogleDriveClientSecret(secret)
    }

    fun toggleService(service: String) {
        val current = _state.value.selectedServices.toMutableSet()
        if (service in current) current.remove(service) else current.add(service)
        _state.value = _state.value.copy(selectedServices = current)
    }

    fun startAuth(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val result = oAuthManager.performFullAuthFlow(context, _state.value.selectedServices.toList())
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        isConnected = true,
                        isLoading = false,
                        authorizedScopes = oAuthManager.getAuthorizedScopes(),
                        success = "Connected to Google Workspace successfully!"
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        error = "Authentication failed: ${e.message}",
                        isLoading = false
                    )
                }
            )
        }
    }

    fun disconnect() {
        oAuthManager.logout()
        securePreferences.clearGoogleDrive()
        _state.value = _state.value.copy(
            isConnected = false,
            authorizedScopes = emptyList(),
            success = null
        )
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, success = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleWorkspaceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: GoogleWorkspaceSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Open auth URL in browser when available
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Workspace") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isConnected)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (state.isConnected) Icons.Default.CheckCircle else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (state.isConnected)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (state.isConnected) "Connected" else "Not Connected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.isConnected && state.authorizedScopes.isNotEmpty()) {
                                Text(
                                    "${state.authorizedScopes.size} scopes authorized",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (state.isConnected) {
                            TextButton(onClick = { viewModel.disconnect() }) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }

            // Error/Success messages
            state.error?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearMessages() }) {
                                Icon(Icons.Default.Close, "Dismiss")
                            }
                        }
                    }
                }
            }

            state.success?.let { success ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Text(success, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // OAuth credentials
            item {
                Text(
                    "OAuth Credentials",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                var showSecret by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = state.clientId,
                    onValueChange = { viewModel.updateClientId(it) },
                    label = { Text("Client ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.clientSecret,
                    onValueChange = { viewModel.updateClientSecret(it) },
                    label = { Text("Client Secret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                "Toggle visibility"
                            )
                        }
                    }
                )
            }

            // Services to enable
            item {
                Text(
                    "Services",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "Select which Google Workspace services to authorize",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val services = listOf(
                "gmail" to "Gmail",
                "drive" to "Google Drive",
                "calendar" to "Google Calendar",
                "sheets" to "Google Sheets",
                "docs" to "Google Docs",
                "slides" to "Google Slides"
            )

            items(services) { (key, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = key in state.selectedServices,
                        onCheckedChange = { viewModel.toggleService(key) }
                    )
                    Text(label, modifier = Modifier.weight(1f))
                }
            }

            // Connect button
            item {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.startAuth(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && state.clientId.isNotBlank() && state.clientSecret.isNotBlank()
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Waiting for authentication...")
                    } else {
                        Icon(Icons.Default.Login, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.isConnected) "Reconnect" else "Connect to Google Workspace")
                    }
                }
            }

            // Authorized scopes (if connected)
            if (state.isConnected && state.authorizedScopes.isNotEmpty()) {
                item {
                    Text(
                        "Authorized Scopes",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                items(state.authorizedScopes) { scope ->
                    val shortScope = scope.substringAfterLast("/")
                    Text(
                        shortScope,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }
            }

            // Info
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("How to set up", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "1. Go to Google Cloud Console\n" +
                            "2. Create a project and enable the APIs you need\n" +
                            "3. Create OAuth 2.0 credentials (Desktop app type)\n" +
                            "4. Add yourself as a test user\n" +
                            "5. Copy the Client ID and Client Secret here\n" +
                            "6. Select services and click Connect",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}