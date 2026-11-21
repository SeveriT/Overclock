package com.serkka.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// ── Shared M3 surfaces ────────────────────────────────────────────────────────
// The app's visual language is container-free: no cards and no panels. Repeated
// content is flat, full-width list rows with dividers; hero/summary blocks sit
// directly on the background in a FlatSection; structure comes from SectionHeaders
// and spacing. Never use elevation shadows (invisible on the near-black background).

/**
 * The app's three corner shapes — use these instead of ad-hoc RoundedCornerShape values.
 * Small: thumbnails, chips, tiny badges · Medium: banners, blocks, ripple clips · Pill: tags,
 * progress tracks, buttons (M3 buttons are already pill-shaped by default).
 */
internal object TrackerShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Pill = RoundedCornerShape(50)
    /** The page sheet under the top bar: rounded top corners only. */
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    /**
     * Scalloped "cookie" with 9 soft lobes, like Material 3's Cookie9Sided (album art), turned by
     * [rotationDegrees]: only the outline turns, whatever it clips stays upright.
     */
    fun cookie(rotationDegrees: Float = 0f): Shape = CookieShape(lobes = 9, depth = 0.07f, rotationDegrees)
}

/**
 * A circle with [lobes] soft bumps round its edge: the radius swings by [depth] (share of the radius)
 * along a cosine, so the scallops are smooth and the shape fills its box at the lobe tips.
 */
private class CookieShape(
    private val lobes: Int,
    private val depth: Float,
    private val rotationDegrees: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy)
        val path = Path()
        val steps = 360
        val turn = Math.toRadians(rotationDegrees.toDouble())
        for (i in 0..steps) {
            val angle = 2.0 * Math.PI * i / steps
            // 1 at a lobe tip, 1 - depth between lobes; the lobes are turned, the circle isn't
            val r = radius * (1f - depth * (1f - kotlin.math.cos(lobes * (angle - turn)).toFloat()) / 2f)
            val x = cx + r * kotlin.math.cos(angle).toFloat()
            val y = cy + r * kotlin.math.sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * The app's three animation speeds (tween default easing is FastOutSlowIn). Use these for any
 * new animation so motion feels the same everywhere:
 *   SHORT  — fades, toggles and every exit
 *   MEDIUM — enters, layout moves (navbar + everything that moves with it), screen changes, banners
 *   LONG   — charts drawing in, counters ticking up, colour crossfades
 * Deliberate exceptions: the timer ring (synced to the 1 s clock), the music widget's swipe
 * snap/text slide and its 1 s progress bar, and endless loops (shimmer, pulse, waveform).
 */
internal object Motion {
    const val SHORT = 200
    const val MEDIUM = 300
    const val LONG = 600
}

/** Horizontal page gutter for anything that isn't a full-bleed list row. */
internal val PageGutter: Dp = 16.dp

/** Leading-avatar size for list rows (M3 list spec). */
internal val ListAvatarSize: Dp = 40.dp

/** Divider start inset that aligns with row text after a leading avatar: 16 + 40 + 16. */
internal val ListDividerInset: Dp = 72.dp

/**
 * Container-free block for hero/summary content: sits directly on the background.
 * Content must align with headers at PageGutter (16dp) — pad either this modifier or
 * the inner content, never both.
 */
@Composable
internal fun FlatSection(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        content = content
    )
}

/** M3 list subheader with an optional trailing text action (e.g. "See all"). */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** Trailing icon buttons (e.g. Home's edit-goal / hide), instead of or next to the text action. */
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    // The action TextButton keeps M3's 48dp touch target, which makes the row ~14dp taller
    // above (and below) the title. Trim the padding by that much so every header's title
    // sits the same 16dp below the content above it, with or without an action.
    val hasAction = (actionLabel != null && onAction != null) || actions != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = PageGutter,
                end = if (hasAction) 4.dp else PageGutter,
                top = if (hasAction) 2.dp else 16.dp,
                bottom = if (hasAction) 0.dp else 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
        actions?.invoke(this)
    }
}

/** Compact muted icon button for [SectionHeader] actions (edit, hide…). */
@Composable
internal fun HeaderIconAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Leading avatar for activity rows: tinted tonal circle with the activity icon. */
@Composable
internal fun ActivityAvatar(type: String, name: String?, tint: Color) {
    Box(
        modifier = Modifier
            .size(ListAvatarSize)
            .background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            getIconForActivity(type, name),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Transparent ListItem colors so flat rows sit directly on the page background. */
@Composable
internal fun flatListItemColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = Color.Transparent)

/** Inset divider between flat list rows, aligned with the row text. */
@Composable
internal fun ListRowDivider(modifier: Modifier = Modifier, inset: Dp = ListDividerInset) {
    HorizontalDivider(
        modifier = modifier.padding(start = inset),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
