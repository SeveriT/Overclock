package com.serkka.tracker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.serkka.tracker.ui.theme.DarkBackground
import com.serkka.tracker.ui.theme.OrangePrimary
import com.serkka.tracker.ui.theme.PersonalBestGold
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel) {
    val workouts by viewModel.allWorkouts.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val currentSong by MediaRepository.getInstance().currentSong.collectAsState()

    // Group workouts by date (formatted for header)
    val dateFormatter = remember { SimpleDateFormat("d.M.yy", Locale.getDefault()) }
    val groupedWorkouts = workouts.groupBy { 
        dateFormatter.format(Date(it.date))
    }

    Scaffold(
        floatingActionButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Music Widget on the Left
                if (currentSong.title != null) {
                    Surface(
                        color = DarkBackground,
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .height(56.dp)
                            .widthIn(max = 240.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { MediaRepository.getInstance().togglePlayPause() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (currentSong.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (currentSong.isPlaying) "Pause" else "Play",
                                    tint = if (currentSong.isPlaying) OrangePrimary else Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = currentSong.title ?: "Unknown",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentSong.artist ?: "Unknown Artist",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { MediaRepository.getInstance().nextTrack() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Track",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Plus Button on the Right
                FloatingActionButton(
                    onClick = { showDialog = true },
                    containerColor = OrangePrimary,
                    contentColor = Color.Black
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            groupedWorkouts.forEach { (date, workoutsInDay) ->
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .padding(8.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                items(workoutsInDay) { workout ->
                    WorkoutCard(
                        workout = workout,
                        onDelete = { viewModel.deleteWorkout(workout) }
                    )
                }
            }
        }

        if (showDialog) {
            AddWorkoutDialog(
                onDismiss = { showDialog = false },
                onAdd = { exercise, sets, reps, weight, dateMillis, isPB ->
                    viewModel.addWorkout(exercise, sets, reps, weight, dateMillis, isPB)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun WorkoutCard(workout: Workout, onDelete: () -> Unit) {
    val backgroundColor = if (workout.isPersonalBest) PersonalBestGold else OrangePrimary
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = workout.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black,
                    modifier = Modifier.padding(end = 120.dp)
                )
                
                val details = remember(workout) {
                    buildString {
                        if (workout.sets > 0) append("${workout.sets} sets")
                        if (workout.reps > 0) {
                            if (isNotEmpty()) append(" x ")
                            append("${workout.reps} reps")
                        }
                        if (workout.weight > 0) {
                            if (isNotEmpty()) append(" @ ")
                            val weightText = if (workout.weight % 1 == 0f) {
                                workout.weight.toInt().toString()
                            } else {
                                workout.weight.toString()
                            }
                            append("${weightText}kg")
                        }
                    }
                }

                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        color = Color.Black
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                if (workout.isPersonalBest) {
                    Text(
                        text = "PERSONAL BEST!",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Workout",
                        tint = Color.Black
                    )
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
    var isPersonalBest by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    var showDatePicker by remember { mutableStateOf(false) }

    val dateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
    val dateText = remember(dateMillis) {
        SimpleDateFormat("d.M.yy", Locale.getDefault()).format(Date(dateMillis))
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = exercise,
                    onValueChange = { exercise = it },
                    label = { Text("Exercise") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it },
                        label = { Text("Sets") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it },
                        label = { Text("Reps") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (kg)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { },
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isPersonalBest,
                        onCheckedChange = { isPersonalBest = it }
                    )
                    Text("Personal Best")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = exercise.isNotBlank(),
                onClick = {
                    onAdd(
                        exercise,
                        sets.toIntOrNull() ?: 0,
                        reps.toIntOrNull() ?: 0,
                        weight.toFloatOrNull() ?: 0f,
                        dateMillis,
                        isPersonalBest
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
