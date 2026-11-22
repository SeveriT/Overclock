package com.serkka.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The pages that don't have their own tab in the bottom bar, opened from the More tab. */
internal val moreRoutes = listOf(Screen.WorkoutStats.name, Screen.Calendar.name, Screen.Sessions.name)

/** The More tab: a list of the pages without a tab of their own (Stats, Calendar, Sessions). */
@Composable
internal fun MorePage(
    primaryColor: Color,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpen: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        MoreRow(Icons.Default.BarChart, "Workout stats", "Volume and reps per exercise", primaryColor) {
            onOpen(Screen.WorkoutStats.name)
        }
        MoreRow(Icons.Default.CalendarMonth, "Calendar", "A year of training days", primaryColor) {
            onOpen(Screen.Calendar.name)
        }
        MoreRow(Icons.Default.History, "Sessions", "Every timer session, with heart rate", primaryColor) {
            onOpen(Screen.Sessions.name)
        }
    }
}

/** A row like Settings' rows: accent icon, title over a muted subtitle, chevron. */
@Composable
private fun MoreRow(icon: ImageVector, title: String, subtitle: String, primaryColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = PageGutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
