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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.aiagents.app.domain.model.SubscriptionPlan
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val usage by viewModel.usage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val products by viewModel.products.collectAsState()
    val privacyAccepted by viewModel.privacyAccepted.collectAsState()
    val googleSignedIn by viewModel.googleSignedIn.collectAsState()
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
                UsageCard(
                    planName = usage.plan.displayName,
                    remainingPercentage = usage.remainingPercentage,
                    detail = if (usage.plan == SubscriptionPlan.FREE) {
                        stringResource(
                            R.string.subscription_free_usage,
                            NumberFormat.getIntegerInstance().format(usage.freeTokensUsed),
                            NumberFormat.getIntegerInstance().format(usage.freeTokensLimit)
                        )
                    } else {
                        stringResource(R.string.subscription_budget_remaining, usage.remainingPercentage)
                    }
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.managed_privacy_notice),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!managedReady) {
                            Button(
                                onClick = {
                                    activity?.let(viewModel::signInWithGoogleAndEnableFreePlan)
                                },
                                enabled = activity != null && !uiState.loading,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            ) {
                                Text(stringResource(R.string.subscription_accept_privacy))
                            }
                        }
                    }
                }
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
private fun UsageCard(planName: String, remainingPercentage: Int, detail: String) {
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
                progress = { remainingPercentage.coerceIn(0, 100) / 100f },
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
        SubscriptionPlan.STARTER -> listOf("DeepSeek V4 Flash", stringResource(R.string.plan_fallback_feature))
        SubscriptionPlan.PLUS -> listOf("DeepSeek V4 Pro", stringResource(R.string.plan_fallback_feature))
        SubscriptionPlan.PRO -> listOf(
            "GPT-5.6 Luna · DeepSeek V4 Pro · MiMo 2.5 Pro",
            "Kimi K2.7 Code · MiniMax M3 · Grok 4.5",
            stringResource(R.string.plan_auto_manual_feature)
        )
        SubscriptionPlan.MAX -> listOf(
            stringResource(R.string.plan_everything_previous),
            "GPT-5.6 Terra · GLM 5.2 · Claude Sonnet 5 · Claude Opus 4.8"
        )
        SubscriptionPlan.ULTRA -> listOf(
            stringResource(R.string.plan_everything_previous),
            "GPT-5.6 Sol · Claude Fable 5"
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
