@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AiAssistantPage(
    workoutViewModel: WorkoutViewModel,
    aiViewModel: AiViewModel,
    workouts: List<Workout>,
    primaryColor: Color,
    topPadding: Dp,
    bottomPadding: Dp,
    dailyLimit: Int = 20
) {
    val uiState by aiViewModel.uiState.collectAsState()
    val generatedWorkouts by aiViewModel.generatedWorkouts.collectAsState()
    val summary by aiViewModel.summary.collectAsState()
    val remainingRequests by aiViewModel.remainingRequests.collectAsState()

    var prompt by remember { mutableStateOf("") }
    var selectedChip by remember { mutableStateOf<String?>(null) }

    val quickPrompts = listOf(
        "Push Day" to "Generate a push day workout (chest, shoulders, triceps)",
        "Pull Day" to "Generate a pull day workout (back, biceps)",
        "Leg Day" to "Generate a leg day workout (quads, hamstrings, glutes, calves)",
        "Full Body" to "Generate a full body workout",
        "Upper Body" to "Generate an upper body workout",
        "Lower Body" to "Generate a lower body workout",
        "Core" to "Generate a core and abs workout",
        "4-Week Program" to "Generate a 4-week progressive training program with 4 sessions per week"
    )

    // Selected exercises to save
    val selectedWorkouts = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(generatedWorkouts) {
        selectedWorkouts.clear()
        generatedWorkouts.forEachIndexed { i, _ -> selectedWorkouts[i] = true }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topPadding + 16.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Quick prompts ────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("What would you like?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "$remainingRequests/$dailyLimit left today",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (remainingRequests <= 3) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEach { (label, fullPrompt) ->
                    FilterChip(
                        selected = selectedChip == label,
                        onClick = {
                            selectedChip = if (selectedChip == label) null else label
                            prompt = if (selectedChip == label) fullPrompt else ""
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                            selectedLabelColor = primaryColor
                        )
                    )
                }
            }
        }

        // ── Custom prompt ────────────────────────────────────────────────
        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = {
                    prompt = it
                    selectedChip = null
                },
                placeholder = { Text("Describe what you want...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )
        }

        // ── Generate button ──────────────────────────────────────────────
        item {
            val generateInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    if (prompt.isNotBlank()) {
                        aiViewModel.generateWorkout(prompt, workouts)
                    }
                },
                enabled = prompt.isNotBlank() && uiState !is AiUiState.Loading,
                interactionSource = generateInteraction,
                modifier = Modifier.fillMaxWidth().height(50.dp).bounceClick(generateInteraction),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState is AiUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating...")
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Workout")
                }
            }
        }

        // ── Error state ──────────────────────────────────────────────────
        if (uiState is AiUiState.Error) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            (uiState as AiUiState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ── Results ──────────────────────────────────────────────────────
        if (uiState is AiUiState.Success && generatedWorkouts.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = primaryColor.copy(alpha = 0.08f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Plan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = primaryColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${generatedWorkouts.size} exercises",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val allSelected = selectedWorkouts.values.all { it }
                    TextButton(onClick = {
                        val newValue = !allSelected
                        selectedWorkouts.keys.forEach { selectedWorkouts[it] = newValue }
                    }) {
                        Text(if (allSelected) "Deselect All" else "Select All")
                    }
                }
            }

            items(generatedWorkouts.size) { index ->
                val entry = generatedWorkouts[index]
                val isSelected = selectedWorkouts[index] ?: true

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.surfaceContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { selectedWorkouts[index] = it },
                            colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.exerciseName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                buildString {
                                    if (entry.sets > 0) append("${entry.sets} sets")
                                    if (entry.reps > 0) append(" x ${entry.reps} reps")
                                    if (entry.weight > 0) append(" @ ${formatWeight(entry.weight)}${entry.weightUnit}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (entry.notes.isNotBlank()) {
                                Text(
                                    entry.notes,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // ── Save button ──────────────────────────────────────────────
            item {
                val saveInteraction = remember { MutableInteractionSource() }
                val selectedCount = selectedWorkouts.count { it.value }
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        generatedWorkouts.forEachIndexed { i, entry ->
                            if (selectedWorkouts[i] == true) {
                                workoutViewModel.addWorkout(
                                    exercise = entry.exerciseName,
                                    sets = entry.sets,
                                    reps = entry.reps,
                                    weight = entry.weight,
                                    dateMillis = now,
                                    isPersonalBest = false,
                                    weightUnit = entry.weightUnit,
                                    notes = entry.notes
                                )
                            }
                        }
                        aiViewModel.clearState()
                        prompt = ""
                        selectedChip = null
                    },
                    enabled = selectedCount > 0,
                    interactionSource = saveInteraction,
                    modifier = Modifier.fillMaxWidth().height(50.dp).bounceClick(saveInteraction),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save $selectedCount exercise${if (selectedCount != 1) "s" else ""} to today")
                }
            }
        }
    }
}
