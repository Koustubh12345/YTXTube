package com.tensei.ytxtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.exoplayer.SeekParameters
import java.util.Locale

/**
 * Frame-by-frame stepping while paused, for checking a moment closely. Each step moves by [stepFrames]
 * frames of the playing video (its real frame rate when the stream reports it, 30 fps otherwise).
 * Steps are exact seeks, so on some streams a step lands a little early or late.
 */
object FrameStep {
    var enabled by mutableStateOf(false)
        private set

    var stepFrames by mutableIntStateOf(1)
        private set

    val stepSizes = listOf(1, 5, 10, 30)

    fun toggle(): Boolean {
        enabled = !enabled
        PlayerBridge.exo?.setSeekParameters(if (enabled) SeekParameters.EXACT else SeekParameters.DEFAULT)
        return enabled
    }

    fun setFramesPerStep(frames: Int) {
        stepFrames = frames.coerceIn(1, 120)
    }

    fun cycleStepSize(): Int {
        val idx = stepSizes.indexOf(stepFrames)
        stepFrames = stepSizes[(idx + 1) % stepSizes.size]
        return stepFrames
    }

    fun currentFps(): Float =
        PlayerBridge.exo?.videoFormat?.frameRate?.takeIf { it > 1f } ?: 30f

    fun frameDurationMs(fps: Float = currentFps()): Long =
        (1000f / fps.coerceAtLeast(1f)).toLong().coerceAtLeast(1L)

    fun estimatedFrameNumber(positionMs: Long, fps: Float = currentFps()): Long =
        ((positionMs.coerceAtLeast(0L) / 1000.0) * fps.coerceAtLeast(1f)).toLong()

    fun formatFrameReadout(positionMs: Long, fps: Float = currentFps()): String {
        val pos = positionMs.coerceAtLeast(0L)
        val min = pos / 60_000L
        val sec = (pos % 60_000L) / 1_000L
        val ms = pos % 1_000L
        val frame = estimatedFrameNumber(pos, fps)
        return String.format(Locale.US, "%02d:%02d.%03d · #F%d (%.0ffps)", min, sec, ms, frame, fps)
    }

    fun step(forward: Boolean, frames: Int = stepFrames) {
        val player = PlayerBridge.exo ?: return
        player.pause()
        player.setSeekParameters(SeekParameters.EXACT)
        val fps = currentFps()
        val deltaMs = frameDurationMs(fps) * frames.coerceAtLeast(1)
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + if (forward) deltaMs else -deltaMs).coerceIn(0L, duration))
    }
}
