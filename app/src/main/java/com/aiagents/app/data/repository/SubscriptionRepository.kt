package com.aiagents.app.data.repository

import com.aiagents.app.data.remote.ManagedGatewayClient
import com.aiagents.app.data.remote.PurchaseVerificationRequest
import com.aiagents.app.domain.model.ManagedModel
import com.aiagents.app.domain.model.ManagedModelCatalog
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

    suspend fun refresh(): Result<Unit> = runCatching {
        val account = gateway.account()
        _usage.value = account
        _models.value = runCatching { gateway.models() }
            .getOrDefault(ManagedModelCatalog.availableFor(account.plan))
            .filter { it.minimumPlan.ordinal <= account.plan.ordinal }
    }

    suspend fun verifyPurchase(productId: String, purchaseToken: String): Result<Unit> = runCatching {
        val account = gateway.verifyPurchase(PurchaseVerificationRequest(productId, purchaseToken))
        _usage.value = account
        _models.value = ManagedModelCatalog.availableFor(account.plan)
    }
}
