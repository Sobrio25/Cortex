package com.aiagents.app.presentation.subscription

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.R
import com.aiagents.app.domain.model.ManagedInferenceUsage
import com.aiagents.app.domain.model.ManagedModel
import com.aiagents.app.domain.model.SubscriptionPlan
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val usage by viewModel.usage.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val lastInferenceUsage by viewModel.lastInferenceUsage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val products by viewModel.products.collectAsState()
    val privacyAccepted by viewModel.privacyAccepted.collectAsState()
    val googleSignedIn by viewModel.googleSignedIn.collectAsState()
    val privacyAcknowledged by viewModel.privacyAcknowledged.collectAsState()
    val activity = LocalContext.current.findActivity()
    val managedReady = privacyAccepted && googleSignedIn

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscription_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GatewayAccountCard(
                    googleSignedIn = googleSignedIn,
                    privacyAccepted = privacyAccepted,
                    privacyAcknowledged = privacyAcknowledged,
                    loading = uiState.loading,
                    activityAvailable = activity != null,
                    onSignIn = { activity?.let(viewModel::signInWithGoogle) },
                    onPrivacyAcknowledgedChange = viewModel::setPrivacyAcknowledged,
                    onActivateFreePlan = viewModel::activateFreePlan
                )
            }

            item {
                UsageCard(
                    planName = usage.plan.displayName,
                    percentage = if (usage.plan == SubscriptionPlan.FREE) {
                        usage.freeUsedPercentage
                    } else {
                        usage.remainingPercentage
                    },
                    detail = if (usage.plan == SubscriptionPlan.FREE) {
                        stringResource(
                            R.string.subscription_free_usage,
                            usage.freeUsedPercentage
                        )
                    } else {
                        stringResource(R.string.subscription_budget_remaining, usage.remainingPercentage)
                    }
                )
            }

            lastInferenceUsage?.let { inference ->
                item { LastInferenceCard(inference) }
            }

            item {
                Text(
                    "Modelos disponibles",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(catalog, key = { "managed-model-${it.id}" }) { model ->
                ManagedModelCard(model)
            }

            item {
                Text(
                    "Planes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(SubscriptionPlan.entries) { plan ->
                PlanCard(
                    plan = plan,
                    current = plan == usage.plan,
                    localizedPrice = viewModel.localizedPrice(plan),
                    storeReady = managedReady && (plan == SubscriptionPlan.FREE || products.containsKey(plan.productId)),
                    onPurchase = { if (managedReady && activity != null) viewModel.purchase(activity, plan) }
                )
            }

            item {
                TextButton(onClick = viewModel::restore, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.subscription_restore))
                }
            }

            if (uiState.loading) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            uiState.message?.let { message ->
                item {
                    Card(
                        onClick = viewModel::dismissMessage,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(message, Modifier.padding(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewayAccountCard(
    googleSignedIn: Boolean,
    privacyAccepted: Boolean,
    privacyAcknowledged: Boolean,
    loading: Boolean,
    activityAvailable: Boolean,
    onSignIn: () -> Unit,
    onPrivacyAcknowledgedChange: (Boolean) -> Unit,
    onActivateFreePlan: () -> Unit
) {
    val ready = googleSignedIn && privacyAccepted
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ready) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                    contentDescription = null
                )
                Text(
                    stringResource(R.string.gateway_account_title),
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            when {
                !googleSignedIn -> {
                    Text(
                        stringResource(R.string.gateway_sign_in_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = onSignIn,
                        enabled = activityAvailable && !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.google_sign_in_button))
                    }
                }

                !privacyAccepted -> {
                    Text(
                        stringResource(R.string.google_sign_in_complete),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.managed_privacy_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.managed_privacy_notice),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(
                            checked = privacyAcknowledged,
                            onCheckedChange = onPrivacyAcknowledgedChange,
                            enabled = !loading
                        )
                        Text(
                            stringResource(R.string.managed_privacy_acknowledgement),
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = onActivateFreePlan,
                        enabled = privacyAcknowledged && !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.gateway_activate_button))
                    }
                }

                else -> {
                    Text(
                        stringResource(R.string.gateway_connected_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LastInferenceCard(usage: ManagedInferenceUsage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Última inferencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(usage.provider, usage.modelUsed).joinToString(" · ").ifBlank { "Modelo no informado" },
                style = MaterialTheme.typography.bodyMedium
            )
            val estimated = if (usage.estimated) " (estimados)" else ""
            Text(
                "${NumberFormat.getIntegerInstance().format(usage.totalTokens)} tokens$estimated · ${usage.costLabel()}",
                style = MaterialTheme.typography.bodySmall
            )
            if (usage.fallback) {
                Text(
                    "Fallback: ${usage.fallbackReason ?: usage.fallbackCategory ?: "motivo no informado"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun ManagedModelCard(model: ManagedModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Desde ${model.minimumPlan.displayName}", style = MaterialTheme.typography.labelMedium)
            }
            Text("Contexto: ${model.contextLabel()}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Tools: ${model.capabilities.tools.displayLabel()} · " +
                    "visión: ${model.capabilities.vision.displayLabel()} · " +
                    "streaming: ${model.capabilities.streaming.displayLabel()} · " +
                    "razonamiento: ${model.capabilities.reasoning.displayLabel()}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(model.priceLabel(), style = MaterialTheme.typography.bodySmall)
            if (!model.selectable) {
                Text(
                    "Lo utiliza la selección automática; la selección manual está disponible desde Pro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (model.requiresFreeToolsWarning) {
                Text(
                    "Aviso: el modelo asignado por una ruta gratuita puede ignorar las tools; no están garantizadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun UsageCard(planName: String, percentage: Int, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null)
                Text(
                    text = planName,
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { percentage.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    current: Boolean,
    localizedPrice: String?,
    storeReady: Boolean,
    onPurchase: () -> Unit
) {
    val features = when (plan) {
        SubscriptionPlan.FREE -> listOf(stringResource(R.string.plan_free_feature))
        SubscriptionPlan.STARTER -> listOf("Selección automática esencial", stringResource(R.string.plan_fallback_feature))
        SubscriptionPlan.PLUS -> listOf("Selección automática avanzada", stringResource(R.string.plan_fallback_feature))
        SubscriptionPlan.PRO -> listOf(
            stringResource(R.string.plan_everything_previous),
            stringResource(R.string.plan_auto_manual_feature)
        )
        SubscriptionPlan.MAX -> listOf(
            stringResource(R.string.plan_everything_previous),
            "Más presupuesto y modelos avanzados"
        )
        SubscriptionPlan.ULTRA -> listOf(
            stringResource(R.string.plan_everything_previous),
            "Máximo presupuesto y catálogo completo"
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (current) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(plan.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    localizedPrice ?: if (plan == SubscriptionPlan.FREE) "$0" else "$${plan.monthlyPriceUsd}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            features.forEach { feature ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(feature, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (current) {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(stringResource(R.string.subscription_current_plan))
                }
            } else if (plan != SubscriptionPlan.FREE) {
                Button(
                    onClick = onPurchase,
                    enabled = storeReady,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text(
                        if (storeReady) stringResource(R.string.subscription_choose_plan)
                        else stringResource(R.string.subscription_store_pending)
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
