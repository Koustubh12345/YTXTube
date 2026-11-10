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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch


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

/**
 * TV: focus ring for every plain `clickable` (provided as LocalIndication in the theme). Ripple is
 * disabled app-wide, so without this a focused row/avatar showed nothing.
 */
object TvFocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 0

    private class Node(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
        private var focused = false
        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect {
                    focused = when (it) {
                        is FocusInteraction.Focus -> true
                        is FocusInteraction.Unfocus -> false
                        else -> return@collect
                    }
                    invalidateDraw()
                }
            }
        }
        override fun ContentDrawScope.draw() {
            drawContent()
            if (!focused) return
            val stroke = 2.dp.toPx()
            drawRoundRect(
                color = TvFocusColor,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(stroke),
            )
        }
    }
}

/** TV: which card opened the player, so focus can go back to it when the player closes. */
object TvFocusMemory {
    var lastId by mutableStateOf<String?>(null)
    var restoreTick by mutableStateOf(0)
}

/** TV: take focus back when the player closes and this card opened it. No-op off TV. */
fun Modifier.tvRestoreFocus(id: String): Modifier = composed {
    if (!TvMode.isTv) return@composed Modifier
    val requester = remember { FocusRequester() }
    val tickAtCompose = remember { TvFocusMemory.restoreTick }
    LaunchedEffect(TvFocusMemory.restoreTick) {
        // Skip the tick already current when composed, else a card scrolled back into view would steal focus.
        if (TvFocusMemory.restoreTick != tickAtCompose && TvFocusMemory.lastId == id) {
            delay(150) // player close animation
            if (runCatching { requester.requestFocus() }.isSuccess) TvFocusMemory.lastId = null
        }
    }
    Modifier.focusRequester(requester)
}

/**
 * TV: in-window sheets (speed, audio, settings) don't take focus when they appear, leaving the remote dead.
 * Pulls focus to the first focusable child once shown. No-op off TV.
 */
fun Modifier.tvFocusOnShow(): Modifier = composed {
    if (!TvMode.isTv) return@composed Modifier
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80) // let the enter animation lay the sheet out first
        runCatching { requester.requestFocus() }
    }
    Modifier.focusRequester(requester).focusGroup()
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
