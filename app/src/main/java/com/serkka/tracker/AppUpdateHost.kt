package com.serkka.tracker

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Renders the in-app update flow ([AppUpdater.state]) as small dialogs. Place once at the root
 * of the app UI; Settings' "Check for updates" and the daily auto-check both feed it.
 */
@Composable
fun AppUpdateHost() {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsState()

    when (val s = state) {
        UpdateState.Idle, UpdateState.Checking -> Unit

        is UpdateState.UpToDate -> TrackerDialog(
            title = "You're up to date",
            onDismiss = AppUpdater::dismiss,
            confirmLabel = "OK",
            onConfirm = AppUpdater::dismiss,
            dismissLabel = null
        ) { Text("Overclock ${s.currentVersion} is the latest version.") }

        is UpdateState.Available -> TrackerDialog(
            title = "Update available",
            icon = Icons.Default.SystemUpdate,
            onDismiss = AppUpdater::dismiss,
            confirmLabel = "Update",
            onConfirm = { AppUpdater.downloadAndInstall(context, s.info) },
            dismissLabel = "Later",
            neutralLabel = "Skip",
            onNeutral = { AppUpdater.skip(context, s.info.version) }
        ) {
            Text(
                "Version ${s.info.version} · you have ${s.currentVersion}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (s.info.notes.isNotBlank()) {
                Text(
                    s.info.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                )
            }
        }

        is UpdateState.Downloading -> TrackerDialog(
            title = "Downloading update",
            onDismiss = {},                       // keep open; Cancel stops it
            confirmLabel = "Cancel",
            onConfirm = AppUpdater::dismiss,
            dismissLabel = null
        ) {
            Text("Overclock ${s.info.version}")
            LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(TrackerShapes.Pill),
                drawStopIndicator = {},
                gapSize = 0.dp
            )
            val mb = { bytes: Long -> String.format(Locale.getDefault(), "%.1f", bytes / 1_048_576f) }
            Text(
                if (s.info.apkSizeBytes > 0) "${mb(s.downloadedBytes)} / ${mb(s.info.apkSizeBytes)} MB"
                else "${mb(s.downloadedBytes)} MB",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Auto-downloaded in the background on app open: one tap to Android's installer
        is UpdateState.ReadyToInstall -> TrackerDialog(
            title = "Update ready",
            icon = Icons.Default.SystemUpdate,
            onDismiss = AppUpdater::dismiss,
            confirmLabel = "Install",
            onConfirm = { AppUpdater.install(context, s.apk) },
            dismissLabel = "Later",
            neutralLabel = "Skip",
            onNeutral = { AppUpdater.skip(context, s.info.version) }
        ) {
            Text(
                "Version ${s.info.version} is downloaded · you have ${s.currentVersion}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (s.info.notes.isNotBlank()) {
                Text(
                    s.info.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                )
            }
        }

        is UpdateState.NeedsInstallPermission -> TrackerDialog(
            title = "Allow installing updates",
            onDismiss = AppUpdater::dismiss,
            confirmLabel = "Install",
            onConfirm = { AppUpdater.install(context, s.apk) },
            dismissLabel = "Cancel",
            neutralLabel = "Open settings",
            onNeutral = { AppUpdater.openInstallPermissionSettings(context) }
        ) {
            Text("Android needs your permission for Overclock to install its own updates.")
            Text("Tap Open settings, turn on \"Allow from this source\", come back and tap Install.")
        }

        is UpdateState.Failed -> TrackerDialog(
            title = "Update failed",
            onDismiss = AppUpdater::dismiss,
            confirmLabel = "OK",
            onConfirm = AppUpdater::dismiss,
            dismissLabel = null
        ) { Text(s.message) }
    }
}
