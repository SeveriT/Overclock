@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

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
import androidx.compose.ui.graphics.Color
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

    var windowStart by remember { mutableFloatStateOf(windowStartFor(current)) }
    // Compare the exact text the slider sent, not floats: 0.1-steps don't round-trip
    // through text (40.1 → "40.1" → 40.099998), which used to look like an outside change
    // and re-centre mid-drag. A new valueRange also recreates the Slider and kills the drag,
    // so never re-centre while the finger is down.
    var lastSentText by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!dragging && value != lastSentText) windowStart = windowStartFor(current)
    }

    val colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
    Slider(
        value = current.coerceIn(windowStart, windowStart + span),
        onValueChange = { raw ->
            dragging = true
            // Work in whole step indices so the comparison can't drift
            val index = (raw / step).roundToInt()
            if (index != (current / step).roundToInt()) {
                val text = formatWeight(index * step)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                lastSentText = text
                onValueChange(text)
            }
        },
        onValueChangeFinished = { dragging = false },
        valueRange = windowStart..(windowStart + span),
        colors = colors,
        modifier = modifier.fillMaxWidth(),
        // Same compact thumb + slim track as the Settings sliders
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = remember { MutableInteractionSource() },
                colors = colors,
                modifier = Modifier.size(16.dp)
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
}

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
