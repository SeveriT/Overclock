@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun SessionsPage(
    sessions: List<WorkoutSession>,
    primaryColor: Color,
    onDelete: (WorkoutSession) -> Unit,
    onEdit: (WorkoutSession) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp
) {
    val totalDurationSeconds = remember(sessions) { sessions.sumOf { it.durationSeconds.toLong() } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        if (sessions.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${sessions.size}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Text(
                                "Sessions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                formatElapsed(totalDurationSeconds),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Text(
                                "Total Time",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(sessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    primaryColor = primaryColor,
                    onDelete = { onDelete(session) },
                    onEdit = { onEdit(session) },
                    modifier = Modifier.animateItem()
                )
            }
        } else {
            item {
                EmptyState(
                    icon = Icons.Default.Schedule,
                    message = "No sessions yet.\nUse the timer or add one manually.",
                    primaryColor = primaryColor
                )
            }
        }

        item { Spacer(modifier = Modifier.height(145.dp)) }
    }
}

@Composable
private fun SessionCard(
    session: WorkoutSession,
    primaryColor: Color,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardInteractionSource = remember { MutableInteractionSource() }

    ElevatedCard(
        onClick = onEdit,
        interactionSource = cardInteractionSource,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = primaryColor.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        getIconForActivity(session.type),
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    formatDate(session.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        formatElapsed(session.durationSeconds.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }
                if (session.notes.isNotEmpty()) {
                    Text(
                        session.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            val deleteInteractionSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onDelete,
                interactionSource = deleteInteractionSource,
                modifier = Modifier.bounceClick(deleteInteractionSource)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun AddSessionDialog(
    primaryColor: Color,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, dateEpochMs: Long, durationSeconds: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(workoutActivityTypes.first()) }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var datePickerOpen by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val selectedDateText = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            val date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
            date.format(DateTimeFormatter.ofPattern("EEEE d.M.yyyy"))
        } ?: "Select date"
    }

    if (datePickerOpen) {
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                val confirmInteraction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { datePickerOpen = false },
                    interactionSource = confirmInteraction,
                    modifier = Modifier.bounceClick(confirmInteraction)
                ) { Text("OK") }
            },
            dismissButton = {
                val dismissInteraction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { datePickerOpen = false },
                    interactionSource = dismissInteraction,
                    modifier = Modifier.bounceClick(dismissInteraction)
                ) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Session", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Session Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )


                val dateInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = { datePickerOpen = true },
                    interactionSource = dateInteraction,
                    modifier = Modifier.fillMaxWidth().bounceClick(dateInteraction)
                ) {
                    Text(selectedDateText)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() } },
                        label = { Text("Hours") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Minutes") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            val saveInteraction = remember { MutableInteractionSource() }
            val totalSeconds = (hours.toIntOrNull() ?: 0) * 3600 + (minutes.toIntOrNull() ?: 0) * 60
            Button(
                onClick = {
                    onSave(
                        name,
                        selectedType.stravaType,
                        datePickerState.selectedDateMillis ?: System.currentTimeMillis(),
                        totalSeconds
                    )
                },
                interactionSource = saveInteraction,
                modifier = Modifier.bounceClick(saveInteraction),
                enabled = name.isNotBlank() && totalSeconds > 0
            ) { Text("Save") }
        },
        dismissButton = {
            val cancelInteraction = remember { MutableInteractionSource() }
            TextButton(
                onClick = onDismiss,
                interactionSource = cancelInteraction,
                modifier = Modifier.bounceClick(cancelInteraction)
            ) { Text("Cancel") }
        }
    )
}

@Composable
internal fun EditSessionDialog(
    session: WorkoutSession,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onSave: (WorkoutSession) -> Unit
) {
    var name by remember { mutableStateOf(session.name) }
    var hours by remember {
        mutableStateOf(if (session.durationSeconds / 3600 > 0) (session.durationSeconds / 3600).toString() else "")
    }
    var minutes by remember {
        mutableStateOf(((session.durationSeconds % 3600) / 60).toString())
    }
    var notes by remember { mutableStateOf(session.notes) }
    var datePickerOpen by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = session.date)
    val selectedDateText = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            val date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
            date.format(DateTimeFormatter.ofPattern("EEEE d.M.yyyy"))
        } ?: "Select date"
    }

    if (datePickerOpen) {
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                val confirmInteraction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { datePickerOpen = false },
                    interactionSource = confirmInteraction,
                    modifier = Modifier.bounceClick(confirmInteraction)
                ) { Text("OK") }
            },
            dismissButton = {
                val dismissInteraction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { datePickerOpen = false },
                    interactionSource = dismissInteraction,
                    modifier = Modifier.bounceClick(dismissInteraction)
                ) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Session", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Session Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val dateInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = { datePickerOpen = true },
                    interactionSource = dateInteraction,
                    modifier = Modifier.fillMaxWidth().bounceClick(dateInteraction)
                ) {
                    Text(selectedDateText)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() } },
                        label = { Text("Hours") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Minutes") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            val saveInteraction = remember { MutableInteractionSource() }
            val totalSeconds = (hours.toIntOrNull() ?: 0) * 3600 + (minutes.toIntOrNull() ?: 0) * 60
            Button(
                onClick = {
                    onSave(
                        session.copy(
                            name = name,
                            date = datePickerState.selectedDateMillis ?: session.date,
                            durationSeconds = totalSeconds,
                            notes = notes
                        )
                    )
                },
                interactionSource = saveInteraction,
                modifier = Modifier.bounceClick(saveInteraction),
                enabled = name.isNotBlank() && totalSeconds > 0
            ) { Text("Save") }
        },
        dismissButton = {
            val cancelInteraction = remember { MutableInteractionSource() }
            TextButton(
                onClick = onDismiss,
                interactionSource = cancelInteraction,
                modifier = Modifier.bounceClick(cancelInteraction)
            ) { Text("Cancel") }
        }
    )
}
