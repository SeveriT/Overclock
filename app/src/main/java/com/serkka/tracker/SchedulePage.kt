@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Schedule (workout checklist) page ─────────────────────────────────────────

@Composable
fun SchedulePage(
    items: List<ScheduleItem>,
    primaryColor: Color,
    onAdd: (String) -> Unit,
    onToggle: (ScheduleItem) -> Unit,
    onRename: (ScheduleItem, String) -> Unit,
    onDelete: (ScheduleItem) -> Unit,
    onResetAll: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp,
    bottomPadding: Dp = 16.dp
) {
    var newText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }

    val doneCount = items.count { it.isDone }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 12.dp, start = 16.dp, end = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Progress + reset ──────────────────────────────────────────────────
        if (items.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$doneCount of ${items.size} done",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val resetInteraction = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = onResetAll,
                        interactionSource = resetInteraction,
                        enabled = doneCount > 0,
                        modifier = Modifier.bounceClick(resetInteraction)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }
        }

        // ── Add item ──────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    label = { Text("Add workout") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            if (newText.isNotBlank()) { onAdd(newText); newText = "" }
                        }
                    )
                )
                val addInteraction = remember { MutableInteractionSource() }
                FilledIconButton(
                    onClick = { if (newText.isNotBlank()) { onAdd(newText); newText = "" } },
                    enabled = newText.isNotBlank(),
                    interactionSource = addInteraction,
                    modifier = Modifier.bounceClick(addInteraction),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }

        // ── Items ───────────────────────────────────────────────────────────────
        if (items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Checklist,
                    message = "No workouts scheduled.\nAdd items above and tick them off.",
                    primaryColor = primaryColor
                )
            }
        } else {
            items(items, key = { it.id }) { item ->
                val interactionSource = remember { MutableInteractionSource() }
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .animateItem()
                        .bounceClick(
                            interactionSource = interactionSource,
                            onClick = { onToggle(item) },
                            onLongClick = { editing = item }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isDone,
                            onCheckedChange = { onToggle(item) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = primaryColor,
                                checkmarkColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val deleteInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { onDelete(item) },
                            interactionSource = deleteInteraction,
                            modifier = Modifier.bounceClick(deleteInteraction)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Rename dialog (long-press an item) ────────────────────────────────────
    editing?.let { item ->
        var text by remember(item.id) { mutableStateOf(item.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit item", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Workout") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            },
            confirmButton = {
                val saveInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        if (text.isNotBlank()) onRename(item, text)
                        editing = null
                    },
                    enabled = text.isNotBlank(),
                    interactionSource = saveInteraction,
                    modifier = Modifier.bounceClick(saveInteraction),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) { Text("Save") }
            },
            dismissButton = {
                val cancelInteraction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { editing = null },
                    interactionSource = cancelInteraction,
                    modifier = Modifier.bounceClick(cancelInteraction)
                ) { Text("Cancel") }
            }
        )
    }
}
