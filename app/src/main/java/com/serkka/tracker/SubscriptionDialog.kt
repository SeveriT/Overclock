package com.serkka.tracker

import android.app.Activity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SubscriptionDialog(
    onDismiss: () -> Unit,
    onSubscribe: () -> Unit,
    formattedPrice: String?,
    primaryColor: Color
) {
    val subscribeInteraction = remember { MutableInteractionSource() }
    val dismissInteraction = remember { MutableInteractionSource() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Unlock Premium",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Get access to all premium features:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PremiumFeatureRow(Icons.Default.Cloud, "Google Drive Backups", "Keep your data safe and synced")
                PremiumFeatureRow(Icons.Default.DirectionsRun, "Strava Integration", "Sync activities and track streaks")
                PremiumFeatureRow(Icons.Default.AutoAwesome, "AI Workout Assistant", "Generate personalized workout plans")

                Surface(
                    color = primaryColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${formattedPrice ?: "€2.00"} / month",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = primaryColor,
                        modifier = Modifier.padding(12.dp).fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubscribe,
                interactionSource = subscribeInteraction,
                modifier = Modifier.fillMaxWidth().bounceClick(subscribeInteraction),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Subscribe", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                interactionSource = dismissInteraction,
                modifier = Modifier.fillMaxWidth().bounceClick(dismissInteraction)
            ) { Text("Maybe Later") }
        }
    )
}

@Composable
private fun PremiumFeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
