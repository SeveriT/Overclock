@file:OptIn(ExperimentalFoundationApi::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

private const val REP_CHART_WEEKS = 12
private const val PREF_HIDDEN_STATS_EXERCISES = "stats_hidden_exercises"

@Composable
fun WorkoutStatsPage(workouts: List<Workout>, primaryColor: Color, topPadding: Dp, bottomPadding: Dp = 16.dp) {
    val weeklyRepsByExercise = remember(workouts) { weeklyReps(workouts) }

    // Ordered by total sets × reps over the last 12 weeks (most first); all-time volume breaks ties.
    // Value = all-time kg volume (shown on the right and summed into the header total).
    val workoutStats = remember(workouts, weeklyRepsByExercise) {
        workouts.filter { it.weightUnit == "kg" }
            .groupBy { it.exerciseName }
            .mapValues { entry ->
                entry.value.sumOf {
                    val s = if (it.sets > 0) it.sets.toLong() else 1L
                    val r = if (it.reps > 0) it.reps.toLong() else 1L
                    s * r * it.weight.toDouble()
                }
            }
            .toList()
            .sortedWith(
                compareByDescending<Pair<String, Double>> { (exercise, _) ->
                    weeklyRepsByExercise[exercise]?.sumOf { it.second } ?: 0
                }.thenByDescending { it.second }
            )
    }

    // Exercises the user long-pressed to hide: left out of the list and the total. Persisted.
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { PreferencesManager.getInstance(context).tracker }
    var hidden by remember {
        mutableStateOf(prefs.getStringSet(PREF_HIDDEN_STATS_EXERCISES, null)?.toSet() ?: emptySet())
    }
    fun updateHidden(newHidden: Set<String>) {
        hidden = newHidden
        prefs.edit().putStringSet(PREF_HIDDEN_STATS_EXERCISES, newHidden).apply()
    }

    val visibleStats = workoutStats.filter { it.first !in hidden }
    val hiddenStats = workoutStats.filter { it.first in hidden }
    val totalWeightLifted = visibleStats.sumOf { it.second }

    // Progress bars show each exercise's share of sets × reps over the chart window (12 weeks),
    // not kg — heavy lifts like squats would otherwise dominate.
    fun recentReps(exercise: String): Int = weeklyRepsByExercise[exercise]?.sumOf { it.second } ?: 0
    val totalRecentReps = visibleStats.sumOf { recentReps(it.first) }

    val expanded = remember { mutableStateListOf<String>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 6.dp, bottom = bottomPadding)
    ) {
        item {
            FlatSection(
                modifier = Modifier.fillMaxWidth().animateContentSize()
                    .padding(horizontal = PageGutter, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Total Volume Lifted",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val animatedWeight by animateFloatAsState(
                        targetValue = totalWeightLifted.toFloat(),
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        label = "TotalWeightAnimation"
                    )
                    Text(
                        "${String.format(Locale.getDefault(), "%,.0f", animatedWeight)} kg",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
            }
        }

        item { SectionHeader("Breakdown by Exercise") }

        itemsIndexed(visibleStats, key = { _, it -> it.first }) { index, (exercise, weight) ->
            val isExpanded = exercise in expanded
            val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevron")
            Column(Modifier.fillMaxWidth().animateContentSize().animateItem()) {
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { if (isExpanded) expanded.remove(exercise) else expanded.add(exercise) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                expanded.remove(exercise)
                                updateHidden(hidden + exercise)
                            }
                        )
                        .padding(horizontal = PageGutter, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            exercise,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${String.format(Locale.getDefault(), "%,.0f", weight)} kg",
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Hide weekly reps" else "Show weekly reps",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp).rotate(chevronRotation)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val progress = if (totalRecentReps > 0) recentReps(exercise) / totalRecentReps.toFloat() else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                        label = "ProgressAnimation"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                    )

                    if (isExpanded) {
                        weeklyRepsByExercise[exercise]?.let { WeeklyRepsChart(it, primaryColor) }
                    }
                }
                if (index < visibleStats.lastIndex) ListRowDivider(inset = PageGutter)
            }
        }

        // ── Hidden exercises (not counted in the total) ───────────────────────
        if (hiddenStats.isNotEmpty()) {
            item(key = "hidden_header") { SectionHeader("Hidden", modifier = Modifier.animateItem()) }
            itemsIndexed(hiddenStats, key = { _, it -> "hidden_${it.first}" }) { index, (exercise, weight) ->
                Column(Modifier.fillMaxWidth().animateItem()) {
                    ListItem(
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                updateHidden(hidden - exercise)
                            }
                        ),
                        colors = flatListItemColors(),
                        headlineContent = {
                            Text(exercise, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        supportingContent = {
                            Text("${String.format(Locale.getDefault(), "%,.0f", weight)} kg")
                        },
                        trailingContent = {
                            IconButton(onClick = { updateHidden(hidden - exercise) }) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = "Show $exercise",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    if (index < hiddenStats.lastIndex) ListRowDivider(inset = PageGutter)
                }
            }
        }
    }
}

// ── Weekly reps per exercise ──────────────────────────────────────────────────

/** Reps in one log entry: sets × reps (missing sets counts as 1 set; missing reps counts as 0). */
private fun repsOf(w: Workout): Int =
    if (w.reps > 0) (if (w.sets > 0) w.sets else 1) * w.reps else 0

/**
 * Per exercise: total reps for each of the last [REP_CHART_WEEKS] ISO weeks (Monday-based),
 * oldest first, ending with the current week. Weeks with no entries are 0.
 */
private fun weeklyReps(workouts: List<Workout>): Map<String, List<Pair<LocalDate, Int>>> {
    val zone = ZoneId.systemDefault()
    val thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = (REP_CHART_WEEKS - 1 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
    val firstWeek = weeks.first()
    return workouts.groupBy { it.exerciseName }.mapValues { (_, entries) ->
        val repsByWeek = entries
            .groupBy {
                Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }
            .filterKeys { !it.isBefore(firstWeek) }
            .mapValues { (_, list) -> list.sumOf(::repsOf) }
        weeks.map { it to (repsByWeek[it] ?: 0) }
    }
}

/** Bar chart of reps per week. Tap a bar to read its value; the dashed line is the average. */
@Composable
private fun WeeklyRepsChart(weeks: List<Pair<LocalDate, Int>>, primaryColor: Color) {
    var selected by remember(weeks) { mutableIntStateOf(weeks.lastIndex) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }

    val maxReps = weeks.maxOf { it.second }.coerceAtLeast(1)
    val avg = weeks.sumOf { it.second } / weeks.size.toFloat()
    val avgLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    fun weekLabel(monday: LocalDate) = "W${monday.get(WeekFields.ISO.weekOfWeekBasedYear())}"

    val (selWeek, selReps) = weeks[selected]

    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$selReps reps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (selected == weeks.lastIndex) "this week" else weekLabel(selWeek),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                "avg ${avg.roundToInt()} / week",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .pointerInput(weeks) {
                    detectTapGestures { offset ->
                        val slot = size.width / weeks.size.toFloat()
                        selected = (offset.x / slot).toInt().coerceIn(0, weeks.lastIndex)
                    }
                }
        ) {
            val slot = size.width / weeks.size
            val barW = slot * 0.6f
            weeks.forEachIndexed { i, (_, reps) ->
                val barH = (reps / maxReps.toFloat() * size.height * grow.value)
                    .coerceAtLeast(if (reps > 0) 3.dp.toPx() else 0f)
                if (barH > 0f) {
                    drawRoundRect(
                        color = if (i == selected) primaryColor else primaryColor.copy(alpha = 0.35f),
                        topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }
            }
            if (avg > 0f) {
                val y = size.height - avg / maxReps * size.height * grow.value
                drawLine(
                    color = avgLineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // Week number under every bar.
        Row(Modifier.fillMaxWidth()) {
            weeks.forEachIndexed { i, (monday, _) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "${monday.get(WeekFields.ISO.weekOfWeekBasedYear())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == selected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
