package com.thinker.flashtube

import android.content.Intent
import android.net.Uri
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo


@Stable
private class ShortsFeedState(private val scope: kotlinx.coroutines.CoroutineScope) {
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var next: String? = null
    private var ended = false

    fun load() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            runCatching { InnerTube.shorts() }
                .onSuccess {
                    videos = it.videos
                    next = it.next
                    ended = it.next == null
                    if (videos.isEmpty()) error = "No Shorts found for this account."
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    val feed = remember { ShortsFeedState(scope) }
    LaunchedEffect(Unit) { if (feed.videos.isEmpty()) feed.load() }

    val pager = rememberPagerState(pageCount = { feed.videos.size })
    val page = if (feed.videos.isEmpty()) 0 else pager.settledPage.coerceIn(0, feed.videos.lastIndex)
    val current = feed.videos.getOrNull(page)

    LaunchedEffect(feed.videos.size) {
        if (feed.videos.isNotEmpty() && page >= feed.videos.lastIndex - 2) feed.loadMore()
    }

    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { exo.release() } }

    var info by remember { mutableStateOf<StreamInfo?>(null) }
    var playError by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(true) }
    var muted by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var liked by remember { mutableStateOf(false) }
    var disliked by remember { mutableStateOf(false) }
    var dislikes by remember { mutableLongStateOf(-1L) }
    var subscribed by remember { mutableStateOf(false) }



    val comments = remember { CommentsState(scope) }
    var commentsOpen by remember { mutableStateOf(false) }
    var resumeAfterComments by remember { mutableStateOf(false) }


    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) playError = null
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
        exo.pause()
        info = null
        playError = null
        position = 0L
        duration = 0L
        liked = false
        disliked = false
        dislikes = -1L
        subscribed = false

        launch {
            val count = withContext(Dispatchers.IO) { fetchDislikeCount(currentId) }
            if (current?.id == currentId) dislikes = count
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching { StreamCache.getOrLoad(currentId) }.getOrNull()
        }
        info = loaded
        if (loaded == null) {
            playError = "Couldn't load this Short."
            return@LaunchedEffect
        }
        val hls = loaded.hlsUrl?.ifBlank { null }
        val url = hls ?: StreamSelection.pick(loaded).let { it.muxed?.content ?: it.video?.content }
        if (url.isNullOrBlank()) {
            playError = "This Short has no playable stream."
            return@LaunchedEffect
        }
        exo.setMediaItem(
            if (hls != null) {
                MediaItem.Builder()
                    .setUri(hls)
                    .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                    .build()
            } else {
                MediaItem.fromUri(url)
            },
        )
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
                Box(Modifier.fillMaxSize().background(Color.Black)) {
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


                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (index == page) {
                                    if (exo.isPlaying) exo.pause() else exo.play()
                                }
                            },
                    )

                    if (index == page && !playing && playError == null) {
                        Box(
                            Modifier
                                .size(76.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp),
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
                                    scope.launch {
                                        val retryId = item.id
                                        val loaded = withContext(Dispatchers.IO) {
                                            runCatching { StreamCache.getOrLoad(retryId) }.getOrNull()
                                        }
                                        info = loaded
                                        val url = loaded?.hlsUrl?.ifBlank { null }
                                            ?: loaded?.let { StreamSelection.pick(it) }?.let { it.muxed?.content ?: it.video?.content }
                                        if (url != null) {
                                            playError = null
                                            exo.setMediaItem(MediaItem.fromUri(url))
                                            exo.prepare()
                                            exo.play()
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
                    )

                    if (index == page) {
                        ShortsRail(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = 8.dp,
                                    bottom = bottomInset + 6.dp,
                                ),
                            item = item,
                            info = info,
                            liked = liked,
                            disliked = disliked,
                            dislikes = dislikes,
                            onLike = {
                                if (!requireSignIn()) return@ShortsRail
                                val next = if (liked) LikeStatus.None else LikeStatus.Like
                                liked = next == LikeStatus.Like
                                disliked = false
                                scope.launch {
                                    runCatching { InnerTube.setLike(item.id, next) }.onFailure {
                                        liked = false
                                        Toast.makeText(context, "Couldn't update your like.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDislike = {
                                if (!requireSignIn()) return@ShortsRail
                                val next = if (disliked) LikeStatus.None else LikeStatus.Dislike
                                disliked = next == LikeStatus.Dislike
                                liked = false
                                scope.launch {
                                    runCatching { InnerTube.setLike(item.id, next) }.onFailure {
                                        disliked = false
                                    }
                                }
                            },
                            onComments = {
                                resumeAfterComments = exo.isPlaying
                                exo.pause()
                                comments.load(item.id)
                                commentsOpen = true
                            },
                            onShare = { share(item) },
                        )

                        ShortsDetails(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(
                                    start = 16.dp,
                                    end = 82.dp,
                                    bottom = bottomInset + 4.dp,
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
                                val url = info?.uploaderUrl ?: item.channelUrl
                                if (url != null) {


                                    Toast.makeText(context, info?.uploaderName ?: item.channel, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )


                        if (duration > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(bottom = bottomInset),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(Color.White.copy(alpha = 0.18f)),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f))
                                            .height(2.dp)
                                            .background(Color.White.copy(alpha = 0.9f)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (commentsOpen) {
            fun closeComments() {
                commentsOpen = false
                if (resumeAfterComments) exo.play()
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { closeComments() },
            )
            CommentsPanel(
                state = comments,
                partialTop = 96.dp,
                onClose = { closeComments() },
                creatorAvatar = info?.uploaderAvatars?.maxByOrNull { it.width }?.url ?: current?.avatar,
                creatorName = (info?.uploaderName ?: current?.channel).orEmpty(),
            )
        }
    }
}

@Composable
private fun ShortsTopBar(
    onBack: () -> Unit,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShortsCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Spacer(Modifier.width(8.dp))
        Text(
            "Shorts",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        ShortsCircleButton(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Sound", onToggleMute)
        Spacer(Modifier.width(6.dp))
        ShortsCircleButton(Icons.Rounded.Search, "Search", onSearch)
    }
}

@Composable
private fun ShortsCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ShortsRail(
    modifier: Modifier,
    item: VideoItem,
    info: StreamInfo?,
    liked: Boolean,
    disliked: Boolean,
    dislikes: Long,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ShortsAction(
            icon = Icons.Rounded.ThumbUp,
            label = countLabel(info?.likeCount ?: -1L),
            tint = if (liked) Color(0xFFFF2D55) else Color.White,
            onClick = onLike,
        )
        ShortsAction(
            icon = Icons.Rounded.ThumbDown,
            label = countLabel(dislikes, "Dislike"),
            tint = if (disliked) Color(0xFFFF2D55) else Color.White,
            onClick = onDislike,
        )
        ShortsAction(Icons.Rounded.Comment, "Comments", onClick = onComments)
        ShortsAction(Icons.Rounded.Share, "Share", onClick = onShare)
    }
}

@Composable
private fun ShortsAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .pressable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
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
                modifier = Modifier.weight(1f, fill = false),
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
