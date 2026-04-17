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
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * A unified display item for the sessions list, wrapping either a local session or a Strava activity.
 */
internal data class SessionDisplayItem(
    val id: String,
    val name: String,
    val type: String,
    val date: Long,           // epoch ms
    val durationSeconds: Int,
    val notes: String = "",
    val isStrava: Boolean = false,
    val localSession: WorkoutSession? = null
)

private fun WorkoutSession.toDisplayItem() = SessionDisplayItem(
    id = "local_$id",
    name = name,
    type = type,
    date = date,
    durationSeconds = durationSeconds,
    notes = notes,
    isStrava = false,
    localSession = this
)

private fun StravaActivity.toDisplayItem(): SessionDisplayItem {
    val epochMs = try {
        java.time.OffsetDateTime.parse(startDate.replace("Z", "+00:00"))
            .toInstant().toEpochMilli()
    } catch (_: Exception) { 0L }
    return SessionDisplayItem(
        id = "strava_$id",
        name = name,
        type = type,
        date = epochMs,
        durationSeconds = movingTime,
        isStrava = true
    )
}

@Composable
fun SessionsPage(
    sessions: List<WorkoutSession>,
    stravaActivities: List<StravaActivity> = emptyList(),
    primaryColor: Color,
    onDelete: (WorkoutSession) -> Unit,
    onEdit: (WorkoutSession) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp,
    bottomPadding: Dp = 16.dp
) {
    val allItems = remember(sessions, stravaActivities) {
        (sessions.map { it.toDisplayItem() } + stravaActivities.map { it.toDisplayItem() })
            .sortedByDescending { it.date }
    }
    val totalDurationSeconds = remember(allItems) { allItems.sumOf { it.durationSeconds.toLong() } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 6.dp, start = 16.dp, end = 16.dp, bottom = bottomPadding)
    ) {
        if (allItems.isNotEmpty()) {
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
                                "${allItems.size}",
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

            items(allItems, key = { it.id }) { item ->
                SessionDisplayCard(
                    item = item,
                    primaryColor = primaryColor,
                    onDelete = { item.localSession?.let { onDelete(it) } },
                    onEdit = { item.localSession?.let { onEdit(it) } },
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
    }
}

private val StravaOrange = Color(0xFFFC4C02)

@Composable
private fun SessionDisplayCard(
    item: SessionDisplayItem,
    primaryColor: Color,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val accentColor = if (item.isStrava) StravaOrange else primaryColor

    ElevatedCard(
        onClick = { if (!item.isStrava) onEdit() },
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
            Box(modifier = Modifier.size(44.dp)) {
                Surface(
                    color = accentColor.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            getIconForActivity(item.type),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                if (item.isStrava) {
                    Icon(
                        painter = painterResource(id = R.drawable.strava_logo),
                        contentDescription = "Strava",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    formatDate(item.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        formatElapsed(item.durationSeconds.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
                if (item.notes.isNotEmpty()) {
                    Text(
                        item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (!item.isStrava) {
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
