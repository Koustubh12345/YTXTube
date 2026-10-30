package com.tensei.ytxtube

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    internal var playerHolder: PlayerState? = null
    internal var pipController: PipController? = null
    internal var intentHandler: ((Intent) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setHighestRefreshRate()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { YtxTubeTheme { YtxTubeApp() } }
    }

    override fun onResume() {
        super.onResume()
        setHighestRefreshRate()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = playerHolder
        val pip = pipController
        if (pip?.supported == true && p != null && p.isPlaying && !p.isMusicMode) {
            pip.enter(p.videoAspect.takeIf { it > 0 } ?: (16f / 9f))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentHandler?.invoke(intent)
    }

    private fun setHighestRefreshRate() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val disp = display ?: return
                val currentMode = disp.mode
                val modes = disp.supportedModes
                val bestMode = modes
                    .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                    .maxByOrNull { it.refreshRate }
                    ?: modes.maxByOrNull { it.refreshRate }
                if (bestMode != null && bestMode.refreshRate > currentMode.refreshRate) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = bestMode.modeId
                    lp.preferredRefreshRate = bestMode.refreshRate
                    window.attributes = lp
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val disp = windowManager.defaultDisplay ?: return
                val currentMode = disp.mode
                val modes = disp.supportedModes
                val bestMode = modes
                    .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                    .maxByOrNull { it.refreshRate }
                    ?: modes.maxByOrNull { it.refreshRate }
                if (bestMode != null && bestMode.refreshRate > currentMode.refreshRate) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = bestMode.modeId
                    window.attributes = lp
                }
            }
        }
    }
}

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Music("Music", Icons.Outlined.MusicNote, Icons.Rounded.MusicNote),
    Shorts("Shorts", Icons.Outlined.PlayCircle, Icons.Rounded.PlayCircle),
    Library("Library", Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
    Downloads("Saved", Icons.Outlined.Download, Icons.Rounded.Download),
}


private val NavHeight = 64.dp
private val NavWidth = 368.dp
private val NavLift = 10.dp
private val MiniWidth = 368.dp
private val MiniGap = 8.dp

@Composable
fun YtxTubeApp() {
    val c = MaterialTheme.colorScheme
    val player = rememberPlayerState()
    val scope = rememberCoroutineScope()
    val search = remember { SearchState(scope) }
    val lib = remember { LibraryData(scope) }
    var signingIn by remember { mutableStateOf(false) }
    var openSettings by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<PlaylistRef?>(null) }
    var openChannel by remember { mutableStateOf<String?>(null) }
    var shortsEntry by remember { mutableStateOf<VideoItem?>(null) }
    var openAllSubscriptions by remember { mutableStateOf(false) }
    var openSearch by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }


    LaunchedEffect(tab) { if (tab == Tab.Shorts && player.isPlaying) player.togglePlay() }
    LaunchedEffect(openSearch) { if (openSearch) search.reset() }

    var channelState by remember { mutableStateOf<ChannelState?>(null) }
    val playlistFeed = remember(openPlaylist?.id) { openPlaylist?.let { lib.playlistFeed(it.id) } }
    val updateState = remember { UpdateState(scope) }
    val appContext = LocalContext.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1_400)
        updateState.check()
    }
    LaunchedEffect(updateState.message) {
        updateState.message?.let { message ->
            android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
            updateState.message = null
        }
    }

    val scrollToTop = remember { mutableStateMapOf<Tab, Int>() }
    val reduced = rememberReducedMotion()
    val morph = remember { PlayerMorph(scope) { expanded -> player.expanded = expanded } }


    val pageBackdrop = rememberLayerBackdrop()
    val playerBackdrop = rememberLayerBackdrop()
    val glassBackdrop = rememberCombinedBackdrop(pageBackdrop, playerBackdrop)



    val rootView = LocalView.current
    DisposableEffect(player.isPlaying) {
        rootView.keepScreenOn = player.isPlaying
        onDispose { rootView.keepScreenOn = false }
    }




    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(player.expanded) {
        if (player.expanded) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }


    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> player.setBackground(true)
                androidx.lifecycle.Lifecycle.Event.ON_START -> player.setBackground(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val stackBottom = navBottom.coerceAtLeast(8.dp) +
        if (WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding() > 0.dp) 10.dp else 0.dp

    val navTop = stackBottom + NavLift + NavHeight
    var optionsFor by remember { mutableStateOf<VideoItem?>(null) }
    var downloadNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(downloadNotice) {
        if (downloadNotice != null) {
            kotlinx.coroutines.delay(4000)
            downloadNotice = null
        }
    }
    val hasMini = player.current != null

    val contentPadding = PaddingValues(
        bottom = navTop + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 16.dp,
    )


    val activity = LocalContext.current as Activity
    val mainActivity = activity as? MainActivity
    val orientation = rememberOrientationController(activity)
    val playerOpen = player.current != null && player.expanded

    orientation.Bind(playerOpen && !AudioOnly.enabled && !player.isMusicMode)
    val pip = remember(activity) { PipController(activity as ComponentActivity) }

    SideEffect {
        mainActivity?.playerHolder = player
        mainActivity?.pipController = pip
    }

    LaunchedEffect(player.isPlaying, player.isMusicMode, player.videoAspect) {
        if (player.isPlaying && !player.isMusicMode) {
            pip.updateAutoPip(true, player.videoAspect.takeIf { it > 0 } ?: (16f / 9f))
        } else {
            pip.updateAutoPip(false)
        }
    }

    fun handleIntent(inIntent: Intent?) {
        val target = parseIncomingIntent(inIntent) ?: return
        if (target.isShort) {
            shortsEntry = VideoItem(
                id = target.videoId,
                title = "",
                channel = "",
                channelUrl = null,
                thumbnail = "https://i.ytimg.com/vi/${target.videoId}/hqdefault.jpg",
                avatar = null,
                durationSec = 0L,
                views = 0L,
                uploaded = null,
            )
        } else {
            player.open(
                VideoItem(
                    id = target.videoId,
                    title = "",
                    channel = "",
                    channelUrl = null,
                    thumbnail = "https://i.ytimg.com/vi/${target.videoId}/hqdefault.jpg",
                    avatar = null,
                    durationSec = 0L,
                    views = 0L,
                    uploaded = null,
                )
            )
            player.expanded = true
        }
    }

    DisposableEffect(mainActivity) {
        mainActivity?.intentHandler = { handleIntent(it) }
        handleIntent(mainActivity?.intent)
        onDispose { mainActivity?.intentHandler = null }
    }

    pip.Bind(onClosed = { player.controller?.pause() })
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val fullscreenBeforePip = remember { mutableStateOf(false) }
    val showFullscreen = if (pip.active) fullscreenBeforePip.value
        else (playerOpen && (landscape || player.portraitFullscreen) && !player.isMusicMode)
    SideEffect { if (!pip.active) fullscreenBeforePip.value = showFullscreen }
    LaunchedEffect(showFullscreen) { player.fullscreen = showFullscreen }
    SystemBarsHidden(showFullscreen)

    fun exitFullscreen() {
        player.portraitFullscreen = false
        player.fullscreen = false
        // Collapse to the mini player as well. Previously only the orientation was
        // changed, so in landscape the fullscreen surface came straight back and
        // Back/minimise felt broken.
        player.expanded = false
        orientation.exitFullscreen()
    }

    fun toggleFullscreen() {
        when {
            showFullscreen -> exitFullscreen()
            // A vertical video fills the portrait screen instead of being letterboxed in landscape.
            player.portraitVideo -> player.portraitFullscreen = true
            else -> orientation.enterFullscreen()
        }
    }

    BackHandler(enabled = player.expanded) { player.expanded = false }
    BackHandler(enabled = !player.expanded && openSettings) { openSettings = false }
    BackHandler(enabled = !player.expanded && openAllSubscriptions && openChannel == null) { openAllSubscriptions = false }
    BackHandler(enabled = !player.expanded && tab != Tab.Home && openPlaylist == null && openChannel == null && !openAllSubscriptions) {
        tab = Tab.Home
    }
    BackHandler(enabled = !player.expanded && openSearch) { openSearch = false }
    BackHandler(enabled = !player.expanded && openPlaylist != null) { openPlaylist = null }
    BackHandler(enabled = signingIn) { signingIn = false }
    BackHandler(enabled = !player.expanded && openChannel != null) { openChannel = null }

    BackHandler(enabled = showFullscreen) { exitFullscreen() }
    BackHandler(enabled = shortsEntry != null) { shortsEntry = null }
    BackHandler(enabled = updateState.visible) { updateState.dismiss() }

    val canHandleRootBack = !player.expanded && !openSettings && !openAllSubscriptions &&
        tab == Tab.Home && openPlaylist == null && openChannel == null &&
        !openSearch && !signingIn && !showFullscreen && shortsEntry == null && !updateState.visible
    BackHandler(enabled = canHandleRootBack && player.isPlaying) {
        if (!player.isMusicMode && pip.supported) {
            val entered = pip.enter(player.videoAspect.takeIf { it > 0 } ?: (16f / 9f))
            if (!entered) activity.moveTaskToBack(true)
        } else {
            activity.moveTaskToBack(true)
        }
    }

    val density = LocalDensity.current



    LaunchedEffect(player.expanded, player.current?.id) {
        if (player.current == null) morph.progress.snapTo(0f) else morph.animateTo(player.expanded, reduced)
    }
    val showPlayer by remember {

        derivedStateOf { player.current != null && (player.expanded || morph.dragging || morph.progress.isRunning || morph.progress.value > 0f) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.surface)) {

        val stageAspect = player.stageAspect
        val stageTopPx = with(density) {
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
        }
        val geometry = remember(constraints.maxWidth, constraints.maxHeight, stackBottom, density, stageAspect, stageTopPx) {
            with(density) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val miniBottom = h - (navTop + MiniGap).toPx()
                val miniW = minOf(w - 32.dp.toPx(), MiniWidth.toPx())
                val miniLeft = (w - miniW) / 2f
                val miniCard = Rect(miniLeft, miniBottom - MiniHeight.toPx(), miniLeft + miniW, miniBottom)
                val thumb = Rect(
                    Offset(miniCard.left + MiniThumbStart.toPx(), miniCard.top + (MiniHeight - MiniThumbHeight).toPx() / 2f),
                    Size(MiniThumbWidth.toPx(), MiniThumbHeight.toPx()),
                )
                val side = StageSideGap.toPx()
                // The picture starts below the status bar; the status bar keeps the
                // animated ambient shade behind it instead of sitting on the video.
                val stageTop = stageTopPx
                val stage = Rect(side, stageTop, w - side, stageTop + (w - 2 * side) / stageAspect)
                MorphGeometry(
                    screen = Size(w, h),
                    miniCard = miniCard,
                    miniThumb = thumb,
                    stage = stage,
                    miniRadius = MiniRadius.toPx(),
                    thumbRadius = MiniThumbRadius.toPx(),
                    stageRadius = StageRadius.toPx(),
                )
            }
        }
        SideEffect { morph.geometry = geometry }



        val sharedSurface = remember(player) {
            androidx.compose.runtime.movableContentOf { m: Modifier -> VideoSurface(player.controller, m) }
        }
        CompositionLocalProvider(
            LocalVideoSurface provides if (pip.active) NoVideoSurface else sharedSurface,
            LocalPictureInPicture provides pip,
            LocalVideoMenu provides { optionsFor = it },
            LocalChannelOpener provides { url ->

                player.expanded = false
                if (channelState?.url != url) channelState = ChannelState(url, scope)
                openChannel = url
            },
        ) {
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop).background(c.surface)) {
        AnimatedContent(
            targetState = tab,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = if (TvMode.isTv) 1280.dp else Dp.Unspecified)
                .fillMaxHeight(),
            transitionSpec = {
                if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
                else (fadeIn(tween(Motion.PageEnterMs, easing = Motion.Emphasized)) +
                    slideInVertically(tween(Motion.PageRiseMs, easing = Motion.Emphasized)) { (it / 35).coerceIn(8, 24) }) togetherWith
                    fadeOut(tween(Motion.PageExitMs))
            },
            label = "tabs",
        ) { t ->
            when (t) {
                Tab.Home -> HomeScreen(
                    lib, contentPadding, onOpen = player::open, onSearch = { openSearch = true },
                    onSignIn = { signingIn = true }, onAccount = { openSettings = true },
                    scrollToTop = scrollToTop[Tab.Home] ?: 0,
                )
                Tab.Music -> MusicScreen(
                    lib, contentPadding, onOpen = player::openMusic, onSearch = { openSearch = true },
                    onAccount = { openSettings = true },
                    onPlayNext = player::playNextMusic,
                    onOpenQueue = { items, index -> player.openMusicQueue(items, index) },
                    scrollToTop = scrollToTop[Tab.Music] ?: 0,
                )
                Tab.Shorts -> ShortsScreen(
                    onBack = { tab = Tab.Home },
                    onSignIn = { signingIn = true },
                    onSearch = { openSearch = true },
                    bottomInset = navTop + 12.dp,
                    active = openChannel == null && !openSearch && !openSettings,
                    onOpenChannel = { url ->
                        player.expanded = false
                        if (channelState?.url != url) channelState = ChannelState(url, scope)
                        openChannel = url
                    },
                )
                Tab.Library -> LibraryScreen(
                    lib, contentPadding, onOpen = player::open, onOpenPlaylist = { ref -> if (ref.id == "DL") tab = Tab.Downloads else openPlaylist = ref },
                    onOpenChannel = { url ->
                        player.expanded = false
                        if (channelState?.url != url) channelState = ChannelState(url, scope)
                        openChannel = url
                    },
                    onOpenAllSubscriptions = { openAllSubscriptions = true },
                    onSearch = { openSearch = true },
                    onSignIn = { signingIn = true }, onAccount = { openSettings = true },
                    scrollToTop = scrollToTop[Tab.Library] ?: 0,
                )
                Tab.Downloads -> DownloadsScreen(
                    contentPadding,
                    onOpen = player::open,
                    onOpenMusic = player::openMusic,
                    onBrowse = { tab = Tab.Home },
                    onBrowseMusic = { tab = Tab.Music },
                    onSearch = { openSearch = true },
                    onAccount = { openSettings = true },
                    scrollToTop = scrollToTop[Tab.Downloads] ?: 0,
                )
            }
        }

        AnimatedVisibility(
            visible = openSearch,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            SearchScreen(
                search,
                contentPadding,
                onBack = { openSearch = false },
                onOpen = player::open,
            )
        }


        AnimatedVisibility(
            visible = openPlaylist != null,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            val pl = openPlaylist
            val feed = playlistFeed
            if (pl != null && feed != null) {
                PlaylistScreen(
                    pl, feed, contentPadding,
                    onBack = { openPlaylist = null },
                    onOpen = { video ->
                        if (pl.id == "MDL") player.openMusic(video)
                        else player.open(video)
                    },
                    channelAvatarSeed = lib.channels.associate { it.id to it.avatar },
                )
            }
        }


        AnimatedVisibility(
            visible = openAllSubscriptions,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            AllSubscriptionsScreen(
                channels = lib.channels,
                contentPadding = contentPadding,
                onBack = { openAllSubscriptions = false },
            )
        }


        AnimatedVisibility(
            visible = openChannel != null,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            channelState?.let { ch ->
                ChannelScreen(
                    ch,
                    contentPadding,
                    onBack = { openChannel = null },
                    onOpen = player::open,
                    onOpenShort = { video ->
                        player.expanded = false
                        shortsEntry = video
                    },
                    onMessage = { player.actionMessage = it },
                )
            }
        }


        AnimatedVisibility(
            visible = openSettings,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            SettingsScreen(
                onBack = { openSettings = false },
                onSignIn = { signingIn = true },
                onSignedOut = { lib.reset(); lib.home.refresh(); player.refreshAccountState() },
                onAccountSwitched = { lib.reset(); lib.home.refresh(); player.refreshAccountState() },
                contentPadding = contentPadding,
                onCheckUpdate = { updateState.check(manual = true) },
            )
        }


        BottomGestureGuard(Modifier.align(Alignment.BottomCenter))

        }

        if (hasMini && !openSettings && tab != Tab.Shorts && shortsEntry == null) {


            val dismiss = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(player.current?.id) { dismiss.snapTo(0f) }
            val dismissDistance = with(density) { 56.dp.toPx() }
            MiniPlayer(
                player,
                onExpand = { player.expanded = true },
                showVideo = !showPlayer && !showFullscreen,
                backdrop = pageBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = navTop + MiniGap)
                    .widthIn(max = MiniWidth)

                    .graphicsLayer {
                        translationY = dismiss.value


                        alpha = (if (showPlayer) 0f else 1f) * (1f - (dismiss.value / (dismissDistance * 1.6f)).coerceIn(0f, 1f))
                    }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            if (morph.progress.value <= 0f && (delta > 0f || dismiss.value > 0f)) {
                                scope.launch { dismiss.snapTo((dismiss.value + delta).coerceAtLeast(0f)) }
                            } else {
                                morph.dragBy(delta)
                            }
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (dismiss.value > 0f) {
                                if (dismiss.value > dismissDistance || velocity > 800f) {
                                    scope.launch {
                                        if (!reduced) dismiss.animateTo(dismissDistance * 2.2f, tween(200, easing = Motion.Emphasized))
                                        player.close()
                                    }
                                } else {
                                    scope.launch { dismiss.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 600f)) }
                                }
                            } else {
                                morph.settle(velocity, reduced)
                            }
                        },
                    ),
            )
        }

        val selectTab: (Tab) -> Unit = { selected ->
            when {

                selected == tab && openSearch -> openSearch = false
                selected == tab && openChannel != null -> openChannel = null
                selected == tab && openPlaylist != null -> openPlaylist = null
                selected == tab && openAllSubscriptions -> openAllSubscriptions = false
                selected == tab -> scrollToTop[selected] = (scrollToTop[selected] ?: 0) + 1
                else -> {
                    openPlaylist = null
                    openChannel = null
                    openAllSubscriptions = false
                    openSearch = false
                    tab = selected
                }
            }
        }


        val showBottomBar = !openSettings && !openSearch && shortsEntry == null
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(tween(260, easing = Motion.Emphasized)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(200, easing = Motion.Emphasized)) { it } + fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = stackBottom + NavLift)
                .widthIn(max = NavWidth)
                .fillMaxWidth(),
        ) {
            LiquidBottomBar(
                selectedTabIndex = { Tab.entries.indexOf(tab) },
                onTabSelected = { index -> Tab.entries.getOrNull(index)?.let(selectTab) },
                backdrop = pageBackdrop,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab.entries.forEach { t ->
                    LiquidNavTab(t, t == tab) { selectTab(t) }
                }
            }
        }



        shortsEntry?.let { entry ->
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                ShortsScreen(
                    onBack = { shortsEntry = null },
                    onSignIn = { signingIn = true },
                    onSearch = { openSearch = true },
                    bottomInset = 12.dp,
                    startItem = entry,
                    active = openChannel == null && !openSearch,
                    onOpenChannel = { url ->
                        shortsEntry = null
                        player.expanded = false
                        if (channelState?.url != url) channelState = ChannelState(url, scope)
                        openChannel = url
                    },
                )
            }
        }

        if (showFullscreen) {
            Box(Modifier.fillMaxSize().layerBackdrop(playerBackdrop)) {
                FullscreenPlayer(player, onExit = { exitFullscreen() })
            }
        } else if (showPlayer) {
            Box(Modifier.fillMaxSize().layerBackdrop(playerBackdrop)) {
                PlayerScreen(
                    player, morph,
                    onOpen = player::open,
                    onCollapse = { player.expanded = false },
                    onFullscreen = { toggleFullscreen() },
                    onSignIn = { signingIn = true },
                )
            }
        }

        if (signingIn) {
            SignInScreen(
                onDone = {
                    signingIn = false
                    lib.reset()
                    lib.home.refresh()
                    lib.loadChannels()
                    player.refreshAccountState()
                },
                onCancel = { signingIn = false },
            )
        }

        when (player.sheet) {
            PlayerSheet.Quality -> QualitySheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Subtitles -> SubtitlesSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Settings -> Unit
            PlayerSheet.Comments -> Unit
            PlayerSheet.Queue -> QueueSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Speed -> Unit
            PlayerSheet.Audio -> Unit
            null -> Unit
        }

        AnimatedVisibility(
            visible = player.sheet == PlayerSheet.Settings,
            enter = fadeIn(tween(180, easing = Motion.Emphasized)),
            exit = fadeOut(tween(150)),
        ) {
            PlayerSettingsSheet(player, glassBackdrop, onDismiss = { player.sheet = null })
        }

        AnimatedVisibility(
            visible = player.sheet == PlayerSheet.Speed,
            enter = fadeIn(tween(180, easing = Motion.Emphasized)),
            exit = fadeOut(tween(150)),
        ) {
            SpeedSheet(player, glassBackdrop, onDismiss = { player.sheet = null })
        }

        AnimatedVisibility(
            visible = player.sheet == PlayerSheet.Audio,
            enter = fadeIn(tween(180, easing = Motion.Emphasized)),
            exit = fadeOut(tween(150)),
        ) {
            AudioTrackSheet(player, glassBackdrop, onDismiss = { player.sheet = null })
        }



        player.downloadFor?.let { video ->
            DownloadQualitySheet(
                video = video,
                onDownloadStarted = { msg -> downloadNotice = msg },
                onDismiss = { player.downloadFor = null },
            )
        }

        AnimatedVisibility(
            visible = downloadNotice != null,
            enter = slideInVertically(tween(280, easing = Motion.Emphasized)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200, easing = Motion.Emphasized)) { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navTop + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 12.dp)
                .padding(horizontal = 20.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = c.surfaceContainerHighest,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.widthIn(max = 420.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        downloadNotice.orEmpty(),
                        color = c.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            tab = Tab.Downloads
                            downloadNotice = null
                            if (showPlayer) player.expanded = false
                        },
                    ) {
                        Text("VIEW", fontWeight = FontWeight.Bold, color = c.primary, fontSize = 13.sp)
                    }
                }
            }
        }
        optionsFor?.let { video -> VideoOptionsSheet(video, player, onDismiss = { optionsFor = null }) }

        if (!showPlayer) {
            QueueMessage(player, Modifier.align(Alignment.BottomCenter).padding(bottom = navTop + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 12.dp))
        }


        if (pip.active) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                sharedSurface(Modifier.fillMaxSize().aspectFit(player.videoAspect))
            }
        }

        UpdateOverlay(updateState, pageBackdrop)
        }
    }
}

@Composable
private fun QueueMessage(player: PlayerState, modifier: Modifier) {
    val message = player.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_000)
        player.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(50)).background(c.inverseSurface).padding(horizontal = 18.dp, vertical = 11.dp)) {
        Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}






@Composable
private fun NavPill(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val shape = RoundedCornerShape(32.dp)

    Surface(
        modifier = modifier
            .widthIn(max = NavWidth)
            .height(NavHeight)
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.30f),
            ),
        shape = shape,
        color = c.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val active = t == selected
                val contentColor by animateColorAsState(
                    if (active) c.onSurface else c.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "tabContentColor",
                )
                val pillColor by animateColorAsState(
                    if (active) c.onSurface.copy(alpha = 0.12f) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "tabPillColor",
                )
                val iconScale by animateFloatAsState(
                    if (active) 1.08f else 1.0f,
                    animationSpec = if (reduced) snap() else spring(dampingRatio = 0.65f, stiffness = 450f),
                    label = "iconScale",
                )
                val interaction = remember(t) { MutableInteractionSource() }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = interaction, indication = null) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onSelect(t)
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = pillColor,
                    contentColor = contentColor,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 2.5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                if (active) t.selectedIcon else t.icon,
                                contentDescription = t.label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            Text(
                                t.label,
                                color = contentColor,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class YouTubeLinkTarget(val videoId: String, val isShort: Boolean)

internal fun parseIncomingIntent(intent: Intent?): YouTubeLinkTarget? {
    if (intent == null) return null
    val raw = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> intent.dataString
        else -> intent.dataString ?: intent.getStringExtra(Intent.EXTRA_TEXT)
    } ?: return null
    return parseYouTubeLink(raw)
}

internal fun parseYouTubeLink(text: String): YouTubeLinkTarget? {
    if (text.isBlank()) return null
    val urlRegex = Regex("""(?i)\b(?:https?://|vnd\.youtube:)?(?:(?:www\.|m\.)?youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|live/|embed/|v/)|youtu\.be/)([\w-]{11})""")
    val match = urlRegex.find(text)
    if (match != null) {
        val videoId = match.groupValues[1]
        val isShort = text.contains("/shorts/", ignoreCase = true)
        return YouTubeLinkTarget(videoId, isShort)
    }
    if (text.matches(Regex("""^[\w-]{11}$"""))) {
        return YouTubeLinkTarget(text, false)
    }
    return null
}
