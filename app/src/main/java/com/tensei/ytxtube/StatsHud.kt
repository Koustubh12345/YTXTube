package com.tensei.ytxtube

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val HudSamples = 60
private val HudGreen = Color(0xFF4ADE80)
private val HudAmber = Color(0xFFFBBF24)
private val HudBlue = Color(0xFF60A5FA)
private val HudViolet = Color(0xFFA78BFA)

/** Rows shown in the header or the graphs, so they are left out of the detail list. */
private val HudShownElsewhere = setOf("Resolution", "State", "Buffer health", "Bandwidth estimate", "Position")

/**
 * A floating "stats for nerds" panel over the video: resolution and state up top, live graphs for
 * network speed and buffer health (one sample per second, the last minute), and, when expanded,
 * every codec, bitrate and decoder row the player reports. Drag it anywhere; tap the header to
 * expand or collapse it. It draws nothing while [PlayerStats.visible] is off, and only samples
 * the player while it is showing.
 */
@Composable
fun StatsHud(modifier: Modifier = Modifier, fullscreen: Boolean = false) {
    if (!PlayerStats.visible) return

    var rows by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    val network = remember { mutableStateListOf<Float>() }
    val buffer = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            PlayerBridge.exo?.let { player ->
                rows = PlayerStats.read(player)
                network.push(PlaybackBandwidth.measuredKbps() / 1000f)
                buffer.push(player.totalBufferedDuration.coerceAtLeast(0L) / 1000f)
            }
            delay(1_000L)
        }
    }

    fun value(label: String): String? = rows.firstOrNull { it.first == label }?.second
    val state = value("State")
    val stateColor = when (state) {
        "Playing" -> HudGreen
        "Buffering" -> HudAmber
        else -> Color.White.copy(alpha = 0.55f)
    }

    var offset by remember { mutableStateOf(Offset.Zero) }
    var expanded by remember(fullscreen) { mutableStateOf(fullscreen) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset += drag
                }
            }
            .width(if (expanded) 284.dp else 244.dp)
            .animateContentSize()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xEA0B0F19), Color(0xCC0B0F19))))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().pressable(pressedScale = 0.99f) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(stateColor))
            Spacer(Modifier.width(7.dp))
            Text(
                "STATS FOR NERDS",
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))
                    .pressable { PlayerStats.set(false) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Close stats", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value("Resolution") ?: "Waiting for video",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (state != null) {
                Text(state, color = stateColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Graph(
                label = "NETWORK",
                readout = "%.1f Mbps".format(network.lastOrNull() ?: 0f),
                values = network,
                floor = 2f,
                color = HudBlue,
                modifier = Modifier.weight(1f),
            )
            Graph(
                label = "BUFFER",
                readout = "%.1f s".format(buffer.lastOrNull() ?: 0f),
                values = buffer,
                floor = 10f,
                color = HudViolet,
                modifier = Modifier.weight(1f),
            )
        }

        if (expanded) {
            val details = rows.filter { it.first !in HudShownElsewhere }
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    details.forEach { (label, text) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                label,
                                color = Color.White.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun MutableList<Float>.push(sample: Float) {
    add(sample)
    if (size > HudSamples) removeAt(0)
}

@Composable
private fun Graph(
    label: String,
    readout: String,
    values: List<Float>,
    floor: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(readout, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        Sparkline(values, floor, color, Modifier.fillMaxWidth().height(36.dp))
    }
}

/** A smooth filled line over the last [HudSamples] samples, scaled to the biggest value seen. */
@Composable
private fun Sparkline(values: List<Float>, floor: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val top = maxOf(values.max(), floor) * 1.1f
        val step = size.width / (HudSamples - 1)
        val startX = size.width - step * (values.size - 1)
        fun yOf(v: Float) = size.height - (v / top).coerceIn(0f, 1f) * size.height

        val line = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = startX + step * i
            val y = yOf(v)
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                val prevX = startX + step * (i - 1)
                val prevY = yOf(values[i - 1])
                val mid = (prevX + x) / 2f
                line.cubicTo(mid, prevY, mid, y, x, y)
                fill.cubicTo(mid, prevY, mid, y, x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.38f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 2.6.dp.toPx(), center = Offset(size.width, yOf(values.last())))
    }
}
