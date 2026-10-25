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
    TrackerDialog(
        title = "Unlock Premium",
        onDismiss = onDismiss,
        confirmLabel = "Subscribe",
        onConfirm = onSubscribe,
        dismissLabel = "Maybe later",
        icon = Icons.Default.Star
    ) {
        Text("Get access to all premium features:")
        PremiumFeatureRow(Icons.Default.Cloud, "Google Drive Backups", "Keep your data safe and synced")
        Text(
            text = "${formattedPrice ?: "€2.00"} / month",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
    }
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
