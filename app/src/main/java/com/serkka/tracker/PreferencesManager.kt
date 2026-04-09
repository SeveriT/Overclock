package com.serkka.tracker

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Centralized access to all SharedPreferences used in the app.
 */
class PreferencesManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    val strava: SharedPreferences by lazy {
        appContext.getSharedPreferences("strava_prefs", Context.MODE_PRIVATE)
    }

    val theme: SharedPreferences by lazy {
        appContext.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    }

    val tracker: SharedPreferences by lazy {
        appContext.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
    }

    val backup: SharedPreferences by lazy {
        appContext.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    }

    val ai: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "ai_prefs",
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context).also { instance = it }
            }
        }
    }
}
