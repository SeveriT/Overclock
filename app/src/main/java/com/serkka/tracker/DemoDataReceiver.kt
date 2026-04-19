package com.serkka.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dev-only hook to seed demo data via adb:
 *   adb shell am broadcast -a com.serkka.tracker.SEED_DEMO -n com.serkka.tracker/.DemoDataReceiver
 *
 * Clears the seeded flag first so it always runs. To avoid duplicates, wipe data first:
 *   adb shell pm clear com.serkka.tracker
 */
class DemoDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("demo_seeded", false).apply()
                val db = WorkoutDatabase.getDatabase(appContext)
                val repo = WorkoutRepository(
                    db.workoutDao(),
                    db.bodyWeightDao(),
                    db.workoutSessionDao()
                )
                DemoData.seed(appContext, repo)
                Log.i("DemoDataReceiver", "Demo data seeded")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.serkka.tracker.SEED_DEMO"
    }
}
