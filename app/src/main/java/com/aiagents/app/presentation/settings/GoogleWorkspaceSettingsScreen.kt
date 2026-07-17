package com.aiagents.app.presentation.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.auth.GoogleAuthorizationOutcome
import com.aiagents.app.data.auth.GoogleWorkspaceOAuthManager
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val GOOGLE_SERVICES = listOf(
    "docs" to "Google Docs",
    "drive" to "Google Drive",
    "sheets" to "Google Sheets",
    "slides" to "Google Slides",
    "calendar" to "Google Calendar",
    "gmail" to "Gmail"
)

data class GoogleWorkspaceSettingsState(
    val isConnected: Boolean = false,
    val isAuthorizationExpired: Boolean = false,
    val accountEmail: String? = null,
    val authorizedScopes: List<String> = emptyList(),
    val selectedServices: Set<String> = GOOGLE_SERVICES.mapTo(linkedSetOf()) { it.first },
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null
)

@HiltViewModel
class GoogleWorkspaceSettingsViewModel @Inject constructor(
    private val oAuthManager: GoogleWorkspaceOAuthManager,
    private val errorReporter: AppErrorReporter
) : ViewModel() {
    private val _state = MutableStateFlow(GoogleWorkspaceSettingsState())
    val state: StateFlow<GoogleWorkspaceSettingsState> = _state.asStateFlow()

    init {
        loadState()
    }

    private fun loadState() {
        _state.value = _state.value.copy(
            isConnected = oAuthManager.isAuthenticated(),
            isAuthorizationExpired = oAuthManager.isAuthorizationExpired(),
            accountEmail = oAuthManager.getAccountEmail(),
            authorizedScopes = oAuthManager.getAuthorizedScopes()
        )
    }

    fun toggleService(service: String) {
        val selected = _state.value.selectedServices.toMutableSet()
        if (!selected.add(service)) selected.remove(service)
        _state.value = _state.value.copy(selectedServices = selected, error = null)
    }

    fun startAuth(
        activity: Activity,
        launchConsent: (IntentSenderRequest) -> Unit
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, success = null)
            oAuthManager.beginAuthorization(activity, _state.value.selectedServices).fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is GoogleAuthorizationOutcome.Authorized -> onAuthorized(outcome)
                        is GoogleAuthorizationOutcome.RequiresConsent -> launchConsent(
                            IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build()
                        )
                    }
                },
                onFailure = ::onAuthorizationFailure
            )
        }
    }

    fun completeAuth(activity: Activity, data: Intent) {
        oAuthManager.completeAuthorization(activity, data).fold(
            onSuccess = ::onAuthorized,
            onFailure = ::onAuthorizationFailure
        )
    }

    fun onAuthCancelled() {
        _state.value = _state.value.copy(
            isLoading = false,
            error = "La autorización de Google fue cancelada."
        )
    }

    fun disconnect(activity: Activity) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, success = null)
            val result = oAuthManager.disconnect(activity)
            _state.value = _state.value.copy(
                isConnected = false,
                isAuthorizationExpired = false,
                accountEmail = null,
                authorizedScopes = emptyList(),
                isLoading = false,
                success = if (result.isSuccess) "Acceso de Google revocado." else null,
                error = result.exceptionOrNull()?.let {
                    workspaceError(it, "google_workspace_disconnect")
                }
            )
        }
    }

    fun reportUnavailableActivity() {
        _state.value = _state.value.copy(
            error = "No se pudo abrir el consentimiento de Google en esta pantalla."
        )
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, success = null)
    }

    private fun onAuthorized(outcome: GoogleAuthorizationOutcome.Authorized) {
        _state.value = _state.value.copy(
            isConnected = true,
            isAuthorizationExpired = false,
            accountEmail = outcome.accountEmail,
            authorizedScopes = outcome.grantedScopes,
            isLoading = false,
            error = null,
            success = "Google Workspace quedó conectado con permiso del usuario."
        )
    }

    private fun onAuthorizationFailure(error: Throwable) {
        _state.value = _state.value.copy(
            isLoading = false,
            error = workspaceError(error, "google_workspace_authorization")
        )
    }

    private fun workspaceError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(
                component = "google_workspace_settings",
                operation = operation,
                provider = "GOOGLE"
            )
        ).displayMessage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleWorkspaceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: GoogleWorkspaceSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null && activity != null) {
            viewModel.completeAuth(activity, data)
        } else {
            viewModel.onAuthCancelled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Workspace") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isConnected && !state.isAuthorizationExpired) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (state.isConnected && !state.isAuthorizationExpired) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.CloudOff
                            },
                            contentDescription = null
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when {
                                    state.isAuthorizationExpired -> "Autorización vencida"
                                    state.isConnected -> "Conectado"
                                    else -> "Sin conectar"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            state.accountEmail?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.isConnected) {
                                Text(
                                    "${state.authorizedScopes.size} permisos concedidos",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (state.isConnected && activity != null) {
                            TextButton(
                                onClick = { viewModel.disconnect(activity) },
                                enabled = !state.isLoading
                            ) {
                                Text("Desconectar")
                            }
                        }
                    }
                }
            }

            state.error?.let { error ->
                item { StatusMessage(error, isError = true, onDismiss = viewModel::clearMessages) }
            }
            state.success?.let { success ->
                item { StatusMessage(success, isError = false, onDismiss = viewModel::clearMessages) }
            }

            item {
                Text("Permisos", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Selecciona solo los servicios que el asistente podrá usar. Google te mostrará el consentimiento antes de concederlos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(GOOGLE_SERVICES, key = { it.first }) { (key, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = key in state.selectedServices,
                        onCheckedChange = { viewModel.toggleService(key) },
                        enabled = !state.isLoading
                    )
                    Text(label, modifier = Modifier.weight(1f))
                }
            }

            item {
                Button(
                    onClick = {
                        if (activity == null) {
                            viewModel.reportUnavailableActivity()
                        } else {
                            viewModel.startAuth(activity, consentLauncher::launch)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && state.selectedServices.isNotEmpty()
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Esperando a Google…")
                    } else {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.isConnected) "Actualizar permisos" else "Conectar con Google")
                    }
                }
            }

            if (state.isConnected && state.authorizedScopes.isNotEmpty()) {
                item {
                    Text(
                        "Permisos autorizados",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.authorizedScopes, key = { it }) { scope ->
                    Text(
                        "• ${scope.substringAfterLast('/')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null)
                            Text("Configuración de Google Cloud", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Habilita las APIs de Docs, Drive, Sheets, Slides, Calendar y Gmail que quieras usar. " +
                                "Crea un cliente OAuth de tipo Android para el paquete com.aiagents.app con el SHA-1 de la firma, " +
                                "configura la pantalla de consentimiento y agrega tu cuenta como usuario de prueba. " +
                                "La app no pide ni guarda un Client Secret.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Cerrar")
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
