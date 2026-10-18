package com.serkka.tracker

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager.getInstance(application).theme

    // Default color is now White (0xFFFFFFFF)
    private val _primaryColor = MutableStateFlow(Color(prefs.getInt("primary_color", Color.White.toArgb())))
    val primaryColor: StateFlow<Color> = _primaryColor

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
