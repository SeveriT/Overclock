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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.clipPath
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

// ── Live graph inside the timer ring ─────────────────────────────────────────

/** Last 10 minutes of samples (SAMPLE_SECONDS apart) shown in the ring. */
private const val RING_GRAPH_SAMPLES = 10 * 60 / HeartRateMonitor.SAMPLE_SECONDS

/**
 * Faint live heart rate graph for the lower half of the timer ring: the running workout's
 * samples plus the current reading as the newest point, as a soft area clipped to the inside
 * of the ring. Drawing only, so lap taps on the ring pass straight through.
 * [ringStroke] is the ring's stroke width, so the graph stays clear of it.
 */
@Composable
internal fun RingHeartRateGraph(color: Color, ringStroke: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val recorded by HeartRateMonitor.recorded.collectAsState()
    val bpm by HeartRateMonitor.bpm.collectAsState()
    val points = remember(recorded, bpm) {
        val window = recorded.takeLast(RING_GRAPH_SAMPLES - 1) + (bpm ?: 0)
        if (window.count { it > 0 } >= 2) window else emptyList()
    }
    // Fades in once there's something to draw, out when the workout is saved or discarded
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (points.isEmpty()) 0f else 1f,
        animationSpec = tween(Motion.LONG),
        label = "ringHrAlpha"
    )
    // Keeps the last graph while it fades out (a plain holder: no state write during composition)
    val lastPoints = remember { arrayOf(points) }
    if (points.isNotEmpty()) lastPoints[0] = points
    val shown = lastPoints[0]
    if (alpha == 0f || shown.isEmpty()) return

    Canvas(modifier) {
        val radius = size.minDimension / 2f - ringStroke.toPx() - 6.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val circle = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), radius))
        }
        // Graph band: below the lap time down to near the bottom of the ring
        val top = cy + radius * 0.38f
        val bottom = cy + radius * 0.88f
        // Fixed 10-minute scale across the ring: grows in from the left, then scrolls. The ring's
        // curve hides roughly the first minute (the user prefers this to a stretched graph).
        val left = cx - radius
        val step = 2f * radius / (RING_GRAPH_SAMPLES - 1)

        val valid = shown.filter { it > 0 }
        val lo = valid.min().toFloat()
        val hi = valid.max().toFloat()
        val mid = (lo + hi) / 2f
        val span = maxOf(hi - lo + 10f, 20f)   // at least 20 bpm tall, so a steady pulse isn't a zigzag
        fun y(v: Int) = bottom - ((v - (mid - span / 2f)) / span) * (bottom - top)

        // One path per run of real readings (0 = no reading breaks the line)
        val runs = mutableListOf<List<Offset>>()
        var run = mutableListOf<Offset>()
        shown.forEachIndexed { i, v ->
            if (v > 0) run.add(Offset(left + i * step, y(v)))
            else if (run.isNotEmpty()) { runs.add(run); run = mutableListOf() }
        }
        if (run.isNotEmpty()) runs.add(run)

        clipPath(circle) {
            runs.filter { it.size >= 2 }.forEach { pts ->
                val line = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (k in 1 until pts.size) {
                        val a = pts[k - 1]; val b = pts[k]
                        val mx = (a.x + b.x) / 2f
                        cubicTo(mx, a.y, mx, b.y, b.x, b.y)   // smooth, never overshoots vertically
                    }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, cy + radius)
                    lineTo(pts.first().x, cy + radius)
                    close()
                }
                drawPath(
                    area,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.22f * alpha), Color.Transparent),
                        startY = top, endY = cy + radius
                    )
                )
                drawPath(
                    line,
                    color = color.copy(alpha = 0.45f * alpha),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
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
