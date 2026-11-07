@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
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
    searchBar: (@Composable () -> Unit)? = null,
    // Active search text (the list is already filtered by the caller). While searching,
    // every matching day opens by default so sets/reps show without tapping each one.
    searchQuery: String = "",
    // Search is open but nothing typed yet: show "most logged" chips instead of a blank start
    searchOpen: Boolean = false,
    onPickSearch: (String) -> Unit = {},
    // Pull down past the top of the list to open search (keeps it in thumb reach)
    onPullToSearch: (() -> Unit)? = null
) {
    // Group by day, then sort each day's workouts by id DESC so the latest
    // insertion is always on top — regardless of whether `date` came from the
    // picker (midnight UTC) or System.currentTimeMillis() (exact timestamp).
    val groupedWorkouts = workouts
        .groupBy { formatDate(it.date) }
        .mapValues { (_, list) -> list.sortedByDescending { it.id } }
    val todayKey = remember { formatDate(System.currentTimeMillis()) }
    val expandedDays = remember { mutableStateMapOf(todayKey to true) }
    // Search has its own expansion state (open by default, collapsible), reset per query,
    // so searching never disturbs which days you had open in the normal list.
    val searching = searchQuery.isNotBlank()
    val searchCollapsedDays = remember(searchQuery) { mutableStateMapOf<String, Boolean>() }

    // Most-logged exercises, offered as one-tap filters when search opens empty
    val topExercises = remember(workouts) {
        workouts.groupingBy { it.exerciseName.trim() }.eachCount()
            .entries.sortedByDescending { it.value }.take(8).map { it.key }
    }

    // ── Pull-to-search: overscroll past the top of the list opens search ───────
    val pullThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val pullConnection = remember(onPullToSearch, pullThresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (onPullToSearch == null || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f) {
                    // Only leftover downward drag reaches here, i.e. the list is already at the top
                    pullDistance += available.y
                    if (pullDistance >= pullThresholdPx) {
                        pullDistance = 0f
                        onPullToSearch()
                    }
                } else if (consumed.y < 0f) {
                    pullDistance = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pullDistance = 0f   // finger lifted before the threshold: cancel
                return Velocity.Zero
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
        searchBar?.invoke()

        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(pullConnection),
            // Full-bleed flat rows — no horizontal page padding.
            contentPadding = PaddingValues(top = 6.dp, bottom = bottomPadding)
        ) {
            // ── Search opened but empty: most-logged exercises as quick filters ──
            if (searchOpen && !searching && topExercises.isNotEmpty()) {
                item(key = "search_suggestions") {
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(
                            "Most logged",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = PageGutter, vertical = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = PageGutter),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            topExercises.forEach { name ->
                                SuggestionChip(onClick = { onPickSearch(name) }, label = { Text(name) })
                            }
                        }
                    }
                }
            }

            if (groupedWorkouts.isEmpty()) {
                item {
                    if (searching) {
                        EmptyState(
                            icon = Icons.Default.Search,
                            message = "No exercises match \"${searchQuery.trim()}\".",
                            primaryColor = primaryColor
                        )
                    } else {
                        EmptyState(
                            icon = ImageVector.vectorResource(R.drawable.ic_weight_training),
                            message = "No workouts yet.\nTap + to log your first set.",
                            primaryColor = primaryColor
                        )
                    }
                }
            }

            val dayEntries = groupedWorkouts.entries.toList()
            // Monday of each day's ISO week, for the week headers between days
            val dayWeekStarts = dayEntries.map { (_, list) -> weekStartOf(list.first().date) }
            val daysPerWeek = dayWeekStarts.groupingBy { it }.eachCount()
            val thisWeekStart = weekStartOf(System.currentTimeMillis())

            dayEntries.forEachIndexed { dayIndex, (date, workoutsInDay) ->
                val isExpanded = if (searching) searchCollapsedDays[date] != true
                                 else expandedDays[date] ?: false
                val weekStart = dayWeekStarts[dayIndex]

                // ── Week header where the week changes (list is newest first) ──
                if (dayIndex == 0 || dayWeekStarts[dayIndex - 1] != weekStart) {
                    // Gap before a new week is its own item so it doesn't stick with the header
                    if (dayIndex > 0) item(key = "week_gap_$weekStart") { Spacer(Modifier.height(16.dp)) }
                    // Sticky: stays pinned under the top bar while scrolling through its week,
                    // and the next week's header pushes it off — so the week change is obvious.
                    stickyHeader(key = "week_$weekStart") {
                        WorkoutWeekHeader(
                            weekStart = weekStart,
                            gymDays = daysPerWeek[weekStart] ?: 0,
                            isCurrentWeek = weekStart == thisWeekStart,
                            primaryColor = primaryColor
                        )
                    }
                }
                // Divider only between days of the same week; a new week gets its header instead
                val nextDaySameWeek = dayIndex < dayEntries.lastIndex && dayWeekStarts[dayIndex + 1] == weekStart

                item(key = "day_$date") {
                    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                        WorkoutDayHeader(
                            workoutsInDay = workoutsInDay,
                            isExpanded = isExpanded,
                            primaryColor = primaryColor,
                            onToggle = {
                                if (searching) searchCollapsedDays[date] = isExpanded
                                else expandedDays[date] = !isExpanded
                            },
                            showDate = false   // the sticky week header carries the dates
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

                        if (nextDaySameWeek) WorkoutDayDivider()
                    }
                }
            }
        }
        // Pull-to-search hint: fades in with the pull, under the top of the list
        if (onPullToSearch != null && pullDistance > 0f) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = (pullDistance / pullThresholdPx).coerceIn(0f, 1f)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(24.dp)
            )
        }
        }
    }
}

/** Monday of the ISO week containing [epochMs], in the device zone (matches the day grouping). */
private fun weekStartOf(epochMs: Long): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Sticky week separator: filled "W44" badge, date range (or "This week"), gym-day count,
 * and an accent rule underneath. Opaque background so rows scroll cleanly beneath it.
 */
@Composable
private fun WorkoutWeekHeader(
    weekStart: LocalDate,
    gymDays: Int,
    isCurrentWeek: Boolean,
    primaryColor: Color
) {
    val weekNumber = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())
    val rangeFormat = DateTimeFormatter.ofPattern("d.M")
    val subtitle = if (isCurrentWeek) "This week"
                   else "${weekStart.format(rangeFormat)} – ${weekStart.plusDays(6).format(rangeFormat)}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "W$weekNumber",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .background(primaryColor, TrackerShapes.Pill)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (gymDays == 1) "1 gym day" else "$gymDays gym days",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(thickness = 1.dp, color = primaryColor.copy(alpha = 0.5f))
    }
}

// ── Shared day-grouped workout list pieces (Workouts screen + Summary "This Week") ──
// Single source for header/row paddings and text styles: tweak here, both screens follow.
// Plain Rows rather than M3 ListItem, because ListItem top-aligns trailing content once
// the text wraps to three lines; these stay vertically centred.

/** "Sets x reps @ weight" detail line; cardio entries show minutes instead. */
internal fun workoutDetailsText(workout: Workout): String {
    val isCardio = isTimeBasedExercise(workout.exerciseName)
    val base = buildString {
        if (isCardio) {
            if (workout.weight > 0) append("${formatWeight(workout.weight)} min")
        } else if (isHoldExercise(workout.exerciseName)) {
            if (workout.reps > 0) append(formatHoldSets(workout.sets, workout.reps))
        } else {
            if (workout.sets > 0) append("${workout.sets} sets ")
            if (workout.reps > 0) {
                if (workout.sets > 0) append("x ")
                append("${workout.reps} reps ")
            }
            if (workout.weight > 0) append("@ ${formatWeight(workout.weight)}${workout.weightUnit}")
        }
    }.trim()
    val rpe = workout.rpe?.let { "RPE ${formatRpe(it)}" } ?: return base
    return if (base.isEmpty()) rpe else "$base · $rpe"
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
    onToggle: () -> Unit,
    // false = weekday only ("Tuesday"), for lists where a week header already gives the dates
    showDate: Boolean = true
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
                text = if (showDate) formatDateNoYear(workoutsInDay.first().date)
                       else formatWeekday(workoutsInDay.first().date),
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
    // exercise, sets, reps, weight, date, isPB, unit, notes, rpe (null = not recorded)
    onConfirm: (String, Int, Int, Float, Long, Boolean, String, String, Float?) -> Unit,
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
    var rpe by remember { mutableStateOf(workout?.rpe) }
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

    val isCardio = isTimeBasedExercise(exercise)
    val isHold = isHoldExercise(exercise)   // plank / lankku: sets × duration

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
                reps.toIntOrNull() ?: 0,                       // holds: seconds per set
                if (isHold) 0f else weight.toLeadFloat() ?: 0f,
                datePickerState.selectedDateMillis ?: System.currentTimeMillis(),
                isPB,
                weightUnit,
                notes,
                rpe
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
            val lastIsCardio = isTimeBasedExercise(last.exerciseName)
            AssistChip(
                onClick = { fillFrom(last) },
                label = {
                    // Show last time's RPE too (for reference only — reusing doesn't copy it,
                    // since RPE rates the set being logged now)
                    val lastRpe = last.rpe?.let { " · RPE ${formatRpe(it)}" } ?: ""
                    Text(
                        if (lastIsCardio) "Last time: ${formatWeight(last.weight)} min$lastRpe · tap to reuse"
                        else if (isHoldExercise(last.exerciseName)) "Last time: ${formatHoldSets(last.sets, last.reps)}$lastRpe · tap to reuse"
                        else "Last time: ${last.sets}×${last.reps} @ ${formatWeight(last.weight)}${last.weightUnit}$lastRpe · tap to reuse"
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
        if (isHold) {
            // Sets, then the hold time per set as Min + Sec (stored as total seconds in `reps`)
            val totalSec = reps.toIntOrNull() ?: 0
            NumericInput(value = sets, onValueChange = { sets = it }, label = "Sets",
                modifier = Modifier.fillMaxWidth().focusRequester(focusSets),
                imeAction = ImeAction.Next, onNext = { focusReps.requestFocus() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericInput(
                    value = if (reps.isBlank()) "" else (totalSec / 60).toString(),
                    onValueChange = { m -> reps = ((m.toIntOrNull() ?: 0) * 60 + totalSec % 60).toString() },
                    label = "Min",
                    modifier = Modifier.weight(1f).focusRequester(focusReps),
                    imeAction = ImeAction.Next, onNext = { focusNotes.requestFocus() }
                )
                NumericInput(
                    value = if (reps.isBlank()) "" else (totalSec % 60).toString(),
                    // Typing 75 s rolls over into 1 min 15 s
                    onValueChange = { s -> reps = ((totalSec / 60) * 60 + (s.toIntOrNull() ?: 0)).toString() },
                    label = "Sec",
                    modifier = Modifier.weight(1f),
                    step = 5f,
                    imeAction = ImeAction.Next, onNext = { focusNotes.requestFocus() }
                )
            }
        }
        if (!isCardio && !isHold) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericInput(value = sets, onValueChange = { sets = it }, label = "Sets",
                    modifier = Modifier.weight(1f).focusRequester(focusSets),
                    imeAction = ImeAction.Next, onNext = { focusReps.requestFocus() })
                NumericInput(value = reps, onValueChange = { reps = it }, label = "Reps",
                    modifier = Modifier.weight(1f).focusRequester(focusReps),
                    imeAction = ImeAction.Next, onNext = { focusWeight.requestFocus() })
            }
        }
        // Holds (plank) have no weight/minutes field — their time is the Min/Sec above
        if (!isHold) NumericInput(
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
        if (!isHold) StepSlider(
            value = weight,
            onValueChange = { weight = it },
            step = if (isCardio) 1f else 0.5f,
            span = 10f,
            primaryColor = primaryColor
        )

        // Optional effort rating — starts "Not set"; Clear unsets it
        RpeSlider(rpe = rpe, onRpeChange = { rpe = it }, primaryColor = primaryColor)

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
