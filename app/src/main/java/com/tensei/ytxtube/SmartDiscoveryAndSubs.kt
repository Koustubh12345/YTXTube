package com.tensei.ytxtube

import androidx.compose.foundation.clickable
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ============================================================================
// CATEGORY 3: SMART DISCOVERY ENGINE (Features 31–45)
// CATEGORY 4: ADVANCED SUBSCRIPTION MANAGER (Features 46–55)
// ============================================================================

enum class DiscoveryLens(val label: String, val description: String) {
    All("All Feed", "Standard unfiltered discovery feed"),
    NewChannels("Fresh Creators", "Videos from channels you haven't watched or subscribed to yet"),
    RecentlyActive("Active 24h", "Uploaded within the last 24 hours"),
    LowViewGems("Hidden Gems", "Quality uploads under 50K views"),
    LongForm("Long-Form (20m+)", "In-depth essays, lectures, podcasts & documentaries"),
    ShortForm("Quick Clips (1–5m)", "Concise videos between 1 and 5 minutes (no Shorts)"),
    WatchBudget("Fits Time Budget", "Matches your selected session time window"),
    RandomSerendipity("Surprise Me", "Unwatched discovery picks outside overplayed channels"),
}

@Immutable
data class TrendingChannelSummary(
    val name: String,
    val channelUrl: String?,
    val avatar: String?,
    val videoCount: Int,
    val totalViews: Long,
    val avgDurationSec: Long,
    val freshestAgeDays: Double?,
    val primaryTopic: String,
)

@Immutable
data class ChannelComparisonReport(
    val channelA: TrendingChannelSummary,
    val channelB: TrendingChannelSummary,
    val viewAdvantage: String,
    val paceAdvantage: String,
    val formatComparison: String,
)

@Immutable
data class CustomDiscoveryFeedSpec(
    val id: String,
    val name: String,
    val query: String,
    val topic: String,
    val minDurationSec: Long,
    val maxDurationSec: Long,
    val maxViews: Long,
    val maxAgeDays: Double?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("query", query)
        .put("topic", topic)
        .put("minDurationSec", minDurationSec)
        .put("maxDurationSec", maxDurationSec)
        .put("maxViews", maxViews)
        .put("maxAgeDays", maxAgeDays ?: -1.0)

    companion object {
        fun fromJson(o: JSONObject): CustomDiscoveryFeedSpec = CustomDiscoveryFeedSpec(
            id = o.optString("id", "feed_${System.currentTimeMillis()}"),
            name = o.optString("name", "Custom Feed"),
            query = o.optString("query", ""),
            topic = o.optString("topic", "All"),
            minDurationSec = o.optLong("minDurationSec", 0L),
            maxDurationSec = o.optLong("maxDurationSec", Long.MAX_VALUE),
            maxViews = o.optLong("maxViews", Long.MAX_VALUE),
            maxAgeDays = o.optDouble("maxAgeDays", -1.0).takeIf { it > 0.0 },
        )
    }
}

/** Feature 44: Saveable Search Filter Presets. */
@Immutable
data class SearchFilterPreset(
    val id: String,
    val name: String,
    val filter: SearchFilter,
    val builtIn: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("duration", filter.duration.name)
        .put("upload", filter.upload.name)
        .put("liveOnly", filter.liveOnly)
        .put("sort", filter.sort.name)

    companion object {
        fun fromJson(o: JSONObject): SearchFilterPreset = SearchFilterPreset(
            id = o.optString("id"),
            name = o.optString("name", "Preset"),
            filter = SearchFilter(
                duration = DurationFilter.entries.firstOrNull { it.name == o.optString("duration") } ?: DurationFilter.AnyLength,
                upload = UploadFilter.entries.firstOrNull { it.name == o.optString("upload") } ?: UploadFilter.AnyTime,
                liveOnly = o.optBoolean("liveOnly", false),
                sort = SortOrder.entries.firstOrNull { it.name == o.optString("sort") } ?: SortOrder.Relevance,
            ),
            builtIn = false,
        )
    }
}

/** Feature 45: Search History Intelligence summary. */
@Immutable
data class SearchHistoryInsight(
    val topTopics: List<Pair<String, Int>>,
    val recentClusters: Map<String, List<String>>,
    val suggestedNextQueries: List<String>,
)

/**
 * Pure, testable discovery & subscription intelligence logic (Categories 3 & 4).
 */
object SmartDiscoveryLogic {

    /** Applies [DiscoveryLens] + optional topic & watch-time budget to a video list. */
    fun filterByLens(
        videos: List<VideoItem>,
        lens: DiscoveryLens,
        knownChannels: Set<String> = emptySet(),
        topic: String = "All",
        watchBudgetMinutes: Int = 20,
    ): List<VideoItem> {
        val knownLower = knownChannels.map { it.lowercase(Locale.US) }.toSet()
        val topicFiltered = if (topic == "All" || topic.isBlank()) videos else {
            videos.filter {
                val cls = AiTranscriptIntelligence.classifyTopic(it.title)
                cls.primaryTopic.equals(topic, ignoreCase = true) || cls.secondaryTopic.equals(topic, ignoreCase = true)
            }
        }
        return when (lens) {
            DiscoveryLens.All -> topicFiltered
            DiscoveryLens.NewChannels -> topicFiltered.filter {
                it.channel.isNotBlank() && it.channel.lowercase(Locale.US) !in knownLower
            }
            DiscoveryLens.RecentlyActive -> topicFiltered.filter {
                val days = UploadAge.days(it.uploaded)
                days != null && days <= 1.5
            }
            DiscoveryLens.LowViewGems -> topicFiltered.filter {
                !it.isLive && it.views in 1..50_000L && it.durationSec >= 120L
            }.sortedBy { it.views }
            DiscoveryLens.LongForm -> topicFiltered.filter {
                !it.isLive && it.durationSec >= 1_200L
            }.sortedByDescending { it.durationSec }
            DiscoveryLens.ShortForm -> topicFiltered.filter {
                !it.isLive && it.durationSec in 61L..300L
            }
            DiscoveryLens.WatchBudget -> {
                val maxSec = watchBudgetMinutes.coerceAtLeast(3) * 60L
                topicFiltered.filter { !it.isLive && it.durationSec in 45L..maxSec }
                    .sortedByDescending { it.durationSec }
            }
            DiscoveryLens.RandomSerendipity -> {
                topicFiltered.filter { !Watched.has(it.id) }.shuffled()
            }
        }
    }

    /** Feature 31: Aggregate and rank trending channels from a pool of videos. */
    fun discoverTrendingChannels(videos: List<VideoItem>): List<TrendingChannelSummary> {
        return videos
            .filter { it.channel.isNotBlank() }
            .groupBy { it.channel.trim() }
            .map { (name, list) ->
                val totalViews = list.sumOf { it.views.coerceAtLeast(0L) }
                val avgDur = list.map { it.durationSec }.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toLong() ?: 0L
                val minAge = list.mapNotNull { UploadAge.days(it.uploaded) }.minOrNull()
                val joinedTitles = list.joinToString(" ") { it.title }
                val topic = AiTranscriptIntelligence.classifyTopic(joinedTitles).primaryTopic
                TrendingChannelSummary(
                    name = name,
                    channelUrl = list.firstNotNullOfOrNull { it.channelUrl },
                    avatar = list.firstNotNullOfOrNull { it.avatar },
                    videoCount = list.size,
                    totalViews = totalViews,
                    avgDurationSec = avgDur,
                    freshestAgeDays = minAge,
                    primaryTopic = topic,
                )
            }
            .sortedByDescending { it.totalViews + it.videoCount * 25_000L }
    }

    /** Feature 33: Similar-channel recommendations based on topic & keyword overlap. */
    fun findSimilarChannels(
        targetChannel: String,
        videos: List<VideoItem>,
    ): List<TrendingChannelSummary> {
        val summaries = discoverTrendingChannels(videos)
        val target = summaries.firstOrNull { it.name.equals(targetChannel, ignoreCase = true) }
        val targetTopic = target?.primaryTopic
            ?: videos.firstOrNull { it.channel.equals(targetChannel, ignoreCase = true) }?.let {
                AiTranscriptIntelligence.classifyTopic(it.title).primaryTopic
            }
        return summaries
            .filterNot { it.name.equals(targetChannel, ignoreCase = true) }
            .sortedByDescending { s ->
                (if (s.primaryTopic == targetTopic) 1_000_000L else 0L) + s.totalViews / 100L
            }
    }

    /** Feature 43: Compare two channels side-by-side. */
    fun compareChannels(a: TrendingChannelSummary, b: TrendingChannelSummary): ChannelComparisonReport {
        val viewAdv = when {
            a.totalViews > b.totalViews * 1.2 -> "${a.name} leads in total views (${formatCount(a.totalViews)} vs ${formatCount(b.totalViews)})"
            b.totalViews > a.totalViews * 1.2 -> "${b.name} leads in total views (${formatCount(b.totalViews)} vs ${formatCount(a.totalViews)})"
            else -> "Both channels have comparable audience reach"
        }
        val aAge = a.freshestAgeDays ?: 999.0
        val bAge = b.freshestAgeDays ?: 999.0
        val paceAdv = when {
            aAge < bAge -> "${a.name} uploaded more recently (${a.videoCount} videos sampled)"
            bAge < aAge -> "${b.name} uploaded more recently (${b.videoCount} videos sampled)"
            else -> "Similar recent upload cadence"
        }
        val fmtCmp = "${a.name}: avg ${a.avgDurationSec / 60}m (${a.primaryTopic}) vs ${b.name}: avg ${b.avgDurationSec / 60}m (${b.primaryTopic})"
        return ChannelComparisonReport(a, b, viewAdv, paceAdv, fmtCmp)
    }

    /** Feature 45: Analyze search history into topic clusters and follow-up suggestions. */
    fun analyzeSearchHistory(queries: List<String>): SearchHistoryInsight {
        if (queries.isEmpty()) {
            return SearchHistoryInsight(emptyList(), emptyMap(), emptyList())
        }
        val clusters = LinkedHashMap<String, MutableList<String>>()
        for (q in queries) {
            val topic = AiTranscriptIntelligence.classifyTopic(q).primaryTopic
            clusters.getOrPut(topic) { mutableListOf() }.add(q)
        }
        val topTopics = clusters.map { it.key to it.value.size }.sortedByDescending { it.second }
        val suggestions = queries.take(3).flatMap { q ->
            listOf("$q tutorial", "$q deep dive 2026")
        }.distinct().take(5)
        return SearchHistoryInsight(topTopics, clusters, suggestions)
    }
}

// ============================================================================
// CATEGORY 4: ADVANCED SUBSCRIPTION MANAGER MODELS & CODEC (Features 46–55)
// ============================================================================

enum class ChannelNotifyMode(val label: String) {
    All("All Uploads"),
    Highlights("Highlights Only"),
    Muted("Silent"),
}

@Immutable
data class ManagedSubscription(
    val channelId: String,
    val name: String,
    val url: String,
    val avatar: String? = null,
    val folder: String = "Uncategorized",
    val categories: List<String> = emptyList(),
    val notifyMode: ChannelNotifyMode = ChannelNotifyMode.All,
    val orderIndex: Int = 0,
    val lastUploadAgeDays: Double? = null,
    val unwatchedCount: Int = 0,
) {
    val isInactive: Boolean
        get() = lastUploadAgeDays != null && lastUploadAgeDays > 60.0

    fun toJson(): JSONObject = JSONObject()
        .put("channelId", channelId)
        .put("name", name)
        .put("url", url)
        .put("avatar", avatar.orEmpty())
        .put("folder", folder)
        .put("categories", JSONArray(categories))
        .put("notifyMode", notifyMode.name)
        .put("orderIndex", orderIndex)
        .put("lastUploadAgeDays", lastUploadAgeDays ?: -1.0)
        .put("unwatchedCount", unwatchedCount)

    companion object {
        fun fromJson(o: JSONObject): ManagedSubscription {
            val cats = o.optJSONArray("categories")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty()
            return ManagedSubscription(
                channelId = o.optString("channelId"),
                name = o.optString("name"),
                url = o.optString("url"),
                avatar = o.optString("avatar").ifBlank { null },
                folder = o.optString("folder", "Uncategorized").ifBlank { "Uncategorized" },
                categories = cats,
                notifyMode = ChannelNotifyMode.entries.firstOrNull { it.name == o.optString("notifyMode") }
                    ?: ChannelNotifyMode.All,
                orderIndex = o.optInt("orderIndex", 0),
                lastUploadAgeDays = o.optDouble("lastUploadAgeDays", -1.0).takeIf { it >= 0.0 },
                unwatchedCount = o.optInt("unwatchedCount", 0),
            )
        }
    }
}

/**
 * Feature 54: Pure multi-format Subscription Import/Export Codec (NewPipe JSON, LibreTube JSON, Google Takeout CSV).
 * Kept free of Android dependencies so it can be unit tested directly.
 */
object SubscriptionImportExportCodec {

    fun exportNewPipeJson(subs: List<ManagedSubscription>, appVersion: String = "2.9.2"): String {
        val arr = JSONArray()
        subs.forEach { s ->
            val url = s.url.ifBlank { "https://www.youtube.com/channel/${s.channelId}" }
            arr.put(
                JSONObject()
                    .put("service_id", 0)
                    .put("url", url)
                    .put("name", s.name)
                    .put("folder", s.folder)
                    .put("categories", JSONArray(s.categories)),
            )
        }
        return JSONObject()
            .put("app_version", appVersion)
            .put("app_version_int", 292)
            .put("subscriptions", arr)
            .toString(2)
    }

    fun exportCsv(subs: List<ManagedSubscription>): String = buildString {
        appendLine("Channel Id,Channel Url,Channel Title,Folder")
        subs.forEach { s ->
            val id = s.channelId.ifBlank { s.url.substringAfterLast('/') }
            val url = s.url.ifBlank { "https://www.youtube.com/channel/$id" }
            val safeName = s.name.replace("\"", "\"\"")
            appendLine("$id,$url,\"$safeName\",${s.folder}")
        }
    }

    /** Parses NewPipe JSON, LibreTube JSON array, or Google Takeout CSV. */
    fun parseAny(raw: String): List<ManagedSubscription> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return when {
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                val arr = root.optJSONArray("subscriptions") ?: return emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val url = o.optString("url")
                    val name = o.optString("name").ifBlank { return@mapNotNull null }
                    val id = o.optString("channelId").ifBlank {
                        url.substringAfter("/channel/", "").substringBefore('?').ifBlank { name }
                    }
                    val folder = o.optString("folder", "Uncategorized").ifBlank { "Uncategorized" }
                    ManagedSubscription(channelId = id, name = name, url = url, folder = folder, orderIndex = i)
                }
            }
            trimmed.startsWith("[") -> {
                val arr = JSONArray(trimmed)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val name = o.optString("name").ifBlank { o.optString("title") }.ifBlank { return@mapNotNull null }
                    val url = o.optString("url").ifBlank { "https://www.youtube.com/channel/${o.optString("id")}" }
                    val id = o.optString("id").ifBlank { url.substringAfter("/channel/", name) }
                    ManagedSubscription(channelId = id, name = name, url = url, orderIndex = i)
                }
            }
            else -> {
                // Google Takeout CSV: Channel Id,Channel Url,Channel Title[,Folder]
                trimmed.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("Channel Id", ignoreCase = true) }
                    .mapIndexedNotNull { idx, line ->
                        val parts = line.split(',')
                        if (parts.size < 3) return@mapIndexedNotNull null
                        val id = parts[0].trim()
                        val url = parts[1].trim()
                        val title = parts[2].trim().removeSurrounding("\"")
                        val folder = parts.getOrNull(3)?.trim()?.ifBlank { "Uncategorized" } ?: "Uncategorized"
                        if (id.isBlank() && title.isBlank()) null
                        else ManagedSubscription(channelId = id.ifBlank { title }, name = title.ifBlank { id }, url = url, folder = folder, orderIndex = idx)
                    }.toList()
            }
        }
    }
}

/**
 * State store for Smart Discovery (31–45) & Subscription Manager (46–55).
 */
object DiscoveryAndSubsManager {
    private const val PREFS = "discovery_and_subs_v292"
    private var prefs: SharedPreferences? = null

    var activeLens by mutableStateOf(DiscoveryLens.All)
        private set
    var activeTopic by mutableStateOf("All")
        private set
    var watchBudgetMinutes by mutableIntStateOf(20)
        private set

    val customFeeds = mutableStateListOf<CustomDiscoveryFeedSpec>()
    val searchPresets = mutableStateListOf(
        SearchFilterPreset("p_recent_long", "Recent Deep Dives", SearchFilter(DurationFilter.Over20, UploadFilter.Month, false, SortOrder.Relevance)),
        SearchFilterPreset("p_quick_today", "Quick Fixes (24h)", SearchFilter(DurationFilter.Under4, UploadFilter.Day, false, SortOrder.UploadDate)),
        SearchFilterPreset("p_most_viewed", "All-Time Benchmarks", SearchFilter(DurationFilter.AnyLength, UploadFilter.AnyTime, false, SortOrder.ViewCount)),
        SearchFilterPreset("p_live_now", "Live Streams", SearchFilter(DurationFilter.AnyLength, UploadFilter.AnyTime, true, SortOrder.Relevance)),
    )

    val folders = mutableStateListOf("All", "Uncategorized", "Tech & Coding", "Music & Audio", "Science & Education", "Gaming", "Favorites")
    var selectedFolder by mutableStateOf("All")
        private set
    var hideInactiveChannels by mutableStateOf(false)
        private set
    var unwatchedOnlyFeed by mutableStateOf(false)
        private set

    val managedSubs = mutableStateListOf<ManagedSubscription>()
    val starterCollections = listOf(
        "Android & Kotlin Engineering" to listOf("Android Developers", "JetBrains", "Philipp Lackner"),
        "Science & Deep Essays" to listOf("Veritasium", "3Blue1Brown", "Kurzgesagt"),
        "Hardware & High-Tech" to listOf("Gamers Nexus", "Digital Foundry", "MKBHD"),
    )

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        activeLens = DiscoveryLens.entries.firstOrNull { it.name == p.getString("active_lens", "All") } ?: DiscoveryLens.All
        activeTopic = p.getString("active_topic", "All") ?: "All"
        watchBudgetMinutes = p.getInt("watch_budget_min", 20).coerceIn(5, 180)
        selectedFolder = p.getString("selected_folder", "All") ?: "All"
        hideInactiveChannels = p.getBoolean("hide_inactive", false)
        unwatchedOnlyFeed = p.getBoolean("unwatched_only", false)

        customFeeds.clear()
        runCatching {
            val arr = JSONArray(p.getString("custom_feeds", "[]"))
            for (i in 0 until arr.length()) customFeeds.add(CustomDiscoveryFeedSpec.fromJson(arr.getJSONObject(i)))
        }

        managedSubs.clear()
        runCatching {
            val arr = JSONArray(p.getString("managed_subs", "[]"))
            for (i in 0 until arr.length()) managedSubs.add(ManagedSubscription.fromJson(arr.getJSONObject(i)))
        }

        runCatching {
            val savedFolders = p.getString("folders", null)?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() }
            if (!savedFolders.isNullOrEmpty()) {
                folders.clear()
                folders.addAll(savedFolders.distinct())
            }
        }
    }

    fun selectLens(lens: DiscoveryLens) {
        activeLens = lens
        prefs?.edit()?.putString("active_lens", lens.name)?.apply()
    }

    fun selectTopic(topic: String) {
        activeTopic = topic
        prefs?.edit()?.putString("active_topic", topic)?.apply()
    }

    fun updateWatchBudget(minutes: Int) {
        watchBudgetMinutes = minutes.coerceIn(5, 240)
        prefs?.edit()?.putInt("watch_budget_min", watchBudgetMinutes)?.apply()
    }

    fun selectFolder(folder: String) {
        selectedFolder = folder
        prefs?.edit()?.putString("selected_folder", folder)?.apply()
    }

    fun addFolder(name: String) {
        val clean = name.trim().take(28)
        if (clean.isNotBlank() && folders.none { it.equals(clean, ignoreCase = true) }) {
            folders.add(clean)
            prefs?.edit()?.putString("folders", folders.joinToString("|"))?.apply()
        }
    }

    fun addCustomFeed(name: String, query: String, topic: String, minMin: Int, maxMin: Int, maxViewsK: Int) {
        val spec = CustomDiscoveryFeedSpec(
            id = "cf_${System.currentTimeMillis()}",
            name = name.trim().ifBlank { "Custom Feed ${customFeeds.size + 1}" },
            query = query.trim(),
            topic = topic,
            minDurationSec = minMin * 60L,
            maxDurationSec = if (maxMin <= 0) Long.MAX_VALUE else maxMin * 60L,
            maxViews = if (maxViewsK <= 0) Long.MAX_VALUE else maxViewsK * 1_000L,
            maxAgeDays = null,
        )
        customFeeds.add(0, spec)
        persistCustomFeeds()
    }

    fun removeCustomFeed(id: String) {
        if (customFeeds.removeAll { it.id == id }) persistCustomFeeds()
    }

    private fun persistCustomFeeds() {
        val arr = JSONArray()
        customFeeds.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString("custom_feeds", arr.toString())?.apply()
    }

    /** Syncs signed-in `ChannelRef` list and feed activity metrics into `managedSubs`. */
    fun syncFromLibrary(channels: List<ChannelRef>, feedVideos: List<VideoItem>) {
        val byName = managedSubs.associateBy { it.name.lowercase(Locale.US) }.toMutableMap()
        var changed = false
        channels.forEachIndexed { idx, ch ->
            val key = ch.name.lowercase(Locale.US)
            val chVideos = feedVideos.filter { it.fromChannel(ch) }
            val minAge = chVideos.mapNotNull { UploadAge.days(it.uploaded) }.minOrNull()
            val unwatched = chVideos.count { !Watched.has(it.id) }
            val existing = byName[key]
            if (existing == null) {
                managedSubs.add(
                    ManagedSubscription(
                        channelId = ch.id,
                        name = ch.name,
                        url = ch.url,
                        avatar = ch.avatar,
                        orderIndex = managedSubs.size + idx,
                        lastUploadAgeDays = minAge,
                        unwatchedCount = unwatched,
                    ),
                )
                changed = true
            } else {
                val updated = existing.copy(
                    avatar = ch.avatar ?: existing.avatar,
                    url = ch.url.ifBlank { existing.url },
                    lastUploadAgeDays = minAge ?: existing.lastUploadAgeDays,
                    unwatchedCount = unwatched,
                )
                val pos = managedSubs.indexOf(existing)
                if (pos >= 0 && updated != existing) {
                    managedSubs[pos] = updated
                    changed = true
                }
            }
        }
        if (changed) persistSubs()
    }

    fun assignFolder(channelIdOrName: String, folder: String) {
        val idx = managedSubs.indexOfFirst {
            it.channelId == channelIdOrName || it.name.equals(channelIdOrName, ignoreCase = true)
        }
        if (idx >= 0) {
            managedSubs[idx] = managedSubs[idx].copy(folder = folder)
            persistSubs()
        }
    }

    fun cycleNotifyMode(channelIdOrName: String) {
        val idx = managedSubs.indexOfFirst {
            it.channelId == channelIdOrName || it.name.equals(channelIdOrName, ignoreCase = true)
        }
        if (idx >= 0) {
            val cur = managedSubs[idx].notifyMode
            val next = ChannelNotifyMode.entries[(cur.ordinal + 1) % ChannelNotifyMode.entries.size]
            managedSubs[idx] = managedSubs[idx].copy(notifyMode = next)
            persistSubs()
        }
    }

    fun moveSubscription(index: Int, delta: Int) {
        val target = index + delta
        if (index in managedSubs.indices && target in managedSubs.indices) {
            val item = managedSubs.removeAt(index)
            managedSubs.add(target, item)
            persistSubs()
        }
    }

    fun importSubscriptions(raw: String): Int {
        val parsed = SubscriptionImportExportCodec.parseAny(raw)
        var added = 0
        for (sub in parsed) {
            if (managedSubs.none { it.name.equals(sub.name, ignoreCase = true) || (it.channelId.isNotBlank() && it.channelId == sub.channelId) }) {
                managedSubs.add(sub.copy(orderIndex = managedSubs.size))
                if (sub.folder !in folders) folders.add(sub.folder)
                added++
            }
        }
        if (added > 0) {
            persistSubs()
            prefs?.edit()?.putString("folders", folders.joinToString("|"))?.apply()
        }
        return added
    }

    fun exportNewPipeJson(): String = SubscriptionImportExportCodec.exportNewPipeJson(managedSubs, BuildConfig.VERSION_NAME)

    fun exportCsv(): String = SubscriptionImportExportCodec.exportCsv(managedSubs)

    private fun persistSubs() {
        val arr = JSONArray()
        managedSubs.forEachIndexed { idx, s -> arr.put(s.copy(orderIndex = idx).toJson()) }
        prefs?.edit()?.putString("managed_subs", arr.toString())?.apply()
    }

    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        activeLens = DiscoveryLens.All
        activeTopic = "All"
        watchBudgetMinutes = 20
        selectedFolder = "All"
        customFeeds.clear()
        managedSubs.clear()
    }
}

/**
 * Interactive Smart Discovery & Subscription Manager Sheet (Features 31–55).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDiscoveryAndSubsSheet(
    lib: LibraryData,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenChannel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Discovery Lenses", "Trending & Similar", "Custom Feeds", "Sub Manager & Folders", "Import / Export")

    val allPool = remember(lib.home.videos, lib.subscriptions.videos, lib.history.videos) {
        (lib.home.videos + lib.subscriptions.videos + lib.history.videos).distinctBy { it.id }
    }
    androidx.compose.runtime.LaunchedEffect(lib.channels, lib.subscriptions.videos) {
        DiscoveryAndSubsManager.syncFromLibrary(lib.channels, lib.subscriptions.videos)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Smart Discovery & Subscription Hub",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
            Text(
                "Topic feeds, hidden gems, channel comparison, subscription folders & NewPipe import/export",
                color = c.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { index, title ->
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(title, fontWeight = FontWeight.SemiBold) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> DiscoveryLensesTab(allPool, lib.channels, onOpenVideo, onDismiss)
                1 -> TrendingAndCompareChannelsTab(allPool, onOpenChannel, onDismiss)
                2 -> CustomDiscoveryFeedsTab(allPool, onOpenVideo, onDismiss)
                3 -> SubscriptionFoldersAndActivityTab(onOpenChannel, onDismiss)
                4 -> SubscriptionImportExportTab(context)
            }
        }
    }
}

@Composable
private fun DiscoveryLensesTab(
    pool: List<VideoItem>,
    subscribedChannels: List<ChannelRef>,
    onOpenVideo: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val knownSet = remember(subscribedChannels) { subscribedChannels.map { it.name }.toSet() }
    val filtered = remember(
        pool,
        DiscoveryAndSubsManager.activeLens,
        DiscoveryAndSubsManager.activeTopic,
        DiscoveryAndSubsManager.watchBudgetMinutes,
        knownSet,
    ) {
        SmartDiscoveryLogic.filterByLens(
            videos = pool,
            lens = DiscoveryAndSubsManager.activeLens,
            knownChannels = knownSet,
            topic = DiscoveryAndSubsManager.activeTopic,
            watchBudgetMinutes = DiscoveryAndSubsManager.watchBudgetMinutes,
        )
    }

    Text("Discovery Mode (Features 32, 34–38, 40–41)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiscoveryLens.entries.forEach { lens ->
            FilterChip(
                selected = DiscoveryAndSubsManager.activeLens == lens,
                onClick = { DiscoveryAndSubsManager.selectLens(lens) },
                label = { Text(lens.label) },
            )
        }
    }
    Text(DiscoveryAndSubsManager.activeLens.description, fontSize = 11.5.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

    if (DiscoveryAndSubsManager.activeLens == DiscoveryLens.WatchBudget) {
        Spacer(Modifier.height(8.dp))
        Text("Available Session Time: ${DiscoveryAndSubsManager.watchBudgetMinutes} min", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10, 15, 25, 45, 60).forEach { m ->
                FilterChip(
                    selected = DiscoveryAndSubsManager.watchBudgetMinutes == m,
                    onClick = { DiscoveryAndSubsManager.updateWatchBudget(m) },
                    label = { Text("$m min") },
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text("Topic Filter (Feature 38)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val topics = listOf("All", "Software & Coding", "Science & Engineering", "Hardware & Tech", "AI & Machine Learning", "Music & Audio", "Gaming", "Education & Lectures", "Design & Creative")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        topics.forEach { topic ->
            FilterChip(
                selected = DiscoveryAndSubsManager.activeTopic == topic,
                onClick = { DiscoveryAndSubsManager.selectTopic(topic) },
                label = { Text(topic) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Matching Videos (${filtered.size})", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        if (filtered.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    val pick = filtered.random()
                    onOpenVideo(pick)
                    onDismiss()
                },
            ) {
                Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Random Pick", fontSize = 12.sp)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    filtered.take(12).forEach { v ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh)
                .pressable { onOpenVideo(v); onDismiss() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(v.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        v.channel,
                        v.views.takeIf { it > 0 }?.let { "${formatCount(it)} views" },
                        v.durationSec.takeIf { it > 0 }?.let { formatDuration(it * 1000) },
                        v.uploaded,
                    ).joinToString(" · "),
                    fontSize = 11.5.sp,
                    color = c.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.PlayArrow, null, tint = c.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TrendingAndCompareChannelsTab(
    pool: List<VideoItem>,
    onOpenChannel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val trending = remember(pool) { SmartDiscoveryLogic.discoverTrendingChannels(pool) }
    var compareIdxA by remember { mutableIntStateOf(0) }
    var compareIdxB by remember { mutableIntStateOf(1) }

    Text("Trending & Active Creators (Features 31 & 33)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    if (trending.isEmpty()) {
        Text("Load the Home or Subscriptions feed first to analyze trending channels.", color = c.onSurfaceVariant, fontSize = 13.sp)
        return
    }
    trending.take(8).forEachIndexed { idx, ch ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh)
                .pressable {
                    ch.channelUrl?.let { onOpenChannel(it); onDismiss() }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#${idx + 1}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = c.primary, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(
                    "${ch.primaryTopic} · ${ch.videoCount} videos · ${formatCount(ch.totalViews)} views",
                    fontSize = 11.5.sp,
                    color = c.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { compareIdxB = idx }) {
                Text("Compare", fontSize = 11.sp)
            }
        }
    }

    if (trending.size >= 2) {
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
        Spacer(Modifier.height(10.dp))
        Text("Channel Comparison (Feature 43)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
        val a = trending[compareIdxA.coerceIn(trending.indices)]
        val b = trending[compareIdxB.coerceIn(trending.indices)]
        val report = remember(a, b) { SmartDiscoveryLogic.compareChannels(a, b) }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceContainerHigh)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${a.name} vs ${b.name}", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
            Text("• ${report.viewAdvantage}", fontSize = 12.sp)
            Text("• ${report.paceAdvantage}", fontSize = 12.sp)
            Text("• ${report.formatComparison}", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun CustomDiscoveryFeedsTab(
    pool: List<VideoItem>,
    onOpenVideo: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var feedName by remember { mutableStateOf("") }
    var feedQuery by remember { mutableStateOf("") }
    var feedTopic by remember { mutableStateOf("Software & Coding") }
    var minMin by remember { mutableIntStateOf(5) }

    Text("Build Custom Discovery Feed (Feature 39)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = feedName,
        onValueChange = { feedName = it },
        label = { Text("Feed Name (e.g. Deep Kotlin & Rust)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = feedQuery,
        onValueChange = { feedQuery = it },
        label = { Text("Title keyword filter (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Min Length:", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        listOf(0, 5, 15, 30).forEach { m ->
            FilterChip(selected = minMin == m, onClick = { minMin = m }, label = { Text("${m}m+") })
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                DiscoveryAndSubsManager.addCustomFeed(feedName, feedQuery, feedTopic, minMin, 0, 0)
                feedName = ""
                feedQuery = ""
            },
        ) {
            Text("Create Feed")
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Saved Custom Feeds (${DiscoveryAndSubsManager.customFeeds.size})", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    DiscoveryAndSubsManager.customFeeds.forEach { spec ->
        val matches = pool.filter { v ->
            (spec.query.isBlank() || v.title.contains(spec.query, ignoreCase = true)) &&
                v.durationSec >= spec.minDurationSec
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh)
                .padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Keyword: '${spec.query.ifBlank { "Any" }}' · Min ${spec.minDurationSec / 60}m · ${matches.size} matches", fontSize = 11.5.sp, color = c.onSurfaceVariant)
                }
                Box(
                    Modifier.size(28.dp).clip(CircleShape).pressable { DiscoveryAndSubsManager.removeCustomFeed(spec.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Delete, null, tint = c.error, modifier = Modifier.size(16.dp))
                }
            }
            matches.take(3).forEach { m ->
                Text(
                    "▶ ${m.title}",
                    fontSize = 12.sp,
                    color = c.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).clickable { onOpenVideo(m); onDismiss() },
                )
            }
        }
    }
}

@Composable
private fun SubscriptionFoldersAndActivityTab(
    onOpenChannel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var newFolder by remember { mutableStateOf("") }

    Text("Subscription Folders & Categories (Features 46–53)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DiscoveryAndSubsManager.folders.forEach { folder ->
            FilterChip(
                selected = DiscoveryAndSubsManager.selectedFolder == folder,
                onClick = { DiscoveryAndSubsManager.selectFolder(folder) },
                label = { Text(folder) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        OutlinedTextField(
            value = newFolder,
            onValueChange = { newFolder = it },
            placeholder = { Text("New folder name…") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { DiscoveryAndSubsManager.addFolder(newFolder); newFolder = "" }) {
            Text("+ Folder")
        }
    }

    Spacer(Modifier.height(10.dp))
    val shown = DiscoveryAndSubsManager.managedSubs.filter {
        DiscoveryAndSubsManager.selectedFolder == "All" || it.folder.equals(DiscoveryAndSubsManager.selectedFolder, ignoreCase = true)
    }
    val inactiveCount = shown.count { it.isInactive }
    Text(
        "Managed Channels (${shown.size}) · Inactive (>60d): $inactiveCount",
        fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp,
    )
    Spacer(Modifier.height(6.dp))

    if (shown.isEmpty()) {
        Text("No channels in this folder yet. Sign in or import subscriptions in the 'Import / Export' tab.", fontSize = 12.5.sp, color = c.onSurfaceVariant)
    } else {
        shown.forEachIndexed { idx, sub ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable {
                            if (sub.url.isNotBlank()) {
                                onOpenChannel(sub.url)
                                onDismiss()
                            }
                        },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sub.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (sub.unwatchedCount > 0) {
                            Text(
                                "${sub.unwatchedCount} new",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = c.onPrimary,
                                modifier = Modifier.clip(CircleShape).background(c.primary).padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                        if (sub.isInactive) {
                            Text("Inactive", fontSize = 10.sp, color = c.error, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Folder: ${sub.folder} · Notify: ${sub.notifyMode.label}",
                        fontSize = 11.5.sp,
                        color = c.onSurfaceVariant,
                    )
                }
                // Cycle folder
                OutlinedButton(
                    onClick = {
                        val realFolders = DiscoveryAndSubsManager.folders.filter { it != "All" }
                        val nextFolder = realFolders[(realFolders.indexOf(sub.folder) + 1) % realFolders.size]
                        DiscoveryAndSubsManager.assignFolder(sub.channelId, nextFolder)
                    },
                ) {
                    Text(sub.folder.take(8), fontSize = 10.5.sp)
                }
                Spacer(Modifier.width(4.dp))
                // Cycle notification mode
                Box(
                    Modifier.size(30.dp).clip(CircleShape).pressable { DiscoveryAndSubsManager.cycleNotifyMode(sub.channelId) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.NotificationsActive, "Notify mode", tint = c.primary, modifier = Modifier.size(16.dp))
                }
                // Reorder up
                if (idx > 0) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).pressable { DiscoveryAndSubsManager.moveSubscription(idx, -1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("↑", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionImportExportTab(context: Context) {
    val c = MaterialTheme.colorScheme
    var importText by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    Text("Subscription Import / Export (Feature 54)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Text("Compatible with NewPipe JSON, LibreTube JSON, and Google Takeout CSV.", fontSize = 12.sp, color = c.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val json = DiscoveryAndSubsManager.exportNewPipeJson()
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("NewPipe Subscriptions JSON", json))
                statusMsg = "Copied ${DiscoveryAndSubsManager.managedSubs.size} subscriptions (NewPipe JSON) to clipboard."
            },
        ) {
            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Export NewPipe JSON")
        }
        OutlinedButton(
            onClick = {
                val csv = DiscoveryAndSubsManager.exportCsv()
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Subscriptions CSV", csv))
                statusMsg = "Copied CSV to clipboard."
            },
        ) {
            Text("Export CSV")
        }
    }

    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = importText,
        onValueChange = { importText = it },
        label = { Text("Paste NewPipe JSON, LibreTube JSON, or Google Takeout CSV") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = importText.isNotBlank(),
            onClick = {
                val count = runCatching { DiscoveryAndSubsManager.importSubscriptions(importText) }.getOrDefault(0)
                statusMsg = "Imported $count new channels."
                importText = ""
            },
        ) {
            Text("Import Subscriptions")
        }
    }

    statusMsg?.let { msg ->
        Spacer(Modifier.height(8.dp))
        Text(msg, color = c.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
    Spacer(Modifier.height(10.dp))
    Text("Curated Channel Collections (Feature 55)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    DiscoveryAndSubsManager.starterCollections.forEach { (packName, creators) ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(packName, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(creators.joinToString(" · "), fontSize = 11.5.sp, color = c.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = {
                    val jsonArr = JSONArray()
                    creators.forEach { chName ->
                        jsonArr.put(JSONObject().put("name", chName).put("url", "https://www.youtube.com/@${chName.replace(" ", "")}").put("folder", packName))
                    }
                    val added = DiscoveryAndSubsManager.importSubscriptions(JSONObject().put("subscriptions", jsonArr).toString())
                    statusMsg = "Added $added channels from '$packName'."
                },
            ) {
                Text("Add Pack", fontSize = 11.5.sp)
            }
        }
    }
}
