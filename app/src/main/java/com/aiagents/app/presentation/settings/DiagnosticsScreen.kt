package com.aiagents.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.aiagents.app.R
import com.aiagents.app.data.diagnostics.AppHealthMonitor
import com.aiagents.app.data.diagnostics.AppHealthSnapshot
import com.aiagents.app.data.diagnostics.DiagnosticToolPhase
import com.aiagents.app.data.diagnostics.DiagnosticTraceStatus
import com.aiagents.app.data.diagnostics.DiagnosticTraceStore
import com.aiagents.app.data.diagnostics.DiagnosticTurnTrace
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val traceStore: DiagnosticTraceStore,
    appHealthMonitor: AppHealthMonitor
) : ViewModel() {
    val traces = traceStore.traces
    val appHealth = appHealthMonitor.snapshot

    fun clear() = traceStore.clear()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val traces by viewModel.traces.collectAsState()
    val appHealth by viewModel.appHealth.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clear, enabled = traces.isNotEmpty()) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.diagnostics_clear)
                        )
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
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.diagnostics_privacy_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { AppHealthCard(appHealth) }
            if (traces.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.diagnostics_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            } else {
                items(traces, key = { it.id }) { trace ->
                    DiagnosticTraceCard(trace)
                }
            }
        }
    }
}

@Composable
private fun AppHealthCard(snapshot: AppHealthSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.diagnostics_app_health),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            DiagnosticValue(
                label = stringResource(R.string.diagnostics_current_startup),
                value = snapshot.currentStartupMs?.let { "$it ms" }
                    ?: stringResource(R.string.diagnostics_not_available)
            )
            DiagnosticValue(
                label = stringResource(R.string.diagnostics_previous_startup),
                value = snapshot.previousStartupMs?.let { "$it ms" }
                    ?: stringResource(R.string.diagnostics_not_available)
            )
            DiagnosticValue(
                label = stringResource(R.string.diagnostics_last_exit),
                value = snapshot.lastExitReason
                    ?: stringResource(R.string.diagnostics_not_available)
            )
            snapshot.lastUncaughtErrorType?.let { errorType ->
                DiagnosticValue(
                    label = stringResource(R.string.diagnostics_last_crash),
                    value = errorType
                )
            }
        }
    }
}

@Composable
private fun DiagnosticTraceCard(trace: DiagnosticTurnTrace) {
    val calls = trace.toolEvents.filter { it.phase == DiagnosticToolPhase.CALL }
    val results = trace.toolEvents.count { it.phase == DiagnosticToolPhase.RESULT }
    val resultsLabel = stringResource(R.string.diagnostics_results_count, results)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusLabel(trace.status),
                    style = MaterialTheme.typography.titleSmall,
                    color = statusColor(trace.status),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                        .format(Date(trace.updatedAtEpochMs)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = trace.id,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )
            DiagnosticValue(
                label = stringResource(R.string.diagnostics_provider_model),
                value = "${trace.provider} · ${trace.model}"
            )
            DiagnosticValue(
                label = stringResource(R.string.diagnostics_agent),
                value = trace.agent
            )
            DiagnosticValue(
                label = stringResource(R.string.diagnostics_requests_duration),
                value = "${trace.requestCount} · ${trace.providerDurationMs} ms"
            )
            DiagnosticValue(
                label = stringResource(R.string.diagnostics_context_metadata),
                value = stringResource(
                    R.string.diagnostics_context_value,
                    trace.messageCount,
                    trace.exposedToolCount
                )
            )
            if (calls.isNotEmpty() || results > 0) {
                DiagnosticValue(
                    label = stringResource(R.string.diagnostics_tools),
                    value = buildString {
                        if (calls.isNotEmpty()) append(calls.joinToString { it.name })
                        if (results > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(resultsLabel)
                        }
                    }
                )
            }
            trace.errorCategory?.let { category ->
                DiagnosticValue(
                    label = stringResource(R.string.diagnostics_error),
                    value = category
                )
            }
        }
    }
}

@Composable
private fun DiagnosticValue(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun statusLabel(status: DiagnosticTraceStatus): String = stringResource(
    when (status) {
        DiagnosticTraceStatus.REQUESTING -> R.string.diagnostics_status_requesting
        DiagnosticTraceStatus.AWAITING_TOOLS -> R.string.diagnostics_status_awaiting_tools
        DiagnosticTraceStatus.COMPLETED -> R.string.diagnostics_status_completed
        DiagnosticTraceStatus.FAILED -> R.string.diagnostics_status_failed
        DiagnosticTraceStatus.CANCELLED -> R.string.diagnostics_status_cancelled
    }
)

@Composable
private fun statusColor(status: DiagnosticTraceStatus) = when (status) {
    DiagnosticTraceStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    DiagnosticTraceStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
