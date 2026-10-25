@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ── Running pace calculator ───────────────────────────────────────────────────
// Two linked sliders: pace (min/km) and speed (km/h). Pace in seconds per km is the
// single source of truth; speed is always derived from it (km/h = 3600 / sec-per-km),
// so dragging either slider moves the other.

private const val PACE_MIN_SEC = 180f                  // 3:00 /km
private const val PACE_MAX_SEC = 480f                  // 8:00 /km
private const val SPEED_MIN = 3600f / PACE_MAX_SEC     // 7.5 km/h
private const val SPEED_MAX = 3600f / PACE_MIN_SEC     // 20 km/h
private const val KM_PER_MILE = 1.609344f
private const val PREF_PACE_SECONDS = "pace_calc_seconds"
private const val COOPER_SECONDS = 12 * 60f

private val finishDistances = listOf(
    "400 m" to 0.4f,
    "5 km" to 5f,
    "10 km" to 10f,
    "Half marathon" to 21.0975f,
    "Marathon" to 42.195f
)

/** Pace calculator body, embedded in the Tools screen (no own scrolling or page padding). */
@Composable
fun PaceCalculatorContent(primaryColor: Color) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context).tracker }

    var paceSec by rememberSaveable {
        mutableFloatStateOf(prefs.getFloat(PREF_PACE_SECONDS, 360f).coerceIn(PACE_MIN_SEC, PACE_MAX_SEC))
    }
    val speedKmh = 3600f / paceSec

    fun setPace(sec: Float) { paceSec = sec.coerceIn(PACE_MIN_SEC, PACE_MAX_SEC) }
    fun setSpeed(kmh: Float) { setPace(3600f / kmh.coerceIn(SPEED_MIN, SPEED_MAX)) }
    fun save() = prefs.edit().putFloat(PREF_PACE_SECONDS, paceSec).apply()

    val sliderColors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
        // ── Hero readout ──────────────────────────────────────────────────────
        FlatSection(modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                PaceReadout(
                    value = formatPace(paceSec),
                    unit = "min/km",
                    sub = "${formatPace(paceSec * KM_PER_MILE)} min/mi",
                    color = primaryColor,
                    modifier = Modifier.weight(1f)
                )
                PaceReadout(
                    value = String.format("%.1f", speedKmh),
                    unit = "km/h",
                    sub = String.format("%.1f mph", speedKmh / KM_PER_MILE),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Sliders ───────────────────────────────────────────────────────────
        SectionHeader("Pace")
        StepperSlider(
            value = paceSec,
            onValueChange = { setPace(it.roundToInt().toFloat()) },   // snap to whole seconds
            onValueChangeFinished = { save() },
            valueRange = PACE_MIN_SEC..PACE_MAX_SEC,
            onDecrement = { setPace(paceSec.roundToInt() - 1f); save() },
            onIncrement = { setPace(paceSec.roundToInt() + 1f); save() },
            colors = sliderColors
        )

        SectionHeader("Speed")
        StepperSlider(
            value = speedKmh,
            onValueChange = { setSpeed((it * 10).roundToInt() / 10f) },  // snap to 0.1 km/h
            onValueChangeFinished = { save() },
            valueRange = SPEED_MIN..SPEED_MAX,
            onDecrement = { setSpeed((speedKmh * 10).roundToInt() / 10f - 0.1f); save() },
            onIncrement = { setSpeed((speedKmh * 10).roundToInt() / 10f + 0.1f); save() },
            colors = sliderColors
        )

        // ── Finish times at this pace ─────────────────────────────────────────
        SectionHeader("Finish Times")
        finishDistances.forEachIndexed { index, (label, km) ->
            FinishRow(label = label, value = formatDuration(paceSec * km))
            if (index == 0) {
                // Cooper test is the inverse: fixed 12 min, show the distance covered at this pace.
                ListRowDivider(inset = PageGutter)
                FinishRow(
                    label = "Cooper",
                    value = "${(COOPER_SECONDS / paceSec * 1000).roundToInt()} m"
                )
            }
            if (index < finishDistances.lastIndex) ListRowDivider(inset = PageGutter)
        }
    }
}

@Composable
private fun FinishRow(label: String, value: String, supporting: String? = null) {
    ListItem(
        colors = flatListItemColors(),
        headlineContent = { Text(label, fontWeight = FontWeight.Medium) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    )
}

@Composable
private fun PaceReadout(value: String, unit: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.width(6.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(
            sub,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Slider with −/+ buttons for fine adjustment. */
@Composable
private fun StepperSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    colors: SliderColors
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDecrement, enabled = value > valueRange.start) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = colors,
            modifier = Modifier.weight(1f),
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
        IconButton(onClick = onIncrement, enabled = value < valueRange.endInclusive) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}

/** Seconds → "m:ss". */
private fun formatPace(seconds: Float): String {
    val total = seconds.roundToInt()
    return "%d:%02d".format(total / 60, total % 60)
}

/** Seconds → "h:mm:ss" (or "m:ss" under an hour). */
private fun formatDuration(seconds: Float): String {
    val total = seconds.roundToInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
