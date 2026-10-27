package com.thinker.flashtube

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import com.kyant.backdrop.Backdrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin






@Composable
fun VideoContent(
    state: PlayerState,
    fullscreen: Boolean,
    onCollapse: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    controlsAlpha: () -> Float = { 1f },

    interactive: Boolean = true,
    onInactiveTap: () -> Unit = {},

    portraitEdgeScrub: Boolean = false,
) {


    var controlsVisible by remember { mutableStateOf(false) }
    var touchedAt by remember { mutableLongStateOf(0L) }
    fun touched() { touchedAt = System.nanoTime() }
    SideEffect { state.videoControlsVisible = controlsVisible }
    LaunchedEffect(controlsVisible, state.isPlaying, touchedAt) {
        if (controlsVisible && state.isPlaying) {
            delay(Motion.ControlsHideMs)
            controlsVisible = false
        }
    }
    val reduced = rememberReducedMotion()
    val visibleAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = if (reduced) tween(0) else tween(160, easing = Motion.Emphasized),
        label = "controlsAlpha",
    )



    var burstSide by remember { mutableIntStateOf(1) }
    var burstSeconds by remember { mutableIntStateOf(0) }
    var burstTaps by remember { mutableIntStateOf(0) }
    var burstActive by remember { mutableStateOf(false) }
    LaunchedEffect(burstTaps) {
        if (burstTaps == 0) return@LaunchedEffect
        delay(SeekBurstWindowMs)
        burstActive = false
    }

    val nudge = remember { Animatable(0f) }
    LaunchedEffect(burstTaps) {
        if (burstTaps > 0 && !reduced) nudge.animateTo(0f, keyframes { durationMillis = 440; 1f at 130 })
    }
    LaunchedEffect(interactive) {
        if (!interactive) {
            controlsVisible = false
            burstActive = false
            state.endScrubDragging()
            state.endHoldSpeed()
        }
    }
    val currentInteractive by androidx.compose.runtime.rememberUpdatedState(interactive)
    val currentInactiveTap by androidx.compose.runtime.rememberUpdatedState(onInactiveTap)
    fun toggleControls() { controlsVisible = !controlsVisible; touched() }
    fun seekStep(side: Int) {
        val moved = state.seekBy(side * SeekStepMs)
        burstSeconds += ((abs(moved) + 500) / 1000).toInt()
        burstTaps++
    }
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val activity = context as? Activity
    var gestureHud by remember { mutableStateOf<Pair<ImageVector, Pair<String, Float>>?>(null) }

    LaunchedEffect(gestureHud) {
        if (gestureHud != null) {
            delay(1200)
            gestureHud = null
        }
    }

    val pip = LocalPictureInPicture.current
    val view = LocalView.current
    val currentView by rememberUpdatedState(view)
    val fullscreenControlsScrub = fullscreen && !portraitEdgeScrub
    val scrubbing = state.scrubDragging
    val scrubPreviewMs = state.scrubPreviewMs
    val zoomState = rememberTransformableState { zoomChange, _, _ ->
        if (interactive) {
            state.videoZoom = (state.videoZoom * zoomChange).coerceIn(1f, 4f)
        }
    }
    LaunchedEffect(state.current?.id) {
        state.current?.id?.let { ScrubStoryboards.preload(it) }
    }
    Box(modifier.background(Color.Black).onGloballyPositioned { pip?.videoBounds = it.boundsInWindow() }.pointerInput(state, portraitEdgeScrub) {
        val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
        val holdSpeedMs = Motion.HoldSpeedMs
        coroutineScope {

            var seekWindow: Job? = null
            var pendingSide = 0
            var pendingAt = 0L
            awaitEachGesture {
                val down = awaitFirstDown()
                if (portraitEdgeScrub && down.position.y >= size.height - EdgeScrubTouchPadAbove.roundToPx()) {
                    return@awaitEachGesture
                }
                down.consume()
                if (!currentInteractive) {
                    waitForUpOrCancellation()
                    currentInactiveTap()
                    return@awaitEachGesture
                }
                if (state.error != null) {
                    waitForUpOrCancellation()
                    return@awaitEachGesture
                }

                val startTime = System.currentTimeMillis()
                val downX = down.position.x / size.width
                val downY = down.position.y
                var isDraggingGesture = false
                var lastY = downY




                var verticalSwipeHandled = false
                val upEarly = withTimeoutOrNull(holdSpeedMs) {
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change != null) {
                            val dy = lastY - change.position.y
                            val totalDy = downY - change.position.y
                            val totalDx = down.position.x - change.position.x

                            val isVerticalIntent = kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx) * 1.5f

                            if (fullscreen) {

                                val isLeftEdge = downX < 0.20f
                                val isRightEdge = downX > 0.80f

                                if (!isDraggingGesture && isVerticalIntent && kotlin.math.abs(totalDy) > 60f) {
                                    if (isLeftEdge || isRightEdge) {
                                        isDraggingGesture = true
                                    } else if (totalDy < -90f) {

                                        verticalSwipeHandled = true
                                        onToggleFullscreen()
                                        return@withTimeoutOrNull null
                                    }
                                }
                                if (isDraggingGesture) {
                                    change.consume()
                                    if (isLeftEdge && activity != null) {
                                        val window = activity.window
                                        val attrs = window.attributes
                                        val currentB = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness

                                        val newB = (currentB + (dy / size.height) * 0.85f).coerceIn(0.02f, 1f)
                                        attrs.screenBrightness = newB
                                        window.attributes = attrs
                                        gestureHud = Pair(Icons.Rounded.BrightnessMedium, Pair("Brightness", newB))
                                    } else if (isRightEdge && audioManager != null) {
                                        val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val curV = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                                        val deltaV = ((dy / size.height) * maxV * 1.0f).toInt()
                                        if (deltaV != 0) {
                                            val newV = (curV + deltaV).coerceIn(0, maxV)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newV, 0)
                                            val icon = if (newV == 0) Icons.AutoMirrored.Rounded.VolumeMute else Icons.AutoMirrored.Rounded.VolumeUp
                                            gestureHud = Pair(icon, Pair("Volume", newV.toFloat() / maxV.toFloat()))
                                        }
                                    }
                                    lastY = change.position.y
                                }
                            } else {


                                if (!verticalSwipeHandled && isVerticalIntent && kotlin.math.abs(totalDy) > 90f) {
                                    verticalSwipeHandled = true
                                    change.consume()
                                    if (totalDy < -90f) {
                                        onCollapse()
                                    } else {
                                        onToggleFullscreen()
                                    }
                                    return@withTimeoutOrNull null
                                }
                            }
                        }
                        event = awaitPointerEvent()
                    }
                    event.changes.firstOrNull()
                }

                if (isDraggingGesture || verticalSwipeHandled) {
                    return@awaitEachGesture
                }

                if (upEarly == null) {
                    val elapsed = System.currentTimeMillis() - startTime

                    if (elapsed >= holdSpeedMs - 80L) {
                        seekWindow?.cancel()
                        if (!state.scrubDragging && state.beginHoldSpeed()) {
                            currentView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            controlsVisible = false
                            burstActive = false
                            waitForUpOrCancellation()?.consume()
                            state.endHoldSpeed()
                        } else {
                            waitForUpOrCancellation()?.consume()
                        }
                    }
                    return@awaitEachGesture
                }
                val up = upEarly
                up.consume()
                val x = up.position.x / size.width
                val side = if (x < 0.4f) -1 else if (x > 0.6f) 1 else 0
                if (burstActive && side == burstSide) { seekStep(side); return@awaitEachGesture }
                burstActive = false
                val inSeekWindow = seekWindow?.isActive == true
                seekWindow?.cancel()
                if (side == 0) {
                    if (state.videoZoom > 1.05f) {
                        state.resetZoom()
                    } else {
                        toggleControls()
                    }
                } else if (inSeekWindow && side == pendingSide && up.uptimeMillis - pendingAt < doubleTapMs) {
                    controlsVisible = false
                    burstSide = side
                    burstSeconds = 0
                    burstActive = true
                    seekStep(side)
                } else {
                    toggleControls()
                    pendingSide = side
                    pendingAt = up.uptimeMillis
                    seekWindow = launch { delay(doubleTapMs) }
                }
            }
        }
    }) {
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .transformable(state = zoomState),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .aspectFit(state.videoAspect, cover = state.zoomToFill)
                    .graphicsLayer {
                        val n = nudge.value
                        translationX = burstSide * 5.dp.toPx() * n
                        val z = state.videoZoom
                        scaleX = (1f + 0.03f * n) * z
                        scaleY = (1f + 0.03f * n) * z
                    },
            ) {


                Thumbnail(
                    state.current?.thumbnail,
                    Modifier.fillMaxSize().graphicsLayer { alpha = if (scrubbing) 0f else 1f },
                    radius = 0,
                )
                LocalVideoSurface.current(Modifier.fillMaxSize())
                if (scrubbing && scrubPreviewMs != null) {
                    ScrubVideoPreview(
                        state.current?.id,
                        scrubPreviewMs,
                        Modifier.fillMaxSize(),
                    )
                }
            }
        }
        var showZoomPill by remember { mutableStateOf(false) }
        LaunchedEffect(state.videoZoom) {
            if (state.videoZoom > 1.05f) {
                showZoomPill = true
                delay(2_000)
                showZoomPill = false
            } else {
                showZoomPill = false
            }
        }
        val zoomAlpha by animateFloatAsState(
            targetValue = if (showZoomPill || controlsVisible) 1f else 0f,
            animationSpec = tween(300),
            label = "zoomPillAlpha"
        )
        if (state.videoZoom > 1.05f && zoomAlpha > 0f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .graphicsLayer { alpha = zoomAlpha }
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { state.resetZoom() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${(state.videoZoom * 10).toInt() / 10f}× · Tap to reset",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (!scrubbing) {
            SeekBurstPill(burstSide, burstSeconds, burstTaps, burstActive, fullscreen)
        }
        if (state.holdSpeedActive) {
            HoldSpeedPill(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).graphicsLayer { alpha = controlsAlpha() })
        }
        gestureHud?.let { (icon, info) ->
            GestureHUD(icon = icon, label = info.first, percent = info.second, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
        }

        val error = state.error
        if (error != null) {
            ErrorPanel(error, onRetry = state::retry, onCollapse = onCollapse)
            return@Box
        }

        val enabled = controlsVisible && interactive

        if (!scrubbing && !controlsVisible && (state.isBuffering || (state.loading && state.details == null))) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
        if (!scrubbing) {
            SkippedNote(state, Modifier.align(Alignment.TopCenter).padding(top = 12.dp).graphicsLayer { alpha = controlsAlpha() })
        }

        if (!scrubbing && !controlsVisible) {
            state.askSegment?.let { segment ->
                SkipSegmentPill(segment, enabled = true, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, top = 12.dp, bottom = 40.dp)) {
                    touched(); state.skipSegment(segment)
                }
            }
        }
        val chromeAlpha = if (scrubbing) 0f else visibleAlpha * controlsAlpha()
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha }) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.5f),
                        0.32f to Color.Black.copy(alpha = 0.08f),
                        0.58f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
            )
            val edge = if (fullscreen) {
                Modifier.fullscreenControlsHorizontalInsets().padding(vertical = FullscreenControlsVerticalPad)
            } else {
                Modifier
            }
            Box(Modifier.fillMaxSize().then(edge)) {

                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (fullscreen) {
                        GlassButton(Icons.AutoMirrored.Rounded.ArrowBack, "Exit fullscreen", enabled, size = 32.dp, iconSize = 20.dp) {
                            touched()
                            onToggleFullscreen()
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.current?.title.orEmpty(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        GlassButton(Icons.Rounded.KeyboardArrowDown, "Minimize", enabled, size = 32.dp, iconSize = 22.dp) {
                            touched()
                            onCollapse()
                        }
                        Spacer(Modifier.weight(1f))
                    }


                    GlassButton(
                        if (state.zoomToFill) Icons.Rounded.AspectRatio else Icons.Rounded.CropFree,
                        if (state.zoomToFill) "Fit to screen" else "Fill screen",
                        enabled,
                        size = 32.dp,
                        iconSize = 18.dp,
                        tinted = state.zoomToFill,
                    ) {
                        touched()
                        state.toggleZoomToFill()
                    }
                    Spacer(Modifier.width(6.dp))


                    if (pip?.supported == true) {
                        GlassButton(Icons.Rounded.PictureInPictureAlt, "Picture-in-picture", enabled, size = 32.dp, iconSize = 18.dp) {
                            controlsVisible = false
                            if (!pip.enter(state.videoAspect)) state.actionMessage = "Picture-in-picture is turned off for FlashTube in Settings."
                        }
                        Spacer(Modifier.width(6.dp))
                    }


                    GlassButton(Icons.Rounded.Settings, "Playback settings", enabled, size = 32.dp, iconSize = 20.dp) {
                        touched()
                        state.sheet = PlayerSheet.Settings
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 56.dp else 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val skip = if (fullscreen) 56.dp else 48.dp
                    GlassButton(Icons.Rounded.Replay10, "Back 10 seconds", enabled, size = skip, iconSize = skip * 0.62f) {
                        touched(); state.seekBy(-10_000)
                    }
                    MorphPlayButton(
                        playing = state.isPlaying,
                        buffering = state.isBuffering || (state.loading && state.details == null),
                        size = if (fullscreen) 76.dp else 66.dp,

                        container = Color.Black.copy(alpha = 0.38f),
                        content = Color.White,
                        enabled = enabled,
                    ) { touched(); state.togglePlay() }
                    GlassButton(Icons.Rounded.Forward10, "Forward 10 seconds", enabled, size = skip, iconSize = skip * 0.62f) {
                        touched(); state.seekBy(10_000)
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = if (fullscreenControlsScrub) 0.dp else 14.dp,
                            end = if (fullscreenControlsScrub) 0.dp else 8.dp,
                            bottom = EdgeScrubTimeRowBottomPad +
                                if (fullscreenControlsScrub) FullscreenControlsLift else 0.dp,
                        ),
                ) {
                    Row(
                        Modifier.height(EdgeScrubTimeRowHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatDuration(state.scrubPreviewMs ?: state.positionMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            " / " + formatDuration(state.durationMs),
                            color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        state.askSegment?.let { segment ->
                            SkipSegmentPill(segment, enabled = true) { touched(); state.skipSegment(segment) }
                            Spacer(Modifier.width(6.dp))
                        }
                        GlassButton(
                            if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            if (fullscreen) "Exit fullscreen" else "Fullscreen",
                            enabled,
                            size = 34.dp,
                            iconSize = 22.dp,
                            tinted = false,
                        ) { onToggleFullscreen() }
                    }
                }
            }
        }

        if (state.error == null && (!fullscreenControlsScrub || controlsVisible || scrubbing)) {
            val scrubActive = controlsVisible && interactive
            val morphFade = controlsAlpha()
            val scrubFade = if (scrubbing) 1f else morphFade
            val segments = if (SponsorBlock.enabled) state.segments.filter { it.action != SponsorBlock.Action.Off } else emptyList()
            val scrubModifier = when {
                portraitEdgeScrub -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()

                    .offset(y = EdgeScrubTouchPadBelow)
                    .height(EdgeScrubTouchHeight)
                    .graphicsLayer { alpha = scrubFade }
                fullscreenControlsScrub -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fullscreenControlsHorizontalInsets()
                    .padding(bottom = FullscreenScrubBottomPad + FullscreenControlsLift)
                    .height(FullscreenScrubTouchHeight)
                    .graphicsLayer { alpha = scrubFade }
                else -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(EdgeScrubTouchPadAbove * 2)
                    .graphicsLayer { alpha = scrubFade }
            }
            Scrubber(
                segments = segments,
                positionMs = state.positionMs,
                bufferedMs = state.bufferedMs,
                durationMs = state.durationMs,
                enabled = interactive,
                active = scrubActive,
                relativeDrag = true,
                edgeToEdge = portraitEdgeScrub,
                tapToSeek = false,
                edgeTouchPadAbove = EdgeScrubTouchPadAbove,
                trackHeight = if (fullscreenControlsScrub) FullscreenScrubTrackHeight else 4.dp,
                touchHeight = when {
                    portraitEdgeScrub -> EdgeScrubTouchHeight
                    fullscreenControlsScrub -> FullscreenScrubTouchHeight
                    else -> EdgeScrubTouchPadAbove * 2
                },
                knob = if (fullscreenControlsScrub) FullscreenScrubKnob else 7.dp,
                knobDragging = if (fullscreenControlsScrub) FullscreenScrubKnobDragging else 9.dp,
                onPreview = { state.scrubPreviewMs = it; touched() },
                onSeek = { state.endScrubDragging(it); touched() },
                onDraggingChanged = { dragging ->
                    if (dragging) state.beginScrubDragging()
                    else state.endScrubDragging()
                },
                modifier = scrubModifier.zIndex(10f),
            )
        }
        if (scrubbing && scrubPreviewMs != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(
                        if (fullscreenControlsScrub) Modifier.fullscreenControlsHorizontalInsets() else Modifier,
                    )
                    .padding(
                        bottom = if (fullscreenControlsScrub) {
                            FullscreenTimeRowBottomFromEdge
                        } else {
                            EdgeScrubTimeRowBottomPad
                        },
                    )
                    .height(EdgeScrubTimeRowHeight)
                    .zIndex(11f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    formatDuration(scrubPreviewMs),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}







@Composable
fun AudioOnlyStage(
    state: PlayerState,
    interactive: Boolean,
    onInactiveTap: () -> Unit,
    progress: () -> Float,
    controlsAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val currentInteractive by androidx.compose.runtime.rememberUpdatedState(interactive)
    val currentInactiveTap by androidx.compose.runtime.rememberUpdatedState(onInactiveTap)

    Box(modifier.pointerInput(Unit) { detectTapGestures { if (!currentInteractive) currentInactiveTap() } }) {
        Box(Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(16f / 9f)) {
            Thumbnail(state.current?.thumbnail, Modifier.fillMaxSize(), radius = 0)
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ramp(progress(), 0.25f, 0.85f) }.background(c.surfaceContainer))
        val error = state.error
        if (error != null) {
            ErrorPanel(error, onRetry = state::retry, onCollapse = {})
            return@Box
        }
        Column(
            Modifier.fillMaxSize().graphicsLayer { alpha = controlsAlpha() }.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TonalSkip(Icons.Rounded.Replay10, "Back 10 seconds", interactive) { state.seekBy(-10_000) }
                MorphPlayButton(
                    playing = state.isPlaying,
                    buffering = state.isBuffering || (state.loading && state.details == null),
                    size = 78.dp,
                    container = c.primary,
                    content = c.onPrimary,
                    enabled = interactive,
                ) { state.togglePlay() }
                TonalSkip(Icons.Rounded.Forward10, "Forward 10 seconds", interactive) { state.seekBy(10_000) }
            }
            Spacer(Modifier.height(8.dp))
            var scrubPreview by remember { mutableStateOf<Long?>(null) }
            Scrubber(
                segments = if (SponsorBlock.enabled) state.segments.filter { it.action != SponsorBlock.Action.Off } else emptyList(),
                positionMs = state.positionMs,
                bufferedMs = state.bufferedMs,
                durationMs = state.durationMs,
                enabled = interactive,
                onPreview = { scrubPreview = it },
                onSeek = { scrubPreview = null; state.seekTo(it) },
                track = c.surfaceContainerHighest,
                buffered = c.primary.copy(alpha = 0.3f),
                played = c.primary,
                trackHeight = 6.dp,
                knob = 8.dp,
                knobDragging = 10.dp,
            )
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                val time = Modifier.width(56.dp)
                Text(formatDuration(scrubPreview ?: state.positionMs), color = c.onSurfaceVariant, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, modifier = time)

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val ask = state.askSegment
                    if (ask != null) SkipSegmentPill(ask, enabled = interactive) { state.skipSegment(ask) }
                    else SkippedNote(state, Modifier)
                }
                Text(
                    formatDuration(state.durationMs), color = c.onSurfaceVariant, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = time,
                )
            }
        }
    }
}

@Composable
private fun TonalSkip(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(58.dp).pressable(enabled = enabled, onClick = onClick).clip(CircleShape).background(c.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = c.onSurface, modifier = Modifier.size(34.dp)) }
}





fun Modifier.aspectFit(aspect: Float, cover: Boolean = false) = layout { measurable, constraints ->
    val boxW = constraints.maxWidth.toFloat()
    val boxH = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else boxW / aspect

    val byHeight = (boxW / boxH > aspect) != cover
    val w = (if (byHeight) boxH * aspect else boxW).roundToInt()
    val h = (if (byHeight) boxH else boxW / aspect).roundToInt()
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
    layout(boxW.roundToInt(), boxH.roundToInt()) {
        placeable.place(((boxW - w) / 2f).roundToInt(), ((boxH - h) / 2f).roundToInt())
    }
}





@Composable
fun FullscreenPlayer(state: PlayerState, onExit: () -> Unit) {
    val reduced = rememberReducedMotion()
    var drag by remember { mutableStateOf(0f) }
    VideoContent(
        state = state,
        fullscreen = true,
        onCollapse = onExit,
        onToggleFullscreen = onExit,
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                state = rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (drag > 120f || velocity > 1400f) onExit()
                    drag = 0f
                },
            )
            .graphicsLayer {

                translationY = if (reduced) 0f else drag * 0.35f
            },
    )
}

@Composable
private fun HoldSpeedPill(modifier: Modifier = Modifier) {
    Text(
        "2×",
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        lineHeight = 13.sp,
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}


@Composable
private fun SkipSegmentPill(segment: SponsorBlock.Segment, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(32.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(segment.category.color.copy(alpha = 0.92f))
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (segment.category.color.luminance() > 0.45f) Color(0xFF151515) else Color.White
        Text("Skip ${segment.category.short}", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Icon(Icons.Rounded.SkipNext, null, tint = ink, modifier = Modifier.size(18.dp))
    }
}


@Composable
private fun SkippedNote(state: PlayerState, modifier: Modifier) {
    val skip = SponsorBlock.lastSkip?.takeIf { it.videoId == state.current?.id } ?: return
    LaunchedEffect(skip) {
        delay(4_000)
        if (SponsorBlock.lastSkip == skip) SponsorBlock.lastSkip = null
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Skipped ${formatDuration(skip.segment.lengthMs)} ${skip.segment.category.short}",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            "Undo",
            color = skip.segment.category.color, fontSize = 12.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.pressable { state.undoSkip(skip) }.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(state: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val details = state.details ?: return
    val chosen = QualityPreference.height
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Quality",
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                state.playingFormat?.let {
                    Text(it, color = c.onSurfaceVariant, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(6.dp))

            val autoDesc = "Best up to 1080p" + ((state.decodedHeight?.let { "${it}p" } ?: details.quality)?.let { " · $it" } ?: "")
            QualityRow("Auto", autoDesc, chosen == null) {
                state.setQuality(null)
                onDismiss()
            }

            details.qualities.forEach { h ->
                QualityRow("${h}p", qualityTag(h), chosen == h) {
                    state.setQuality(h)
                    onDismiss()
                }
            }
        }
    }
}

private fun qualityTag(height: Int): String? = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "QHD"
    height >= 1080 -> "Full HD"
    height >= 720 -> "HD"
    height <= 240 -> "Data saver"
    else -> null
}

@Composable
private fun QualityRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.primaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 14.5.sp,
            color = if (selected) c.onPrimaryContainer else c.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) c.onPrimaryContainer.copy(alpha = 0.8f) else c.onSurfaceVariant
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Check, "Selected", tint = c.onPrimaryContainer, modifier = Modifier.size(17.dp))
        }
    }
}








@Composable
fun PlayerSettingsSheet(state: PlayerState, backdrop: Backdrop, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    BackHandler(onBack = onDismiss)
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val spec = glassSpec(
        shape = shape,
        tint = glassFill(0.62f),
        blur = 26.dp,
        refractionHeight = 18.dp,
        refractionAmount = 24.dp,
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .liquidGlass(backdrop, spec)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(c.onSurfaceVariant.copy(alpha = 0.35f)),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Playback Settings",
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                CircleAction(Icons.Rounded.Close, "Close", onDismiss)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {

                val qualityText = (state.decodedHeight?.let { "${it}p" } ?: state.details?.quality) ?: "Auto"
                PlayerSettingItem(
                    icon = Icons.Rounded.HighQuality,
                    title = "Quality",
                    value = if (QualityPreference.height == null) "Auto ($qualityText)" else qualityText,
                    onClick = {
                        onDismiss()
                        state.sheet = PlayerSheet.Quality
                    }
                )


                PlayerSettingItem(
                    icon = Icons.Rounded.Speed,
                    title = "Playback speed",
                    value = if (state.speed == 1f) "Normal (1.0×)" else "${state.speed}×",
                    onClick = {
                        val speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
                        val idx = speeds.indexOf(state.speed)
                        val next = speeds[(idx + 1).coerceAtLeast(0) % speeds.size]
                        state.commitSpeed(next)
                    }
                )


                PlayerSettingItem(
                    icon = Icons.Rounded.Repeat,
                    title = "Loop video",
                    value = if (state.loopEnabled) "On" else "Off",
                    onClick = { state.toggleLoop() }
                )


                PlayerSettingItem(
                    icon = Icons.Rounded.PlayCircle,
                    title = "Autoplay",
                    value = if (state.autoPlayEnabled) "On" else "Off",
                    onClick = { state.toggleAutoPlay() }
                )


                PlayerSettingItem(
                    icon = if (state.zoomToFill) Icons.Rounded.AspectRatio else Icons.Rounded.CropFree,
                    title = "Video aspect",
                    value = if (state.zoomToFill) "Zoom to fill" else "Fit to screen",
                    onClick = { state.toggleZoomToFill() }
                )


                val zoomText = if (state.videoZoom > 1.05f) "${(state.videoZoom * 10).toInt() / 10f}× (Reset)" else "1.0×"
                PlayerSettingItem(
                    icon = Icons.Rounded.ZoomIn,
                    title = "Video zoom",
                    value = zoomText,
                    onClick = { state.resetZoom() }
                )

                


                state.playingFormat?.let { format ->
                    PlayerSettingItem(
                        icon = Icons.Rounded.Info,
                        title = "Video format",
                        value = format,
                        onClick = null
                    )
                }

                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun PlayerSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(pressedScale = 0.98f, onClick = onClick) else Modifier)
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceContainerHigh.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.onSurface, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = c.onSurfaceVariant)
    }
}

@Composable
private fun BoxScope.ErrorPanel(message: String, onRetry: () -> Unit, onCollapse: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
    Column(
        Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.pressable(onClick = onRetry).clip(CircleShape).background(Color.White).padding(horizontal = 18.dp, vertical = 9.dp),
        ) { Text("Try again", color = Color(0xFF141414), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}

@Composable
private fun GlassButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 22.dp,
    tinted: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(if (tinted) Color.Black.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

private const val SeekStepMs = 10_000L

private const val SeekBurstWindowMs = 600L







@Composable
private fun SeekBurstPill(side: Int, seconds: Int, taps: Int, active: Boolean, fullscreen: Boolean) {
    val reduced = rememberReducedMotion()
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (reduced) 0 else if (active) 140 else 200, easing = Motion.Emphasized),
        label = "seekPillAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (active || reduced) 1f else 0.4f,
        animationSpec = if (active) spring(dampingRatio = 0.5f, stiffness = 500f) else tween(200, easing = Motion.Emphasized),
        label = "seekPillScale",
    )
    val squishX = remember { Animatable(1f) }
    val squishY = remember { Animatable(1f) }
    val dash = remember { Animatable(0f) }
    val sparks = remember { Animatable(1f) }
    LaunchedEffect(taps) {
        if (taps == 0 || reduced) return@LaunchedEffect
        launch { squishX.animateTo(1f, keyframes { durationMillis = 440; 1.14f at 120; 0.96f at 270 }) }
        launch { squishY.animateTo(1f, keyframes { durationMillis = 440; 0.88f at 120; 1.04f at 270 }) }
        launch { dash.animateTo(0f, keyframes { durationMillis = 380; 1f at 115 }) }
        launch { sparks.snapTo(0f); sparks.animateTo(1f, tween(520, easing = Motion.Emphasized)) }
    }
    val sparkColor = MaterialTheme.colorScheme.primary
    val font = if (fullscreen) 20.sp else 16.sp
    Row(
        Modifier
            .layout { measurable, constraints ->
                val pill = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val centerX = constraints.maxWidth * (if (side < 0) 0.22f else 0.78f)
                    pill.place((centerX - pill.width / 2f).roundToInt(), (constraints.maxHeight - pill.height) / 2)
                }
            }
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale * squishX.value
                scaleY = scale * squishY.value
            }
            .drawBehind {
                val t = sparks.value
                if (t >= 1f) return@drawBehind

                val reach = size.width / 2f + 16.dp.toPx()
                for (i in 0 until 8) {
                    val a = (i / 8f + 1f / 16f) * 2f * PI.toFloat()
                    val from = Offset(cos(a) * size.width * 0.36f, sin(a) * 12.dp.toPx())
                    val to = Offset(cos(a) * reach, sin(a) * reach * 0.62f)
                    drawCircle(
                        (if (i % 2 == 0) sparkColor else Color.White).copy(alpha = 1f - t),
                        radius = 3.5.dp.toPx() * (1f - 0.65f * t),
                        center = center + from + (to - from) * t,
                    )
                }
            }
            .height(if (fullscreen) 48.dp else 40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(start = if (fullscreen) 14.dp else 11.dp, end = if (fullscreen) 18.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (side < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(if (fullscreen) 28.dp else 22.dp)
                .graphicsLayer { translationX = side * 6.dp.toPx() * dash.value },
        )
        Spacer(Modifier.width(8.dp))
        Text(if (side < 0) "−" else "+", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = font)
        RollingText("$seconds", Color.White, up = true, fontSize = font)
    }
}





@Composable
fun MorphPlayButton(
    playing: Boolean,
    buffering: Boolean,
    size: Dp,
    container: Color,
    content: Color,
    enabled: Boolean = true,
    backdrop: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val reduced = rememberReducedMotion()
    val radius by animateDpAsState(
        targetValue = if (playing) size * 0.33f else size / 2,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "playShape",
    )
    Box(
        modifier = Modifier
            .size(size)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .then(backdrop)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) {
            CircularProgressIndicator(color = content, strokeWidth = 3.dp, modifier = Modifier.size(size * 0.42f))
        } else {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Pause" else "Play",
                tint = content,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

private val ScrubberActiveRed = Color(0xFFFF0033)


internal val EdgeScrubTouchPadAbove = 14.dp
internal val EdgeScrubTouchPadBelow = 36.dp
internal val EdgeScrubTouchHeight = EdgeScrubTouchPadAbove + EdgeScrubTouchPadBelow

private val EdgeScrubTimeRowBottomPad = EdgeScrubTouchPadAbove + 6.dp
private val EdgeScrubTimeRowHeight = 34.dp

private val FullscreenControlsHorizontalPad = 20.dp


private fun Modifier.fullscreenControlsHorizontalInsets(): Modifier = composed {
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout.asPaddingValues()
    val balanced = maxOf(
        cutout.calculateStartPadding(layoutDirection),
        cutout.calculateEndPadding(layoutDirection),
    )
    padding(
        start = balanced + FullscreenControlsHorizontalPad,
        end = balanced + FullscreenControlsHorizontalPad,
    )
}
private val FullscreenControlsVerticalPad = 8.dp
private val FullscreenScrubTrackHeight = 4.dp
private val FullscreenScrubTouchHeight = 30.dp
private val FullscreenScrubBottomPad = 6.dp
private val FullscreenControlsLift = 10.dp

private val FullscreenTimeRowBottomFromEdge: Dp
    get() = FullscreenControlsVerticalPad + EdgeScrubTimeRowBottomPad + FullscreenControlsLift

private val FullscreenScrubKnob = 5.dp
private val FullscreenScrubKnobDragging = 6.dp






@Composable
private fun Scrubber(
    segments: List<SponsorBlock.Segment>,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onPreview: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onDraggingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    active: Boolean = true,
    relativeDrag: Boolean = false,
    edgeToEdge: Boolean = false,
    edgeTouchPadAbove: Dp = EdgeScrubTouchPadAbove,
    tapToSeek: Boolean = true,
    track: Color = Color.White.copy(alpha = 0.3f),
    buffered: Color = Color.White.copy(alpha = 0.5f),
    played: Color = Color.White,
    trackHeight: Dp = 4.dp,
    touchHeight: Dp = 24.dp,
    knob: Dp = 7.dp,
    knobDragging: Dp = 9.dp,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentPositionMs by rememberUpdatedState(positionMs)
    val duration = durationMs.coerceAtLeast(1L)
    val playedFraction = dragFraction ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    val dragging = dragFraction != null

    val chromeActive = active || dragging
    val playedColor by animateColorAsState(
        if (chromeActive) ScrubberActiveRed else played,
        tween(160, easing = Motion.Emphasized),
        label = "scrubPlayed",
    )
    val knobRadius by animateDpAsState(if (dragging) knobDragging else knob, label = "knob")
    val h = if (edgeToEdge) 2.dp else trackHeight
    val insetEnds = !edgeToEdge
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(touchHeight)
            .pointerInput(enabled, durationMs, relativeDrag, tapToSeek, edgeToEdge, trackHeight) {
                if (!enabled || durationMs <= 0) return@pointerInput
                val slop = viewConfiguration.touchSlop * 0.35f
                val endInset = if (edgeToEdge) 0f else (trackHeight / 2).toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val width = (size.width - endInset * 2f).coerceAtLeast(1f)
                    var fraction = (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
                    var dragged = false
                    var slopAccum = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                            if (dragged) onDraggingChanged(false)
                            break
                        }
                        if (!change.pressed) {
                            if (dragged) onSeek((fraction * durationMs).toLong())
                            else if (tapToSeek) {
                                val x = (change.position.x - endInset).coerceIn(0f, width)
                                onSeek((x / width * durationMs).toLong())
                            }
                            dragFraction = null
                            if (dragged) onDraggingChanged(false)
                            break
                        }
                        val delta = change.positionChange()
                        if (!dragged) {
                            slopAccum += delta
                            if (slopAccum.getDistance() < slop) continue
                            dragged = true
                            onDraggingChanged(true)
                            fraction = (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
                            dragFraction = fraction
                            onPreview((fraction * durationMs).toLong())
                        } else {
                            fraction = if (relativeDrag) {
                                (fraction + delta.x / width).coerceIn(0f, 1f)
                            } else {
                                ((change.position.x - endInset) / width).coerceIn(0f, 1f)
                            }
                            dragFraction = fraction
                            onPreview((fraction * durationMs).toLong())
                        }
                        change.consume()
                    }
                }
            },
    ) {
        val trackH = h.toPx()
        val centerY = if (edgeToEdge) edgeTouchPadAbove.toPx() else size.height / 2f
        val y = centerY - trackH / 2f
        val r = CornerRadius(trackH / 2f)
        val endInset = if (insetEnds) trackH / 2f else 0f
        val trackW = size.width - endInset * 2f
        val left = endInset
        drawRoundRect(track, Offset(left, y), Size(trackW, trackH), r)
        drawRoundRect(buffered, Offset(left, y), Size(trackW * bufferedFraction, trackH), r)
        drawRoundRect(playedColor, Offset(left, y), Size(trackW * playedFraction.coerceAtLeast(0f), trackH), r)
        segments.forEach { seg ->
            val x0 = left + trackW * (seg.startMs.toFloat() / duration).coerceIn(0f, 1f)
            val x1 = left + trackW * (seg.endMs.toFloat() / duration).coerceIn(0f, 1f)
            if (x1 > x0) drawRoundRect(seg.category.color, Offset(x0, y), Size(x1 - x0, trackH), r)
        }
        if (chromeActive) {
            val knobR = knobRadius.toPx()
            val knobX = (left + trackW * playedFraction).coerceIn(left + knobR, left + trackW - knobR)
            drawCircle(playedColor, radius = knobR, center = Offset(knobX, centerY))
        }
    }
}

@Composable
private fun GestureHUD(
    icon: ImageVector,
    label: String,
    percent: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(
                text = "$label ${(percent * 100).toInt().coerceIn(0, 100)}%",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
