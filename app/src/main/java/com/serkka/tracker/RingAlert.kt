package com.serkka.tracker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ── Ring end alert ────────────────────────────────────────────────────────────
// When a timer ring runs out: a 1 s vibration and, if headphones are connected, the chosen sound in
// them (never out of the phone speaker in the gym). The beep briefly ducks the music (Spotify
// lowers its volume) instead of pausing it. Fired by TimerForegroundService, so it also works with
// the screen off or the app swiped away. The sound is picked in Settings → Timer.

/** The ring end sounds to choose from (system tones, so no audio files in the APK). */
internal enum class BeepSound(val label: String, val tone: Int?) {
    DoubleBeep("Double beep", ToneGenerator.TONE_PROP_BEEP2),
    Beep("Beep", ToneGenerator.TONE_PROP_BEEP),
    Pips("Pips", ToneGenerator.TONE_CDMA_PIP),
    Chime("Chime", ToneGenerator.TONE_PROP_ACK),
    Alert("Alert", ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD),
    Off("Off", null)
}

internal object RingAlert {
    private const val PREF_SOUND = "ring_beep_sound"

    fun sound(context: Context): BeepSound {
        val name = PreferencesManager.getInstance(context).tracker.getString(PREF_SOUND, null)
        return BeepSound.entries.firstOrNull { it.name == name } ?: BeepSound.DoubleBeep
    }

    fun setSound(context: Context, sound: BeepSound) {
        PreferencesManager.getInstance(context).tracker.edit().putString(PREF_SOUND, sound.name).apply()
    }

    /** Ring ran out: buzz, plus the chosen sound in the headphones. */
    fun fire(context: Context) {
        vibrate(context)
        val tone = sound(context).tone ?: return
        if (headphonesConnected(context)) beep(context, tone)
    }

    /** Settings preview: plays [sound] wherever audio goes now (speaker too), as the user asked for it. */
    fun preview(context: Context, sound: BeepSound) {
        sound.tone?.let { beep(context, it) }
    }

    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun headphonesConnected(context: Context): Boolean {
        val types = mutableSetOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) types += AudioDeviceInfo.TYPE_HEARING_AID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) types += AudioDeviceInfo.TYPE_BLE_HEADSET
        val audio = context.getSystemService(AudioManager::class.java)
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    }

    /** [tone] on the media stream (= the headphones), with the music ducked meanwhile. */
    private fun beep(context: Context, tone: Int) {
        try {
            val audio = context.getSystemService(AudioManager::class.java)
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            audio.requestAudioFocus(focus)
            val generator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
            generator.startTone(tone, 900)   // capped: some system tones would otherwise repeat
            Handler(Looper.getMainLooper()).postDelayed({
                generator.release()
                audio.abandonAudioFocusRequest(focus)
            }, 1_000L)
        } catch (e: RuntimeException) {
            Log.w("RingAlert", "Beep failed", e)   // ToneGenerator can fail if audio is busy
        }
    }
}

/** Settings → Timer: the ring end sound. Tapping a choice selects it and plays it once. */
@Composable
internal fun RingBeepPicker(primaryColor: Color) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(RingAlert.sound(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ring End Sound", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BeepSound.entries.forEach { sound ->
                FilterChip(
                    selected = sound == selected,
                    onClick = {
                        selected = sound
                        RingAlert.setSound(context, sound)
                        RingAlert.preview(context, sound)
                    },
                    label = { Text(sound.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                        selectedLabelColor = primaryColor
                    )
                )
            }
        }
        Text(
            "Plays in your headphones when a ring runs out, along with the buzz. Never from the phone speaker during a workout.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
