package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Live ambient light for the watch page.
 *
 * The technique (read a tiny frame off the playing video every couple of seconds, box-blur it,
 * saturate it, cross-fade it in and fade it into the page colour) is adapted from MeeTube's
 * AmbientGlow, https://github.com/yashoncode/meetube, MIT licensed, derived from SmartTube
 * (Copyright (c) 2020-present yuliskov). See THIRD_PARTY_NOTICES.md.
 */

/**
 * Everything about the ambient glow that can be tuned. Stored with the other appearance settings;
 * every value is read live by the sampler and the painter, so changes show immediately.
 */
object AmbientSettings {
    private const val PREFS = "appearance"

    const val DEFAULT_INTENSITY = 0.82f
    const val DEFAULT_REACH = 1.1f
    const val DEFAULT_BLUR = 3
    const val DEFAULT_SATURATION = 1.6f
    const val DEFAULT_INTERVAL_MS = 2_000
    const val DEFAULT_FADE_MS = 1_200
    const val DEFAULT_FILL_DIM = 0.6f

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set
    var intensity by mutableFloatStateOf(DEFAULT_INTENSITY)
        private set
    var reach by mutableFloatStateOf(DEFAULT_REACH)
        private set
    var blur by mutableIntStateOf(DEFAULT_BLUR)
        private set
    var saturation by mutableFloatStateOf(DEFAULT_SATURATION)
        private set
    var intervalMs by mutableIntStateOf(DEFAULT_INTERVAL_MS)
        private set
    var fadeMs by mutableIntStateOf(DEFAULT_FADE_MS)
        private set
    var fillBands by mutableStateOf(true)
        private set
    var fillDim by mutableFloatStateOf(DEFAULT_FILL_DIM)
        private set
    var statusBar by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean("ambient_light", true)
        intensity = p.getFloat("ambient_intensity", DEFAULT_INTENSITY).coerceIn(0.2f, 1f)
        reach = p.getFloat("ambient_reach", DEFAULT_REACH).coerceIn(0.5f, 1.6f)
        blur = p.getInt("ambient_blur", DEFAULT_BLUR).coerceIn(1, 6)
        saturation = p.getFloat("ambient_saturation", DEFAULT_SATURATION).coerceIn(1f, 2.5f)
        intervalMs = p.getInt("ambient_interval", DEFAULT_INTERVAL_MS).coerceIn(500, 5_000)
        fadeMs = p.getInt("ambient_fade", DEFAULT_FADE_MS).coerceIn(300, 3_000)
        fillBands = p.getBoolean("ambient_fill_bands", true)
        fillDim = p.getFloat("ambient_fill_dim", DEFAULT_FILL_DIM).coerceIn(0.2f, 1f)
        statusBar = p.getBoolean("ambient_status_bar", true)
    }

    private fun save(block: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit()?.apply(block)?.apply()
    }

    fun updateEnabled(value: Boolean) { enabled = value; save { putBoolean("ambient_light", value) } }
    fun updateIntensity(value: Float) { intensity = value.coerceIn(0.2f, 1f); save { putFloat("ambient_intensity", intensity) } }
    fun updateReach(value: Float) { reach = value.coerceIn(0.5f, 1.6f); save { putFloat("ambient_reach", reach) } }
    fun updateBlur(value: Int) { blur = value.coerceIn(1, 6); save { putInt("ambient_blur", blur) } }
    fun updateSaturation(value: Float) { saturation = value.coerceIn(1f, 2.5f); save { putFloat("ambient_saturation", saturation) } }
    fun updateIntervalMs(value: Int) { intervalMs = value.coerceIn(500, 5_000); save { putInt("ambient_interval", intervalMs) } }
    fun updateFadeMs(value: Int) { fadeMs = value.coerceIn(300, 3_000); save { putInt("ambient_fade", fadeMs) } }
    fun updateFillBands(value: Boolean) { fillBands = value; save { putBoolean("ambient_fill_bands", value) } }
    fun updateFillDim(value: Float) { fillDim = value.coerceIn(0.2f, 1f); save { putFloat("ambient_fill_dim", fillDim) } }
    fun updateStatusBar(value: Boolean) { statusBar = value; save { putBoolean("ambient_status_bar", value) } }

    /** Puts every tuning value back to its default; the on/off switch is left alone. */
    fun resetTuning() {
        updateIntensity(DEFAULT_INTENSITY)
        updateReach(DEFAULT_REACH)
        updateBlur(DEFAULT_BLUR)
        updateSaturation(DEFAULT_SATURATION)
        updateIntervalMs(DEFAULT_INTERVAL_MS)
        updateFadeMs(DEFAULT_FADE_MS)
        updateFillBands(true)
        updateFillDim(DEFAULT_FILL_DIM)
        updateStatusBar(true)
    }
}

private const val FRAME_W = 48
private const val FRAME_H = 27

/** The two most recent blurred video frames and the cross-fade between them. */
class AmbientFrames {
    var current by mutableStateOf<ImageBitmap?>(null)
        private set
    var previous by mutableStateOf<ImageBitmap?>(null)
        private set

    /** 0 = show [previous], 1 = show [current]. */
    val mix = Animatable(1f)
    private var fadeJob: Job? = null

    fun push(frame: ImageBitmap, scope: CoroutineScope) {
        previous = current
        current = frame
        fadeJob?.cancel()
        fadeJob = scope.launch {
            mix.snapTo(0f)
            val fade = AmbientSettings.fadeMs
            mix.animateTo(1f, tween(if (previous == null) fade / 2 else fade))
        }
    }

    fun clear() {
        fadeJob?.cancel()
        current = null
        previous = null
    }
}

/**
 * Samples the playing video while [active] and exposes the blurred frames. Nothing runs while the
 * video surface is not on screen, so a collapsed player or a background app costs nothing.
 */
@Composable
fun rememberAmbientFrames(active: Boolean): AmbientFrames {
    val frames = remember { AmbientFrames() }
    LaunchedEffect(active) {
        if (!active) {
            frames.clear()
            return@LaunchedEffect
        }
        while (isActive) {
            val view = SharedVideoTexture.view?.get()
            if (view != null && view.isAvailable && view.isShown) {
                val raw = runCatching { view.getBitmap(FRAME_W, FRAME_H) }.getOrNull()
                if (raw != null) {
                    val blurred = withContext(Dispatchers.Default) { blur(raw, AmbientSettings.blur) }
                    frames.push(blurred.asImageBitmap(), this)
                }
            }
            delay(AmbientSettings.intervalMs.toLong())
        }
    }
    return frames
}

/** Two separable box-blur passes: at 48x27 this is free and reads as a wide gaussian. */
private fun blur(source: Bitmap, radius: Int): Bitmap {
    val w = source.width
    val h = source.height
    val px = IntArray(w * h)
    val tmp = IntArray(w * h)
    source.getPixels(px, 0, w, 0, 0, w, h)
    repeat(2) {
        boxPass(px, tmp, radius, len = w, lines = h, step = 1, stride = w)
        boxPass(tmp, px, radius, len = h, lines = w, step = w, stride = 1)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

private fun boxPass(src: IntArray, dst: IntArray, radius: Int, len: Int, lines: Int, step: Int, stride: Int) {
    for (line in 0 until lines) {
        val base = line * stride
        for (i in 0 until len) {
            var r = 0
            var g = 0
            var b = 0
            var n = 0
            val from = maxOf(0, i - radius)
            val to = minOf(len - 1, i + radius)
            for (k in from..to) {
                val c = src[base + k * step]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
            }
            dst[base + i * step] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
    }
}

private fun saturationFilter(amount: Float): ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(amount) })

/**
 * Paints the glow from the top of the page: the video's colours spread down and fade into
 * [pageColor], so the title area sits on a soft light cast by the picture. With [statusBarPx] > 0
 * the strip behind the status bar is lit a little brighter, like the top edge of the picture.
 */
fun DrawScope.drawAmbientGlow(frames: AmbientFrames, pageColor: Color, strength: Float, statusBarPx: Int = 0) {
    val cur = frames.current ?: return
    if (strength <= 0f || size.width < 10f) return
    val pad = 30.dp.roundToPx()
    val height = minOf((size.width * AmbientSettings.reach).toInt(), (420 * AmbientSettings.reach / DEFAULT_REACH_DP).dp.roundToPx())
    val offset = IntOffset(-pad, 0)
    val dst = IntSize(size.width.toInt() + pad * 2, height)
    val mix = frames.mix.value
    val alpha = AmbientSettings.intensity
    val filter = saturationFilter(AmbientSettings.saturation)

    frames.previous?.takeIf { mix < 1f }?.let {
        drawImage(
            it,
            dstOffset = offset,
            dstSize = dst,
            alpha = (alpha * (1f - mix) * strength).coerceIn(0f, 1f),
            colorFilter = filter,
            filterQuality = FilterQuality.Medium,
        )
    }
    drawImage(
        cur,
        dstOffset = offset,
        dstSize = dst,
        alpha = (alpha * mix * strength).coerceIn(0f, 1f),
        colorFilter = filter,
        filterQuality = FilterQuality.Medium,
    )
    if (statusBarPx > 0) {
        // The top quarter of the frame, stretched over the status bar and a little below it.
        val band = statusBarPx + 10.dp.roundToPx()
        val srcH = (cur.height / 4).coerceAtLeast(1)
        drawImage(
            cur,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(cur.width, srcH),
            dstOffset = offset,
            dstSize = IntSize(size.width.toInt() + pad * 2, band),
            alpha = (0.55f * mix * strength).coerceIn(0f, 1f),
            colorFilter = filter,
            filterQuality = FilterQuality.Medium,
        )
    }
    drawRect(
        brush = Brush.verticalGradient(
            0f to pageColor.copy(alpha = 0f),
            0.55f to pageColor.copy(alpha = 0.35f),
            1f to pageColor,
            startY = 0f,
            endY = height.toFloat(),
        ),
        size = Size(size.width, height.toFloat()),
    )
}

private const val DEFAULT_REACH_DP = 1.1f

/**
 * Fills the black bands around the video in fullscreen with its own colours: the whole frame
 * stretched over the screen, saturated and dimmed. The video itself is drawn on top.
 */
fun DrawScope.drawAmbientFill(frames: AmbientFrames) {
    val cur = frames.current ?: return
    val mix = frames.mix.value
    val filter = saturationFilter(AmbientSettings.saturation * 0.95f)
    val dst = IntSize(size.width.toInt(), size.height.toInt())
    val dim = AmbientSettings.fillDim
    frames.previous?.takeIf { mix < 1f }?.let {
        drawImage(it, dstSize = dst, alpha = dim * (1f - mix), colorFilter = filter, filterQuality = FilterQuality.Medium)
    }
    drawImage(cur, dstSize = dst, alpha = dim * mix, colorFilter = filter, filterQuality = FilterQuality.Medium)
}
