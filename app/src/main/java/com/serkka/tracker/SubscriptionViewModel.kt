package com.serkka.tracker

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val billingManager = BillingManager.getInstance(application)

    val isSubscribed: StateFlow<Boolean> = billingManager.isSubscribed

    fun launchPurchase(activity: Activity) {
        billingManager.launchPurchaseFlow(activity)
    }

    fun refreshStatus() {
        billingManager.refreshStatus()
    }

    fun recheckWhitelist() {
        billingManager.recheckWhitelist()
    }

    fun getFormattedPrice(): String? {
        return billingManager.getFormattedPrice()
    }
}
