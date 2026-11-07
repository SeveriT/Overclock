package com.serkka.tracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.serkka.tracker.ui.theme.PersonalBestGold
import kotlinx.coroutines.delay

// ── Personal-best celebration (the app's "peak moment") ───────────────────────
// Restrained on purpose (the old confetti was removed): a gold-tinted pill slides in
// under the top bar, the trophy pops with a spring, and the copy says what was beaten.
// Flat style: tint + shape only, no shadow. Auto-dismisses; tap to dismiss early.

/**
 * One "moment" banner. Used for the peak (a new PB: gold trophy) and the end of a workout
 * (saved timer session: accent check mark) — same component, so both feel like one system.
 */
internal data class PbCelebrationData(
    val title: String,       // "New personal best" / "Workout saved"
    val line1: String,       // "Bench Press · 3×5 @ 100 kg" / "62 min · 14 sets · 1 PB"
    val line2: String,       // "+5 kg over your previous best (95 kg)" / "3rd gym day this week"
    val isPb: Boolean = true,
    val key: Long = System.nanoTime()   // re-trigger even for an identical repeat
)

/**
 * Builds the banner text for a PB entry. [history] is the log *before* this entry is saved
 * (an edited entry is excluded via [excludeId]), so "previous best" never includes itself.
 */
internal fun pbCelebrationFor(
    exercise: String, sets: Int, reps: Int, weight: Float, unit: String,
    history: List<Workout>, excludeId: Int? = null
): PbCelebrationData {
    if (isHoldExercise(exercise)) {
        val previousHold = history
            .filter { it.id != excludeId && it.exerciseName.equals(exercise, ignoreCase = true) }
            .maxOfOrNull { it.reps }
        val holdComparison = when {
            previousHold == null || previousHold == 0 -> "First ${exercise.trim()} logged"
            reps > previousHold ->
                "+${formatHoldDuration(reps - previousHold)} over your previous best (${formatHoldDuration(previousHold)})"
            reps == previousHold -> "Matches your best of ${formatHoldDuration(previousHold)}"
            else -> "Longest so far: ${formatHoldDuration(previousHold)}"
        }
        return PbCelebrationData("New personal best", "${exercise.trim()} · ${formatHoldSets(sets, reps)}", holdComparison)
    }
    val isCardio = isTimeBasedExercise(exercise)
    val valueUnit = if (isCardio) "min" else unit
    val detail = when {
        isCardio -> "${formatWeight(weight)} min"
        sets > 0 && reps > 0 -> "$sets×$reps @ ${formatWeight(weight)} $unit"
        reps > 0 -> "$reps reps @ ${formatWeight(weight)} $unit"
        else -> "${formatWeight(weight)} $unit"
    }
    val previousBest = history
        .filter { it.id != excludeId && it.exerciseName.equals(exercise, ignoreCase = true) }
        .maxOfOrNull { it.weight }
    val comparison = when {
        previousBest == null -> "First ${exercise.trim()} logged"
        weight > previousBest ->
            "+${formatWeight(weight - previousBest)} $valueUnit over your previous best (${formatWeight(previousBest)} $valueUnit)"
        weight == previousBest -> "Matches your best of ${formatWeight(previousBest)} $valueUnit"
        // Marked PB below the heaviest (e.g. a rep PB) — no "minus" framing at a peak moment
        else -> "Heaviest so far: ${formatWeight(previousBest)} $valueUnit"
    }
    return PbCelebrationData("New personal best", "${exercise.trim()} · $detail", comparison)
}

/**
 * End-of-workout summary for a saved timer session: duration, today's sets and PBs, and
 * which gym day of the (Monday-based) week this is. [workouts]/[sessions] are the log before
 * this session is saved; the session itself counts as today.
 */
internal fun workoutSavedSummary(
    durationSeconds: Int,
    workouts: List<Workout>,
    sessions: List<WorkoutSession>
): PbCelebrationData {
    val zone = java.time.ZoneId.systemDefault()
    fun dateOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now()
    val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    val todaysWorkouts = workouts.filter { dateOf(it.date) == today }
    val sets = todaysWorkouts
        .filterNot { isTimeBasedExercise(it.exerciseName) }
        .sumOf { if (it.sets > 0) it.sets else 1 }
    val pbs = todaysWorkouts.count { it.isPersonalBest }

    val minutes = durationSeconds / 60
    val duration = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    val line1 = buildList {
        add(duration)
        if (sets > 0) add(if (sets == 1) "1 set" else "$sets sets")
        if (pbs > 0) add(if (pbs == 1) "1 PB" else "$pbs PBs")
    }.joinToString(" · ")

    val gymDays = (workouts.map { dateOf(it.date) } + sessions.map { dateOf(it.date) } + today)
        .filter { !it.isBefore(weekStart) && !it.isAfter(today) }
        .toSet().size
    val ordinal = when {
        gymDays % 100 in 11..13 -> "${gymDays}th"
        gymDays % 10 == 1 -> "${gymDays}st"
        gymDays % 10 == 2 -> "${gymDays}nd"
        gymDays % 10 == 3 -> "${gymDays}rd"
        else -> "${gymDays}th"
    }
    return PbCelebrationData("Workout saved", line1, "$ordinal gym day this week", isPb = false)
}

/** Overlay banner; place at the top of the screen content, below the top bar. */
@Composable
internal fun PbCelebrationBanner(
    data: PbCelebrationData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep the last shown data so the exit animation still has text to render
    var shown by remember { mutableStateOf(data) }
    if (data != null) shown = data

    LaunchedEffect(data?.key) {
        if (data != null) {
            delay(3500)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = data != null,
        enter = slideInVertically(tween(280)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        val pb = shown ?: return@AnimatedVisibility
        // Trophy "pop": small overshoot spring, restarted for every new PB
        val trophyScale = remember(pb.key) { Animatable(0.4f) }
        LaunchedEffect(pb.key) {
            trophyScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        // PB = gold trophy; workout summary = accent check mark
        val accent = if (pb.isPb) PersonalBestGold else MaterialTheme.colorScheme.primary
        Row(
            modifier = Modifier
                .padding(horizontal = PageGutter)
                .fillMaxWidth()
                .background(
                    // Opaque base + gold tint so list text behind never shows through
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    TrackerShapes.Medium
                )
                .background(accent.copy(alpha = 0.14f), TrackerShapes.Medium)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(trophyScale.value)
                    .background(accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (pb.isPb) Icons.Default.EmojiEvents else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pb.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Text(
                    pb.line1,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pb.line2,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
