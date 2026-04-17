package com.serkka.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiPiece(
    val startX: Float,
    val startY: Float,
    val vxBase: Float,
    val vyBase: Float,
    val rotationStart: Float,
    val rotationSpeed: Float,
    val color: Color,
    val size: Float,
    val lifetimeMs: Long,
    val spawnDelayMs: Long
)

private val ConfettiColors = listOf(
    Color(0xFFFFD166),
    Color(0xFFEF476F),
    Color(0xFF06D6A0),
    Color(0xFF118AB2),
    Color(0xFFFFB703),
    Color(0xFF9B5DE5)
)

@Composable
fun ConfettiOverlay(
    trigger: Int,
    modifier: Modifier = Modifier,
    durationMs: Long = 4200L,
    pieceCount: Int = 70
) {
    if (trigger == 0) return

    val pieces = remember(trigger) {
        List(pieceCount) {
            ConfettiPiece(
                startX = Random.nextFloat(),
                startY = -0.05f - Random.nextFloat() * 0.1f,
                vxBase = (Random.nextFloat() - 0.5f) * 0.6f,
                vyBase = 0.6f + Random.nextFloat() * 0.5f,
                rotationStart = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,
                color = ConfettiColors[Random.nextInt(ConfettiColors.size)],
                size = 6f + Random.nextFloat() * 6f,
                lifetimeMs = (durationMs * (0.75f + Random.nextFloat() * 0.25f)).toLong(),
                spawnDelayMs = (Random.nextFloat() * 250f).toLong()
            )
        }
    }

    var elapsedMs by remember(trigger) { mutableLongStateOf(0L) }

    LaunchedEffect(trigger) {
        val start = System.nanoTime()
        while (elapsedMs < durationMs + 300L) {
            awaitFrame()
            elapsedMs = (System.nanoTime() - start) / 1_000_000L
        }
    }

    if (elapsedMs >= durationMs + 300L) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val gravity = 1f

        pieces.forEach { p ->
            val localMs = elapsedMs - p.spawnDelayMs
            if (localMs <= 0 || localMs > p.lifetimeMs) return@forEach
            val t = localMs / 1000f

            val x = (p.startX + p.vxBase * t * 0.5f) * w
            val y = (p.startY + p.vyBase * t + 0.5f * gravity * t * t) * h
            if (y > h + 40f) return@forEach

            val fade = (1f - (localMs.toFloat() / p.lifetimeMs)).coerceIn(0f, 1f)
            val rotation = p.rotationStart + p.rotationSpeed * t
            val sizePx = p.size.dp.toPx()
            val wobble = sin((t * 6f + p.rotationStart).toDouble()).toFloat() * 8f

            rotate(degrees = rotation, pivot = Offset(x + wobble, y)) {
                drawRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(x + wobble - sizePx / 2f, y - sizePx / 4f),
                    size = androidx.compose.ui.geometry.Size(sizePx, sizePx / 2f)
                )
            }
        }
    }
}
