package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Colour picker dialog (layout after the usual web pickers): a saturation × brightness square,
 * a hue rainbow slider, and a row with the colour swatch, an editable hex code and a copy button.
 * Dragging updates the hex code; typing a valid code moves the handles.
 */
@Composable
private fun HexColorDialog(initial: Color, onDismiss: () -> Unit, onConfirm: (Color) -> Unit) {
    val context = LocalContext.current
    // HSV is the source of truth while dragging (hue survives at zero saturation / brightness)
    val start = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(hue, sat, value)
    var hexText by remember { mutableStateOf(initial.toHex().removePrefix("#")) }
    fun syncHex() { hexText = Color.hsv(hue, sat, value).toHex().removePrefix("#") }

    TrackerDialog(
        title = "Custom colour",
        onDismiss = onDismiss,
        confirmLabel = "Apply",
        onConfirm = { onConfirm(color) }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.animateContentSize()   // the warning below slides in and out
        ) {
            // ── Saturation (left → right) × brightness (top → bottom) ──
            val pureHue = Color.hsv(hue, 1f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(TrackerShapes.Medium)
                    .pointerInput(Unit) {
                        fun pick(pos: Offset) {
                            sat = (pos.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                            syncHex()
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            pick(down.position); down.consume()
                            drag(down.id) { change -> pick(change.position); change.consume() }
                        }
                    }
                    .drawBehind {
                        drawRect(pureHue)
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
                        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black)))
                        // Handle: white ring around the picked colour
                        val c = Offset(sat * size.width, (1f - value) * size.height)
                        drawCircle(Color.White, radius = 11.dp.toPx(), center = c)
                        drawCircle(color, radius = 7.dp.toPx(), center = c)
                    }
            )

            // ── Hue slider ──
            val hueColors = remember { (0..6).map { Color.hsv((it * 60f) % 360f, 1f, 1f) } }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        val inset = 10.dp.toPx()
                        fun pick(pos: Offset) {
                            hue = (((pos.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f) * 359.9f)
                            syncHex()
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            pick(down.position); down.consume()
                            drag(down.id) { change -> pick(change.position); change.consume() }
                        }
                    }
                    .drawBehind {
                        val inset = 10.dp.toPx()
                        val barH = 10.dp.toPx()
                        val top = (size.height - barH) / 2f
                        drawRoundRect(
                            Brush.horizontalGradient(hueColors, startX = inset, endX = size.width - inset),
                            topLeft = Offset(0f, top),
                            size = Size(size.width, barH),
                            cornerRadius = CornerRadius(barH / 2f)
                        )
                        val c = Offset(inset + hue / 360f * (size.width - 2 * inset), size.height / 2f)
                        drawCircle(Color.White, radius = 11.dp.toPx(), center = c)
                        drawCircle(pureHue, radius = 7.dp.toPx(), center = c)
                    }
            )

            // ── Swatch · # hex · copy ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TrackerShapes.Medium)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(12.dp))
                Text("#", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = hexText,
                    onValueChange = { new ->
                        hexText = new.removePrefix("#").filter { it.lowercaseChar() in "0123456789abcdef" }
                            .take(6).uppercase()
                        // A complete code moves the handles
                        parseHexColor(hexText)?.let { typed ->
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(typed.toArgb(), hsv)
                            if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]   // greys/black keep the current hue
                            sat = hsv[1]; value = hsv[2]
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Colour", color.toHex()))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy hex code", modifier = Modifier.size(18.dp))
                }
            }

            // ── Too-dark warning ──
            // The accent colours text, icons and charts on the near-black background. Below a 3:1
            // contrast ratio (the WCAG minimum for UI graphics) they get hard to see. Only a
            // warning: Apply still works.
            val background = MaterialTheme.colorScheme.background
            if (contrastRatio(color, background) < 3f) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "This colour is too dark for the app's dark background. Text, icons and charts in it will be hard to see.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** WCAG contrast ratio between two colours: 1 (none) to 21 (black on white). */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}
