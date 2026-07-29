@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.serkka.tracker

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkka.tracker.ui.theme.PersonalBestGold
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@Composable
fun SummaryPage(
    workouts: List<Workout>,
    bodyWeights: List<BodyWeight>,
    workoutSessions: List<WorkoutSession>,
    stravaViewModel: StravaViewModel,
    stepsViewModel: StepsViewModel,
    primaryColor: Color,
    onWorkoutEdit: (Workout) -> Unit,
    onWorkoutDelete: (Workout) -> Unit,
    onWorkoutCopy: (Workout) -> Unit,
    onNavigateToWeightTracking: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onNavigateToReps: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 16.dp,
    weightCardVisible: Boolean = true,
    onHideWeightCard: () -> Unit = {}
) {
    val context = LocalContext.current
    val activities by stravaViewModel.activities.collectAsState()
    val isLoading by stravaViewModel.isLoading.collectAsState()
    val savedToken by stravaViewModel.savedToken.collectAsState()
    var refreshTrigger by remember { mutableStateOf(false) }

    val isRefreshing = refreshTrigger && isLoading

    LaunchedEffect(isLoading) {
        if (!isLoading && refreshTrigger) refreshTrigger = false
    }

    val lastWeight = remember(bodyWeights) { bodyWeights.maxByOrNull { it.date } }

    val today = LocalDate.now()

    val weekWorkouts = remember(workouts) {
        val weekAgo = today.minusDays(6)
        workouts.filter {
            val d = Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
            !d.isBefore(weekAgo) && !d.isAfter(today)
        }.sortedByDescending { it.date }
    }

    val weekWorkoutsGrouped = remember(weekWorkouts) {
        weekWorkouts.groupBy { formatDate(it.date) }
    }

    // Weekly dots use local sessions + weight-training workout entries only (no Strava).
    val weeklyStreak = remember(workoutSessions, workouts, today) {
        val startDate = today.minusDays(6)
        val sessionDates = workoutSessions.map { session ->
            Instant.ofEpochMilli(session.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toSet()
        val workoutDates = workouts.map { w ->
            Instant.ofEpochMilli(w.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toSet()
        (0..6).map { i ->
            val date = startDate.plusDays(i.toLong())
            date to (sessionDates.contains(date) || workoutDates.contains(date))
        }
    }

    // Count of activities per day (local sessions + workouts). Used to badge multi-workout days.
    val weeklyActivityCount = remember(workoutSessions, workouts, today) {
        val startDate = today.minusDays(6)
        val sessionCounts = workoutSessions
            .map { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate() }
            .filter { !it.isBefore(startDate) && !it.isAfter(today) }
            .groupingBy { it }
            .eachCount()
        // Workout rows are per-exercise, so collapse to one weight-training session per day.
        val workoutCounts = workouts
            .map { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate() }
            .filter { !it.isBefore(startDate) && !it.isAfter(today) }
            .distinct()
            .associateWith { 1 }
        (sessionCounts.keys + workoutCounts.keys).associateWith {
            (sessionCounts[it] ?: 0) + (workoutCounts[it] ?: 0)
        }
    }

    // Primary (type, name) per day for icon selection. Sessions win, then weight-training workouts.
    val weeklyActivityType = remember(workoutSessions, workouts, today) {
        val startDate = today.minusDays(6)
        val map = mutableMapOf<LocalDate, Pair<String, String?>>()
        workoutSessions.forEach { s ->
            val d = Instant.ofEpochMilli(s.date).atZone(ZoneId.systemDefault()).toLocalDate()
            if (!d.isBefore(startDate) && !d.isAfter(today)) {
                map.putIfAbsent(d, s.type to s.name)
            }
        }
        workouts.forEach { w ->
            val d = Instant.ofEpochMilli(w.date).atZone(ZoneId.systemDefault()).toLocalDate()
            if (!d.isBefore(startDate) && !d.isAfter(today)) {
                map.putIfAbsent(d, "WeightTraining" to null)
            }
        }
        map
    }

    // Consecutive week-streak: how many weeks back have at least one active day.
    // Local sessions + workout entries only (no Strava). If the current week has no
    // activity yet, we still count the streak from last week (matches StravaCalendarPage).
    val weekStreak = remember(workoutSessions, workouts, today) {
        val sessionDates = workoutSessions.map { s ->
            Instant.ofEpochMilli(s.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toHashSet()
        val workoutDates = workouts.map { w ->
            Instant.ofEpochMilli(w.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toHashSet()
        val allActiveDates = sessionDates + workoutDates
        var weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        if (allActiveDates.none { !it.isBefore(weekStart) }) {
            weekStart = weekStart.minusWeeks(1)
        }
        var count = 0
        while (count < 520) {
            val weekEnd = weekStart.plusDays(6)
            val any = allActiveDates.any { !it.isBefore(weekStart) && !it.isAfter(weekEnd) }
            if (!any) break
            count++
            weekStart = weekStart.minusWeeks(1)
        }
        count
    }

    // Combine Strava activities and local sessions into one sorted list
    data class RecentItem(
        val name: String,
        val type: String,
        val date: Long,          // epoch ms
        val durationSeconds: Int,
        val distance: Float = 0f,
        val calories: Float = 0f,
        val isStrava: Boolean = false,
        val stravaId: Long? = null
    )

    val recentItems = remember(activities, workoutSessions) {
        val sevenDaysAgo = today.minusDays(6)
        val stravaItems = activities.mapNotNull { activity ->
            val activityDate = LocalDate.parse(activity.startDate.substringBefore("T"))
            if (activityDate.isBefore(sevenDaysAgo) || activityDate.isAfter(today)) return@mapNotNull null
            val epochMs = try {
                java.time.OffsetDateTime.parse(activity.startDate.replace("Z", "+00:00"))
                    .toInstant().toEpochMilli()
            } catch (_: Exception) { 0L }
            RecentItem(activity.name, activity.type, epochMs, activity.movingTime, activity.distance, activity.calories, isStrava = true, stravaId = activity.id)
        }
        val sessionItems = workoutSessions.mapNotNull { session ->
            val d = Instant.ofEpochMilli(session.date).atZone(ZoneId.systemDefault()).toLocalDate()
            if (d.isBefore(sevenDaysAgo) || d.isAfter(today)) return@mapNotNull null
            RecentItem(session.name, session.type, session.date, session.durationSeconds)
        }
        (stravaItems + sessionItems).sortedByDescending { it.date }
    }

    val recentItemsGrouped = remember(recentItems) {
        recentItems.groupBy { formatDate(it.date) }
    }

    // Aggregate totals for the last-7-days summary card (Strava + local sessions).
    data class WeeklyTotals(
        val activityCount: Int,
        val totalSeconds: Int,
        val totalCalories: Int,
        val totalDistanceKm: Float
    )
    val weeklyTotals = remember(recentItems) {
        WeeklyTotals(
            activityCount = recentItems.size,
            totalSeconds = recentItems.sumOf { it.durationSeconds },
            totalCalories = recentItems.sumOf { it.calories.toDouble() }.toInt(),
            totalDistanceKm = (recentItems.sumOf { it.distance.toDouble() } / 1000.0).toFloat()
        )
    }

    val expandedDays = remember { mutableStateMapOf<String, Boolean>() }
    val expandedActivityDays = remember { mutableStateMapOf<String, Boolean>() }
    val haptic = LocalHapticFeedback.current

    // Steps state
    val hasStepsPermission by stepsViewModel.hasPermission.collectAsState()
    val todaySteps by stepsViewModel.todaySteps.collectAsState()
    val weeklySteps by stepsViewModel.weeklySteps.collectAsState()
    val stepGoal by stepsViewModel.stepGoal.collectAsState()
    val isStepsCardVisible by stepsViewModel.isCardVisible.collectAsState()
    val stepsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> stepsViewModel.onPermissionResult(granted) }
    var showStepGoalDialog by remember { mutableStateOf(false) }
    var showAddStepsDialog by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (savedToken.isBlank()) {
                Toast.makeText(context, "Link Strava to refresh activities", Toast.LENGTH_SHORT).show()
            } else {
                refreshTrigger = true
                stravaViewModel.checkAndFetchActivities()
            }
        },
        modifier = Modifier.padding(top = topPadding)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Weekly streak dots ────────────────────────────────────────────
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flame + week-streak counter
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(end = 20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_streak_flame),
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(width = 34.dp, height = 45.dp)
                                )
                                Text(
                                    text = weekStreak.toString(),
                                    color = MaterialTheme.colorScheme.surface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                            Text(
                                text = "Weeks",
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Mon-Sun row
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weeklyStreak.forEach { (date, hadActivity) ->
                                val isToday = date == today
                                val isFuture = date.isAfter(today)
                                val isActive = !isFuture && hadActivity

                                val activityCount = weeklyActivityCount[date] ?: 0
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = date.dayOfWeek.getDisplayName(
                                            java.time.format.TextStyle.SHORT, Locale.getDefault()
                                        ).take(1),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Box(contentAlignment = Alignment.Center) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .background(
                                                    color = if (isActive) Color.White
                                                            else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .border(
                                                    width = if (isToday) 1.5.dp else 1.dp,
                                                    color = when {
                                                        isToday -> Color.White
                                                        isActive -> Color.Transparent
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                    },
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when {
                                                isActive -> {
                                                    val (activityType, activityName) = weeklyActivityType[date] ?: ("WeightTraining" to null)
                                                    Icon(
                                                        imageVector = getIconForActivity(activityType, activityName),
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                else -> Text(
                                                    text = date.dayOfMonth.toString(),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                            else Color.White,
                                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                        // Badge for multi-workout days
                                        if (activityCount > 1) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 6.dp, y = (-6).dp)
                                                    .size(14.dp)
                                                    .background(primaryColor, CircleShape)
                                                    .border(1.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = activityCount.toString(),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    fontSize = 9.sp,
                                                    lineHeight = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    style = androidx.compose.material3.LocalTextStyle.current.copy(
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
            }


            // ── Steps card ───────────────────────────────────────────────────
            val hasStepData = todaySteps > 0 || weeklySteps.any { it.second > 0 }
            if ((stepsViewModel.isAvailable || hasStepData) && isStepsCardVisible) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Steps",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasStepsPermission) {
                                IconButton(
                                    onClick = { showStepGoalDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit step goal",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            IconButton(
                                onClick = { stepsViewModel.setCardVisible(false) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = "Hide steps card",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().animateContentSize().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                    ) {
                        if (!hasStepsPermission && !hasStepData) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Step Counter", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Allow activity access to track steps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val connectInteractionSource = remember { MutableInteractionSource() }
                                Button(
                                    onClick = { stepsPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                                    interactionSource = connectInteractionSource,
                                    modifier = Modifier.bounceClick(connectInteractionSource),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = MaterialTheme.colorScheme.surface)
                                ) { Text("Allow") }
                            }
                        } else {
                            val goalSteps = stepGoal
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = String.format(Locale.getDefault(), "%,d", todaySteps),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "steps today",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val progress = (todaySteps.toFloat() / goalSteps).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(0.75f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = primaryColor,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        drawStopIndicator = {},
                                        gapSize = 0.dp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%,d", todaySteps)} / ${String.format(Locale.getDefault(), "%,d", goalSteps)} goal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (weeklySteps.isNotEmpty()) {
                                    val stepsBarAnim = remember { Animatable(0f) }
                                    LaunchedEffect(weeklySteps) {
                                        stepsBarAnim.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
                                    }
                                    val canvasInteractionSource = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .width(150.dp)
                                            .height(75.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(primaryColor.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                            .combinedClickable(
                                                interactionSource = canvasInteractionSource,
                                                indication = null,
                                                onClick = {},
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showAddStepsDialog = true
                                                }
                                            )
                                            .padding(8.dp)
                                    ) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            val goalF = goalSteps.toFloat()
                                            val todayDate = LocalDate.now()
                                            val barSlot = size.width / weeklySteps.size
                                            val barW = barSlot * 0.6f
                                            val barOffset = (barSlot - barW) / 2f
                                            weeklySteps.forEachIndexed { i, (date, steps) ->
                                                val normalizedH = (steps / goalF * stepsBarAnim.value).coerceIn(0f, 1f)
                                                val barH = (size.height * normalizedH).coerceAtLeast(if (steps > 0) 3.dp.toPx() else 0f)
                                                if (barH > 0f) {
                                                    drawRoundRect(
                                                        color = if (date == todayDate) primaryColor else primaryColor.copy(alpha = 0.35f),
                                                        topLeft = Offset(i * barSlot + barOffset, size.height - barH),
                                                        size = Size(barW, barH),
                                                        cornerRadius = CornerRadius(3.dp.toPx())
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
            }

            if (showStepGoalDialog) {
                item {
                    var goalInput by remember { mutableStateOf(stepGoal.toString()) }
                    AlertDialog(
                        onDismissRequest = { showStepGoalDialog = false },
                        title = { Text("Edit step goal") },
                        text = {
                            OutlinedTextField(
                                value = goalInput,
                                onValueChange = { input -> goalInput = input.filter { it.isDigit() }.take(6) },
                                label = { Text("Daily step goal") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val parsed = goalInput.toLongOrNull()?.coerceAtLeast(100L) ?: 10_000L
                                stepsViewModel.setStepGoal(parsed)
                                showStepGoalDialog = false
                            }) { Text("Save", color = primaryColor) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStepGoalDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }

            if (showAddStepsDialog) {
                item {
                    var addInput by remember { mutableStateOf("") }
                    val last7Days = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
                    var selectedDate by remember { mutableStateOf(today) }
                    val currentForDay = weeklySteps.firstOrNull { it.first == selectedDate }?.second ?: 0L
                    AlertDialog(
                        onDismissRequest = { showAddStepsDialog = false },
                        title = { Text("Add steps") },
                        text = {
                            Column {
                                Text(
                                    "Day",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    last7Days.forEach { d ->
                                        val chipSource = remember(d) { MutableInteractionSource() }
                                        val label = when (d) {
                                            today -> "Today"
                                            today.minusDays(1) -> "Yest"
                                            else -> d.dayOfWeek.getDisplayName(
                                                java.time.format.TextStyle.SHORT, Locale.getDefault()
                                            )
                                        }
                                        FilterChip(
                                            selected = d == selectedDate,
                                            onClick = { selectedDate = d },
                                            label = { Text(label) },
                                            modifier = Modifier.bounceClick(chipSource),
                                            interactionSource = chipSource,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                                                selectedLabelColor = primaryColor
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = addInput,
                                    onValueChange = { input -> addInput = input.filter { it.isDigit() }.take(5) },
                                    label = { Text("Steps to add") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Currently: ${String.format(Locale.getDefault(), "%,d", currentForDay)} steps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                addInput.toLongOrNull()
                                    ?.takeIf { it > 0 }
                                    ?.let { stepsViewModel.addStepsManually(it, selectedDate) }
                                showAddStepsDialog = false
                            }) { Text("Add", color = primaryColor) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddStepsDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }

            // ── Latest weight card ────────────────────────────────────────────
            if (weightCardVisible) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Latest Weight",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = onHideWeightCard,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = "Hide weight card",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    val weekWeights = remember(bodyWeights) {
                        val twoDaysAgo = today.minusDays(13)
                        bodyWeights.filter {
                            val d = Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
                            !d.isBefore(twoDaysAgo) && !d.isAfter(today)
                        }.sortedBy { it.date }
                    }

                    val weightInteractionSource = remember { MutableInteractionSource() }
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .padding(top = 8.dp)
                            .bounceClick(
                                interactionSource = weightInteractionSource,
                                onClick = onNavigateToWeightTracking
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 10.dp
                        )
                    ) {
                        if (lastWeight == null) {
                            EmptyState(
                                icon = Icons.Default.MonitorWeight,
                                message = "No weight entries yet.\nTap to add your first.",
                                primaryColor = primaryColor
                            )
                        } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(
                                    text = "${formatWeight(lastWeight.weight)} kg",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = formatDate(lastWeight.date),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (weekWeights.size >= 2) {
                                val trend = weekWeights.last().weight - weekWeights.first().weight

                                Box(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .height(75.dp)
                                        .background(
                                            color = primaryColor.copy(alpha = 0.05f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(8.dp)
                                ) {
                                    val animationProgress = remember { Animatable(0f) }
                                    LaunchedEffect(Unit) {
                                        animationProgress.animateTo(
                                            1f,
                                            animationSpec = tween(500, easing = FastOutSlowInEasing)
                                        )
                                    }
                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        val weights = weekWeights.map { it.weight }
                                        val rawMin = weights.minOrNull() ?: 0f
                                        val rawMax = weights.maxOrNull() ?: 100f
                                        val pad = (rawMax - rawMin).coerceAtLeast(1f) * 0.15f
                                        val minWeight = rawMin - pad
                                        val maxWeight = rawMax + pad
                                        val range = (maxWeight - minWeight).coerceAtLeast(1f)
                                        val graphWidth  = size.width
                                        val graphHeight = size.height
                                        val spacing = graphWidth / (weights.size - 1).coerceAtLeast(1)

                                        // Build evenly-spaced points
                                        val points = weights.mapIndexed { index, weight ->
                                            val x = index * spacing
                                            val y = graphHeight - ((weight - minWeight) / range * graphHeight)
                                            Offset(x, y)
                                        }

                                        // Smooth cubic bezier helper (Catmull-Rom → Bézier)
                                        fun Path.smoothCurveTo(pts: List<Offset>) {
                                            if (pts.size < 2) return
                                            moveTo(pts.first().x, pts.first().y)
                                            if (pts.size == 2) { lineTo(pts[1].x, pts[1].y); return }
                                            for (i in 0 until pts.size - 1) {
                                                val cur  = pts[i]
                                                val next = pts[i + 1]
                                                val cp1x = cur.x  + (next.x - (if (i > 0) pts[i - 1].x else cur.x)) / 6f
                                                val cp1y = cur.y  + (next.y - (if (i > 0) pts[i - 1].y else cur.y)) / 6f
                                                val cp2x = next.x - ((if (i < pts.size - 2) pts[i + 2].x else next.x) - cur.x) / 6f
                                                val cp2y = next.y - ((if (i < pts.size - 2) pts[i + 2].y else next.y) - cur.y) / 6f
                                                cubicTo(cp1x, cp1y, cp2x, cp2y, next.x, next.y)
                                            }
                                        }

                                        // Grid lines
                                        val gridColor = Color(0xFF424349)
                                        for (i in 0..3) {
                                            val yPx = graphHeight * (i.toFloat() / 3f)
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(0f, yPx),
                                                end   = Offset(graphWidth, yPx),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }

                                        // Fill gradient
                                        val fillPath = Path().apply {
                                            smoothCurveTo(points)
                                            lineTo(points.last().x, graphHeight)
                                            lineTo(points.first().x, graphHeight)
                                            close()
                                        }
                                        drawPath(
                                            path  = fillPath,
                                            brush = Brush.verticalGradient(
                                                listOf(primaryColor.copy(alpha = 0.25f * animationProgress.value), Color.Transparent)
                                            )
                                        )

                                        // Smooth line
                                        val linePath = Path().apply { smoothCurveTo(points) }
                                        drawPath(linePath, color = primaryColor, style = Stroke(width = 2.5.dp.toPx()), alpha = animationProgress.value)

                                        // Hollow dots
                                        points.forEach { pt ->
                                            drawCircle(color = primaryColor,        radius = 4.dp.toPx() * animationProgress.value, center = pt)
                                            drawCircle(color = Color(0xFF24252B),   radius = 2.dp.toPx() * animationProgress.value, center = pt)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .background(
                                                color = when {
                                                    trend > 0.1f  -> Color(0xFFEE3E3E).copy(alpha = 0.7f)
                                                    trend < -0.1f -> Color(0xFF46CE46).copy(alpha = 0.7f)
                                                    else          -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                trend > 0.1f  -> Icons.Default.TrendingUp
                                                trend < -0.1f -> Icons.Default.TrendingDown
                                                else          -> Icons.Default.TrendingFlat
                                            },
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "${if (trend > 0) "+" else ""}${formatWeight(trend)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }

            // ── This week's exercises ─────────────────────────────────────────
            item {
                val repsTitleInteractionSource = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "This Week's Gym Entries",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .bounceClick(repsTitleInteractionSource)
                            .clickable(
                                interactionSource = repsTitleInteractionSource,
                                indication = null,
                                onClick = onNavigateToReps
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "See all")
                    }
                }
            }

            if (weekWorkouts.isEmpty()) {
                item {
                    EmptyState(
                        icon = ImageVector.vectorResource(R.drawable.ic_weight_training),
                        message = "No exercises recorded this week yet."
                    )
                }
            } else {

                weekWorkoutsGrouped.forEach { (date, workoutsInDay) ->
                    val isExpanded = expandedDays[date] ?: false
                    val exercises = workoutsInDay.map { it.exerciseName }.distinct()
                    val totalSets = workoutsInDay.sumOf { it.sets }
                    val hasPB = workoutsInDay.any { it.isPersonalBest }

                    item(key = "summary_day_$date") {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column {
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
                                            style = MaterialTheme.typography.titleSmall,
                                            color = primaryColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${exercises.size} exercises · $totalSets sets",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!isExpanded) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = exercises.joinToString(", "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isExpanded) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )
                                    workoutsInDay.forEach { workout ->
                                        val textColor = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurface
                                        val subtextColor = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurfaceVariant
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = { onWorkoutEdit(workout) },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onWorkoutCopy(workout)
                                                    }
                                                )
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = workout.exerciseName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = textColor,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val details = buildString {
                                                    if (workout.sets > 0) append("${workout.sets} sets ")
                                                    if (workout.reps > 0) {
                                                        if (workout.sets > 0) append("x ")
                                                        append("${workout.reps} reps ")
                                                    }
                                                    if (workout.weight > 0) append("@ ${formatWeight(workout.weight)}${workout.weightUnit}")
                                                }
                                                Text(details, style = MaterialTheme.typography.bodySmall, color = subtextColor)
                                                if (workout.notes.isNotBlank()) {
                                                    Text(
                                                        workout.notes,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = subtextColor.copy(alpha = 0.8f),
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
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // ── Recent Activity (combined Strava + local sessions) ──────────
            item {
                val sessionsTitleInteractionSource = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Activity (Last 7 Days)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .bounceClick(sessionsTitleInteractionSource)
                            .clickable(
                                interactionSource = sessionsTitleInteractionSource,
                                indication = null,
                                onClick = onNavigateToSessions
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "See all")
                    }
                }
            }

            // ── Last 7 days totals ────────────────────────────────────────────
            if (recentItems.isNotEmpty()) {
                item {
                    val totalMinutes = weeklyTotals.totalSeconds / 60
                    val timeText = when {
                        totalMinutes < 60      -> "${totalMinutes}m"
                        totalMinutes % 60 == 0 -> "${totalMinutes / 60}h"
                        else                   -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
                    }
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Top
                        ) {
                            WeeklyStatCell(Icons.Default.Schedule, timeText, "Time", primaryColor, Modifier.weight(1f))
                            WeeklyStatCell(Icons.Default.LocalFireDepartment, "${weeklyTotals.totalCalories}", "Calories", primaryColor, Modifier.weight(1f))
                            if (weeklyTotals.totalDistanceKm > 0f) {
                                WeeklyStatCell(
                                    Icons.AutoMirrored.Filled.DirectionsRun,
                                    String.format(Locale.getDefault(), "%.1f km", weeklyTotals.totalDistanceKm),
                                    "Distance", primaryColor, Modifier.weight(1f)
                                )
                            }
                            WeeklyStatCell(Icons.Default.FitnessCenter, "${weeklyTotals.activityCount}", "Activities", primaryColor, Modifier.weight(1f))
                        }
                    }
                }
            }

            if (isLoading && activities.isEmpty() && recentItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = primaryColor) }
                }
            } else if (recentItems.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Schedule,
                        message = "No activity in the past 7 days."
                    )
                }
            } else {
                items(recentItems, key = { "${it.date}_${it.name}" }) { item ->
                    val accentColor = primaryColor
                    val cardModifier = if (item.isStrava && item.stravaId != null) {
                        Modifier.fillMaxWidth().clickable { context.openStravaActivity(item.stravaId) }
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    ElevatedCard(
                        modifier = cardModifier,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(36.dp)) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(accentColor.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(getIconForActivity(item.type, item.name), null, tint = accentColor, modifier = Modifier.size(21.dp))
                                }
                                if (item.isStrava) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.strava_logo),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).clip(RoundedCornerShape(6.dp))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val details = buildString {
                                    append(item.type)
                                    val totalMinutes = item.durationSeconds / 60
                                    append(" · ")
                                    append(when {
                                        totalMinutes < 60      -> "$totalMinutes min"
                                        totalMinutes % 60 == 0 -> "${totalMinutes / 60} h"
                                        else                   -> "${totalMinutes / 60} h ${totalMinutes % 60} min"
                                    })
                                    if (item.isStrava && item.distance > 0f) {
                                        append(" · ${String.format(Locale.getDefault(), "%.1f", item.distance / 1000f)} km")
                                    }
                                }
                                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    formatDate(item.date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Weekly totals stat cell ───────────────────────────────────────────────────
@Composable
private fun WeeklyStatCell(
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
