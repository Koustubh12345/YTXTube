package com.tensei.ytxtube

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.AdaptationSet
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.dash.manifest.Period
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.Stream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors





@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val resolver = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))

    override fun onCreate() {
        super.onCreate()

        val sources = YouTubeMediaSourceFactory(this)
        val player = ExoPlayer.Builder(this, SyncedRenderersFactory(this))
            .setMediaSourceFactory(sources)
            .setBandwidthMeter(PlaybackBandwidth.get())


            .setTrackSelector(
                DefaultTrackSelector(
                    this,
                    AdaptiveTrackSelection.Factory(
 2_000,
 25_000,
 25_000,
 0.75f,
                    ),
                ).apply {



                    setParameters(
                        buildUponParameters()
                            .setAllowVideoMixedDecoderSupportAdaptiveness(true)
                            .setAllowVideoNonSeamlessAdaptiveness(true),
                    )
                },
            )


            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Start after a quarter second, keep a 30 s runway and a 10 s rewind
                    // buffer. The old 15 s/50 s/1 s policy is what made every open sit on
                    // a spinner before the first frame.
                    .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, 64 * 1024))
                    .setBufferDurationsMs(2_500, 30_000, 250, 750)
                    .setBackBuffer(10_000, false)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
 true,
            )
            .setHandleAudioBecomingNoisy(true)


            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        player.setVideoFrameMetadataListener { _, releaseTimeNs, _, _ -> AvSync.onFrameReleased(releaseTimeNs) }
        player.addListener(SponsorSkipper(player))
        player.addListener(SpeedKeeper(player))


        if (AudioOnly.enabled) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
        }

        player.addListener(StreamRecoverer(player, sources, resolver))
        resumeSaver = ResumeSaver(player).also(player::addListener)
        watchTime = WatchTimeReporter(player).also(player::addListener)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {


        pauseAllPlayersAndStopSelf()
    }

    private var resumeSaver: ResumeSaver? = null
    private var watchTime: WatchTimeReporter? = null

    override fun onDestroy() {
        resumeSaver?.saveNow()
        watchTime?.finish()
        session?.run {
            player.release()
            release()
        }
        session = null
        resolver.shutdown()
        super.onDestroy()
    }

    




    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = resolver.submit<MutableList<MediaItem>> {
            mediaItems.map { item ->
                val local = item.requestMetadata.mediaUri?.takeIf { it.scheme == "file" }
                    ?: item.mediaMetadata.extras?.getString("local_uri")?.let(Uri::parse)
                if (local != null) {

                    item.buildUpon()
                        .setUri(local)
                        .setMediaMetadata(
                            item.mediaMetadata.buildUpon()
                                .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                                .build(),
                        )
                        .build()
                } else {
                    val info = StreamCache.getOrLoad(item.mediaId)
                    item.buildUpon()
                        .setUri(YouTube.watchUrl(item.mediaId))
                        .setMediaMetadata(
                            item.mediaMetadata.buildUpon()
                                .setTitle(item.mediaMetadata.title ?: info.name)
                                .setArtist(item.mediaMetadata.artist ?: info.uploaderName)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                                .build(),
                        )
                        .build()
                }
            }.toMutableList()
        }
    }
}


object StreamRecovery {

    var recovering by mutableStateOf<String?>(null)
        internal set


    var failed by mutableStateOf<Pair<String, String>?>(null)
        internal set
}








@OptIn(UnstableApi::class)
private class StreamRecoverer(
    private val player: ExoPlayer,
    private val sources: MediaSource.Factory,
    private val resolver: java.util.concurrent.Executor,
) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private var lastId: String? = null
    private var lastPositionMs = 0L

    override fun onPlayerError(error: PlaybackException) {
        if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return
        val item = player.currentMediaItem ?: return

        if (item.localConfiguration?.uri?.scheme == "file") return
        val id = item.mediaId
        val position = player.currentPosition
        if (id == lastId && position < lastPositionMs + PROGRESS_MS) {
            Log.w("YTXTube", "stream refused again for $id at ${position / 1000}s; giving up")
            return
        }
        lastId = id
        lastPositionMs = position
        Log.i("YTXTube", "stream refused for $id; re-extracting and resuming at ${position / 1000}s")
        StreamRecovery.failed = null
        StreamRecovery.recovering = id
        StreamCache.invalidate(id)
        resolver.execute {
            val result = runCatching { StreamCache.getOrLoad(id) }
            main.post {
                StreamRecovery.recovering = null

                if (player.currentMediaItem?.mediaId != id || player.playerError == null) return@post
                result
                    .onSuccess { resume(item, position) }
                    .onFailure {
                        Log.w("YTXTube", "re-extracting $id failed", it)
                        StreamRecovery.failed = id to YouTube.friendlyError(it)
                    }
            }
        }
    }

    



    private fun resume(item: MediaItem, positionMs: Long) {
        val index = player.currentMediaItemIndex
        player.addMediaSource(index + 1, sources.createMediaSource(item))
        player.removeMediaItem(index)
        player.seekTo(index, positionMs)
        player.prepare()
    }

    private companion object {
        const val PROGRESS_MS = 30_000L
    }
}









@OptIn(UnstableApi::class)
private class WatchTimeReporter(private val player: ExoPlayer) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var videoId: String? = null
    private var session: InnerTube.WatchSession? = null
    private var startedAt = 0L

    private val starts = ArrayList<Double>()
    private val ends = ArrayList<Double>()

    private var stretchFrom: Long? = null
    private var pings = 0
    private val tick = Runnable { onTick() }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val id = mediaItem?.mediaId?.ifBlank { null }

        if (id == videoId) return
        finish()
        videoId = id
        if (id == null || !Account.signedIn) return
        startedAt = android.os.SystemClock.elapsedRealtime()
        if (player.isPlaying) startStretch()
        io.execute {
            val started = runCatching { InnerTube.startWatch(id) }
                .onFailure { Log.w("YTXTube", "watch start for $id", it) }.getOrNull()
            main.post { if (videoId == id) session = started }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (videoId == null || !Account.signedIn) return
        if (isPlaying) {
            startStretch()
        } else {
            endStretch(player.currentPosition)
            send(playing = false, final = false)
        }
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (stretchFrom == null) return
        endStretch(oldPosition.positionMs)

        if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex && player.isPlaying) stretchFrom = newPosition.positionMs
    }


    fun finish() {
        main.removeCallbacks(tick)
        if (stretchFrom != null && player.currentMediaItem?.mediaId == videoId) endStretch(player.currentPosition)
        stretchFrom = null
        send(playing = false, final = true)
        videoId = null
        session = null
        starts.clear(); ends.clear()
        pings = 0
    }

    private fun startStretch() {
        stretchFrom = player.currentPosition
        schedule()
    }

    private fun endStretch(atMs: Long) {
        val from = stretchFrom ?: return
        stretchFrom = null
        main.removeCallbacks(tick)
        if (atMs > from + 250) { starts += from / 1000.0; ends += atMs / 1000.0 }
    }

    private fun schedule() {
        main.removeCallbacks(tick)
        main.postDelayed(tick, PING_DELAYS_MS.getOrElse(pings) { PING_DELAYS_MS.last() })
    }

    private fun onTick() {
        if (!player.isPlaying) return
        val now = player.currentPosition
        endStretch(now)
        stretchFrom = now
        send(playing = true, final = false)
        schedule()
    }


    private fun send(playing: Boolean, final: Boolean) {
        val s = session ?: return
        if (starts.isEmpty() && !final) return
        pings++
        val position = player.currentPosition / 1000.0
        val elapsed = (android.os.SystemClock.elapsedRealtime() - startedAt) / 1000.0
        val st = starts.toList()
        val et = ends.toList()
        starts.clear(); ends.clear()
        io.execute {
            runCatching { InnerTube.pingWatchtime(s, position, st, et, playing, elapsed, final) }
                .onFailure { Log.w("YTXTube", "watch time for ${s.videoId}", it) }
        }
    }

    private companion object {
        val PING_DELAYS_MS = longArrayOf(10_000, 10_000, 10_000, 40_000).toList()
    }
}






private class SpeedKeeper(private val player: ExoPlayer) : Player.Listener {
    private var videoId: String? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val id = mediaItem?.mediaId?.ifBlank { null } ?: return
        if (id == videoId) return
        videoId = id
        player.setPlaybackSpeed(PlaybackSpeed.forNewVideo)
    }
}









@OptIn(UnstableApi::class)
private class SponsorSkipper(private val player: ExoPlayer) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val id = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        io.execute {
            val segments = SponsorBlock.fetch(id)
            main.post {
                SponsorBlock.forUi[id] = segments

                if (player.currentMediaItem?.mediaId != id) return@post
                val index = player.currentMediaItemIndex
                segments.forEach { segment ->
                    player.createMessage { _, _ -> skip(id, segment) }
                        .setLooper(Looper.getMainLooper())
                        .setPosition(index, segment.startMs)
                        .send()
                }
            }
        }
    }

    private fun skip(videoId: String, segment: SponsorBlock.Segment) {

        if (!SponsorBlock.enabled || segment.action != SponsorBlock.Action.Auto) return
        if (player.currentMediaItem?.mediaId != videoId) return
        if (player.currentPosition > segment.endMs - 500) return
        Log.i("YTXTube", "SponsorBlock: skipping ${segment.category.key} ${segment.startMs}-${segment.endMs} in $videoId")
        ramp(from = 1f, to = 0f, durationMs = 300) {
            player.seekTo(segment.endMs)
            SponsorBlock.lastSkip = SponsorBlock.Skip(videoId, segment)
            ramp(from = 0f, to = 1f, durationMs = 450) {}
        }
    }

    private fun ramp(from: Float, to: Float, durationMs: Long, then: () -> Unit) {
        val start = android.os.SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                val t = ((android.os.SystemClock.uptimeMillis() - start).toFloat() / durationMs).coerceIn(0f, 1f)
                player.volume = from + (to - from) * t
                if (t < 1f) main.postDelayed(this, 16) else then()
            }
        }
        main.post(step)
    }
}







@OptIn(UnstableApi::class)
private class YouTubeMediaSourceFactory(context: Context) : MediaSource.Factory {
    private val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
        context,
        OkHttpDataSource.Factory(Net.client).setUserAgent(Net.USER_AGENT),
    )
    private val fallback = DefaultMediaSourceFactory(dataSourceFactory)

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory =
        apply { fallback.setDrmSessionManagerProvider(provider) }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory =
        apply { fallback.setLoadErrorHandlingPolicy(policy) }

    override fun getSupportedTypes(): IntArray = fallback.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        buildSource(mediaItem)

    private fun buildSource(mediaItem: MediaItem): MediaSource {

        if (mediaItem.localConfiguration?.uri?.scheme == "file") {
            return fallback.createMediaSource(mediaItem)
        }
        val info = StreamCache.get(mediaItem.mediaId) ?: return fallback.createMediaSource(mediaItem)
        val isLive = info.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
        val hlsUrl = info.hlsUrl?.ifBlank { null }
        // HLS is a live fallback only. A VOD HLS manifest carries just YouTube's default audio
        // track, so using it for normal videos silently ignored both the picked language and the
        // picked quality. VOD goes through the generated DASH/progressive path below.
        if (isLive && !hlsUrl.isNullOrBlank()) {
            Log.i("YTXTube", "media source ${mediaItem.mediaId}: playing live HLS stream from $hlsUrl")
            return HlsMediaSource.Factory(dataSourceFactory).createMediaSource(
                MediaItem.Builder()
                    .setUri(hlsUrl)
                    .setMediaId(mediaItem.mediaId)
                    .setMediaMetadata(mediaItem.mediaMetadata)
                    .build(),
            )
        }
        val picked = StreamSelection.pick(info)

        // Nothing separable or muxed was extracted: HLS is still better than a black screen.
        if (picked.video == null && picked.muxed == null && !hlsUrl.isNullOrBlank()) {
            Log.i("YTXTube", "media source ${mediaItem.mediaId}: no progressive streams, falling back to HLS")
            return HlsMediaSource.Factory(dataSourceFactory).createMediaSource(
                MediaItem.Builder()
                    .setUri(hlsUrl)
                    .setMediaId(mediaItem.mediaId)
                    .setMediaMetadata(mediaItem.mediaMetadata)
                    .build(),
            )
        }

        if (QualityPreference.height == null && picked.audio != null) {
            val adaptive = adaptiveVideo(info, mediaItem)
            if (adaptive != null) {
                Log.i("YTXTube", "media source ${mediaItem.mediaId}: adaptive video up to ${NetworkSpeed.linkHeight()}p + audio itag ${picked.audio.itag}")
                return MergingMediaSource(true, true, adaptive, source(picked.audio, info.duration, mediaItem))
            }
        }
        Log.i(
            "YTXTube",
            "media source ${mediaItem.mediaId}: " + if (picked.usesSeparate) "video itag ${picked.video?.itag} (${picked.video?.height}p) + audio itag ${picked.audio?.itag}"
            else "combined fallback itag ${picked.muxed?.itag} (${picked.muxed?.height}p); video=${picked.video != null} audio=${picked.audio != null}; " +
                "audio offered: " + info.audioStreams.joinToString { "${it.itag}/${it.format}/${it.deliveryMethod}/url=${it.isUrl}/${it.audioTrackType}" },
        )
        val video = picked.video
        val audio = picked.audio
        return when {
            video != null && audio != null -> MergingMediaSource(
 true,
 true,
                source(video, info.duration, mediaItem),
                source(audio, info.duration, mediaItem),
            )
            picked.muxed != null -> source(picked.muxed, info.duration, mediaItem)


            else -> fallback.createMediaSource(mediaItem)
        }
    }

    




    private fun adaptiveVideo(info: org.schabi.newpipe.extractor.stream.StreamInfo, mediaItem: MediaItem): MediaSource? {
        val durationSec = info.duration.takeIf { it > 0 } ?: return null
        // Build the ladder up to what the link itself could carry and let ExoPlayer's
        // bandwidth meter choose the starting rung. It starts conservatively and climbs
        // as real throughput is measured, so fast connections still reach high quality.
        val maxTier = NetworkSpeed.linkHeight()
        val ladder = StreamSelection.autoLadder(info, maxTier)
        val manifests = ladder.mapNotNull { stream ->
            runCatching {
                val xml = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(stream.content, stream.itagItem!!, durationSec)
                DashManifestParser().parse(Uri.parse(stream.content), ByteArrayInputStream(xml.toByteArray()))
            }.getOrNull()
        }
        if (manifests.size < 2) return null
        val representations = manifests.map { it.getPeriod(0).adaptationSets[0].representations[0] }
        val base = manifests.first()
        val manifest = DashManifest(
            base.availabilityStartTimeMs, base.durationMs, base.minBufferTimeMs, false,
            C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, null, null, null, null,
            listOf(Period("0", 0, listOf(AdaptationSet(0, C.TRACK_TYPE_VIDEO, representations, emptyList(), emptyList(), emptyList())))),
        )
        return DashMediaSource.Factory(dataSourceFactory).createMediaSource(manifest, mediaItem)
    }

    private fun source(stream: Stream, durationSec: Long, mediaItem: MediaItem): MediaSource {
        val url = stream.content
        val itag = stream.itagItem
        if (itag != null && durationSec > 0) {
            val dash = runCatching {
                val xml = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(url, itag, durationSec)
                val manifest = DashManifestParser().parse(Uri.parse(url), ByteArrayInputStream(xml.toByteArray()))
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(manifest, mediaItem)
            }.getOrNull()
            if (dash != null) return dash
        }

        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem.buildUpon().setUri(url).build())
    }
}
