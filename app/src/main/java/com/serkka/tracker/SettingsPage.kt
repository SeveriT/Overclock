@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import kotlin.math.roundToInt

private const val BACKUP_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000  // 1 week (matches OverclockActivity.scheduleBackup)

// ── Settings page ─────────────────────────────────────────────────────────────

@Composable
fun SettingsPage(
    primaryColor: Color,
    themeViewModel: ThemeViewModel,
    viewModel: WorkoutViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
    isSubscribed: Boolean = false,
    onSubscribe: () -> Unit = {},
    onRecheckWhitelist: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }

    val workouts by viewModel.allWorkouts.collectAsState()
    val bodyWeights by viewModel.allBodyWeights.collectAsState()
    val notesList by viewModel.allNotes.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showUserGuide by remember { mutableStateOf(false) }
    var showHeartRateSheet by remember { mutableStateOf(false) }

    // ── Google Sign-In ────────────────────────────────────────────────────────
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }
    var googleAccount by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }
    val isGoogleSignedIn = googleAccount != null

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                googleAccount = account
                Toast.makeText(context, "Google Drive linked!", Toast.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                Log.e("SettingsPage", "Sign-in failed: ${e.statusCode}", e)
                Toast.makeText(context, "Sign-in failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Google Sign-In for premium activation (email-only, no Drive scope)
    val premiumGso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
    }
    val premiumSignInClient = remember { GoogleSignIn.getClient(context, premiumGso) }
    val premiumSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                task.getResult(ApiException::class.java)
                onRecheckWhitelist()
                Toast.makeText(context, "Checking premium status...", Toast.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                Log.e("SettingsPage", "Premium sign-in failed: ${e.statusCode}", e)
                Toast.makeText(context, "Sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Backup launchers ──────────────────────────────────────────────────────
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                if (backupManager.backupDatabase(it))
                    Toast.makeText(context, "Local backup successful", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                if (backupManager.restoreDatabase(uris)) {
                    Toast.makeText(context, "Restore successful! Restarting...", Toast.LENGTH_LONG).show()
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    context.startActivity(Intent.makeRestartActivityTask(intent?.component))
                    Runtime.getRuntime().exit(0)
                }
            }
        }
    }

    // ── Settings export / import (no training data) ──────────────────────────
    val settingsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                val ok = SettingsExport.export(context, it)
                Toast.makeText(context, if (ok) "Settings exported" else "Couldn't export settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val settingsImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                if (SettingsExport.import(context, it)) {
                    // Theme, timer and the rest are read at start-up: restart like a backup restore
                    Toast.makeText(context, "Settings imported. Restarting...", Toast.LENGTH_LONG).show()
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    context.startActivity(Intent.makeRestartActivityTask(intent?.component))
                    Runtime.getRuntime().exit(0)
                } else {
                    Toast.makeText(context, "That isn't an Overclock settings file", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Drive backup ──────────────────────────────────────────────────────────
    val performDriveBackup: () -> Unit = {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        } else {
            Toast.makeText(context, "Uploading to Drive...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val credential = GoogleAccountCredential.usingOAuth2(
                            context, Collections.singleton(DriveScopes.DRIVE_FILE)
                        ).apply { selectedAccount = account.account }

                        val driveService = Drive.Builder(
                            NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
                        ).setApplicationName("Tracker").build()

                        val driveHelper = GoogleDriveHelper(driveService)
                        val bundle = backupManager.buildBackupFile()
                        val fileId = bundle?.let {
                            driveHelper.uploadFile(it, "application/zip", "workout_backup_auto.zip")
                        }
                        bundle?.delete()

                        withContext(Dispatchers.Main) {
                            if (fileId != null) {
                                PreferencesManager.getInstance(context).backup
                                    .edit().putLong("last_backup_ms", System.currentTimeMillis()).apply()
                                Toast.makeText(context, "Drive backup successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Drive upload failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SettingsPage", "Manual backup failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    // A short list of categories; each opens its own page (Back or the arrow returns to the list).
    var category by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    BackHandler(enabled = category != null) { category = null }

    AnimatedContent(
        targetState = category,
        transitionSpec = {
            // Into a category: in from the right; back to the list: in from the left
            val forward = targetState != null
            (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
        },
        label = "settingsCategory"
    ) { shown ->
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Flat M3 settings layout: section headers + control groups on the background.
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (shown == null) {
            // ── Category list ───────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }
            items(SettingsCategory.entries.size) { i ->
                val cat = SettingsCategory.entries[i]
                SettingsRow(
                    icon = cat.icon,
                    title = cat.title,
                    subtitle = when (cat) {
                        SettingsCategory.Subscription -> if (isSubscribed) "Premium active" else "Free plan · unlock Drive backups"
                        SettingsCategory.Backup -> if (isGoogleSignedIn) "Drive linked · ${cat.subtitle}" else cat.subtitle
                        else -> cat.subtitle
                    },
                    primaryColor = primaryColor,
                    onClick = { category = cat }
                )
            }
            item { OverclockVersionFooter() }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        } else {
        item { SettingsCategoryHeader(shown.title, onBack = { category = null }) }
        when (shown) {
        SettingsCategory.Appearance -> {
        item {
            Box(modifier = Modifier.fillMaxWidth().animateContentSize().padding(horizontal = PageGutter)) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Auto = accent follows the music widget's album art; the swatches set the
                    // user's own colour, used whenever there's no song (ThemeViewModel).
                    val userAccent by themeViewModel.primaryColor.collectAsState()
                    val accentAuto by themeViewModel.accentFromMusic.collectAsState()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Accent Color",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { themeViewModel.updateAccentFromMusic(!accentAuto) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (accentAuto) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.semantics { stateDescription = if (accentAuto) "On" else "Off" }
                        ) {
                            if (accentAuto) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text("Auto")
                        }
                    }
                    if (accentAuto) {
                        Text(
                            "Takes the colour of the album art in the music widget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Preset swatches + Custom (hex entry), AccentPicker.kt
                    AccentColorPicker(
                        current = userAccent,
                        onPick = { themeViewModel.updatePrimaryColor(it) }
                    )

                    // ── Background color ──────────────────────────────────────
                    val backgroundColor by themeViewModel.backgroundColor.collectAsState()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Background Brightness",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { themeViewModel.resetBackgroundColor() },
                            enabled = backgroundColor != TrackerColors.Background
                        ) { Text("Reset") }
                    }

                    // Single neutral-gray slider (R = G = B). A previously tinted background
                    // shows at its average level and becomes neutral once dragged.
                    val grayLevel = (backgroundColor.red + backgroundColor.green + backgroundColor.blue) / 3f
                    val brightnessInteraction = remember { MutableInteractionSource() }
                    Slider(
                        value = grayLevel,
                        onValueChange = { v -> themeViewModel.updateBackgroundColor(Color(v, v, v)) },
                        // Capped near-black (see ThemeViewModel.BACKGROUND_MAX_CHANNEL).
                        valueRange = 0f..ThemeViewModel.BACKGROUND_MAX_CHANNEL,
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor
                        ),
                        interactionSource = brightnessInteraction,
                        // Bubble shows brightness as % of the allowed range
                        thumb = {
                            TrackerSliderThumb(
                                brightnessInteraction, primaryColor,
                                "${(grayLevel / ThemeViewModel.BACKGROUND_MAX_CHANNEL * 100).roundToInt()}%"
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(4.dp),
                                thumbTrackGapSize = 4.dp
                            )
                        }
                    )

                    val fontScale by themeViewModel.fontScale.collectAsState()
                    val fontLabel = when {
                        fontScale <= 0.8f -> "Tiny"
                        fontScale <= 0.95f -> "Small"
                        fontScale <= 1.07f -> "Default"
                        fontScale <= 1.2f -> "Large"
                        else -> "Extra Large"
                    }
                    Text(
                        "Font Size · $fontLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val fontInteraction = remember { MutableInteractionSource() }
                    Slider(
                        value = fontScale,
                        onValueChange = { themeViewModel.updateFontScale(it) },
                        valueRange = 0.7f..1.3f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor
                        ),
                        interactionSource = fontInteraction,
                        thumb = { TrackerSliderThumb(fontInteraction, primaryColor, fontLabel) },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(4.dp),
                                thumbTrackGapSize = 4.dp
                            )
                        }
                    )

                    // ── Launcher icon ─────────────────────────────────────────
                    AppIconPicker(primaryColor)
                }
            }
        }



        }

        // ── Music ───────────────────────────────────────────────────────
        SettingsCategory.Music -> {
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 4.dp)) {
                MusicAppPicker(primaryColor)
            }
        }

        }

        // ── Timer ───────────────────────────────────────────────────────
        SettingsCategory.Timer -> {
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 4.dp)) {
                RingBeepPicker(primaryColor)
            }
        }
        item {
            // Heart rate strap: name and battery (live while connected, else the last reading);
            // tapping opens the same strap sheet as the timer's heart rate chip
            val hrState by HeartRateMonitor.state.collectAsState()
            val battery by HeartRateMonitor.battery.collectAsState()
            LaunchedEffect(Unit) { HeartRateMonitor.loadSavedBattery(context) }
            val strapName = when (val s = hrState) {
                is HeartRateMonitor.State.Connected -> s.name
                is HeartRateMonitor.State.Connecting -> s.name
                is HeartRateMonitor.State.Reconnecting -> s.name
                // Re-read when the sheet closes (it can pair or forget a strap)
                HeartRateMonitor.State.Idle -> remember(showHeartRateSheet) { HeartRateMonitor.savedDeviceName(context) }
            }
            val subtitle = when {
                strapName == null -> "Not paired. Tap to find your strap"
                battery == null -> if (hrState is HeartRateMonitor.State.Connected) "$strapName · Connected" else strapName
                hrState is HeartRateMonitor.State.Connected -> "$strapName · Battery ${battery!!.percent} %"
                else -> "$strapName · Battery ${battery!!.percent} % " +
                    android.text.format.DateUtils.getRelativeTimeSpanString(
                        battery!!.atMs, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                    ).toString().let { "($it)" }
            }
            SettingsRow(
                icon = Icons.Default.MonitorHeart,
                title = "Heart rate strap",
                subtitle = subtitle,
                primaryColor = primaryColor,
                onClick = { showHeartRateSheet = true }
            )
        }

        }

        // ── Permissions ─────────────────────────────────────────────────
        SettingsCategory.Permissions -> {
        item {
            PermissionsCard(primaryColor = primaryColor)
        }
        }

        // ── Backup & data ───────────────────────────────────────────────
        SettingsCategory.Backup -> {
        item {
            Box(modifier = Modifier.fillMaxWidth().animateContentSize().padding(horizontal = PageGutter)) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    if (isGoogleSignedIn) {
                        // ── Signed-in profile ────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val photoUrl = googleAccount?.photoUrl
                            if (photoUrl != null) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Profile picture",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        googleAccount?.displayName?.firstOrNull()?.uppercase() ?: "?",
                                        color = MaterialTheme.colorScheme.surface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    googleAccount?.displayName ?: "Google Account",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    googleAccount?.email ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                googleSignInClient.signOut().addOnCompleteListener {
                                    googleAccount = null
                                    androidx.work.WorkManager.getInstance(context)
                                        .cancelUniqueWork("AutoBackupWork")
                                    context.getSharedPreferences("backup_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().remove("last_backup_ms").apply()
                                    Toast.makeText(context, "Google signed out", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign out",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        NextBackupCountdown(primaryColor = primaryColor)
                    }
                }
            }
        }

        // Backup actions: plain settings rows, not a wall of filled buttons
        item {
            Column(Modifier.fillMaxWidth()) {
                if (isGoogleSignedIn) {
                    SettingsRow(
                        icon = Icons.Default.CloudUpload,
                        title = "Back up to Drive now",
                        subtitle = if (isSubscribed) "Also runs automatically once a week" else "Premium feature",
                        primaryColor = primaryColor,
                        locked = !isSubscribed,
                        onClick = { if (isSubscribed) performDriveBackup() else onSubscribe() }
                    )
                } else {
                    SettingsRow(
                        icon = Icons.Default.AccountCircle,
                        title = "Connect Google Drive",
                        subtitle = if (isSubscribed) "Sign in to enable weekly cloud backups" else "Premium feature",
                        primaryColor = primaryColor,
                        locked = !isSubscribed,
                        onClick = {
                            if (isSubscribed) googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            else onSubscribe()
                        }
                    )
                }
                SettingsRow(
                    icon = Icons.Default.Save,
                    title = "Save local backup",
                    subtitle = "A .zip with your workouts and step history",
                    primaryColor = primaryColor,
                    onClick = { backupLauncher.launch("workout_backup.zip") }
                )
                SettingsRow(
                    icon = Icons.Default.SettingsBackupRestore,
                    title = "Restore from backup",
                    subtitle = "Replaces current data with a backup file",
                    primaryColor = primaryColor,
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) }
                )
                SettingsRow(
                    icon = Icons.Default.IosShare,
                    title = "Export settings",
                    subtitle = "Theme, timer, music, goals and app icon. No workouts, weigh-ins or notes",
                    primaryColor = primaryColor,
                    onClick = {
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        settingsExportLauncher.launch("overclock-settings-$date.json")
                    }
                )
                SettingsRow(
                    icon = Icons.Default.FileOpen,
                    title = "Import settings",
                    subtitle = "Replaces your settings; your workouts stay as they are",
                    primaryColor = primaryColor,
                    onClick = { settingsImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                )
                SettingsRow(
                    icon = Icons.Default.DeleteForever,
                    title = "Clear all data",
                    subtitle = "Delete your training data, or reset the whole app",
                    primaryColor = primaryColor,
                    destructive = true,
                    onClick = { showDeleteConfirmDialog = true }
                )
            }
        }
        }

        // ── Subscription ──────────────────────────────────────────────────
        SettingsCategory.Subscription -> {
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter)) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (isSubscribed) Icons.Default.CheckCircle else Icons.Default.Star,
                            null,
                            tint = if (isSubscribed) TrackerColors.Green else primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            if (isSubscribed) "Premium Active" else "Free Plan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isSubscribed) {
                        Text(
                            "Subscribe to unlock Drive backups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // The one filled button on this screen: it's the page's primary action
                        val subInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = onSubscribe,
                            interactionSource = subInteraction,
                            modifier = Modifier.fillMaxWidth().bounceClick(subInteraction),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = MaterialTheme.colorScheme.surface
                            )
                        ) { Text("Subscribe") }
                    }
                }
            }
        }
        item {
            if (isSubscribed) {
                SettingsRow(
                    icon = Icons.Default.CreditCard,
                    title = "Manage subscription",
                    subtitle = "Opens Google Play",
                    primaryColor = primaryColor,
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://play.google.com/store/account/subscriptions".toUri())
                        )
                    }
                )
            } else {
                SettingsRow(
                    icon = Icons.Default.AccountCircle,
                    title = "Have a premium account?",
                    subtitle = "Sign in with Google to activate it",
                    primaryColor = primaryColor,
                    onClick = { premiumSignInLauncher.launch(premiumSignInClient.signInIntent) }
                )
            }
        }

        }

        // ── Help ───────────────────────────────────────────────────────────
        SettingsCategory.Help -> {
        item {
            Column(Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = "User guide",
                    subtitle = "How every screen and feature works",
                    primaryColor = primaryColor,
                    onClick = { showUserGuide = true }
                )
                val updateState by AppUpdater.state.collectAsState()
                SettingsRow(
                    icon = Icons.Default.SystemUpdate,
                    title = "Check for updates",
                    subtitle = if (updateState is UpdateState.Checking) "Checking GitHub…"
                               else "Version ${AppUpdater.installedVersion(context)}",
                    primaryColor = primaryColor,
                    onClick = { AppUpdater.checkNow(context) }
                )
                SettingsRow(
                    icon = Icons.Default.Feedback,
                    title = "Contact support",
                    subtitle = "Send feedback or report a problem on GitHub",
                    primaryColor = primaryColor,
                    onClick = {
                        // New GitHub issue, pre-filled with what a bug report needs first.
                        // Nothing is sent until the user submits it on GitHub.
                        val body = "\n\n---\nOverclock ${AppUpdater.installedVersion(context)} · " +
                            "Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}"
                        val url = "https://github.com/SeveriT/Overclock/issues/new?body=${Uri.encode(body)}"
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                            .onFailure {
                                android.widget.Toast.makeText(context, "No browser found", android.widget.Toast.LENGTH_SHORT).show()
                            }
                    }
                )
            }
        }

        }
        }   // when (shown)
        }   // else: a category page
    }
    }   // AnimatedContent

    if (showDeleteConfirmDialog) {
        // Two levels (DataReset): training data only, or everything (like a fresh install)
        fun clearAndRestart(level: DataReset.Level) {
            coroutineScope.launch {
                try {
                    if (level == DataReset.Level.EVERYTHING && isGoogleSignedIn) {
                        runCatching { googleSignInClient.signOut() }
                    }
                    DataReset.clear(context, level)
                    showDeleteConfirmDialog = false
                    val message = if (level == DataReset.Level.EVERYTHING) "App reset. Restarting..."
                                  else "Training data deleted. Restarting..."
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    context.startActivity(Intent.makeRestartActivityTask(launchIntent?.component))
                    Runtime.getRuntime().exit(0)
                } catch (e: Exception) {
                    Toast.makeText(context, "Error deleting data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        TrackerDialog(
            title = "Clear data?",
            onDismiss = { showDeleteConfirmDialog = false },
            confirmLabel = "Everything",
            destructive = true,
            icon = Icons.Default.Warning,
            neutralLabel = "Training data",
            onNeutral = { clearAndRestart(DataReset.Level.TRAINING) },
            onConfirm = { clearAndRestart(DataReset.Level.EVERYTHING) }
        ) {
            Text("Training data: workouts, body weight, sessions, notes, the schedule and step history. Your settings stay.")
            Text("Everything: all of that plus every setting, the app icon and the Drive sign-in, as if freshly installed.")
            Text("This can't be undone.", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showUserGuide) {
        UserGuideDialog(onDismiss = { showUserGuide = false })
    }

    if (showHeartRateSheet) {
        HeartRateSheet(primaryColor = primaryColor, onDismiss = { showHeartRateSheet = false })
    }
}

// ── Categories ───────────────────────────────────────────────────────────────

/** Settings is a list of these; each opens a page with its own controls (SettingsPage). */
private enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Appearance("Appearance", "Accent colour, background, font size and app icon", Icons.Default.Palette),
    Music("Music", "Which app the music widget follows", Icons.Default.MusicNote),
    Timer("Timer", "Rest beep and heart rate strap", Icons.Default.Timer),
    Permissions("Permissions", "Notifications, music, steps, Bluetooth and battery", Icons.Default.Security),
    Backup("Backup & data", "Backups, settings export and clearing data", Icons.Default.Backup),
    Subscription("Subscription", "Premium and Drive backups", Icons.Default.WorkspacePremium),
    Help("Help", "User guide, updates and support", Icons.AutoMirrored.Filled.HelpOutline)
}

/** Top of a category page: back arrow and the category's name. */
@Composable
private fun SettingsCategoryHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = PageGutter, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to settings", tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── Settings row ──────────────────────────────────────────────────────────────

/**
 * Standard settings row: accent icon, title, one-line subtitle, chevron. Full-bleed and
 * tappable, like Android's own Settings. [destructive] turns icon + title red; [locked]
 * (premium-only) dims the row and swaps the chevron for a lock while staying tappable
 * (it opens the subscription offer).
 */
@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    primaryColor: Color,
    onClick: () -> Unit,
    destructive: Boolean = false,
    locked: Boolean = false
) {
    val accent = when {
        destructive -> MaterialTheme.colorScheme.error
        locked -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> primaryColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = PageGutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            if (locked) Icons.Default.Lock else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Backup countdown ──────────────────────────────────────────────────────────

@Composable
private fun NextBackupCountdown(primaryColor: Color) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context).backup }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    val lastBackupMs = prefs.getLong("last_backup_ms", 0L)
    val remainingMs = (lastBackupMs + BACKUP_INTERVAL_MS - now).coerceAtLeast(0L)
    val isDue = lastBackupMs == 0L || remainingMs == 0L

    val d = remainingMs / 86_400_000
    val h = (remainingMs % 86_400_000) / 3_600_000
    val m = (remainingMs % 3_600_000) / 60_000
    val s = (remainingMs % 60_000) / 1_000

    // Flat status row (was a tinted panel): icon + countdown, red only when a backup is due
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = if (isDue) Icons.Default.CloudOff else Icons.Default.CloudSync,
                contentDescription = null,
                tint = if (isDue) MaterialTheme.colorScheme.error else primaryColor,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDue) "Cloud backup due" else "Next cloud backup in",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isDue) {
                    Text(
                        // e.g. "6d 23:59:59", or "23:59:59" on the last day
                        text = if (d > 0) String.format("%dd %02d:%02d:%02d", d, h, m, s)
                               else String.format("%02d:%02d:%02d", h, m, s),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
            }
            if (lastBackupMs > 0L) {
                Text(
                    text = "Last: " + formatBackupDate(lastBackupMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionsCard(primaryColor: Color) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabledInSettings(context)) }
    var activityRecognitionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var batteryOptIgnored by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }
    var bluetoothGranted by remember { mutableStateOf(HeartRateMonitor.hasPermissions(context)) }

    // Re-check on resume (user may have toggled in system settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                listenerEnabled = isNotificationListenerEnabledInSettings(context)
                activityRecognitionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACTIVITY_RECOGNITION
                    ) == PackageManager.PERMISSION_GRANTED
                batteryOptIgnored = isIgnoringBatteryOptimizations(context)
                bluetoothGranted = HeartRateMonitor.hasPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Bluetooth (heart rate strap): scan + connect on Android 12+, location before that
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { bluetoothGranted = HeartRateMonitor.hasPermissions(context) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationGranted = granted }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> activityRecognitionGranted = granted }

    val listenerSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { listenerEnabled = isNotificationListenerEnabledInSettings(context) }

    // Flat list of permission rows (no container).
    Box(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(
            modifier = Modifier.padding(horizontal = PageGutter),
        ) {
            SettingsPermissionRow(
                icon = Icons.Default.Notifications,
                label = "Notifications",
                description = "Timer alerts and backup status",
                granted = notificationGranted,
                primaryColor = primaryColor,
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppInfo(context)
                    }
                },
                onRevoke = { openAppInfo(context) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsPermissionRow(
                icon = Icons.Default.MusicNote,
                label = "Notification Listener",
                description = "Music widget controls",
                granted = listenerEnabled,
                primaryColor = primaryColor,
                onGrant = {
                    listenerSettingsLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onRevoke = {
                    listenerSettingsLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsPermissionRow(
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                label = "Activity Recognition",
                description = "Step counter",
                granted = activityRecognitionGranted,
                primaryColor = primaryColor,
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    } else {
                        openAppInfo(context)
                    }
                },
                onRevoke = { openAppInfo(context) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsPermissionRow(
                icon = Icons.Default.BatteryFull,
                label = "Unrestricted Battery",
                description = "Keep step tracking running in the background",
                granted = batteryOptIgnored,
                primaryColor = primaryColor,
                onGrant = { requestIgnoreBatteryOptimizations(context) },
                onRevoke = { openAppInfo(context) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsPermissionRow(
                icon = Icons.Default.Bluetooth,
                label = "Bluetooth",
                description = "Heart rate strap",
                granted = bluetoothGranted,
                primaryColor = primaryColor,
                onGrant = { bluetoothLauncher.launch(HeartRateMonitor.requiredPermissions()) },
                onRevoke = { openAppInfo(context) }
            )
        }
    }
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true
}

@SuppressLint("BatteryLife")
internal fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Some OEMs reject the targeted intent — fall back to the generic battery settings page
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(fallback)
    }
}

@Composable
private fun SettingsPermissionRow(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    primaryColor: Color,
    onGrant: () -> Unit,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (granted) TrackerColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            TextButton(onClick = onRevoke) {
                Text("Manage", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        } else {
            TextButton(onClick = onGrant) {
                Text("Enable", color = primaryColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun isNotificationListenerEnabledInSettings(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val component = ComponentName(context, MediaNotificationListener::class.java)
    return flat.contains(component.flattenToString())
}

private fun openAppInfo(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}

/**
 * App name + version at the bottom of Settings. Extra: tap it 7 times (like Android's
 * build number) to overclock: the accent cycles through the rainbow for 10 seconds.
 */
@Composable
private fun OverclockVersionFooter() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val rainbow by Extras.rainbow.collectAsState()
    var taps by remember { mutableIntStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    val tapsNeeded = 7
    val text = when {
        rainbow -> "\u26A1 Overclocked!"
        taps in 4 until tapsNeeded -> "${tapsNeeded - taps} more\u2026"
        else -> "Overclock ${AppUpdater.installedVersion(context)}"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (rainbow) FontWeight.Bold else FontWeight.Normal,
        color = if (rainbow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                val now = System.currentTimeMillis()
                taps = if (now - lastTapMs < 1_500L) taps + 1 else 1   // taps must come in quick succession
                lastTapMs = now
                if (taps >= tapsNeeded) {
                    taps = 0
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Extras.overclock()
                }
            }
            .padding(vertical = 16.dp)
    )
}
