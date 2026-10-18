@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
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
 * A unified display item for the sessions list.
 */
internal data class SessionDisplayItem(
    val id: String,
    val name: String,
    val type: String,
    val date: Long,           // epoch ms
    val durationSeconds: Int,
    val notes: String = "",
    val localSession: WorkoutSession? = null
)

private fun WorkoutSession.toDisplayItem() = SessionDisplayItem(
    id = "local_$id",
    name = name,
    type = type,
    date = date,
    durationSeconds = durationSeconds,
    notes = notes,
    localSession = this
)

@Composable
fun SessionsPage(
    sessions: List<WorkoutSession>,
    primaryColor: Color,
    onDelete: (WorkoutSession) -> Unit,
    onEdit: (WorkoutSession) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp,
    bottomPadding: Dp = 16.dp
) {
    val allItems = remember(sessions) {
        sessions.map { it.toDisplayItem() }.sortedByDescending { it.date }
    }
    val totalDurationSeconds = remember(allItems) { allItems.sumOf { it.durationSeconds.toLong() } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // No horizontal padding: list rows run full-bleed; the hero panel adds its own gutter.
        contentPadding = PaddingValues(top = topPadding + 6.dp, bottom = bottomPadding)
    ) {
        if (allItems.isNotEmpty()) {
            item {
                FlatSection(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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

            item { SectionHeader("History") }

            itemsIndexed(allItems, key = { _, it -> it.id }) { index, item ->
                Column(Modifier.animateItem()) {
                    SessionRow(
                        item = item,
                        primaryColor = primaryColor,
                        onDelete = { item.localSession?.let { onDelete(it) } },
                        onEdit = { item.localSession?.let { onEdit(it) } }
                    )
                    if (index < allItems.lastIndex) ListRowDivider()
                }
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

/** Flat, full-width M3 list row for a session. Tap to edit; trailing delete. */
@Composable
private fun SessionRow(
    item: SessionDisplayItem,
    primaryColor: Color,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        colors = flatListItemColors(),
        leadingContent = { ActivityAvatar(item.type, item.name, primaryColor) },
        headlineContent = {
            Text(
                item.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDate(item.date))
                    Text("  ·  ")
                    Text(
                        formatElapsed(item.durationSeconds.toLong()),
                        color = primaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (item.notes.isNotEmpty()) {
                    Text(item.notes, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}

@Composable
internal fun AddSessionDialog(
    primaryColor: Color,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, dateEpochMs: Long, durationSeconds: Int) -> Unit
) {
    var selectedType by remember { mutableStateOf(workoutActivityTypes.first()) }
    var datePickerOpen by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val selectedDateText = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            val date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
            date.format(DateTimeFormatter.ofPattern("EEEE d.M.yyyy"))
        } ?: "Select date"
    }
    fun autoName(type: WorkoutActivityType, dateMs: Long?): String {
        val date = (dateMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }
            ?: java.time.LocalDate.now())
        return "${type.label} – ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
    }
    var name by remember { mutableStateOf(autoName(selectedType, datePickerState.selectedDateMillis)) }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    LaunchedEffect(selectedType, datePickerState.selectedDateMillis) {
        val autoForAny = workoutActivityTypes.any { name.startsWith("${it.label} – ") }
        if (name.isBlank() || autoForAny) {
            name = autoName(selectedType, datePickerState.selectedDateMillis)
        }
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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    workoutActivityTypes.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label) },
                            leadingIcon = {
                                Icon(getIconForActivity(type.typeId, type.label), contentDescription = null, modifier = Modifier.size(19.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                                selectedLabelColor = primaryColor,
                                selectedLeadingIconColor = primaryColor
                            )
                        )
                    }
                }

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
                        selectedType.typeId,
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
    var selectedType by remember {
        mutableStateOf(
            workoutActivityTypes.firstOrNull { it.typeId == session.type } ?: workoutActivityTypes.first()
        )
    }
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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    workoutActivityTypes.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label) },
                            leadingIcon = {
                                Icon(getIconForActivity(type.typeId, type.label), contentDescription = null, modifier = Modifier.size(19.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                                selectedLabelColor = primaryColor,
                                selectedLeadingIconColor = primaryColor
                            )
                        )
                    }
                }

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
                            type = selectedType.typeId,
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
