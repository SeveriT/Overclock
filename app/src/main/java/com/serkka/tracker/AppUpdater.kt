package com.serkka.tracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ── In-app updater (GitHub Releases) ──────────────────────────────────────────
// Checks github.com/SeveriT/Overclock for a newer release, downloads its APK and hands it to
// Android's package installer. Release APKs are all signed with the same key, so the update
// installs over the existing app and keeps its data. Releases come from the `githubRelease`
// Gradle task (tag "v<versionName>", one .apk asset).

private const val RELEASES_API = "https://api.github.com/repos/SeveriT/Overclock/releases/latest"
private const val PREF_LAST_CHECK = "update_last_check_ms"
private const val PREF_SKIPPED = "update_skipped_version"

/** Thrown when the phone is too full to download or install the update. */
class NotEnoughStorageException(val neededBytes: Long) : Exception("Not enough free storage")

/** A newer release found on GitHub. */
data class UpdateInfo(
    val version: String,      // "2.7.0"
    val notes: String,        // release body, lightly cleaned of markdown
    val apkUrl: String,
    val apkSizeBytes: Long
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Available(val info: UpdateInfo, val currentVersion: String) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float, val downloadedBytes: Long) : UpdateState
    /** Downloaded in the background on app open; waiting for the user to tap Install. */
    data class ReadyToInstall(val info: UpdateInfo, val apk: File, val currentVersion: String) : UpdateState
    data class NeedsInstallPermission(val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

object AppUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    // One automatic check per app launch (this object lives as long as the process)
    private var checkedThisLaunch = false

    /**
     * Automatic check every time the app is opened (once per launch, not on every screen
     * rebuild). Silent on errors and "up to date", and quiet about a skipped version.
     */
    fun autoCheck(context: Context) {
        if (checkedThisLaunch) return
        checkedThisLaunch = true
        val prefs = PreferencesManager.getInstance(context).tracker
        val now = System.currentTimeMillis()
        scope.launch {
            val info = runCatching { fetchLatest() }.getOrNull() ?: return@launch
            prefs.edit().putLong(PREF_LAST_CHECK, now).apply()
            val current = installedVersion(context)
            if (!isNewer(info.version, current) || prefs.getString(PREF_SKIPPED, null) == info.version) return@launch
            // Auto-download on any network (user's choice: unlimited mobile data)
            try {
                val apk = downloadApk(context.applicationContext, info, reportProgress = false)
                _state.value = UpdateState.ReadyToInstall(info, apk, current)
            } catch (e: Exception) {
                Log.e("AppUpdater", "Background update download failed", e)
                _state.value = UpdateState.Available(info, current)   // fall back to asking
            }
        }
    }

    /** Manual check from Settings: always hits GitHub and always reports the result. */
    fun checkNow(context: Context) {
        _state.value = UpdateState.Checking
        scope.launch {
            val current = installedVersion(context)
            _state.value = try {
                val info = fetchLatest()
                PreferencesManager.getInstance(context).tracker.edit()
                    .putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply()
                if (info != null && isNewer(info.version, current)) UpdateState.Available(info, current)
                else UpdateState.UpToDate(current)
            } catch (e: Exception) {
                Log.e("AppUpdater", "Update check failed", e)
                UpdateState.Failed("Couldn't reach GitHub. Check your connection and try again.")
            }
        }
    }

    fun skip(context: Context, version: String) {
        PreferencesManager.getInstance(context).tracker.edit().putString(PREF_SKIPPED, version).apply()
        dismiss()
    }

    fun dismiss() {
        downloadJob?.cancel()
        _state.value = UpdateState.Idle
    }

    /** Downloads the APK with progress, then opens the installer (or asks for permission first). */
    fun downloadAndInstall(context: Context, info: UpdateInfo) {
        val appContext = context.applicationContext
        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                _state.value = UpdateState.Downloading(info, 0f, 0L)
                val apk = downloadApk(appContext, info, reportProgress = true)
                if (!isActive) return@launch
                install(appContext, apk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: NotEnoughStorageException) {
                _state.value = UpdateState.Failed(storageMessage(e.neededBytes))
            } catch (e: Exception) {
                Log.e("AppUpdater", "Update download failed", e)
                _state.value = UpdateState.Failed("Download failed. Try again in a moment.")
            }
        }
    }

    /**
     * Downloads the release APK into cache/updates (older downloads are removed). An already
     * complete download of the same version is reused, so "Later" doesn't mean re-downloading.
     */
    private suspend fun downloadApk(context: Context, info: UpdateInfo, reportProgress: Boolean): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "Overclock-${info.version}.apk")
        if (apk.exists() && info.apkSizeBytes > 0 && apk.length() == info.apkSizeBytes) return apk
        dir.listFiles()?.forEach { it.delete() }
        // Room for the download itself plus what the installer needs afterwards
        if (info.apkSizeBytes > 0) {
            val needed = info.apkSizeBytes * INSTALL_SPACE_FACTOR
            if (!hasFreeSpace(context, needed)) throw NotEnoughStorageException(needed)
        }
        val part = File(dir, apk.name + ".part")

        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "Overclock-Android")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.apkSizeBytes
        val ctx = kotlin.coroutines.coroutineContext
        conn.inputStream.use { input ->
            part.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (ctx.isActive) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (reportProgress) {
                        _state.value = UpdateState.Downloading(
                            info, if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f, done
                        )
                    }
                }
            }
        }
        if (!ctx.isActive) { part.delete(); throw kotlinx.coroutines.CancellationException("Download cancelled") }
        // Only a complete file gets the final name, so a half download is never offered for install
        if (!part.renameTo(apk)) error("Couldn't finish saving the update")
        return apk
    }

    /** Opens Android's installer for [apk]; if installs from this app aren't allowed yet, says so. */
    fun install(context: Context, apk: File) {
        // Android may clear the cache folder when storage runs low, taking the download with it
        if (!apk.exists()) {
            _state.value = UpdateState.Failed("The downloaded update was cleared by Android. Check for updates again to re-download it.")
            return
        }
        // The installer copies the APK and unpacks it; with too little space it just closes
        // without saying why, so check first.
        val needed = apk.length() * (INSTALL_SPACE_FACTOR - 1)
        if (!hasFreeSpace(context, needed)) {
            _state.value = UpdateState.Failed(storageMessage(needed))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = UpdateState.NeedsInstallPermission(apk)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        _state.value = UpdateState.Idle
    }

    /** Opens the "Install unknown apps" switch for Overclock. */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ── Storage ─────────────────────────────────────────────────────────────

    /** Download + installer copy + unpacked app: roughly 3× the APK size. */
    private const val INSTALL_SPACE_FACTOR = 3L

    /** Space the system can give us, counting cache it's allowed to clear (same check the installer makes). */
    private fun hasFreeSpace(context: Context, bytes: Long): Boolean = runCatching {
        val sm = context.getSystemService(StorageManager::class.java)
        sm.getAllocatableBytes(sm.getUuidForPath(context.dataDir)) >= bytes
    }.getOrDefault(true)   // can't tell: let the installer try

    private fun storageMessage(neededBytes: Long): String {
        val mb = (neededBytes + 999_999) / 1_000_000
        return "Not enough free storage to update. Free up about $mb MB on your phone and try again."
    }

    // ── GitHub API ──────────────────────────────────────────────────────────

    private fun fetchLatest(): UpdateInfo? {
        val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Overclock-Android")   // GitHub requires a User-Agent
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        if (conn.responseCode == 404) return null                    // no releases published
        if (conn.responseCode !in 200..299) error("GitHub API ${conn.responseCode}")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val assets = json.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: return null
        return UpdateInfo(
            version = json.optString("tag_name").removePrefix("v").trim(),
            notes = cleanNotes(json.optString("body")),
            apkUrl = apk.getString("browser_download_url"),
            apkSizeBytes = apk.optLong("size")
        )
    }

    /** "2.10.0" > "2.9.3": compares numeric parts, missing parts count as 0. */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote); val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }; val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** GitHub's generated notes are markdown; keep them readable as plain text. */
    private fun cleanNotes(body: String): String =
        body.lines()
            .map { it.trim() }
            .filterNot { it.startsWith("**Full Changelog**") || it.isBlank() }
            .map { it.removePrefix("## ").removePrefix("### ").removePrefix("* ").removePrefix("- ") }
            .map { it.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1").replace("**", "") }   // [text](url) → text
            .joinToString("\n")
            .take(1200)
}
