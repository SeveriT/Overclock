package com.serkka.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.roundToInt

/** A bottom bar tab: the route it opens, its icon and its name (read out by TalkBack). */
internal data class NavTab(val route: String, val icon: ImageVector, val label: String)

/** The button next to the tabs: add something on pages that have an add sheet, otherwise Settings. */
internal data class NavFab(val icon: ImageVector, val description: String, val onClick: () -> Unit)

/** Height of the bar's row (tabs pill and button), without the system navigation bar below it. */
internal val NAV_ROW_HEIGHT = 56.dp

/** Space the bar takes above the system navigation bar, including its bottom margin. */
internal val NAV_BAR_SPACE: Dp = NAV_ROW_HEIGHT + 16.dp

/** A tab's width when the row has room: the 24 dp icon with 20 dp either side. */
private val TAB_WIDTH = 64.dp

/** Space between tabs inside the pill. */
private val TAB_GAP = 2.dp

/**
 * The bottom bar, same style as the Overtime app: a floating pill of icon tabs with the selected one
 * highlighted, and the page's button beside it in the same centred row. [highlightPosition] is where
 * the highlight sits as a tab index, fractional while the pages are being swiped so it follows the
 * finger (null: no tab, e.g. on Settings). It's read while laying out, so swiping doesn't recompose.
 * [timerText], when set, replaces the Timer tab's icon with the running time. A null [fab] keeps the
 * button's slot (invisible), so the tabs never shift sideways.
 */
@Composable
internal fun PillNavBar(
    tabs: List<NavTab>,
    highlightPosition: () -> Float?,
    accent: Color,
    timerRoute: String,
    timerText: String?,
    fab: NavFab?,
    onSelect: (String) -> Unit,
    /** The row's width (tabs + button), so the music widget above can match it. */
    onRowWidth: (Dp) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Content fades out under the bar instead of ending at a hard edge
            .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp)
            // Taps between and around the pill and button don't fall through to the page
            .pointerInput(Unit) {},
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.onSizeChanged { onRowWidth(with(density) { it.width.toDp() }) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The pill yields space if the row is ever too narrow, so the button stays square
            TabsPill(tabs, highlightPosition, accent, timerRoute, timerText, onSelect, Modifier.weight(1f, fill = false))
            NavFabButton(fab, accent)
        }
    }
}

@Composable
private fun TabsPill(
    tabs: List<NavTab>,
    highlightPosition: () -> Float?,
    accent: Color,
    timerRoute: String,
    timerText: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // The Timer tab's width while it shows the running time: the wider of its two forms
    // (formatTabTime: "59:59" under an hour, "10h 59" after)
    val timeStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    val textMeasurer = rememberTextMeasurer()
    val timeWidth = remember(timeStyle, density) {
        val widest = listOf("00:00", "00h 00").maxOf { textMeasurer.measure(it, timeStyle).size.width }
        with(density) { widest.toDp() + 2.dp }
    }
    // Where each tab sits in the row (left edge, width), for placing the highlight between them
    val tabBounds = remember(tabs.size) { List(tabs.size) { mutableStateOf<Pair<Int, Int>?>(null) } }
    // The latest position source (a new lambda each time the bar recomposes), read as state, so
    // neither the nearest tab nor the highlight's layout keeps an old one
    val position by rememberUpdatedState(highlightPosition)
    // The tab the highlight is nearest: its icon takes the accent colour
    val nearest by remember { derivedStateOf { position()?.roundToInt() } }
    val highlightShown by animateFloatAsState(
        if (nearest != null) 1f else 0f, tween(Motion.SHORT), label = "highlightAlpha"
    )
    // Last place it was, so it fades out where it was when no tab is current (e.g. Settings)
    val lastPosition = remember { floatArrayOf(0f) }

    Box(
        modifier = modifier
            // Always the width of all tabs at TAB_WIDTH (or what the row allows): the running timer
            // takes its extra room from the other tabs, never from the rest of the bar
            .width(TAB_WIDTH * tabs.size + TAB_GAP * (tabs.size - 1) + 8.dp)
            .graphicsLayer(shadowElevation = with(density) { 6.dp.toPx() }, shape = CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp)
    ) {
        // The highlight: between two tabs while swiping, by how far the swipe has got
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val at = position()?.also { lastPosition[0] = it } ?: lastPosition[0]
                    val bounds = tabBounds.map { it.value }
                    if (bounds.any { it == null }) {
                        return@layout layout(constraints.maxWidth, constraints.maxHeight) {}
                    }
                    val from = floor(at).toInt().coerceIn(0, tabs.lastIndex)
                    val to = (from + 1).coerceAtMost(tabs.lastIndex)
                    val f = (at - from).coerceIn(0f, 1f)
                    val (fromLeft, fromWidth) = bounds[from]!!
                    val (toLeft, toWidth) = bounds[to]!!
                    val left = (fromLeft + (toLeft - fromLeft) * f).roundToInt()
                    val width = (fromWidth + (toWidth - fromWidth) * f).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(left, 0) }
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(highlightShown)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f))
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TAB_GAP)
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == nearest
                val tint by animateColorAsState(
                    if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(Motion.SHORT),
                    label = "tabTint"
                )
                val showTime = tab.route == timerRoute && timerText != null
                Box(
                    modifier = Modifier
                        // The tabs share the pill's width equally. While the timer runs, its tab takes
                        // the time's fixed width (so ticking digits don't resize it) and the others
                        // share what's left, so the pill itself never changes size.
                        .then(
                            if (showTime) Modifier.width(timeWidth + 32.dp)
                            else Modifier.weight(1f)
                        )
                        .onPlaced { tabBounds[index].value = it.positionInParent().x.roundToInt() to it.size.width }
                        .clip(CircleShape)
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(tab.route) },
                            role = Role.Tab,
                            indication = null,
                            interactionSource = null
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (showTime) {
                        // While the timer runs its tab shows the time instead of the icon, in a
                        // fixed width, so the bar doesn't change size as the digits tick or the
                        // time passes an hour
                        Text(
                            timerText.orEmpty(),
                            style = timeStyle,
                            color = tint,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(timeWidth)
                        )
                    } else {
                        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NavFabButton(fab: NavFab?, accent: Color) {
    // Keeps its slot while hidden, so the centred row (and the tabs) never jump
    val shown by animateFloatAsState(if (fab != null) 1f else 0f, tween(Motion.SHORT), label = "fabAlpha")
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(NAV_ROW_HEIGHT)
            .alpha(shown)
            .bounceClick(interactionSource)
            .clip(MaterialTheme.shapes.large)
            .background(accent)
            .then(
                if (fab != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = androidx.compose.material3.ripple(), onClick = fab.onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Animates only when the icon changes: pages sharing Settings don't swap the gear for itself
        AnimatedContent(
            targetState = fab,
            contentKey = { it?.icon },
            transitionSpec = {
                (fadeIn(tween(Motion.SHORT)) + scaleIn(tween(Motion.SHORT), initialScale = 0.25f)) togetherWith
                    (fadeOut(tween(Motion.SHORT)) + scaleOut(tween(Motion.SHORT), targetScale = 0.25f))
            },
            label = "fabIcon"
        ) { current ->
            if (current != null) {
                Icon(
                    current.icon,
                    contentDescription = current.description,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * The running time as the Timer tab shows it: minutes and seconds ("12:34") for the first hour,
 * then hours and minutes ("1h 05"), so the tab stays narrow.
 */
internal fun formatTabTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h %02d".format(minutes)
    else "%02d:%02d".format(minutes, totalSeconds % 60)
}
