package com.tensei.ytxtube

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.blur
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.exp
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW




internal val StageTopGap = 0.dp
internal val StageSideGap = 0.dp
internal val StageRadius = 0.dp
internal val MiniHeight = 66.dp
internal val MiniRadius = 20.dp
internal val MiniThumbStart = 8.dp
internal val MiniThumbWidth = 88.dp
internal val MiniThumbHeight = 50.dp
internal val MiniThumbRadius = 12.dp




internal const val StageMinAspect = 0.8f
internal const val StageMaxAspect = 2.4f

internal const val StageFrameAspect = 16f / 9f

internal const val AudioStageAspect = 2.5f

private const val DetailsFadeStart = 0.55f



@Composable
fun PlayerScreen(
    state: PlayerState,
    morph: PlayerMorph,
    onOpen: (VideoItem) -> Unit,
    onCollapse: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return


    val interactive = state.expanded || morph.dragging
    val showDetails by remember { derivedStateOf { state.expanded || morph.dragging || morph.progress.value > DetailsFadeStart } }
    val details = state.details
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var descriptionOpen by remember(item.id) { mutableStateOf(false) }

    val context = LocalContext.current
    var dominantColor by remember(item.id) { mutableStateOf(c.primaryContainer) }
    LaunchedEffect(item.thumbnail) {
        val thumb = item.thumbnail ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context).data(thumb).size(32).allowHardware(false).build()
                val bm = (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap(1, 1)
                bm?.getPixel(0, 0)?.let { pixel ->
                    withContext(Dispatchers.Main) {
                        dominantColor = Color(pixel)
                    }
                }
            }
        }
    }
    val ambientColor by animateColorAsState(dominantColor, tween(600), label = "ambient")
    val ambientMotion = androidx.compose.animation.core.rememberInfiniteTransition(label = "ambientDrift")
    val ambientDrift by ambientMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(14_000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "ambientDriftValue",
    )

    val isDark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    val pageColor = if (isAmoled) Color.Black else c.surface
    val miniColor = when {
        isAmoled -> Color(0xFF000000)
        isDark -> Color(0xFF1A1A1E)
        else -> Color(0xFFFFFFFF)
    }
    Box(
        Modifier
            .fillMaxSize()

            .graphicsLayer {
                val g = morph.geometry ?: return@graphicsLayer
                val p = morph.progress.value



                val full = Rect(Offset.Zero, size)
                val frame = Rect(
                    lerpF(g.miniCard.left, full.left, p), lerpF(g.miniCard.top, full.top, p),
                    lerpF(g.miniCard.right, full.right, p), lerpF(g.miniCard.bottom, full.bottom, ramp(p, 0.3f)),
                )
                shape = RoundRectClip(frame, lerpF(g.miniRadius, 0f, p.coerceIn(0f, 1f)))
                clip = true
            }
            .drawBehind {
                val p = morph.progress.value

                drawRect(lerpColor(miniColor, pageColor, ramp(p, 0f, 0.6f)))
            }
            .drawBehind {
                if (state.isMusicMode || size.width < 10f || size.height < 10f) return@drawBehind
                val topStrength = if (isAmoled) 0.40f else if (isDark) 0.35f else 0.22f
                val bodyStrength = if (isAmoled) 0.30f else if (isDark) 0.26f else 0.16f
                val aspect = state.stageAspect.coerceAtLeast(0.5f)
                val topRadius = (size.width * 0.95f).coerceAtLeast(10f)
                // Live moving blur effect above video in status bar
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(ambientColor.copy(alpha = topStrength), Color.Transparent),
                        center = Offset(size.width * (0.2f + 0.6f * ambientDrift), 0f),
                        radius = topRadius,
                    ),
                )
                // Color shade stretching down behind title, channel and fading away slowly after the like/action buttons row
                val startY = top.toPx() + (size.width / aspect * 0.4f).coerceAtLeast(0f)
                val glowReach = minOf(size.height * 0.58f, size.width * 1.35f).coerceAtLeast(startY + 50f)
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(
                            ambientColor.copy(alpha = bodyStrength * 0.85f),
                            ambientColor.copy(alpha = bodyStrength * 0.65f),
                            ambientColor.copy(alpha = bodyStrength * 0.25f),
                            Color.Transparent,
                        ),
                        startY = startY,
                        endY = glowReach,
                    ),
                )
                val bottomRadius = (size.width * 0.85f).coerceAtLeast(10f)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(ambientColor.copy(alpha = bodyStrength * 0.7f), Color.Transparent),
                        center = Offset(size.width * (0.85f - 0.7f * ambientDrift), top.toPx() + size.width / aspect + 100f),
                        radius = bottomRadius,
                    ),
                )
            }


            .then(if (interactive) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } } else Modifier),
    ) {
        ActionMessage(state, Modifier.align(Alignment.BottomCenter).zIndex(1f).padding(bottom = bottom + 24.dp))
        if (state.isMusicMode) {
            val p = morph.progress.value
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = ramp(p, 0.35f)
                        translationY = lerpF(48f, 0f, ramp(p, 0.35f))
                    }
            ) {
                BitChordMusicPlayer(
                    state = state,
                    onCollapse = onCollapse,
                    dominantColor = dominantColor,
                    top = top,
                    bottom = bottom,
                )
            }
        } else {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(top))


            Box(Modifier.zIndex(2f)) {
                VideoStage(state, morph, onCollapse, onFullscreen, interactive)
            }


            if (!showDetails) Spacer(Modifier.weight(1f)) else
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .zIndex(0f)
                    .nestedScroll(morph.nestedScroll)
                    .graphicsLayer {


                        val p = morph.progress.value
                        alpha = ramp(p, DetailsFadeStart)
                        val g = morph.geometry
                        translationY = if (g != null && p < 0.98f) {
                            lerpF(g.miniThumb.bottom - g.stage.bottom, 0f, p)
                        } else {
                            0f
                        }
                    },
                contentPadding = PaddingValues(bottom = bottom + 32.dp),
            ) {
                item(key = "info") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            item.title,
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            letterSpacing = (-0.4).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        val stats = listOfNotNull(
                            item.views.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
                            item.uploaded?.ifBlank { null },
                            details?.likes?.takeIf { it > 0 }?.let { "${formatCount(it)} likes" },
                        ).joinToString(" · ")
                        val hasDescription = !details?.description.isNullOrBlank()
                        Row(
                            modifier = if (hasDescription) Modifier.pressable(pressedScale = 0.98f) { descriptionOpen = !descriptionOpen } else Modifier,
                        ) {
                            Text(stats, color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (hasDescription) {
                                Text(
                                    if (descriptionOpen) "  less" else "  …more",
                                    color = c.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                        if (descriptionOpen && details != null) {
                            Text(
                                details.description,
                                modifier = Modifier.padding(top = 10.dp),
                                color = c.onSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val openChannel = LocalChannelOpener.current
                            val channelUrl = details?.item?.channelUrl ?: item.channelUrl

                            Row(
                                Modifier.weight(1f).pressable(enabled = channelUrl != null, pressedScale = 0.97f) { channelUrl?.let(openChannel) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            ChannelAvatar(details?.item?.avatar ?: item.avatar, item.channel, size = 46)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.channel, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                details?.subscribers?.takeIf { it > 0 }?.let {
                                    Text("${formatCount(it)} subscribers", color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            }
                            state.accountState?.subscribed?.let { subscribed ->
                                SubscribeButton(subscribed, onClick = state::toggleSubscribe)
                            }
                        }
                        PlayerActions(state)
                        QueueCard(state, onOpen = { state.sheet = PlayerSheet.Queue })
                        CommentsCard(state.comments, onOpen = { state.sheet = PlayerSheet.Comments })
                    }
                }
                val related = details?.related.orEmpty()
                if (related.isNotEmpty()) {
                    item(key = "moreTitle") { SectionTitle("More videos") }
                    items(related, key = { "rel-" + it.id }) { video ->
                        Box(Modifier.padding(horizontal = 20.dp)) { VideoRow(video, onClick = { onOpen(video) }) }
                    }
                } else if (state.loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                            CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }


        if (interactive) BottomGestureGuard(Modifier.align(Alignment.BottomCenter))


        MorphingMiniDetails(state, morph)
        }


        if (state.sheet == PlayerSheet.Comments) {
            val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            val videoBottom = top + top + (screenW - StageSideGap * 2) / state.stageAspect
            val item = state.details?.item
            CommentsPanel(
                state.comments,
                partialTop = videoBottom + 10.dp,
                onClose = { state.sheet = null },
                creatorAvatar = item?.avatar,
                creatorName = item?.channel.orEmpty(),
            )
        }
    }
}







@Composable
private fun VideoStage(state: PlayerState, morph: PlayerMorph, onCollapse: () -> Unit, onFullscreen: () -> Unit, interactive: Boolean) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()

    val aspect by animateFloatAsState(
        state.stageAspect,
        if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 380f),
        label = "stageAspect",
    )
    val pullUp = remember { Animatable(0f) }
    val pullDistance = with(LocalDensity.current) { FullscreenPull.toPx() }


    var decided by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }
    val stage = Modifier
        .padding(horizontal = StageSideGap)
        .fillMaxWidth()
        .aspectRatio(aspect)
        .graphicsLayer {

            val k = 1f + 0.06f * (1f - exp(-pullUp.value / (pullDistance * 1.5f)))
            scaleX = k
            scaleY = k
            transformOrigin = TransformOrigin(0.5f, 0f)
        }



        .graphicsLayer {
            val g = morph.geometry
            val p = morph.progress.value
            if (g != null) {
                val f = g.videoFrame(size, p)
                scaleX = f.scale
                scaleY = f.scale
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = f.offset.x
                translationY = f.offset.y


                shape = RoundRectClip(f.window, lerpF(g.thumbRadius / f.startScale, g.stageRadius, p.coerceIn(0f, 1f)))
            } else {
                shape = RoundedCornerShape(StageRadius)
            }

            clip = false

            shadowElevation = 14.dp.toPx() * (1f - p).coerceIn(0f, 1f)
            ambientShadowColor = Color.Black.copy(alpha = 0.25f)
            spotShadowColor = Color.Black.copy(alpha = 0.35f)
        }
        .draggable(
            state = rememberDraggableState { delta ->
                if (!decided) {
                    decided = true
                    pulling = delta < 0f && state.expanded && !state.fullscreen && !AudioOnly.enabled && morph.progress.value >= 0.999f
                }
                if (pulling) scope.launch(start = CoroutineStart.UNDISPATCHED) { pullUp.snapTo((pullUp.value - delta).coerceAtLeast(0f)) }
                else morph.dragBy(delta)
            },
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                val wasPulling = pulling
                decided = false
                pulling = false
                if (wasPulling) {
                    if (pullUp.value > pullDistance || velocity < -PlayerMorph.FLING_PX_PER_S) onFullscreen()
                    if (reduced) pullUp.snapTo(0f) else pullUp.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                } else {
                    morph.settle(velocity, reduced)
                }
            },
        )

    val controlsAlpha = { ramp(morph.progress.value, 0.7f) }
    if (AudioOnly.enabled) {
        AudioOnlyStage(
            state = state,
            interactive = interactive,
            onInactiveTap = { state.expanded = true },
            progress = { morph.progress.value },
            controlsAlpha = controlsAlpha,
            modifier = stage,
        )
    } else {
        VideoContent(
            state = state,
            fullscreen = false,
            interactive = interactive,

            onInactiveTap = { state.expanded = true },
            onCollapse = onCollapse,
            onToggleFullscreen = onFullscreen,
            controlsAlpha = controlsAlpha,
            portraitEdgeScrub = true,
            modifier = stage,
        )
    }
}


private val FullscreenPull = 96.dp



@Composable
private fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()


    val radius by animateDpAsState(if (subscribed) 16.dp else 22.dp, if (reduced) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f), label = "subShape")
    Row(
        modifier = Modifier
            .squishOn(subscribed)
            .height(44.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .background(if (subscribed) c.secondaryContainer else c.onSurface)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = if (subscribed) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(Icons.Rounded.NotificationsActive, null, active = subscribed, kind = Flourish.Ring, tint = c.onSecondaryContainer, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            color = if (subscribed) c.onSecondaryContainer else c.surface,
            fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
        )
    }
}





@Composable
private fun PlayerActions(state: PlayerState) {
    val context = LocalContext.current
    val item = state.current
    Row(
        Modifier
            .padding(top = 10.dp)
            .bleed(20.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountActions(state)
        if (item != null) {
            ActionPill(
                icon = Icons.Rounded.Download,
                label = if (item.localUri != null) "Downloaded" else "Download",
                active = item.localUri != null,
                flourish = Flourish.Pop,
                shape = RoundedCornerShape(18.dp),
                description = "Download video",
            ) {
                if (item.localUri != null) {
                    state.actionMessage = "Playing from your downloads"
                } else if (state.isMusicMode) {


                    val queued = Downloads.enqueueMusic(item)
                    state.actionMessage = if (queued) {
                        "Downloading audio: ${item.title}"
                    } else {
                        "Already queued or downloaded"
                    }
                } else {
                    state.downloadFor = item
                }
            }
            ActionPill(
                icon = Icons.Rounded.Share,
                label = "Share",
                active = false,
                flourish = Flourish.Pop,
                shape = RoundedCornerShape(18.dp),
                description = "Share video",
            ) {
                val url = "https://youtu.be/${item.id}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                context.startActivity(Intent.createChooser(intent, item.title))
            }
        }
    }
}


private fun Modifier.bleed(x: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val extra = (x * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}


@Composable
private fun AccountActions(state: PlayerState) {
    val signedIn = state.accountState != null
    val like = state.currentLike
    val likes = state.displayLikes
    var lastLikes by remember { mutableStateOf(likes) }
    val rising = likes >= lastLikes
    SideEffect { lastLikes = likes }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ActionPill(
            icon = if (like == LikeStatus.Like) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
            label = likes.takeIf { it > 0 }?.let { formatCount(it) },
            active = like == LikeStatus.Like,
            flourish = Flourish.Pop,
            labelRollsUp = rising,
            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            description = "Like",
        ) {
            state.toggleLike()
        }
        ActionPill(
            icon = if (like == LikeStatus.Dislike) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
            label = state.displayDislikes.takeIf { it >= 0 }?.let { formatCount(it) },
            active = like == LikeStatus.Dislike,
            flourish = Flourish.Droop,
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp),
            description = "Dislike",
        ) {
            state.toggleDislike()
        }
    }
    ActionPill(
        icon = if (state.inWatchLater) Icons.Rounded.WatchLater else Icons.Outlined.WatchLater,
        label = if (state.inWatchLater) "Saved" else "Save",
        active = state.inWatchLater,
        flourish = Flourish.Drop,
        shape = RoundedCornerShape(18.dp),
        description = "Save to Watch later",
    ) {
        if (signedIn) state.toggleWatchLater()
        else state.actionMessage = "Sign in to save videos"
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    active: Boolean,
    flourish: Flourish,
    shape: androidx.compose.ui.graphics.Shape,
    description: String,
    labelRollsUp: Boolean = true,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(if (active) c.primary else c.surfaceContainerHigh, tween(220), label = "pillBg")
    val tint by androidx.compose.animation.animateColorAsState(if (active) c.onPrimary else c.onSurface, tween(220), label = "pillInk")
    Row(
        modifier = Modifier
            .squishOn(active)
            .height(36.dp)
            .pressable(onClick = onClick)

            .background(bg, shape)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlourishIcon(icon, description, active = active, kind = flourish, tint = tint, size = 18.dp, sparkColor = c.primary)
        if (label != null) {
            Spacer(Modifier.width(5.dp))
            RollingText(label, tint, up = labelRollsUp, fontSize = 12.5.sp)
        }
    }
}


@Composable
private fun ActionMessage(state: PlayerState, modifier: Modifier) {
    val message = state.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_500)
        state.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(CircleShape)
            .background(c.inverseSurface)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) { Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}



@Composable
fun MiniPlayer(
    state: PlayerState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    showVideo: Boolean = true,
    backdrop: Backdrop? = null,
) {
    val c = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val item = state.current ?: return
    val isAmoled = ThemeSettings.mode == "amoled"
    val shape = RoundedCornerShape(MiniRadius)

    val glassTint = when {
        isAmoled -> Color(0xFF000000).copy(alpha = 0.6f)
        isDark -> Color(0xFF16161A).copy(alpha = 0.65f)
        else -> Color(0xFFFFFFFF).copy(alpha = 0.72f)
    }
    val glassBorder = when {
        isAmoled -> Color(0xFF333333).copy(alpha = 0.7f)
        isDark -> Color(0xFFFFFFFF).copy(alpha = 0.14f)
        else -> Color(0xFF000000).copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniHeight)
            .pressable(pressedScale = 0.98f, onClick = onExpand)
            .shadow(
                elevation = if (isAmoled) 0.dp else if (isDark) 16.dp else 12.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.16f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.22f),
            )
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(18f.dp.toPx())
                            lens(18f.dp.toPx(), 18f.dp.toPx(), chromaticAberration = true)
                        },
                        onDrawSurface = { drawRect(glassTint) },
                    )
                } else {
                    Modifier.background(glassTint)
                }
            )
            .border(1.dp, glassBorder, shape)
            .clip(shape)
    ) {
        if (state.isMusicMode) {
            BitChordMiniDetails(state = state, interactive = true, backdrop = backdrop)
        } else {
            Row(
                Modifier.fillMaxSize().padding(start = MiniThumbStart, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(width = MiniThumbWidth, height = MiniThumbHeight)
                        .clip(RoundedCornerShape(MiniThumbRadius))
                        .background(Color.Black),
                ) {
                    if (showVideo) {
                        Thumbnail(item.thumbnail, Modifier.fillMaxSize(), radius = 0)
                        if (!AudioOnly.enabled) {
                            Box(Modifier.fillMaxSize().aspectFit(state.stageAspect, cover = true)) {
                                LocalVideoSurface.current(Modifier.fillMaxSize().aspectFit(state.videoAspect))
                            }
                        }
                    }
                }
                Spacer(Modifier.width(11.dp))
                MiniDetails(state, interactive = true)
            }
        }
    }
}








@Composable
private fun RowScope.MiniDetails(state: PlayerState, interactive: Boolean) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    Box(Modifier.weight(1f).fillMaxHeight()) {
        Row(
            Modifier.fillMaxSize().padding(bottom = 3.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.channel,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.onSurfaceVariant.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(2.dp))

            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .pressable(enabled = interactive, onClick = state::togglePlay),
                contentAlignment = Alignment.Center
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = c.onSurface,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = c.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .pressable(enabled = interactive, onClick = state::close),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    "Close",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(21.dp)
                )
            }
        }


        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(c.onSurface.copy(alpha = 0.1f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFF2D55), Color(0xFFFF0000)),
                        ),
                    ),
            )
        }
    }
}







@Composable
private fun MorphingMiniDetails(state: PlayerState, morph: PlayerMorph) {
    val g = morph.geometry ?: return
    Row(
        Modifier



            .layout { measurable, _ ->
                val p = morph.progress.value
                val gap = 12.dp.toPx()
                val f = g.videoFrame(g.stage.size, p)
                val start = g.stage.left + f.offset.x + f.scale * f.window.right + gap
                val videoCenterY = g.stage.top + f.offset.y + f.scale * f.window.center.y
                val frameRight = lerpF(g.miniCard.right, g.screen.width, p)
                val width = (frameRight - 8.dp.toPx() - start).coerceAtLeast(0f).toInt()
                val height = MiniHeight.roundToPx()
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
                layout(width, height) {
                    placeable.placeRelative(start.toInt(), (videoCenterY - height / 2f).toInt())
                }
            }
            .graphicsLayer { alpha = 1f - ramp(morph.progress.value, 0f, 0.3f) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isMusicMode) {
            Box(Modifier.fillMaxSize()) {
                BitChordMiniDetails(state, interactive = false)
            }
        } else {
            MiniDetails(state, interactive = false)
        }
    }
}

@Composable
fun BitChordMusicPlayer(
    state: PlayerState,
    onCollapse: () -> Unit,
    dominantColor: Color,
    top: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    val context = LocalContext.current
    var showTrackMenu by remember(item.id) { mutableStateOf(false) }
    var showLyrics by remember(item.id) { mutableStateOf(false) }
    var lyrics by remember(item.id) { mutableStateOf<LyricsDoc?>(null) }
    var lyricsLoading by remember(item.id) { mutableStateOf(false) }
    var englishScript by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id, showLyrics) {
        if (showLyrics && lyrics == null && !lyricsLoading) {
            lyricsLoading = true
            lyrics = runCatching {
                InnerTube.musicLyrics(item.id, item.title, item.channel, item.durationSec)
            }.getOrNull()
            lyricsLoading = false
        }
    }
    val albumScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1.0f else 0.92f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "album_scale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(c.surface),
    ) {

        AsyncImage(
            model = highResMusicThumbnail(item.thumbnail) ?: sharpenFeedThumbnail(null, item.id),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(72.dp)
                .graphicsLayer {
                    alpha = 0.42f
                    scaleX = 1.35f
                    scaleY = 1.35f
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            dominantColor.copy(alpha = 0.52f),
                            c.surface.copy(alpha = 0.78f),
                            c.surface,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = top, bottom = bottom + 16.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = c.onSurface,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "YTX MUSIC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = c.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                    Text(
                        "Now Playing",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.onSurface,
                    )
                }
                IconButton(onClick = { showTrackMenu = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = c.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.weight(if (showLyrics) 0.12f else 0.5f))


            if (showLyrics) {
                MusicLyricsPanel(
                    lyrics = lyrics,
                    loading = lyricsLoading,
                    positionMs = state.positionMs,
                    englishScript = englishScript,
                    onToggleEnglish = { englishScript = !englishScript },
                    onSeek = { state.seekTo(it) },
                    onDismiss = { showLyrics = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (showLyrics) 7f else 3.5f),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = albumScale
                            scaleY = albumScale
                        }
                        .shadow(
                            elevation = 30.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = dominantColor.copy(alpha = 0.55f),
                            spotColor = Color.Black.copy(alpha = 0.7f),
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .background(c.surfaceContainerHighest)
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.06f),
                                ),
                            ),
                            shape = RoundedCornerShape(28.dp),
                        ),
                ) {
                    AsyncImage(
                        model = highResMusicThumbnail(item.thumbnail) ?: sharpenFeedThumbnail(null, item.id),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.weight(if (showLyrics) 0.12f else 0.5f))


            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.channel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                val liked = state.accountState?.like == LikeStatus.Like
                IconButton(onClick = state::toggleLike) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (liked) Color(0xFFFF2D55) else c.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))


            val duration = state.durationMs.coerceAtLeast(1L)
            val currentPos = state.positionMs.coerceIn(0L, duration)
            var dragPos by remember { mutableStateOf<Float?>(null) }
            val sliderValue = dragPos ?: (currentPos.toFloat() / duration.toFloat())

            Slider(
                value = sliderValue.coerceIn(0f, 1f),
                onValueChange = { dragPos = it },
                onValueChangeFinished = {
                    dragPos?.let {
                        state.seekTo((it * duration).toLong())
                    }
                    dragPos = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = c.onSurface,
                    activeTrackColor = c.onSurface,
                    inactiveTrackColor = c.onSurface.copy(alpha = 0.2f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatDuration((dragPos?.let { (it * duration).toLong() } ?: currentPos)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurfaceVariant.copy(alpha = 0.75f),
                )
                Text(
                    formatDuration(duration),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }

            Spacer(Modifier.height(12.dp))


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {

                IconButton(
                    onClick = state::toggleShuffle,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffleEnabled) c.primary else c.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp),
                    )
                }


                IconButton(
                    onClick = state::skipPrevious,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = c.onSurface,
                        modifier = Modifier.size(36.dp),
                    )
                }


                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(c.onSurface)
                        .pressable(pressedScale = 0.92f, onClick = state::togglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            color = c.surface,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp),
                        )
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = c.surface,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }


                IconButton(
                    onClick = state::skipNext,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = c.onSurface,
                        modifier = Modifier.size(36.dp),
                    )
                }


                IconButton(
                    onClick = state::toggleLoop,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (state.loopEnabled) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (state.loopEnabled) c.primary else c.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            if (!showLyrics) {
                Spacer(Modifier.height(16.dp))


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.onSurface.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            "HQ AUDIO · 256 KBPS AAC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = c.onSurfaceVariant,
                        )
                    }


                    IconButton(
                        onClick = { showLyrics = !showLyrics },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Lyrics,
                            contentDescription = "Lyrics",
                            tint = if (showLyrics) c.primary else c.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }

    if (showTrackMenu) {
        MusicTrackOptionsSheet(
            track = item,
            onPlayNext = { state.playNextMusic(item) },
            onDownloadAudio = {
                val queued = Downloads.enqueueMusic(item)
                Toast.makeText(
                    context,
                    if (queued) "Downloading audio: ${item.title}" else "Already queued or downloaded",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${item.id}")
                context.startActivity(Intent.createChooser(send, item.title))
            },
            onDismiss = { showTrackMenu = false },
        )
    }
}






@Composable
private fun MusicLyricsPanel(
    lyrics: LyricsDoc?,
    loading: Boolean,
    positionMs: Long,
    englishScript: Boolean,
    onToggleEnglish: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 330f),
        label = "lyrics-entrance",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = entrance
                val s = 0.95f + 0.05f * entrance
                scaleX = s
                scaleY = s
                translationY = (1f - entrance) * 22.dp.toPx()
            }
            .clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainerHigh.copy(alpha = 0.8f))
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 8.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "LYRICS",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.primary,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (englishScript) c.primary.copy(alpha = 0.18f)
                            else c.onSurface.copy(alpha = 0.06f),
                        )
                        .pressable(onClick = onToggleEnglish)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Translate,
                        contentDescription = "Show lyrics in English letters",
                        tint = if (englishScript) c.primary else c.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "EN",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (englishScript) c.primary else c.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(30.dp).clip(CircleShape).pressable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close lyrics",
                        tint = c.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp)
                }
                lyrics == null || lyrics.lines.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No lyrics available for this song.",
                        color = c.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                lyrics.synced -> SyncedLyrics(
                    lyrics = lyrics,
                    positionMs = positionMs,
                    englishScript = englishScript,
                    onSeek = onSeek,
                )
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 8.dp),
                ) {
                    Text(
                        if (englishScript) lyrics.lines.joinToString("\n") { transliterate(it.text) } else lyrics.plain,
                        fontSize = 16.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.onSurface.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: LyricsDoc,
    positionMs: Long,
    englishScript: Boolean,
    onSeek: (Long) -> Unit,
) {
    val c = MaterialTheme.colorScheme

    val lines = remember(lyrics, englishScript) {
        lyrics.lines.map { if (englishScript) it.copy(text = transliterate(it.text)) else it }
    }
    val current = lines.indexOfLast { it.timeMs in 0..positionMs }
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()

    // Apple Music keeps the line being sung pinned to the middle of the pane and glides there
    // with a soft spring instead of jumping. A short lead makes the highlight land with the
    // first syllable rather than a beat after it.
    LaunchedEffect(current, dragged) {
        if (current < 0 || dragged) return@LaunchedEffect
        val info = listState.layoutInfo
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        val visible = info.visibleItemsInfo.firstOrNull { it.index == current }
        if (visible != null) {
            val delta = visible.offset + visible.size / 2f - center
            if (kotlin.math.abs(delta) > 1f) {
                listState.animateScrollBy(
                    delta,
                    spring(dampingRatio = 0.9f, stiffness = 210f),
                )
            }
        } else {
            listState.scrollToItem(current)
            val settled = listState.layoutInfo
            val item = settled.visibleItemsInfo.firstOrNull { it.index == current }
            if (item != null) {
                val mid = (settled.viewportStartOffset + settled.viewportEndOffset) / 2f
                listState.animateScrollBy(
                    item.offset + item.size / 2f - mid,
                    spring(dampingRatio = 0.9f, stiffness = 210f),
                )
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(end = 8.dp)) {
        val edge = (maxHeight / 2 - 16.dp).coerceAtLeast(20.dp)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = edge),
        ) {
            itemsIndexed(lines, key = { i, line -> "$i-${line.timeMs}" }) { index, line ->
                val active = index == current
                val activation by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 430f),
                    label = "lyric-line",
                )
                val resting = when {
                    index < current -> 0.7f
                    else -> 0.58f
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (line.timeMs >= 0) onSeek(line.timeMs) }
                        .padding(vertical = 7.dp),
                ) {
                    Text(
                        text = line.text,
                        color = lerpColor(
                            c.onSurfaceVariant.copy(alpha = resting),
                            c.onSurface,
                            activation,
                        ),
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val scale = 0.95f + 0.11f * activation
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                alpha = 0.8f + 0.2f * activation
                            },
                    )
                }
            }
        }

        // Soft edges so upcoming and finished lines dissolve instead of being cut off.
        val fade = c.surfaceContainerHigh.copy(alpha = 0.82f)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(30.dp)
                .background(Brush.verticalGradient(listOf(fade, fade.copy(alpha = 0f)))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(30.dp)
                .background(Brush.verticalGradient(listOf(fade.copy(alpha = 0f), fade))),
        )
    }
}


private fun transliterate(text: String): String {
    if (text.isBlank()) return text
    if (android.os.Build.VERSION.SDK_INT < 29) return text
    return runCatching {
        android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII").transliterate(text)
    }.getOrDefault(text)
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.BitChordMiniDetails(
    state: PlayerState,
    interactive: Boolean,
    backdrop: Backdrop? = null,
) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    val glassCircle = if (backdrop != null) {
        Modifier
            .clip(CircleShape)
            .liquidGlass(
                backdrop,
                glassSpec(
                    shape = CircleShape,
                    tint = glassFill(0.45f),
                    blur = 12.dp,
                    refractionHeight = 7.dp,
                    refractionAmount = 10.dp,
                ),
            )
    } else {
        Modifier
    }
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceContainerHighest),
        ) {
            Thumbnail(highResMusicThumbnail(item.thumbnail), Modifier.fillMaxSize(), radius = 10, videoId = item.id)
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = c.onSurface,
            )
            Text(
                item.channel,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = c.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))

        Box(
            Modifier
                .size(38.dp)
                .pressable(enabled = interactive, onClick = state::togglePlay)
                .then(glassCircle),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isBuffering) {
                CircularProgressIndicator(
                    color = c.onSurface,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = c.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Box(
            Modifier
                .size(38.dp)
                .pressable(enabled = interactive, onClick = state::skipNext)
                .then(glassCircle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = c.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }

        Box(
            Modifier
                .size(36.dp)
                .pressable(enabled = interactive, onClick = state::close)
                .then(glassCircle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Close",
                tint = c.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(progress)
            .height(2.dp)
            .background(c.primary),
    )
}
