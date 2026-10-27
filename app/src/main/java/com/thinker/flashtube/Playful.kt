package com.thinker.flashtube

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


enum class Flourish {

    Pop,

    Droop,

    Ring,

    Drop,
}






@Composable
fun FlourishIcon(icon: ImageVector, description: String?, active: Boolean, kind: Flourish, tint: Color, size: Dp, sparkColor: Color = tint) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val shift = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(active) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (!active || reduced) return@LaunchedEffect
        view.performHapticFeedback(if (kind == Flourish.Droop) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONFIRM)
        coroutineScope {
            when (kind) {
                Flourish.Pop -> {
                    launch {
                        scale.snapTo(0.7f)
                        scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
                    }
                    launch {
                        rotation.animateTo(0f, keyframes { durationMillis = 520; -22f at 140; 8f at 300; 0f at 520 })
                    }
                    launch { burst.snapTo(0f); burst.animateTo(1f, tween(520, easing = LinearEasing)) }
                }
                Flourish.Droop -> {
                    launch { shift.animateTo(0f, keyframes { durationMillis = 460; 0.22f at 160; -0.05f at 320; 0f at 460 }) }
                    launch { rotation.animateTo(0f, keyframes { durationMillis = 460; 14f at 160; -6f at 320; 0f at 460 }) }
                    launch { scale.snapTo(0.85f); scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f)) }
                }
                Flourish.Ring -> {
                    launch {
                        rotation.animateTo(0f, keyframes {
                            durationMillis = 720
                            24f at 80; -22f at 180; 18f at 280; -14f at 380; 9f at 480; -4f at 580; 0f at 720
                        })
                    }
                    launch { scale.snapTo(0.8f); scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
                }
                Flourish.Drop -> {
                    launch { shift.snapTo(-0.9f); shift.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 520f)) }
                    launch { scale.animateTo(1f, keyframes { durationMillis = 420; 1f at 0; 1.18f at 200; 0.92f at 300; 1f at 420 }) }
                }
            }
        }
    }
    Box(
        Modifier.size(size).drawBehind {
            val t = burst.value
            if (t >= 1f) return@drawBehind

            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val reach = this.size.minDimension * (0.55f + 0.75f * t)
            for (i in 0 until 8) {
                val a = (i / 8f) * 2f * PI.toFloat() - PI.toFloat() / 2f
                val p = Offset(c.x + cos(a) * reach, c.y + sin(a) * reach)
                drawCircle(sparkColor.copy(alpha = (1f - t) * 0.95f), radius = this.size.minDimension * 0.09f * (1f - t * 0.6f), center = p)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, description, tint = tint,
            modifier = Modifier.size(size).graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                rotationZ = rotation.value
                translationY = shift.value * size.toPx()

                transformOrigin = if (kind == Flourish.Ring) TransformOrigin(0.5f, 0.1f) else TransformOrigin(0.5f, 0.75f)
            },
        )
    }
}


@Composable
fun RollingText(text: String, color: Color, up: Boolean, fontSize: TextUnit = 14.sp) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val dir = if (up) 1 else -1
            (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 600f)) { it * dir } + fadeIn(tween(150))) togetherWith
                (slideOutVertically(tween(160)) { -it * dir } + fadeOut(tween(120)))
        },
        label = "rolling",
    ) { value -> Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = fontSize) }
}


@Composable
fun Modifier.squishOn(active: Boolean): Modifier {
    val reduced = rememberReducedMotion()
    val s = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(active) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (reduced) return@LaunchedEffect
        s.animateTo(1f, keyframes { durationMillis = 420; 0.86f at 90; 1.07f at 240; 1f at 420 })
    }
    return graphicsLayer { scaleX = s.value; scaleY = s.value }
}
