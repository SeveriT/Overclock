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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
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
    val activityData = remember(activities) { stravaViewModel.getActivityData() }

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

    val weeklyStreak = remember(activityData, workoutSessions, today) {
        val startDate = today.minusDays(6)
        val sessionDates = workoutSessions.map { session ->
            Instant.ofEpochMilli(session.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toSet()
        (0..6).map { i ->
            val date = startDate.plusDays(i.toLong())
            val dateString = String.format(Locale.getDefault(), "%04d-%02d-%02d", date.year, date.monthValue, date.dayOfMonth)
            date to (activityData.containsKey(dateString) || sessionDates.contains(date))
        }
    }

    // Consecutive week-streak: how many weeks back (including current) have at least one active day.
    val weekStreak = remember(activityData, workoutSessions, workouts, today) {
        val sessionDates = workoutSessions.map { s ->
            Instant.ofEpochMilli(s.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toHashSet()
        val workoutDates = workouts.map { w ->
            Instant.ofEpochMilli(w.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toHashSet()
        val stravaDates = activityData.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toHashSet()
        val allActiveDates = sessionDates + workoutDates + stravaDates
        var weekStart = today.with(java.time.DayOfWeek.MONDAY)
        var count = 0
        while (count < 520) {
            val weekEnd = weekStart.plusDays(6)
            val any = allActiveDates.any { !it.isBefore(weekStart) && !it.isAfter(weekEnd) }
            if (!any) break
            count++
            weekStart = weekStart.minusDays(7)
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
                        val flameColor = Color(0xFFFF6D24)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(end = 20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_streak_flame),
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(width = 32.dp, height = 45.dp)
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
                                val stepsForDay = weeklySteps.firstOrNull { it.first == date }?.second ?: 0L
                                val hitStepGoal = stepGoal > 0 && stepsForDay >= stepGoal && !isFuture
                                val isPastActive = !isFuture && !isToday && (hadActivity || hitStepGoal)

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
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(
                                                color = if (isPastActive) Color.White
                                                        else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = if (isToday) 1.5.dp else 1.dp,
                                                color = when {
                                                    isToday -> Color.White
                                                    isPastActive -> Color.Transparent
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                },
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            isPastActive && hadActivity -> Icon(
                                                imageVector = Icons.Default.FitnessCenter,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            isPastActive && hitStepGoal -> Box(contentAlignment = Alignment.TopEnd) {
                                                Icon(
                                                    imageVector = Icons.Default.TouchApp,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(flameColor, CircleShape)
                                                        .offset(x = 2.dp, y = (-2).dp)
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
                    var addToYesterday by remember { mutableStateOf(false) }
                    AlertDialog(
                        onDismissRequest = { showAddStepsDialog = false },
                        title = { Text("Add steps") },
                        text = {
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val todayChip = remember { MutableInteractionSource() }
                                    val yChip = remember { MutableInteractionSource() }
                                    FilterChip(
                                        selected = !addToYesterday,
                                        onClick = { addToYesterday = false },
                                        label = { Text("Today") },
                                        modifier = Modifier.weight(1f).bounceClick(todayChip),
                                        interactionSource = todayChip,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                                            selectedLabelColor = primaryColor
                                        )
                                    )
                                    FilterChip(
                                        selected = addToYesterday,
                                        onClick = { addToYesterday = true },
                                        label = { Text("Yesterday") },
                                        modifier = Modifier.weight(1f).bounceClick(yChip),
                                        interactionSource = yChip,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                                            selectedLabelColor = primaryColor
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = addInput,
                                    onValueChange = { input -> addInput = input.filter { it.isDigit() }.take(5) },
                                    label = { Text("Steps to add") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                addInput.toLongOrNull()
                                    ?.takeIf { it > 0 }
                                    ?.let {
                                        val date = if (addToYesterday) LocalDate.now().minusDays(1)
                                                   else LocalDate.now()
                                        stepsViewModel.addStepsManually(it, date)
                                    }
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
                                    Icon(getIconForActivity(item.type, item.name), null, tint = accentColor, modifier = Modifier.size(20.dp))
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

            // ── This week's exercises ─────────────────────────────────────────
            item {
                val repsTitleInteractionSource = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "This Week's Exercises",
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
                        icon = Icons.Default.FitnessCenter,
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
        }
    }
}
