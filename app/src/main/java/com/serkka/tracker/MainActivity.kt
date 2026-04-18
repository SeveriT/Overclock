package com.serkka.tracker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat


class MainActivity : ComponentActivity() {
    private lateinit var stravaViewModel: StravaViewModel
    private lateinit var stepsViewModel: StepsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize the Database, DAO and Repository
        val database = WorkoutDatabase.getDatabase(applicationContext)
        val workoutDao = database.workoutDao()
        val bodyWeightDao = database.bodyWeightDao()
        val workoutSessionDao = database.workoutSessionDao()
        val repository = WorkoutRepository(workoutDao, bodyWeightDao, workoutSessionDao)

        // 2. Initialize StravaViewModel immediately to handle intents
        stravaViewModel = ViewModelProvider(this)[StravaViewModel::class.java]
        stepsViewModel = ViewModelProvider(this)[StepsViewModel::class.java]
        
        // Initialize ThemeViewModel
        val themeViewModel = ViewModelProvider(this)[ThemeViewModel::class.java]

        // 3. Schedule Automatic Backup
        scheduleBackup()

        // Notification permission is now handled in WelcomeScreen

        setContent {
            val primaryColor by themeViewModel.primaryColor.collectAsState()
            val fontScale by themeViewModel.fontScale.collectAsState()

            TrackerTheme(primaryColor = primaryColor, fontScale = fontScale) {
                // 4. Create the WorkoutViewModel using a Factory
                val workoutViewModel: WorkoutViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return WorkoutViewModel(repository) as T
                        }
                    }
                )

                val welcomePrefs = remember { getSharedPreferences("app_prefs", MODE_PRIVATE) }
                var showWelcome by remember { mutableStateOf(!welcomePrefs.getBoolean("welcome_skip", false)) }
                var showExitDialog by remember { mutableStateOf(false) }

                BackHandler {
                    if (showWelcome) {
                        showWelcome = false
                    } else {
                        showExitDialog = true
                    }
                }

                if (showExitDialog) {
                    val exitInteractionSource = remember { MutableInteractionSource() }
                    val cancelInteractionSource = remember { MutableInteractionSource() }
                    
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text("Exit App") },
                        text  = { Text("Are you sure you want to exit?") },
                        confirmButton = {
                            Button(
                                onClick = { finishAffinity() },
                                interactionSource = exitInteractionSource,
                                modifier = Modifier.bounceClick(exitInteractionSource),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryColor,
                                    contentColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text("Exit")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showExitDialog = false },
                                interactionSource = cancelInteractionSource,
                                modifier = Modifier.bounceClick(cancelInteractionSource)
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // 5. Launch the UI
                if (showWelcome) {
                    WelcomeScreen(
                        primaryColor = primaryColor,
                        onGetStarted = { dontShowAgain ->
                            showWelcome = false
                            if (dontShowAgain) {
                                welcomePrefs.edit().putBoolean("welcome_skip", true).apply()
                            }
                        }
                    )
                } else {
                    WorkoutScreen(
                        viewModel = workoutViewModel,
                        stravaViewModel = stravaViewModel,
                        themeViewModel = themeViewModel
                    )
                }
            }
        }
        
        // Handle the initial intent if the app was started via a deep link
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Always update the intent
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::stepsViewModel.isInitialized) stepsViewModel.refresh()
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        Log.d("MainActivity", "Handling intent: $uri")
        if (uri != null && uri.scheme == "tracker-app" && uri.host == "localhost") {
            val code = uri.getQueryParameter("code")
            Log.d("MainActivity", "Received Strava code: $code")
            if (code != null) {
                stravaViewModel.exchangeCodeForToken(code)
            }
        }
    }

    private fun scheduleBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "AutoBackupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }
}
