@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ── Exercise history (opened from Workout Stats) ─────────────────────────────
// Everything logged for one exercise: best estimated 1RM and heaviest set, a trend of the
// best estimated 1RM per training day, then every day's entries (same rows as Workouts).

/**
 * Full-height sheet with [exercise]'s whole history. Time-based and hold exercises skip the
 * 1RM parts and just list their entries.
 */
@Composable
internal fun ExerciseHistorySheet(
    exercise: String,
    workouts: List<Workout>,
    primaryColor: Color,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

    // Newest day first; within a day newest-added first (same as the Workouts list)
    val days: List<Pair<LocalDate, List<Workout>>> = remember(workouts, exercise) {
        workouts.filter { it.exerciseName == exercise }
            .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            .toList()
            .sortedByDescending { it.first }
            .map { (day, list) -> day to list.sortedByDescending { it.id } }
    }
    // Best estimated 1RM per training day, oldest first, for the trend chart
    val trend: List<Pair<LocalDate, Float>> = remember(days) {
        days.mapNotNull { (day, list) -> list.mapNotNull(::estimatedOneRepMax).maxOrNull()?.let { day to it } }
            .reversed()
    }
    val allEntries = remember(days) { days.flatMap { it.second } }
    val bestOneRepMax = trend.maxByOrNull { it.second }
    val heaviest = allEntries.filter { estimatedOneRepMax(it) != null }.maxByOrNull { it.weight }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEEE d.M.yyyy") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = PageGutter, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(exercise, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        days.lastOrNull()?.let { (firstDay, _) ->
                            Text(
                                "${days.size} ${if (days.size == 1) "day" else "days"} · since ${firstDay.format(DateTimeFormatter.ofPattern("d.M.yyyy"))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted
                            )
                        }
                    }
                    TextButton(onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }) { Text("Done", fontWeight = FontWeight.SemiBold) }
                }
            }

            // ── Best numbers + 1RM trend (weighted exercises only) ──────────────
            if (bestOneRepMax != null) {
                item(key = "bests") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 8.dp)) {
                        HistoryStat(
                            label = "Best est. 1RM",
                            value = "${formatWeight((bestOneRepMax.second * 2).roundToInt() / 2f)} kg",
                            caption = bestOneRepMax.first.format(DateTimeFormatter.ofPattern("d.M.yyyy")),
                            color = primaryColor,
                            modifier = Modifier.weight(1f)
                        )
                        heaviest?.let {
                            HistoryStat(
                                label = "Heaviest set",
                                value = "${formatWeight(it.weight)} kg",
                                caption = "${if (it.sets > 0) "${it.sets} × " else ""}${it.reps} reps",
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (trend.size >= 2) {
                    item(key = "trend") {
                        Column(Modifier.padding(horizontal = PageGutter, vertical = 8.dp)) {
                            Text("Estimated 1RM per day", style = MaterialTheme.typography.labelLarge, color = muted)
                            Spacer(Modifier.height(8.dp))
                            OneRepMaxTrendChart(trend, primaryColor)
                        }
                    }
                }
            }

            // ── Every training day ──────────────────────────────────────────────
            days.forEach { (day, entries) ->
                item(key = "day_$day") {
                    Text(
                        day.format(dayFormat).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor,
                        modifier = Modifier.padding(start = PageGutter, end = PageGutter, top = 20.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(entries, key = { _, w -> "entry_${w.id}" }) { i, w ->
                    WorkoutEntryRow(
                        workout = w,
                        trailing = {
                            estimatedOneRepMax(w)?.let {
                                Text(
                                    "≈ ${formatWeight((it * 2).roundToInt() / 2f)} kg 1RM",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = muted
                                )
                            }
                        }
                    )
                    if (i < entries.lastIndex) WorkoutEntryDivider()
                }
            }
        }
    }
}

@Composable
private fun HistoryStat(label: String, value: String, caption: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Line of the best estimated 1RM per training day, spaced by real dates, with min/max labels. */
@Composable
private fun OneRepMaxTrendChart(points: List<Pair<LocalDate, Float>>, color: Color) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val minV = points.minOf { it.second }
    val maxV = points.maxOf { it.second }
    val lo = minV - maxOf(1f, (maxV - minV) * 0.1f)
    val hi = maxV + maxOf(1f, (maxV - minV) * 0.1f)
    val firstDay = points.first().first.toEpochDay()
    val span = (points.last().first.toEpochDay() - firstDay).coerceAtLeast(1L).toFloat()
    val dateFormat = DateTimeFormatter.ofPattern("d.M.yy")

    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 11.sp.toPx()
            setColor(labelColor.toArgb())
        }
        val labelW = 34.dp.toPx()
        val bottomH = 18.dp.toPx()
        val chartW = size.width - labelW
        val chartH = size.height - bottomH
        fun x(day: LocalDate) = labelW + (day.toEpochDay() - firstDay) / span * chartW
        fun y(v: Float) = chartH - (v - lo) / (hi - lo) * chartH

        listOf(maxV, minV).forEach { v ->
            val yy = y(v)
            drawLine(TrackerColors.ChartGrid, Offset(labelW, yy), Offset(size.width, yy), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(formatWeight(v.roundToInt().toFloat()), 0f, yy + paint.textSize / 3f, paint)
        }

        val path = Path()
        points.forEachIndexed { i, (day, v) ->
            if (i == 0) path.moveTo(x(day), y(v)) else path.lineTo(x(day), y(v))
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEach { (day, v) ->
            drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(x(day), y(v)))
            drawCircle(TrackerColors.ChartDot, radius = 1.5.dp.toPx(), center = Offset(x(day), y(v)))
        }

        val baseline = size.height - 2.dp.toPx()
        paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(points.first().first.format(dateFormat), labelW, baseline, paint)
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(points.last().first.format(dateFormat), size.width, baseline, paint)
    }
}
