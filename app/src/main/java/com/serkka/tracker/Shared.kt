@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Waves
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ── Navigation ───────────────────────────────────────────────────────────────

enum class Screen(val title: String) {
    Summary("Weekly Summary"),
    Workouts("Workouts"),
    StravaCalendar("Calendar"),
    WeightTracking("Weight Tracking"),
    WorkoutStats("Workout Stats"),
    Notes("Notes"),
    Sessions("Sessions"),
    Settings("Settings"),
    WorkoutTimer("Workout Timer"),
    AiAssistant("AI Assistant")
}

// ── Strava ────────────────────────────────────────────────────────────────────

internal val STRAVA_CLIENT_ID = BuildConfig.STRAVA_CLIENT_ID
internal val STRAVA_CLIENT_SECRET = BuildConfig.STRAVA_CLIENT_SECRET

// ── Date & time formatting ────────────────────────────────────────────────────

private val fullDateFormat = java.text.SimpleDateFormat("EEEE d.M.yyyy", java.util.Locale.getDefault())
private val shortDateFormat = java.text.SimpleDateFormat("EEEE d.M.yy", java.util.Locale.getDefault())
private val backupDateFormat = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
private val chartDateFormat = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())

internal fun formatDate(epochMs: Long): String = fullDateFormat.format(java.util.Date(epochMs))
internal fun formatDateShort(epochMs: Long): String = shortDateFormat.format(java.util.Date(epochMs))
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
    if (onClick != null || onLongClick != null) {
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource as MutableInteractionSource,
                indication = ripple(),
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick ?: {}
            )
    } else {
        // If used for visual feedback on an existing clickable, we clip to ensure the ripple is rounded
        Modifier.clip(RoundedCornerShape(12.dp))
    }
)

// ── Shared dialogs ────────────────────────────────────────────────────────────

@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmInteractionSource = remember { MutableInteractionSource() }
    val dismissInteractionSource = remember { MutableInteractionSource() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(title) },
        text    = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                interactionSource = confirmInteractionSource,
                modifier = Modifier.bounceClick(confirmInteractionSource),
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                interactionSource = dismissInteractionSource,
                modifier = Modifier.bounceClick(dismissInteractionSource)
            ) { Text("Cancel") }
        }
    )
}

// ── Shimmer ──────────────────────────────────────────────────────────────────

fun Modifier.shimmer(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)): Modifier = composed {
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
