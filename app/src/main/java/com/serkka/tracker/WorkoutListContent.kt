@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
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
    onDuplicate: (Workout) -> Unit = {},
    onTogglePB: (Workout) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 170.dp,
    searchBar: (@Composable () -> Unit)? = null
) {
    // Group by day, then sort each day's workouts by id DESC so the latest
    // insertion is always on top — regardless of whether `date` came from the
    // picker (midnight UTC) or System.currentTimeMillis() (exact timestamp).
    val groupedWorkouts = workouts
        .groupBy { formatDate(it.date) }
        .mapValues { (_, list) -> list.sortedByDescending { it.id } }
    val todayKey = remember { formatDate(System.currentTimeMillis()) }
    val expandedDays = remember { mutableStateMapOf(todayKey to true) }

    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
        searchBar?.invoke()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Full-bleed flat rows — no horizontal page padding.
            contentPadding = PaddingValues(top = 6.dp, bottom = bottomPadding)
        ) {
            if (groupedWorkouts.isEmpty()) {
                item {
                    EmptyState(
                        icon = ImageVector.vectorResource(R.drawable.ic_weight_training),
                        message = "No workouts yet.\nTap + to log your first set.",
                        primaryColor = primaryColor
                    )
                }
            }

            val dayEntries = groupedWorkouts.entries.toList()
            dayEntries.forEachIndexed { dayIndex, (date, workoutsInDay) ->
                val isExpanded = expandedDays[date] ?: false

                item(key = "day_$date") {
                    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                        WorkoutDayHeader(
                            workoutsInDay = workoutsInDay,
                            isExpanded = isExpanded,
                            primaryColor = primaryColor,
                            onToggle = { expandedDays[date] = !isExpanded }
                        )

                        // ── Expanded movements: flat, grouped by indent only ─
                        if (isExpanded) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                workoutsInDay.forEachIndexed { i, workout ->
                                    WorkoutMovementRow(
                                        workout = workout,
                                        primaryColor = primaryColor,
                                        onEdit = { onEdit(workout) },
                                        onCopy = { onCopy(workout) },
                                        onDuplicate = { onDuplicate(workout) },
                                        onDelete = { onDelete(workout) },
                                        onTogglePB = { onTogglePB(workout) }
                                    )
                                    if (i < workoutsInDay.lastIndex) WorkoutEntryDivider()
                                }
                            }
                        }

                        if (dayIndex < dayEntries.lastIndex) WorkoutDayDivider()
                    }
                }
            }
        }
    }
}

// ── Shared day-grouped workout list pieces (Workouts screen + Summary "This Week") ──
// Single source for header/row paddings and text styles: tweak here, both screens follow.
// Plain Rows rather than M3 ListItem, because ListItem top-aligns trailing content once
// the text wraps to three lines; these stay vertically centred.

/** "Sets x reps @ weight" detail line; cardio entries show minutes instead. */
internal fun workoutDetailsText(workout: Workout): String {
    val isCardio = workout.exerciseName.trim().equals("Cardio", ignoreCase = true)
    return buildString {
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
}

/**
 * Tap-to-expand header for one day. Expects [workoutsInDay] sorted newest-added first
 * (id DESC); the collapsed preview is reversed so it reads in the order performed.
 */
@Composable
internal fun WorkoutDayHeader(
    workoutsInDay: List<Workout>,
    isExpanded: Boolean,
    primaryColor: Color,
    onToggle: () -> Unit
) {
    val exercises = workoutsInDay.map { it.exerciseName }.distinct()
    val hasPB = workoutsInDay.any { it.isPersonalBest }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = PageGutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // Label only; callers group by the full date (incl. year).
                text = formatDateNoYear(workoutsInDay.first().date),
                style = MaterialTheme.typography.bodyLarge,
                color = primaryColor,
                fontWeight = FontWeight.SemiBold
            )
            if (!isExpanded) {
                Text(
                    text = exercises.reversed().joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
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
}

/**
 * One exercise entry: name / details / notes, vertically centred with optional [trailing]
 * content. Put the click handling in [modifier]; it's applied before the padding so the
 * ripple covers the full row.
 */
@Composable
internal fun WorkoutEntryRow(
    workout: Workout,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val textColor = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurface
    val subtextColor = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PageGutter, end = PageGutter, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workout.exerciseName,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = workoutDetailsText(workout),
                style = MaterialTheme.typography.bodyMedium,
                color = subtextColor
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
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}

/** Divider between exercises within a day — aligned with the row text. */
@Composable
internal fun WorkoutEntryDivider() = ListRowDivider(inset = PageGutter)

/** Divider between days — full width with breathing room above and below. */
@Composable
internal fun WorkoutDayDivider() =
    ListRowDivider(modifier = Modifier.padding(vertical = 8.dp), inset = 0.dp)

// ── Workout movement row (inside expanded day card) ──────────────────────────

@Composable
private fun WorkoutMovementRow(
    workout: Workout,
    primaryColor: androidx.compose.ui.graphics.Color,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onTogglePB: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    // Box anchors the dropdown menu to the row.
    Box(modifier = Modifier.fillMaxWidth()) {
        WorkoutEntryRow(
            workout = workout,
            modifier = Modifier.clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            }
        ) {
            // Duplicate: instant copy with today's date — haptic confirms the tap.
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDuplicate()
            }) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Duplicate",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
            // Copy: opens the dialog pre-filled so it can be tweaked before saving.
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.PostAdd,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { menuOpen = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text(if (workout.isPersonalBest) "Unmark PB" else "Mark PB") },
                leadingIcon = {
                    Icon(
                        Icons.Default.EmojiEvents,
                        null,
                        tint = if (workout.isPersonalBest) PersonalBestGold else LocalContentColor.current
                    )
                },
                onClick = { menuOpen = false; onTogglePB() }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onDelete() }
            )
        }
    }
}

// ── Workout form (bottom sheet) ───────────────────────────────────────────────
// Keeps the old WorkoutDialog name/signature so the add / edit / copy call sites
// didn't change when it moved from an AlertDialog to a FormSheet.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDialog(
    workout: Workout? = null,
    history: List<Workout> = emptyList(),
    titleOverride: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Float, Long, Boolean, String, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
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

    fun fillFrom(recent: Workout) {
        sets = recent.sets.toString()
        reps = recent.reps.toString()
        weight = formatWeight(recent.weight)
    }

    val isCardio = exercise.trim().equals("Cardio", ignoreCase = true)

    FormSheet(
        title = titleOverride ?: if (workout == null) "Add Workout" else "Edit Workout",
        primaryColor = primaryColor,
        onDismiss = onDismiss,
        confirmEnabled = exercise.isNotBlank(),
        onConfirm = {
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
        }
    ) {
        // ── Recent exercises (only while the name is empty) ──────────────────
        if (exercise.isEmpty() && history.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                history.distinctBy { it.exerciseName }.take(8).forEach { recent ->
                    SuggestionChip(
                        onClick = { exercise = recent.exerciseName; fillFrom(recent) },
                        label = { Text(recent.exerciseName) }
                    )
                }
            }
        }

        // ── Exercise name with history dropdown ──────────────────────────────
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = exercise,
                onValueChange = { exercise = it; expanded = true },
                label = { Text("Exercise") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusExercise),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onNext = { if (isCardio) focusWeight.requestFocus() else focusSets.requestFocus() }
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
                            history.find { it.exerciseName == suggestion }?.let { fillFrom(it) }
                            expanded = false
                        }
                    )
                }
            }
        }

        // ── Last time (tap to reuse) ─────────────────────────────────────────
        lastPerformance?.let { last ->
            val lastIsCardio = last.exerciseName.trim().equals("Cardio", ignoreCase = true)
            AssistChip(
                onClick = { fillFrom(last) },
                label = {
                    Text(
                        if (lastIsCardio) "Last time: ${formatWeight(last.weight)} min · tap to reuse"
                        else "Last time: ${last.sets}×${last.reps} @ ${formatWeight(last.weight)}${last.weightUnit} · tap to reuse"
                    )
                },
                leadingIcon = { Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = primaryColor,
                    leadingIconContentColor = primaryColor
                )
            )
        }

        // ── Numbers ──────────────────────────────────────────────────────────
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
            step = 5f,                        // −/+ buttons: coarse jumps of 5 kg (or 5 min)
            allowDecimal = !isCardio,
            imeAction = ImeAction.Next,
            onNext = { focusNotes.requestFocus() }
        )
        // Fine adjustment: ±5 around the value in 0.5 kg (1 min) steps
        StepSlider(
            value = weight,
            onValueChange = { weight = it },
            step = if (isCardio) 1f else 0.5f,
            span = 10f,
            primaryColor = primaryColor
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

        // ── Date + PB ────────────────────────────────────────────────────────
        SheetDateRow(state = datePickerState, primaryColor = primaryColor)

        FilterChip(
            selected = isPB,
            onClick = {
                isPB = !isPB
                dialogHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            label = { Text("Personal best") },
            leadingIcon = { Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(18.dp)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = PersonalBestGold.copy(alpha = 0.18f),
                selectedLabelColor = PersonalBestGold,
                selectedLeadingIconColor = PersonalBestGold
            )
        )
    }
}
