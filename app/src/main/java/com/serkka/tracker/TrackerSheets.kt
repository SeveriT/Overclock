@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ── Form sheets ───────────────────────────────────────────────────────────────
// Add/edit forms open as modal bottom sheets instead of floating dialogs: they slide
// up from the bottom edge, keep the fields in thumb reach and resize with the keyboard.
// Small yes/no questions stay as AlertDialogs.

/**
 * Bottom-sheet frame for an add/edit form: title, scrollable [content], and a
 * Cancel / confirm button pair. Cancel and confirm animate the sheet away before
 * calling back, so the parent can simply drop the composable afterwards.
 */
@Composable
internal fun FormSheet(
    title: String,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Save",
    confirmEnabled: Boolean = true,
    dismissLabel: String = "Cancel",
    onDelete: (() -> Unit)? = null,   // shows a delete icon next to the title (edit mode)
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PageGutter)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (onDelete != null) {
                    IconButton(onClick = { closeThen(onDelete) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val cancelInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = { closeThen(onDismiss) },
                    interactionSource = cancelInteraction,
                    modifier = Modifier.weight(1f).height(48.dp).bounceClick(cancelInteraction)
                ) { Text(dismissLabel) }
                val confirmInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = { closeThen(onConfirm) },
                    enabled = confirmEnabled,
                    interactionSource = confirmInteraction,
                    modifier = Modifier.weight(1f).height(48.dp).bounceClick(confirmInteraction),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) { Text(confirmLabel, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ── Small dialogs ─────────────────────────────────────────────────────────────

/**
 * The one look for short questions and quick single-value edits (forms use [FormSheet]):
 * plain title, body, text buttons on the right. The confirm button is bold in the accent
 * color, or red when [destructive]. [neutralLabel] adds a third button left of Cancel
 * (e.g. "Auto"); a null [dismissLabel] leaves only the confirm button.
 */
@Composable
internal fun TrackerDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissLabel: String? = "Cancel",
    icon: ImageVector? = null,
    neutralLabel: String? = null,
    onNeutral: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, contentDescription = null, tint = accent) } },
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        text = {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    confirmLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = if (confirmEnabled) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        },
        dismissButton = if (dismissLabel == null && neutralLabel == null) null else {
            {
                Row {
                    if (neutralLabel != null) TextButton(onClick = onNeutral) { Text(neutralLabel) }
                    if (dismissLabel != null) TextButton(onClick = onDismiss) { Text(dismissLabel) }
                }
            }
        }
    )
}

/**
 * Fine-adjust slider for a numeric text field: snaps to [step] and covers a [span]-wide
 * window centred on the value (e.g. ±5 kg), while the field's −/+ buttons make the big
 * jumps. The window re-centres whenever the value changes from outside the slider
 * (typed, −/+, picked from history) — never from its own drag — so the thumb doesn't jump.
 */
@Composable
internal fun StepSlider(
    value: String,
    onValueChange: (String) -> Unit,
    step: Float,
    span: Float,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val current = value.toLeadFloat() ?: 0f
    fun windowStartFor(v: Float) = (((v - span / 2f) / step).roundToInt() * step).coerceAtLeast(0f)

    // Window bookkeeping lives in a plain holder and is updated during composition, so a −/+
    // press renders the re-centred window in its very first frame. (Doing it in a
    // LaunchedEffect drew one frame with the old window first — thumb pinned to an edge,
    // track full/empty — which showed up as a colour flash on every press.)
    // Compare the exact text the slider sent, not floats: 0.1-steps don't round-trip
    // through text (40.1 → "40.1" → 40.099998), which used to look like an outside change
    // and re-centre mid-drag. A new valueRange also recreates the Slider and kills the drag,
    // so never re-centre while the finger is down.
    val window = remember {
        object {
            var start = windowStartFor(current)
            var seenValue = value
            var lastSentText: String? = null
            var dragging = false
        }
    }
    if (value != window.seenValue) {
        if (!window.dragging && value != window.lastSentText) window.start = windowStartFor(current)
        window.seenValue = value
    }
    val windowStart = window.start

    TickedSlider(
        value = current.coerceIn(windowStart, windowStart + span),
        onValueChange = { raw ->
            window.dragging = true
            // Work in whole step indices so the comparison can't drift
            val index = (raw / step).roundToInt()
            if (index != (current / step).roundToInt()) {
                val text = formatWeight(index * step)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                window.lastSentText = text
                onValueChange(text)
            }
        },
        onValueChangeFinished = { window.dragging = false },
        valueRange = windowStart..(windowStart + span),
        step = step,
        primaryColor = primaryColor,
        modifier = modifier.padding(top = 10.dp)   // breathing room between the field above and the ticks
    )
}

/**
 * Settings-style slider (16dp thumb, 4dp track) with little tick marks ABOVE it at every
 * [step] — not in the track. Every [majorEvery]-th tick by absolute value (e.g. each 2.5 kg,
 * or each whole RPE) is taller; ticks up to the thumb take the accent colour like the active
 * track. [active] = false greys thumb/track/ticks for an unset value but stays draggable.
 */
@Composable
private fun TickedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    majorEvery: Int = 5,
    active: Boolean = true
) {
    val accent = if (active) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val colors = if (active) {
        SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
    } else {
        SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )
    }
    val tickCount = ((valueRange.endInclusive - valueRange.start) / step).roundToInt()  // intervals
    val firstTickIndex = (valueRange.start / step).roundToInt()
    val idleTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // The 48dp Slider leaves 16dp of empty touch area under the 16dp thumb; report
            // 10dp less height so the next field sits closer. Touch area is unchanged.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val trim = SLIDER_BOTTOM_TRIM.roundToPx()
                layout(placeable.width, (placeable.height - trim).coerceAtLeast(0)) {
                    placeable.place(0, 0)
                }
            }
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = remember { MutableInteractionSource() },
                    colors = colors,
                    modifier = Modifier.size(THUMB_SIZE)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = colors,
                    modifier = Modifier.height(4.dp),
                    thumbTrackGapSize = 4.dp
                )
            }
        )

        // Ticks in the empty space above the thumb (the Slider is 48dp tall for touch; the
        // 16dp thumb sits in the middle, so its top is at 16dp). The track runs from half a
        // thumb in from each edge, so the ticks use the same inset to line up with snap points.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = THUMB_SIZE / 2)
                .padding(top = 1.dp)
                .height(8.dp)
        ) {
            val stroke = 1.5.dp.toPx()
            val activeUpTo = if (active) ((value - valueRange.start) / step).roundToInt() else -1
            for (i in 0..tickCount) {
                val x = size.width * i / tickCount
                val major = (firstTickIndex + i) % majorEvery == 0
                val h = if (major) size.height else size.height * 0.5f
                drawLine(
                    color = if (i <= activeUpTo) primaryColor else idleTickColor,
                    start = Offset(x, size.height - h),
                    end = Offset(x, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// ── RPE ───────────────────────────────────────────────────────────────────────

private const val RPE_MIN = 5f
private const val RPE_MAX = 10f
private const val RPE_STEP = 0.5f

/** RPE → reps in reserve, e.g. 8 → "2 reps left", 8.5 → "1–2 reps left", 10 → "max effort". */
internal fun rpeDescription(rpe: Float): String {
    val rir = RPE_MAX - rpe
    return when {
        rpe >= 10f -> "max effort"
        rpe >= 9.5f -> "maybe 1 more"
        rir >= 5f -> "5+ reps left"
        rir % 1f == 0f -> if (rir == 1f) "1 rep left" else "${rir.toInt()} reps left"
        else -> "${rir.toInt()}–${rir.toInt() + 1} reps left"
    }
}

/** "8" / "8.5" for display. */
internal fun formatRpe(rpe: Float): String = formatWeight(rpe)

/**
 * Optional RPE (rate of perceived exertion) 5–10 in 0.5 steps. Starts unset (greyed,
 * "Not set"); dragging sets it; Clear unsets it again. Label shows reps in reserve.
 */
@Composable
internal fun RpeSlider(
    rpe: Float?,
    onRpeChange: (Float?) -> Unit,
    primaryColor: Color
) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.heightIn(min = 40.dp),   // Clear button height: no jump when it appears
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "RPE",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (rpe == null) "Not set" else "${formatRpe(rpe)} · ${rpeDescription(rpe)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (rpe == null) MaterialTheme.colorScheme.onSurfaceVariant else primaryColor,
                modifier = Modifier.weight(1f)
            )
            if (rpe != null) {
                TextButton(onClick = { onRpeChange(null) }) { Text("Clear") }
            }
        }
        TickedSlider(
            value = rpe ?: RPE_MIN,
            onValueChange = { raw ->
                val snapped = (raw / RPE_STEP).roundToInt() * RPE_STEP
                if (snapped != rpe) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRpeChange(snapped)
                }
            },
            onValueChangeFinished = {},
            valueRange = RPE_MIN..RPE_MAX,
            step = RPE_STEP,
            primaryColor = primaryColor,
            majorEvery = 2,          // taller tick on every whole RPE
            active = rpe != null
        )
    }
}

private val THUMB_SIZE = 16.dp
private val SLIDER_BOTTOM_TRIM = 10.dp

/**
 * Calendar date of a DatePicker value. Picked dates are UTC midnight; untouched defaults
 * (e.g. "now") carry a time of day and are read in the local zone.
 */
internal fun pickerMillisToLocalDate(millis: Long): LocalDate {
    val zone = if (millis % 86_400_000L == 0L) ZoneOffset.UTC else ZoneId.systemDefault()
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

/**
 * One-line date picker row: calendar icon + date (tap opens the full picker), with
 * Today / Yesterday quick chips. Quick picks keep the current time of day.
 */
@Composable
internal fun SheetDateRow(state: DatePickerState, primaryColor: Color) {
    var showPicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val selected = pickerMillisToLocalDate(state.selectedDateMillis ?: System.currentTimeMillis())
    val dateText = when (selected) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> selected.format(DateTimeFormatter.ofPattern("EEEE d.M.yyyy"))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CalendarToday,
            contentDescription = "Pick date",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            dateText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = primaryColor.copy(alpha = 0.18f),
            selectedLabelColor = primaryColor
        )
        FilterChip(
            selected = selected == today,
            onClick = { state.selectedDateMillis = System.currentTimeMillis() },
            label = { Text("Today") },
            colors = chipColors
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = selected == today.minusDays(1),
            onClick = { state.selectedDateMillis = System.currentTimeMillis() - 86_400_000L },
            label = { Text("Yesterday") },
            colors = chipColors
        )
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("OK") } }
        ) { DatePicker(state = state) }
    }
}
