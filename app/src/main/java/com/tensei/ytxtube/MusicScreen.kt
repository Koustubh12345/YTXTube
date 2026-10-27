package com.tensei.ytxtube

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

fun isMusicTrack(video: VideoItem): Boolean {
    val t = video.title.lowercase()
    val ch = video.channel.lowercase()
    return t.contains("music") || t.contains("song") || t.contains("audio") ||
        t.contains("official") || t.contains("lyrics") || t.contains("feat") ||
        t.contains("ft.") || t.contains("remix") || t.contains("album") ||
        t.contains("track") || ch.contains("vevo") || ch.contains("records") ||
        ch.contains("music") || ch.contains("topic") || video.durationSec in 60..720
}

@Stable
class MusicFeedState(private val scope: CoroutineScope) {
    var shelves by mutableStateOf<List<MusicShelf>>(emptyList())
    var recentPlays by mutableStateOf<List<VideoItem>>(emptyList())
    var likedTracks by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private var initialLoaded = false
    private var job: Job? = null

    fun ensureLoaded(lib: LibraryData) {
        if (!initialLoaded && !loading) loadAll(lib)
    }

    fun loadAll(lib: LibraryData) {
        RecentMusic.load()
        initialLoaded = true
        loading = true
        error = null
        job?.cancel()
        job = scope.launch {
            try {
                coroutineScope {
                    val signedIn = Account.signedIn


                    val homeDeferred = async {
                        runCatching { InnerTube.musicHome() }.getOrDefault(emptyList())
                    }



                    val historyDeferred = async {
                        val network = if (signedIn) {
                            runCatching { InnerTube.musicHistory() }.getOrDefault(emptyList())
                        } else emptyList()
                        val local = lib.history.videos.filter { isMusicTrack(it) }
                        (RecentMusic.items + network + local)
                            .distinctBy { it.id }
                            .map {
                                it.copy(
                                    thumbnail = highResMusicThumbnail(it.thumbnail)
                                        ?: sharpenFeedThumbnail(null, it.id),
                                )
                            }
                    }


                    val likedDeferred = async {
                        if (signedIn) {
                            runCatching { InnerTube.musicLiked() }.getOrDefault(emptyList()).map {
                                it.copy(thumbnail = highResMusicThumbnail(it.thumbnail) ?: sharpenFeedThumbnail(null, it.id))
                            }
                        } else emptyList()
                    }

                    val fetchedShelves = homeDeferred.await()
                    val fetchedHistory = historyDeferred.await()
                    val fetchedLiked = likedDeferred.await()

                    shelves = fetchedShelves
                    recentPlays = fetchedHistory
                    likedTracks = fetchedLiked
                }
            } catch (t: Throwable) {
                error = t.message ?: "Failed to load YouTube Music"
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun MusicScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    onPlayNext: (VideoItem) -> Unit = {},
    onOpenQueue: (List<VideoItem>, Int) -> Unit = { list, index -> list.getOrNull(index)?.let(onOpen) },
    scrollToTop: Int = 0,
) {
    val scope = rememberCoroutineScope()
    val state = remember { MusicFeedState(scope) }


    val listState = remember { androidx.compose.foundation.lazy.LazyListState() }
    var lastScrollSignal by remember { mutableIntStateOf(scrollToTop) }
    LaunchedEffect(scrollToTop) {
        if (scrollToTop != lastScrollSignal) {
            lastScrollSignal = scrollToTop
            listState.animateScrollToItem(0)
        }
    }


    LaunchedEffect(state.loading, state.recentPlays.size, state.likedTracks.size, state.shelves.size) {
        if (!state.loading) listState.scrollToItem(0, 0)
    }
    val context = LocalContext.current

    var trackOptionsFor by remember { mutableStateOf<VideoItem?>(null) }


    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }


    var mix by remember { mutableStateOf<MusicShelfItem?>(null) }
    var mixSongs by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var mixLoading by remember { mutableStateOf(false) }
    var mixError by remember { mutableStateOf<String?>(null) }
    val loadMix: (MusicShelfItem) -> Unit = { item ->
        mix = item
        mixSongs = emptyList()
        mixError = null
        mixLoading = true
        scope.launch {
            runCatching { InnerTube.musicItemSongs(item) }
                .onSuccess { if (mix == item) mixSongs = it }
                .onFailure { if (mix == item) mixError = YouTube.friendlyError(it) }
            if (mix == item) mixLoading = false
        }
    }

    BackHandler(enabled = mix != null) { mix = null }

    BackHandler(enabled = isSearching) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            searchResults = emptyList()
        } else {
            isSearching = false
        }
    }

    LaunchedEffect(Unit) {
        state.ensureLoaded(lib)
        lib.history.ensureLoaded()
        listState.scrollToItem(0, 0)
    }

    val isDark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    val c = MaterialTheme.colorScheme
    val signedIn = Account.signedIn

    fun downloadAudio(video: VideoItem) {
        val ok = Downloads.enqueueMusic(video)
        if (ok) {
            Toast.makeText(context, "Downloading audio: ${video.title}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Already queued or downloaded", Toast.LENGTH_SHORT).show()
        }
    }

    trackOptionsFor?.let { track ->
        MusicTrackOptionsSheet(
            track = track,
            onPlayNext = {
                onPlayNext(track)
                Toast.makeText(context, "Playing next: ${track.title}", Toast.LENGTH_SHORT).show()
            },
            onDownloadAudio = { downloadAudio(track) },
            onShare = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${track.id}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share track"))
            },
            onDismiss = { trackOptionsFor = null },
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (isSearching) {
            DedicatedMusicSearchView(
                query = searchQuery,
                onQueryChange = { q ->
                    searchQuery = q
                    if (q.isBlank()) {
                        searchSuggestions = emptyList()
                        searchResults = emptyList()
                    } else {
                        scope.launch {
                            runCatching {
                                searchSuggestions = YouTube.searchSuggestions("$q music").take(6)
                            }
                        }
                    }
                },
                onSearchSubmit = { q ->
                    searchQuery = q
                    searchLoading = true
                    scope.launch {
                        runCatching {
                            searchResults = YouTube.searchMusic(q)
                        }
                        searchLoading = false
                    }
                },
                onClose = {
                    isSearching = false
                    searchQuery = ""
                    searchResults = emptyList()
                },
                results = searchResults,
                loading = searchLoading,
                suggestions = searchSuggestions,
                onOpenTrack = { _, index -> onOpenQueue(searchResults, index) },
                onDownloadTrack = ::downloadAudio,
                contentPadding = contentPadding,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                MusicScreenHeader(
                    onSearch = { isSearching = true },
                    onAccount = onAccount,
                )
                when {
                    state.loading && state.shelves.isEmpty() && state.recentPlays.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = c.primary,
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }
                    state.error != null && state.shelves.isEmpty() && state.recentPlays.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                        ) {
                            EmptyState(
                                icon = Icons.Rounded.MusicNote,
                                title = "Couldn't load YouTube Music",
                                body = state.error ?: "Unable to connect to music servers.",
                                action = { PillButton("Retry", onClick = { state.loadAll(lib) }) },
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {

                            if (state.recentPlays.isNotEmpty()) {
                                item(key = "section_recent_plays") {
                                    MusicSectionHeader(
                                        title = "Listen Again",
                                        subtitle = if (signedIn) "Your recent YouTube Music listening history" else "Recently played",
                                        icon = Icons.Rounded.History,
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.padding(top = 10.dp),
                                    ) {
                                        itemsIndexed(state.recentPlays, key = { _, video -> "recent_" + video.id }) { index, video ->
                                            MusicTrackSquareCard(
                                                video = video,
                                                onClick = { onOpenQueue(state.recentPlays, index) },
                                                onOptions = { trackOptionsFor = video },
                                            )
                                        }
                                    }
                                }
                            }


                            if (state.likedTracks.isNotEmpty()) {
                                item(key = "section_liked_music") {
                                    MusicSectionHeader(
                                        title = "Liked Music",
                                        subtitle = "Your favorite saved tracks",
                                        icon = Icons.Rounded.Favorite,
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.padding(top = 10.dp),
                                    ) {
                                        itemsIndexed(state.likedTracks, key = { _, video -> "liked_" + video.id }) { index, video ->
                                            MusicTrackSquareCard(
                                                video = video,
                                                onClick = { onOpenQueue(state.likedTracks, index) },
                                                onOptions = { trackOptionsFor = video },
                                            )
                                        }
                                    }
                                }
                            }


                            items(state.shelves, key = { "shelf_" + it.title }) { shelf ->
                                val directTracks = remember(shelf) {
                                    shelf.items
                                        .filter { it.videoId != null && it.browseId.isNullOrEmpty() && it.playlistId.isNullOrEmpty() }
                                        .mapNotNull { it.toVideoItem() }
                                }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    MusicSectionHeader(
                                        title = shelf.title,
                                        subtitle = shelf.subtitle ?: "Curated for you by YouTube Music",
                                        icon = Icons.Rounded.PlayArrow,
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.padding(top = 10.dp),
                                    ) {
                                        items(shelf.items, key = { "item_" + (it.videoId ?: it.browseId ?: it.title) }) { item ->
                                            MusicShelfCard(
                                                item = item,
                                                onClick = {
                                                    val direct = item.toVideoItem()
                                                    val isTrack = direct != null &&
                                                        item.browseId.isNullOrEmpty() &&
                                                        item.playlistId.isNullOrEmpty()
                                                    if (isTrack && direct != null) {


                                                        val index = directTracks.indexOfFirst { it.id == direct.id }
                                                        onOpenQueue(directTracks, if (index >= 0) index else 0)
                                                    } else {

                                                        loadMix(item)
                                                    }
                                                },
                                                onOptions = {
                                                    val direct = item.toVideoItem()
                                                    if (direct != null && item.browseId.isNullOrEmpty() && item.playlistId.isNullOrEmpty()) {
                                                        trackOptionsFor = direct
                                                    } else {
                                                        scope.launch {
                                                            InnerTube.resolveMusicItem(item)?.let { trackOptionsFor = it }
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            item(key = "bottom_space") {
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }


        mix?.let { selected ->
            MusicMixScreen(
                item = selected,
                songs = mixSongs,
                loading = mixLoading,
                error = mixError,
                contentPadding = contentPadding,
                onBack = { mix = null },
                onRetry = { loadMix(selected) },
                onOpenSong = { index -> onOpenQueue(mixSongs, index) },
                onDownloadSong = ::downloadAudio,
                onOptions = { trackOptionsFor = it },
            )
        }
    }
}

@Composable
private fun MusicScreenHeader(
    onSearch: () -> Unit,
    onAccount: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp, 26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF0033), Color(0xFFFF5E62)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "YTX Music",
            modifier = Modifier.weight(1f),
            fontFamily = RoundedHeavy,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "Music Search", tint = c.onSurface)
            }
            AccountAction(onClick = onAccount)
        }
    }
}

@Composable
private fun MusicSectionHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                title,
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = (-0.3).sp,
                color = c.onSurface,
            )
        }
        Text(
            subtitle,
            fontSize = 12.5.sp,
            color = c.onSurfaceVariant.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTrackOptionsSheet(
    track: VideoItem,
    onPlayNext: () -> Unit,
    onDownloadAudio: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceContainerLow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surfaceContainerHighest),
                ) {
                    AsyncImage(
                        model = highResMusicThumbnail(track.thumbnail) ?: sharpenFeedThumbnail(null, track.id),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        track.channel,
                        fontSize = 12.5.sp,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onPlayNext()
                        onDismiss()
                    }
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "Play next",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurface,
                )
            }


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onDownloadAudio()
                        onDismiss()
                    }
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Download (Audio only)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.onSurface,
                    )
                    Text(
                        "High quality audio (256 kbps)",
                        fontSize = 11.5.sp,
                        color = c.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onShare()
                        onDismiss()
                    }
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Share,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "Share track",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MusicShelfCard(
    item: MusicShelfItem,
    onClick: () -> Unit,
    onOptions: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(152.dp)
            .clip(RoundedCornerShape(14.dp))
            .pressable(pressedScale = 0.96f) { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = highResMusicThumbnail(item.thumbnail),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            IconButton(
                onClick = onOptions,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(30.dp),
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            color = c.onSurface,
        )
        Text(
            item.subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Normal,
            fontSize = 11.5.sp,
            color = c.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun MusicTrackSquareCard(
    video: VideoItem,
    onClick: () -> Unit,
    onOptions: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(152.dp)
            .clip(RoundedCornerShape(14.dp))
            .pressable(pressedScale = 0.96f) { onClick() }
            .prefetchOnPress(video.id),
    ) {
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = highResMusicThumbnail(video.thumbnail) ?: sharpenFeedThumbnail(null, video.id),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            IconButton(
                onClick = onOptions,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(30.dp),
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            video.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            color = c.onSurface,
        )
        Text(
            video.channel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Normal,
            fontSize = 11.5.sp,
            color = c.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun DedicatedMusicSearchView(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onClose: () -> Unit,
    results: List<VideoItem>,
    loading: Boolean,
    suggestions: List<String>,
    onOpenTrack: (VideoItem, Int) -> Unit,
    onDownloadTrack: (VideoItem) -> Unit,
    contentPadding: PaddingValues,
) {
    val c = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (isAmoled) Color.Black else c.surface)
            .statusBarsPadding(),
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = c.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        when {
                            isAmoled -> Color(0xFF1A1A1A)
                            isDark -> Color(0xFF222226)
                            else -> Color(0xFFEFEFF4)
                        },
                    )
                    .border(
                        1.dp,
                        if (isAmoled) Color(0xFF2E2E2E) else Color.Transparent,
                        RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = c.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "Search songs, artists, albums...",
                                fontSize = 14.sp,
                                color = c.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = c.onSurface,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(c.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    if (query.isNotBlank()) onSearchSubmit(query)
                                },
                            ),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = c.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }


        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 2.5.dp)
                }
            }
            results.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "Top Results",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = c.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(results, key = { _, video -> "sr_" + video.id }) { index, video ->
                        MusicSearchResultRow(
                            video = video,
                            onClick = { onOpenTrack(video, index) },
                            onDownload = { onDownloadTrack(video) },
                        )
                    }
                }
            }
            suggestions.isNotEmpty() && query.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    items(suggestions) { sugg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyboardController?.hide()
                                    onSearchSubmit(sugg)
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = c.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                sugg,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = c.onSurface,
                            )
                        }
                    }
                }
            }
            else -> {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(
                            "Trending Music Searches",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                            color = c.onSurfaceVariant,
                        )
                    }
                    item {
                        val trendingTags = listOf(
                            "The Weeknd", "Taylor Swift", "Kendrick Lamar",
                            "Billie Eilish", "Drake", "Coldplay",
                            "Arijit Singh", "Dua Lipa", "Post Malone", "Travis Scott",
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(trendingTags) { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isAmoled) Color(0xFF1E1E1E)
                                            else if (isDark) Color(0xFF26262A)
                                            else Color(0xFFE8E8EE),
                                        )
                                        .clickable {
                                            keyboardController?.hide()
                                            onSearchSubmit(tag)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        tag,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicSearchResultRow(
    video: VideoItem,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val menu = LocalVideoMenu.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = video.thumbnail ?: sharpenFeedThumbnail(null, video.id),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = c.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    video.channel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (video.durationSec > 0) {
                    Text(
                        " · ${formatDuration(video.durationSec * 1000)}",
                        fontSize = 11.5.sp,
                        color = c.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onDownload,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = "Download Audio",
                tint = c.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(
            onClick = { menu(video) },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = c.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}





@Composable
private fun MusicMixScreen(
    item: MusicShelfItem,
    songs: List<VideoItem>,
    loading: Boolean,
    error: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenSong: (Int) -> Unit,
    onDownloadSong: (VideoItem) -> Unit,
    onOptions: (VideoItem) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(c.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            BackScreenHeader(onBack = onBack) {
                Text(
                    item.title,
                    modifier = Modifier.weight(1f),
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surfaceContainerHighest),
                ) {
                    AsyncImage(
                        model = highResMusicThumbnail(item.thumbnail)
                            ?: sharpenFeedThumbnail(null, item.videoId.orEmpty()),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp,
                        letterSpacing = (-0.4).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = c.onSurface,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.subtitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            loading -> "Loading songs…"
                            songs.isNotEmpty() -> "${songs.size} songs"
                            else -> "Mix"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            when {
                loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 2.5.dp)
                }
                error != null -> EmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = "Couldn't load this mix",
                    body = error,
                    action = { PillButton("Retry", onClick = onRetry) },
                )
                songs.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = "No songs here",
                    body = "This mix didn't return any playable songs.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    itemsIndexed(songs, key = { _, song -> "mix_" + song.id }) { index, song ->
                        MusicMixSongRow(
                            index = index,
                            song = song,
                            onClick = { onOpenSong(index) },
                            onDownload = { onDownloadSong(song) },
                            onOptions = { onOptions(song) },
                        )
                    }
                    item(key = "mix_bottom") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MusicMixSongRow(
    index: Int,
    song: VideoItem,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onOptions: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.width(26.dp),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = highResMusicThumbnail(song.thumbnail) ?: sharpenFeedThumbnail(null, song.id),
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = c.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.channel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (song.durationSec > 0) {
                    Text(
                        " · ${formatDuration(song.durationSec * 1000)}",
                        fontSize = 11.5.sp,
                        color = c.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
        IconButton(onClick = onDownload, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = "Download audio",
                tint = c.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onOptions, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = c.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
