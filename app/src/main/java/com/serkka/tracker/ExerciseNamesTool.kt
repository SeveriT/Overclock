package com.serkka.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

// ── Tools → Exercise names: bulk rename / merge ───────────────────────────────
// Renaming an exercise updates every logged entry with that exact name. Renaming to a
// name that already exists merges the two (handy for "Bench" / "bench press" variants).

internal data class ExerciseNameInfo(val name: String, val entries: Int, val lastDate: Long)

/** Distinct exercise names with entry counts, alphabetical so spelling variants sit together. */
internal fun exerciseNameInfos(workouts: List<Workout>): List<ExerciseNameInfo> =
    workouts.groupBy { it.exerciseName }
        .map { (name, list) -> ExerciseNameInfo(name, list.size, list.maxOf { it.date }) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

/** Filter field + one row per exercise name. Keys are prefixed "exname_". */
internal fun LazyListScope.exerciseNameItems(
    infos: List<ExerciseNameInfo>,
    filter: String,
    onFilterChange: (String) -> Unit,
    onPick: (ExerciseNameInfo) -> Unit
) {
    item(key = "exname_filter") {
        OutlinedTextField(
            value = filter,
            onValueChange = onFilterChange,
            placeholder = { Text("Filter exercises") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.isNotEmpty()) {
                    IconButton(onClick = { onFilterChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear filter")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .animateItem()
                .padding(horizontal = PageGutter)
                .padding(bottom = 8.dp)
        )
    }

    val shown = if (filter.isBlank()) infos
                else infos.filter { it.name.contains(filter.trim(), ignoreCase = true) }

    if (shown.isEmpty()) {
        item(key = "exname_empty") {
            Text(
                if (infos.isEmpty()) "No exercises logged yet." else "No exercises match \"${filter.trim()}\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.animateItem().padding(horizontal = PageGutter, vertical = 12.dp)
            )
        }
    }

    itemsIndexed(shown, key = { _, it -> "exname_${it.name}" }) { index, info ->
        Column(Modifier.fillMaxWidth().animateItem()) {
            ListItem(
                modifier = Modifier.clickable { onPick(info) },
                colors = flatListItemColors(),
                headlineContent = { Text(info.name, fontWeight = FontWeight.Medium) },
                supportingContent = {
                    Text(
                        "${if (info.entries == 1) "1 entry" else "${info.entries} entries"} · last ${formatDate(info.lastDate)}"
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename ${info.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            if (index < shown.lastIndex) ListRowDivider(inset = PageGutter)
        }
    }
}

/**
 * Rename dialog. If the new name matches another existing exercise (ignoring case), the
 * entries are merged into that exact existing name and the dialog says so up front.
 */
@Composable
internal fun ExerciseRenameDialog(
    info: ExerciseNameInfo,
    allInfos: List<ExerciseNameInfo>,
    primaryColor: Color,
    onRename: (newName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(info.name) { mutableStateOf(info.name) }
    val trimmed = text.trim()
    val mergeTarget = allInfos.firstOrNull {
        it.name != info.name && it.name.equals(trimmed, ignoreCase = true)
    }
    val finalName = mergeTarget?.name ?: trimmed
    val entriesText = if (info.entries == 1) "1 entry" else "${info.entries} entries"

    TrackerDialog(
        title = "Rename exercise",
        onDismiss = onDismiss,
        confirmLabel = if (mergeTarget != null) "Merge" else "Rename",
        confirmEnabled = trimmed.isNotEmpty() && finalName != info.name,
        onConfirm = {
            onRename(finalName)
            onDismiss()
        }
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Exercise name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        if (mergeTarget != null) {
            val targetEntries = if (mergeTarget.entries == 1) "1 entry" else "${mergeTarget.entries} entries"
            Text(
                "Merges $entriesText into \"${mergeTarget.name}\" ($targetEntries).",
                style = MaterialTheme.typography.bodyMedium,
                color = primaryColor
            )
        } else {
            Text(
                "Renames all $entriesText of \"${info.name}\".",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
