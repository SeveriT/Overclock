package com.serkka.tracker

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Settings-only export / import: every preference that is a setting (theme, accent, timer, music app,
 * step goal, charts, tools, hidden Stats exercises, app icon) and none of the training data (workouts,
 * weigh-ins, notes, sessions, step history live in the database and step store, which aren't touched).
 * Things that only make sense on this phone (paired heart-rate strap, update checks, What's new seen,
 * welcome / demo flags, a running timer) are left out too. The file is JSON, readable and small.
 */
internal object SettingsExport {
    private const val FORMAT = "overclock-settings"
    private const val VERSION = 1

    /** Preference files that hold nothing but settings: exported whole, replaced on import. */
    private val SETTINGS_FILES = listOf("theme_prefs", "timer_prefs")

    /** tracker_prefs is mostly settings; these keys aren't, and stay as they are on import. */
    private const val TRACKER_FILE = "tracker_prefs"
    private val TRACKER_NOT_SETTINGS = setOf(
        "update_last_check_ms", "update_skipped_version", "update_last_auto_check_ms",
        "hr_device_address", "hr_device_name",
        "whats_new_seen_version",
    )

    /** step_counter holds step history; only these two are settings. */
    private const val STEP_FILE = "step_counter"
    private val STEP_SETTINGS = setOf("step_goal", "card_visible")

    suspend fun export(context: Context, destination: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = JSONObject()
            // serializePrefs gives JSON text: parse it, or it would be stored as one escaped string
            SETTINGS_FILES.forEach { name -> prefs.put(name, JSONObject(serializePrefs(context.prefs(name)))) }
            prefs.put(TRACKER_FILE, JSONObject(serializePrefs(context.prefs(TRACKER_FILE)) { it !in TRACKER_NOT_SETTINGS }))
            prefs.put(STEP_FILE, JSONObject(serializePrefs(context.prefs(STEP_FILE)) { it in STEP_SETTINGS }))
            val root = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("exported_at", System.currentTimeMillis())
                .put("app_icon", AppIconChanger.current(context).name)
                .put("prefs", prefs)
            context.contentResolver.openOutputStream(destination, "wt")?.use { it.write(root.toString(2).toByteArray()) }
                ?: error("Can't write to the chosen file")
            true
        }.getOrElse {
            Log.e("SettingsExport", "Export failed", it)
            false
        }
    }

    /** Applies a settings file; false if it isn't one (or can't be read). The caller restarts the app. */
    suspend fun import(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
                ?: error("Can't read the chosen file")
            val root = JSONObject(text)
            require(root.optString("format") == FORMAT) { "Not an Overclock settings file" }
            val prefs = root.getJSONObject("prefs")
            var applied = 0
            SETTINGS_FILES.forEach { name ->
                prefs.section(name)?.let {
                    applyPrefsJson(context.prefs(name), it)
                    applied++
                }
            }
            prefs.section(TRACKER_FILE)?.let { json ->
                applyPrefsJson(context.prefs(TRACKER_FILE), json, clearFirst = false) { it !in TRACKER_NOT_SETTINGS }
                applied++
            }
            prefs.section(STEP_FILE)?.let { json ->
                applyPrefsJson(context.prefs(STEP_FILE), json, clearFirst = false) { it in STEP_SETTINGS }
                applied++
            }
            // A file without any settings in it isn't a success
            require(applied > 0) { "No settings in the file" }
            root.optString("app_icon").takeIf { it.isNotEmpty() }?.let { name ->
                AppIcon.entries.firstOrNull { it.name == name }?.let { AppIconChanger.set(context, it) }
            }
            true
        }.getOrElse {
            Log.e("SettingsExport", "Import failed", it)
            false
        }
    }

    private fun Context.prefs(name: String) = getSharedPreferences(name, Context.MODE_PRIVATE)

    /**
     * One preferences file's section as JSON text: an object, or (in files from the first version of
     * this export) the same JSON stored as a string. Null when missing.
     */
    private fun JSONObject.section(name: String): String? =
        optJSONObject(name)?.toString() ?: optString(name).takeIf { it.trimStart().startsWith("{") }
}
