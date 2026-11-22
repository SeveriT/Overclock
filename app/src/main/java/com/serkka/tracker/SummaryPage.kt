@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.serkka.tracker

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SummaryPage(
    workouts: List<Workout>,
    bodyWeights: List<BodyWeight>,
    workoutSessions: List<WorkoutSession>,
    stepsViewModel: StepsViewModel,
    primaryColor: Color,
    onNavigateToWeightTracking: () -> Unit,
    onNavigateToSessions: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 16.dp,
    weightCardVisible: Boolean = true,
    onHideWeightCard: () -> Unit = {}
) {
    val context = LocalContext.current

    val lastWeight = remember(bodyWeights) { bodyWeights.maxByOrNull { it.date } }

    val today = LocalDate.now()

    // The one "active day" rule for the whole Summary page (last 7 days, week streak, Last 30 Days):
    // workout entries, plus timer sessions that AREN'T weight training. A weight-training session
    // is the same gym visit as that day's workout entries, so counting it too doubled the day.
    val countedSessions = remember(workoutSessions) {
        workoutSessions.filter { it.type != "WeightTraining" }
    }
    val workoutDates = remember(workouts) {
        workouts.mapTo(HashSet()) { w ->
            Instant.ofEpochMilli(w.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    val activeDates = remember(workoutDates, countedSessions) {
        workoutDates + countedSessions.map { s ->
            Instant.ofEpochMilli(s.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    // Last 7 days row: was there anything that counts that day
    val weeklyStreak = remember(activeDates, today) {
        val startDate = today.minusDays(6)
        (0..6).map { i ->
            val date = startDate.plusDays(i.toLong())
            date to activeDates.contains(date)
        }
    }

    // Activities per day for the badge on the day circle: one for the day's workout entries
    // (rows are per exercise) plus each counted session.
    val weeklyActivityCount = remember(countedSessions, workoutDates, today) {
        val startDate = today.minusDays(6)
        val sessionCounts = countedSessions
            .map { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate() }
            .filter { !it.isBefore(startDate) && !it.isAfter(today) }
            .groupingBy { it }
            .eachCount()
        val workoutDays = workoutDates.filter { !it.isBefore(startDate) && !it.isAfter(today) }
        (sessionCounts.keys + workoutDays).associateWith {
            (sessionCounts[it] ?: 0) + (if (it in workoutDays) 1 else 0)
        }
    }

    // Icon per day: a counted session's type wins, else weight training (workout entries).
    val weeklyActivityType = remember(countedSessions, today) {
        val startDate = today.minusDays(6)
        val map = mutableMapOf<LocalDate, Pair<String, String?>>()
        countedSessions.forEach { s ->
            val d = Instant.ofEpochMilli(s.date).atZone(ZoneId.systemDefault()).toLocalDate()
            if (!d.isBefore(startDate) && !d.isAfter(today)) map.putIfAbsent(d, s.type to s.name)
        }
        map
    }

    // Consecutive week-streak: how many weeks back have at least one active day.
    // If the current week has nothing yet, we still count the streak from last week.
    // streakAtRisk: the streak only counts because of the grace week, i.e. nothing logged yet this
    // week, and it's Friday or later. It ends on Sunday unless something is logged, so the card
    // shows it "at risk".
    val (weekStreak, streakAtRisk) = remember(activeDates, today) {
        val allActiveDates = activeDates
        var weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val thisWeekEmpty = allActiveDates.none { !it.isBefore(weekStart) }
        if (thisWeekEmpty) {
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
        // Only flagged from Friday: earlier in the week an empty week is normal, not a risk yet
        count to (count > 0 && thisWeekEmpty && today.dayOfWeek >= java.time.DayOfWeek.FRIDAY)
    }

    // Last 30 days vs the 30 days before
    val periodComparison = remember(workouts, countedSessions, today) {
        val zone = ZoneId.systemDefault()
        fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        fun isCardio(w: Workout) = isTimeBasedExercise(w.exerciseName)   // Cardio, Stretching…
        fun stats(from: LocalDate, to: LocalDate): PeriodStats {
            val w = workouts.filter { dateOf(it.date) in from..to }
            val strength = w.filterNot { isCardio(it) || isHoldExercise(it.exerciseName) }   // holds: seconds, not reps
            return PeriodStats(
                // Same "active day" rule as the streaks: workout entries + non-weight-training sessions
                gymDays = (w.map { dateOf(it.date) } +
                    countedSessions.map { dateOf(it.date) }.filter { it in from..to }).toSet().size,
                // Mean of rated sets only (RPE is optional); null when nothing was rated
                avgRpe = w.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average(),
                reps = strength.sumOf { if (it.reps > 0) (if (it.sets > 0) it.sets else 1) * it.reps else 0 },
                volumeKg = strength.filter { it.weightUnit == "kg" }.sumOf {
                    (if (it.sets > 0) it.sets else 1) * (if (it.reps > 0) it.reps else 1) * it.weight.toDouble()
                }
            )
        }
        stats(today.minusDays(29), today) to stats(today.minusDays(59), today.minusDays(30))
    }

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
    var showSetStepsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(top = topPadding),
        // No page gutter: panels carry PageGutter themselves, list rows run full-bleed.
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding)
    ) {
            // ── Weekly streak dots ────────────────────────────────────────────
            item {
                FlatSection(
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flame + week-streak counter
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(end = 20.dp)
                        ) {
                            // Living streak: the flame flickers gently (two out-of-step loops so it
                            // never looks mechanical); a 0 streak stays still
                            val flicker = rememberInfiniteTransition(label = "flame")
                            val flameStretch by flicker.animateFloat(
                                initialValue = 0f, targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                label = "flameStretch"
                            )
                            val flameSway by flicker.animateFloat(
                                initialValue = -1f, targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                label = "flameSway"
                            )
                            // At risk: dimmed and still until something is logged this week
                            val alive = weekStreak > 0 && !streakAtRisk
                            val flameTint by animateColorAsState(
                                if (streakAtRisk) primaryColor.copy(alpha = 0.4f) else primaryColor,
                                tween(Motion.MEDIUM), label = "flameTint"
                            )
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_streak_flame),
                                    contentDescription = if (streakAtRisk) "Streak at risk" else null,
                                    tint = flameTint,
                                    modifier = Modifier
                                        .size(width = 34.dp, height = 45.dp)
                                        .graphicsLayer {
                                            if (alive) {
                                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                                scaleY = 1f + 0.025f * flameStretch
                                                scaleX = 1f - 0.012f * flameStretch
                                                rotationZ = 1f * flameSway
                                            }
                                        }
                                )
                                Text(
                                    text = weekStreak.toString(),
                                    color = MaterialTheme.colorScheme.surface,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
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
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        // Badge for days with more than one activity (workout + other session)
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
                                                    fontSize = 9.sp,   // deliberate exception: count badge inside a small day circle
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
                    if (streakAtRisk) {
                        // Grace week: the streak ends on Sunday unless a workout is logged
                        Text(
                            text = "Log a workout by Sunday to keep your $weekStreak-week streak.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        )
                    }
                }
            }


            // ── Steps card ───────────────────────────────────────────────────
            val hasStepData = todaySteps > 0 || weeklySteps.any { it.second > 0 }
            if ((stepsViewModel.isAvailable || hasStepData) && isStepsCardVisible) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = PageGutter, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Steps",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
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
                    FlatSection(
                        modifier = Modifier.fillMaxWidth().animateContentSize()
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
                            // Bar fills up from empty; once the goal is reached, the count gives a
                            // little bounce and a shine sweeps across the full bar
                            val progress = (todaySteps.toFloat() / goalSteps).coerceIn(0f, 1f)
                            val barFill = remember { Animatable(0f) }
                            val pageVisible = LocalPageVisible.current
                            LaunchedEffect(progress, pageVisible) {
                                if (!pageVisible) return@LaunchedEffect
                                barFill.animateTo(progress, tween(Motion.LONG, easing = FastOutSlowInEasing))
                            }
                            // Fill again from empty each time Home is shown
                            LaunchedEffect(pageVisible) { if (!pageVisible) barFill.snapTo(0f) }
                            val goalReached = todaySteps >= goalSteps
                            val celebrate = remember { Animatable(0f) }
                            LaunchedEffect(goalReached, pageVisible) {
                                if (goalReached && pageVisible) {
                                    kotlinx.coroutines.delay(Motion.LONG.toLong())   // after the bar has filled
                                    celebrate.snapTo(0f)
                                    celebrate.animateTo(1f, tween(1100, easing = LinearEasing))
                                } else celebrate.snapTo(0f)
                            }
                            // 0 → 1 → 0 over the first ~third of the celebration
                            val countBounce = kotlin.math.sin((celebrate.value * 3f).coerceAtMost(1f) * Math.PI.toFloat())
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
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.graphicsLayer {
                                                val s = 1f + 0.12f * countBounce
                                                scaleX = s; scaleY = s
                                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "steps today",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    // Extra line for a huge step day (Extras.kt)
                                    if (todaySteps >= 25_000) {
                                        Text(
                                            "Your shoes called. They want a day off.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = primaryColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { barFill.value },
                                        modifier = Modifier.fillMaxWidth(0.75f).height(4.dp).clip(RoundedCornerShape(2.dp))
                                            .drawWithContent {
                                                drawContent()
                                                // Goal shine: a bright band sweeping left to right
                                                if (celebrate.value in 0.001f..0.999f) {
                                                    val band = size.width * 0.35f
                                                    val x = -band + (size.width + band) * celebrate.value
                                                    drawRect(
                                                        Brush.horizontalGradient(
                                                            listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent),
                                                            startX = x, endX = x + band
                                                        )
                                                    )
                                                }
                                            },
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
                                    LaunchedEffect(weeklySteps, pageVisible) {
                                        if (!pageVisible) { stepsBarAnim.snapTo(0f); return@LaunchedEffect }
                                        stepsBarAnim.animateTo(1f, animationSpec = tween(Motion.LONG, easing = FastOutSlowInEasing))
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
                                                    showSetStepsDialog = true
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
                    TrackerDialog(
                        title = "Step goal",
                        onDismiss = { showStepGoalDialog = false },
                        confirmLabel = "Save",
                        onConfirm = {
                            val parsed = goalInput.toLongOrNull()?.coerceAtLeast(100L) ?: 10_000L
                            stepsViewModel.setStepGoal(parsed)
                            showStepGoalDialog = false
                        }
                    ) {
                        OutlinedTextField(
                            value = goalInput,
                            onValueChange = { input -> goalInput = input.filter { it.isDigit() }.take(6) },
                            label = { Text("Daily step goal") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }

            if (showSetStepsDialog) {
                item {
                    // Set a day's total (e.g. the number from a watch or another step app)
                    val last7Days = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
                    var selectedDate by remember { mutableStateOf(today) }
                    val currentForDay = weeklySteps.firstOrNull { it.first == selectedDate }?.second ?: 0L
                    var stepsInput by remember { mutableStateOf(currentForDay.toString()) }
                    val total = stepsInput.toLongOrNull()
                    TrackerDialog(
                        title = "Set steps",
                        onDismiss = { showSetStepsDialog = false },
                        confirmLabel = "Set",
                        confirmEnabled = total != null,
                        onConfirm = {
                            stepsViewModel.setStepsManually(total ?: 0L, selectedDate)
                            showSetStepsDialog = false
                        }
                    ) {
                            Column {
                                Text(
                                    "Day",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
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
                                            onClick = {
                                                selectedDate = d
                                                // Start from that day's current count
                                                stepsInput = (weeklySteps.firstOrNull { it.first == d }?.second ?: 0L).toString()
                                            },
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
                                    value = stepsInput,
                                    onValueChange = { input -> stepsInput = input.filter { it.isDigit() }.take(6) },
                                    label = { Text("Total steps for the day") },
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
                    }
                }
            }

            // ── Latest weight card ────────────────────────────────────────────
            if (weightCardVisible) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = PageGutter, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Latest Weight",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
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

                    FlatSection(
                        onClick = onNavigateToWeightTracking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
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
                                            animationSpec = tween(Motion.LONG, easing = FastOutSlowInEasing)
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

            // ── Last 30 days vs the previous 30 ─────────────────────────────
            item {
                SectionHeader(
                    title = "Last 30 Days",
                    actionLabel = "See all",
                    onAction = onNavigateToSessions
                )
            }
            item(key = "summary_30d_stats") {
                val (current, previous) = periodComparison
                if (current.isEmpty && previous.isEmpty) {
                    EmptyState(
                        icon = Icons.Default.Schedule,
                        message = "No activity in the past 60 days."
                    )
                } else {
                    FlatSection(modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter)) {
                        Text(
                            "Compared with the previous 30 days",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(Modifier.fillMaxWidth()) {
                            PeriodStatCell(
                                icon = Icons.Default.CalendarMonth, label = "Gym days",
                                current = current.gymDays.toDouble(), previous = previous.gymDays.toDouble(),
                                format = { "%d".format(it.toInt()) },
                                primaryColor = primaryColor, modifier = Modifier.weight(1f)
                            )
                            PeriodStatCell(
                                icon = Icons.Default.Speed, label = "Avg RPE",
                                current = current.avgRpe, previous = previous.avgRpe,
                                format = { String.format(Locale.getDefault(), "%.1f", it) },
                                absoluteDelta = true,   // "▲ 0.4" reads better than a % for a 5–10 scale
                                neutralDelta = true,    // harder isn't good or bad, so no green/red
                                primaryColor = primaryColor, modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth()) {
                            PeriodStatCell(
                                icon = Icons.Default.Repeat, label = "Sets × reps",
                                current = current.reps.toDouble(), previous = previous.reps.toDouble(),
                                format = { String.format(Locale.getDefault(), "%,d", it.toInt()) },
                                primaryColor = primaryColor, modifier = Modifier.weight(1f)
                            )
                            PeriodStatCell(
                                icon = Icons.Default.FitnessCenter, label = "Volume",
                                current = current.volumeKg, previous = previous.volumeKg,
                                format = { String.format(Locale.getDefault(), "%,.0f kg", it) },
                                primaryColor = primaryColor, modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

/** Totals for one 30-day window on the Home screen. */
private data class PeriodStats(
    val gymDays: Int,
    val avgRpe: Double?,
    val reps: Int,
    val volumeKg: Double
) {
    val isEmpty get() = gymDays == 0 && reps == 0 && volumeKg == 0.0
}

// ── 30-day stat cell with change vs previous period ───────────────────────────
@Composable
private fun PeriodStatCell(
    icon: ImageVector,
    label: String,
    current: Double?,          // null = no data for this stat (e.g. no rated sets)
    previous: Double?,
    format: (Double) -> String,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    absoluteDelta: Boolean = false,   // show "▲ 0.4" instead of "▲ 12%"
    neutralDelta: Boolean = false     // accent colour instead of green/red
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val up = if (neutralDelta) primaryColor else TrackerColors.Green
    val down = if (neutralDelta) primaryColor else MaterialTheme.colorScheme.error
    val cur = current ?: 0.0
    val prev = previous ?: 0.0
    // Change vs previous 30 days: "New" when there was nothing before, "—" when there's nothing now
    val (deltaText, deltaColor) = when {
        current == null || (cur == 0.0 && prev == 0.0) -> "—" to muted
        previous == null || prev == 0.0 -> "New" to up
        absoluteDelta -> {
            val diff = Math.round((cur - prev) * 10) / 10.0
            when {
                diff > 0 -> "▲ ${String.format(Locale.getDefault(), "%.1f", diff)}" to up
                diff < 0 -> "▼ ${String.format(Locale.getDefault(), "%.1f", -diff)}" to down
                else -> "± 0" to muted
            }
        }
        else -> {
            val pct = ((cur - prev) / prev * 100).roundToInt()
            when {
                pct > 0 -> "▲ $pct%" to up
                pct < 0 -> "▼ ${-pct}%" to down
                else -> "± 0%" to muted
            }
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1)
        }
        Text(
            current?.let(format) ?: "—",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(deltaText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = deltaColor)
            Text(
                "  ·  was ${previous?.let(format) ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

