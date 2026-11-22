package com.serkka.tracker

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Settings → Clear all data. Two levels:
 *  - [Level.TRAINING]: workouts, weigh-ins, sessions (heart rate too), notes, schedule and step
 *    history. Settings stay; the welcome screen shows again (so demo data can be loaded) and demo
 *    leftovers (the demo's height) go.
 *  - [Level.EVERYTHING]: that plus every setting, saved value, the app icon and cached files: the app
 *    starts as if freshly installed. Signing out of Google Drive is the caller's (it holds the client).
 * Files are deleted and preferences committed synchronously, because the caller restarts the process
 * right after (see feedback_clear_data_atomic).
 */
internal object DataReset {
    enum class Level { TRAINING, EVERYTHING }

    suspend fun clear(context: Context, level: Level) = withContext(Dispatchers.IO) {
        // A running timer is part of the data: stop it and forget its saved state
        runCatching { context.stopService(Intent(context, TimerForegroundService::class.java)) }

        WorkoutDatabase.getDatabase(context).close()
        WorkoutDatabase.resetInstance()
        val dbPath = context.getDatabasePath("workout_db").path
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbPath + it).delete() }

        when (level) {
            Level.TRAINING -> {
                val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val demoWasLoaded = appPrefs.getBoolean("demo_seeded", false)
                context.getSharedPreferences("step_counter", Context.MODE_PRIVATE).edit().clear().commit()
                context.getSharedPreferences("timer_state", Context.MODE_PRIVATE).edit().clear().commit()
                // Welcome screen again, with its Try with demo data button
                appPrefs.edit().remove("demo_seeded").remove("welcome_skip").commit()
                // The demo sets a height for BMI; a real one the user typed stays
                if (demoWasLoaded) {
                    PreferencesManager.getInstance(context).tracker.edit().remove("height_cm").commit()
                }
            }
            Level.EVERYTHING -> {
                // Every preferences file, emptied first (in-memory copies) and then deleted
                File(context.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { file ->
                    val name = file.name.removeSuffix(".xml")
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                    context.deleteSharedPreferences(name)
                }
                AppIconChanger.set(context, AppIcon.Red)
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
    }
}
