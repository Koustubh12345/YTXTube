package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.localization.DateWrapper
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import kotlin.math.abs

@Immutable
data class VideoItem(
    val id: String,
    val title: String,
    val channel: String,
    val channelUrl: String?,
    val thumbnail: String?,
    val avatar: String?,
    val durationSec: Long,
    val views: Long,
    val uploaded: String?,

    val localUri: String? = null,
    val isLive: Boolean = false,
)


fun VideoItem.channelId(): String? =
    channelUrl?.substringAfter("/channel/", "")?.substringBefore('?')?.takeIf { it.startsWith("UC") && it.length > 2 }

fun VideoItem.fromChannel(ref: ChannelRef): Boolean =
    channelId() == ref.id || channel.equals(ref.name, ignoreCase = true)


fun VideoItem.feedAvatar(): String? =
    avatar?.let(::normalizeChannelAvatarUrl)
        ?: channelId()?.let { ChannelAvatars.known[it] }

@Immutable
data class CommunityPost(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val image: String?,
    val published: String?,
    val likes: String?,
)

@Immutable
data class CommunityPage(
    val posts: List<CommunityPost>,
    val next: String?,
)

@Immutable
data class VideoDetails(
    val item: VideoItem,
    val description: String,
    val likes: Long,
    val dislikes: Long = -1L,
    val subscribers: Long,
    val related: List<VideoItem>,
    val playable: Boolean,

    val quality: String?,

    val qualities: List<Int>,

    val aspect: Float? = null,
)

@Immutable
data class ChannelDetails(
    val url: String,
    val id: String,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val subscribers: Long,

    val videoCount: Long = -1,

    val handle: String? = null,
    val verified: Boolean = false,
    val description: String,
    val videosTab: ListLinkHandler?,
    val liveTab: ListLinkHandler? = null,
)

fun ChannelDetails.withHeader(header: ChannelHeaderMeta): ChannelDetails = copy(
    handle = header.handle ?: handle,
    subscribers = if (header.subscribers > 0) header.subscribers else subscribers,
    videoCount = if (header.videoCount > 0) header.videoCount else videoCount,
    verified = header.verified || verified,
)

@Immutable
data class Comment(
    val id: String,

    val author: String,

    val channelId: String?,

    val name: String? = null,
    val avatar: String?,
    val text: String,
    val likes: Int,
    val ago: String?,
    val pinned: Boolean,
    val hearted: Boolean,
    val replyCount: Int,

    val repliesToken: String?,

    val liked: Boolean = false,
)

@Immutable
data class CommentsPage(val comments: List<Comment>, val total: Int, val disabled: Boolean, val next: String?)


object YouTube {
    private val service get() = ServiceList.YouTube

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    








    suspend fun trending(next: String? = null): FeedPage = withContext(Dispatchers.IO) {


        val extractor = service.kioskList.getExtractorById("Trending", null)
        val items: List<StreamInfoItem>
        val nextPage: String?
        if (next == null) {
            val info = KioskInfo.getInfo(service, extractor.url)
            items = info.relatedItems.filterIsInstance<StreamInfoItem>()
            nextPage = if (info.hasNextPage()) info.nextPage.url else null
        } else {
            val more = KioskInfo.getMoreItems(service, extractor.url, Page(next))
            items = more.items.filterIsInstance<StreamInfoItem>()
            nextPage = more.nextPage?.url
        }


        val videos = items.mapNotNull { it.toVideoItem() }.filter { !it.isLive }
        FeedPage(videos, nextPage)
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toVideoItem() }
    }

    suspend fun searchPage(query: String, pageUrl: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        if (pageUrl.isNullOrBlank()) {
            val info = SearchInfo.getInfo(service, handler)
            val items = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() }.filter { !it.isLive }
            val next = if (info.hasNextPage()) info.nextPage?.url else null
            FeedPage(items, next)
        } else {
            val more = SearchInfo.getMoreItems(service, handler, Page(pageUrl))
            val items = more.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() }.filter { !it.isLive }
            val next = more.nextPage?.url
            FeedPage(items, next)
        }
    }

    suspend fun searchMusic(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val musicQuery = if (q.contains("song", ignoreCase = true) || q.contains("music", ignoreCase = true) || q.contains("audio", ignoreCase = true) || q.contains("lyrics", ignoreCase = true)) {
            q
        } else {
            "$q official audio"
        }
        search(musicQuery).filter { video ->
            video.durationSec in 45..720 || video.durationSec == 0L
        }
    }

    suspend fun searchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else service.suggestionExtractor.suggestionList(q)
    }

    suspend fun details(id: String): VideoDetails = withContext(Dispatchers.IO) {
        val info = StreamCache.getOrLoad(id)
        val picked = StreamSelection.pick(info)
        val isLive = info.streamType == StreamType.LIVE_STREAM
        VideoDetails(
            item = VideoItem(
                id = id,
                title = info.name,
                channel = info.uploaderName.orEmpty(),
                channelUrl = info.uploaderUrl,
                thumbnail = sharpenFeedThumbnail(info.thumbnails.bestUrl(), id),
                avatar = info.uploaderAvatars.bestUrl(targetWidth = 96),
                durationSec = if (isLive) 0L else info.duration,
                views = info.viewCount,
                uploaded = if (isLive) "Live" else uploadedText(info.textualUploadDate, info.uploadDate),
                isLive = isLive,
            ),
            description = info.description?.let { d ->
                if (d.type == Description.HTML) HtmlCompat.fromHtml(d.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                else d.content.trim()
            }.orEmpty(),
            likes = info.likeCount,
            subscribers = info.uploaderSubscriberCount,
            related = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() },
            playable = picked.isPlayable,
            quality = picked.playedHeight?.let { "${it}p" } ?: if (isLive) "Live" else null,
            qualities = StreamSelection.heights(info),
            aspect = (if (picked.usesSeparate) picked.video else picked.muxed)
                ?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height },
        )
    }



    suspend fun channel(url: String): ChannelDetails = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(service, url)
        val videosTab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
        val liveTab = info.tabs.firstOrNull { it.contentFilters.contains("streams") || it.contentFilters.contains("live") }
        ChannelDetails(
            url = url,
            id = info.id,
            name = info.name,
            avatar = info.avatars.bestUrl(targetWidth = 176),
            banner = info.banners.bestUrl(targetWidth = 1080),
            subscribers = info.subscriberCount,
            handle = handleFromChannelUrl(url),
            description = info.description.orEmpty(),
            videosTab = videosTab,
            liveTab = liveTab,
        )
    }

    private fun handleFromChannelUrl(url: String): String? {
        val path = url.substringAfter("youtube.com", url).trimStart('/')
        if (!path.startsWith("@")) return null
        return path.removePrefix("@").substringBefore('/').substringBefore('?').ifBlank { null }
    }

    



    suspend fun channelVideos(channel: ChannelDetails, token: String?): FeedPage {
        val page = InnerTube.channelVideos(channel.id, token)

        return page.copy(videos = page.videos.map {
            it.copy(channel = it.channel.ifBlank { channel.name }, avatar = it.avatar ?: channel.avatar, channelUrl = it.channelUrl ?: channel.url)
        })
    }

    suspend fun channelLive(channel: ChannelDetails, token: String?): FeedPage {
        val page = InnerTube.channelLive(channel.id, token)
        return page.copy(videos = page.videos.map {
            it.copy(channel = it.channel.ifBlank { channel.name }, avatar = it.avatar ?: channel.avatar, channelUrl = it.channelUrl ?: channel.url)
        })
    }

    suspend fun channelShorts(channel: ChannelDetails, token: String?): FeedPage {
        val page = InnerTube.channelShorts(channel.id, token)
        return page.copy(videos = page.videos.map {
            it.copy(channel = it.channel.ifBlank { channel.name }, avatar = it.avatar ?: channel.avatar, channelUrl = it.channelUrl ?: channel.url)
        })
    }

    suspend fun channelCommunity(channel: ChannelDetails, token: String?): CommunityPage {
        return InnerTube.channelCommunity(channel.id, token)
    }

    private fun StreamInfoItem.toVideoItem(): VideoItem? {
        if (streamType != StreamType.VIDEO_STREAM && streamType != StreamType.LIVE_STREAM) return null
        val id = runCatching { service.streamLHFactory.getId(url) }.getOrNull() ?: return null
        val live = streamType == StreamType.LIVE_STREAM
        return VideoItem(
            id = id,
            title = name,
            channel = uploaderName.orEmpty(),
            channelUrl = uploaderUrl,
            thumbnail = sharpenFeedThumbnail(thumbnails.bestUrl(), id),
            avatar = uploaderAvatars.bestUrl(targetWidth = 96),
            durationSec = if (live) 0L else duration,
            views = viewCount,
            uploaded = if (live) "Live" else uploadedText(textualUploadDate, uploadDate),
            isLive = live,
        )
    }


    private fun List<Image>.bestUrl(targetWidth: Int = 1080): String? =
        filter { it.width > 0 }.minByOrNull { abs(it.width - targetWidth) }?.url ?: firstOrNull()?.url

    



    private fun uploadedText(textual: String?, date: DateWrapper?): String? {
        if (textual != null && !ISO_DATE.containsMatchIn(textual)) return textual
        val then = date?.offsetDateTime()?.toInstant() ?: return textual?.substringBefore('T')
        return relativeTime(then)
    }

    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}T")

    fun relativeTime(then: Instant, now: Instant = Instant.now()): String {
        val s = Duration.between(then, now).seconds.coerceAtLeast(0)
        fun ago(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
        return when {
            s < 60 -> "just now"
            s < 3_600 -> ago(s / 60, "minute")
            s < 86_400 -> ago(s / 3_600, "hour")
            s < 7 * 86_400 -> ago(s / 86_400, "day")
            s < 30 * 86_400 -> ago(s / (7 * 86_400), "week")
            s < 365 * 86_400 -> ago(s / (30 * 86_400), "month")
            else -> ago(s / (365 * 86_400), "year")
        }
    }


    fun friendlyError(t: Throwable): String = when (t) {
        is AgeRestrictedContentException -> "This video is age-restricted and needs a YouTube account to watch."
        is GeographicRestrictionException -> "This video isn't available in your country."
        is org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException ->


            "YouTube is blocking this video for a moment. Try again in a minute, or switch networks."
        is org.schabi.newpipe.extractor.exceptions.PrivateContentException -> "This video is private."


        is ContentNotAvailableException ->
            if (t.message.orEmpty().contains("member", ignoreCase = true) || t is org.schabi.newpipe.extractor.exceptions.PaidContentException)
                "This is a members-only video. YTXTube can't play members-only videos yet."
            else "This video isn't available."
        is ReCaptchaException -> "YouTube is rate-limiting requests right now. Try again in a minute."
        is IOException -> "Couldn't reach YouTube. Check your connection."
        else -> "Something went wrong loading this. Try again."
    }
}






object StreamCache {
    private const val MAX_AGE_MS = 4 * 60 * 60 * 1000L
    private class Entry(val info: StreamInfo, val loadedAt: Long)
    private val entries = ConcurrentHashMap<String, Entry>()

    



    fun invalidate(id: String) { entries.remove(id) }


    fun invalidateAll() = entries.clear()

    fun get(id: String): StreamInfo? =
        entries[id]?.takeIf { System.currentTimeMillis() - it.loadedAt < MAX_AGE_MS }?.info

    



    private val inflight = ConcurrentHashMap<String, FutureTask<StreamInfo>>()

    fun getOrLoad(id: String): StreamInfo {
        get(id)?.let { return it }
        val mine = FutureTask {
            try {
                StreamInfo.getInfo(ServiceList.YouTube, YouTube.watchUrl(id)).also {
                    entries[id] = Entry(it, System.currentTimeMillis())
                    Net.noteMediaHost(StreamSelection.pick(it).let { p -> (p.video ?: p.muxed)?.content })
                    CaptionTracks.put(id, CaptionTracks.fromStreamInfo(it))
                    if (it.subtitles.isEmpty()) runCatching { CaptionTracks.load(id) }
                }
            } catch (t: Throwable) {
                if (t is org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException) {

                    entries.remove(id)
                    StreamInfo.getInfo(ServiceList.YouTube, YouTube.watchUrl(id)).also {
                        entries[id] = Entry(it, System.currentTimeMillis())
                        Net.noteMediaHost(StreamSelection.pick(it).let { p -> (p.video ?: p.muxed)?.content })
                        CaptionTracks.put(id, CaptionTracks.fromStreamInfo(it))
                        if (it.subtitles.isEmpty()) runCatching { CaptionTracks.load(id) }
                    }
                } else throw t
            }
        }
        val running = inflight.putIfAbsent(id, mine)
        val task = running ?: mine.also { it.run() }
        try {
            return task.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } finally {
            if (running == null) inflight.remove(id, mine)
        }
    }


    /**
     * Shorts and the next-up queue can resolve several videos at once; a single prefetch thread
     * used to make the tapped video wait behind an unrelated prefetch. Four lanes keep the
     * critical one moving without hammering YouTube.
     */
    private val prefetcher = Executors.newFixedThreadPool(4)

    fun prefetch(id: String) {
        if (get(id) != null || inflight.containsKey(id)) return
        prefetcher.execute { runCatching { getOrLoad(id) } }
    }

    fun prefetchAll(ids: Iterable<String>) {
        ids.forEach(::prefetch)
    }
}





object QualityPreference {
    private const val KEY = "quality_height"
    private var prefs: SharedPreferences? = null

    @Volatile
    var height: Int? = null
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        height = prefs?.getInt(KEY, 0)?.takeIf { it > 0 }?.takeIf { it in QUALITY_LADDER }
    }

    fun set(value: Int?) {
        height = value
        prefs?.edit()?.putInt(KEY, value ?: 0)?.apply()
    }
}


val QUALITY_LADDER = intArrayOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)





fun qualityTier(width: Int, height: Int): Int {
    if (height <= 0) return 0
    // Portrait video (Shorts) is sized by its short side: a 1080x1920 short is 1080p, not 2160p.
    val basis = when {
        width <= 0 -> height
        width < height -> width
        width.toFloat() / height > 16f / 9f + 0.02f -> width * 9 / 16
        else -> height
    }
    return QUALITY_LADDER.minByOrNull { kotlin.math.abs(it - basis) } ?: basis
}

private fun VideoStream.tier() = qualityTier(width, height)

/** Whether this rendition's real (or typical) bitrate fits the sustainable budget. */
private fun VideoStream.fitsKbps(budgetKbps: Int): Boolean {
    val bits = bitrate
    val needed = if (bits > 0) bits / 1000 else NetworkSpeed.typicalKbps(tier())
    return needed <= budgetKbps
}

private fun videoOrder(hdr: Boolean, formatRank: (MediaFormat?) -> Int): Comparator<VideoStream> =
    compareBy<VideoStream> { it.tier() }
        .thenBy { if (hdr && HdrSupport.isHdr(it)) 1 else 0 }
        .thenBy { it.bitrate }
        .thenBy { -formatRank(it.format) }

private fun lowestFirst(formatRank: (MediaFormat?) -> Int): Comparator<VideoStream> =
    compareBy<VideoStream> { it.tier() }
        .thenBy { it.bitrate }
        .thenBy { formatRank(it.format) }


object StreamSelection {

    private const val MAX_TIER = 2160

    data class Picked(val video: VideoStream?, val audio: AudioStream?, val muxed: VideoStream?, val hlsUrl: String? = null) {
        val isPlayable get() = (video != null && audio != null) || muxed != null || !hlsUrl.isNullOrBlank()

        val usesSeparate get() = video != null && audio != null

        val playedHeight: Int? get() = (if (usesSeparate) video?.tier() else muxed?.tier())?.takeIf { it > 0 }
    }

    fun pick(info: StreamInfo): Picked {
        val hls = info.hlsUrl?.ifBlank { null }

        fun formatRank(format: MediaFormat?) = when (format) {
            MediaFormat.MPEG_4 -> 0
            MediaFormat.WEBM -> 1
            else -> 2
        }
        val candidates = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 }
        val chosen = QualityPreference.height

        // Auto uses the measured throughput, not the link speed: a Wi-Fi link can report
        // hundreds of megabits while the connection behind it cannot feed 4K. A manual
        // choice still wins and is honoured as the closest rendition at or below it.
        val hdr = HdrSupport.isSupported
        val video = if (chosen != null) {
            candidates
                .filter { it.tier() <= chosen }
                .maxWithOrNull(videoOrder(hdr, ::formatRank))
                ?: candidates.minWithOrNull(lowestFirst(::formatRank))
        } else {
            val budget = NetworkSpeed.sustainableKbps()
            candidates
                .filter { it.fitsKbps(budget) }
                .maxWithOrNull(videoOrder(hdr, ::formatRank))
                ?: candidates.minWithOrNull(lowestFirst(::formatRank))
        }



        val audio = selectAudio(info)
        val muxedCandidates = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val muxed = if (chosen != null) {
            muxedCandidates.filter { it.tier() <= chosen }
                .maxWithOrNull(videoOrder(hdr, ::formatRank))
                ?: muxedCandidates.minWithOrNull(lowestFirst(::formatRank))
        } else {
            val budget = NetworkSpeed.sustainableKbps()
            muxedCandidates.filter { it.fitsKbps(budget) }
                .maxWithOrNull(videoOrder(hdr, ::formatRank))
                ?: muxedCandidates.minWithOrNull(lowestFirst(::formatRank))
        }
        return Picked(video, audio, muxed, hls)
    }

    /**
     * Every logical audio track the extractor can actually serve, Original first. One row per
     * track (highest bitrate encoding), never one per itag; untagged entries are dropped once the
     * response names real tracks, because that bucket is the same default audio arriving twice.
     */
    fun audioTracks(info: StreamInfo): List<AudioTrackOption> {
        val usable = info.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        if (usable.isEmpty()) return emptyList()
        val named = usable.any {
            !it.audioTrackId.isNullOrBlank() || !it.audioTrackName.isNullOrBlank() || it.audioLocale != null
        }
        val byKey = LinkedHashMap<String, AudioStream>()
        usable.sortedByDescending { it.averageBitrate }.forEach { stream ->
            val key = audioTrackKey(stream)
            if (named && key == "default") return@forEach
            byKey.putIfAbsent(key, stream)
        }
        return byKey.values
            .sortedWith(
                compareBy<AudioStream> { if (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL) 0 else 1 }
                    .thenBy { if (it.format == MediaFormat.M4A) 0 else 1 }
                    .thenByDescending { it.averageBitrate },
            )
            .map { stream ->
                val original = stream.audioTrackType == null || stream.audioTrackType == AudioTrackType.ORIGINAL
                AudioTrackOption(
                    key = audioTrackKey(stream),
                    label = audioTrackLabel(stream, original),
                    detail = audioTrackDetail(stream, original),
                    stream = stream,
                )
            }
    }

    /** The alternate languages shown in the sheet; Original is its own fixed first row. */
    fun audioOptions(info: StreamInfo): List<AudioTrackOption> =
        audioTracks(info).filterNot { it.isOriginal }

    /**
     * Audio selection. Original is the default on a fresh install and stays selected until the
     * viewer explicitly chooses another track; a stale preference falls back to Original rather
     * than leaving the video silent.
     */
    fun selectAudio(info: StreamInfo): AudioStream? {
        val tracks = audioTracks(info)
        if (tracks.isEmpty()) return null
        val wanted = AudioTrackPreference.key
        if (wanted != null) {
            tracks.firstOrNull { it.key == wanted }?.let { return it.stream }
            // Preferences written by the old key format carried the itag as a suffix.
            tracks.firstOrNull { wanted.startsWith(it.key) }?.let { return it.stream }
        }
        return tracks.firstOrNull { it.isOriginal }?.stream ?: tracks.first().stream
    }

    /**
     * Shorts use the original audio and the best rendition the measured connection can
     * sustain. A muxed stream is only the fallback when the video has no separable
     * adaptive pair; it is usually capped at 360p/720p.
     */
    fun pickShorts(info: StreamInfo): Picked {
        val chosen = QualityPreference.height
        val hdr = HdrSupport.isSupported
        fun order(): Comparator<VideoStream> =
            compareBy<VideoStream> { it.tier() }
                .thenBy { if (hdr && HdrSupport.isHdr(it)) 1 else 0 }
                .thenBy { if (it.codec?.startsWith("avc") == true) 1 else 0 }
                .thenBy { it.bitrate }

        fun lowest(): Comparator<VideoStream> =
            compareBy<VideoStream> { it.tier() }
                .thenBy { if (it.codec?.startsWith("avc") == true) 0 else 1 }
                .thenBy { it.bitrate }

        fun muxedOrder(): Comparator<VideoStream> =
            compareBy<VideoStream> { it.tier() }
                .thenBy { if (it.codec?.startsWith("avc") == true) 1 else 0 }
                .thenBy { it.bitrate }

        val candidates = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 && it.tier() <= 2160 }
        val video = if (chosen != null) {
            candidates.filter { it.tier() <= chosen }.maxWithOrNull(order())
                ?: candidates.minWithOrNull(lowest())
        } else {
            val budget = NetworkSpeed.sustainableKbps()
            candidates.filter { it.fitsKbps(budget) }.maxWithOrNull(order())
                ?: candidates.minWithOrNull(lowest())
        }
        val audio = audioTracks(info).firstOrNull { it.isOriginal }?.stream
            ?: info.audioStreams.filter { it.isUrl }
                .sortedWith(compareBy<AudioStream> { if (it.format == MediaFormat.M4A) 0 else 1 }.thenByDescending { it.averageBitrate })
                .firstOrNull()
        if (video != null && audio != null) return Picked(video, audio, null)

        val muxedCandidates = info.videoStreams.filter { it.isUrl && it.height > 0 }
        val muxed = if (chosen != null) {
            muxedCandidates.filter { it.tier() <= chosen }.maxWithOrNull(muxedOrder())
                ?: muxedCandidates.minWithOrNull(lowest())
        } else {
            val budget = NetworkSpeed.sustainableKbps()
            muxedCandidates.filter { it.fitsKbps(budget) }.maxWithOrNull(muxedOrder())
                ?: muxedCandidates.minWithOrNull(lowest())
        }
        return Picked(null, null, muxed)
    }

    /** The best side-loadable caption track for the current caption language. */
    fun subtitleFor(info: StreamInfo): SubtitlesStream? {
        val usable = info.subtitles.filter {
            it.isUrl && it.content.isNotBlank() && subtitleMimeType(it) != null
        }
        if (usable.isEmpty()) return null
        val wanted = CaptionPreference.languageTag.lowercase(Locale.US)
        return usable.firstOrNull { it.languageTag.lowercase(Locale.US).startsWith(wanted) && !it.isAutoGenerated }
            ?: usable.firstOrNull { it.languageTag.lowercase(Locale.US).startsWith(wanted) }
            ?: usable.firstOrNull { !it.isAutoGenerated }
            ?: usable.first()
    }

    fun hasCaptions(info: StreamInfo): Boolean = info.subtitles.any {
        it.isUrl && it.content.isNotBlank() && subtitleMimeType(it) != null
    }

    





    fun autoLadder(info: StreamInfo, maxTier: Int = MAX_TIER): List<VideoStream> {
        val usable = info.videoOnlyStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                it.tier() in 1..maxTier && it.itagItem != null
        }
        val hdr = HdrSupport.isSupported
        return usable.groupBy { it.tier() }
            .map { (_, sameTier) ->
                sameTier.maxWith(
                    compareBy<VideoStream> { if (hdr && HdrSupport.isHdr(it)) 2 else 0 }
                        .thenBy { it.fps * 100_000L + it.bitrate },
                )
            }
            .sortedBy { it.tier() }
    }

    fun heights(info: StreamInfo): List<Int> {
        val progressive = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 }
            .map { it.tier() }
        if (progressive.isNotEmpty()) {
            return progressive.distinct().sortedDescending()
        }
        val muxed = info.videoStreams
            .filter { it.isUrl && it.height > 0 }
            .map { it.tier() }
        if (muxed.isNotEmpty()) {
            return muxed.distinct().sortedDescending()
        }
        if (info.streamType == StreamType.LIVE_STREAM || !info.hlsUrl.isNullOrBlank()) {
            return listOf(1080, 720, 480, 360, 240, 144)
        }
        return emptyList()
    }
}





private val YT_VIDEO_ID_IN_THUMB = Regex("""i\.ytimg\.com/vi/([\w-]{11})/""")

fun ytVideoIdFromThumbUrl(url: String): String? = YT_VIDEO_ID_IN_THUMB.find(url)?.groupValues?.getOrNull(1)

fun isLowRungYtimgUrl(url: String): Boolean {
    val path = url.substringBefore('?')
    return "/hqdefault" in path || "/mqdefault" in path || "/default.jpg" in path
}

fun ytimgThumbnailWidth(url: String): Int {
    Regex("=w(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    return when {
        "maxresdefault" in url -> 1280
        "sddefault" in url -> 640
        "hqdefault" in url -> 480
        "mqdefault" in url -> 320
        else -> 0
    }
}


fun feedThumbnailUrl(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hq720.jpg"


fun sharpenFeedThumbnail(apiUrl: String?, videoId: String): String {
    val api = apiUrl?.trim()?.ifBlank { null }

    if (api != null && isLowRungYtimgUrl(api)) return feedThumbnailUrl(videoId)
    if (api != null && ytimgThumbnailWidth(api) >= 720) return api
    if (api != null && ("googleusercontent.com" in api || "ggpht.com" in api)) {
        return upscaleGgphtThumbnail(api, 1280)
    }
    return feedThumbnailUrl(videoId)
}


fun channelAvatarEdgePx(url: String): Int =
    Regex("=s(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0


fun normalizeChannelAvatarUrl(url: String): String {
    if ("/ytc/" !in url || "googleusercontent.com" in url) return url
    val id = url.substringAfter("/ytc/").substringBefore("=").substringBefore("?").ifBlank { return url }
    return "https://yt3.googleusercontent.com/ytc/$id=s88-c-k-c0x00ffffff-no-rj"
}


fun sharpenChannelAvatarUrl(url: String, minEdgePx: Int): String {
    val normalized = normalizeChannelAvatarUrl(url)
    val m = Regex("=s(\\d+)").find(normalized) ?: return normalized
    val current = m.groupValues[1].toIntOrNull() ?: return normalized
    if ("/ytc/" in normalized && current > 96 && "googleusercontent.com" !in normalized) {
        return normalized.replace(Regex("=s\\d+"), "=s96")
    }
    if (current >= minEdgePx) return normalized

    val ceiling = if ("/ytc/" in normalized && "googleusercontent.com" !in normalized) 96 else 800
    val edge = minOf(minEdgePx, ceiling).coerceAtLeast(current)
    return normalized.replace(Regex("=s\\d+"), "=s$edge")
}

internal fun upscaleGgphtThumbnail(url: String, targetWidth: Int): String {
    val current = ytimgThumbnailWidth(url)
    if (current >= targetWidth) return url
    val height = targetWidth * 9 / 16
    return if ("=w" in url) {
        url.replace(Regex("=w\\d+-h\\d+"), "=w$targetWidth-h$height")
    } else {
        "$url=w$targetWidth-h$height"
    }
}







fun fetchDislikeCount(videoId: String): Long {
    return runCatching {
        val req = okhttp3.Request.Builder()
            .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
            .header("User-Agent", Net.USER_AGENT)
            .build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@runCatching -1L
            org.json.JSONObject(r.body?.string().orEmpty()).optLong("dislikes", -1L)
        }
    }.getOrElse { -1L }
}
