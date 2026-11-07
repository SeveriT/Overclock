package com.serkka.tracker

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp


object TrackerColors {
    // Backgrounds — M3 tonal surface ladder (depth via tone, not shadow)
    val Background        = Color(0xFF050505)   // deepest dark
    val SurfaceContainerLowest = Color(0xFF0A0A0A)
    val Surface           = Color(0xFF0F0F0F)   // slightly lifted
    val SurfaceContainerLow    = Color(0xFF111111)
    val SurfaceContainer  = Color(0xFF141414)   // panel surface
    val SurfaceBright     = Color(0xFF191919)   // hover / pressed (= surfaceContainerHigh)
    val SurfaceContainerHighest = Color(0xFF1F1F1F)

    // Primaries
    val Purple            = Color(0xFF8766EB)   // primary brand purple
    val PurpleLight       = Color(0xFFB49BF5)   // lighter purple tint
    val PurpleDark        = Color(0xFF5E42C8)   // pressed state
    val StravaOrange      = Color(0xFFC94100)   // Strava Orange
    val MusicWidgetColor       = Color(0xFFC94100)

    // Accents
    val Lime              = Color(0xFFECFE72)   // lime yellow — used for highlights
    val Cyan              = Color(0xFF72FEED)   // cyan accent
    val Green             = Color(0xFF4AC067)   // success / positive — the only green
    val Blue              = Color(0xFF6693EB)   // info / tertiary

    // Charts (used inside Canvas draw blocks, where MaterialTheme can't be read)
    val ChartGrid         = Color(0xFF424349)
    val ChartDot          = Color(0xFF24252B)   // hollow-centre of chart points

    // Third-party brand colours (music widget)
    val SpotifyGreen      = Color(0xFF1DB954)
    val SpotifyBlack      = Color(0xFF191414)

    // Semantic
    val Error             = Color(0xFFFF7043)   // orange-red error — the only red (negative / destructive)
    val ErrorContainer    = Color(0xFF3A1E16)
    val OnError           = Color(0xFFFFFFFF)

    // On-colors
    val OnBackground      = Color(0xFFFFFFFF)
    val OnSurface         = Color(0xFFFFFFFF)
    val OnSurfaceVariant  = Color(0xFFDEDEE0)   // muted label color
    val OnPrimary         = Color(0xFFFFFFFF)
    val OnSecondary       = Color(0xFF24252B)   // dark text on lime
    val OnTertiary        = Color(0xFF24252B)
    val Outline           = Color(0xFF5A5B63)
    val OutlineVariant    = Color(0xFF38393F)
}

// ── Dark color scheme ─────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    // Core
    primary              = TrackerColors.Purple,
    onPrimary            = TrackerColors.OnPrimary,
    primaryContainer     = TrackerColors.PurpleDark,
    onPrimaryContainer   = TrackerColors.PurpleLight,

    secondary            = TrackerColors.Lime,
    onSecondary          = TrackerColors.OnSecondary,
    secondaryContainer   = Color(0xFF3A3C1A),
    onSecondaryContainer = TrackerColors.Lime,

    tertiary             = TrackerColors.Cyan,
    onTertiary           = TrackerColors.OnTertiary,
    tertiaryContainer    = Color(0xFF153332),
    onTertiaryContainer  = TrackerColors.Cyan,

    // Surfaces
    background           = TrackerColors.Background,
    onBackground         = TrackerColors.OnBackground,
    surface              = TrackerColors.Surface,
    onSurface            = TrackerColors.OnSurface,
    surfaceVariant       = TrackerColors.SurfaceContainer,
    onSurfaceVariant     = TrackerColors.OnSurfaceVariant,
    surfaceContainerLowest  = TrackerColors.SurfaceContainerLowest,
    surfaceContainerLow     = TrackerColors.SurfaceContainerLow,
    surfaceContainer     = TrackerColors.SurfaceContainer,
    surfaceContainerHigh = TrackerColors.SurfaceBright,
    surfaceContainerHighest = TrackerColors.SurfaceContainerHighest,
    surfaceBright        = TrackerColors.SurfaceBright,
    surfaceDim           = TrackerColors.Background,

    // Error
    error                = TrackerColors.Error,
    onError              = TrackerColors.OnError,
    errorContainer       = TrackerColors.ErrorContainer,
    onErrorContainer     = TrackerColors.Error,

    // Outline
    outline              = TrackerColors.Outline,
    outlineVariant       = TrackerColors.OutlineVariant,

    // Inverse (for snackbars etc.)
    inverseSurface       = TrackerColors.OnSurface,
    inverseOnSurface     = TrackerColors.Background,
    inversePrimary       = TrackerColors.PurpleDark,

    scrim                = Color(0xFF000000),
)


// ── Type scale ────────────────────────────────────────────────────────────────
// Four working sizes (+ one display size for a single focal number per screen). Every M3
// role maps onto a tier, so screens can keep using semantic roles (titleSmall, bodySmall…)
// and still land on the same few sizes:
//   Display 45  — display*                       focal readout (pace calculator)
//   Number  24  — headline*, titleLarge          stat values, weight, sheet/dialog titles
//   Title   16  — titleMedium, bodyLarge         day headers, row titles
//   Body    14  — titleSmall, bodyMedium, labelLarge   details, buttons
//   Caption 12  — bodySmall, labelMedium, labelSmall   labels, dates, captions
// Tabular figures ("tnum") everywhere: digits share one width, so ticking timers, animated
// counters and number columns don't jitter or shift.
private const val TNUM = "tnum"

private fun tier(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = TNUM
)

private val TrackerTypography = Typography(
    displayLarge   = tier(45, 52, FontWeight.Bold, -0.25f),
    displayMedium  = tier(45, 52, FontWeight.Bold, -0.25f),
    displaySmall   = tier(45, 52, FontWeight.SemiBold, -0.25f),
    headlineLarge  = tier(24, 32, FontWeight.Bold, -0.2f),
    headlineMedium = tier(24, 32, FontWeight.Bold, -0.2f),
    headlineSmall  = tier(24, 32, FontWeight.SemiBold, -0.2f),
    titleLarge     = tier(24, 32, FontWeight.SemiBold, -0.2f),
    titleMedium    = tier(16, 24, FontWeight.Medium, 0.1f),
    bodyLarge      = tier(16, 24, FontWeight.Normal),
    titleSmall     = tier(14, 20, FontWeight.Medium, 0.1f),
    bodyMedium     = tier(14, 20, FontWeight.Normal),
    labelLarge     = tier(14, 20, FontWeight.SemiBold, 0.1f),
    bodySmall      = tier(12, 16, FontWeight.Normal),
    labelMedium    = tier(12, 16, FontWeight.Medium, 0.4f),
    labelSmall     = tier(12, 16, FontWeight.Medium, 0.4f)
)


/** Lift [base] toward white by [amount] (0–1), per sRGB channel. */
private fun lift(base: Color, amount: Float) = Color(
    red = base.red + (1f - base.red) * amount,
    green = base.green + (1f - base.green) * amount,
    blue = base.blue + (1f - base.blue) * amount
)

/**
 * Applies a user-chosen background and derives the whole tonal surface ladder from it, so
 * dialogs, menus, fields and sheets match a custom background instead of staying near-black.
 * The lift amounts reproduce the original ladder for the default #050505
 * (0A / 0F / 11 / 14 / 19 / 1E).
 */
private fun ColorScheme.withBackground(bg: Color) = copy(
    background              = bg,
    surfaceDim              = bg,
    inverseOnSurface        = bg,
    surfaceContainerLowest  = lift(bg, 0.02f),
    surface                 = lift(bg, 0.04f),
    surfaceContainerLow     = lift(bg, 0.05f),
    surfaceContainer        = lift(bg, 0.06f),
    surfaceVariant          = lift(bg, 0.06f),
    surfaceContainerHigh    = lift(bg, 0.08f),
    surfaceBright           = lift(bg, 0.08f),
    surfaceContainerHighest = lift(bg, 0.10f)
)

@Composable
fun TrackerTheme(
    primaryColor: Color = TrackerColors.Purple,
    backgroundColor: Color = TrackerColors.Background,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * fontScale
    )
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = DarkColorScheme.copy(primary = primaryColor).withBackground(backgroundColor),
            typography  = TrackerTypography,
            content     = content
        )
    }
}

// ── Convenience colour extensions ─────────────────────────────────────────────

/** Quick access to the lime accent without going through MaterialTheme. */
val MaterialTheme.lime: Color get() = TrackerColors.Lime

/** Quick access to the green success colour. */
val MaterialTheme.green: Color get() = TrackerColors.Green

/** Quick access to the cyan accent. */
val MaterialTheme.cyan: Color get() = TrackerColors.Cyan
