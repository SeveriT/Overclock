@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.serkka.tracker

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

// ── Weight tracking page ──────────────────────────────────────────────────────

@Composable
fun WeightTrackingPage(
    bodyWeights: List<BodyWeight>,
    primaryColor: Color,
    onWeightClick: (BodyWeight) -> Unit,
    onWeightDelete: (BodyWeight) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp,
    bottomPadding: Dp = 16.dp,
    onChartTouch: (Boolean) -> Unit = {}
) {
    val sortedWeights = remember(bodyWeights) { bodyWeights.sortedBy { it.date } }

    // ── Height preference for BMI ─────────────────────────────────────────────
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context).tracker }
    var heightCm by remember { mutableStateOf(prefs.getFloat("height_cm", 0f)) }
    var showHeightDialog by remember { mutableStateOf(false) }

    // ── Chart Y-axis range override (0 = auto) ────────────────────────────────
    var chartMinWeight by remember { mutableStateOf(prefs.getFloat("chart_weight_min", 0f)) }
    var chartMaxWeight by remember { mutableStateOf(prefs.getFloat("chart_weight_max", 0f)) }
    var showAxisRangeDialog by remember { mutableStateOf(false) }

    // ── Prediction target date (persisted) ────────────────────────────────────
    val msPerDay = 1000L * 60 * 60 * 24
    val defaultPredictionMillis = remember(sortedWeights) {
        val baseDate = sortedWeights.lastOrNull()?.date ?: System.currentTimeMillis()
        baseDate + 30L * msPerDay
    }
    var predictionTargetMillis by remember(defaultPredictionMillis) {
        val saved = prefs.getLong("weight_prediction_target_millis", 0L)
        val lastDate = sortedWeights.lastOrNull()?.date ?: 0L
        mutableStateOf(if (saved > lastDate) saved else defaultPredictionMillis)
    }
    var showPredictionDatePicker by remember { mutableStateOf(false) }

    val prediction = remember(sortedWeights, predictionTargetMillis) {
        if (sortedWeights.size < 2) null
        else {
            // Only use entries from the last ~3 months (relative to the newest entry);
            // older history is ignored so the trend reflects current trajectory.
            val cutoffMillis = sortedWeights.last().date - 30L * msPerDay
            val recent = sortedWeights.filter { it.date >= cutoffMillis }
            if (recent.size < 2) null
            else {
                val last = recent.last()
                val first = recent.first()
                val spanDays = (last.date - first.date) / msPerDay.toDouble()
                if (spanDays < 1) null
                else {
                // Recency-weighted linear regression over the last-45-days entries.
                //  • Linear fade from 1.0 (today) to 0.2 at 30 days old.
                //  • Older than 30 days: flat 0.2, so earlier entries in the window keep
                //    a steady say instead of fading toward zero.
                //  • Fitting the slope across many points — and starting the projection
                //    from the regression's fitted "current" value instead of the raw
                //    last reading — means one outlier weigh-in can't throw it off.
                val fadeDays = 30.0        // age at which the weight bottoms out
                val floorWeight = 0.2      // weight at fadeDays and beyond
                val anchor = last.date          // x = days relative to newest entry (0 = newest)
                var sumW = 0.0; var sumWX = 0.0; var sumWY = 0.0
                var sumWXX = 0.0; var sumWXY = 0.0
                for (entry in recent) {
                    val ageDays = (anchor - entry.date) / msPerDay.toDouble()   // ≥ 0
                    val w = if (ageDays < fadeDays) 1.0 - (1.0 - floorWeight) * (ageDays / fadeDays)
                            else floorWeight
                    val x = -ageDays            // newest = 0, older = negative, future = positive
                    val y = entry.weight.toDouble()
                    sumW += w; sumWX += w * x; sumWY += w * y
                    sumWXX += w * x * x; sumWXY += w * x * y
                }
                val denom = sumW * sumWXX - sumWX * sumWX
                val ratePerDay: Double
                val fittedNow: Double
                if (kotlin.math.abs(denom) < 1e-9) {
                    // Degenerate (e.g. all points effectively same date) → simple slope.
                    ratePerDay = (last.weight - first.weight) / spanDays
                    fittedNow = last.weight.toDouble()
                } else {
                    ratePerDay = (sumW * sumWXY - sumWX * sumWY) / denom      // kg/day
                    fittedNow = (sumWY - ratePerDay * sumWX) / sumW           // fitted weight at x = 0
                }
                val targetDays = ((predictionTargetMillis - last.date) / msPerDay.toDouble()).coerceAtLeast(1.0)
                Triple((fittedNow + ratePerDay * targetDays).toFloat(), ratePerDay * 7, targetDays.toInt())
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 6.dp, bottom = bottomPadding)
    ) {
        if (sortedWeights.isNotEmpty()) {
            item {
                FlatSection(
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                        .padding(horizontal = PageGutter, vertical = 8.dp)
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            // ── Left: weight + BMI ───────────────────────────
                            Column {
                                Text(
                                    "Current Weight",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${formatWeight(sortedWeights.last().weight)} kg",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (heightCm > 0f) {
                                    val rpi = heightCm / Math.cbrt(sortedWeights.last().weight.toDouble()).toFloat()
                                    val rpiCategory = when {
                                        rpi < 38f  -> "Heavy"
                                        rpi < 41f  -> "Stocky"
                                        rpi < 44f  -> "Average"
                                        rpi < 46f  -> "Lean"
                                        else       -> "Slender"
                                    }
                                    val rpiColor = when {
                                        rpi < 38f  -> Color(0xFFFF7043)
                                        rpi < 41f  -> Color(0xFFECFE72)
                                        rpi < 44f  -> Color(0xFF4AC067)
                                        rpi < 46f  -> Color(0xFF6693EB)
                                        else       -> Color(0xFF6693EB)
                                    }
                                    Text(
                                        "RPI ${String.format(Locale.getDefault(), "%.1f", rpi)} · $rpiCategory",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = rpiColor,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { showHeightDialog = true }
                                    )
                                    val heightM = heightCm / 100f
                                    val bmi = sortedWeights.last().weight / (heightM * heightM)
                                    val bmiCategory = when {
                                        bmi < 18.5f -> "Underweight"
                                        bmi < 25f   -> "Normal"
                                        bmi < 30f   -> "Overweight"
                                        else        -> "Obese"
                                    }
                                    Text(
                                        "BMI ${String.format(Locale.getDefault(), "%.1f", bmi)} · $bmiCategory",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { showHeightDialog = true }
                                    )
                                } else {
                                    val bmiInteractionSource = remember { MutableInteractionSource() }
                                    TextButton(
                                        onClick = { showHeightDialog = true },
                                        interactionSource = bmiInteractionSource,
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp).bounceClick(bmiInteractionSource)
                                    ) {
                                        Text(
                                            "Set height for RPI",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // ── Right: trend + prediction (target date selectable) ──
                            prediction?.let { (pred, rate, days) ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "Trend",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val sign = if (rate >= 0) "+" else ""
                                    Text(
                                        "$sign${String.format(Locale.getDefault(), "%.2f", rate)} kg/week",
                                        color = if (rate <= 0) Color(0xFF46CE46).copy(alpha = 0.8f) else Color(0xFFEE3E3E).copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val labelFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
                                    Text(
                                        "${labelFormat.format(Date(predictionTargetMillis))} Prediction",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { showPredictionDatePicker = true }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${String.format(Locale.getDefault(), "%.1f", pred)} kg",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (pred > sortedWeights.last().weight) Color(0xFFEE3E3E).copy(alpha = 0.8f) else Color(0xFF46CE46).copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { showPredictionDatePicker = true }
                                    )
                                    Text(
                                        "in $days ${if (days == 1) "day" else "days"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        WeightChart(
                            weights = sortedWeights,
                            color = primaryColor,
                            predictionDateMillis = prediction?.let { predictionTargetMillis },
                            predictionWeight = prediction?.first,
                            minWeightOverride = chartMinWeight.takeIf { it > 0f },
                            maxWeightOverride = chartMaxWeight.takeIf { it > 0f },
                            onHorizontalTouch = onChartTouch,
                            onAxisClick = { showAxisRangeDialog = true }
                        )
                    }
                }
            }

            val groupedByMonth = sortedWeights.reversed().groupBy { entry ->
                val cal = Calendar.getInstance().apply { timeInMillis = entry.date }
                val month = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
                month
            }

            groupedByMonth.forEach { (month, entries) ->
                stickyHeader {
                    val bgColor = MaterialTheme.colorScheme.background
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to bgColor,
                                        0.5f to bgColor,
                                        1.0f to Color.Transparent
                                    )
                                )
                            ),
                        color = Color.Transparent
                    ) {
                        Text(
                            text = month,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = PageGutter, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                itemsIndexed(entries, key = { _, it -> it.id }) { index, weightEntry ->
                    Column(Modifier.fillMaxWidth().animateContentSize().animateItem()) {
                        ListItem(
                            modifier = Modifier.clickable { onWeightClick(weightEntry) },
                            colors = flatListItemColors(),
                            headlineContent = {
                                Text(
                                    "${formatWeight(weightEntry.weight)} kg",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            supportingContent = {
                                Column {
                                    Text(formatDate(weightEntry.date))
                                    if (weightEntry.notes.isNotEmpty()) {
                                        Text(
                                            weightEntry.notes,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { onWeightDelete(weightEntry) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        )
                        if (index < entries.lastIndex) ListRowDivider(inset = PageGutter)
                    }
                }
            }
        } else {
            item {
                EmptyState(
                    icon = Icons.Default.MonitorWeight,
                    message = "No weight entries yet.\nAdd your first to see progress.",
                    primaryColor = primaryColor
                )
            }
        }
    }

    if (showPredictionDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = predictionTargetMillis,
            selectableDates = object : SelectableDates {
                val lastEntry = sortedWeights.lastOrNull()?.date ?: 0L
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis > lastEntry
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPredictionDatePicker = false },
            confirmButton = {
                val okInteraction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            predictionTargetMillis = it
                            prefs.edit().putLong("weight_prediction_target_millis", it).apply()
                        }
                        showPredictionDatePicker = false
                    },
                    interactionSource = okInteraction,
                    modifier = Modifier.bounceClick(okInteraction)
                ) { Text("OK") }
            },
            dismissButton = {
                val cancelInteraction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showPredictionDatePicker = false },
                    interactionSource = cancelInteraction,
                    modifier = Modifier.bounceClick(cancelInteraction)
                ) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showAxisRangeDialog) {
        var minInput by remember { mutableStateOf(if (chartMinWeight > 0f) formatWeight(chartMinWeight) else "") }
        var maxInput by remember { mutableStateOf(if (chartMaxWeight > 0f) formatWeight(chartMaxWeight) else "") }
        val minVal = minInput.toLeadFloat()
        val maxVal = maxInput.toLeadFloat()
        val rangeValid = minInput.isBlank() || maxInput.isBlank() ||
            (minVal != null && maxVal != null && minVal < maxVal)
        TrackerDialog(
            title = "Chart weight range",
            onDismiss = { showAxisRangeDialog = false },
            confirmLabel = "Save",
            confirmEnabled = rangeValid,
            onConfirm = {
                val newMin = minInput.toLeadFloat()?.takeIf { it > 0f } ?: 0f
                val newMax = maxInput.toLeadFloat()?.takeIf { it > 0f } ?: 0f
                chartMinWeight = newMin
                chartMaxWeight = newMax
                prefs.edit()
                    .putFloat("chart_weight_min", newMin)
                    .putFloat("chart_weight_max", newMax)
                    .apply()
                showAxisRangeDialog = false
            },
            neutralLabel = "Auto",
            onNeutral = {
                chartMinWeight = 0f
                chartMaxWeight = 0f
                prefs.edit().remove("chart_weight_min").remove("chart_weight_max").apply()
                showAxisRangeDialog = false
            }
        ) {
                    Text(
                        "Set the min and max weight shown on the chart's vertical axis. Leave a field blank for automatic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = minInput,
                            onValueChange = { v -> minInput = v.filter { it.isDigit() || it == '.' }.take(6) },
                            label = { Text("Min (kg)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = maxInput,
                            onValueChange = { v -> maxInput = v.filter { it.isDigit() || it == '.' }.take(6) },
                            label = { Text("Max (kg)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    if (!rangeValid) {
                        Text(
                            "Min must be less than max.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
        }
    }

    if (showHeightDialog) {
        var heightInput by remember {
            mutableStateOf(if (heightCm > 0f) heightCm.toInt().toString() else "")
        }
        val heightValid = heightInput.toIntOrNull()?.let { it in 100..250 } == true
        TrackerDialog(
            title = "Set height",
            onDismiss = { showHeightDialog = false },
            confirmLabel = "Save",
            confirmEnabled = heightValid,
            onConfirm = {
                val h = heightInput.toInt()
                prefs.edit().putFloat("height_cm", h.toFloat()).apply()
                heightCm = h.toFloat()
                showHeightDialog = false
            }
        ) {
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { v ->
                            if (v.length <= 3 && v.all { it.isDigit() }) heightInput = v
                        },
                        label = { Text("Height (cm)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            if (heightInput.toIntOrNull()?.let { it !in 100..250 } == true) {
                                Text("Enter a value between 100–250 cm")
                            }
                        }
                    )
                    Text(
                        "RPI (Reciprocal Ponderal Index)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Height ÷ ³√Weight. More accurate than BMI for muscular builds because it scales weight by cube root instead of squaring height.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "< 38 Heavy · 38–41 Stocky · 41–44 Average · 44–46 Lean · > 46 Slender",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        "BMI (Body Mass Index)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Weight ÷ Height². Widely used but doesn't distinguish muscle from fat. Can overestimate body fat in athletic individuals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "< 18.5 Underweight · 18.5–25 Normal · 25–30 Overweight · > 30 Obese",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
        }
    }
}

// ── Weight chart ──────────────────────────────────────────────────────────────

@Composable
fun WeightChart(
    weights: List<BodyWeight>,
    color: Color,
    predictionDateMillis: Long? = null,
    predictionWeight: Float? = null,
    minWeightOverride: Float? = null,
    maxWeightOverride: Float? = null,
    onHorizontalTouch: (Boolean) -> Unit = {},
    onAxisClick: () -> Unit = {}
) {
    if (weights.isEmpty()) return

    val gridLineColor  = Color(0xFF424349)
    val labelColor     = Color.White
    val gridLineStroke = 1.dp
    val yLabelCount    = 4
    val visibleDaysMs  = 30L * 24 * 60 * 60 * 1000  // 30 days in ms

    // Auto range folds the prediction point in so the projected line fits on-screen.
    // A user-set min/max override (from tapping the axis) wins over the auto value.
    val autoMinBase = minOf(weights.minOf { it.weight }, predictionWeight ?: Float.MAX_VALUE)
    val autoMaxBase = maxOf(weights.maxOf { it.weight }, predictionWeight ?: -Float.MAX_VALUE)
    val padding = maxOf(1f, (autoMaxBase - autoMinBase) * 0.15f)
    val minWeight  = minWeightOverride ?: (autoMinBase - padding)
    val maxWeight  = maxWeightOverride ?: (autoMaxBase + padding)
    val weightRange = maxOf(1f, maxWeight - minWeight)

    val minDate  = weights.first().date
    val maxDate  = maxOf(weights.last().date, predictionDateMillis ?: weights.last().date)
    val dateRange = maxOf(1L, maxDate - minDate)

    // Calculate how wide the canvas needs to be relative to the 30-day viewport
    val totalPages = if (dateRange > visibleDaysMs) dateRange.toFloat() / visibleDaysMs else 1f

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(weights) {
        animationProgress.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
    }

    // Open with the latest real weigh-in at the right edge of the viewport. The forecast
    // (which extends maxDate past the last entry) stays off-screen until you scroll right.
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    // Single source for the canvas width — used by both the scroll math and the Canvas.
    val canvasWidth = (300.dp * totalPages).coerceAtLeast(300.dp)
    LaunchedEffect(weights, maxDate) {
        // maxValue is Int.MAX_VALUE until the scroll container has been measured.
        val maxScroll = snapshotFlow { scrollState.maxValue }.first { it != Int.MAX_VALUE }
        if (maxScroll <= 0) return@LaunchedEffect          // everything fits; nothing to scroll
        with(density) {
            val canvasPx = canvasWidth.toPx()
            val viewportPx = canvasPx - maxScroll
            val dotRadiusPx = 4.dp.toPx()                     // matches the Canvas's chartLeft inset
            val usablePx = canvasPx - 2 * dotRadiusPx
            val lastX = dotRadiusPx +
                ((weights.last().date - minDate).toFloat() / dateRange.toFloat()) * usablePx
            val rightMargin = 16.dp.toPx()                    // keep the last dot off the edge
            val target = (lastX + rightMargin - viewportPx).roundToInt().coerceIn(0, maxScroll)
            scrollState.scrollTo(target)
        }
    }

    // Swallow any leftover horizontal scroll/fling so swiping the chart never
    // bubbles up to the screen pager and flips to another page.
    val consumeHorizontalSwipe = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                Offset(available.x, 0f)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }

    // X-axis labels: ISO week numbers ("W42"), one per week, each placed on that week's
    // Monday so the gridlines mark week boundaries.
    val xLabels: List<Pair<Float, String>> = remember(weights, maxDate, dateRange) {
        if (weights.size < 2) return@remember emptyList()
        val zone = java.time.ZoneId.systemDefault()
        val weekOfYear = java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()
        var monday = java.time.Instant.ofEpochMilli(minDate).atZone(zone).toLocalDate()
            .with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY))
        val labels = mutableListOf<Pair<Float, String>>()
        while (true) {
            val mondayMs = monday.atStartOfDay(zone).toInstant().toEpochMilli()
            if (mondayMs > maxDate) break
            val ratio = (mondayMs - minDate).toFloat() / dateRange.toFloat()
            labels.add(ratio to "W${monday.get(weekOfYear)}")
            monday = monday.plusWeeks(1)
        }
        labels
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(vertical = 8.dp)
            // While a finger is down on the chart, tell the host to freeze the pager so
            // swiping the chart can never flip to another screen.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onHorizontalTouch(true)
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    onHorizontalTouch(false)
                }
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Fixed Y-axis labels on the left — tap to configure the min/max range.
            Canvas(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .clickable { onAxisClick() }
            ) {
                val xLabelHeightPx = 22.dp.toPx()
                val chartBottom = size.height - xLabelHeightPx
                val chartHeight = chartBottom
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 12.sp.toPx()
                    setColor(labelColor.toArgb())
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                for (i in 0..yLabelCount) {
                    val fraction = i.toFloat() / yLabelCount
                    val yVal = minWeight + fraction * weightRange
                    val yPx = chartBottom - fraction * chartHeight
                    val label = if (yVal % 1 == 0f) yVal.toInt().toString()
                    else String.format("%.1f", yVal)
                    drawContext.canvas.nativeCanvas.drawText(
                        label, 0f, yPx + textPaint.textSize / 3f, textPaint
                    )
                }
            }

            // Scrollable chart area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .nestedScroll(consumeHorizontalSwipe)
                    .horizontalScroll(scrollState)
            ) {
                Canvas(
                    modifier = Modifier
                        // Minimum width = parent width, scales up for longer date ranges
                        .width(canvasWidth)
                        .fillMaxHeight()
                ) {
                    val xLabelHeightPx = 20.dp.toPx()
                    val chartBottom = size.height - xLabelHeightPx
                    val chartHeight = chartBottom

                    val textPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = 12.sp.toPx()
                        setColor(labelColor.toArgb())
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    // ── Horizontal grid lines ─────────────────────────────────
                    for (i in 0..yLabelCount) {
                        val fraction = i.toFloat() / yLabelCount
                        val yPx = chartBottom - fraction * chartHeight
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, yPx),
                            end = Offset(size.width, yPx),
                            strokeWidth = gridLineStroke.toPx()
                        )
                    }

                    // ── X-axis date labels ────────────────────────────────────
                    xLabels.forEach { (ratio, label) ->
                        val x = ratio * size.width
                        drawContext.canvas.nativeCanvas.drawText(
                            label, x, size.height, textPaint
                        )
                        drawLine(
                            color = gridLineColor.copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, chartBottom),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }

                    // ── Map data to canvas points ─────────────────────────────
                    val dotRadius = 4.dp.toPx()
                    val chartRight = size.width - dotRadius
                    val chartLeft = dotRadius
                    val usableWidth = chartRight - chartLeft
                    val points = weights.map { w ->
                        val x = chartLeft + ((w.date - minDate).toFloat() / dateRange.toFloat()) * usableWidth
                        val y = chartBottom - ((w.weight - minWeight) / weightRange) * chartHeight
                        Offset(x, y)
                    }

                    // ── Monotone cubic spline (no overshoot) ────────────────
                    fun Path.smoothCurveTo(pts: List<Offset>) {
                        if (pts.size < 2) return
                        moveTo(pts.first().x, pts.first().y)
                        if (pts.size == 2) { lineTo(pts[1].x, pts[1].y); return }

                        // Compute tangents with Fritsch-Carlson monotone method
                        val n = pts.size
                        val dx = FloatArray(n - 1) { pts[it + 1].x - pts[it].x }
                        val dy = FloatArray(n - 1) { pts[it + 1].y - pts[it].y }
                        val slope = FloatArray(n - 1) { if (dx[it] != 0f) dy[it] / dx[it] else 0f }
                        val tangent = FloatArray(n)

                        tangent[0] = slope[0] * 0.5f
                        tangent[n - 1] = slope[n - 2] * 0.5f
                        for (i in 1 until n - 1) {
                            if (slope[i - 1] * slope[i] <= 0f) {
                                tangent[i] = 0f
                            } else {
                                tangent[i] = (slope[i - 1] + slope[i]) / 2f
                            }
                        }

                        // Clamp tangents to ensure monotonicity
                        for (i in 0 until n - 1) {
                            if (slope[i] == 0f) {
                                tangent[i] = 0f
                                tangent[i + 1] = 0f
                            } else {
                                val alpha = tangent[i] / slope[i]
                                val beta = tangent[i + 1] / slope[i]
                                val mag = alpha * alpha + beta * beta
                                if (mag > 9f) {
                                    val tau = 3f / kotlin.math.sqrt(mag)
                                    tangent[i] = tau * alpha * slope[i]
                                    tangent[i + 1] = tau * beta * slope[i]
                                }
                            }
                        }

                        for (i in 0 until n - 1) {
                            val seg = dx[i] / 3f
                            cubicTo(
                                pts[i].x + seg, pts[i].y + tangent[i] * seg,
                                pts[i + 1].x - seg, pts[i + 1].y - tangent[i + 1] * seg,
                                pts[i + 1].x, pts[i + 1].y
                            )
                        }
                    }

                    // ── Fill gradient ─────────────────────────────────────────
                    if (points.size > 1) {
                        val fillPath = Path().apply {
                            smoothCurveTo(points)
                            lineTo(points.last().x, chartBottom)
                            lineTo(points.first().x, chartBottom)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.25f * animationProgress.value), Color.Transparent),
                                startY = 0f,
                                endY = chartBottom
                            )
                        )

                        // ── Line ──────────────────────────────────────────────
                        val linePath = Path().apply { smoothCurveTo(points) }
                        drawPath(
                            path = linePath,
                            color = color,
                            style = Stroke(width = 2.5.dp.toPx()),
                            alpha = animationProgress.value
                        )
                    }

                    // ── Data point dots ───────────────────────────────────────
                    points.forEach { pt ->
                        drawCircle(color = color, radius = 4.dp.toPx() * animationProgress.value, center = pt)
                        drawCircle(color = Color(0xFF24252B), radius = 2.dp.toPx() * animationProgress.value, center = pt)
                    }

                    // ── Prediction projection (dashed line to the forecast point) ──
                    if (predictionDateMillis != null && predictionWeight != null && points.isNotEmpty()) {
                        val predX = chartLeft + ((predictionDateMillis - minDate).toFloat() / dateRange.toFloat()) * usableWidth
                        val predY = chartBottom - ((predictionWeight - minWeight) / weightRange) * chartHeight
                        val start = points.last()
                        val end = Offset(predX, predY)
                        // Grow the dashed line out from the last real point as the chart animates in.
                        val animEnd = Offset(
                            start.x + (end.x - start.x) * animationProgress.value,
                            start.y + (end.y - start.y) * animationProgress.value
                        )
                        drawLine(
                            color = color.copy(alpha = 0.7f),
                            start = start,
                            end = animEnd,
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                        // Hollow dot marks the forecast value (distinct from solid data dots).
                        drawCircle(
                            color = color,
                            radius = 4.dp.toPx() * animationProgress.value,
                            center = end,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        if (animationProgress.value > 0.6f) {
                            val predLabelPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                textSize = 12.sp.toPx()
                                setColor(color.toArgb())
                                textAlign = android.graphics.Paint.Align.RIGHT
                                isFakeBoldText = true
                            }
                            val labelY = kotlin.math.max(predY - 8.dp.toPx(), predLabelPaint.textSize)
                            drawContext.canvas.nativeCanvas.drawText(
                                String.format(Locale.getDefault(), "%.1f", predictionWeight),
                                end.x, labelY, predLabelPaint
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Body weight form (bottom sheet) ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightDialog(
    bodyWeight: BodyWeight? = null,
    initialWeight: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Float, Long, String) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var weight by remember(bodyWeight, initialWeight) {
        mutableStateOf(bodyWeight?.weight?.let { formatWeight(it) } ?: initialWeight)
    }
    var notes by remember { mutableStateOf(bodyWeight?.notes ?: "") }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = bodyWeight?.date ?: System.currentTimeMillis()
    )
    val parsedWeight = weight.toLeadFloat() ?: 0f

    FormSheet(
        title = if (bodyWeight == null) "Add Weight" else "Edit Weight",
        primaryColor = primaryColor,
        onDismiss = onDismiss,
        confirmEnabled = parsedWeight > 0f,
        onConfirm = {
            onConfirm(parsedWeight, datePickerState.selectedDateMillis ?: System.currentTimeMillis(), notes)
        }
    ) {
        NumericInput(
            value = weight,
            onValueChange = { weight = it },
            label = "Weight (kg)",
            modifier = Modifier.fillMaxWidth(),
            step = 1f,                 // −/+ buttons: 1 kg jumps
            allowDecimal = true
        )
        // Fine adjustment: ±1 kg around the value in 0.1 kg steps
        StepSlider(
            value = weight,
            onValueChange = { weight = it },
            step = 0.1f,
            span = 2f,
            primaryColor = primaryColor
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        SheetDateRow(state = datePickerState, primaryColor = primaryColor)
    }
}
