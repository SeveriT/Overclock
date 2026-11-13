package com.serkka.tracker

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ── App icon changer ──────────────────────────────────────────────────────────
// Android can't swap an app's icon at runtime, so each icon is its own launcher entry: an
// <activity-alias> in the manifest pointing at OverclockActivity, with exactly one enabled.
// The red default keeps the old name ".MainActivity" so existing home-screen shortcuts survive.
// The icons are the app's bumper plate in the standard plate colours (red 25, blue 20,
// yellow 15, green 10), generated from the original artwork.

internal enum class AppIcon(val alias: String, val label: String, val foreground: Int) {
    Red(".MainActivity", "25 kg", R.mipmap.app_logo_foreground),
    Blue(".IconBlue", "20 kg", R.mipmap.app_logo_foreground_blue),
    Yellow(".IconYellow", "15 kg", R.mipmap.app_logo_foreground_yellow),
    Green(".IconGreen", "10 kg", R.mipmap.app_logo_foreground_green);

    fun component(context: Context) = ComponentName(context.packageName, "com.serkka.tracker$alias")
}

internal object AppIconChanger {
    /** The icon whose launcher entry is enabled now (Red when nothing was ever changed). */
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull {
            pm.getComponentEnabledSetting(it.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppIcon.Red   // DEFAULT state = manifest values, where only Red is enabled
    }

    /**
     * Switches the launcher icon. The new entry is enabled before the old ones are disabled,
     * so there's never a moment without a launcher entry. DONT_KILL_APP keeps the app open.
     */
    fun set(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            icon.component(context),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(
                it.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}

/** Settings → Appearance: the four plate icons, tap one to make it the launcher icon. */
@Composable
internal fun AppIconPicker(primaryColor: Color) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppIconChanger.current(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("App Icon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppIcon.entries.forEach { icon ->
                val isSelected = icon == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(TrackerShapes.Small)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                if (!isSelected) {
                                    AppIconChanger.set(context, icon)
                                    selected = icon
                                }
                            }
                        )
                        .padding(8.dp)
                ) {
                    // Same look as the launcher: just the plate (the icon has no background)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .border(2.dp, if (isSelected) primaryColor else Color.Transparent, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                    ) {
                        // Foreground art is 108 dp with the plate in the middle 72 dp: scale so the plate fills the circle
                        Image(
                            painter = painterResource(icon.foreground),
                            contentDescription = "${icon.name} plate icon",
                            modifier = Modifier.requiredSize(66.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        icon.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            "Your launcher can take a few seconds to show the new icon. Some launchers remove the home-screen shortcut when it changes; add it back from the app drawer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
