package com.serkka.tracker

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ── Music app for the widget ─────────────────────────────────────────────────
// The widget reads any media session the same way (title, artist, art, progress, controls). The
// user picks ONE app in Settings → Music; the widget, its launch button and accent Auto follow only
// that app. Each package also needs a <queries> entry in the manifest so getLaunchIntentForPackage
// can see it (Android 11+).

internal enum class MusicApp(val packageName: String, val label: String) {
    Spotify("com.spotify.music", "Spotify"),
    Metrolist("com.metrolist.music", "Metrolist");   // YouTube Music client (github.com/MetrolistGroup/Metrolist)
}

internal object MusicApps {
    private const val PREF_APP = "music_app"
    private var state: MutableStateFlow<MusicApp>? = null

    /** The chosen app, live: the widget and accent switch as soon as it changes in Settings. */
    fun selected(context: Context): StateFlow<MusicApp> = state ?: synchronized(this) {
        state ?: MutableStateFlow(
            PreferencesManager.getInstance(context).tracker.getString(PREF_APP, null)
                .let { name -> MusicApp.entries.firstOrNull { it.name == name } ?: MusicApp.Spotify }
        ).also { state = it }
    }

    fun select(context: Context, app: MusicApp) {
        PreferencesManager.getInstance(context).tracker.edit().putString(PREF_APP, app.name).apply()
        (selected(context) as MutableStateFlow).value = app
        MediaRepository.getInstance().refresh()   // follow the new app's session straight away
    }

    fun isInstalled(context: Context, app: MusicApp) =
        context.packageManager.getLaunchIntentForPackage(app.packageName) != null
}

/** Settings → Music: which app the music widget controls. */
@Composable
internal fun MusicAppPicker(primaryColor: Color) {
    val context = LocalContext.current
    val selected by MusicApps.selected(context).collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Music App", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicApp.entries.forEach { app ->
                FilterChip(
                    selected = app == selected,
                    onClick = { MusicApps.select(context, app) },
                    label = { Text(app.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                        selectedLabelColor = primaryColor
                    )
                )
            }
        }
        Text(
            if (MusicApps.isInstalled(context, selected)) "The music widget shows and controls ${selected.label}."
            else "${selected.label} isn't installed. The music widget shows it once it is.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
