@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

// ── Notes page ────────────────────────────────────────────────────────────────

@Composable
fun NotesPage(
    notes: List<Note>,
    primaryColor: Color,
    onNoteClick: (Note) -> Unit,
    onNoteDelete: (Note) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp,
    bottomPadding: Dp = 16.dp
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 6.dp, bottom = bottomPadding)
    ) {
        if (notes.isNotEmpty()) {
            itemsIndexed(notes, key = { _, it -> it.id }) { index, note ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .animateItem()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNoteClick(note) }
                            .padding(horizontal = PageGutter, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatDate(note.date),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (note.title.isNotEmpty()) {
                                Text(
                                    text = note.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            Text(
                                text = note.content,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 25,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    if (index < notes.lastIndex) ListRowDivider(inset = 0.dp)
                }
            }
        } else {
            item {
                EmptyState(
                    icon = Icons.Default.StickyNote2,
                    message = "No notes yet.\nTap + to start tracking your progress."
                )
            }
        }
    }
}

// ── Note form (bottom sheet) ──────────────────────────────────────────────────

@Composable
fun NoteDialog(
    note: Note? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = note?.date ?: System.currentTimeMillis()
    )

    FormSheet(
        title = if (note == null) "Add Note" else "Edit Note",
        primaryColor = primaryColor,
        onDismiss = onDismiss,
        confirmEnabled = content.isNotBlank(),
        onConfirm = {
            onConfirm(title, content, datePickerState.selectedDateMillis ?: System.currentTimeMillis())
        },
        onDelete = if (note != null) onDelete else null
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Content") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 25,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        SheetDateRow(state = datePickerState, primaryColor = primaryColor)
    }
}
