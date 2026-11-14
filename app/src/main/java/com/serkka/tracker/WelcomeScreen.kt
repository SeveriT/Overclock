package com.serkka.tracker

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun WelcomeScreen(
    primaryColor: Color,
    onGetStarted: (dontShowAgain: Boolean) -> Unit,
    onTryDemo: (dontShowAgain: Boolean) -> Unit = {},
    demoAvailable: Boolean = true
) {
    val context = LocalContext.current
    val fadeIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fadeIn.animateTo(1f, animationSpec = tween(Motion.LONG))
    }

    var showUserGuide by remember { mutableStateOf(false) }
    if (showUserGuide) {
        UserGuideDialog(onDismiss = { showUserGuide = false })
    }

    var dontShowAgain by remember { mutableStateOf(false) }
    var showDemoConfirm by remember { mutableStateOf(false) }

    if (showDemoConfirm) {
        TrackerDialog(
            title = "Load demo data?",
            onDismiss = { showDemoConfirm = false },
            confirmLabel = "Load demo",
            onConfirm = {
                showDemoConfirm = false
                onTryDemo(dontShowAgain)
            }
        ) {
            Text("This will add ~4 weeks of sample workouts, body weight entries, timer sessions, notes, and step history so you can see how the app looks with use. You can wipe everything later from Settings → Clear All Data.")
        }
    }

    // Notification permission
    var notificationGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
    }

    // Notification listener permission (for music widget)
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val listenerSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        listenerEnabled = isNotificationListenerEnabled(context)
    }

    // Activity recognition permission (for step counter)
    var activityRecognitionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        activityRecognitionGranted = granted
    }

    // Unrestricted battery (keeps the step counter service alive)
    var batteryOptIgnored by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    // Bluetooth (heart rate strap): scan + connect on Android 12+, location before that
    var bluetoothGranted by remember { mutableStateOf(HeartRateMonitor.hasPermissions(context)) }
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { bluetoothGranted = HeartRateMonitor.hasPermissions(context) }

    // Re-check on resume (user may have toggled in system settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                listenerEnabled = isNotificationListenerEnabled(context)
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .alpha(fadeIn.value),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(modifier = Modifier.height(32.dp))

        Image(
            painter = painterResource(id = R.mipmap.app_logo_foreground),
            contentDescription = "Overclock logo",
            modifier = Modifier.size(120.dp)
        )

        Text(
            "Overclock",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            "Your personal gym workout tracker",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Features
        FeatureItem(ImageVector.vectorResource(R.drawable.ic_weight_training), "Workout Logging", "Track sets, reps, and weight with smart auto-fill", primaryColor)
        FeatureItem(Icons.Default.Timer, "Workout Timer", "Lap tracking with persistent notification", primaryColor)
        FeatureItem(Icons.Default.Favorite, "Heart Rate", "Bluetooth strap support: live BPM on the timer, a graph per session, TCX export", primaryColor)
        FeatureItem(Icons.Default.ShowChart, "Progress Tracking", "Weight trend with prediction, weekly reps per exercise, and a weekly overview on Home", primaryColor)
        FeatureItem(Icons.Default.CalendarMonth, "Calendar", "Year-at-a-glance view of your training days and streaks", primaryColor)
        FeatureItem(Icons.AutoMirrored.Filled.DirectionsWalk, "Step Counter", "Daily step tracking with customizable goal", primaryColor)
        FeatureItem(Icons.AutoMirrored.Filled.Notes, "Notes", "Keep program plans, cues, and reminders in one place", primaryColor)
        FeatureItem(Icons.Default.Handyman, "Tools", "Workout schedule checklist and a running pace calculator", primaryColor)
        FeatureItem(Icons.Default.MusicNote, "Music Control", "Integrated player with swipe gestures", primaryColor)
        FeatureItem(Icons.Default.CloudUpload, "Backup & Restore", "Zip bundle with workouts and step history, local or Drive", primaryColor)

        Spacer(modifier = Modifier.height(24.dp))

        // Permissions
        Text(
            "Permissions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        PermissionRow(
            icon = Icons.Default.Notifications,
            label = "Notifications",
            description = "Timer alerts and backup status",
            granted = notificationGranted,
            primaryColor = primaryColor,
            onEnable = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )

        PermissionRow(
            icon = Icons.Default.MusicNote,
            label = "Notification Listener",
            description = "Required for music widget controls",
            granted = listenerEnabled,
            primaryColor = primaryColor,
            onEnable = {
                listenerSettingsLauncher.launch(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            }
        )

        PermissionRow(
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            label = "Activity Recognition",
            description = "Required for step counter",
            granted = activityRecognitionGranted,
            primaryColor = primaryColor,
            onEnable = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        )

        PermissionRow(
            icon = Icons.Default.BatteryChargingFull,
            label = "Unrestricted Battery",
            description = "Keeps step counting running in the background",
            granted = batteryOptIgnored,
            primaryColor = primaryColor,
            onEnable = { requestIgnoreBatteryOptimizations(context) }
        )

        PermissionRow(
            icon = Icons.Default.Bluetooth,
            label = "Bluetooth",
            description = "Connect a heart rate strap",
            granted = bluetoothGranted,
            primaryColor = primaryColor,
            onEnable = { bluetoothLauncher.launch(HeartRateMonitor.requiredPermissions()) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Subscription info — flat, no tinted panel
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Free to use",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Unlock Cloud Backup with Premium",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = dontShowAgain,
                onCheckedChange = { dontShowAgain = it },
                colors = CheckboxDefaults.colors(checkedColor = primaryColor)
            )
            Text(
                "Don't show this again",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Button(
            onClick = { onGetStarted(dontShowAgain) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Text("Get Started", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (demoAvailable) {
            TextButton(
                onClick = { showDemoConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try with demo data", color = primaryColor, fontWeight = FontWeight.Bold)
            }
        }

        TextButton(
            onClick = { showUserGuide = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("View User Guide", color = primaryColor, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
    }
}

@Composable
private fun FeatureItem(icon: ImageVector, title: String, description: String, primaryColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    primaryColor: Color,
    onEnable: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = TrackerColors.Green, modifier = Modifier.size(24.dp))
        } else {
            TextButton(onClick = onEnable) {
                Text("Enable", color = primaryColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val component = ComponentName(context, MediaNotificationListener::class.java)
    return flat.contains(component.flattenToString())
}
