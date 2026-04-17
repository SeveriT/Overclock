@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import com.serkka.tracker.TrackerColors.StravaOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

private const val BACKUP_INTERVAL_MS = 24L * 60 * 60 * 1000  // 24 hours

// ── Settings page ─────────────────────────────────────────────────────────────

@Composable
fun SettingsPage(
    primaryColor: Color,
    themeViewModel: ThemeViewModel,
    stravaViewModel: StravaViewModel,
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
    val activities by stravaViewModel.activities.collectAsState()
    val isLoading by stravaViewModel.isLoading.collectAsState()
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
        ActivityResultContracts.CreateDocument("application/octet-stream")
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
                        val db = WorkoutDatabase.getDatabase(context)
                        db.query(androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
                            .use { cursor -> cursor.moveToFirst() }

                        val dbFile = context.getDatabasePath("workout_db")
                        val fileId = driveHelper.uploadFile(dbFile, "application/x-sqlite3", "workout_backup_auto.db")

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
        contentPadding = PaddingValues(top = topPadding + 16.dp, start = 16.dp, end = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsButton(
                        label = "User Guide",
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        containerColor = primaryColor,
                        onClick = { showUserGuide = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingsButton(
                        label = "Contact Support",
                        icon = Icons.Default.Email,
                        containerColor = primaryColor,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = "mailto:support@seppo.tech".toUri()
                                putExtra(Intent.EXTRA_SUBJECT, "Overclock Support")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp, pressedElevation = 4.dp, hoveredElevation = 10.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                            Slider(
                                value = value,
                                onValueChange = onValueChange,
                                colors = SliderDefaults.colors(
                                    thumbColor = primaryColor,
                                    activeTrackColor = primaryColor
                                ),
                                thumb = {
                                    SliderDefaults.Thumb(
                                        interactionSource = remember { MutableInteractionSource() },
                                        modifier = Modifier.size(16.dp)
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
                        }
                    }

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
                    Slider(
                        value = fontScale,
                        onValueChange = { themeViewModel.updateFontScale(it) },
                        valueRange = 0.7f..1.3f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor
                        ),
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                modifier = Modifier.size(16.dp)
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
                }
            }
        }



        item {
            Text(
                "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp, pressedElevation = 4.dp, hoveredElevation = 10.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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

                        // Drive Backup button
                        SettingsButton(
                            label = "Drive Backup",
                            icon = Icons.Default.CloudUpload,
                            containerColor = if (isSubscribed) primaryColor else primaryColor.copy(alpha = 0.4f),
                            onClick = { if (isSubscribed) performDriveBackup() else onSubscribe() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // ── Sign-in button ───────────────────────────
                        SettingsButton(
                            label = "Sign in with Google",
                            icon = Icons.Default.AccountCircle,
                            containerColor = if (isSubscribed) primaryColor else primaryColor.copy(alpha = 0.4f),
                            onClick = {
                                if (isSubscribed) googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                else onSubscribe()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Row 2: Local + Restore
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsButton(
                            label = "Local Backup",
                            icon = Icons.Default.Save,
                            containerColor = primaryColor,
                            onClick = { backupLauncher.launch("workout_backup.db") },
                            modifier = Modifier.weight(1f)
                        )
                        SettingsButton(
                            label = "Restore Data",
                            icon = Icons.Default.SettingsBackupRestore,
                            containerColor = primaryColor,
                            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 3: Strava login (only when not linked)
                    if (activities.isEmpty() && !isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingsButton(
                                label = "Login with Strava",
                                icon = Icons.Default.DirectionsRun,
                                containerColor = if (isSubscribed) StravaOrange else StravaOrange.copy(alpha = 0.4f),
                                onClick = {
                                    if (isSubscribed) {
                                        val authUri = "https://www.strava.com/oauth/mobile/authorize".toUri()
                                            .buildUpon()
                                            .appendQueryParameter("client_id", STRAVA_CLIENT_ID)
                                            .appendQueryParameter("redirect_uri", "tracker-app://localhost")
                                            .appendQueryParameter("response_type", "code")
                                            .appendQueryParameter("approval_prompt", "force")
                                            .appendQueryParameter("scope", "activity:read_all,activity:write,profile:read_all")
                                            .build()
                                        context.startActivity(Intent(Intent.ACTION_VIEW, authUri))
                                    } else onSubscribe()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingsButton(
                                label = "Strava Sign Out",
                                icon = Icons.Default.DirectionsRun,
                                containerColor = StravaOrange,
                                onClick = {
                                    stravaViewModel.logout()
                                    Toast.makeText(context, "Strava signed out", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Row 4: Delete all data
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsButton(
                            label = "Clear All Data",
                            icon = Icons.Default.DeleteForever,
                            containerColor = Color(0xFFEE3E3E).copy(alpha = 0.8f),
                            contentColor = Color.Black,
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        // ── Subscription ──────────────────────────────────────────────────
        item {
            Text(
                "Subscription",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
        }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (isSubscribed) Icons.Default.CheckCircle else Icons.Default.Star,
                            null,
                            tint = if (isSubscribed) Color(0xFF4AC067) else primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            if (isSubscribed) "Premium Active" else "Free Plan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isSubscribed) {
                        val manageInteraction = remember { MutableInteractionSource() }
                        SettingsButton(
                            label = "Manage Subscription",
                            icon = Icons.Default.CreditCard,
                            containerColor = primaryColor,
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://play.google.com/store/account/subscriptions".toUri())
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            "Subscribe to unlock Drive backups, Strava, and AI assistant.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Text(
                            "Have a premium account?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SettingsButton(
                            label = "Sign in with Google",
                            icon = Icons.Default.AccountCircle,
                            containerColor = primaryColor,
                            onClick = { premiumSignInLauncher.launch(premiumSignInClient.signInIntent) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (showDeleteConfirmDialog) {
        val deleteInteractionSource = remember { MutableInteractionSource() }
        val cancelInteractionSource = remember { MutableInteractionSource() }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text("Delete All Data?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will permanently delete:\n\n• All workouts\n• All body weight entries\n• All notes\n\nThis action cannot be undone!",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                workouts.forEach { viewModel.deleteWorkout(it) }
                                bodyWeights.forEach { viewModel.deleteBodyWeight(it) }
                                notesList.forEach { viewModel.deleteNote(it) }
                                sessions.forEach { viewModel.deleteWorkoutSession(it) }
                                showDeleteConfirmDialog = false
                                Toast.makeText(context, "All data deleted successfully", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error deleting data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    interactionSource = deleteInteractionSource,
                    modifier = Modifier.bounceClick(deleteInteractionSource),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All", color = Color.White) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    interactionSource = cancelInteractionSource,
                    modifier = Modifier.bounceClick(cancelInteractionSource)
                ) { Text("Cancel") }
            }
        )
    }

    if (showUserGuide) {
        UserGuideDialog(onDismiss = { showUserGuide = false })
    }
}

@Composable
fun UserGuideDialog(onDismiss: () -> Unit) {
    val gotItInteractionSource = remember { MutableInteractionSource() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("User Guide", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GuideSection("1. Navigation", "Swipe horizontally to glide between screens. The bottom navbar hides when scrolling down and reappears when scrolling up.")
                GuideSection("2. Workout Timer", "Start/Pause with the main button. Tap the timer ring to start a new lap. Use the status bar notification to track time outside the app. Completed sessions are saved locally.")
                GuideSection("3. Logging Workouts", "Add sets and reps using the (+) button. Suggestions from your history will appear as you type. Use the search icon to filter exercises by name. Workouts are grouped by day with expandable cards — today's card is open by default.")
                GuideSection("4. AI Assistant", "Generate personalized workouts and full programs with AI. Use quick prompts or type your own. Save selected exercises directly to your workout log. The AI reads your last 50 exercises to suggest realistic weights. Premium feature.")
                GuideSection("5. Home", "Weekly summary with activity count and streaks combining local sessions and Strava activities. Shows steps, latest weight, recent activity, and this week's exercises. Tap the eye-off icon on any card to hide it — a matching icon appears in the top bar to bring it back.")
                GuideSection("6. Step Counter", "Daily step tracking via the device's built-in step sensor. Tap the pencil icon to edit your daily goal. You'll get a notification when the goal is reached. A 7-day bar chart shows weekly progress.")
                GuideSection("7. Weight Tracking", "Log body weight entries with optional notes. Set your height to see RPI (Reciprocal Ponderal Index) and BMI. RPI is more accurate for muscular builds. Tap the metric to update your height.")
                GuideSection("8. Sessions", "View saved workout sessions from the timer or add them manually. Sessions count toward your weekly activity streak. Tap 'See all' on the Home screen to jump here.")
                GuideSection("9. Calendar", "View your activity history with daily dot indicators combining Strava activities and local sessions. The calendar dynamically extends back to cover all your Strava history. Week numbers are shown for weeks without activity.")
                GuideSection("10. Music Widget", "Control Spotify directly. The progress bar waves while music plays. Tap to open Spotify. Swipe left or right to skip tracks. The widget picks an accent color from the current album art.")
                GuideSection("11. Strava", "Link your Strava account in Settings to sync activities. Your Strava workouts will appear in Sessions, Calendar, and count toward streaks. Premium feature.")
                GuideSection("12. Backups", "Enable Google Drive backups in Settings to keep your data safe. Automatic backups run every 24 hours when signed in. You can also create local backups manually. Premium feature.")
                GuideSection("13. Customization", "Change your primary accent color using the RGB sliders in Settings. Adjust the font size slider (0.7–1.3) to scale the whole UI. The entire app adapts to your choices.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                interactionSource = gotItInteractionSource,
                modifier = Modifier.bounceClick(gotItInteractionSource)
            ) { Text("Got it") }
        }
    )
}

@Composable
private fun GuideSection(title: String, description: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Reusable settings button ──────────────────────────────────────────────────

@Composable
private fun SettingsButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Black
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.height(50.dp).bounceClick(interactionSource),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, fontSize = 12.sp, textAlign = TextAlign.Left)
        }
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

    val h = remainingMs / 3_600_000
    val m = (remainingMs % 3_600_000) / 60_000
    val s = (remainingMs % 60_000) / 1_000

    Surface(
        color = if (isDue) MaterialTheme.colorScheme.errorContainer else primaryColor.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        text = String.format("%02d:%02d:%02d", h, m, s),
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
