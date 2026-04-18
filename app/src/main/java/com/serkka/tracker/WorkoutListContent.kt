@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.serkka.tracker.ui.theme.PersonalBestGold
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// ── Workout list ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutListContent(
    workouts: List<Workout>,
    primaryColor: androidx.compose.ui.graphics.Color,
    onDelete: (Workout) -> Unit,
    onEdit: (Workout) -> Unit,
    onCopy: (Workout) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 170.dp,
    searchBar: (@Composable () -> Unit)? = null
) {
    val groupedWorkouts = workouts.groupBy {
        formatDate(it.date)
    }
    val todayKey = remember { formatDate(System.currentTimeMillis()) }
    val expandedDays = remember { mutableStateMapOf(todayKey to true) }

    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
        searchBar?.invoke()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, bottomPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            groupedWorkouts.forEach { (date, workoutsInDay) ->
                val isExpanded = expandedDays[date] ?: false
                val exercises = workoutsInDay.map { it.exerciseName }.distinct()
                val totalSets = workoutsInDay.sumOf { it.sets }
                val hasPB = workoutsInDay.any { it.isPersonalBest }

                item(key = "day_$date") {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column {
                            // ── Day header (always visible) ──────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedDays[date] = !isExpanded }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${exercises.size} exercises · $totalSets sets",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!isExpanded) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = exercises.joinToString(", "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (hasPB) {
                                    Icon(
                                        Icons.Default.EmojiEvents,
                                        contentDescription = "Personal Best",
                                        tint = PersonalBestGold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // ── Expanded movements ───────────────────────
                            if (isExpanded) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                                workoutsInDay.forEach { workout ->
                                    WorkoutMovementRow(
                                        workout = workout,
                                        primaryColor = primaryColor,
                                        onEdit = { onEdit(workout) },
                                        onCopy = { onCopy(workout) },
                                        onDelete = { onDelete(workout) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Workout card (used in SummaryPage) ───────────────────────────────────────

@Composable
fun WorkoutCard(
    workout: Workout,
    primaryColor: androidx.compose.ui.graphics.Color,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 4.dp)
            .bounceClick(
                interactionSource = interactionSource,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCopy()
                },
                onClick = { onEdit() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = workout.exerciseName,
                style = MaterialTheme.typography.titleSmall,
                color = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val isCardio = workout.exerciseName.trim().equals("Cardio", ignoreCase = true)
            val details = buildString {
                if (isCardio) {
                    if (workout.weight > 0) append("${formatWeight(workout.weight)} min")
                } else {
                    if (workout.sets > 0) append("${workout.sets} sets ")
                    if (workout.reps > 0) {
                        if (workout.sets > 0) append("x ")
                        append("${workout.reps} reps ")
                    }
                    if (workout.weight > 0) append("@ ${formatWeight(workout.weight)}${workout.weightUnit}")
                }
            }
            Text(
                text = details,
                color = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (workout.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = workout.notes,
                    color = if (workout.isPersonalBest) PersonalBestGold
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

// ── Workout movement row (inside expanded day card) ──────────────────────────

@Composable
private fun WorkoutMovementRow(
    workout: Workout,
    primaryColor: androidx.compose.ui.graphics.Color,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val textColor = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurface
    val subtextColor = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onEdit() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCopy()
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workout.exerciseName,
                style = MaterialTheme.typography.titleSmall,
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val isCardio = workout.exerciseName.trim().equals("Cardio", ignoreCase = true)
            val details = buildString {
                if (isCardio) {
                    if (workout.weight > 0) append("${formatWeight(workout.weight)} min")
                } else {
                    if (workout.sets > 0) append("${workout.sets} sets ")
                    if (workout.reps > 0) {
                        if (workout.sets > 0) append("x ")
                        append("${workout.reps} reps ")
                    }
                    if (workout.weight > 0) append("@ ${formatWeight(workout.weight)}${workout.weightUnit}")
                }
            }
            Text(
                text = details,
                color = subtextColor,
                style = MaterialTheme.typography.bodyMedium
            )
            if (workout.notes.isNotBlank()) {
                Text(
                    text = workout.notes,
                    color = subtextColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = FontStyle.Italic
                )
            }
        }
        if (workout.isPersonalBest) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = "PB",
                tint = PersonalBestGold,
                modifier = Modifier.size(18.dp)
            )
        }
        val copyInteractionSource = remember { MutableInteractionSource() }
        IconButton(
            onClick = onCopy,
            interactionSource = copyInteractionSource,
            modifier = Modifier.bounceClick(copyInteractionSource)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
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
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Workout dialog ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDialog(
    workout: Workout? = null,
    history: List<Workout> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Float, Long, Boolean, String, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null
) {
    var exercise by remember { mutableStateOf(workout?.exerciseName ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var sets by remember { mutableStateOf(workout?.sets?.toString() ?: "") }
    var reps by remember { mutableStateOf(workout?.reps?.toString() ?: "") }
    var weight by remember { mutableStateOf(workout?.weight?.let { formatWeight(it) } ?: "") }
    val weightUnit = "kg"
    var notes by remember { mutableStateOf(workout?.notes ?: "") }
    var isPB by remember { mutableStateOf(workout?.isPersonalBest ?: false) }
    val dialogHaptic = LocalHapticFeedback.current
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = workout?.date ?: System.currentTimeMillis()
    )
    var showDatePicker by remember { mutableStateOf(false) }

    val focusExercise = remember { FocusRequester() }
    val focusSets     = remember { FocusRequester() }
    val focusReps     = remember { FocusRequester() }
    val focusWeight   = remember { FocusRequester() }
    val focusNotes    = remember { FocusRequester() }

    val lastPerformance = remember(exercise, history) {
        history.find { it.exerciseName.equals(exercise, ignoreCase = true) }
    }

    val suggestions = remember(exercise, history) {
        if (exercise.isEmpty()) {
            history.asSequence().map { it.exerciseName }.distinct().toList()
        } else {
            history.asSequence()
                .filter { it.exerciseName.contains(exercise, ignoreCase = true) }
                .map { it.exerciseName }
                .distinct()
                .filter { it.lowercase() != exercise.lowercase() }
                .toList()
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                val okInteractionSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showDatePicker = false },
                    interactionSource = okInteractionSource,
                    modifier = Modifier.bounceClick(okInteractionSource)
                ) { Text("OK") }
            },
            dismissButton = {
                val cancelInteractionSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showDatePicker = false },
                    interactionSource = cancelInteractionSource,
                    modifier = Modifier.bounceClick(cancelInteractionSource)
                ) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    val selectedDateText = datePickerState.selectedDateMillis?.let {
        val date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
        date.format(DateTimeFormatter.ofPattern("EEEE d.M.yyyy"))
    } ?: "Select date"

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(24.dp).fillMaxWidth(),
        title = { Text(if (workout == null) "Add Workout" else "Edit Workout") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.animateContentSize()
            ) {
                if (exercise.isEmpty() && history.isNotEmpty()) {
                    Text(
                        "Recent exercises:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        history.take(8).forEach { recent ->
                            AssistChip(
                                onClick = {
                                    exercise = recent.exerciseName
                                    sets = recent.sets.toString()
                                    reps = recent.reps.toString()
                                    weight = formatWeight(recent.weight)
                                },
                                label = { Text(recent.exerciseName) }
                            )
                        }
                    }
                }

                val keyboardController = LocalSoftwareKeyboardController.current
                val focusManager = LocalFocusManager.current
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = exercise,
                        onValueChange = { exercise = it; expanded = true },
                        label = { Text("Exercise") },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusExercise),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onNext = {
                                if (exercise.trim().equals("Cardio", ignoreCase = true)) {
                                    focusWeight.requestFocus()
                                } else {
                                    focusSets.requestFocus()
                                }
                            }
                        ),
                        trailingIcon = {
                            IconButton(onClick = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                expanded = !expanded
                            }) {
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = if (expanded) "Collapse" else "Expand"
                                )
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = expanded && suggestions.isNotEmpty(),
                        onDismissRequest = { expanded = false },
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = {
                                    exercise = suggestion
                                    history.find { it.exerciseName == suggestion }?.let { recent ->
                                        sets = recent.sets.toString()
                                        reps = recent.reps.toString()
                                        weight = formatWeight(recent.weight)
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                lastPerformance?.let { last ->
                    val lastIsCardio = last.exerciseName.trim().equals("Cardio", ignoreCase = true)
                    Text(
                        text = if (lastIsCardio) "Last time: ${formatWeight(last.weight)} min"
                               else "Last time: ${last.sets}x${last.reps} @ ${formatWeight(last.weight)}${last.weightUnit}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .clickable {
                                sets = last.sets.toString()
                                reps = last.reps.toString()
                                weight = formatWeight(last.weight)
                            }
                    )
                }

                val isCardio = exercise.trim().equals("Cardio", ignoreCase = true)

                if (!isCardio) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInput(value = sets, onValueChange = { sets = it }, label = "Sets",
                            modifier = Modifier.weight(1f).focusRequester(focusSets),
                            imeAction = ImeAction.Next, onNext = { focusReps.requestFocus() })
                        NumericInput(value = reps, onValueChange = { reps = it }, label = "Reps",
                            modifier = Modifier.weight(1f).focusRequester(focusReps),
                            imeAction = ImeAction.Next, onNext = { focusWeight.requestFocus() })
                    }
                }

                NumericInput(
                    value = weight,
                    onValueChange = { weight = it },
                    label = if (isCardio) "Minutes" else "Weight (kg)",
                    modifier = Modifier.fillMaxWidth().focusRequester(focusWeight),
                    step = if (isCardio) 1f else 2.5f,
                    imeAction = ImeAction.Next,
                    onNext = { focusNotes.requestFocus() }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusNotes),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    )
                )

                val dateInteractionSource = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    interactionSource = dateInteractionSource,
                    modifier = Modifier.fillMaxWidth().bounceClick(dateInteractionSource)
                ) {
                    Text(selectedDateText)
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isPB = !isPB
                        dialogHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                ) {
                    Checkbox(
                        checked = isPB,
                        onCheckedChange = {
                            isPB = it
                            dialogHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                    Text(
                        "PB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    val cancelInteractionSource = remember { MutableInteractionSource() }
                    val saveInteractionSource = remember { MutableInteractionSource() }

                    TextButton(
                        onClick = onDismiss,
                        interactionSource = cancelInteractionSource,
                        modifier = Modifier.bounceClick(cancelInteractionSource)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            dialogHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirm(
                                exercise,
                                sets.toIntOrNull() ?: 0,
                                reps.toIntOrNull() ?: 0,
                                weight.toLeadFloat() ?: 0f,
                                datePickerState.selectedDateMillis ?: System.currentTimeMillis(),
                                isPB,
                                weightUnit,
                                notes
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.surface,
                        ),
                        interactionSource = saveInteractionSource,
                        modifier = Modifier.bounceClick(saveInteractionSource)
                    ) { Text("Save") }
                }
            }
        },
        dismissButton = null
    )
}
