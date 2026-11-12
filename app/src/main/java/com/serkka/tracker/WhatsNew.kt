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

// ── "What's new" after an update ─────────────────────────────────────────────
// Shown once, the first time a new version opens after an update (never on a fresh install:
// the welcome screen covers that). Only versions with notes below get a popup; patch releases
// without notes are marked as seen silently. Add the next version's notes at the top.

private class WhatsNewItem(val icon: ImageVector, val title: String, val text: String)

private val releaseNotes: Map<String, List<WhatsNewItem>> = mapOf(
    "3.0.0" to listOf(
        WhatsNewItem(Icons.Default.Favorite, "Heart rate",
            "Connect a Bluetooth strap from the heart under the timer. Live BPM while you train, and every timed session saves its heart rate graph."),
        WhatsNewItem(Icons.Default.Share, "Export to Strava",
            "Share icon in Sessions exports a .tcx file with time and heart rate. Upload it at strava.com/upload."),
        WhatsNewItem(Icons.Default.Menu, "New navbar",
            "Always visible, text labels only. While the timer runs, the Timer tab shows the time."),
        WhatsNewItem(Icons.Default.ShowChart, "Weight chart",
            "Pinch to zoom from 1 to 10 weeks, and a whole-kilo axis fitted to your weigh-ins."),
    ),
)

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
    return releaseNotes[current]?.let { current to it }
}

/** Place once in the main screen; shows the popup when there's something new. */
@Composable
fun WhatsNewHost() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf<Pair<String, List<WhatsNewItem>>?>(null) }
    LaunchedEffect(Unit) { notes = pendingNotes(context) }

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
