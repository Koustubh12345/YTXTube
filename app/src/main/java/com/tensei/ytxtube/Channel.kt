package com.tensei.ytxtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page


val LocalChannelOpener = staticCompositionLocalOf<(String) -> Unit> { {} }

enum class ChannelTab(val label: String) {
    Videos("Videos"),
    Live("Live"),
    Community("Community"),
}

@Stable
class ChannelState(val url: String, private val scope: CoroutineScope) {
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var subscribed by mutableStateOf<Boolean?>(null)
        private set
    var activeTab by mutableStateOf(ChannelTab.Videos)
        private set
    var liveVideos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var liveLoading by mutableStateOf(false)
        private set
    var communityPosts by mutableStateOf<List<CommunityPost>>(emptyList())
        private set
    var communityLoading by mutableStateOf(false)
        private set

    private var next: String? = null
    val hasMore get() = next != null

    init { load() }

    fun selectTab(tab: ChannelTab) {
        activeTab = tab
        when (tab) {
            ChannelTab.Live -> if (liveVideos.isEmpty() && !liveLoading) loadLive()
            ChannelTab.Community -> if (communityPosts.isEmpty() && !communityLoading) loadCommunity()
            ChannelTab.Videos -> {}
        }
    }

    fun load() {
        loading = true
        error = null
        scope.launch {
            runCatching { YouTube.channel(url) }
                .onSuccess { base ->
                    var d = base
                    details = d
                    if (Account.signedIn) launch { subscribed = runCatching { InnerTube.channelSubscribed(d.id) }.getOrNull() }
                    runCatching { YouTube.channelVideos(d, null) }
                        .onSuccess { p ->
                            videos = p.videos
                            next = p.next
                            p.channelHeader?.let { d = d.withHeader(it) }
                            details = d
                        }
                        .onFailure {
                            details = d
                            error = YouTube.friendlyError(it)
                        }
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun loadLive() {
        val d = details ?: return
        liveLoading = true
        scope.launch {
            runCatching { YouTube.channelLive(d, null) }
                .onSuccess { p ->
                    liveVideos = p.videos.ifEmpty { videos.filter { it.isLive } }
                }
                .onFailure {
                    liveVideos = videos.filter { it.isLive }
                }
            liveLoading = false
        }
    }

    fun loadCommunity() {
        val d = details ?: return
        communityLoading = true
        scope.launch {
            runCatching { YouTube.channelCommunity(d, null) }
                .onSuccess { p ->
                    communityPosts = p.posts
                }
                .onFailure {
                    communityPosts = emptyList()
                }
            communityLoading = false
        }
    }

    fun loadMore() {
        val d = details ?: return
        val page = next ?: return
        if (loading) return
        loading = true
        scope.launch {
            runCatching { YouTube.channelVideos(d, page) }
                .onSuccess { p ->
                    val seen = videos.mapTo(HashSet()) { it.id }
                    videos = videos + p.videos.filter { seen.add(it.id) }
                    next = p.next
                }
            loading = false
        }
    }

    fun toggleSubscribe(onError: (String) -> Unit) {
        val id = details?.id ?: return
        val before = subscribed ?: return
        subscribed = !before
        scope.launch {
            runCatching { InnerTube.setSubscribed(id, !before) }
                .onFailure { subscribed = before; onError("Couldn't update your subscription.") }
        }
    }
}

@Composable
fun ChannelTabStrip(selected: ChannelTab, onSelect: (ChannelTab) -> Unit, modifier: Modifier = Modifier) {
    val isDark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        ChannelTab.entries.forEach { tab ->
            val isSelected = tab == selected
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
                    .pressable(pressedScale = 0.94f) { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
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
fun ChannelScreen(state: ChannelState, contentPadding: PaddingValues, onBack: () -> Unit, onOpen: (VideoItem) -> Unit, onMessage: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, state.videos.size) { if (nearEnd && state.videos.isNotEmpty()) state.loadMore() }
    Box(Modifier.fillMaxSize().background(c.surface)) {
    CollapsingHeaderLayout(
        header = {
        BackScreenHeader(onBack = onBack) {
            ChannelNameWithBadge(
                state.details?.name.orEmpty(),
                verified = state.details?.verified == true,
                modifier = Modifier.weight(1f),
                fontSize = 22.sp,
                maxLines = 1,
                letterSpacing = (-0.6).sp,
            )
        }
        },
    ) { top ->
        val d = state.details
        when {
            d == null && state.error != null -> Box(Modifier.padding(top = top)) { EmptyState(
                Icons.Rounded.CloudOff, "Couldn't load this channel", state.error.orEmpty(),
                action = { PillButton("Try again", state::load) },
            ) }
            d == null -> Box(Modifier.fillMaxWidth().padding(top = top + 60.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
            }
            else -> LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                item(key = "header") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        if (d.banner != null) {
                            Thumbnail(d.banner, Modifier.fillMaxWidth().aspectRatio(5.6f), radius = 12)
                            Spacer(Modifier.height(16.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelAvatar(d.avatar, d.name, size = 76)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                ChannelNameWithBadge(d.name, verified = d.verified, maxLines = 2)
                                d.handle?.let { handle ->
                                    Spacer(Modifier.height(2.dp))
                                    Text("@$handle", color = c.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                channelStatLine(d.subscribers, d.videoCount).takeIf { it.isNotBlank() }?.let { stats ->
                                    Text(stats, color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        state.subscribed?.let { subscribed ->
                            Spacer(Modifier.height(14.dp))
                            ChannelSubscribeButton(subscribed) { state.toggleSubscribe(onMessage) }
                        }
                        if (d.description.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(d.description, color = c.onSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item(key = "tabs") {
                    ChannelTabStrip(selected = state.activeTab, onSelect = { state.selectTab(it) })
                }
                when (state.activeTab) {
                    ChannelTab.Videos -> {
                        items(state.videos, key = { it.id }) { video -> VideoCard(video, onClick = { onOpen(video) }) }
                        if (state.loading || state.hasMore) item {
                            Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    ChannelTab.Live -> {
                        if (state.liveLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else if (state.liveVideos.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Rounded.CloudOff,
                                    "No live streams",
                                    "This channel has no active live streams or past live broadcasts.",
                                )
                            }
                        } else {
                            items(state.liveVideos, key = { it.id }) { video -> VideoCard(video, onClick = { onOpen(video) }) }
                        }
                    }
                    ChannelTab.Community -> {
                        if (state.communityLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else if (state.communityPosts.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Rounded.CloudOff,
                                    "No community posts",
                                    "This channel hasn't posted anything to their community tab yet.",
                                )
                            }
                        } else {
                            items(state.communityPosts, key = { it.id }) { post -> CommunityCard(post) }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ChannelSubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .squishOn(subscribed)
            .fillMaxWidth()
            .height(46.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(if (subscribed) c.secondaryContainer else c.onSurface),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(Icons.Rounded.NotificationsActive, null, active = true, kind = Flourish.Ring, tint = c.onSecondaryContainer, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(if (subscribed) "Subscribed" else "Subscribe", color = if (subscribed) c.onSecondaryContainer else c.surface, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}
