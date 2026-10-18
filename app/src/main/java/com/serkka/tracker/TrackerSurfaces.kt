package com.serkka.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Shared M3 surfaces ────────────────────────────────────────────────────────
// The app's visual language is container-free: no cards and no panels. Repeated
// content is flat, full-width list rows with dividers; hero/summary blocks sit
// directly on the background in a FlatSection; structure comes from SectionHeaders
// and spacing. Never use elevation shadows (invisible on the near-black background).

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
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PageGutter, end = if (actionLabel != null) 4.dp else PageGutter, top = 16.dp, bottom = 4.dp),
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
