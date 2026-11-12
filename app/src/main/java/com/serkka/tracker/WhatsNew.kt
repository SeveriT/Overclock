package com.serkka.tracker

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ── "What's new" after an update ─────────────────────────────────────────────
// Shown once, the first time a new version opens after an update (never on a fresh install:
// the welcome screen covers that). The notes live in assets/release_notes.json, which the
// githubRelease Gradle task also publishes as the GitHub release notes, so both always match.
// Versions without notes are marked as seen silently.

private class WhatsNewItem(val icon: ImageVector, val title: String, val text: String)

/** Icon names usable in release_notes.json (R8 strips unused icons, so they're listed here). */
private fun iconFor(name: String): ImageVector = when (name) {
    "favorite" -> Icons.Default.Favorite
    "share" -> Icons.Default.Share
    "navbar" -> Icons.Default.Menu
    "chart" -> Icons.Default.ShowChart
    "timer" -> Icons.Default.Timer
    "weight" -> Icons.Default.MonitorWeight
    "workout" -> Icons.Default.FitnessCenter
    "calendar" -> Icons.Default.CalendarMonth
    "home" -> Icons.Default.Home
    "settings" -> Icons.Default.Settings
    "backup" -> Icons.Default.CloudUpload
    "update" -> Icons.Default.SystemUpdate
    "bug" -> Icons.Default.BugReport
    "speed" -> Icons.Default.Speed
    else -> Icons.Default.Star
}

/** Notes for [version] from assets/release_notes.json, or null if it has none. */
private fun loadNotes(context: Context, version: String): List<WhatsNewItem>? = runCatching {
    val json = JSONObject(context.assets.open("release_notes.json").bufferedReader().use { it.readText() })
    val items = json.optJSONArray(version) ?: return null
    (0 until items.length()).map { i ->
        val o = items.getJSONObject(i)
        WhatsNewItem(iconFor(o.optString("icon")), o.getString("title"), o.getString("text"))
    }.takeIf { it.isNotEmpty() }
}.getOrNull()

private const val PREF_SEEN = "whats_new_seen_version"

/** Notes to show now, or null. Marks the current version as seen either way. */
private fun pendingNotes(context: Context): Pair<String, List<WhatsNewItem>>? {
    val prefs = PreferencesManager.getInstance(context).tracker
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val current = info.versionName ?: return null
    val seen = prefs.getString(PREF_SEEN, null)
    if (seen == current) return null
    prefs.edit().putString(PREF_SEEN, current).apply()
    // Fresh install: nothing is "new" yet. (Updates keep firstInstallTime, so it differs.)
    val isUpdate = info.lastUpdateTime != info.firstInstallTime
    if (!isUpdate) return null
    return loadNotes(context, current)?.let { current to it }
}

/** Place once in the main screen; shows the popup when there's something new. */
@Composable
fun WhatsNewHost() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf<Pair<String, List<WhatsNewItem>>?>(null) }
    LaunchedEffect(Unit) { notes = withContext(Dispatchers.IO) { pendingNotes(context) } }

    // Don't stack on top of an update prompt; show once that's gone
    val updateState by AppUpdater.state.collectAsState()
    val updateIdle = updateState is UpdateState.Idle || updateState is UpdateState.Checking

    val (version, items) = notes ?: return
    if (!updateIdle) return
    TrackerDialog(
        title = "What's new in $version",
        onDismiss = { notes = null },
        confirmLabel = "Got it",
        onConfirm = { notes = null },
        dismissLabel = null
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp).size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(item.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
