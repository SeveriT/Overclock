package com.serkka.tracker

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.score.Score
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
    /** The accent the user picked (swatches or hex, AccentPicker.kt). Use [rememberAccentColor] for the colour on screen. */
    val primaryColor: StateFlow<Color> = _primaryColor

    // Accent "Auto": follow the album art of the song in the music widget (falls back to primaryColor).
    // On by default; only an explicit Off in Settings is stored as false.
    private val _accentFromMusic = MutableStateFlow(prefs.getBoolean("accent_from_music", true))
    val accentFromMusic: StateFlow<Boolean> = _accentFromMusic

    /**
     * Colour of the album art of the song in the music widget, or null when there's none.
     * The listener re-sends the song every second with a fresh Bitmap, so the art is compared by
     * pixels and Palette only runs when it really changes, off the main thread.
     */
    val albumColors: StateFlow<AlbumColors?> = MediaRepository.getInstance().let { media ->
        combine(media.currentSong, media.widgetHidden, MusicApps.selected(application)) { song, hidden, app ->
            // Same rule as the widget itself (WorkoutScreen): the chosen app's song, widget not closed
            song.albumArt.takeIf { song.packageName == app.packageName && song.title != null && !hidden }
        }
    }
        .distinctUntilChanged { a, b -> a === b || (a != null && b != null && a.sameAs(b)) }
        .map { art -> art?.let(::albumSeedColor)?.let(::tonalColors) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The album art's accent (the tonal scheme's primary): drives accent Auto. Null when there's no art. */
    val albumColor: StateFlow<Color?> = albumColors
        .map { it?.primary }
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

/**
 * Colours made from one seed the way Material You (and the Metrolist app) makes them: a dark
 * Tonal Spot scheme, so they're balanced and readable whatever the album art looks like.
 * primary = the accent; the container pair = a calm body with text that reads on it.
 */
data class AlbumColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color
)

internal fun tonalColors(seedArgb: Int): AlbumColors {
    val scheme = SchemeTonalSpot(Hct.fromInt(seedArgb), true, 0.0, ColorSpec.SpecVersion.SPEC_2025)
    return AlbumColors(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer)
    )
}

/** Material's own fallback seed (Google Blue) for art with no usable colour, as Metrolist uses. */
private const val ART_FALLBACK_SEED = 0xFF4285F4.toInt()

/**
 * The seed colour of the album art, picked like Metrolist: up to 128 × 128 sampled pixels (mostly
 * transparent ones skipped), every exact colour counted (so small bright accents aren't merged into
 * greys), then ranked by Material's Score, which prefers colourful hues over large dull areas.
 */
private fun albumSeedColor(art: Bitmap): Int? = try {
    val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && art.config == Bitmap.Config.HARDWARE) {
        art.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        art
    }
    if (source == null || source.width <= 0 || source.height <= 0) {
        null
    } else {
        try {
            val w = minOf(source.width, 128)
            val h = minOf(source.height, 128)
            val counts = HashMap<Int, Int>()
            for (i in 0 until w * h) {
                val pixel = source.getPixel((i % w) * source.width / w, (i / w) * source.height / h)
                if (pixel ushr 24 >= 128) counts.merge(pixel or 0xFF000000.toInt(), 1, Int::plus)
            }
            if (counts.isEmpty()) ART_FALLBACK_SEED
            else Score.score(counts, fallbackColorArgb = ART_FALLBACK_SEED).first()
        } finally {
            if (source !== art) source.recycle()
        }
    }
} catch (_: Exception) {
    null   // e.g. a bitmap that can't be read
}

/**
 * The accent colour on screen: the album colour while Auto is on and a song with art is in the
 * music widget, otherwise the user's colour. Song changes fade over [Motion.LONG]; dragging the
 * picking a colour without music stays instant. Includes the Overclock rainbow (Extras.kt).
 */
@Composable
internal fun rememberAccentColor(themeViewModel: ThemeViewModel): Color {
    val base by themeViewModel.primaryColor.collectAsState()
    val auto by themeViewModel.accentFromMusic.collectAsState()
    val album by themeViewModel.albumColor.collectAsState()
    val fromMusic = auto && album != null
    // The album accent is already a readable tone (the scheme's primary)
    val target = if (fromMusic) album!! else base

    val animated = remember { Animatable(target) }
    var lastFromMusic by remember { mutableStateOf(fromMusic) }
    LaunchedEffect(target) {
        if (fromMusic || lastFromMusic) animated.animateTo(target, tween(Motion.LONG)) else animated.snapTo(target)
        lastFromMusic = fromMusic
    }
    val shown = if (fromMusic || lastFromMusic || animated.isRunning) animated.value else base
    return withOverclockRainbow(shown)
}
