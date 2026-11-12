package com.serkka.tracker

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ── Live heart rate chip (timer ring) ────────────────────────────────────────

/**
 * Small tappable readout: beating heart + BPM while a strap is connected, otherwise a
 * "Heart rate" prompt. Tapping opens [HeartRateSheet].
 */
@Composable
internal fun HeartRateChip(primaryColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val state by HeartRateMonitor.state.collectAsState()
    val bpm by HeartRateMonitor.bpm.collectAsState()
    val live = state is HeartRateMonitor.State.Connected && bpm != null
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // Heart beats along with the reading
    val beat = remember { Animatable(1f) }
    LaunchedEffect(live) {
        while (live) {
            val rate = HeartRateMonitor.bpm.value ?: break
            beat.animateTo(1.2f, tween(90))
            beat.animateTo(1f, tween(160))
            delay((60_000L / rate - 250L).coerceAtLeast(100L))
        }
        beat.snapTo(1f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(TrackerShapes.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            if (state is HeartRateMonitor.State.Idle) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
            contentDescription = "Heart rate monitor",
            tint = if (live) primaryColor else muted,
            modifier = Modifier.size(18.dp).scale(beat.value)
        )
        Spacer(Modifier.width(6.dp))
        when {
            live -> {
                Text("$bpm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // Extra lines at the extremes (Extras.kt)
                val rate = bpm ?: 0
                when {
                    rate > 190 -> Text("  Breathe!", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = primaryColor)
                    rate < 45 -> Text("  Cyclist or asleep?", style = MaterialTheme.typography.labelLarge, color = muted)
                    else -> Text(" bpm", style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
            state is HeartRateMonitor.State.Connected ->
                Text("--", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = muted)
            state is HeartRateMonitor.State.Idle ->
                Text("Heart rate", style = MaterialTheme.typography.labelLarge, color = muted)
            else ->
                Text("Connecting…", style = MaterialTheme.typography.labelLarge, color = muted)
        }
    }
}

// ── Strap picker ─────────────────────────────────────────────────────────────

/** Bottom sheet to find, connect, disconnect and forget a Bluetooth heart rate strap. */
@Composable
internal fun HeartRateSheet(primaryColor: Color, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(HeartRateMonitor.hasPermissions(context)) }
    var bluetoothOn by remember { mutableStateOf(HeartRateMonitor.isBluetoothOn(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    val state by HeartRateMonitor.state.collectAsState()
    val bpm by HeartRateMonitor.bpm.collectAsState()
    val scanning by HeartRateMonitor.scanning.collectAsState()
    val devices by HeartRateMonitor.devices.collectAsState()
    var savedName by remember { mutableStateOf(HeartRateMonitor.savedDeviceName(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermissions = HeartRateMonitor.hasPermissions(context)
        permissionDenied = !hasPermissions
        bluetoothOn = HeartRateMonitor.isBluetoothOn(context)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bluetoothOn = HeartRateMonitor.isBluetoothOn(context) }

    // Start looking for straps as soon as we're allowed to, unless one is already connected
    LaunchedEffect(hasPermissions, bluetoothOn) {
        if (hasPermissions && bluetoothOn && state is HeartRateMonitor.State.Idle) {
            HeartRateMonitor.startScan(context)
        }
    }
    DisposableEffect(Unit) { onDispose { HeartRateMonitor.stopScan(context) } }

    FormSheet(
        title = "Heart Rate Monitor",
        primaryColor = primaryColor,
        onDismiss = onDismiss,
        onConfirm = onDismiss,
        confirmLabel = "Done",
        dismissLabel = "Close"
    ) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        when {
            !HeartRateMonitor.isSupported(context) ->
                Text("This phone doesn't support Bluetooth Low Energy.", color = muted)

            !hasPermissions -> {
                Text(
                    "Overclock needs Bluetooth access to find your heart rate strap and read it during workouts.",
                    color = muted
                )
                if (permissionDenied) {
                    // Android won't show the prompt again after a "Don't allow"; send them to settings
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Open app settings") }
                }
                Button(
                    onClick = { permissionLauncher.launch(HeartRateMonitor.requiredPermissions()) },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = MaterialTheme.colorScheme.surface)
                ) { Text("Allow Bluetooth", fontWeight = FontWeight.SemiBold) }
            }

            !bluetoothOn -> {
                Text("Bluetooth is off.", color = muted)
                Button(
                    onClick = { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = MaterialTheme.colorScheme.surface)
                ) { Text("Turn on Bluetooth", fontWeight = FontWeight.SemiBold) }
            }

            else -> {
                // ── Current / remembered strap ─────────────────────────────
                val name = when (val s = state) {
                    is HeartRateMonitor.State.Connected -> s.name
                    is HeartRateMonitor.State.Connecting -> s.name
                    is HeartRateMonitor.State.Reconnecting -> s.name
                    HeartRateMonitor.State.Idle -> savedName
                }
                if (name != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (state) {
                                    is HeartRateMonitor.State.Connected -> bpm?.let { "Connected · $it bpm" } ?: "Connected · waiting for a reading"
                                    is HeartRateMonitor.State.Connecting -> "Connecting…"
                                    is HeartRateMonitor.State.Reconnecting -> "Waiting for the strap…"
                                    HeartRateMonitor.State.Idle -> "Not connected"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = muted
                            )
                        }
                        if (state is HeartRateMonitor.State.Idle) {
                            TextButton(onClick = { HeartRateMonitor.reconnectSaved(context) }) { Text("Connect", color = primaryColor) }
                        } else {
                            TextButton(onClick = { HeartRateMonitor.disconnect(context) }) { Text("Disconnect", color = primaryColor) }
                        }
                        TextButton(onClick = {
                            HeartRateMonitor.disconnect(context, forget = true)
                            savedName = null
                            HeartRateMonitor.startScan(context)
                        }) { Text("Forget", color = muted) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // ── Nearby straps ──────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Nearby straps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (scanning) {
                        CircularProgressIndicator(color = primaryColor, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        TextButton(onClick = { HeartRateMonitor.startScan(context) }) { Text("Scan", color = primaryColor) }
                    }
                }
                val others = devices.filterNot { it.name == name && state !is HeartRateMonitor.State.Idle }
                if (others.isEmpty()) {
                    Text(
                        buildString {
                            append(if (scanning) "Looking for straps… " else "No straps found. ")
                            append("Put the strap on: most only broadcast when they feel a heartbeat.")
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) append(" Location must be on to scan on this Android version.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted
                    )
                } else {
                    others.forEach { device ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(TrackerShapes.Small)
                                .clickable {
                                    HeartRateMonitor.connect(context, device)
                                    savedName = device.name
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(device.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(signalLabel(device.rssi), style = MaterialTheme.typography.labelMedium, color = muted)
                        }
                    }
                }
            }
        }
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -60 -> "Strong"
    rssi >= -80 -> "OK"
    else -> "Weak"
}

// ── Heart rate graph (Sessions) ──────────────────────────────────────────────

/**
 * Heart rate over a session: one point per [HeartRateMonitor.SAMPLE_SECONDS], gaps where the
 * strap had no reading, dashed average line, min/max labels on the left, time along the bottom.
 */
@Composable
internal fun HeartRateChart(samples: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val readings = remember(samples) { samples.filter { it > 0 } }
    if (readings.isEmpty()) return
    val minHr = readings.min()
    val maxHr = readings.max()
    val avg = readings.average().toFloat()
    val lo = (minHr - 5).coerceAtLeast(0)
    val hi = maxHr + 5
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val totalSeconds = samples.size.toLong() * HeartRateMonitor.SAMPLE_SECONDS

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 11.sp.toPx()
            setColor(labelColor.toArgb())
        }
        val labelW = 30.dp.toPx()
        val bottomH = 18.dp.toPx()
        val chartLeft = labelW
        val chartW = size.width - labelW
        val chartH = size.height - bottomH
        fun x(i: Int) = chartLeft + if (samples.size > 1) i / (samples.size - 1f) * chartW else 0f
        fun y(v: Float) = chartH - (v - lo) / (hi - lo).toFloat() * chartH

        // Min / max gridlines with labels
        listOf(maxHr, minHr).forEach { v ->
            val yy = y(v.toFloat())
            drawLine(TrackerColors.ChartGrid, Offset(chartLeft, yy), Offset(size.width, yy), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("$v", 0f, yy + paint.textSize / 3f, paint)
        }
        // Average
        val avgY = y(avg)
        drawLine(
            color.copy(alpha = 0.6f), Offset(chartLeft, avgY), Offset(size.width, avgY),
            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )

        // Line, broken where there was no reading
        val path = Path()
        var penDown = false
        samples.forEachIndexed { i, v ->
            if (v > 0) {
                if (penDown) path.lineTo(x(i), y(v.toFloat())) else path.moveTo(x(i), y(v.toFloat()))
                penDown = true
            } else penDown = false
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Time axis: start, middle, end
        val baseline = size.height - 2.dp.toPx()
        paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText("0:00", chartLeft, baseline, paint)
        paint.textAlign = android.graphics.Paint.Align.CENTER
        drawContext.canvas.nativeCanvas.drawText(formatElapsed(totalSeconds / 2), chartLeft + chartW / 2f, baseline, paint)
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(formatElapsed(totalSeconds), size.width, baseline, paint)
    }
}
