package com.aiagents.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.aiagents.app.domain.model.SubscriptionPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayBillingManager @Inject constructor(
    @ApplicationContext context: Context
) : PurchasesUpdatedListener {
    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products.asStateFlow()

    private val _purchaseUpdates = MutableSharedFlow<Purchase>(extraBufferCapacity = 8)
    val purchaseUpdates: SharedFlow<Purchase> = _purchaseUpdates.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (billingClient.isReady) {
            refreshProducts()
            restorePurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshProducts()
                    restorePurchases()
                } else {
                    _errors.tryEmit(result.debugMessage.ifBlank { "Google Play Billing no está disponible" })
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun refreshProducts() {
        if (!billingClient.isReady) return
        val products = SubscriptionPlan.entries.mapNotNull { plan ->
            plan.productId?.let {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = detailsResult.productDetailsList.associateBy { it.productId }
            } else {
                _errors.tryEmit(result.debugMessage.ifBlank { "No se pudieron cargar los planes" })
            }
        }
    }

    fun launchPurchase(activity: Activity, plan: SubscriptionPlan) {
        val productId = plan.productId ?: return
        val product = _products.value[productId]
        if (product == null) {
            refreshProducts()
            _errors.tryEmit("El plan todavía no está disponible en Google Play")
            return
        }
        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken.isNullOrBlank()) {
            _errors.tryEmit("El plan no tiene una oferta mensual disponible")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offerToken)
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _errors.tryEmit(result.debugMessage.ifBlank { "No se pudo abrir la compra" })
        }
    }

    fun restorePurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach(_purchaseUpdates::tryEmit)
            } else {
                _errors.tryEmit(result.debugMessage.ifBlank { "No se pudieron restaurar las compras" })
            }
        }
    }

    fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _errors.tryEmit(result.debugMessage.ifBlank { "No se pudo confirmar la compra" })
            }
        }
    }

    fun localizedPrice(plan: SubscriptionPlan): String? = plan.productId
        ?.let(_products.value::get)
        ?.subscriptionOfferDetails
        ?.firstOrNull()
        ?.pricingPhases
        ?.pricingPhaseList
        ?.firstOrNull { it.priceAmountMicros > 0 }
        ?.formattedPrice

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    _purchaseUpdates.tryEmit(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _errors.tryEmit(result.debugMessage.ifBlank { "La compra no pudo completarse" })
        }
    }
}
