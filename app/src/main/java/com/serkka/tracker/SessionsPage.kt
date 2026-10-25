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

// ── Session forms (bottom sheet) ──────────────────────────────────────────────

@Composable
internal fun AddSessionDialog(
    primaryColor: Color,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, dateEpochMs: Long, durationSeconds: Int) -> Unit
) {
    SessionFormSheet(
        title = "Add Session",
        primaryColor = primaryColor,
        initialName = null,
        initialType = workoutActivityTypes.first(),
        initialDateMs = System.currentTimeMillis(),
        initialSeconds = 0,
        initialNotes = null,
        onDismiss = onDismiss,
        onSave = { name, type, dateMs, seconds, _ -> onSave(name, type, dateMs, seconds) }
    )
}

@Composable
internal fun EditSessionDialog(
    session: WorkoutSession,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onSave: (WorkoutSession) -> Unit
) {
    SessionFormSheet(
        title = "Edit Session",
        primaryColor = primaryColor,
        initialName = session.name,
        initialType = workoutActivityTypes.firstOrNull { it.typeId == session.type } ?: workoutActivityTypes.first(),
        initialDateMs = session.date,
        initialSeconds = session.durationSeconds,
        initialNotes = session.notes,
        onDismiss = onDismiss,
        onSave = { name, type, dateMs, seconds, notes ->
            onSave(session.copy(name = name, type = type, date = dateMs, durationSeconds = seconds, notes = notes ?: session.notes))
        }
    )
}

/**
 * Shared add/edit session form. [initialName] null = new session: the name auto-fills as
 * "<Type> – <MMM d>" and follows type/date changes until the user types their own.
 * [initialNotes] null hides the notes field (add flow has no notes).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionFormSheet(
    title: String,
    primaryColor: Color,
    initialName: String?,
    initialType: WorkoutActivityType,
    initialDateMs: Long,
    initialSeconds: Int,
    initialNotes: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, dateMs: Long, seconds: Int, notes: String?) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMs)
    val selectedDateMs = datePickerState.selectedDateMillis ?: initialDateMs

    fun autoName(type: WorkoutActivityType, dateMs: Long): String =
        "${type.label} – ${pickerMillisToLocalDate(dateMs).format(DateTimeFormatter.ofPattern("MMM d"))}"

    var name by remember { mutableStateOf(initialName ?: autoName(initialType, initialDateMs)) }
    if (initialName == null) {
        LaunchedEffect(selectedType, selectedDateMs) {
            val isAutoName = workoutActivityTypes.any { name.startsWith("${it.label} – ") }
            if (name.isBlank() || isAutoName) name = autoName(selectedType, selectedDateMs)
        }
    }

    var hours by remember { mutableStateOf(if (initialSeconds / 3600 > 0) (initialSeconds / 3600).toString() else "") }
    var minutes by remember {
        mutableStateOf(if (initialSeconds > 0) ((initialSeconds % 3600) / 60).toString() else "")
    }
    var notes by remember { mutableStateOf(initialNotes ?: "") }
    val totalSeconds = (hours.toIntOrNull() ?: 0) * 3600 + (minutes.toIntOrNull() ?: 0) * 60

    FormSheet(
        title = title,
        primaryColor = primaryColor,
        onDismiss = onDismiss,
        confirmEnabled = name.isNotBlank() && totalSeconds > 0,
        onConfirm = {
            onSave(name, selectedType.typeId, selectedDateMs, totalSeconds, if (initialNotes != null) notes else null)
        }
    ) {
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericInput(
                value = hours,
                onValueChange = { hours = it },
                label = "Hours",
                modifier = Modifier.weight(1f)
            )
            NumericInput(
                value = minutes,
                onValueChange = { minutes = it },
                label = "Minutes",
                modifier = Modifier.weight(1f),
                step = 5f
            )
        }

        if (initialNotes != null) {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        }

        SheetDateRow(state = datePickerState, primaryColor = primaryColor)
    }
}
