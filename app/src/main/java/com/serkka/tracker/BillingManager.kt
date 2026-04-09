package com.serkka.tracker

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BillingManager(context: Context) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "premium_monthly"
        private const val RC_KEY_PREMIUM_EMAILS = "premium_emails"
        private const val RC_KEY_GEMINI_API_KEY = "gemini_api_key"

        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val remoteConfig = FirebaseRemoteConfig.getInstance().apply {
        setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 1800 })
        setDefaultsAsync(mapOf(
            RC_KEY_PREMIUM_EMAILS to "",
            RC_KEY_GEMINI_API_KEY to BuildConfig.GEMINI_API_KEY
        ))
    }

    val geminiApiKey: String
        get() = remoteConfig.getString(RC_KEY_GEMINI_API_KEY).ifBlank { BuildConfig.GEMINI_API_KEY }

    private val _isWhitelisted = MutableStateFlow(false)
    val isWhitelisted: StateFlow<Boolean> = _isWhitelisted

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    private fun getPremiumEmails(): Set<String> {
        return remoteConfig.getString(RC_KEY_PREMIUM_EMAILS)
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun checkWhitelist(): Boolean {
        return try {
            val accounts = AccountManager.get(appContext).getAccountsByType("com.google")
            accounts.any { it.name.lowercase() in getPremiumEmails() }
        } catch (_: Exception) { false }
    }

    private fun fetchRemoteEmails() {
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            _isWhitelisted.value = checkWhitelist()
            if (_isWhitelisted.value) {
                _isSubscribed.value = true
            }
        }
    }

    private val _isReady = MutableStateFlow(false)

    private var productDetails: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    init {
        fetchRemoteEmails()
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isReady.value = true
                    querySubscription()
                    queryProductDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
                _isReady.value = false
            }
        })
    }

    private fun querySubscription() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchases.any { purchase ->
                    purchase.products.contains(PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                _isSubscribed.value = active || _isWhitelisted.value

                // Acknowledge any unacknowledged purchases
                purchases.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
                }.forEach { purchase ->
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                    ) { /* no-op */ }
                }
            }
        }
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()
        ) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && detailsList.isNotEmpty()) {
                productDetails = detailsList.first()
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val details = productDetails ?: run {
            // Not ready yet, try reconnecting
            connect()
            return
        }

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    _isSubscribed.value = true
                    if (!purchase.isAcknowledged) {
                        billingClient.acknowledgePurchase(
                            AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                        ) { /* no-op */ }
                    }
                }
            }
        }
    }

    fun refreshStatus() {
        if (_isReady.value) {
            querySubscription()
        } else {
            connect()
        }
    }

    fun getFormattedPrice(): String? {
        return productDetails?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
    }
}
