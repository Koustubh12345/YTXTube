package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * GESTURE DESIGNER — Features 61 to 70
 *
 * 61. Custom double-tap seeking duration
 * 62. Left-side brightness gestures
 * 63. Right-side volume gestures
 * 64. Custom gesture sensitivity
 * 65. Two-finger pinch-to-zoom
 * 66. Video panning
 * 67. Custom gesture assignments
 * 68. Swipe-to-dismiss interactions
 * 69. Animated gesture HUD
 * 70. Separate gesture profiles for video and Shorts
 */

enum class DoubleTapSeekStep(val seconds: Int, val label: String) {
    Sec5(5, "5 seconds"),
    Sec10(10, "10 seconds"),
    Sec15(15, "15 seconds"),
    Sec20(20, "20 seconds"),
    Sec30(30, "30 seconds"),
    Sec60(60, "60 seconds")
}

enum class TapAction(val label: String) {
    ToggleControls("Show / Hide Controls"),
    PlayPause("Play / Pause Toggle"),
    ToggleStats("Show Stats HUD"),
    Bookmark("Save Bookmark"),
    None("Disabled")
}

data class GestureStateHud(
    val icon: ImageVector,
    val title: String,
    val progress: Float, // 0f to 1f
    val textValue: String
)

object GestureDesigner {
    private const val PREFS = "gesture_designer_prefs"
    private var prefs: SharedPreferences? = null

    // 61. Double Tap Seek Duration
    var doubleTapSeekStep by mutableStateOf(DoubleTapSeekStep.Sec10)
        private set

    // 62 & 63. Brightness & Volume Swipe
    var brightnessSwipeEnabled by mutableStateOf(true)
        private set
    var volumeSwipeEnabled by mutableStateOf(true)
        private set

    // 64. Gesture Sensitivity Multiplier (0.5f to 2.0f)
    var gestureSensitivityMultiplier by mutableFloatStateOf(1.0f)
        private set

    // 65 & 66. Pinch zoom and video panning
    var pinchZoomEnabled by mutableStateOf(true)
        private set
    var videoPanningEnabled by mutableStateOf(true)
        private set

    // 67. Custom Gesture Assignments
    var singleTapAction by mutableStateOf(TapAction.ToggleControls)
        private set
    var longPressAction by mutableStateOf(TapAction.None)
        private set

    // 68. Swipe to Dismiss
    var swipeToDismissEnabled by mutableStateOf(true)
        private set

    // 69. Active Gesture HUD State
    var activeGestureHud by mutableStateOf<GestureStateHud?>(null)

    // 70. Separate Profiles for Video vs Shorts
    var shortsSwipeSensitivity by mutableFloatStateOf(1.0f)
        private set
    var shortsVolumeSwipeEnabled by mutableStateOf(false) // Disable on shorts to avoid conflict with vertical scroll
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }

        val stepSec = p.getInt("seek_step_sec", 10)
        doubleTapSeekStep = DoubleTapSeekStep.entries.find { it.seconds == stepSec } ?: DoubleTapSeekStep.Sec10

        brightnessSwipeEnabled = p.getBoolean("brightness_swipe", true)
        volumeSwipeEnabled = p.getBoolean("volume_swipe", true)
        gestureSensitivityMultiplier = p.getFloat("gesture_sens", 1.0f)
        pinchZoomEnabled = p.getBoolean("pinch_zoom", true)
        videoPanningEnabled = p.getBoolean("video_panning", true)
        swipeToDismissEnabled = p.getBoolean("swipe_dismiss", true)

        val sta = p.getString("single_tap_act", TapAction.ToggleControls.name)
        singleTapAction = runCatching { TapAction.valueOf(sta ?: "") }.getOrDefault(TapAction.ToggleControls)

        val lpa = p.getString("long_press_act", TapAction.None.name)
        longPressAction = runCatching { TapAction.valueOf(lpa ?: "") }.getOrDefault(TapAction.None)

        shortsSwipeSensitivity = p.getFloat("shorts_sens", 1.0f)
        shortsVolumeSwipeEnabled = p.getBoolean("shorts_vol_swipe", false)
    }

    fun updateSeekStep(step: DoubleTapSeekStep) {
        doubleTapSeekStep = step
        prefs?.edit()?.putInt("seek_step_sec", step.seconds)?.apply()
    }

    fun updateBrightnessSwipe(enabled: Boolean) {
        brightnessSwipeEnabled = enabled
        prefs?.edit()?.putBoolean("brightness_swipe", enabled)?.apply()
    }

    fun updateVolumeSwipe(enabled: Boolean) {
        volumeSwipeEnabled = enabled
        prefs?.edit()?.putBoolean("volume_swipe", enabled)?.apply()
    }

    fun updateSensitivity(sens: Float) {
        gestureSensitivityMultiplier = sens.coerceIn(0.5f, 2.0f)
        prefs?.edit()?.putFloat("gesture_sens", gestureSensitivityMultiplier)?.apply()
    }

    fun updatePinchZoom(enabled: Boolean) {
        pinchZoomEnabled = enabled
        prefs?.edit()?.putBoolean("pinch_zoom", enabled)?.apply()
    }

    fun updateVideoPanning(enabled: Boolean) {
        videoPanningEnabled = enabled
        prefs?.edit()?.putBoolean("video_panning", enabled)?.apply()
    }

    fun updateSwipeToDismiss(enabled: Boolean) {
        swipeToDismissEnabled = enabled
        prefs?.edit()?.putBoolean("swipe_dismiss", enabled)?.apply()
    }

    fun updateSingleTapAction(action: TapAction) {
        singleTapAction = action
        prefs?.edit()?.putString("single_tap_act", action.name)?.apply()
    }

    fun updateShortsSettings(sens: Float, volSwipe: Boolean) {
        shortsSwipeSensitivity = sens
        shortsVolumeSwipeEnabled = volSwipe
        prefs?.edit()
            ?.putFloat("shorts_sens", sens)
            ?.putBoolean("shorts_vol_swipe", volSwipe)
            ?.apply()
    }
}

/**
 * Feature 69: Animated Liquid Gesture HUD Composable
 * Sleek glass capsule badge displaying volume, brightness, or seek progress.
 */
@Composable
fun AnimatedGestureHudBadge(
    hud: GestureStateHud,
    modifier: Modifier = Modifier
) {
    val accentHex = LiquidGlassThemeEngine.activeNeonHex ?: SEED_CREAM_ROSE
    val accentColor = runCatching {
        Color(android.graphics.Color.parseColor(accentHex))
    }.getOrDefault(MaterialTheme.colorScheme.primary)

    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.75f))
            .border(1.dp, accentColor.copy(alpha = 0.5f), shape)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = hud.icon,
                    contentDescription = hud.title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.width(120.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hud.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = hud.textValue,
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(hud.progress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor)
                    )
                }
            }
        }
    }
}
