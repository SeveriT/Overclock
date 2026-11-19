package com.serkka.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

// ── Accent colour picker (Settings → Appearance) ─────────────────────────────
// Preset swatches chosen to read well on the near-black background, plus a Custom swatch that
// opens a hex entry dialog. Replaced the old R/G/B sliders. This is the user's own colour; with
// accent Auto on, the album art colour takes over while a song is playing (ThemeViewModel).

private val ACCENT_PRESETS = listOf(
    "White" to Color(0xFFFFFFFF),
    "Red" to Color(0xFFFF5A5A),
    "Orange" to Color(0xFFFF9A3C),
    "Amber" to Color(0xFFFFC93D),
    "Lime" to Color(0xFFB5E550),
    "Green" to Color(0xFF4ADE80),
    "Teal" to Color(0xFF2DD4BF),
    "Sky" to Color(0xFF4FC3F7),
    "Blue" to Color(0xFF5B8CFF),
    "Violet" to Color(0xFFA78BFA),
    "Pink" to Color(0xFFF472B6)
)

/** "#4FA3FF" for [color] (alpha dropped). */
internal fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** Parses "4FA3FF" / "#4fa3ff" (exactly six hex digits) to an opaque colour, else null. */
internal fun parseHexColor(text: String): Color? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    return Color(0xFF000000 or hex.toLong(16))
}

@Composable
internal fun AccentColorPicker(current: Color, onPick: (Color) -> Unit) {
    val presetMatch = ACCENT_PRESETS.firstOrNull { it.second.toArgb() == current.toArgb() }
    var showHexDialog by remember { mutableStateOf(false) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ACCENT_PRESETS.forEach { (name, color) ->
            ColorSwatch(
                fill = Brush.linearGradient(listOf(color, color)),
                selected = presetMatch?.second == color,
                checkTint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                description = name,
                onClick = { onPick(color) }
            )
        }
        // Custom: the current colour when it isn't a preset, otherwise a rainbow; opens hex entry
        val isCustom = presetMatch == null
        ColorSwatch(
            fill = if (isCustom) Brush.linearGradient(listOf(current, current))
                   else Brush.sweepGradient(
                       listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                   ),
            selected = isCustom,
            checkTint = if (current.luminance() > 0.5f) Color.Black else Color.White,
            description = if (isCustom) "Custom colour ${current.toHex()}" else "Custom colour",
            icon = if (isCustom) null else Icons.Default.Edit,
            onClick = { showHexDialog = true }
        )
    }

    if (showHexDialog) {
        HexColorDialog(
            initial = current,
            onDismiss = { showHexDialog = false },
            onConfirm = { onPick(it); showHexDialog = false }
        )
    }
}

@Composable
private fun ColorSwatch(
    fill: Brush,
    selected: Boolean,
    checkTint: Color,
    description: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = description, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(4.dp)
            .clip(CircleShape)
            .background(fill)
    ) {
        when {
            selected -> Icon(Icons.Default.Check, contentDescription = description, tint = checkTint, modifier = Modifier.size(20.dp))
            icon != null -> Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** Hex entry with a live preview; Apply is enabled once six hex digits are typed. */
@Composable
private fun HexColorDialog(initial: Color, onDismiss: () -> Unit, onConfirm: (Color) -> Unit) {
    var text by remember { mutableStateOf(initial.toHex().removePrefix("#")) }
    val parsed = parseHexColor(text)
    TrackerDialog(
        title = "Custom colour",
        onDismiss = onDismiss,
        confirmLabel = "Apply",
        confirmEnabled = parsed != null,
        onConfirm = { parsed?.let(onConfirm) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(parsed ?: Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Spacer(Modifier.width(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    text = new.removePrefix("#").filter { it.lowercaseChar() in "0123456789abcdef" }
                        .take(6).uppercase()
                },
                prefix = { Text("#") },
                placeholder = { Text("4FA3FF") },
                singleLine = true,
                isError = text.length == 6 && parsed == null,
                supportingText = { Text("Six hex digits, e.g. FF9A3C") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { parsed?.let(onConfirm) }),
                textStyle = MaterialTheme.typography.titleMedium
            )
        }
    }
}
