package com.serkka.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Auto-starts the step-counter foreground service after the device finishes booting,
 * so steps walked between reboot and the next app-open aren't lost.
 */
class StepCounterBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                StepCounterForegroundService.start(context)
            }
        }
    }
}
