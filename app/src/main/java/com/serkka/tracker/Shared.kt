@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ── Navigation ───────────────────────────────────────────────────────────────

enum class Screen(val title: String) {
    Summary("Home"),
    Workouts("Workouts"),
    Calendar("Calendar"),
    WeightTracking("Weight Tracking"),
    WorkoutStats("Workout Stats"),
    Notes("Notes"),
    Sessions("Sessions"),
    Tools("Tools"),
    Settings("Settings"),
    WorkoutTimer("Workout Timer")
}

// ── Date & time formatting ────────────────────────────────────────────────────

private val fullDateFormat = java.text.SimpleDateFormat("EEEE d.M.yyyy", java.util.Locale.getDefault())
private val shortDateFormat = java.text.SimpleDateFormat("EEEE d.M.yy", java.util.Locale.getDefault())
private val backupDateFormat = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
private val chartDateFormat = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())

internal fun formatDate(epochMs: Long): String = fullDateFormat.format(java.util.Date(epochMs))
internal fun formatDateShort(epochMs: Long): String = shortDateFormat.format(java.util.Date(epochMs))

private val dateNoYearFormat = java.text.SimpleDateFormat("EEEE d.M", java.util.Locale.getDefault())
/** formatDate() without the year, e.g. "Tuesday 14.10". Display only — never a grouping key. */
internal fun formatDateNoYear(epochMs: Long): String = dateNoYearFormat.format(java.util.Date(epochMs))
private val weekdayFormat = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
/** Weekday only, e.g. "Tuesday". Display only. */
internal fun formatWeekday(epochMs: Long): String = weekdayFormat.format(java.util.Date(epochMs))
internal fun formatBackupDate(epochMs: Long): String = backupDateFormat.format(java.util.Date(epochMs))
internal fun formatChartDate(epochMs: Long): String = chartDateFormat.format(java.util.Date(epochMs))

internal fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
           else       String.format("%02d:%02d", m, s)
}

// ── Number formatting ─────────────────────────────────────────────────────────

/**
 * Exercises logged by duration only: the minutes go in Workout.weight and sets/reps stay 0.
 * The workout sheet then shows a Minutes field instead of sets/reps/kg, lists show "45 min",
 * and they're left out of reps/volume stats. Add a name here to make another one time-based.
 */
internal val TIME_BASED_EXERCISES = setOf(
    // English
    "cardio", "stretching", "treadmill", "rowing", "cycling", "elliptical", "stairmaster",
    "running", "walking", "swimming", "hiit", "warm-up", "cool-down", "mobility",
    "foam rolling", "yoga", "sauna",
    // Finnish
    "venyttely", "kardio", "juoksumatto", "soutu", "pyöräily", "crosstrainer", "porraskone",
    "juoksu", "kävely", "uinti", "lämmittely", "jäähdyttely", "liikkuvuus", "rullaus", "jooga"
)

internal fun isTimeBasedExercise(name: String): Boolean =
    name.trim().lowercase() in TIME_BASED_EXERCISES

/**
 * Hold exercises: sets × a duration per set (e.g. plank 3 × 1:30). The seconds per set go in
 * Workout.reps and weight stays 0. The sheet shows Sets + Min + Sec; lists show "3 × 1:30 min";
 * PBs compare hold time; they're left out of reps/volume stats (seconds aren't reps).
 */
internal val HOLD_EXERCISES = setOf(
    "plank", "side plank", "wall sit", "dead hang",       // English
    "lankku", "kylkilankku", "seinäkyykky", "roikunta"    // Finnish
)

internal fun isHoldExercise(name: String): Boolean =
    name.trim().lowercase() in HOLD_EXERCISES

/** Seconds per set for display: 45 → "45 s", 90 → "1:30 min", 120 → "2 min". */
internal fun formatHoldDuration(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "%d:%02d min".format(seconds / 60, seconds % 60)
}

/** "3 × 1:30 min", or just the duration when sets weren't entered. */
internal fun formatHoldSets(sets: Int, seconds: Int): String =
    if (sets > 0) "$sets × ${formatHoldDuration(seconds)}" else formatHoldDuration(seconds)

internal fun formatWeight(weight: Float): String {
    val rounded = (weight * 1000f).roundToInt() / 1000f
    return if (rounded % 1 == 0f) rounded.toInt().toString() else rounded.toString()
}

/** Accepts both '.' and ',' as decimal separators. */
internal fun String.toLeadFloat(): Float? = this.replace(',', '.').toFloatOrNull()

// ── Activity icons ────────────────────────────────────────────────────────────

@Composable
internal fun getIconForActivity(type: String, name: String? = null): ImageVector {
    if (type == "Run" && name?.contains("trail run", ignoreCase = true) == true) {
        return ImageVector.vectorResource(R.drawable.ic_activity_trail_run)
    }
    return when (type) {
        "WeightTraining" -> ImageVector.vectorResource(R.drawable.ic_weight_training)
        "Run"            -> ImageVector.vectorResource(R.drawable.ic_activity_run)
        "TrailRun"       -> ImageVector.vectorResource(R.drawable.ic_activity_trail_run)
        "Ride"           -> ImageVector.vectorResource(R.drawable.ic_activity_ride)
        "Swim"           -> ImageVector.vectorResource(R.drawable.ic_activity_swim)
        "Walk"           -> ImageVector.vectorResource(R.drawable.ic_activity_walk)
        "Hike"           -> ImageVector.vectorResource(R.drawable.ic_activity_hike)
        "Cardio"         -> ImageVector.vectorResource(R.drawable.ic_activity_run)
        else             -> ImageVector.vectorResource(R.drawable.ic_activity_workout)
    }
}

// ── Animations ────────────────────────────────────────────────────────────────

/**
 * Replaced the old spring bounce effect with a clean Material 3 ripple.
 * This ensures consistency across buttons and interactive elements.
 * Supports combinedClickable for long press actions.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceClick(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
): Modifier = this.then(
    // Press bounce: shrinks a little while held and springs back on release
    Modifier.composed {
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (pressed && enabled) 0.92f else 1f,
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 700f),
            label = "bounceScale"
        )
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    }
).then(
    if (onClick != null || onLongClick != null) {
        Modifier
            .clip(TrackerShapes.Medium)
            .combinedClickable(
                interactionSource = interactionSource as MutableInteractionSource,
                indication = ripple(),
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick ?: {}
            )
    } else {
        // If used for visual feedback on an existing clickable, we clip to ensure the ripple is rounded
        Modifier.clip(TrackerShapes.Medium)
    }
)

// ── Shimmer ──────────────────────────────────────────────────────────────────

fun Modifier.shimmer(shape: androidx.compose.ui.graphics.Shape = TrackerShapes.Small): Modifier = composed {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "shimmerTranslate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = androidx.compose.ui.geometry.Offset(translate - 400f, 0f),
        end = androidx.compose.ui.geometry.Offset(translate, 0f)
    )
    this.background(brush = brush, shape = shape)
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
internal fun EmptyState(
    icon: ImageVector,
    message: String,
    primaryColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = primaryColor.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ── Chart draw-in ────────────────────────────────────────────────────────────

/**
 * 0 → 1 once when a chart first appears, for drawing it in from left to right
 * (clipRect(right = left + width * value)). Remembered per chart instance, so it doesn't replay
 * on every recomposition.
 */
@androidx.compose.runtime.Composable
internal fun rememberDrawIn(durationMillis: Int = 900): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val visible = LocalPageVisible.current
    // Replays each time its swipe page becomes the current one (pages next to the current one are
    // already composed off-screen, so starting on composition would finish before you get there)
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) {
            progress.snapTo(0f)
            progress.animateTo(1f, androidx.compose.animation.core.tween(durationMillis, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    return progress
}

/**
 * True while this content's page is the current page of the main swipe pager (WorkoutScreen
 * provides it per page). Outside the pager (sheets, NavHost screens) it's always true. Entrance
 * animations key on it so they play when the page is actually shown.
 */
internal val LocalPageVisible = androidx.compose.runtime.compositionLocalOf { true }
