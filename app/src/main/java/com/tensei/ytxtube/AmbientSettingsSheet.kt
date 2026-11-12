package com.tensei.ytxtube

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** A small sample "video frame" so the preview shows the glow without needing a playing video. */
private fun previewFrame(): Bitmap {
    val colors = intArrayOf(
        0xFFFF5A36.toInt(), 0xFFFFB02E.toInt(), 0xFF2EC4FF.toInt(), 0xFF7B4DFF.toInt(),
        0xFFFF3D81.toInt(), 0xFF22D3A0.toInt(), 0xFF3B82F6.toInt(), 0xFFFF8A3D.toInt(),
        0xFF6D28D9.toInt(), 0xFFEF4444.toInt(), 0xFF0EA5E9.toInt(), 0xFFF59E0B.toInt(),
    )
    return Bitmap.createBitmap(colors, 4, 3, Bitmap.Config.ARGB_8888)
}

/**
 * The ambient light's own settings: how bright, how far it reaches, how soft and saturated it is,
 * how often it follows the video, and the fullscreen and status-bar extras. Everything applies live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientSettingsSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    FloatingSheet(onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 18.dp, bottom = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ambient light",
                    fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { AmbientSettings.resetTuning() }) { Text("Reset") }
            }
            Spacer(Modifier.height(10.dp))

            val frames = remember { AmbientFrames() }
            LaunchedEffect(Unit) { frames.push(previewFrame().asImageBitmap(), this) }
            val page = c.surfaceContainerLow
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(page),
            ) {
                Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                    drawAmbientGlow(frames, page, strength = 1f, statusBarPx = 0)
                }
                Text(
                    "Preview",
                    color = c.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            ToggleLine("Ambient light", "Glow behind the player", AmbientSettings.enabled, AmbientSettings::updateEnabled)
            val on = AmbientSettings.enabled
            TuneSlider("Intensity", "${(AmbientSettings.intensity * 100).roundToInt()}%", AmbientSettings.intensity, 0.2f..1f, on) { AmbientSettings.updateIntensity(it) }
            TuneSlider("Reach", "${(AmbientSettings.reach * 100).roundToInt()}%", AmbientSettings.reach, 0.5f..1.6f, on) { AmbientSettings.updateReach(it) }
            TuneSlider("Softness", "${AmbientSettings.blur}", AmbientSettings.blur.toFloat(), 1f..6f, on, steps = 4) { AmbientSettings.updateBlur(it.roundToInt()) }
            TuneSlider("Saturation", "${(AmbientSettings.saturation * 100).roundToInt()}%", AmbientSettings.saturation, 1f..2.5f, on) { AmbientSettings.updateSaturation(it) }
            TuneSlider(
                "Follow speed",
                "every ${"%.1f".format(AmbientSettings.intervalMs / 1000f)} s",
                // Faster is to the right, so the slider shows the inverse of the interval.
                5_500f - AmbientSettings.intervalMs, 500f..5_000f, on,
            ) { AmbientSettings.updateIntervalMs((5_500f - it).roundToInt()) }
            TuneSlider("Fade", "${"%.1f".format(AmbientSettings.fadeMs / 1000f)} s", AmbientSettings.fadeMs.toFloat(), 300f..3_000f, on) { AmbientSettings.updateFadeMs(it.roundToInt()) }

            ToggleLine("Status bar glow", "Light the status bar with the top edge of the picture", AmbientSettings.statusBar, AmbientSettings::updateStatusBar, on)
            ToggleLine("Fill fullscreen bands", "Colour the black bars around the video in fullscreen", AmbientSettings.fillBands, AmbientSettings::updateFillBands, on)
            TuneSlider("Band brightness", "${(AmbientSettings.fillDim * 100).roundToInt()}%", AmbientSettings.fillDim, 0.2f..1f, on && AmbientSettings.fillBands) { AmbientSettings.updateFillDim(it) }

            Spacer(Modifier.height(6.dp))
            Text(
                "The glow is read from the playing video a few times a second at a tiny size, so it costs almost nothing. It is switched off on the AMOLED theme and in music mode.",
                color = c.onSurfaceVariant, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ToggleLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (enabled) c.onSurface else c.onSurface.copy(alpha = 0.4f))
            Text(subtitle, fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun TuneSlider(
    label: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (enabled) c.onSurface else c.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
            Text(readout, fontSize = 13.sp, color = c.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps, enabled = enabled)
    }
}
