package com.thinker.flashtube

import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs


private val ModeButtonShape = RoundedCornerShape(18.dp)


@Composable
fun SpeedButton(state: PlayerState) {
    val c = MaterialTheme.colorScheme
    val normal = PlaybackSpeed.isNormal(state.speed)
    val sticky = PlaybackSpeed.remember && !normal
    val bg by animateColorAsState(
        when {
            sticky -> c.primary
            state.speedPanelOpen -> c.surfaceContainerHighest
            else -> c.surfaceContainerHigh
        },
        tween(220), label = "speedBg",
    )
    val ink by animateColorAsState(if (sticky) c.onPrimary else c.onSurface, tween(220), label = "speedInk")
    Box(
        Modifier
            .squishOn(sticky)
            .size(width = 48.dp, height = 36.dp)
            .pressable { state.speedPanelOpen = !state.speedPanelOpen }
            .background(bg, ModeButtonShape),
        contentAlignment = Alignment.Center,
    ) {
        if (normal) Icon(Icons.Rounded.Speed, "Playback speed", tint = ink, modifier = Modifier.size(18.dp))
        else Text(PlaybackSpeed.label(state.speed) + "×", color = ink, fontSize = 12.5.sp, fontWeight = FontWeight.Black)
    }
}


@Composable
fun AudioOnlyButton(state: PlayerState) {
    val c = MaterialTheme.colorScheme
    val on = AudioOnly.enabled
    val bg by animateColorAsState(if (on) c.primary else c.surfaceContainerHigh, tween(220), label = "audioBg")
    val ink by animateColorAsState(if (on) c.onPrimary else c.onSurface, tween(220), label = "audioInk")
    Box(
        Modifier
            .squishOn(on)
            .size(width = 44.dp, height = 36.dp)
            .pressable { state.setAudioOnly(!on) }
            .background(bg, ModeButtonShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) Icons.Rounded.Headphones else Icons.Outlined.Headphones,
            if (on) "Audio only is on" else "Audio only",
            tint = ink, modifier = Modifier.size(18.dp),
        )
    }
}






@Composable
fun ColumnScope.SpeedPanel(state: PlayerState) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = state.speedPanelOpen,
        enter = if (reduced) fadeIn(tween(0)) else
            expandVertically(spring(dampingRatio = 0.9f, stiffness = 500f), expandFrom = Alignment.Top) + fadeIn(tween(220, delayMillis = 60)),
        exit = if (reduced) fadeOut(tween(0)) else
            shrinkVertically(tween(260, easing = Motion.Emphasized), shrinkTowards = Alignment.Top) + fadeOut(tween(160)),
    ) {
        SpeedPanelContent(state)
    }
}

@Composable
private fun SpeedPanelContent(state: PlayerState) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val haptics = remember(view) { SpeedHaptics(view) }
    val pxPerUnit = with(LocalDensity.current) { RulerStep.toPx() } / PlaybackSpeed.STEP


    val value = remember { Animatable(state.speed).apply { updateBounds(PlaybackSpeed.MIN, PlaybackSpeed.MAX) } }
    var driving by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    val generation = remember { intArrayOf(0) }
    LaunchedEffect(state.speed) { if (!driving) value.snapTo(state.speed) }

    LaunchedEffect(Unit) {
        var last = PlaybackSpeed.step(value.value)
        snapshotFlow { PlaybackSpeed.step(value.value) }.collect { step ->
            if (step == last) return@collect
            last = step
            if (!driving) return@collect
            haptics.step(step)
            state.previewSpeed(PlaybackSpeed.atStep(step))
        }
    }


    fun drive(block: suspend () -> Unit) {
        job?.cancel()
        val mine = ++generation[0]
        job = scope.launch {
            driving = true
            try {
                block()
            } finally {

                if (generation[0] == mine) {
                    state.commitSpeed(PlaybackSpeed.snap(value.value))
                    driving = false
                }
            }
        }
    }
    fun glideTo(speed: Float) = drive { value.animateTo(speed, spring(dampingRatio = 0.85f, stiffness = 380f)) }

    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainer)
            .padding(top = 16.dp, bottom = 6.dp),
    ) {
        SpeedReadout({ value.value }, Modifier.align(Alignment.CenterHorizontally))
        Text(
            if (PlaybackSpeed.remember) "Every video" else "This video only",
            color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        )

        val tick = c.onSurfaceVariant
        val near = c.onSurface
        val ring = c.surfaceContainer
        val needle = c.primary
        val measurer = rememberTextMeasurer()
        val labels = remember(measurer) {
            val style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            (0..5).map { i -> (0.5f + i * 0.5f).let { s -> s to measurer.measure(PlaybackSpeed.label(s) + "×", style) } }
        }
        val fade = remember {
            Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0f), 0.24f to Color.Black, 0.76f to Color.Black, 1f to Color.Black.copy(alpha = 0f))
        }
        Canvas(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(62.dp)
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            value.snapTo((value.value - delta / pxPerUnit).coerceIn(PlaybackSpeed.MIN, PlaybackSpeed.MAX))
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStarted = { job?.cancel(); generation[0]++; driving = true },

                    onDragStopped = { velocity ->
                        val v = -velocity / pxPerUnit
                        drive {
                            if (abs(v) > 0.05f) value.animateDecay(v, exponentialDecay(frictionMultiplier = 2f))
                            value.animateTo(PlaybackSpeed.snap(value.value), spring(dampingRatio = 1f, stiffness = 900f))
                        }
                    },
                )

                .pointerInput(pxPerUnit) {
                    detectTapGestures { offset -> glideTo(PlaybackSpeed.snap(value.value + (offset.x - size.width / 2f) / pxPerUnit)) }
                }

                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(fade, blendMode = BlendMode.DstIn)
                },
        ) {
            val v = value.value
            val cx = size.width / 2f
            val stroke = 2.dp.toPx()
            for (i in 0..PlaybackSpeed.LAST_STEP) {
                val x = cx + (PlaybackSpeed.atStep(i) - v) * pxPerUnit
                if (x < -stroke || x > size.width + stroke) continue
                val (top, length, alpha) = when {
                    i % 10 == 0 -> Triple(7.dp, 26.dp, 1f)
                    i % 5 == 0 -> Triple(10.dp, 20.dp, 0.7f)
                    else -> Triple(10.dp, 12.dp, 0.4f)
                }
                drawLine(tick.copy(alpha = alpha), Offset(x, top.toPx()), Offset(x, (top + length).toPx()), stroke, StrokeCap.Round)
            }
            labels.forEach { (s, layout) ->
                val x = cx + (s - v) * pxPerUnit
                if (x < -layout.size.width || x > size.width + layout.size.width) return@forEach
                drawText(layout, color = if (abs(s - v) < 0.13f) near else tick, topLeft = Offset(x - layout.size.width / 2f, 40.dp.toPx()))
            }

            drawRoundRect(ring, Offset(cx - 5.dp.toPx(), 0f), Size(10.dp.toPx(), 42.dp.toPx()), CornerRadius(5.dp.toPx()))
            drawRoundRect(needle, Offset(cx - 2.dp.toPx(), 3.dp.toPx()), Size(4.dp.toPx(), 36.dp.toPx()), CornerRadius(2.dp.toPx()))
        }

        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            RememberCheck(PlaybackSpeed.remember, Modifier.weight(1f)) {
                view.performHapticFeedback(if (PlaybackSpeed.remember) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONFIRM)
                state.toggleRememberSpeed()
            }
            Spacer(Modifier.width(10.dp))
            val atOne = PlaybackSpeed.isNormal(state.speed)
            val resetAlpha by animateFloatAsState(if (atOne) 0.35f else 1f, tween(200), label = "resetAlpha")
            Box(
                Modifier
                    .graphicsLayer { alpha = resetAlpha }
                    .height(36.dp)
                    .pressable(enabled = !atOne) { glideTo(1f) }
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surfaceContainerHighest)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("1×", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}


private val RulerStep = 12.dp


@Composable
private fun SpeedReadout(value: () -> Float, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
        Text(
            PlaybackSpeed.label(value()),
            fontFamily = RoundedHeavy, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 46.sp,
            letterSpacing = (-1.4).sp,
        )
        Text(
            "×", color = c.primary, fontFamily = RoundedHeavy, fontWeight = FontWeight.Black, fontSize = 24.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
    }
}


@Composable
private fun RememberCheck(checked: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val radius by animateDpAsState(if (checked) 9.dp else 7.dp, if (reduced) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f), label = "checkShape")
    val fill by animateColorAsState(if (checked) c.primary else c.surfaceContainerHighest, tween(200), label = "checkFill")
    val mark by animateFloatAsState(if (checked) 1f else 0f, if (reduced) tween(0) else spring(dampingRatio = 0.5f, stiffness = 600f), label = "checkMark")
    Row(modifier.pressable(pressedScale = 0.98f, onClick = onToggle).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).background(fill, RoundedCornerShape(radius)), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Check, null, tint = c.onPrimary,
                modifier = Modifier.size(18.dp).graphicsLayer { alpha = mark.coerceIn(0f, 1f); scaleX = mark; scaleY = mark },
            )
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text("Remember for every video", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text("Until you set it back to 1×", color = c.onSurfaceVariant, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}









class SpeedHaptics(private val view: View) {
    private enum class Kind { Step, Quarter, One, End }

    private val vibrator: Vibrator? = when {
        Build.VERSION.SDK_INT >= 31 -> view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else -> @Suppress("DEPRECATION") view.context.getSystemService(Vibrator::class.java)
    }
    private val primitives = Build.VERSION.SDK_INT >= 30 && vibrator?.hasVibrator() == true &&
        vibrator.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_CLICK,
        )
    private val oneStep = PlaybackSpeed.step(1f)
    private var lastAt = 0L


    fun step(step: Int) {
        val kind = when {
            step == 0 || step == PlaybackSpeed.LAST_STEP -> Kind.End
            step == oneStep -> Kind.One
            step % 5 == 0 -> Kind.Quarter
            else -> Kind.Step
        }
        val now = SystemClock.uptimeMillis()
        if (kind == Kind.Step && now - lastAt < MIN_GAP_MS) return
        lastAt = now
        if (primitives && Build.VERSION.SDK_INT >= 30) play(kind) else fallback(kind)
    }

    @androidx.annotation.RequiresApi(30)
    private fun play(kind: Kind) {
        val (primitive, scale) = when (kind) {
            Kind.Step -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK to STEP_SCALE
            Kind.Quarter -> VibrationEffect.Composition.PRIMITIVE_TICK to QUARTER_SCALE
            Kind.One -> VibrationEffect.Composition.PRIMITIVE_CLICK to ONE_SCALE
            Kind.End -> VibrationEffect.Composition.PRIMITIVE_CLICK to END_SCALE
        }
        val effect = VibrationEffect.startComposition().addPrimitive(primitive, scale).compose()
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        else v.vibrate(effect)
    }

    private fun fallback(kind: Kind) {
        val constant = when {
            Build.VERSION.SDK_INT >= 34 -> when (kind) {
                Kind.Step -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                Kind.Quarter, Kind.One -> HapticFeedbackConstants.SEGMENT_TICK
                Kind.End -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            }
            kind == Kind.End -> HapticFeedbackConstants.CONTEXT_CLICK
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }

    private companion object {

        const val STEP_SCALE = 0.45f
        const val QUARTER_SCALE = 0.55f
        const val ONE_SCALE = 0.6f
        const val END_SCALE = 0.9f
        const val MIN_GAP_MS = 22L
    }
}
