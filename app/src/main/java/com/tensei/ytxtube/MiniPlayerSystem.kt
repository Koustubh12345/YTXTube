package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * MINIPLAYER SYSTEM — Features 71 to 80
 *
 * 71. Docked glass miniplayer
 * 72. Draggable floating pill
 * 73. Compact floating video card
 * 74. Audio-only waveform player
 * 75. Customisable miniplayer controls
 * 76. Smooth player transitions
 * 77. Miniplayer appearance settings
 * 78. Configurable swipe interactions
 * 79. Persistent playback state
 * 80. Custom miniplayer presets
 */

enum class MiniPlayerPreset(val label: String) {
    DockedGlass("Docked Liquid Glass"),
    FloatingPill("Floating Glass Pill"),
    FloatingVideoCard("Floating Video Card"),
    WaveformCapsule("Waveform Audio Capsule")
}

enum class MiniPlayerSwipeAction(val label: String) {
    Dismiss("Close Player"),
    SkipNext("Next Track"),
    Previous("Previous Track"),
    Seek10s("Skip 10 Seconds"),
    None("Disabled")
}

object MiniPlayerSystem {
    private const val PREFS = "miniplayer_system_prefs"
    private var prefs: SharedPreferences? = null

    // 80. Active Preset
    var activePreset by mutableStateOf(MiniPlayerPreset.DockedGlass)
        private set

    // 75. Customizable Controls Toggles
    var showNextButton by mutableStateOf(true)
        private set
    var showPrevButton by mutableStateOf(false)
        private set
    var showSeek10Button by mutableStateOf(false)
        private set

    // 77. Appearance Settings
    var miniPlayerHeightDp by mutableIntStateOf(66)
        private set
    var miniPlayerCornerRadiusDp by mutableIntStateOf(20)
        private set
    var miniPlayerOpacity by mutableFloatStateOf(0.70f)
        private set

    // 78. Configurable Swipe Interactions
    var swipeLeftAction by mutableStateOf(MiniPlayerSwipeAction.SkipNext)
        private set
    var swipeRightAction by mutableStateOf(MiniPlayerSwipeAction.Previous)
        private set
    var swipeDownAction by mutableStateOf(MiniPlayerSwipeAction.Dismiss)
        private set

    // Floating position coordinates (for floating presets)
    var floatingX by mutableFloatStateOf(24f)
    var floatingY by mutableFloatStateOf(160f)

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }

        val presetName = p.getString("preset", MiniPlayerPreset.DockedGlass.name)
        activePreset = runCatching { MiniPlayerPreset.valueOf(presetName ?: "") }.getOrDefault(MiniPlayerPreset.DockedGlass)

        showNextButton = p.getBoolean("show_next", true)
        showPrevButton = p.getBoolean("show_prev", false)
        showSeek10Button = p.getBoolean("show_seek10", false)

        miniPlayerHeightDp = p.getInt("height_dp", 66)
        miniPlayerCornerRadiusDp = p.getInt("radius_dp", 20)
        miniPlayerOpacity = p.getFloat("opacity", 0.70f)

        val swLeft = p.getString("swipe_left", MiniPlayerSwipeAction.SkipNext.name)
        swipeLeftAction = runCatching { MiniPlayerSwipeAction.valueOf(swLeft ?: "") }.getOrDefault(MiniPlayerSwipeAction.SkipNext)

        val swRight = p.getString("swipe_right", MiniPlayerSwipeAction.Previous.name)
        swipeRightAction = runCatching { MiniPlayerSwipeAction.valueOf(swRight ?: "") }.getOrDefault(MiniPlayerSwipeAction.Previous)

        val swDown = p.getString("swipe_down", MiniPlayerSwipeAction.Dismiss.name)
        swipeDownAction = runCatching { MiniPlayerSwipeAction.valueOf(swDown ?: "") }.getOrDefault(MiniPlayerSwipeAction.Dismiss)
    }

    fun updatePreset(preset: MiniPlayerPreset) {
        activePreset = preset
        prefs?.edit()?.putString("preset", preset.name)?.apply()
    }

    fun updateControls(showNext: Boolean, showPrev: Boolean, showSeek10: Boolean) {
        showNextButton = showNext
        showPrevButton = showPrev
        showSeek10Button = showSeek10
        prefs?.edit()
            ?.putBoolean("show_next", showNext)
            ?.putBoolean("show_prev", showPrev)
            ?.putBoolean("show_seek10", showSeek10)
            ?.apply()
    }

    fun updateAppearance(heightDp: Int, radiusDp: Int, opacity: Float) {
        miniPlayerHeightDp = heightDp.coerceIn(54, 84)
        miniPlayerCornerRadiusDp = radiusDp.coerceIn(10, 36)
        miniPlayerOpacity = opacity.coerceIn(0.2f, 0.95f)
        prefs?.edit()
            ?.putInt("height_dp", miniPlayerHeightDp)
            ?.putInt("radius_dp", miniPlayerCornerRadiusDp)
            ?.putFloat("opacity", miniPlayerOpacity)
            ?.apply()
    }

    fun updateSwipeActions(left: MiniPlayerSwipeAction, right: MiniPlayerSwipeAction, down: MiniPlayerSwipeAction) {
        swipeLeftAction = left
        swipeRightAction = right
        swipeDownAction = down
        prefs?.edit()
            ?.putString("swipe_left", left.name)
            ?.putString("swipe_right", right.name)
            ?.putString("swipe_down", down.name)
            ?.apply()
    }
}

/**
 * Feature 74: Audio-Only Waveform Player View
 * Renders animated neon audio frequency bars when playing audio-only.
 */
@Composable
fun AudioWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val phase = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            phase.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            phase.snapTo(0f)
        }
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / (barCount * 1.6f)
        val gap = barWidth * 0.6f
        val maxHeight = size.height * 0.85f

        for (i in 0 until barCount) {
            val progress = phase.value
            val wave = if (isPlaying) {
                val seed = (i * 0.4f + progress * 6.28f)
                (kotlin.math.sin(seed.toDouble()).toFloat() * 0.5f + 0.5f)
            } else {
                0.2f
            }
            val h = (wave * maxHeight).coerceAtLeast(4.dp.toPx())
            val x = i * (barWidth + gap) + gap / 2f
            val y = (size.height - h) / 2f

            drawRoundRect(
                color = color.copy(alpha = 0.85f),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
