package com.aiagents.app.presentation.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.billing.PlayBillingManager
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.repository.SubscriptionRepository
import com.aiagents.app.domain.model.SubscriptionPlan
import com.aiagents.app.domain.model.UsageSnapshot
import com.android.billingclient.api.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val loading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val billing: PlayBillingManager,
    private val securePreferences: SecurePreferences
) : ViewModel() {
    val usage: StateFlow<UsageSnapshot> = repository.usage
    val products = billing.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState = _uiState.asStateFlow()
    private val _privacyAccepted = MutableStateFlow(securePreferences.isManagedPrivacyAccepted())
    val privacyAccepted = _privacyAccepted.asStateFlow()

    init {
        billing.connect()
        viewModelScope.launch {
            repository.refresh().onFailure {
                _uiState.value = SubscriptionUiState(false, it.message)
            }.onSuccess {
                _uiState.value = SubscriptionUiState(false)
            }
        }
        viewModelScope.launch {
            billing.purchaseUpdates.collect(::processPurchase)
        }
        viewModelScope.launch {
            billing.errors.collect { _uiState.value = _uiState.value.copy(loading = false, message = it) }
        }
    }

    fun purchase(activity: Activity, plan: SubscriptionPlan) {
        _uiState.value = _uiState.value.copy(message = null)
        billing.launchPurchase(activity, plan)
    }

    fun restore() {
        _uiState.value = _uiState.value.copy(message = null)
        billing.restorePurchases()
    }

    fun acceptPrivacyAndEnableFreePlan() {
        securePreferences.enableManagedFreePlan()
        _privacyAccepted.value = true
        viewModelScope.launch { repository.refresh() }
    }

    fun localizedPrice(plan: SubscriptionPlan): String? = billing.localizedPrice(plan)

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun processPurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, message = null)
            repository.verifyPurchase(productId, purchase.purchaseToken)
                .onSuccess {
                    billing.acknowledge(purchase)
                    _uiState.value = SubscriptionUiState(false, "Plan activado")
                }
                .onFailure {
                    _uiState.value = SubscriptionUiState(false, it.message ?: "No se pudo verificar la compra")
                }
        }
    }
}
