@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WorkoutActivityType(
    val label: String,
    val stravaType: String,
    val icon: ImageVector
)

val workoutActivityTypes = listOf(
    WorkoutActivityType("Weight Training", "WeightTraining", Icons.Default.FitnessCenter),
    WorkoutActivityType("Other",   "Workout", Icons.Default.MoreHoriz),
)

@Composable
fun WorkoutTimerScreen(
    timerViewModel: WorkoutTimerViewModel,
    stravaViewModel: StravaViewModel,
    bottomPadding: Dp = 0.dp,
    topPadding: Dp,
    onSaveLocally: (name: String, type: String, startEpochMs: Long, durationSeconds: Int) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current

    val elapsedSeconds by timerViewModel.elapsedSeconds.collectAsState()
    val currentLapSeconds by timerViewModel.currentLapSeconds.collectAsState()
    val isRunning by timerViewModel.isRunning.collectAsState()
    val hasStarted by timerViewModel.hasStarted.collectAsState()
    val selectedType by timerViewModel.selectedType.collectAsState()
    val showUploadDialog by timerViewModel.showUploadDialog.collectAsState()
    val activityName by timerViewModel.activityName.collectAsState()
    val distanceKm by timerViewModel.distanceKm.collectAsState()
    val startDateTime by timerViewModel.startDateTime.collectAsState()
    val uploadState by stravaViewModel.uploadState.collectAsState()
    val savedToken by stravaViewModel.savedToken.collectAsState()


    val timeString = formatElapsed(elapsedSeconds)
    val lapTimeString = formatElapsed(currentLapSeconds)

    val rawProgress = (elapsedSeconds % 60) / 60f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "timerRing"
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    // ── React to Strava upload result ─────────────────────────────────────────
    LaunchedEffect(uploadState) {
        when (uploadState) {
            is UploadState.Success -> {
                Toast.makeText(context, "✓ Uploaded to Strava!", Toast.LENGTH_SHORT).show()
                timerViewModel.reset()
                stravaViewModel.clearUploadState()
                stravaViewModel.checkAndFetchActivities()
            }

            is UploadState.Error -> {
                Toast.makeText(
                    context,
                    (uploadState as UploadState.Error).message,
                    Toast.LENGTH_LONG
                ).show()
                stravaViewModel.clearUploadState()
            }

            else -> Unit
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = topPadding, bottom = 60.dp + bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        Spacer(modifier = Modifier.weight(0.25f))

        Text(
            text = if (hasStarted) timeString else "",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (hasStarted) 50.sp else 50.sp,
            letterSpacing = if (hasStarted) 2.sp else 0.sp,
            color = if (hasStarted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
        )


        Spacer(modifier = Modifier.weight(0.25f))

        // ── Ring + time display ───────────────────────────────────────────────
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val ringSize = (screenHeight * 0.38f).coerceIn(200.dp, 320.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ringSize)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = ringSize / 2),
                    onClick = { timerViewModel.lap() }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)

                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                if (hasStarted && animatedProgress > 0f) {
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (hasStarted) lapTimeString else timeString,
                    fontSize = if (elapsedSeconds >= 3600) 60.sp else 70.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.25f))

        // ── Controls ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stop button — only when paused (so user can discard/upload)
            AnimatedVisibility(
                visible = hasStarted && !isRunning,
                enter = fadeIn() + slideInHorizontally { -it },
                exit = fadeOut() + slideOutHorizontally { -it }
            ) {
                val stopInteractionSource = remember { MutableInteractionSource() }
                FilledTonalIconButton(
                    onClick = { timerViewModel.requestStop() },
                    interactionSource = stopInteractionSource,
                    modifier = Modifier.size(80.dp).bounceClick(stopInteractionSource),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }


            // Start / Pause — always visible once hasStarted, or as the initial start button
            val startInteractionSource = remember { MutableInteractionSource() }
            FloatingActionButton(
                onClick = { timerViewModel.toggleRunning() },
                interactionSource = startInteractionSource,
                modifier = Modifier.size(80.dp).bounceClick(startInteractionSource),
                containerColor = if (isRunning) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Pause" else "Start",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.surface

                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Upload dialog ─────────────────────────────────────────────────────────
        if (showUploadDialog) {
            UploadWorkoutDialog(
                activityName = activityName,
                onNameChange = { timerViewModel.setActivityName(it) },
                selectedType = selectedType,
                onTypeChange = { timerViewModel.setSelectedType(it) },
                elapsedSeconds = elapsedSeconds,
                isUploading = uploadState == UploadState.Loading,
                isStravaLinked = savedToken.isNotBlank(),
                onSaveLocally = {
                    val dt = startDateTime ?: LocalDateTime.now().minusSeconds(elapsedSeconds)
                    val startMs = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onSaveLocally(activityName, selectedType.stravaType, startMs, elapsedSeconds.toInt())
                    timerViewModel.discard()
                },
                onUpload = {
                    val dt = startDateTime
                        ?: LocalDateTime.now().minusSeconds(elapsedSeconds)
                    val iso = dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val dist = distanceKm.replace(',', '.').toFloatOrNull()?.let { it * 1000f }
                    stravaViewModel.uploadWorkout(
                        name = activityName,
                        sportType = selectedType.stravaType,
                        startDateLocal = iso,
                        elapsedSeconds = elapsedSeconds.toInt(),
                        distanceMeters = dist
                    )
                },
                onDismiss = { timerViewModel.dismissUploadDialog() },
                onDiscard = { timerViewModel.discard() }
            )
        }

    }
}

@Composable
private fun UploadWorkoutDialog(
    activityName: String,
    onNameChange: (String) -> Unit,
    selectedType: WorkoutActivityType,
    onTypeChange: (WorkoutActivityType) -> Unit,
    elapsedSeconds: Long,
    isUploading: Boolean,
    isStravaLinked: Boolean,
    onSaveLocally: () -> Unit,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("Save Workout", fontWeight = FontWeight.Bold) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Duration: ${formatElapsed(elapsedSeconds)}", style = MaterialTheme.typography.bodyMedium)
                }

                OutlinedTextField(
                    value = activityName,
                    onValueChange = onNameChange,
                    label = { Text("Activity Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )


            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Save locally + Upload to Strava row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val saveInteractionSource = remember { MutableInteractionSource() }
                    val uploadInteractionSource = remember { MutableInteractionSource() }

                    Button(
                        onClick = onSaveLocally,
                        interactionSource = saveInteractionSource,
                        enabled = activityName.isNotBlank() && !isUploading,
                        modifier = Modifier.weight(1f).bounceClick(saveInteractionSource),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text("Save")
                    }

                    if (isStravaLinked) {
                    Button(
                        onClick = onUpload,
                        interactionSource = uploadInteractionSource,
                        enabled = activityName.isNotBlank() && !isUploading,
                        modifier = Modifier.weight(1f).bounceClick(uploadInteractionSource)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.surface,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Strava", color = MaterialTheme.colorScheme.surface)
                        }
                    }
                    }
                }

                // Discard + Cancel row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val discardInteractionSource = remember { MutableInteractionSource() }
                    val cancelInteractionSource = remember { MutableInteractionSource() }

                    TextButton(
                        onClick = onDiscard,
                        interactionSource = discardInteractionSource,
                        enabled = !isUploading,
                        modifier = Modifier.weight(1f).bounceClick(discardInteractionSource)
                    ) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = onDismiss,
                        interactionSource = cancelInteractionSource,
                        enabled = !isUploading,
                        modifier = Modifier.weight(1f).bounceClick(cancelInteractionSource)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        },
        dismissButton = {}
    )
}
