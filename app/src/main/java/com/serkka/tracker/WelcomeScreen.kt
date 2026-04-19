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
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

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
        fadeIn.animateTo(1f, animationSpec = tween(600))
    }

    var showUserGuide by remember { mutableStateOf(false) }
    if (showUserGuide) {
        UserGuideDialog(onDismiss = { showUserGuide = false })
    }

    var dontShowAgain by remember { mutableStateOf(false) }
    var showDemoConfirm by remember { mutableStateOf(false) }

    if (showDemoConfirm) {
        AlertDialog(
            onDismissRequest = { showDemoConfirm = false },
            title = { Text("Load demo data?") },
            text = { Text("This will add ~4 weeks of sample workouts, body weight entries, and sessions so you can see how the app looks with use. You can wipe everything later from Settings → Clear All Data.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDemoConfirm = false
                        onTryDemo(dontShowAgain)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) { Text("Load demo") }
            },
            dismissButton = {
                TextButton(onClick = { showDemoConfirm = false }) { Text("Cancel") }
            }
        )
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
            "Your personal fitness companion",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Features
        FeatureItem(Icons.Default.FitnessCenter, "Workout Logging", "Track sets, reps, and weight with smart auto-fill", primaryColor)
        FeatureItem(Icons.Default.Timer, "Workout Timer", "Lap tracking with persistent notification", primaryColor)
        FeatureItem(Icons.Default.AutoAwesome, "AI Assistant", "Generate personalized workouts with Gemini AI", primaryColor)
        FeatureItem(Icons.Default.ShowChart, "Progress Tracking", "Weight trends, volume stats, and weekly summaries", primaryColor)
        FeatureItem(Icons.Default.DirectionsWalk, "Step Counter", "Daily step tracking with customizable goal", primaryColor)
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
            icon = Icons.Default.DirectionsWalk,
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

        Spacer(modifier = Modifier.height(16.dp))

        // Subscription info
        Surface(
            color = primaryColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
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
                    "Unlock Cloud Backup, Strava Sync, and AI Assistant with Premium",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
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
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                Spacer(modifier = Modifier.width(6.dp))
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
            Spacer(modifier = Modifier.width(6.dp))
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
            .padding(vertical = 6.dp),
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (granted) Color(0xFF4AC067) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = Color(0xFF4AC067), modifier = Modifier.size(24.dp))
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
