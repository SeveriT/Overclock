@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ── User guide ────────────────────────────────────────────────────────────────
// Full-height sheet of topics grouped by what you're doing. Each row: icon, title and a
// one-line summary; tap to expand into short tips (one topic open at a time).
// Keep this in sync with features — it's the in-app source of truth for "how does X work".

private data class GuideTopic(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val tips: List<String>
)

private data class GuideGroup(val title: String, val topics: List<GuideTopic>)

private val guideGroups = listOf(
    GuideGroup("Getting started", listOf(
        GuideTopic(Icons.Default.Explore, "Navigation", "Five swipeable tabs, everything else in the top bar", listOf(
            "Swipe between Stats, Timer, Workouts, Home, Weight, Calendar and Sessions, or tap a tab in the navbar. The navbar scrolls sideways and keeps the current tab in the middle.",
            "Top bar: Tools (wrench), Notes (pencil) and Settings.",
            "The navbar at the bottom is always visible. While the workout timer runs, the Timer tab shows the elapsed time."
        )),
        GuideTopic(Icons.Default.AutoAwesome, "Demo mode", "Try the app with four weeks of sample data", listOf(
            "Tap \"Try with demo data\" on the welcome screen.",
            "Loads workouts, weigh-ins, sessions, notes and step history.",
            "Remove it anytime with Settings → Clear all data."
        ))
    )),
    GuideGroup("Logging", listOf(
        GuideTopic(Icons.Default.FitnessCenter, "Logging workouts", "Sets, reps and weight in a few taps", listOf(
            "Tap + to open the workout sheet. Pick a recent exercise or start typing; suggestions come from your history.",
            "The \"Last time\" chip refills your previous sets, reps and weight.",
            "Weight − / + jump 5 kg; the slider below fine-tunes ±5 kg in 0.5 kg steps.",
            "Cardio, stretching and similar activities are logged by duration only (minutes and notes, no sets or reps): e.g. Treadmill, Running, Walking, Cycling, Rowing, Yoga, Mobility, Warm-up, Sauna, or Finnish names like Juoksumatto, Kävely, Venyttely, Lämmittely.",
            "Plank, Side plank, Wall sit and Dead hang (Lankku, Kylkilankku, Seinäkyykky, Roikunta) are logged as sets × hold time: enter sets, then minutes and seconds per set.",
            "Rate effort with the optional RPE slider (5–10, shown as reps left). Clear removes it.",
            "Use the Today / Yesterday chips, or tap the date for any day.",
            "Saving a personal best shows a gold banner with how much you beat your previous best by."
        )),
        GuideTopic(Icons.Default.ViewAgenda, "Your workout list", "Grouped by week and day, with quick actions", listOf(
            "Week headers stay pinned while you scroll and show the week number, dates and gym days.",
            "Tap a day to expand or collapse it.",
            "Duplicate copies an entry to today instantly; Copy lets you edit first. Tap an entry for Edit, PB or Delete.",
            "Search with the magnifier or by pulling down at the top of the list. Your most-logged exercises appear as quick filters."
        )),
        GuideTopic(Icons.Default.Timer, "Workout timer", "Track session time and laps", listOf(
            "Start and pause with the main button; tap the ring to start a new lap.",
            "The lap time counts down the ring length. When it's up, the phone buzzes and the time counts up in your accent colour, showing how long you've rested. Change the ring length with − and + while the timer runs.",
            "The timer notification shows the same lap countdown (or how far over you are) and the total time, so you can check it without opening the app.",
            "When you stop, save the session with a name and type, Resume, or discard it with the trash icon.",
            "Saving shows a summary: duration, sets and PBs today, and which gym day of the week it was."
        )),
        GuideTopic(Icons.Default.Favorite, "Heart rate", "Bluetooth strap on the timer", listOf(
            "Tap the heart under the ring on the timer to connect a strap. Works with straps and watches that broadcast standard Bluetooth heart rate (Polar, Garmin, Wahoo, Coospo and others).",
            "Put the strap on before scanning: most only broadcast when they feel a heartbeat.",
            "Your strap is remembered and reconnects by itself whenever you open the timer.",
            "While the timer runs, heart rate is recorded every 5 seconds (paused time isn't). The session saves average, max and the full graph.",
            "The last 10 minutes of heart rate show as a faint graph inside the ring.",
            "Disconnect or Forget the strap in the same sheet."
        )),
        GuideTopic(Icons.Default.History, "Sessions", "Every timer session in one list", listOf(
            "Saved timer sessions plus ones you add with +.",
            "Sessions count toward your weekly streak.",
            "Its own tab after Calendar; saving a timer session takes you there.",
            "Sessions with heart rate show average and max; tap that line for the heart rate graph.",
            "The share icon exports a session as a .tcx file (time and heart rate). Upload it at strava.com/upload or to other training sites."
        )),
        GuideTopic(Icons.Default.EditNote, "Notes", "Program plans, cues and reminders", listOf(
            "Open from the pencil icon in the top bar.",
            "Tap a note to edit it; delete it with the trash icon in the sheet."
        ))
    )),
    GuideGroup("Progress", listOf(
        GuideTopic(Icons.Default.Home, "Home", "Your week at a glance", listOf(
            "Weekly streak, Last 30 Days stats, latest weight and steps.",
            "Steps and latest weight with full-width charts.",
            "Last 30 Days compares gym days, average RPE, sets × reps and volume with the previous 30 days.",
            "Hide a section with its eye-off icon; bring it back from the top bar."
        )),
        GuideTopic(Icons.Default.BarChart, "Workout stats", "Volume and reps per exercise", listOf(
            "Its own tab, right after Workouts.",
            "Exercises are ranked by sets × reps over the last 12 weeks.",
            "Tap an exercise for its reps-per-week chart; tap a bar to read that week.",
            "Full history under the chart shows every set you've logged for that exercise, your best estimated 1RM and heaviest set, and an estimated-1RM trend.",
            "Long-press an exercise to hide it from the list and total. Restore it from Hidden at the bottom."
        )),
        GuideTopic(Icons.Default.MonitorWeight, "Weight tracking", "Trend, prediction, RPI and BMI", listOf(
            "Log weight with optional notes: − / + jump 1 kg and the slider fine-tunes in 0.1 kg steps.",
            "Tap the RPI/BMI metric to set your height. RPI suits muscular builds better than BMI.",
            "The prediction projects your weight to a date you pick, weighting recent weigh-ins most. Scroll the chart right to see its dashed line.",
            "Drag the chart to scroll and pinch to zoom: from 1 week up to 10 weeks on screen. It opens at 6 weeks.",
            "Tap the weight labels on the left of the chart to set its range."
        )),
        GuideTopic(Icons.Default.CalendarMonth, "Calendar", "A year of training days", listOf(
            "Compact month grids for the last 12 months.",
            "Days with a workout or session get an accent circle; today has a ring."
        )),
        GuideTopic(Icons.AutoMirrored.Filled.DirectionsWalk, "Step counter", "Daily steps with a goal", listOf(
            "Counts steps with your phone's built-in sensor.",
            "The pencil icon edits your goal; you get a notification when you reach it.",
            "Long-press the steps chart to add steps for any of the last 7 days.",
            "Needs Activity Recognition and Unrestricted Battery (Settings → Permissions)."
        ))
    )),
    GuideGroup("Tools", listOf(
        GuideTopic(Icons.Default.Handyman, "Tools", "Schedule, 1RM and plate calculators, pace and exercise names", listOf(
            "Open from the wrench icon in the top bar. One tool is open at a time.",
            "Schedule: tick off planned workouts, tap Reset for a new week, long-press an item to rename it.",
            "One-rep max: enter a weight and reps (or tap one of your exercises) for your estimated 1RM and a percentage table for working sets.",
            "Plate calculator: enter a target weight and pick the bar to see the plates for each side.",
            "Pace calculator: drag min/km or km/h and the other follows. Shows finish times and your 12-minute Cooper distance.",
            "Exercise names: rename an exercise everywhere at once, or merge two by renaming one to the other. Each rename can be undone."
        ))
    )),
    GuideGroup("App & data", listOf(
        GuideTopic(Icons.Default.MusicNote, "Music widget", "Control Spotify", listOf(
            "Tap to open the player; swipe left or right to skip tracks.",
            "The progress bar waves while music plays, and colours follow the album art."
        )),
        GuideTopic(Icons.Default.CloudUpload, "Backups", "Keep your training data safe", listOf(
            "Save local backup creates a .zip with your workouts and step history.",
            "Google Drive backups run automatically once a week (Premium). Settings shows the countdown.",
            "Restore from backup replaces your current data with a backup file."
        )),
        GuideTopic(Icons.Default.SystemUpdate, "Updates", "New versions straight from GitHub", listOf(
            "Every time you open the app it checks for a new release, downloads it in the background and shows an Update ready prompt.",
            "Android always asks you to confirm the install itself.",
            "Check any time in Settings → Help → Check for updates.",
            "Updates install over the current app and keep all your data.",
            "The first time, Android asks you to allow installing apps from Overclock."
        )),
        GuideTopic(Icons.Default.Security, "Permissions", "What the app needs and why", listOf(
            "Notifications: timer and step-goal alerts.",
            "Notification Listener: the music widget.",
            "Activity Recognition: the step counter.",
            "Unrestricted Battery: keeps step counting running in the background.",
            "Bluetooth: connecting a heart rate strap (on Android 11 and older, scanning also needs location).",
            "Manage them in Settings → Permissions; status updates when you return from system settings."
        )),
        GuideTopic(Icons.Default.Restore, "Undo & clearing data", "Mistakes are easy to reverse", listOf(
            "Deleting a workout, weight, note or session shows Undo for a few seconds.",
            "Settings → Clear all data wipes everything and restarts the app. Demo mode becomes available again."
        )),
        GuideTopic(Icons.Default.Palette, "Customization", "Make the app yours", listOf(
            "Pick your accent colour with the RGB sliders in Settings.",
            "Tap Auto next to Accent Color and the accent takes the colour of the album art in the music widget. Your own colour comes back when no song is playing.",
            "The background brightness and font size sliders adjust the whole app.",
            "Pick an app icon under Settings → Appearance: the plate in red (25 kg), blue (20), yellow (15) or green (10)."
        ))
    ))
)

/** Opens the user guide as a full-height sheet (name kept: used by Settings and Welcome). */
@Composable
fun UserGuideDialog(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    var openTopic by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "guide_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = PageGutter, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("User Guide", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap a topic to see how it works",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }) { Text("Done", fontWeight = FontWeight.SemiBold) }
                }
            }
            guideGroups.forEach { group ->
                item(key = "group_${group.title}") { SectionHeader(group.title) }
                items(group.topics, key = { it.title }) { topic ->
                    GuideTopicRow(
                        topic = topic,
                        expanded = openTopic == topic.title,
                        primaryColor = primary,
                        onToggle = { openTopic = if (openTopic == topic.title) null else topic.title }
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideTopicRow(
    topic: GuideTopic,
    expanded: Boolean,
    primaryColor: Color,
    onToggle: () -> Unit
) {
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "guideChevron")
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = PageGutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(ListAvatarSize)
                    .background(primaryColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(topic.icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(topic.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(topic.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevron)
            )
        }
        if (expanded) {
            // Breathing room between the topic row and its tips
            Spacer(Modifier.height(8.dp))
            // Tips indented halfway between the icon edge (16 dp) and the title text (72 dp)
            Column(
                modifier = Modifier.padding(start = 44.dp, end = PageGutter, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topic.tips.forEach { tip ->
                    Row {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(4.dp)
                                .background(primaryColor, CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
