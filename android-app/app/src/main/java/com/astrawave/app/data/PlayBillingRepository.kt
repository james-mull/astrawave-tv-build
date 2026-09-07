package com.astrawave.app.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.astrawave.app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google Play subscription client. A Play purchase never grants AstraWave Premium locally.
 * Purchased tokens are submitted to the AstraWave backend, which verifies Google Play and writes
 * the read-only /entitlements/{uid} document. Only that server entitlement unlocks Premium.
 */
class PlayBillingRepository(context: Context) : PurchasesUpdatedListener {
    data class PremiumOffer(
        val productDetails: ProductDetails,
        val offerToken: String,
        val formattedPrice: String,
    )

    sealed interface Event {
        data class Status(val message: String) : Event
        data class Verified(val purchaseToken: String) : Event
        data class Pending(val message: String) : Event
        data class Error(val message: String) : Event
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val auth get() = FirebaseAuth.getInstance()
    private val productId = BuildConfig.ASTRAWAVE_PREMIUM_PRODUCT_ID
    private var eventListener: ((Event) -> Unit)? = null

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun setEventListener(listener: ((Event) -> Unit)?) {
        eventListener = listener
    }

    fun connect(onComplete: (Result<Unit>) -> Unit = {}) {
        if (billingClient.isReady) {
            onComplete(Result.success(Unit))
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onComplete(Result.success(Unit))
                } else {
                    onComplete(Result.failure(IllegalStateException(result.debugMessage.ifBlank { "Google Play Billing unavailable" })))
                }
            }

            override fun onBillingServiceDisconnected() {
                eventListener?.invoke(Event.Status("Google Play disconnected; AstraWave will reconnect automatically."))
            }
        })
    }

    fun loadPremiumOffer(onComplete: (Result<PremiumOffer>) -> Unit) {
        connect { connection ->
            if (connection.isFailure) {
                onComplete(Result.failure(connection.exceptionOrNull() ?: IllegalStateException("Billing unavailable")))
                return@connect
            }
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build()
            billingClient.queryProductDetailsAsync(params) { result, queryResult ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    onComplete(Result.failure(IllegalStateException(result.debugMessage.ifBlank { "Unable to load Premium from Google Play" })))
                    return@queryProductDetailsAsync
                }
                val details = queryResult.productDetailsList.firstOrNull { it.productId == productId }
                val offer = details?.subscriptionOfferDetails
                    ?.sortedWith(compareBy<ProductDetails.SubscriptionOfferDetails> { it.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros ?: Long.MAX_VALUE }
                        .thenBy { it.basePlanId })
                    ?.firstOrNull()
                if (details == null || offer == null) {
                    onComplete(Result.failure(IllegalStateException("AstraWave Premium is not available from Google Play on this account/device yet.")))
                    return@queryProductDetailsAsync
                }
                val displayPrice = offer.pricingPhases.pricingPhaseList
                    .lastOrNull { it.priceAmountMicros > 0L }
                    ?.formattedPrice
                    ?: offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
                    ?: "$19.99"
                onComplete(Result.success(PremiumOffer(details, offer.offerToken, displayPrice)))
            }
        }
    }

    fun launchPremium(activity: Activity, offer: PremiumOffer): BillingResult {
        if (!billingClient.isReady) {
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                .setDebugMessage("Google Play Billing is reconnecting")
                .build()
        }
        if (auth.currentUser == null) {
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.ERROR)
                .setDebugMessage("Sign in to AstraWave before purchasing Premium so the purchase can be verified to your account.")
                .build()
        }
        val item = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.productDetails)
            .setOfferToken(offer.offerToken)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(item))
            .build()
        return billingClient.launchBillingFlow(activity, params)
    }

    /** Re-queries active subscriptions so purchases from another device/out-of-app can be restored. */
    fun restore(onComplete: (Result<Int>) -> Unit) {
        connect { connection ->
            if (connection.isFailure) {
                onComplete(Result.failure(connection.exceptionOrNull() ?: IllegalStateException("Billing unavailable")))
                return@connect
            }
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    onComplete(Result.failure(IllegalStateException(result.debugMessage.ifBlank { "Unable to restore subscriptions" })))
                    return@queryPurchasesAsync
                }
                val relevant = purchases.filter { productId in it.products }
                relevant.forEach(::processPurchase)
                onComplete(Result.success(relevant.size))
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().filter { productId in it.products }.forEach(::processPurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> eventListener?.invoke(Event.Status("Purchase canceled."))
            else -> eventListener?.invoke(Event.Error(result.debugMessage.ifBlank { "Google Play purchase failed (${result.responseCode})" }))
        }
    }

    private fun processPurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> eventListener?.invoke(Event.Pending("Payment is pending. Premium stays locked until Google Play confirms payment."))
            Purchase.PurchaseState.PURCHASED -> verifyOnBackend(purchase)
            else -> eventListener?.invoke(Event.Status("Purchase is not currently active."))
        }
    }

    private fun verifyOnBackend(purchase: Purchase) {
        val user = auth.currentUser
        if (user == null) {
            eventListener?.invoke(Event.Error("Sign in to AstraWave to verify this Google Play purchase."))
            return
        }
        val base = BuildConfig.ASTRAWAVE_API_BASE_URL.trimEnd('/')
        if (base.isBlank()) {
            eventListener?.invoke(Event.Error("AstraWave purchase verification service is not configured in this build."))
            return
        }
        eventListener?.invoke(Event.Status("Verifying Premium with AstraWave…"))
        user.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                val idToken = tokenResult.token
                if (idToken.isNullOrBlank()) {
                    eventListener?.invoke(Event.Error("Could not obtain an AstraWave sign-in token for purchase verification."))
                    return@addOnSuccessListener
                }
                scope.launch {
                    val verified = runCatching { postForVerification(base, idToken, purchase.purchaseToken) }
                    verified.onSuccess { accepted ->
                        if (!accepted) {
                            eventListener?.invoke(Event.Error("Google Play purchase could not be verified. Premium was not activated."))
                            return@onSuccess
                        }
                        acknowledgeAfterVerification(purchase)
                    }.onFailure { error ->
                        eventListener?.invoke(Event.Error(error.message ?: "Purchase verification failed"))
                    }
                }
            }
            .addOnFailureListener { error -> eventListener?.invoke(Event.Error(error.message ?: "AstraWave sign-in verification failed")) }
    }

    private fun acknowledgeAfterVerification(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            eventListener?.invoke(Event.Verified(purchase.purchaseToken))
            return
        }
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                eventListener?.invoke(Event.Verified(purchase.purchaseToken))
            } else {
                eventListener?.invoke(Event.Error("Premium was verified, but Google Play acknowledgement failed: ${result.debugMessage}"))
            }
        }
    }

    private fun postForVerification(base: String, firebaseIdToken: String, purchaseToken: String): Boolean {
        val endpoint = URL("$base/billing/verify")
        val connection = endpoint.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $firebaseIdToken")
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("packageName", BuildConfig.APPLICATION_ID)
                .put("productId", productId)
                .put("purchaseToken", purchaseToken)
                .toString()
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                throw IOException(JSONObject(responseText.ifBlank { "{}" }).optString("error").ifBlank { "Verification service returned HTTP ${connection.responseCode}" })
            }
            JSONObject(responseText).optBoolean("verified", false)
        } finally {
            connection.disconnect()
        }
    }

    fun close() {
        eventListener = null
        billingClient.endConnection()
    }
}
