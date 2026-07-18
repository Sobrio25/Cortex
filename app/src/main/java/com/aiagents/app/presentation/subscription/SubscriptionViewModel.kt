package com.aiagents.app.presentation.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.auth.FirebaseAuthManager
import com.aiagents.app.data.billing.PlayBillingManager
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
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
    private val securePreferences: SecurePreferences,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val errorReporter: AppErrorReporter
) : ViewModel() {
    val usage: StateFlow<UsageSnapshot> = repository.usage
    val models = repository.models
    val catalog = repository.catalog
    val lastInferenceUsage = repository.lastInferenceUsage
    val products = billing.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState = _uiState.asStateFlow()
    private val _privacyAccepted = MutableStateFlow(securePreferences.isManagedPrivacyAccepted())
    val privacyAccepted = _privacyAccepted.asStateFlow()
    private val _googleSignedIn = MutableStateFlow(firebaseAuthManager.isGoogleSignedIn)
    val googleSignedIn = _googleSignedIn.asStateFlow()
    private val _privacyAcknowledged = MutableStateFlow(false)
    val privacyAcknowledged = _privacyAcknowledged.asStateFlow()

    init {
        billing.connect()
        viewModelScope.launch {
            repository.refresh().onFailure {
                _uiState.value = SubscriptionUiState(false, purchaseError(it, "billing_refresh"))
            }.onSuccess {
                applyAccountConsent()
                _uiState.value = SubscriptionUiState(false)
            }
        }
        viewModelScope.launch {
            billing.purchaseUpdates.collect(::processPurchase)
        }
        viewModelScope.launch {
            billing.errors.collect { detail ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    message = purchaseError(IllegalStateException(detail), "billing_client")
                )
            }
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

    fun signInWithGoogle(activity: Activity) {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            _uiState.value = SubscriptionUiState(loading = true)
            runCatching {
                firebaseAuthManager.signInWithGoogle(activity)
                check(firebaseAuthManager.isGoogleSignedIn) {
                    "No se pudo vincular la cuenta de Google"
                }
                _googleSignedIn.value = true
                repository.refresh().getOrThrow()
                applyAccountConsent()
            }.onSuccess {
                _uiState.value = SubscriptionUiState(loading = false)
            }.onFailure {
                _googleSignedIn.value = firebaseAuthManager.isGoogleSignedIn
                _uiState.value = SubscriptionUiState(
                    loading = false,
                    message = purchaseError(it, "google_sign_in")
                )
            }
        }
    }

    fun setPrivacyAcknowledged(acknowledged: Boolean) {
        _privacyAcknowledged.value = acknowledged
    }

    fun activateFreePlan() {
        if (
            _uiState.value.loading ||
            !_googleSignedIn.value ||
            !_privacyAcknowledged.value
        ) return

        viewModelScope.launch {
            _uiState.value = SubscriptionUiState(loading = true)
            repository.acceptFreeDataConsent()
                .onSuccess {
                    securePreferences.enableManagedFreePlan()
                    _privacyAccepted.value = true
                    _privacyAcknowledged.value = false
                    _uiState.value = SubscriptionUiState(
                        loading = false
                    )
                }
                .onFailure {
                    _uiState.value = SubscriptionUiState(
                        loading = false,
                        message = purchaseError(it, "consent_acceptance")
                    )
                }
        }
    }

    private fun applyAccountConsent() {
        val accepted = repository.usage.value.hasCurrentFreeDataConsent
        _privacyAccepted.value = accepted
        if (accepted) {
            securePreferences.enableManagedFreePlan()
        } else {
            securePreferences.setManagedPrivacyAccepted(false)
        }
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
                    _uiState.value = SubscriptionUiState(
                        false,
                        purchaseError(it, "purchase_verification")
                    )
                }
        }
    }

    private fun purchaseError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(component = "subscription", operation = operation)
        ).displayMessage
}
