package com.tensei.ytxtube

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The loading animations to choose from (Tweaks > Look). The first is the standard Material ring. */
val SpinnerStyles = listOf("ring", "neon", "orbit", "orb", "squircle", "bars", "glitch")

fun spinnerLabel(style: String) = when (style) {
    "ring" -> "Classic ring"
    "neon" -> "Neon pulse"
    "orbit" -> "Orbiting dots"
    "orb" -> "Liquid orb"
    "squircle" -> "Squircle"
    "bars" -> "Equalizer"
    else -> "Glitch bar"
}

/**
 * The app's loading indicator. It draws the style chosen in YTX Tweaks, at the speed chosen there,
 * in [color] and about [size] square, so every loading spot in the app follows the same choice.
 */
@Composable
fun YtxSpinner(color: Color, size: Dp = 32.dp, modifier: Modifier = Modifier, style: String = Tweaks.spinnerStyle.value) {
    val speed = (Tweaks.spinnerSpeed.value / 100f).coerceIn(0.4f, 3f)
    val base = (1000 / speed).toInt().coerceAtLeast(200)
    if (style == "ring" || style !in SpinnerStyles) {
        CircularProgressIndicator(color = color, strokeWidth = (size.value / 8f).coerceIn(2f, 4f).dp, modifier = modifier.size(size))
        return
    }
    val t = rememberInfiniteTransition(label = "spinner")
    val turn by t.animateFloat(0f, 1f, infiniteRepeatable(tween(base, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(base, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(base * 2, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            when (style) {
                "neon" -> {
                    val r = s / 2f * (0.55f + 0.4f * pulse)
                    drawCircle(color.copy(alpha = 0.18f * (1f - pulse)), radius = s / 2f * (0.5f + 0.5f * pulse), center = c)
                    drawCircle(color, radius = r, center = c, style = Stroke(width = s / 10f))
                    drawCircle(color.copy(alpha = 0.35f), radius = r, center = c, style = Stroke(width = s / 5f))
                }
                "orbit" -> repeat(3) { i ->
                    val a = (turn + i / 3f) * 2f * PI.toFloat()
                    val rad = s / 2f - s / 9f
                    drawCircle(color.copy(alpha = 1f - i * 0.25f), radius = s / 9f, center = Offset(c.x + cos(a) * rad, c.y + sin(a) * rad))
                }
                "orb" -> {
                    val w = s * (0.45f + 0.25f * pulse)
                    val h = s * (0.7f - 0.25f * pulse)
                    drawOval(color, topLeft = Offset(c.x - w / 2f, c.y - h / 2f), size = Size(w, h))
                    drawCircle(color.copy(alpha = 0.3f), radius = s / 2f * (0.6f + 0.3f * pulse), center = c)
                }
                "squircle" -> rotate(turn * 360f, c) {
                    val side = s * 0.62f
                    drawRoundRect(color, topLeft = Offset(c.x - side / 2f, c.y - side / 2f), size = Size(side, side), cornerRadius = CornerRadius(side * 0.36f), style = Stroke(width = s / 9f, cap = StrokeCap.Round))
                }
                "bars" -> repeat(4) { i ->
                    val phase = (wave * 2f * PI.toFloat()) + i * 0.9f
                    val h = s * (0.3f + 0.6f * (0.5f + 0.5f * sin(phase)))
                    val bw = s / 7f
                    val x = c.x + (i - 1.5f) * bw * 1.6f
                    drawRoundRect(color, topLeft = Offset(x - bw / 2f, c.y - h / 2f), size = Size(bw, h), cornerRadius = CornerRadius(bw / 2f))
                }
                else -> { // glitch bar
                    val barH = s / 6f
                    val x = (turn * 1.4f - 0.2f) * s
                    drawRoundRect(color.copy(alpha = 0.25f), topLeft = Offset(0f, c.y - barH / 2f), size = Size(s, barH), cornerRadius = CornerRadius(barH / 2f))
                    drawRoundRect(color, topLeft = Offset(x.coerceIn(0f, s - s / 3f), c.y - barH / 2f), size = Size(s / 3f, barH), cornerRadius = CornerRadius(barH / 2f))
                    if (pulse > 0.8f) drawRect(color.copy(alpha = 0.6f), topLeft = Offset(s * 0.1f, c.y - barH * 1.2f), size = Size(s * 0.25f, barH * 0.4f))
                }
            }
        }
    }
}
