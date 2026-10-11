@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.*

// ── Calendar page — year-at-a-glance overview of compact month grids ──────────

@Composable
fun CalendarPage(
    workoutSessions: List<WorkoutSession>,
    workouts: List<Workout>,
    primaryColor: Color,
    topPadding: Dp,
    bottomPadding: Dp = 16.dp,
    listState: LazyListState = rememberLazyListState()
) {
    // Local data only: timer sessions + weight-training workout entries.
    val allActivityDates = remember(workoutSessions, workouts) {
        val sessionDates = workoutSessions.map {
            Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        val workoutDates = workouts.map {
            Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        (sessionDates + workoutDates).distinct().sortedDescending()
    }
    val activeDates = remember(allActivityDates) { allActivityDates.toHashSet() }

    val streakInfo = remember(allActivityDates) {
        if (allActivityDates.isEmpty()) 0 to 0
        else {
            var count = 0
            var weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if (allActivityDates.none { !it.isBefore(weekStart) }) weekStart = weekStart.minusWeeks(1)
            while (allActivityDates.any { !it.isBefore(weekStart) && it.isBefore(weekStart.plusWeeks(1)) }) {
                count++
                weekStart = weekStart.minusWeeks(1)
            }
            count to allActivityDates.size
        }
    }
    val streak = streakInfo.first
    val totalActivities = streakInfo.second

    val today = LocalDate.now()
    // Latest shown month is the current month; go one year (12 months) back from it.
    val monthsByYear = remember(today.month) {
        val now = YearMonth.now()
        (0..11).map { now.minusMonths(it.toLong()) }.groupBy { it.year }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(top = topPadding),
        contentPadding = PaddingValues(top = 6.dp, start = 16.dp, end = 16.dp, bottom = bottomPadding)
    ) {
            // ── Streak / totals card ──────────────────────────────────────────
            if (allActivityDates.isNotEmpty()) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().animateContentSize().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 8.dp, pressedElevation = 4.dp, hoveredElevation = 10.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val animatedStreak by animateIntAsState(
                                targetValue = streak,
                                animationSpec = tween(durationMillis = 700),
                                label = "streak"
                            )
                            val animatedTotal by animateIntAsState(
                                targetValue = totalActivities,
                                animationSpec = tween(durationMillis = 700),
                                label = "totalActivities"
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Streak", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    Text("$animatedStreak Weeks", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Total activities", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    Text("$animatedTotal", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            // ── Year overview ─────────────────────────────────────────────────
            if (allActivityDates.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.CalendarMonth,
                        message = "No activities yet.\nLogged workouts and sessions will show up here.",
                        primaryColor = primaryColor
                    )
                }
            } else {
                monthsByYear.forEach { (year, monthsInYear) ->
                    item(key = "year_$year") {
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                        )
                    }
                    val monthPairs = monthsInYear.chunked(2)
                    items(monthPairs, key = { it.first().toString() }) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            pair.forEach { ym ->
                                MiniMonth(
                                    month = ym,
                                    activeDates = activeDates,
                                    today = today,
                                    primaryColor = primaryColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
}

// ── Compact month grid ────────────────────────────────────────────────────────

@Composable
private fun MiniMonth(
    month: YearMonth,
    activeDates: Set<LocalDate>,
    today: LocalDate,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val daysInMonth = month.lengthOfMonth()
    val firstOffset = month.atDay(1).dayOfWeek.value - 1   // Monday = 0
    val totalSlots = firstOffset + daysInMonth
    val rows = (totalSlots + 6) / 7

    Column(modifier = modifier) {
        Text(
            text = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - firstOffset + 1
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day in 1..daysInMonth) {
                            val date = month.atDay(day)
                            val isActive = activeDates.contains(date)
                            val isToday = date == today
                            val cellModifier = when {
                                isActive -> Modifier.fillMaxSize(0.8f).background(primaryColor, CircleShape)
                                isToday  -> Modifier.fillMaxSize(0.8f).border(1.5.dp, primaryColor, CircleShape)
                                else     -> Modifier
                            }
                            Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                                Text(
                                    text = day.toString(),
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (isActive || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isActive -> MaterialTheme.colorScheme.surface
                                        else     -> MaterialTheme.colorScheme.onSurface
                                    },
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
