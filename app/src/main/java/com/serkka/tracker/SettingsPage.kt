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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

private const val BACKUP_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000  // 1 week (matches MainActivity.scheduleBackup)

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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Flat M3 settings layout: section headers + control groups on the background.
        contentPadding = PaddingValues(top = topPadding + 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item { SectionHeader("Appearance") }

        item {
            Box(modifier = Modifier.fillMaxWidth().animateContentSize().padding(horizontal = PageGutter)) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Accent Color (RGB)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy((-8).dp)) {
                        listOf(
                            Triple("R", primaryColor.red) { v: Float -> themeViewModel.updatePrimaryColor(primaryColor.copy(red = v)) },
                            Triple("G", primaryColor.green) { v: Float -> themeViewModel.updatePrimaryColor(primaryColor.copy(green = v)) },
                            Triple("B", primaryColor.blue) { v: Float -> themeViewModel.updatePrimaryColor(primaryColor.copy(blue = v)) }
                        ).forEach { (_, value, onValueChange) ->
                            val rgbInteraction = remember { MutableInteractionSource() }
                            Slider(
                                value = value,
                                onValueChange = onValueChange,
                                colors = SliderDefaults.colors(
                                    thumbColor = primaryColor,
                                    activeTrackColor = primaryColor
                                ),
                                interactionSource = rgbInteraction,
                                // Bubble shows the channel as 0–255
                                thumb = { TrackerSliderThumb(rgbInteraction, primaryColor, "${(value * 255).roundToInt()}") },
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        modifier = Modifier.height(4.dp),
                                        thumbTrackGapSize = 4.dp
                                    )
                                }
                            )
                        }
                    }

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
                }
            }
        }



        // ── Permissions ─────────────────────────────────────────────────
        item { SectionHeader("Permissions") }
        item {
            PermissionsCard(primaryColor = primaryColor)
        }

        item { SectionHeader("Backup & Restore") }

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
                    icon = Icons.Default.DeleteForever,
                    title = "Clear all data",
                    subtitle = "Delete everything and restart the app",
                    primaryColor = primaryColor,
                    destructive = true,
                    onClick = { showDeleteConfirmDialog = true }
                )
            }
        }
        // ── Subscription ──────────────────────────────────────────────────
        item { SectionHeader("Subscription") }
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

        // ── Help ───────────────────────────────────────────────────────────
        item { SectionHeader("Help") }
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
                    icon = Icons.Default.Email,
                    title = "Contact support",
                    subtitle = "Send feedback or report a problem",
                    primaryColor = primaryColor,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = "mailto:support@seppo.tech".toUri()
                            putExtra(Intent.EXTRA_SUBJECT, "Overclock Support")
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (showDeleteConfirmDialog) {
        TrackerDialog(
            title = "Delete all data?",
            onDismiss = { showDeleteConfirmDialog = false },
            confirmLabel = "Delete all",
            destructive = true,
            icon = Icons.Default.Warning,
            onConfirm = {
                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    WorkoutDatabase.getDatabase(context).close()
                                    WorkoutDatabase.resetInstance()
                                    val dbPath = context.getDatabasePath("workout_db")
                                    java.io.File(dbPath.path).delete()
                                    java.io.File(dbPath.path + "-wal").delete()
                                    java.io.File(dbPath.path + "-shm").delete()
                                    java.io.File(dbPath.path + "-journal").delete()
                                    context.getSharedPreferences("step_counter", Context.MODE_PRIVATE)
                                        .edit().clear().commit()
                                    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                        .edit().remove("demo_seeded").commit()
                                }
                                showDeleteConfirmDialog = false
                                Toast.makeText(context, "All data deleted. Restarting...", Toast.LENGTH_LONG).show()
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                context.startActivity(Intent.makeRestartActivityTask(launchIntent?.component))
                                Runtime.getRuntime().exit(0)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error deleting data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
            }
        ) {
            Text("This will permanently delete all workouts, body weight entries, sessions, notes, and step counter history.")
            Text("This can't be undone.", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showUserGuide) {
        UserGuideDialog(onDismiss = { showUserGuide = false })
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
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                icon = Icons.Default.DirectionsWalk,
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
