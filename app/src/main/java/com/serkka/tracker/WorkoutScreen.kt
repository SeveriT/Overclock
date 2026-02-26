package com.serkka.tracker

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.serkka.tracker.ui.theme.DarkBackground
import com.serkka.tracker.ui.theme.OrangePrimary
import com.serkka.tracker.ui.theme.PersonalBestGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }
    
    val workouts by viewModel.allWorkouts.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val currentSong by MediaRepository.getInstance().currentSong.collectAsState()

    // Google Sign-In Setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                task.getResult(ApiException::class.java)
                Toast.makeText(context, "Google Drive linked!", Toast.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                Log.e("WorkoutScreen", "Sign-in failed: ${e.statusCode}", e)
                Toast.makeText(context, "Sign-in failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Manual Drive Backup Trigger
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
                        
                        // Checkpoint DB
                        val db = WorkoutDatabase.getDatabase(context)
                        db.query(androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { cursor ->
                            cursor.moveToFirst()
                        }

                        val dbFile = context.getDatabasePath("workout_db")
                        val fileId = driveHelper.uploadFile(dbFile, "application/x-sqlite3", "workout_backup_auto.db")
                        
                        withContext(Dispatchers.Main) {
                            if (fileId != null) {
                                Toast.makeText(context, "Drive backup successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Drive upload failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WorkoutScreen", "Manual backup failed", e)
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Backup error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Launcher for Saving the Local Backup
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { 
            coroutineScope.launch {
                if (backupManager.backupDatabase(it)) {
                    Toast.makeText(context, "Local backup successful", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Launcher for Restoring the Backup
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                if (backupManager.restoreDatabase(uris)) {
                    Toast.makeText(context, "Restore successful! Restarting...", Toast.LENGTH_LONG).show()
                    val packageManager = context.packageManager
                    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                    context.startActivity(android.content.Intent.makeRestartActivityTask(intent?.component))
                    Runtime.getRuntime().exit(0)
                }
            }
        }
    }

    val groupedWorkouts = workouts.groupBy { SimpleDateFormat("d.M.yy", Locale.getDefault()).format(Date(it.date)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracker") },
                actions = {
                    IconButton(onClick = performDriveBackup) {
                        Icon(Icons.Default.CloudUpload, "Drive Backup", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = { backupLauncher.launch("workout_backup.db") }) {
                        Text("Backup", color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }) {
                        Text("Restore", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentSong.title != null) {
                    Surface(
                        color = DarkBackground,
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 4.dp,
                        modifier = Modifier.height(56.dp).widthIn(max = 240.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = { MediaRepository.getInstance().togglePlayPause() }) {
                                Icon(
                                    imageVector = if (currentSong.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = if (currentSong.isPlaying) OrangePrimary else Color.Gray
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(currentSong.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                                Text(currentSong.artist ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            IconButton(onClick = { MediaRepository.getInstance().nextTrack() }) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                FloatingActionButton(onClick = { showDialog = true }, containerColor = DarkBackground, contentColor = OrangePrimary) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            groupedWorkouts.forEach { (date, workoutsInDay) ->
                stickyHeader {
                    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)) {
                        Text(date, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                    }
                }
                items(workoutsInDay) { workout -> WorkoutCard(workout = workout, onDelete = { viewModel.deleteWorkout(workout) }) }
            }
        }
        if (showDialog) {
            AddWorkoutDialog(onDismiss = { showDialog = false }, onAdd = { exercise, sets, reps, weight, dateMillis, isPB ->
                viewModel.addWorkout(exercise, sets, reps, weight, dateMillis, isPB)
                showDialog = false
            })
        }
    }
}

@Composable
fun WorkoutCard(workout: Workout, onDelete: () -> Unit) {
    val backgroundColor = if (workout.isPersonalBest) PersonalBestGold else OrangePrimary
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(workout.exerciseName, style = MaterialTheme.typography.titleLarge, color = Color.Black)
                val details = buildString {
                    if (workout.sets > 0) append("${workout.sets} sets ")
                    if (workout.reps > 0) append("x ${workout.reps} reps ")
                    if (workout.weight > 0) append("@ ${workout.weight}kg")
                }
                Text(details, color = Color.Black)
            }
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp)) {
                if (workout.isPersonalBest) {
                    Text("PB!", style = MaterialTheme.typography.labelSmall, color = Color.Black, modifier = Modifier.padding(top = 12.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Black)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkoutDialog(onDismiss: () -> Unit, onAdd: (String, Int, Int, Float, Long, Boolean) -> Unit) {
    var exercise by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var isPB by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } }) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = exercise, onValueChange = { exercise = it }, label = { Text("Exercise") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sets, onValueChange = { sets = it }, label = { Text("Sets") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text("Reps") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") })
                OutlinedTextField(
                    value = SimpleDateFormat("d.M.yy", Locale.getDefault()).format(Date(datePickerState.selectedDateMillis ?: System.currentTimeMillis())),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, null) } }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPB, onCheckedChange = { isPB = it })
                    Text("Personal Best")
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                onAdd(exercise, sets.toIntOrNull() ?: 0, reps.toIntOrNull() ?: 0, weight.toFloatOrNull() ?: 0f, datePickerState.selectedDateMillis ?: System.currentTimeMillis(), isPB)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
