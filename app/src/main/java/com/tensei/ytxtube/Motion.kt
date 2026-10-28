package com.tensei.ytxtube

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp


object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)
    val Morph = CubicBezierEasing(0.35f, 0f, 0.15f, 1.12f)
    const val PageEnterMs = 220
    const val PageRiseMs = 280
    const val PageExitMs = 90

    const val MorphMs = 480

    const val CollapseMs = 380

    const val ControlsHideMs = 2_000L

    const val ScrubChromeMs = 220

    const val HoldSpeedMs = 580L
}


@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) == 0f
    }
}

/** YouTube-ish blue, visible on light, dark and video surfaces. */
internal val TvFocusColor = Color(0xFF3EA6FF)

/**
 * Draws a focus ring around any surface and grows it slightly while focused, so a D-pad remote
 * can always tell where the selection is. Kept out of the touch path: it only reacts to focus.
 */
fun Modifier.tvFocusRing(
    radius: androidx.compose.ui.unit.Dp = 22.dp,
    color: Color = TvFocusColor,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (focused) {
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
                    style = Stroke(stroke),
                )
            }
        }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val tv = TvMode.isTv
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 1400f),
        label = "pressScale",
    )
    val layer = this.graphicsLayer { scaleX = scale; scaleY = scale }


    if (!enabled) return@composed layer
    layer
        .then(if (tv) Modifier.tvFocusRing() else Modifier)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { long -> { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); long() } },
            onClick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() },
        )
}
