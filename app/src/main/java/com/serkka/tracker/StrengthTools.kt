@file:OptIn(ExperimentalLayoutApi::class)

package com.serkka.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ── Strength maths ────────────────────────────────────────────────────────────

/**
 * Estimated one-rep max with the Epley formula: weight × (1 + reps / 30). A single is its own
 * 1RM. Accuracy drops above ~10 reps. Null for unusable input.
 */
internal fun estimatedOneRepMax(weight: Float, reps: Int): Float? = when {
    weight <= 0f || reps <= 0 -> null
    reps == 1 -> weight
    else -> weight * (1f + reps / 30f)
}

/** Estimated 1RM of a logged set, or null for time/hold exercises and sets without weight or reps. */
internal fun estimatedOneRepMax(w: Workout): Float? =
    if (w.weightUnit != "kg" || isTimeBasedExercise(w.exerciseName) || isHoldExercise(w.exerciseName)) null
    else estimatedOneRepMax(w.weight, w.reps)

/** Reps you can roughly do at [percent] of your 1RM (Epley solved for reps). */
private fun repsAtPercent(percent: Int): Int =
    if (percent >= 100) 1 else (30f * (100f / percent - 1f)).roundToInt().coerceAtLeast(1)

/** Rounds to the nearest [step] (0.5 kg for display, plate-friendly). */
private fun roundTo(value: Float, step: Float): Float = (value / step).roundToInt() * step

private data class BestSet(val exercise: String, val weight: Float, val reps: Int, val oneRepMax: Float)

/** Most-logged weighted exercises first, each with the set that gives the highest estimated 1RM. */
private fun bestSetsByExercise(workouts: List<Workout>): List<BestSet> =
    workouts.filter { estimatedOneRepMax(it) != null }
        .groupBy { it.exerciseName }
        .entries
        .sortedByDescending { it.value.size }
        .mapNotNull { (name, sets) ->
            val best = sets.maxByOrNull { estimatedOneRepMax(it) ?: 0f } ?: return@mapNotNull null
            BestSet(name, best.weight, best.reps, estimatedOneRepMax(best) ?: return@mapNotNull null)
        }

// ── One-rep max calculator (Tools) ────────────────────────────────────────────

private const val PREF_ORM_WEIGHT = "orm_weight"
private const val PREF_ORM_REPS = "orm_reps"
private val ORM_PERCENTAGES = listOf(95, 90, 85, 80, 75, 70, 65, 60)

/** Weight × reps → estimated 1RM plus a percentage table for planning working sets. */
@Composable
internal fun OneRepMaxContent(primaryColor: Color, workouts: List<Workout>) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context).tracker }
    var weight by remember { mutableStateOf(prefs.getString(PREF_ORM_WEIGHT, "100") ?: "100") }
    var reps by remember { mutableStateOf(prefs.getString(PREF_ORM_REPS, "5") ?: "5") }
    LaunchedEffect(weight, reps) {
        prefs.edit().putString(PREF_ORM_WEIGHT, weight).putString(PREF_ORM_REPS, reps).apply()
    }
    val repCount = reps.toIntOrNull()
    val oneRepMax = weight.toLeadFloat()?.let { w -> repCount?.let { estimatedOneRepMax(w, it) } }
    val bests = remember(workouts) { bestSetsByExercise(workouts).take(6) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter).padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Fill from your own best sets
        if (bests.isNotEmpty()) {
            Text("From your log", style = MaterialTheme.typography.labelLarge, color = muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bests.forEach { best ->
                    SuggestionChip(
                        onClick = { weight = formatWeight(best.weight); reps = best.reps.toString() },
                        label = { Text(best.exercise, maxLines = 1) }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericInput(
                value = weight, onValueChange = { weight = it }, label = "Weight (kg)",
                modifier = Modifier.weight(1f), step = 2.5f, allowDecimal = true
            )
            NumericInput(
                value = reps, onValueChange = { reps = it }, label = "Reps",
                modifier = Modifier.weight(1f), step = 1f
            )
        }

        // Result
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Estimated 1RM", style = MaterialTheme.typography.titleMedium, color = muted)
            Text(
                oneRepMax?.let { "${formatWeight(roundTo(it, 0.5f))} kg" } ?: "—",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            when {
                // Extra lines (Extras.kt)
                oneRepMax != null && oneRepMax >= 300f ->
                    Text("Someone call the federation.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = primaryColor)
                repCount == 1 ->
                    Text("That's not an estimate, that's a single.", style = MaterialTheme.typography.bodySmall, color = muted)
                repCount != null && repCount > 10 ->
                    Text("Less accurate above 10 reps", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }

        // Percentage table
        if (oneRepMax != null) {
            Column {
                ORM_PERCENTAGES.forEachIndexed { i, percent ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$percent %", style = MaterialTheme.typography.bodyLarge, color = muted, modifier = Modifier.width(64.dp))
                        Text(
                            "${formatWeight(roundTo(oneRepMax * percent / 100f, 0.5f))} kg",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text("~${repsAtPercent(percent)} reps", style = MaterialTheme.typography.bodyMedium, color = muted)
                    }
                    if (i < ORM_PERCENTAGES.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

// ── Plate calculator (Tools) ──────────────────────────────────────────────────

private const val PREF_PLATE_TARGET = "plate_target"
private const val PREF_PLATE_BAR = "plate_bar"
private const val PREF_PLATE_MAX = "plate_max"
private const val PREF_PLATE_TYPE = "plate_type"

/** Bumper or metal: changes only how the plates are drawn. */
private enum class PlateType(val label: String) { Bumper("Bumper"), Metal("Metal") }
private val MAX_PLATE_OPTIONS = listOf(25f, 20f)                     // gyms differ: some top out at 20 kg
private val PLATES = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f)   // standard kg plates
private val BARS = listOf(20f, 15f)                                  // men's and women's Olympic bar

/**
 * Standard bumper plate colours, so the drawing matches what's on the gym floor:
 * 25 red, 20 blue, 15 yellow, 10 green, 5 white; small change plates 2.5 and 1.25 chrome
 * (as in the user's gym; IWF would make 2.5 red).
 */
private val PLATE_COLORS = mapOf(
    25f to Color(0xFFD32F2F),
    20f to Color(0xFF1E63C6),
    15f to Color(0xFFF2C12E),
    10f to Color(0xFF2E9E4F),
    5f to Color(0xFFF2F2F2),
    2.5f to Color(0xFFB0B4BA),
    1.25f to Color(0xFFB0B4BA)
)

/**
 * Plates for one side of the bar, heaviest first, using plates up to [biggest] kg
 * (greedy works for standard plate sets).
 */
private fun platesPerSide(target: Float, bar: Float, biggest: Float): List<Float> {
    var remaining = (target - bar) / 2f
    val plates = mutableListOf<Float>()
    for (plate in PLATES.filter { it <= biggest }) {
        while (remaining >= plate - 0.001f) {
            plates += plate
            remaining -= plate
        }
    }
    return plates
}

/** Target weight + bar → which plates go on each side, with a drawing of the loaded sleeve. */
@Composable
internal fun PlateCalculatorContent(primaryColor: Color) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context).tracker }
    var target by remember { mutableStateOf(prefs.getString(PREF_PLATE_TARGET, "100") ?: "100") }
    // A saved bar that's no longer offered (the old 10 kg option) falls back to 20 kg
    var bar by remember { mutableFloatStateOf(prefs.getFloat(PREF_PLATE_BAR, 20f).takeIf { it in BARS } ?: 20f) }
    var biggest by remember { mutableFloatStateOf(prefs.getFloat(PREF_PLATE_MAX, 25f)) }
    var plateType by remember {
        mutableStateOf(PlateType.entries.find { it.name == prefs.getString(PREF_PLATE_TYPE, null) } ?: PlateType.Bumper)
    }
    LaunchedEffect(target, bar, biggest, plateType) {
        prefs.edit().putString(PREF_PLATE_TARGET, target).putFloat(PREF_PLATE_BAR, bar)
            .putFloat(PREF_PLATE_MAX, biggest).putString(PREF_PLATE_TYPE, plateType.name).apply()
    }
    val targetKg = target.toLeadFloat()
    val plates = targetKg?.takeIf { it >= bar }?.let { platesPerSide(it, bar, biggest) }
    val loaded = plates?.let { bar + 2f * it.sum() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter).padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NumericInput(
            value = target, onValueChange = { target = it }, label = "Target weight (kg)",
            modifier = Modifier.fillMaxWidth(), step = 2.5f, allowDecimal = true
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BARS.forEach { b ->
                FilterChip(
                    selected = bar == b,
                    onClick = { bar = b },
                    label = { Text("${formatWeight(b)} kg bar") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                        selectedLabelColor = primaryColor
                    )
                )
            }
        }
        // Bumper or metal plates
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text("Plates", style = MaterialTheme.typography.labelLarge, color = muted)
            PlateType.entries.forEach { type ->
                FilterChip(
                    selected = plateType == type,
                    onClick = { plateType = type },
                    label = { Text(type.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                        selectedLabelColor = primaryColor
                    )
                )
            }
        }

        // Biggest plate the gym has
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text("Biggest plate", style = MaterialTheme.typography.labelLarge, color = muted)
            MAX_PLATE_OPTIONS.forEach { kg ->
                FilterChip(
                    selected = biggest == kg,
                    onClick = { biggest = kg },
                    label = { Text("${formatWeight(kg)} kg") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                        selectedLabelColor = primaryColor
                    )
                )
            }
        }

        when {
            targetKg == null -> Unit
            plates == null -> Text("That's less than the ${formatWeight(bar)} kg bar.", color = muted)
            plates.isEmpty() -> Text("Just the bar.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            else -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Each side", style = MaterialTheme.typography.titleMedium, color = muted)
                    Text(
                        plates.joinToString(" + ") { formatWeight(it) },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    if (loaded != null && kotlin.math.abs(loaded - targetKg) > 0.001f) {
                        Text(
                            "Closest is ${formatWeight(loaded)} kg (${formatWeight(targetKg - loaded)} kg short)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted
                        )
                    }
                }
                LoadedSleeve(plates, plateType)
                // More plates than a real sleeve holds: the drawing has to stretch the bar.
                // A new quote each time it starts overflowing, steady while it stays that way.
                val overflowing = overflowsSleeve(plates, plateType)
                val quote = remember(overflowing) { RONNIE_COLEMAN_QUOTES.random() }
                if (overflowing) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "“$quote”",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor,
                            textAlign = TextAlign.Center
                        )
                        Text("— Ronnie Coleman", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }
        }
    }
}

// ── Drawing to scale ──────────────────────────────────────────────────────────
// Everything is in millimetres and scaled so a full 450 mm plate fills the drawing's height.

/** Pair = (diameter mm, thickness mm). Bumpers: 25–5 kg full 450 mm, only thickness differs. */
private val BUMPER_SHAPES = mapOf(
    25f to (450f to 82f),
    20f to (450f to 66f),
    15f to (450f to 52f),
    10f to (450f to 40f),
    5f to (450f to 28f),
    2.5f to (190f to 19f),     // small metal change plates
    1.25f to (160f to 15f)
)

/** Metal (iron / calibrated) plates: 25 and 20 kg full size, then each lighter plate smaller. */
private val METAL_SHAPES = mapOf(
    25f to (450f to 27f),
    20f to (450f to 23f),
    15f to (400f to 19f),
    10f to (325f to 17f),
    5f to (228f to 15f),
    2.5f to (190f to 13f),
    1.25f to (160f to 11f)
)

// Olympic bar end (mm)
private const val SHAFT_DIAMETER = 28f      // knurled grip
private const val COLLAR_DIAMETER = 70f     // the ring plates rest against
private const val COLLAR_WIDTH = 30f
private const val SLEEVE_DIAMETER = 50f
private const val SLEEVE_LENGTH = 415f      // loadable length
private const val SHAFT_SHOWN = 160f        // how much of the grip is drawn

/** Total thickness of [plates] in mm, plus a little room at the end cap. */
private fun loadedLengthMm(plates: List<Float>, type: PlateType): Float {
    val shapes = if (type == PlateType.Metal) METAL_SHAPES else BUMPER_SHAPES
    return plates.sumOf { ((shapes[it] ?: (300f to 20f)).second).toDouble() }.toFloat() + 20f
}

/** More plates than fit on a real sleeve (the drawing stretches the sleeve). */
private fun overflowsSleeve(plates: List<Float>, type: PlateType) = loadedLengthMm(plates, type) > SLEEVE_LENGTH

private val BAR_SHAFT = Color(0xFF8C9096)   // darker knurled steel
private val BAR_CHROME = Color(0xFFC9CDD2)  // polished sleeve
private val BAR_COLLAR = Color(0xFFAEB3BA)

/** One end of an Olympic bar, to scale: grip, collar, sleeve with the plates (heaviest inside), end cap. */
@Composable
private fun LoadedSleeve(plates: List<Float>, type: PlateType) {
    val shapes = if (type == PlateType.Metal) METAL_SHAPES else BUMPER_SHAPES
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val mm = size.height / 450f
        val centreY = size.height / 2f
        fun bar(x: Float, length: Float, diameter: Float, color: Color, radius: Float = 1.5f) =
            drawRoundRect(
                color = color,
                topLeft = Offset(x, centreY - diameter * mm / 2f),
                size = Size(length, diameter * mm),
                cornerRadius = CornerRadius(radius * mm)
            )

        // The loaded part (collar + sleeve) is centred; the sleeve grows if the plates need more room
        val sleeveLength = maxOf(SLEEVE_LENGTH, loadedLengthMm(plates, type)) * mm
        val collarX = (size.width - (COLLAR_WIDTH * mm + sleeveLength)) / 2f + SHAFT_SHOWN * mm / 2f
        val sleeveX = collarX + COLLAR_WIDTH * mm

        bar(collarX - SHAFT_SHOWN * mm, SHAFT_SHOWN * mm, SHAFT_DIAMETER, BAR_SHAFT)
        bar(sleeveX, sleeveLength, SLEEVE_DIAMETER, BAR_CHROME, radius = 6f)
        bar(sleeveX + sleeveLength - 8f * mm, 8f * mm, SLEEVE_DIAMETER, BAR_COLLAR, radius = 6f)   // end cap
        bar(collarX, COLLAR_WIDTH * mm, COLLAR_DIAMETER, BAR_COLLAR, radius = 4f)

        // Plates slide on against the collar, touching each other
        var x = sleeveX
        plates.forEach { kg ->
            val (diameter, thickness) = shapes[kg] ?: (300f to 20f)
            val w = thickness * mm
            val h = diameter * mm
            drawRoundRect(
                color = PLATE_COLORS[kg] ?: Color.Gray,
                topLeft = Offset(x, centreY - h / 2f),
                size = Size(w - 0.5.dp.toPx(), h),   // hairline between plates so they read separately
                cornerRadius = CornerRadius(minOf(6f * mm, w / 3f))
            )
            x += w
        }
    }
}
