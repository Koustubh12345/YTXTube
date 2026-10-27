package com.thinker.flashtube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch







@Composable
private fun AnonHomeBanner(onSignIn: () -> Unit, onSearch: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surfaceContainerLow)
            .clickable(onClick = onSignIn)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f)) {
            Text("Sign in for recommendations", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text("Showing popular videos · Tap to personalise", color = c.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

enum class HomeFilter(val label: String) {
    All("All"),
    Today("Today"),
    Videos("Videos"),
    Subscribed("Subscribed"),
    Live("Live"),
}

@Composable
fun HomeFilterChips(
    selected: HomeFilter,
    onSelect: (HomeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(HomeFilter.entries) { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isSelected -> if (isDark || isAmoled) Color.White else Color.Black
                            isAmoled -> Color(0xFF161616)
                            isDark -> Color(0xFF272727)
                            else -> Color(0xFFE5E5E5)
                        }
                    )
                    .pressable(pressedScale = 0.94f) { onSelect(filter) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    filter.label,
                    color = when {
                        isSelected -> if (isDark || isAmoled) Color.Black else Color.White
                        isDark || isAmoled -> Color.White
                        else -> Color(0xFF0F0F0F)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val signedIn = Account.signedIn
    var activeFilter by remember { mutableStateOf(HomeFilter.All) }
    LaunchedEffect(Unit) {
        if (lib.home.videos.isEmpty()) lib.home.ensureLoaded()
    }
    LaunchedEffect(activeFilter) {
        if (activeFilter == HomeFilter.Subscribed && lib.channels.isEmpty()) {
            lib.loadChannels()
        }
    }
    val filteredVideos = remember(lib.home.videos, activeFilter, lib.channels) {
        when (activeFilter) {
            HomeFilter.All -> null
            HomeFilter.Today -> lib.home.videos.filter {
                val up = it.uploaded?.lowercase().orEmpty()
                up.contains("hour") || up.contains("minute") || up.contains("second") || up.contains("just now") || up.contains("1 day") || up == "today"
            }
            HomeFilter.Videos -> lib.home.videos.filter { !it.isLive }
            HomeFilter.Subscribed -> {
                val channelNames = lib.channels.map { it.name.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                val channelIds = lib.channels.map { it.id.trim() }.filter { it.isNotBlank() }.toSet()
                lib.home.videos.filter { video ->
                    val vidId = video.channelId()
                    val author = video.channel.trim().lowercase()
                    (vidId != null && channelIds.contains(vidId)) ||
                        channelNames.contains(author) ||
                        lib.channels.any { video.fromChannel(it) }
                }
            }
            HomeFilter.Live -> lib.home.videos.filter { it.isLive }
        }
    }
    CollapsingHeaderLayout(
        resetKey = scrollToTop to (lib.home.generation to activeFilter),
        header = {
            Column(Modifier.fillMaxWidth()) {
                ScreenHeader(title = "YTX Tube", brandMark = true) { HeaderActions(onSearch, onAccount) }
                HomeFilterChips(selected = activeFilter, onSelect = { activeFilter = it })
            }
        },
    ) { top ->
        when {
            filteredVideos != null && filteredVideos.isEmpty() && !lib.home.loading -> {
                Box(Modifier.fillMaxSize().padding(contentPadding.withTop(top))) {
                    EmptyState(
                        icon = if (activeFilter == HomeFilter.Subscribed) Icons.Rounded.Subscriptions else Icons.Rounded.TravelExplore,
                        title = "No ${activeFilter.label.lowercase()} videos",
                        body = if (activeFilter == HomeFilter.Subscribed)
                            "No videos from your subscribed channels were found in the current home feed."
                        else
                            "There are no ${activeFilter.label.lowercase()} videos in your home feed right now.",
                        action = { PillButton("Show all", { activeFilter = HomeFilter.All }) },
                    )
                }
            }
            else -> FeedList(
                feed = lib.home,
                contentPadding = contentPadding.withTop(top),
                onOpen = onOpen,
                scrollToTop = scrollToTop,
                indicatorTop = top,
                videos = filteredVideos,
                filterScrollKey = activeFilter,
                header = {
                    if (!signedIn) {
                        item(key = "anon_banner") {
                            AnonHomeBanner(onSignIn = onSignIn, onSearch = onSearch)
                        }
                    }
                },
            )
        }
    }
}

enum class SubFilter(val label: String) {
    Videos("Videos"),
    Live("Live"),
    Community("Community"),
}

@Composable
fun SubFilterChips(
    selected: SubFilter,
    onSelect: (SubFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isSelected -> if (isDark || isAmoled) Color.White else Color.Black
                            isAmoled -> Color(0xFF161616)
                            isDark -> Color(0xFF272727)
                            else -> Color(0xFFE5E5E5)
                        }
                    )
                    .pressable(pressedScale = 0.94f) { onSelect(filter) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    filter.label,
                    color = when {
                        isSelected -> if (isDark || isAmoled) Color.Black else Color.White
                        isDark || isAmoled -> Color.White
                        else -> Color(0xFF0F0F0F)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
fun SubscriptionsScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
    onOpenAllSubscriptions: () -> Unit = {},
) {
    val signedIn = Account.signedIn
    val c = MaterialTheme.colorScheme
    var selectedChannelId by remember { mutableStateOf<String?>(null) }
    var activeSubFilter by remember { mutableStateOf(SubFilter.Videos) }
    var channelCommunityPosts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var loadingCommunity by remember { mutableStateOf(false) }

    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.subscriptions.ensureLoaded()
            if (lib.channels.isEmpty()) lib.loadChannels()
        } else {
            selectedChannelId = null
        }
    }
    LaunchedEffect(scrollToTop) { selectedChannelId = null }
    LaunchedEffect(selectedChannelId) {
        activeSubFilter = SubFilter.Videos
        channelCommunityPosts = emptyList()
    }
    val selectedChannel = remember(selectedChannelId, lib.channels) {
        selectedChannelId?.let { id -> lib.channels.find { it.id == id } }
    }
    LaunchedEffect(activeSubFilter, selectedChannelId) {
        if (activeSubFilter == SubFilter.Community && selectedChannel != null && channelCommunityPosts.isEmpty()) {
            loadingCommunity = true
            channelCommunityPosts = runCatching { InnerTube.channelCommunity(selectedChannel.id).posts }.getOrDefault(emptyList())
            loadingCommunity = false
        }
    }
    val channelFiltered = remember(lib.subscriptions.videos, selectedChannel) {
        val ch = selectedChannel ?: return@remember null
        lib.subscriptions.videos.filter { it.fromChannel(ch) }
    }
    val filteredVideos = remember(channelFiltered, lib.subscriptions.videos, activeSubFilter) {
        val base = channelFiltered ?: lib.subscriptions.videos
        when (activeSubFilter) {
            SubFilter.Videos -> if (channelFiltered != null) channelFiltered else null
            SubFilter.Live -> base.filter { it.isLive }
            SubFilter.Community -> null
        }
    }

    CollapsingHeaderLayout(
        resetKey = scrollToTop to listOf(lib.subscriptions.generation, selectedChannelId, lib.channels.size, activeSubFilter),
        header = {
            Column(Modifier.fillMaxWidth()) {
                ScreenHeader(title = "Subscriptions") { HeaderActions(onSearch, onAccount) }
                if (signedIn && lib.channels.isNotEmpty()) {
                    SubscriptionsChannelStrip(
                        channels = lib.channels,
                        selectedId = selectedChannelId,
                        onSelect = { id -> selectedChannelId = id },
                        onAll = onOpenAllSubscriptions,
                    )
                    SubFilterChips(
                        selected = activeSubFilter,
                        onSelect = { activeSubFilter = it },
                    )
                }
            }
        },
    ) { top ->
            if (signedIn) {
                when {
                    activeSubFilter == SubFilter.Community -> {
                        when {
                            selectedChannel == null -> {
                                Box(Modifier.fillMaxSize().padding(contentPadding.withTop(top))) {
                                    EmptyState(
                                        icon = Icons.Rounded.Subscriptions,
                                        title = "Community posts",
                                        body = "Select a channel above to read its community posts and updates.",
                                    )
                                }
                            }
                            loadingCommunity -> {
                                Box(Modifier.fillMaxWidth().padding(top = top + 60.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
                                }
                            }
                            channelCommunityPosts.isEmpty() -> {
                                Box(Modifier.fillMaxSize().padding(contentPadding.withTop(top))) {
                                    EmptyState(
                                        icon = Icons.Rounded.CloudOff,
                                        title = "No community posts",
                                        body = "No community posts found for ${selectedChannel.name}.",
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(contentPadding = contentPadding.withTop(top)) {
                                    items(channelCommunityPosts, key = { it.id }) { post ->
                                        CommunityCard(post)
                                    }
                                }
                            }
                        }
                    }
                    activeSubFilter == SubFilter.Live && filteredVideos.isNullOrEmpty() && !lib.subscriptions.loading -> {
                        Box(Modifier.fillMaxSize().padding(contentPadding.withTop(top))) {
                            EmptyState(
                                icon = Icons.Rounded.Subscriptions,
                                title = "No live streams",
                                body = if (selectedChannel != null) "${selectedChannel.name} isn't live right now."
                                else "None of your subscribed channels are live right now.",
                            )
                        }
                    }
                    selectedChannel != null && filteredVideos.isNullOrEmpty() && !lib.subscriptions.loading -> {
                        Box(Modifier.fillMaxSize().padding(contentPadding.withTop(top))) {
                            EmptyState(
                                icon = Icons.Rounded.Subscriptions,
                                title = "Nothing recent",
                                body = "No new uploads from ${selectedChannel.name} in this feed yet.",
                            )
                        }
                    }
                    else -> FeedList(
                        lib.subscriptions,
                        contentPadding.withTop(top),
                        onOpen,
                        scrollToTop = scrollToTop,
                        indicatorTop = top,
                        videos = filteredVideos,
                        filterScrollKey = selectedChannelId to activeSubFilter,
                    )
                }
            } else {
                LazyColumn(contentPadding = contentPadding.withTop(top)) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.Subscriptions,
                            title = "Your subscriptions",
                            body = "Sign in with Google to see new videos from the channels you follow.",
                            action = { PillButton("Sign in with Google", onSignIn) },
                        )
                    }
                }
            }
        }
}

@Composable
private fun SubscriptionsChannelStrip(
    channels: List<ChannelRef>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onAll: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp),
    ) {
        items(channels, key = { it.id }) { ch ->
            val selected = ch.id == selectedId
            Column(
                Modifier
                    .width(68.dp)
                    .pressable { onSelect(if (selected) null else ch.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChannelAvatar(ch.avatar, ch.name, size = 56, selected = selected, ringSlot = true)
                Spacer(Modifier.height(6.dp))
                Text(
                    ch.name,
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item(key = "all_channels_action") {
            Column(
                Modifier
                    .width(68.dp)
                    .pressable(onClick = onAll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Subscriptions,
                        contentDescription = "All channels",
                        tint = c.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "All",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AllSubscriptionsScreen(
    channels: List<ChannelRef>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val openChannel = LocalChannelOpener.current
    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(Modifier.fillMaxSize()) {
            BackScreenHeader(onBack = onBack) {
                Text(
                    "All subscriptions",
                    modifier = Modifier.weight(1f),
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.8).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LazyColumn(contentPadding = contentPadding) {
                items(channels, key = { it.id }) { ch ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable(onClick = { openChannel(ch.url) })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelAvatar(ch.avatar, ch.name, size = 48)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            ch.name,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onOpenChannel: (String) -> Unit = {},
    onOpenAllSubscriptions: () -> Unit = {},
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val signedIn = Account.signedIn
    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.history.ensureLoaded()
            if (lib.playlists.isEmpty()) lib.loadPlaylists()
            if (lib.channels.isEmpty()) lib.loadChannels()
        }
    }
    CollapsingHeaderLayout(
        resetKey = scrollToTop to lib.history.generation,
        header = { ScreenHeader(title = "Library") { HeaderActions(onSearch, onAccount) } },
    ) { top ->
        PullToRefresh(
            enabled = signedIn,
            refreshing = lib.history.refreshing,
            onRefresh = { lib.history.refresh(); lib.loadPlaylists(); lib.loadChannels() },
            topPadding = top,
        ) {
        LazyColumn(state = rememberScrollToTop(scrollToTop), contentPadding = contentPadding.withTop(top)) {
            if (!signedIn) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.VideoLibrary,
                        title = "Your library",
                        body = "Sign in with Google for your history, Watch later, liked videos and playlists.",
                        action = { PillButton("Sign in with Google", onSignIn) },
                    )
                }
                return@LazyColumn
            }
            item(key = "historyTitle") { SectionTitle("History") }
            item(key = "history") {
                val history = lib.history.videos
                when {
                    history.isNotEmpty() -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(history.take(20), key = { "h-" + it.id }) { video -> HistoryCard(video) { onOpen(video) } }
                    }
                    lib.history.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp, modifier = Modifier.size(28.dp))
                    }
                    else -> Text(
                        lib.history.error ?: "Nothing watched yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            if (lib.channels.isNotEmpty()) {
                item(key = "subscriptionsTitle") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SectionTitle("Subscriptions", modifier = Modifier.padding(0.dp))
                        Text(
                            "All",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenAllSubscriptions() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                item(key = "subscriptions") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(lib.channels, key = { "sub-" + it.id }) { ch ->
                            Column(
                                modifier = Modifier
                                    .width(68.dp)
                                    .clickable { ch.url?.let(onOpenChannel) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ChannelAvatar(ch.avatar, ch.name, size = 56)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    ch.name,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "playlistsTitle") { SectionTitle("Playlists") }
            val pinned = listOf(
                PlaylistRef("WL", "Watch later", null, null),
                PlaylistRef("MDL", "YTX Music Downloads", null, null),
                PlaylistRef("LL", "Liked videos", null, null),
            )
            items((pinned + lib.playlists).chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { pl -> PlaylistTile(pl, Modifier.weight(1f)) { onOpenPlaylist(pl) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        }
    }
}

@Composable
private fun HistoryCard(video: VideoItem, onClick: () -> Unit) {
    val menu = LocalVideoMenu.current
    Column(Modifier.width(168.dp).prefetchOnPress(video.id).pressable(pressedScale = 0.97f, onLongClick = { menu(video) }, onClick = onClick)) {
        Box {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 12)
            DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(6.dp), small = true)
        }
        Spacer(Modifier.height(7.dp))
        Text(video.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistTile(playlist: PlaylistRef, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    val (icon, tone) = when (playlist.id) {
        "WL" -> Icons.Rounded.WatchLater to c.primaryContainer
        "MDL" -> Icons.Rounded.MusicNote to c.tertiaryContainer
        "LL" -> Icons.Rounded.ThumbUp to c.secondaryContainer
        "DL" -> Icons.Rounded.Download to c.secondaryContainer
        else -> Icons.AutoMirrored.Rounded.PlaylistPlay to c.secondaryContainer
    }
    Column(
        modifier = modifier
            .height(132.dp)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .background(c.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (playlist.thumbnail != null) {
            Thumbnail(playlist.thumbnail, Modifier.size(width = 72.dp, height = 44.dp), radius = 12)
        } else {
            Box(Modifier.size(52.dp).clip(CookieShape).background(tone), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = contentColorFor(tone))
            }
        }
        Column {
            Text(playlist.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            playlist.subtitle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}


@Composable
fun PlaylistScreen(
    playlist: PlaylistRef,
    feed: FeedState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    channelAvatarSeed: Map<String, String?> = emptyMap(),
) {
    LaunchedEffect(playlist.id) { feed.ensureLoaded() }
    val avatarSeed = remember(channelAvatarSeed) { channelAvatarSeed }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    CollapsingHeaderLayout(
        resetKey = feed.generation,
        header = {
        BackScreenHeader(onBack = onBack) {
            Text(
                playlist.title,
                modifier = Modifier.weight(1f),
                fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.8).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        },
    ) { top ->
        if (feed.videos.isEmpty() && !feed.loading && feed.error == null && !feed.hasMore) {
            val icon = if (playlist.id == "DL") Icons.Rounded.Download else Icons.Rounded.VideoLibrary
            val title = if (playlist.id == "DL") "No downloads yet" else "Nothing here yet"
            val body = if (playlist.id == "DL") "Downloaded videos will appear here." else "Videos you add to this playlist will show up here."
            Box(Modifier.padding(top = top)) { EmptyState(icon, title, body) }
        } else {
            FeedList(feed, contentPadding.withTop(top), onOpen, indicatorTop = top, channelAvatarSeed = avatarSeed)
        }
    }
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        modifier = Modifier.pressable(onClick = onClick).padding(8.dp),
    )
}




@Stable
class SearchState(private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set
    var submitted by mutableStateOf("")
        private set
    var results by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var suggestionsLoading by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var generation by mutableStateOf(0)
        private set
    private var job: Job? = null
    private var suggestJob: Job? = null

    fun reset() {
        job?.cancel()
        suggestJob?.cancel()
        query = ""
        submitted = ""
        results = emptyList()
        suggestions = emptyList()
        suggestionsLoading = false
        loading = false
        error = null
    }

    fun updateQuery(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            suggestionsLoading = false
            if (submitted.isNotEmpty() || results.isNotEmpty() || error != null || loading) {
                submitted = ""
                results = emptyList()
                error = null
                loading = false
                job?.cancel()
            }
            return
        }
        suggestJob = scope.launch {
            delay(220)
            suggestionsLoading = true
            runCatching { YouTube.searchSuggestions(text) }
                .onSuccess { suggestions = it }
                .onFailure { suggestions = emptyList() }
            suggestionsLoading = false
        }
    }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        SearchHistory.add(q)
        suggestJob?.cancel()
        suggestions = emptyList()
        suggestionsLoading = false
        submitted = q
        loading = true
        error = null
        job?.cancel()
        job = scope.launch {
            runCatching { YouTube.search(q) }
                .onSuccess { results = it; generation++ }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }
}

@Composable
fun SearchScreen(
    state: SearchState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    scrollToTop: Int = 0,
) {
    val c = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) { SearchHistory.init(context) }
    fun showSearchKeyboard() {
        scope.launch {
            delay(80)
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(Unit) { showSearchKeyboard() }



    fun submit() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        view.postDelayed({
            view.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }, 120)
        state.submit()
    }
    Box(Modifier.fillMaxSize().background(c.surface).overlayTouchBlock()) {

    CollapsingHeaderLayout(
        resetKey = scrollToTop to state.generation,
        header = {
            BackScreenHeader(onBack = onBack) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            Text(
                                "Search YouTube",
                                color = c.onSurfaceVariant,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = state::updateQuery,
                            singleLine = true,
                            textStyle = TextStyle(color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(c.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocus)
                                .onPreviewKeyEvent { e ->
                                    if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                                        if (e.type == KeyEventType.KeyDown) submit()
                                        true
                                    } else false
                                },
                        )
                    }
                    if (state.query.isNotEmpty()) {
                        Box(
                            modifier = Modifier.size(40.dp).pressable {
                                state.updateQuery("")
                                showSearchKeyboard()
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Clear", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        },
    ) { top ->
        val draft = state.query.isNotBlank() &&
            (state.submitted.isEmpty() || state.query.trim() != state.submitted.trim())
        Box(Modifier.fillMaxSize()) {
        Box(Modifier.padding(top = if (!draft && state.results.isNotEmpty() && state.error == null) 0.dp else top)) {
        when {
            draft -> {
                val matchingHistory = SearchHistory.queries.filter {
                    it.contains(state.query.trim(), ignoreCase = true)
                }
                val nonHistorySuggestions = state.suggestions.filterNot { s ->
                    matchingHistory.any { it.equals(s, ignoreCase = true) }
                }

                if (state.suggestionsLoading && state.suggestions.isEmpty() && matchingHistory.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.Center) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                    }
                } else if (state.suggestions.isEmpty() && matchingHistory.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "Keep typing",
                        body = "Suggestions appear as you enter a search.",
                    )
                } else {
                    LazyColumn(contentPadding = contentPadding) {
                        items(matchingHistory, key = { "hist-$it" }) { query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable {
                                        state.updateQuery(query)
                                        submit()
                                    }
                                    .padding(start = 20.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.History,
                                    "Recent search",
                                    tint = c.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    query,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .pressable { SearchHistory.remove(query) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Close, "Remove", tint = c.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        items(nonHistorySuggestions, key = { "sug-$it" }) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable {
                                        state.updateQuery(suggestion)
                                        submit()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    null,
                                    tint = c.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    suggestion,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            state.loading && state.results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
            }
            state.error != null -> EmptyState(
                icon = Icons.Rounded.CloudOff,
                title = "Search didn't load",
                body = state.error.orEmpty(),
                action = { PillButton("Try again", { state.submit() }) },
            )
            state.submitted.isEmpty() -> {
                val history = SearchHistory.queries
                if (history.isNotEmpty()) {
                    LazyColumn(contentPadding = contentPadding) {
                        item(key = "historyHeader") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Recent searches",
                                    fontFamily = RoundedHeavy,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = c.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .pressable { SearchHistory.clear() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        "Clear all",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = c.primary,
                                    )
                                }
                            }
                        }
                        items(history, key = { "hist-$it" }) { query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable {
                                        state.updateQuery(query)
                                        submit()
                                    }
                                    .padding(start = 20.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.History,
                                    null,
                                    tint = c.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    query,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .pressable { SearchHistory.remove(query) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Close, "Remove", tint = c.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                } else {
                    EmptyState(
                        icon = Icons.Rounded.TravelExplore,
                        title = "Search YouTube",
                        body = "Results play straight away, with no ads and no sign-in.",
                    )
                }
            }
            state.results.isEmpty() -> EmptyState(
                icon = Icons.Rounded.History,
                title = "Nothing found",
                body = "No videos matched “${state.submitted}”.",
            )
            else -> {
                val list = rememberScrollToTop(scrollToTop)
                LaunchedEffect(state.generation) { list.scrollToItem(0) }
                LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                items(state.results, key = { it.id }) { video ->
                    VideoCard(video, onClick = {
                        val currentQuery = state.submitted.trim().ifEmpty { state.query.trim() }
                        if (currentQuery.isNotEmpty()) {
                            SearchHistory.add(currentQuery)
                        }
                        onOpen(video)
                    })
                }
            }
            }
        }
        }
        }
    }
    }
}
