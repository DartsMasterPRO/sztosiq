package com.melodia.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Spectrum analyzer – słupki od basu (lewo) do sopranów (prawo). */
@Composable
fun SpectrumBars(bars: FloatArray, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (bars.isEmpty()) return@Canvas
        val gap = size.width / bars.size * 0.28f
        val w = size.width / bars.size - gap
        val brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)))
        bars.forEachIndexed { i, v ->
            val h = (v * size.height).coerceAtLeast(w.coerceAtMost(4f))
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * (w + gap) + gap / 2, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2, w / 2)
            )
        }
    }
}

/** Kształt fali dźwiękowej. */
@Composable
fun WaveLine(wave: FloatArray, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val mid = size.height / 2
        if (wave.size < 2) {
            drawLine(color.copy(alpha = 0.4f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 3f)
            return@Canvas
        }
        val path = Path()
        val step = size.width / (wave.size - 1)
        wave.forEachIndexed { i, v ->
            val x = i * step
            val y = mid - v * mid * 0.95f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

/** Animowane tło: kolorowe, powoli płynące plamy światła, pulsujące z basem. */
@Composable
fun AnimatedBlobs(colors: List<Color>, pulse: Float, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "blobs").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "t"
    )
    Canvas(modifier) {
        val base = size.minDimension
        colors.forEachIndexed { i, c ->
            val phase = t + i * 2.1f
            val cx = size.width * (0.5f + 0.35f * cos(phase * (1f + i * 0.3f)))
            val cy = size.height * (0.4f + 0.3f * sin(phase * (0.8f + i * 0.25f)))
            val r = base * (0.55f + 0.1f * i) * (1f + pulse * 0.25f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }
    }
}
