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
    val Green             = Color(0xFF4AC067)   // success / positive
    val Blue              = Color(0xFF6693EB)   // info / tertiary

    // Semantic
    val Error             = Color(0xFFFF7043)   // orange-red error
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


private val TrackerTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
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
