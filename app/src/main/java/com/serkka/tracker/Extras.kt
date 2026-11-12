package com.serkka.tracker

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import kotlin.random.Random

// ── Extras ───────────────────────────────────────────────────────────────────
// Small extra lines and touches that only show up in specific moments. Where each one lives:
//  • Plate calculator: Ronnie Coleman quote when the plates overflow a real sleeve (StrengthTools)
//  • PB banner: ~1 in 10 PBs get a Ronnie quote as the title (PbCelebration)
//  • Pace calculator at 3:00 /km: "Kipchoge territory." (PaceCalculator)
//  • One-rep max ≥ 300 kg / a single (StrengthTools)
//  • Heart rate > 190 / < 45 bpm on the timer chip (HeartRateUi)
//  • 25,000+ steps today (SummaryPage)
//  • Timer past 3 hours (WorkoutTimerScreen)
//  • Tap the version at the bottom of Settings 7 times: rainbow accent for 10 s (SettingsPage + here)
//  • A workout on Christmas Eve / Christmas Day / New Year's Day: snowflake in the Calendar (CalendarPage)

internal val RONNIE_COLEMAN_QUOTES = listOf(
    "Yeah buddy!",
    "Light weight, baby!",
    "Light weight! Light weight!",
    "Yeah buddy! Light weight!",
    "Yep, yep!",
    "Woooo!",
    "Time to bleed!",
    "Ain't nothin' but a peanut!",
    "Ain't nothin' to it but to do it!",
    "Everybody wants to be a bodybuilder, but don't nobody wanna lift no heavy-ass weights!"
)

/** The PB banner title: usually "New personal best", now and then a (short) Ronnie quote. */
internal fun pbBannerTitle(): String =
    if (Random.nextInt(10) == 0) "“${RONNIE_COLEMAN_QUOTES.filter { it.length <= 32 }.random()}”"
    else "New personal best"

/** Christmas Eve, Christmas Day or New Year's Day. */
internal fun isHolidayDate(date: LocalDate): Boolean =
    (date.month == Month.DECEMBER && date.dayOfMonth in 24..25) || (date.month == Month.JANUARY && date.dayOfMonth == 1)

internal object Extras {
    private const val RAINBOW_MS = 10_000L
    private val scope = CoroutineScope(Dispatchers.Main)
    private var rainbowJob: Job? = null

    private val _rainbow = MutableStateFlow(false)
    /** True while the "Overclocked!" rainbow accent is running. */
    val rainbow: StateFlow<Boolean> = _rainbow

    /** Seven taps on the version in Settings: cycle the accent colour through the rainbow for 10 s. */
    fun overclock() {
        rainbowJob?.cancel()
        _rainbow.value = true
        rainbowJob = scope.launch {
            delay(RAINBOW_MS)
            _rainbow.value = false
        }
    }
}

/** [base] normally; a hue cycling through the rainbow while [Extras.overclock] runs. */
@Composable
internal fun withOverclockRainbow(base: Color): Color {
    val active by Extras.rainbow.collectAsState()
    if (!active) return base
    val hue by rememberInfiniteTransition(label = "overclock").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = LinearEasing)),
        label = "overclockHue"
    )
    return Color.hsv(hue, 0.6f, 1f)
}
