package com.serkka.tracker

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager.getInstance(application).theme

    // Default color is now White (0xFFFFFFFF)
    private val _primaryColor = MutableStateFlow(Color(prefs.getInt("primary_color", Color.White.toArgb())))
    /** The accent the user picked with the RGB sliders. Use [rememberAccentColor] for the colour on screen. */
    val primaryColor: StateFlow<Color> = _primaryColor

    // Accent "Auto": follow the album art of the song in the music widget (falls back to primaryColor)
    private val _accentFromMusic = MutableStateFlow(prefs.getBoolean("accent_from_music", false))
    val accentFromMusic: StateFlow<Boolean> = _accentFromMusic

    /**
     * Colour of the album art of the Spotify song in the music widget, or null when there's none.
     * The listener re-sends the song every second with a fresh Bitmap, so the art is compared by
     * pixels and Palette only runs when it really changes, off the main thread.
     */
    val albumColor: StateFlow<Color?> = MediaRepository.getInstance().let { media ->
        combine(media.currentSong, media.widgetHidden) { song, hidden ->
            // Same rule as the widget itself (WorkoutScreen): a Spotify song, widget not closed
            song.albumArt.takeIf { song.packageName == "com.spotify.music" && song.title != null && !hidden }
        }
    }
        .distinctUntilChanged { a, b -> a === b || (a != null && b != null && a.sameAs(b)) }
        .map { art -> art?.let(::albumArtColor) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _fontScale = MutableStateFlow(prefs.getFloat("font_scale", 1f))
    val fontScale: StateFlow<Float> = _fontScale

    private val _backgroundColor = MutableStateFlow(
        Color(prefs.getInt("background_color", TrackerColors.Background.toArgb()))
    )
    val backgroundColor: StateFlow<Color> = _backgroundColor

    fun updatePrimaryColor(color: Color) {
        _primaryColor.value = color
        prefs.edit().putInt("primary_color", color.toArgb()).apply()
    }

    fun updateAccentFromMusic(on: Boolean) {
        _accentFromMusic.value = on
        prefs.edit().putBoolean("accent_from_music", on).apply()
    }

    /** Channels are clamped to [BACKGROUND_MAX_CHANNEL] so the app's white text stays readable. */
    fun updateBackgroundColor(color: Color) {
        val safe = Color(
            red = color.red.coerceAtMost(BACKGROUND_MAX_CHANNEL),
            green = color.green.coerceAtMost(BACKGROUND_MAX_CHANNEL),
            blue = color.blue.coerceAtMost(BACKGROUND_MAX_CHANNEL)
        )
        _backgroundColor.value = safe
        prefs.edit().putInt("background_color", safe.toArgb()).apply()
    }

    fun resetBackgroundColor() = updateBackgroundColor(TrackerColors.Background)

    companion object {
        /** Max per-channel brightness for the background (0.08 ≈ #141414): keeps the app a
         *  near-black dark theme, well clear of contrast problems with the white text. */
        const val BACKGROUND_MAX_CHANNEL = 0.08f
    }

    fun updateFontScale(scale: Float) {
        _fontScale.value = scale
        prefs.edit().putFloat("font_scale", scale).apply()
    }
}

/** The album art's vibrant colour (muted, then dominant as fallbacks); the music widget's tint. */
private fun albumArtColor(art: Bitmap): Color? = try {
    val palette = Palette.from(art).maximumColorCount(16).generate()
    val rgb = palette.getVibrantColor(palette.getMutedColor(palette.getDominantColor(0)))
    if (rgb != 0) Color(rgb) else null
} catch (_: Exception) {
    null   // e.g. a hardware bitmap that can't be read
}

/** Album colours can be very dark: lift the lightness so the accent stays readable on the dark theme. */
private fun readableAccent(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = hsl[2].coerceIn(0.6f, 0.85f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * The accent colour on screen: the album colour while Auto is on and a song with art is in the
 * music widget, otherwise the user's colour. Song changes fade over [Motion.LONG]; dragging the
 * RGB sliders without music stays instant. Includes the Overclock rainbow (Extras.kt).
 */
@Composable
internal fun rememberAccentColor(themeViewModel: ThemeViewModel): Color {
    val base by themeViewModel.primaryColor.collectAsState()
    val auto by themeViewModel.accentFromMusic.collectAsState()
    val album by themeViewModel.albumColor.collectAsState()
    val fromMusic = auto && album != null
    val target = if (fromMusic) readableAccent(album!!) else base

    val animated = remember { Animatable(target) }
    var lastFromMusic by remember { mutableStateOf(fromMusic) }
    LaunchedEffect(target) {
        if (fromMusic || lastFromMusic) animated.animateTo(target, tween(Motion.LONG)) else animated.snapTo(target)
        lastFromMusic = fromMusic
    }
    val shown = if (fromMusic || lastFromMusic || animated.isRunning) animated.value else base
    return withOverclockRainbow(shown)
}
