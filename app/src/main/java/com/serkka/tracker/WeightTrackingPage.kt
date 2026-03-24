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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    topPadding: Dp
) {
    val sortedWeights = remember(bodyWeights) { bodyWeights.sortedBy { it.date } }

    val prediction = remember(sortedWeights) {
        if (sortedWeights.size < 2) null
        else {
            val last = sortedWeights.last()
            val first = sortedWeights.first()
            val daysDiff = (last.date - first.date) / (1000 * 60 * 60 * 24).toDouble()
            if (daysDiff < 1) null
            else {
                val ratePerDay = (last.weight - first.weight) / daysDiff
                Pair(last.weight + (ratePerDay * 30), ratePerDay * 7)
            }
        }
    }

    // ── Height preference for BMI ─────────────────────────────────────────────
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context).tracker }
    var heightCm by remember { mutableStateOf(prefs.getFloat("height_cm", 0f)) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 6.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        if (sortedWeights.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                    val heightM = heightCm / 100f
                                    val bmi = sortedWeights.last().weight / (heightM * heightM)
                                    val bmiCategory = when {
                                        bmi < 18.5f -> "Underweight"
                                        bmi < 25f   -> "Normal"
                                        bmi < 30f   -> "Overweight"
                                        else        -> "Obese"
                                    }
                                    val bmiColor = when {
                                        bmi < 18.5f -> Color(0xFF6693EB)
                                        bmi < 25f   -> Color(0xFF4AC067)
                                        bmi < 30f   -> Color(0xFFECFE72)
                                        else        -> Color(0xFFFF7043)
                                    }
                                    Text(
                                        "BMI ${String.format(Locale.getDefault(), "%.1f", bmi)} · $bmiCategory",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = bmiColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    val bmiInteractionSource = remember { MutableInteractionSource() }
                                    TextButton(
                                        onClick = { /* handled below via heightCm dialog */ },
                                        interactionSource = bmiInteractionSource,
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp).bounceClick(bmiInteractionSource)
                                    ) {
                                        Text(
                                            "Set height for BMI",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // ── Right: trend + 30-day prediction ────────────
                            prediction?.let { (pred, rate) ->
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
                                    Text(
                                        "30-Day Prediction",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${String.format(Locale.getDefault(), "%.1f", pred)} kg",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (pred > sortedWeights.last().weight) Color(0xFFEE3E3E).copy(alpha = 0.8f) else Color(0xFF46CE46).copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                } //color = if (pred <= 0) Color(0xFF46CE46).copy(alpha = 0.8f) else Color(0xFFEE3E3E).copy(alpha = 0.8f),
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        WeightChart(weights = sortedWeights, color = primaryColor)
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
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = primaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(entries, key = { it.id }) { weightEntry ->
                    val interactionSource = remember { MutableInteractionSource() }
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .animateContentSize()
                            .animateItem()
                            .bounceClick(
                                interactionSource = interactionSource,
                                onClick = { onWeightClick(weightEntry) }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 8.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            ) {
                                Text(
                                    formatDate(weightEntry.date),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    "${formatWeight(weightEntry.weight)} kg",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                                if (weightEntry.notes.isNotEmpty()) {
                                    Text(
                                        weightEntry.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            val deleteInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = { onWeightDelete(weightEntry) },
                                interactionSource = deleteInteractionSource,
                                modifier = Modifier.bounceClick(deleteInteractionSource)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "Add your first weight entry to see progress!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(155.dp)) }
    }
}

// ── Weight chart ──────────────────────────────────────────────────────────────

@Composable
fun WeightChart(weights: List<BodyWeight>, color: Color) {
    if (weights.isEmpty()) return

    val gridLineColor  = Color(0xFF424349)
    val labelColor     = Color(0xFF9E9EA8)
    val gridLineStroke = 1.dp
    val yLabelCount    = 4
    val visibleDaysMs  = 30L * 24 * 60 * 60 * 1000  // 30 days in ms

    val rawMin = weights.minOf { it.weight }
    val rawMax = weights.maxOf { it.weight }
    val padding = maxOf(1f, (rawMax - rawMin) * 0.15f)
    val minWeight  = rawMin - padding
    val maxWeight  = rawMax + padding
    val weightRange = maxOf(1f, maxWeight - minWeight)

    val minDate  = weights.first().date
    val maxDate  = weights.last().date
    val dateRange = maxOf(1L, maxDate - minDate)

    // Calculate how wide the canvas needs to be relative to the 30-day viewport
    val totalPages = if (dateRange > visibleDaysMs) dateRange.toFloat() / visibleDaysMs else 1f

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(weights) {
        animationProgress.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
    }

    // Scroll to end (most recent data) on first load
    val scrollState = rememberScrollState()
    LaunchedEffect(weights) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    // X-axis labels: one per ~7 days
    val xLabels: List<Pair<Float, String>> = remember(weights) {
        if (weights.size < 2) return@remember emptyList()
        val stepMs = 7L * 24 * 60 * 60 * 1000 // 7 days
        val labels = mutableListOf<Pair<Float, String>>()
        var nextLabel = minDate
        while (nextLabel <= maxDate) {
            val ratio = (nextLabel - minDate).toFloat() / dateRange.toFloat()
            labels.add(Pair(ratio, formatChartDate(nextLabel)))
            nextLabel += stepMs
        }
        labels
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Fixed Y-axis labels on the left
            Canvas(modifier = Modifier.width(30.dp).fillMaxHeight()) {
                val xLabelHeightPx = 20.dp.toPx()
                val chartBottom = size.height - xLabelHeightPx
                val chartHeight = chartBottom
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 10.sp.toPx()
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
                    .horizontalScroll(scrollState)
            ) {
                Canvas(
                    modifier = Modifier
                        .width(with(androidx.compose.ui.platform.LocalDensity.current) {
                            // Minimum width = parent width, scales up for longer date ranges
                            (300.dp * totalPages).coerceAtLeast(300.dp)
                        })
                        .fillMaxHeight()
                ) {
                    val xLabelHeightPx = 20.dp.toPx()
                    val chartBottom = size.height - xLabelHeightPx
                    val chartHeight = chartBottom

                    val textPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = 10.sp.toPx()
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
                }
            }
        }
    }
}

// ── Body weight dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightDialog(
    bodyWeight: BodyWeight? = null,
    initialWeight: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Float, Long, String) -> Unit
) {
    var weight by remember(bodyWeight, initialWeight) {
        mutableStateOf(bodyWeight?.weight?.let { formatWeight(it) } ?: initialWeight)
    }
    var notes by remember { mutableStateOf(bodyWeight?.notes ?: "") }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = bodyWeight?.date ?: System.currentTimeMillis()
    )
    var showDatePicker by remember { mutableStateOf(false) }

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
            }
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bodyWeight == null) "Add Weight" else "Edit Weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumericInput(
                    value = weight,
                    onValueChange = { weight = it },
                    label = "Weight (kg)",
                    modifier = Modifier.fillMaxWidth(),
                    step = 0.1f
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = formatDate(datePickerState.selectedDateMillis ?: System.currentTimeMillis()),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = {
                        val dateInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { showDatePicker = true },
                            interactionSource = dateInteractionSource,
                            modifier = Modifier.bounceClick(dateInteractionSource)
                        ) {
                            Icon(Icons.Default.DateRange, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val cancelInteractionSource = remember { MutableInteractionSource() }
            val saveInteractionSource = remember { MutableInteractionSource() }
            Row {
                TextButton(
                    onClick = onDismiss,
                    interactionSource = cancelInteractionSource,
                    modifier = Modifier.bounceClick(cancelInteractionSource)
                ) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val w = weight.toLeadFloat() ?: 0f
                        if (w > 0) onConfirm(w, datePickerState.selectedDateMillis ?: System.currentTimeMillis(), notes)
                    },
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                    interactionSource = saveInteractionSource,
                    modifier = Modifier.bounceClick(saveInteractionSource)
                ) { Text("Save") }
            }
        },
        dismissButton = null
    )
}
