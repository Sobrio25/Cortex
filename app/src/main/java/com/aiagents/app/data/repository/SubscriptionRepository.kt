package com.aiagents.app.data.repository

import com.aiagents.app.data.remote.ManagedGatewayClient
import com.aiagents.app.data.remote.FreeDataConsentRequest
import com.aiagents.app.data.remote.PurchaseVerificationRequest
import com.aiagents.app.domain.model.ManagedModel
import com.aiagents.app.domain.model.ManagedModelCatalog
import com.aiagents.app.domain.model.SubscriptionPlan
import com.aiagents.app.domain.model.UsageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val gateway: ManagedGatewayClient
) {
    private val _usage = MutableStateFlow(UsageSnapshot())
    val usage: StateFlow<UsageSnapshot> = _usage.asStateFlow()

    private val _models = MutableStateFlow(ManagedModelCatalog.availableFor(_usage.value.plan))
    val models: StateFlow<List<ManagedModel>> = _models.asStateFlow()
    private val _catalog = MutableStateFlow(ManagedModelCatalog.availableFor(_usage.value.plan))
    val catalog: StateFlow<List<ManagedModel>> = _catalog.asStateFlow()
    val lastInferenceUsage = gateway.lastInferenceUsage

    suspend fun refresh(): Result<Unit> = runCatching {
        val account = gateway.account()
        _usage.value = account
        val catalog = runCatching { gateway.models() }
            .getOrDefault(ManagedModelCatalog.availableFor(account.plan))
            .filter { it.minimumPlan.ordinal <= account.plan.ordinal }
        _catalog.value = catalog
        _models.value = catalog.filter { it.available && it.selectable }
    }

    suspend fun verifyPurchase(productId: String, purchaseToken: String): Result<Unit> = runCatching {
        val account = gateway.verifyPurchase(PurchaseVerificationRequest(productId, purchaseToken))
        _usage.value = account
        refreshCatalog(account.plan)
    }

    suspend fun acceptFreeDataConsent(): Result<Unit> = runCatching {
        val account = gateway.acceptFreeDataConsent(FreeDataConsentRequest())
        _usage.value = account
        refreshCatalog(account.plan)
    }

    private suspend fun refreshCatalog(plan: SubscriptionPlan) {
        val catalog = runCatching { gateway.models() }
            .getOrDefault(ManagedModelCatalog.availableFor(plan))
            .filter { it.minimumPlan.ordinal <= plan.ordinal }
        _catalog.value = catalog
        _models.value = catalog.filter { it.available && it.selectable }
    }
}
