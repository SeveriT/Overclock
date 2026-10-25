package com.serkka.tracker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Tools ─────────────────────────────────────────────────────────────────────
// Accordion of small utilities. One tool is open at a time; the open one is
// remembered across launches.

private enum class Tool { Schedule, PaceCalculator }

private const val PREF_OPEN_TOOL = "tools_open_tool"

@Composable
fun ToolsPage(
    scheduleItems: List<ScheduleItem>,
    primaryColor: Color,
    onAddScheduleItem: (String) -> Unit,
    onToggleScheduleItem: (ScheduleItem) -> Unit,
    onRenameScheduleItem: (ScheduleItem, String) -> Unit,
    onDeleteScheduleItem: (ScheduleItem) -> Unit,
    onResetSchedule: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp,
    bottomPadding: Dp = 16.dp
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context).tracker }
    var openTool by remember {
        mutableStateOf(prefs.getString(PREF_OPEN_TOOL, null)?.let { name -> Tool.entries.find { it.name == name } })
    }
    fun toggle(tool: Tool) {
        openTool = if (openTool == tool) null else tool
        prefs.edit().putString(PREF_OPEN_TOOL, openTool?.name).apply()
    }

    var editingScheduleItem by remember { mutableStateOf<ScheduleItem?>(null) }
    val doneCount = scheduleItems.count { it.isDone }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 4.dp, bottom = bottomPadding)
    ) {
        // ── Schedule ──────────────────────────────────────────────────────────
        item(key = "tool_schedule") {
            ToolHeader(
                icon = Icons.Default.Checklist,
                title = "Schedule",
                subtitle = if (scheduleItems.isEmpty()) "Workout checklist"
                           else "$doneCount of ${scheduleItems.size} done",
                expanded = openTool == Tool.Schedule,
                primaryColor = primaryColor,
                onClick = { toggle(Tool.Schedule) },
                modifier = Modifier.animateItem()
            )
        }
        if (openTool == Tool.Schedule) {
            scheduleChecklistItems(
                items = scheduleItems,
                primaryColor = primaryColor,
                onAdd = onAddScheduleItem,
                onToggle = onToggleScheduleItem,
                onDelete = onDeleteScheduleItem,
                onResetAll = onResetSchedule,
                onEdit = { editingScheduleItem = it }
            )
        }
        item(key = "tool_divider_schedule") { ListRowDivider(modifier = Modifier.animateItem(), inset = 0.dp) }

        // ── Pace calculator ───────────────────────────────────────────────────
        item(key = "tool_pace") {
            ToolHeader(
                icon = Icons.Default.Speed,
                title = "Pace Calculator",
                subtitle = "Convert min/km and km/h",
                expanded = openTool == Tool.PaceCalculator,
                primaryColor = primaryColor,
                onClick = { toggle(Tool.PaceCalculator) },
                modifier = Modifier.animateItem()
            )
        }
        if (openTool == Tool.PaceCalculator) {
            item(key = "pace_content") {
                Box(Modifier.animateItem()) { PaceCalculatorContent(primaryColor) }
            }
        }
        item(key = "tool_divider_pace") { ListRowDivider(modifier = Modifier.animateItem(), inset = 0.dp) }
    }

    editingScheduleItem?.let { item ->
        ScheduleItemEditDialog(
            item = item,
            primaryColor = primaryColor,
            onRename = onRenameScheduleItem,
            onDismiss = { editingScheduleItem = null }
        )
    }
}

/** Tappable header row for one tool: tinted icon, title, live subtitle, rotating chevron. */
@Composable
private fun ToolHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    primaryColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "toolChevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = PageGutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(ListAvatarSize)
                .background(primaryColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(chevronRotation)
        )
    }
}
