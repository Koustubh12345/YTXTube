package com.tensei.ytxtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.TextureView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.math.absoluteValue


@Stable
private class ShortsFeedState(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val seed: VideoItem? = null,
) {
    var videos by mutableStateOf(seed?.let { listOf(it) } ?: emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var next: String? = null
    private var ended = false
    private var loaded = false

    /** Every clip this session has shown, so a refresh can never repeat one. */
    private val seen = HashSet<String>()

    fun load() {
        if (loading || loaded) return
        loading = true
        error = null
        scope.launch {
            runCatching { InnerTube.shorts() }
                .onSuccess { page ->
                    videos = merge(page.videos)
                    seen += videos.map { it.id }
                    next = page.next
                    ended = page.next == null
                    loaded = true
                    if (videos.isEmpty()) error = "No Shorts found for this account."
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    /** Pull a brand new batch from the same personalised sources and start again from the top. */
    fun refresh() {
        if (refreshing || loading) return
        refreshing = true
        error = null
        scope.launch {
            val token = next
            val page = runCatching { InnerTube.newShorts(seen, token) }.getOrNull()
            if (page != null && page.videos.isNotEmpty()) {
                val fresh = page.videos.filter { it.id !in seen }
                if (fresh.isNotEmpty()) {
                    videos = fresh
                    seen += fresh.map { it.id }
                    next = page.next ?: token
                    ended = next == null
                    loaded = true
                    error = null
                } else {
                    error = "No new Shorts right now — tap refresh again."
                }
            } else {
                error = "Couldn't load new Shorts. Check your connection."
            }
            refreshing = false
        }
    }

    private fun merge(page: List<VideoItem>): List<VideoItem> {
        val merged = LinkedHashMap<String, VideoItem>()
        seed?.let { merged[it.id] = it }
        page.forEach { merged.putIfAbsent(it.id, it) }
        return merged.values.toList()
    }

    fun loadMore() {
        val token = next ?: return
        if (loading || ended) return
        loading = true
        scope.launch {
            runCatching { InnerTube.shorts(token) }
                .onSuccess { page ->
                    val known = videos.mapTo(HashSet()) { it.id }
                    videos = videos + page.videos.filter { known.add(it.id) }
                    seen += page.videos.map { it.id }
                    next = page.next
                    ended = page.next == null
                }
            loading = false
        }
    }
}

@Composable
fun ShortsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSearch: () -> Unit,
    bottomInset: Dp = 0.dp,
    startItem: VideoItem? = null,
    active: Boolean = true,
    onOpenChannel: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (lightStatus != null) controller.isAppearanceLightStatusBars = lightStatus
            if (lightNav != null) controller.isAppearanceLightNavigationBars = lightNav
        }
    }
    val feed = remember(startItem?.id) { ShortsFeedState(scope, startItem) }
    LaunchedEffect(feed) { feed.load() }

    val pager = rememberPagerState(pageCount = { feed.videos.size })
    val page = if (feed.videos.isEmpty()) 0 else pager.settledPage.coerceIn(0, feed.videos.lastIndex)
    val current = feed.videos.getOrNull(page)

    LaunchedEffect(feed.videos.size) {
        if (feed.videos.isNotEmpty() && page >= feed.videos.lastIndex - 2) feed.loadMore()
    }

    val exo = remember {
        buildShortsPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    val preloader = remember {
        buildShortsPlayer(context).apply {
            volume = 0f
            playWhenReady = false
        }
    }
    DisposableEffect(Unit) { onDispose { exo.release(); preloader.release() } }

    var resumeAfterOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) {
            resumeAfterOverlay = exo.playWhenReady
            exo.pause()
            preloader.pause()
        } else if (resumeAfterOverlay) {
            resumeAfterOverlay = false
            exo.playWhenReady = true
        }
    }

    var info by remember { mutableStateOf<StreamInfo?>(null) }
    var playError by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var liked by remember { mutableStateOf(false) }
    var disliked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableLongStateOf(-1L) }
    var dislikes by remember { mutableLongStateOf(-1L) }
    var likeBump by remember { mutableIntStateOf(0) }
    var dislikeBump by remember { mutableIntStateOf(0) }
    var subscribed by remember { mutableStateOf(false) }
    val comments = remember { CommentsState(scope) }
    var commentsOpen by remember { mutableStateOf(false) }
    var downloadOpen by remember { mutableStateOf(false) }
    var overlayWasPlaying by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    fun togglePlayback() {
        // While the player is buffering, a tap should keep playback wanted
        // instead of turning playWhenReady off and creating the old dead pause.
        if (exo.playWhenReady && exo.playbackState == Player.STATE_READY) {
            exo.pause()
        } else {
            exo.playWhenReady = true
        }
    }

    fun pauseForOverlay() {
        overlayWasPlaying = exo.playWhenReady
        if (overlayWasPlaying) exo.pause()
    }

    fun resumeAfterOverlayPanel() {
        if (overlayWasPlaying && active) exo.playWhenReady = true
        overlayWasPlaying = false
    }


    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            // The pause/play overlay follows playWhenReady, not isPlaying. While
            // ExoPlayer is buffering isPlaying is false even though playback is
            // wanted, which used to show a dead pause button that could not resume.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playing = playWhenReady
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) playing = true
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    loading = false
                    playError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                loading = false
                playError = error.message ?: "Playback failed."
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }
    LaunchedEffect(exo) {
        while (true) {
            position = exo.currentPosition.coerceAtLeast(0L)
            duration = exo.duration.takeIf { it > 0 } ?: 0L
            delay(200)
        }
    }

    val currentId = current?.id
    LaunchedEffect(currentId) {
        if (currentId == null) return@LaunchedEffect
        loading = true
        exo.pause()
        info = null
        playError = null
        position = 0L
        duration = 0L
        liked = false
        disliked = false
        likeCount = -1L
        dislikes = -1L
        likeBump = 0
        dislikeBump = 0
        subscribed = false
        commentsOpen = false
        downloadOpen = false
        scrubbing = false

        launch {
            val count = withContext(Dispatchers.IO) { fetchDislikeCount(currentId) }
            if (current?.id == currentId) dislikes = count
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching { StreamCache.getOrLoad(currentId) }.getOrNull()
        }
        info = loaded
        if (loaded == null) {
            loading = false
            playError = "Couldn't load this Short."
            return@LaunchedEffect
        }
        likeCount = loaded.likeCount
        val source = shortsSource(context, loaded)
        if (source == null) {
            loading = false
            playError = "This Short has no playable stream."
            return@LaunchedEffect
        }
        exo.setMediaSource(source)
        exo.prepare()
        exo.play()
        if (Account.signedIn) {
            runCatching { InnerTube.videoState(currentId) }.getOrNull()?.let { st ->
                liked = st.like == LikeStatus.Like
                disliked = st.like == LikeStatus.Dislike
                subscribed = st.subscribed == true
            }
        }
    }

    // Resolve the next Shorts while this one plays, and quietly buffer the upcoming
    // stream into the shared MediaCache so the swipe lands on already-downloaded bytes.
    LaunchedEffect(feed.videos, page) {
        val upcoming = feed.videos.drop(page + 1).take(3)
        if (upcoming.isEmpty()) {
            preloader.stop()
            preloader.clearMediaItems()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) { upcoming.forEach { StreamCache.prefetch(it.id) } }
        val next = upcoming.first()
        val loaded = withContext(Dispatchers.IO) {
            runCatching { StreamCache.getOrLoad(next.id) }.getOrNull()
        } ?: return@LaunchedEffect
        val source = shortsSource(context, loaded) ?: return@LaunchedEffect
        preloader.stop()
        preloader.setMediaSource(source)
        preloader.prepare()
    }

    LaunchedEffect(muted) { exo.volume = if (muted) 0f else 1f }

    fun requireSignIn(): Boolean {
        if (Account.signedIn) return true
        onSignIn()
        return false
    }

    fun share(item: VideoItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/shorts/${item.id}")
        }
        context.startActivity(Intent.createChooser(intent, "Share Short"))
    }

    val glassTint = glassOverlayFill(0.5f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (feed.videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (feed.loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            feed.error ?: "No Shorts right now.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        PillButton("Retry", { feed.load() })
                    }
                }
            }
        } else {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                pageSpacing = 0.dp,
            ) { index ->
                val item = feed.videos.getOrNull(index) ?: return@VerticalPager
                val pageOffset = ((pager.currentPage - index) + pager.currentPageOffsetFraction)
                    .absoluteValue.coerceIn(0f, 1f)
                val pageFocus = remember { FocusRequester() }
                var selfFocused by remember { mutableStateOf(false) }
                LaunchedEffect(index, page) {
                    if (TvMode.isTv && index == page && feed.videos.isNotEmpty()) {
                        runCatching { pageFocus.requestFocus() }
                    }
                }
                val backdrop = rememberLayerBackdrop()
                val glass = glassSpec(
                    shape = CircleShape,
                    tint = glassTint,
                    blur = 16.dp,
                    refractionHeight = 9.dp,
                    refractionAmount = 14.dp,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = 1f - 0.05f * pageOffset
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.3f * pageOffset
                        }
                        .then(
                            if (TvMode.isTv) Modifier
                                .focusRequester(pageFocus)
                                .onFocusChanged { selfFocused = it.isFocused }
                                .onPreviewKeyEvent { e ->
                                    if (!selfFocused || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.DirectionUp -> {
                                            if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (pager.currentPage < feed.videos.lastIndex) {
                                                scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                                            }
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            togglePlayback()
                                            true
                                        }
                                        Key.MediaPlayPause -> {
                                            togglePlayback()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            focusManager.moveFocus(FocusDirection.Right)
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .focusable()
                            else Modifier,
                        )
                        .background(Color.Black),
                ) {
                    // Everything the glass samples lives in this layer. The buttons are drawn
                    // outside it so a backdrop can never contain the surface that samples it.
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    if (index == page && playError == null) {


                        ShortsVideo(exo, Modifier.fillMaxSize())
                    } else {
                        AsyncImage(
                            model = item.thumbnail ?: feedThumbnailUrl(item.id),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                        if (index != page) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(64.dp).align(Alignment.Center),
                            )
                        }
                    }


                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                                ),
                            ),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                                ),
                            ),
                    )
                    }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (index == page) {
                                    togglePlayback()
                                }
                            },
                    )

                    if (index == page && playError == null && (loading || (buffering && exo.playWhenReady))) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(36.dp).align(Alignment.Center),
                        )
                    }

                    if (index == page && !playing && !loading && playError == null) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .align(Alignment.Center)
                                .pressable(pressedScale = 0.9f) { exo.playWhenReady = true }
                                .clip(CircleShape)
                                .liquidGlass(backdrop, glass),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }

                    if (playError != null && index == page) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(playError.orEmpty(), color = Color.White, fontSize = 14.sp)
                                Spacer(Modifier.height(12.dp))
                                PillButton("Retry", {
                                    info = null
                                    loading = true
                                    playError = null
                                    scope.launch {
                                        val retryId = item.id
                                        val loaded = withContext(Dispatchers.IO) {
                                            runCatching { StreamCache.getOrLoad(retryId) }.getOrNull()
                                        }
                                        info = loaded
                                        val source = loaded?.let { shortsSource(context, it) }
                                        if (source != null) {
                                            playError = null
                                            exo.setMediaSource(source)
                                            exo.prepare()
                                            exo.play()
                                        } else {
                                            loading = false
                                        }
                                    }
                                })
                            }
                        }
                    }

                    ShortsTopBar(
                        onBack = onBack,
                        muted = muted,
                        onToggleMute = { muted = !muted },
                        onSearch = onSearch,
                        refreshing = feed.refreshing,
                        onRefresh = {
                            feed.refresh()
                            scope.launch { if (feed.videos.isNotEmpty()) pager.scrollToPage(0) }
                        },
                        backdrop = backdrop,
                        glass = glass,
                    )

                    if (index == page) {
                        val currentLikeCount = if (likeCount >= 0) (likeCount + likeBump).coerceAtLeast(0L) else if (liked) 1L else -1L
                        val currentDislikes = if (dislikes >= 0) (dislikes + dislikeBump).coerceAtLeast(0L) else if (disliked) 1L else -1L
                        ShortsRail(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = 8.dp,
                                    bottom = bottomInset + 22.dp,
                                ),
                            item = item,
                            info = info,
                            liked = liked,
                            disliked = disliked,
                            likeCount = currentLikeCount,
                            dislikes = currentDislikes,
                            onLike = {
                                val beforeLiked = liked
                                val beforeDisliked = disliked
                                val next = !beforeLiked
                                liked = next
                                if (next) disliked = false
                                likeBump += (if (next) 1 else 0) - (if (beforeLiked) 1 else 0)
                                if (beforeDisliked) dislikeBump -= 1
                                if (Account.signedIn) {
                                    val target = if (next) LikeStatus.Like else LikeStatus.None
                                    scope.launch {
                                        runCatching { InnerTube.setLike(item.id, target) }
                                    }
                                }
                            },
                            onDislike = {
                                val beforeLiked = liked
                                val beforeDisliked = disliked
                                val next = !beforeDisliked
                                disliked = next
                                if (next) liked = false
                                dislikeBump += (if (next) 1 else 0) - (if (beforeDisliked) 1 else 0)
                                if (beforeLiked) likeBump -= 1
                                if (Account.signedIn) {
                                    val target = if (next) LikeStatus.Dislike else LikeStatus.None
                                    scope.launch {
                                        runCatching { InnerTube.setLike(item.id, target) }
                                    }
                                }
                            },
                            onShare = { share(item) },
                            onComments = {
                                pauseForOverlay()
                                comments.load(item.id)
                                commentsOpen = true
                            },
                            onDownload = {
                                pauseForOverlay()
                                downloadOpen = true
                            },
                            backdrop = backdrop,
                            glass = glass,
                        )

                        ShortsDetails(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(
                                    start = 16.dp,
                                    end = 82.dp,
                                    bottom = bottomInset + 20.dp,
                                ),
                            item = item,
                            info = info,
                            subscribed = subscribed,
                            onSubscribe = {
                                if (!requireSignIn()) return@ShortsDetails
                                val channelId = info?.uploaderUrl?.substringAfter("/channel/")?.substringBefore('?')
                                    ?.takeIf { it.startsWith("UC") }
                                if (channelId == null) {
                                    Toast.makeText(context, "Channel unavailable", Toast.LENGTH_SHORT).show()
                                    return@ShortsDetails
                                }
                                val target = !subscribed
                                subscribed = target
                                scope.launch {
                                    runCatching { InnerTube.setSubscribed(channelId, target) }.onFailure {
                                        subscribed = !target
                                        Toast.makeText(context, "Couldn't update subscription.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onOpenChannel = {
                                val url = info?.uploaderUrl
                                    ?: item.channelUrl
                                    ?: item.channelId()?.let { "https://www.youtube.com/channel/$it" }
                                if (url != null) {
                                    exo.pause()
                                    onOpenChannel(url)
                                } else {
                                    Toast.makeText(context, "Channel unavailable", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )


                        if (duration > 0) {
                            val shownPosition = if (scrubbing) scrubPosition else position
                            val fraction = (shownPosition.toFloat() / duration).coerceIn(0f, 1f)
                            Canvas(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(bottom = bottomInset + 2.dp)
                                    .height(20.dp)
                                    .pointerInput(duration) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            var target = (down.position.x / size.width * duration)
                                                .toLong().coerceIn(0L, duration)
                                            var moved = false
                                            scrubbing = true
                                            scrubPosition = target
                                            try {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    if (!change.pressed) break
                                                    if (kotlin.math.abs(change.position.x - down.position.x) > 4f) moved = true
                                                    if (moved) {
                                                        target = (change.position.x / size.width * duration)
                                                            .toLong().coerceIn(0L, duration)
                                                        scrubPosition = target
                                                        exo.seekTo(target)
                                                        change.consume()
                                                    }
                                                }
                                                if (!moved) exo.seekTo(target)
                                                position = target
                                            } finally {
                                                scrubbing = false
                                            }
                                        }
                                    },
                            ) {
                                val y = size.height / 2f
                                val stroke = 2.5.dp.toPx()
                                drawLine(
                                    Color.White.copy(alpha = 0.24f),
                                    Offset(0f, y),
                                    Offset(size.width, y),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    Color.White.copy(alpha = 0.92f),
                                    Offset(0f, y),
                                    Offset(size.width * fraction, y),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round,
                                )
                                drawCircle(
                                    Color.White,
                                    radius = if (scrubbing) 5.5.dp.toPx() else 4.dp.toPx(),
                                    center = Offset(size.width * fraction, y),
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = feed.refreshing,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 58.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "Finding new Shorts…",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (commentsOpen) {
            CommentsPanel(
                state = comments,
                partialTop = 0.dp,
                onClose = {
                    commentsOpen = false
                    resumeAfterOverlayPanel()
                },
                creatorAvatar = info?.uploaderAvatars?.maxByOrNull { it.width }?.url,
                creatorName = info?.uploaderName.orEmpty(),
            )
        }

        if (downloadOpen) {
            current?.let { short ->
                DownloadQualitySheet(
                    video = short,
                    onDownloadStarted = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = {
                        downloadOpen = false
                        resumeAfterOverlayPanel()
                    },
                )
            }
        }
    }
}

@Composable
private fun ShortsTopBar(
    onBack: () -> Unit,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onSearch: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    backdrop: Backdrop,
    glass: GlassSpec,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShortsCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, backdrop, glass)
        Spacer(Modifier.width(8.dp))
        Text(
            "Shorts",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        ShortsCircleButton(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Sound", onToggleMute, backdrop, glass)
        Spacer(Modifier.width(6.dp))
        ShortsCircleButton(
            Icons.Rounded.Refresh,
            if (refreshing) "Loading new Shorts" else "Load new Shorts",
            onRefresh,
            backdrop,
            glass,
            loading = refreshing,
            enabled = !refreshing,
        )
        Spacer(Modifier.width(6.dp))
        ShortsCircleButton(Icons.Rounded.Search, "Search", onSearch, backdrop, glass)
    }
}

@Composable
private fun ShortsCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    glass: GlassSpec,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .size(40.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .liquidGlass(backdrop, glass),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun ShortsRail(
    modifier: Modifier,
    item: VideoItem,
    info: StreamInfo?,
    liked: Boolean,
    disliked: Boolean,
    likeCount: Long,
    dislikes: Long,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onShare: () -> Unit,
    onComments: () -> Unit,
    onDownload: () -> Unit,
    backdrop: Backdrop,
    glass: GlassSpec,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShortsAction(
            icon = Icons.Rounded.ThumbUp,
            label = countLabel(likeCount, "Like"),
            tint = if (liked) Color(0xFFFF2D55) else Color.White,
            active = liked,
            backdrop = backdrop,
            glass = glass,
            onClick = onLike,
        )
        ShortsAction(
            icon = Icons.Rounded.ThumbDown,
            label = countLabel(dislikes, "Dislike"),
            tint = if (disliked) Color(0xFFFF2D55) else Color.White,
            active = disliked,
            backdrop = backdrop,
            glass = glass,
            onClick = onDislike,
        )
        ShortsAction(Icons.Rounded.Comment, "Comments", backdrop = backdrop, glass = glass, onClick = onComments)
        ShortsAction(Icons.Rounded.Download, "Download", backdrop = backdrop, glass = glass, onClick = onDownload)
        ShortsAction(Icons.Rounded.Share, "Share", backdrop = backdrop, glass = glass, onClick = onShare)
    }
}

@Composable
private fun ShortsAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    active: Boolean = false,
    backdrop: Backdrop,
    glass: GlassSpec,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 450f),
        label = "actionScale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .pressable(onClick = onClick)
                .clip(CircleShape)
                .liquidGlass(backdrop, glass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShortsDetails(
    modifier: Modifier,
    item: VideoItem,
    info: StreamInfo?,
    subscribed: Boolean,
    onSubscribe: () -> Unit,
    onOpenChannel: () -> Unit,
) {
    val channel = info?.uploaderName?.ifBlank { null } ?: item.channel.ifBlank { "YouTube" }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.pressable(onClick = onOpenChannel)) {
                ChannelAvatar(
                    info?.uploaderAvatars?.maxByOrNull { it.width }?.url ?: item.avatar,
                    channel,
                    size = 34,
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                channel,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .pressable(onClick = onOpenChannel),
            )
            if (!subscribed) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .pressable(onClick = onSubscribe)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Subscribe", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .pressable(onClick = onSubscribe)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Subscribed", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            info?.name?.ifBlank { null } ?: item.title,
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                "Shorts",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun countLabel(value: Long, fallback: String = "Like"): String = when {
    value < 0 -> fallback
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f).replace(".0M", "M")
    value >= 1_000 -> String.format("%.1fK", value / 1_000f).replace(".0K", "K")
    value <= 0 -> fallback
    else -> value.toString()
}





@Composable
private fun ShortsVideo(player: ExoPlayer, modifier: Modifier = Modifier) {
    var video by remember { mutableStateOf(VideoSize(0, 0)) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) video = videoSize
            }
        }
        player.addListener(listener)
        val current = player.videoSize
        if (current.width > 0 && current.height > 0) video = current
        onDispose { player.removeListener(listener) }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val ratio = if (video.width > 0 && video.height > 0) {
            video.width.toFloat() / video.height
        } else {
            9f / 16f
        }
        val containerRatio = if (maxHeight.value > 0f) maxWidth / maxHeight else ratio
        val videoWidth = if (ratio >= containerRatio) maxWidth else maxHeight * ratio
        val videoHeight = if (ratio >= containerRatio) maxWidth / ratio else maxHeight
        AndroidView(
            factory = { context ->



                TextureView(context).apply {


                    runCatching { player.setVideoTextureView(this) }
                }
            },
            modifier = Modifier.size(videoWidth, videoHeight),
            update = { },
            onRelease = { view -> runCatching { player.clearVideoTextureView(view) } },
        )
    }
}

@OptIn(UnstableApi::class)
private fun buildShortsPlayer(context: Context): ExoPlayer {
    val loadControl = DefaultLoadControl.Builder()
        .setAllocator(DefaultAllocator(true, 64 * 1024))
        .setBufferDurationsMs(
            1_000,
            10_000,
            150,
            400,
        )
        .setBackBuffer(5_000, false)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    return ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setBandwidthMeter(PlaybackBandwidth.get())
        .build()
}

@OptIn(UnstableApi::class)
private fun shortsSource(context: Context, info: StreamInfo): MediaSource? {
    val picked = StreamSelection.pickShorts(info)
    val dsFactory = MediaCache.dataSourceFactory(context)
    val progressive = ProgressiveMediaSource.Factory(dsFactory)

    val muxedUrl = picked.muxed?.content
    if (!muxedUrl.isNullOrBlank()) {
        return progressive.createMediaSource(MediaItem.fromUri(muxedUrl))
    }

    val videoUrl = picked.video?.content
    val audioUrl = picked.audio?.content
    if (!videoUrl.isNullOrBlank() && !audioUrl.isNullOrBlank()) {
        val videoSource = progressive.createMediaSource(MediaItem.fromUri(videoUrl))
        val audioSource = progressive.createMediaSource(MediaItem.fromUri(audioUrl))
        return MergingMediaSource(videoSource, audioSource)
    }

    val fallbackUrl = info.videoStreams.firstOrNull { it.isUrl }?.content
        ?: info.videoOnlyStreams.firstOrNull { it.isUrl }?.content
    return fallbackUrl?.let { progressive.createMediaSource(MediaItem.fromUri(it)) }
}

