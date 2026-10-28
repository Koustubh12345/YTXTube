package com.tensei.ytxtube

import android.content.ComponentName
import android.util.Log
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext





@Stable
class PlayerState(private val scope: CoroutineScope) {
    var controller by mutableStateOf<MediaController?>(null)
        internal set


    var current by mutableStateOf<VideoItem?>(null)
        private set
    var details by mutableStateOf<VideoDetails?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    var speed by mutableFloatStateOf(1f)
        private set
    var loopEnabled by mutableStateOf(false)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set
    var isMusicMode by mutableStateOf(false)
    var autoPlayEnabled by mutableStateOf(true)
        private set
    var videoZoom by mutableFloatStateOf(1f)
    var zoomToFill by mutableStateOf(false)

    fun resetZoom() { videoZoom = 1f; zoomToFill = false }

    fun toggleZoomToFill() {
        zoomToFill = !zoomToFill
        actionMessage = if (zoomToFill) "Zoomed to fill screen" else "Fit to screen"
    }

    fun toggleAutoPlay() {
        autoPlayEnabled = !autoPlayEnabled
        actionMessage = if (autoPlayEnabled) "Autoplay on" else "Autoplay off"
    }

    fun toggleShuffle() {
        val player = controller ?: return
        val next = !player.shuffleModeEnabled
        player.shuffleModeEnabled = next
        shuffleEnabled = next
        actionMessage = if (shuffleEnabled) "Shuffle on" else "Shuffle off"
    }

    fun skipNext() {

        if (isMusicMode && musicQueue.isNotEmpty()) {
            openMusicQueue(musicQueue, 0, expanded)
            return
        }
        if (!isMusicMode && queue.isNotEmpty()) {
            playFromQueue(0)
        } else {
            autoPlayNext()
        }
    }

    fun skipPrevious() {
        val player = controller ?: return
        if (player.currentPosition > 3000) {
            player.seekTo(0)
        } else {
            player.seekTo(0)
        }
    }

    fun toggleLoop() {
        val player = controller ?: return
        val next = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        player.repeatMode = next
        loopEnabled = next == Player.REPEAT_MODE_ONE
        actionMessage = if (loopEnabled) "Loop on" else "Loop off"
    }
    var speedPanelOpen by mutableStateOf(false)

    var holdSpeedActive by mutableStateOf(false)
        internal set

    var scrubPreviewMs by mutableStateOf<Long?>(null)
        internal set
    var scrubDragging by mutableStateOf(false)
        internal set
    private var resumePlayAfterScrub = false
    private var speedBeforeHold: Float? = null
    var videoControlsVisible by mutableStateOf(false)
        internal set

    init {

        scope.launch {
            snapshotFlow { StreamRecovery.recovering to StreamRecovery.failed }.collect { (_, failed) ->
                syncFromPlayer()
                if (failed != null && failed.first == current?.id) {
                    error = failed.second
                    isBuffering = false
                }
            }
        }
    }
    var videoAspect by mutableFloatStateOf(16f / 9f)
        private set

    private var aspectFromFrames = false

    



    val stageAspect: Float get() = if (AudioOnly.enabled) AudioStageAspect else {
        min(videoAspect.coerceIn(StageMinAspect, StageMaxAspect), StageFrameAspect)
    }
    var firstFrameRendered by mutableStateOf(false)
        private set

    var playingFormat by mutableStateOf<String?>(null)
        private set

    var decodedHeight by mutableStateOf<Int?>(null)
        private set

    var expanded by mutableStateOf(false)

    var fullscreen by mutableStateOf(false)

    /** Portrait videos go edge-to-edge without rotating the device, exactly like YouTube. */
    var portraitFullscreen by mutableStateOf(false)

    val portraitVideo get() = videoAspect < 1.02f

    var sheet by mutableStateOf<PlayerSheet?>(null)

    var downloadFor by mutableStateOf<VideoItem?>(null)

    val comments = CommentsState(scope)



    var accountState by mutableStateOf<VideoAccountState?>(null)
        private set
    private var originalLike = LikeStatus.None
    var inWatchLater by mutableStateOf(false)
        private set

    /** Audio tracks offered by the current video, Original first. */
    var audioOptions by mutableStateOf<List<AudioTrackOption>>(emptyList())
        private set

    /** The cue text Media3's text renderer is currently showing, or null when captions are off. */
    var captionText by mutableStateOf<String?>(null)
        private set

    private var likeOverrides by mutableStateOf<Map<String, LikeStatus>>(emptyMap())
    private var likeDeltas by mutableStateOf<Map<String, Long>>(emptyMap())
    private var dislikeDeltas by mutableStateOf<Map<String, Long>>(emptyMap())

    val currentLike: LikeStatus
        get() = current?.id?.let { likeOverrides[it] } ?: accountState?.like ?: LikeStatus.None

    var actionMessage by mutableStateOf<String?>(null)

    private var loadJob: Job? = null

    private var pendingStart: VideoItem? = null





    private val known = HashMap<String, VideoItem>()

    var queue by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    var musicQueue by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    fun open(item: VideoItem, expand: Boolean = true) {
        isMusicMode = false
        AudioOnly.set(false)
        runCatching { applyVideoTrack() }
        if (expand) expanded = true
        if (current?.id == item.id && error == null) return
        known[item.id] = item
        show(item, startPlayback = true)
    }

    fun openMusic(item: VideoItem, expand: Boolean = true) {
        openMusicQueue(listOf(item), 0, expand)
    }

    



    fun openMusicQueue(items: List<VideoItem>, index: Int, expand: Boolean = true) {
        val item = items.getOrNull(index) ?: return
        isMusicMode = true
        applyVideoTrack()
        musicQueue = items.drop(index + 1).filter { it.id != item.id }
        if (expand) expanded = true
        if (current?.id == item.id && error == null) return
        known[item.id] = item
        show(item, startPlayback = true)
    }

    fun playNextMusic(item: VideoItem) {
        val player = controller ?: return
        known[item.id] = item
        if (current == null || player.mediaItemCount == 0) { openMusic(item, expand = false); return }
        player.addMediaItem(player.currentMediaItemIndex + 1, mediaItemFor(item))
        actionMessage = "Playing next"
        continueIfEnded(player)
    }

    




    private var openedAt = 0L

    private fun show(item: VideoItem, startPlayback: Boolean) {
        endHoldSpeed()
        openedAt = android.os.SystemClock.elapsedRealtime()
        Log.i("YTXTubeTime", "open ${item.id}")
        if (startPlayback) Net.prewarmMediaConnection()
        current = item
        if (isMusicMode) RecentMusic.record(item)
        details = null
        error = null
        firstFrameRendered = false
        videoAspect = 16f / 9f
        aspectFromFrames = false
        decodedHeight = null
        playingFormat = null
        loading = true
        sheet = null
        comments.clearFor(item.id)
        accountState = null
        inWatchLater = false
        audioOptions = emptyList()
        captionText = null
        speedPanelOpen = false
        videoZoom = 1f
        portraitFullscreen = false
        if (item.localUri != null) {

            details = VideoDetails(
                item = item,
                description = "",
                likes = 0,
                dislikes = -1,
                subscribers = 0,
                related = emptyList(),
                playable = true,
                quality = null,
                qualities = emptyList(),
                aspect = null,
            )
            loading = false
            if (startPlayback) startPlayback(item)
            return
        }
        if (Account.signedIn) {
            scope.launch {
                runCatching { InnerTube.videoState(item.id) }
                    .onSuccess { if (current?.id == item.id) { accountState = it; originalLike = it.like } }
                    .onFailure { Log.w(TAG, "account state for ${item.id}", it) }
            }
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            runCatching { YouTube.details(item.id) }
                .onSuccess { d ->
                    Log.i("YTXTubeTime", "details +${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
                    details = d
                    refreshTracks()

                    if (!aspectFromFrames) d.aspect?.let { videoAspect = it }


                    comments.load(item.id)
                    known[item.id] = d.item
                    d.related.forEach { known[it.id] = it }
                    if (!d.playable) error = "No playable stream was found for this video."
                    else if (startPlayback) startPlayback(d.item)

                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val dislikes = fetchDislikeCount(item.id)
                        if (current?.id == item.id && dislikes >= 0) {
                            details = details?.copy(dislikes = dislikes)
                        }
                    }
                }
                .onFailure {
                    Log.w(TAG, "details failed for ${item.id}", it)
                    error = YouTube.friendlyError(it)

                    comments.load(item.id)
                }
            loading = false
        }
    }

    private fun mediaItemFor(item: VideoItem): MediaItem {
        val localUri = item.localUri
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.channel)
            .setArtworkUri(item.thumbnail?.toUri())
            .apply {
                if (localUri != null) {
                    setExtras(android.os.Bundle().apply { putString("local_uri", localUri) })
                }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(item.id)
            .setMediaMetadata(metadata)
            .apply {
                if (localUri != null) {


                    setRequestMetadata(
                        MediaItem.RequestMetadata.Builder().setMediaUri(localUri.toUri()).build(),
                    )
                }
            }
            .build()
    }

    private fun startPlayback(item: VideoItem) {
        val player = controller ?: run { pendingStart = item; return }

        replaceCurrent(player, item, positionMs = ResumeStore.startPosition(item.id))
        player.play()
        applyCaptionSelection()
        refreshTracks()
    }

    




    private fun replaceCurrent(player: MediaController, item: VideoItem, positionMs: Long) {
        if (player.mediaItemCount == 0) {
            player.setMediaItem(mediaItemFor(item), positionMs)
        } else {


            swapping = true
            try {
                val index = player.currentMediaItemIndex
                player.addMediaItem(index + 1, mediaItemFor(item))
                player.seekTo(index + 1, positionMs)
                player.removeMediaItem(index)
            } finally {
                swapping = false
            }
        }
        player.prepare()
    }


    fun playNext(item: VideoItem) {
        val player = controller ?: return
        known[item.id] = item
        if (current == null || player.mediaItemCount == 0) { open(item, expand = false); return }
        player.addMediaItem(player.currentMediaItemIndex + 1, mediaItemFor(item))
        actionMessage = "Playing next"
        continueIfEnded(player)
    }


    private fun continueIfEnded(player: MediaController) {
        if (player.playbackState == Player.STATE_ENDED && player.currentMediaItemIndex + 1 < player.mediaItemCount) {
            player.seekTo(player.currentMediaItemIndex + 1, 0L)
            player.play()
        }
    }

    fun addToQueue(item: VideoItem) {
        val player = controller ?: return
        known[item.id] = item
        if (current == null || player.mediaItemCount == 0) { open(item, expand = false); return }
        player.addMediaItem(mediaItemFor(item))
        actionMessage = "Added to queue"
        continueIfEnded(player)
    }


    fun playFromQueue(index: Int) {
        val player = controller ?: return
        player.seekTo(player.currentMediaItemIndex + 1 + index, 0L)
        player.play()
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        player.removeMediaItem(player.currentMediaItemIndex + 1 + index)
    }

    fun clearQueue() {
        val player = controller ?: return
        val from = player.currentMediaItemIndex + 1
        if (from < player.mediaItemCount) player.removeMediaItems(from, player.mediaItemCount)
    }

    private fun rebuildQueue() {
        val player = controller ?: return
        val from = player.currentMediaItemIndex + 1
        queue = (from until player.mediaItemCount).map { i -> itemFor(player.getMediaItemAt(i)) }
    }

    private fun itemFor(media: MediaItem): VideoItem = known[media.mediaId] ?: VideoItem(
        id = media.mediaId,
        title = media.mediaMetadata.title?.toString().orEmpty(),
        channel = media.mediaMetadata.artist?.toString().orEmpty(),
        channelUrl = null,
        thumbnail = media.mediaMetadata.artworkUri?.toString(),
        avatar = null,
        durationSec = 0,
        views = -1,
        uploaded = null,
    ).also { known[media.mediaId] = it }

    private fun autoPlayNext() {
        val curr = current ?: return
        val currId = curr.id

        if (isMusicMode && musicQueue.isNotEmpty()) {
            skipNext()
            return
        }
        val relList = details?.related.orEmpty().filter { it.id != currId }

        val next = if (isMusicMode) {
            relList.firstOrNull { isMusicTrack(it) } ?: relList.firstOrNull()
        } else {
            relList.firstOrNull { it.channel.equals(curr.channel, ignoreCase = true) } ?: relList.firstOrNull()
        }
        if (next != null) {
            actionMessage = "Up next: ${next.title}"
            if (isMusicMode) openMusic(next, expand = expanded) else open(next, expand = expanded)
        } else {
            scope.launch {
                runCatching { YouTube.details(currId) }
                    .onSuccess { d ->
                        val list = d.related.filter { it.id != currId }
                        val rel = if (isMusicMode) {
                            list.firstOrNull { isMusicTrack(it) } ?: list.firstOrNull()
                        } else {
                            list.firstOrNull { it.channel.equals(curr.channel, ignoreCase = true) } ?: list.firstOrNull()
                        }
                        if (rel != null) {
                            actionMessage = "Up next: ${rel.title}"
                            if (isMusicMode) openMusic(rel, expand = expanded) else open(rel, expand = expanded)
                        }
                    }
            }
        }
    }


    private var swapping = false

    private fun onPlayerMovedTo(media: MediaItem) {
        val player = controller ?: return
        if (swapping || media.mediaId.isBlank()) return

        if (player.currentMediaItemIndex > 0) player.removeMediaItems(0, player.currentMediaItemIndex)
        if (media.mediaId != current?.id) show(itemFor(media), startPlayback = false)
    }


    val segments: List<SponsorBlock.Segment>
        get() = current?.id?.let { SponsorBlock.forUi[it] }.orEmpty()


    val askSegment: SponsorBlock.Segment?
        get() = if (!SponsorBlock.enabled) null else segments.firstOrNull {
            it.action == SponsorBlock.Action.Button && positionMs in it.startMs until it.endMs - 500
        }

    fun skipSegment(segment: SponsorBlock.Segment) = seekTo(segment.endMs)

    fun undoSkip(skip: SponsorBlock.Skip) {
        if (skip.videoId == current?.id) seekTo(skip.segment.startMs)
        SponsorBlock.lastSkip = null
    }


    fun refreshAccountState() {
        val item = current ?: return
        accountState = null
        inWatchLater = false
        if (!Account.signedIn) return
        scope.launch {
            runCatching { InnerTube.videoState(item.id) }
                .onSuccess { if (current?.id == item.id) { accountState = it; originalLike = it.like } }
                .onFailure { Log.w(TAG, "account state for ${item.id}", it) }
        }
    }


    val displayLikes: Long
        get() {
            val base = details?.likes ?: return -1
            val delta = current?.id?.let { likeDeltas[it] } ?: 0L
            return (base + delta).coerceAtLeast(0)
        }

    val displayDislikes: Long
        get() {
            val base = details?.dislikes ?: return -1
            if (base < 0) return -1
            val delta = current?.id?.let { dislikeDeltas[it] } ?: 0L
            return (base + delta).coerceAtLeast(0)
        }

    private fun likeWeight(status: LikeStatus): Long = when (status) {
        LikeStatus.Like -> 1L
        LikeStatus.Dislike -> -1L
        LikeStatus.None -> 0L
    }

    private fun dislikeWeight(status: LikeStatus): Long = if (status == LikeStatus.Dislike) 1L else 0L

    /**
     * Tapping a thumb is optimistic for everyone, signed in or not. The visible count moves
     * immediately; the network call only decides whether the choice survives a restart.
     */
    fun setLike(target: LikeStatus) {
        val id = current?.id ?: return
        val before = currentLike
        val next = if (before == target) LikeStatus.None else target
        likeOverrides = likeOverrides + (id to next)
        likeDeltas = likeDeltas + (id to ((likeDeltas[id] ?: 0L) + likeWeight(next) - likeWeight(before)))
        dislikeDeltas = dislikeDeltas + (id to ((dislikeDeltas[id] ?: 0L) + dislikeWeight(next) - dislikeWeight(before)))
        actionMessage = when (next) {
            LikeStatus.Like -> "Liked"
            LikeStatus.Dislike -> "Not for me"
            LikeStatus.None -> "Rating removed"
        }

        if (!Account.signedIn) return
        val previousAccount = accountState
        accountState = previousAccount?.copy(like = next)
        scope.launch {
            runCatching { InnerTube.setLike(id, next) }
                .onFailure {
                    if (current?.id == id) {
                        likeOverrides = likeOverrides + (id to before)
                        likeDeltas = likeDeltas + (id to ((likeDeltas[id] ?: 0L) - likeWeight(next) + likeWeight(before)))
                        dislikeDeltas = dislikeDeltas + (id to ((dislikeDeltas[id] ?: 0L) - dislikeWeight(next) + dislikeWeight(before)))
                        accountState = previousAccount
                        actionMessage = "Couldn't update your like."
                    }
                }
        }
    }

    fun toggleLike() = setLike(LikeStatus.Like)

    fun toggleDislike() = setLike(LikeStatus.Dislike)

    fun toggleSubscribe() {
        val before = accountState ?: return
        val channelId = before.channelId
            ?: details?.item?.channelUrl?.substringAfter("/channel/", "")?.takeIf { it.startsWith("UC") }
            ?: return
        val subscribe = before.subscribed != true
        accountState = before.copy(subscribed = subscribe, channelId = channelId)
        scope.launch {
            runCatching { InnerTube.setSubscribed(channelId, subscribe) }
                .onFailure { accountState = before; actionMessage = "Couldn't update your subscription." }
        }
    }

    fun toggleWatchLater() {
        val id = current?.id ?: return
        val add = !inWatchLater
        inWatchLater = add
        scope.launch {
            runCatching { InnerTube.setInWatchLater(id, add) }
                .onSuccess { actionMessage = if (add) "Saved to Watch later" else "Removed from Watch later" }
                .onFailure { inWatchLater = !add; actionMessage = "Couldn't update Watch later."; Log.w(TAG, "watch later $id", it) }
        }
    }


    fun saveToWatchLater(video: VideoItem) {
        scope.launch {
            runCatching { InnerTube.setInWatchLater(video.id, true) }
                .onSuccess {
                    if (current?.id == video.id) inWatchLater = true
                    actionMessage = "Saved to Watch later"
                }
                .onFailure { actionMessage = "Couldn't save to Watch later."; Log.w(TAG, "watch later ${video.id}", it) }
        }
    }

    



    fun setQuality(height: Int?) {
        QualityPreference.set(height)
        val player = controller ?: return
        val item = details?.item ?: current ?: return
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        replaceCurrent(player, item, position)
        player.playWhenReady = playWhenReady
        val info = StreamCache.get(item.id) ?: return
        details = details?.copy(quality = StreamSelection.pick(info).playedHeight?.let { "${it}p" })
    }

    /** Refresh the audio-track list from the resolved stream info. */
    fun refreshTracks() {
        val id = current?.id ?: return
        val cached = StreamCache.get(id)
        if (cached != null) {
            syncAudioPreference(cached)
            audioOptions = StreamSelection.audioOptions(cached)
            applyCaptionSelection()
            return
        }
        scope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { StreamCache.getOrLoad(id) }.getOrNull()
            } ?: return@launch
            if (current?.id != id) return@launch
            syncAudioPreference(info)
            audioOptions = StreamSelection.audioOptions(info)
            applyCaptionSelection()
        }
    }

    /** A saved language that this video does not offer falls back to Original, not silence. */
    private fun syncAudioPreference(info: org.schabi.newpipe.extractor.stream.StreamInfo) {
        val wanted = AudioTrackPreference.key ?: return
        val offered = StreamSelection.audioTracks(info)
        if (offered.none { it.key == wanted || wanted.startsWith(it.key) }) AudioTrackPreference.reset()
    }

    /**
     * Swap the audio rendition without losing the place. The service re-reads the persisted
     * preference when it builds the next media source.
     */
    fun setAudioTrack(option: AudioTrackOption?) {
        AudioTrackPreference.set(if (option == null || option.isOriginal) null else option.key, option?.label ?: "Original")
        val player = controller ?: return
        val item = details?.item ?: current ?: return
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        replaceCurrent(player, item, position)
        player.playWhenReady = playWhenReady
        actionMessage = "Audio: ${option?.label ?: "Original"}"
    }

    fun setCaptions(on: Boolean) {
        CaptionPreference.set(on)
        if (!on) captionText = null
        applyCaptionSelection()
        actionMessage = if (on) "Captions on" else "Captions off"
    }

    /**
     * Media3 owns caption timing and parsing. The subtitle tracks are already merged into the
     * media source, so turning captions on is only a track-selection change.
     */
    private var captionSignature: String? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyCaptionSelection() {
        val player = controller ?: return
        val on = CaptionPreference.enabled
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val wanted = CaptionPreference.languageTag
        val group = groups.firstOrNull { g ->
            (0 until g.length).any { g.getTrackFormat(it).language?.startsWith(wanted, ignoreCase = true) == true }
        } ?: groups.firstOrNull()
        val signature = "$on|$wanted|${groups.size}|${group?.let { groups.indexOf(it) }}"
        if (signature == captionSignature) return
        captionSignature = signature
        runCatching {
            var params = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
                .setPreferredTextLanguage(if (on) wanted else null)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            if (on && group != null) {
                params = params.setOverrideForType(
                    androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, 0),
                )
            }
            player.trackSelectionParameters = params.build()
        }.onFailure { Log.w(TAG, "caption track selection", it) }
    }


    fun retry() = current?.let { item -> StreamCache.invalidate(item.id); current = null; open(item) }

    fun togglePlay() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }


    fun seekBy(deltaMs: Long): Long {
        val player = controller ?: return 0L
        val from = player.currentPosition
        seekTo(from + deltaMs)
        return positionMs - from
    }

    fun seekTo(ms: Long) {
        val player = controller ?: return
        val max = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = ms.coerceIn(0L, max)
        positionMs = target
        player.seekTo(target)
    }

    fun beginScrubDragging() {
        if (scrubDragging) return
        val player = controller
        resumePlayAfterScrub = player?.isPlaying == true
        if (resumePlayAfterScrub) player?.pause()
        scrubDragging = true
    }


    fun endScrubDragging(seekMs: Long? = null) {
        if (!scrubDragging) return
        val player = controller
        val resume = resumePlayAfterScrub
        resumePlayAfterScrub = false
        scrubDragging = false
        scrubPreviewMs = null
        if (seekMs != null) seekTo(seekMs)
        if (resume) player?.play()
    }


    fun beginHoldSpeed(): Boolean {
        val player = controller ?: return false
        if (scrubDragging || speedBeforeHold != null) return false
        if (!player.isPlaying) return false
        speedBeforeHold = player.playbackParameters.speed
        holdSpeedActive = true
        previewSpeed(2f)
        return true
    }

    fun endHoldSpeed() {
        val restore = speedBeforeHold ?: return
        speedBeforeHold = null
        holdSpeedActive = false
        previewSpeed(restore)
    }


    fun previewSpeed(value: Float) {
        val player = controller ?: return
        val s = PlaybackSpeed.snap(value)
        if (player.playbackParameters.speed != s) player.setPlaybackSpeed(s)
        speed = s
    }

    




    fun commitSpeed(value: Float) {
        previewSpeed(value)
        val s = PlaybackSpeed.snap(value)
        if (!PlaybackSpeed.remember) return
        if (PlaybackSpeed.isNormal(s) && !PlaybackSpeed.isNormal(PlaybackSpeed.saved)) PlaybackSpeed.setRemember(false, 1f)
        else PlaybackSpeed.save(s)
    }

    fun toggleRememberSpeed() = PlaybackSpeed.setRemember(!PlaybackSpeed.remember, speed)


    fun setAudioOnly(on: Boolean) {
        AudioOnly.set(on)
        applyVideoTrack()
    }

    




    fun setBackground(background: Boolean) {
        if (wantBackground == background) return
        wantBackground = background
        if (isMusicMode) return
        applyVideoTrack()
    }

    





    private var wantBackground = false


    private fun applyVideoTrack() {
        runCatching {
            // Music keeps its video track alive in the background. Toggling it off and on
            // made the decoder re-init, which is the audible dip heard when reopening the
            // app during background playback.
            if (isMusicMode) return
            val off = AudioOnly.enabled || wantBackground
            val player = controller ?: return
            if (player.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO) == off) return
            Log.i(TAG, if (off) "video off (${if (AudioOnly.enabled) "audio only" else "background"})" else "video on")
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, off)
                .build()
        }
    }

    fun close() {
        loadJob?.cancel()
        controller?.run { stop(); clearMediaItems() }
        queue = emptyList()
        expanded = false
        fullscreen = false
        portraitFullscreen = false
        sheet = null
        current = null
        details = null
        error = null
        loading = false
    }

    




    internal fun onConnected() {
        syncFromPlayer()
        applyVideoTrack()
        controller?.videoSize?.let(::takeVideoSize)
        pendingStart?.let { pendingStart = null; startPlayback(it); return }
        val player = controller ?: return
        rebuildQueue()
        val playing = player.currentMediaItem ?: return
        if (current != null || playing.mediaId.isBlank()) return
        val meta = playing.mediaMetadata
        val item = VideoItem(
            id = playing.mediaId,
            title = meta.title?.toString().orEmpty(),
            channel = meta.artist?.toString().orEmpty(),
            channelUrl = null,
            thumbnail = meta.artworkUri?.toString(),
            avatar = null,
            durationSec = player.duration.takeIf { it > 0 }?.div(1000) ?: 0L,
            views = -1,
            uploaded = null,
        )
        current = item
        loadJob = scope.launch {
            runCatching { YouTube.details(item.id) }.onSuccess { details = it; current = it.item; refreshTracks() }
        }
    }

    internal fun syncFromPlayer() {
        val player = controller ?: return
        isPlaying = player.isPlaying

        isBuffering = player.playbackState == Player.STATE_BUFFERING || StreamRecovery.recovering == current?.id
        positionMs = player.currentPosition.coerceAtLeast(0L)
        bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
        durationMs = player.duration.takeIf { it > 0 } ?: ((current?.durationSec ?: 0L) * 1000L)
        speed = player.playbackParameters.speed
        loopEnabled = player.repeatMode == Player.REPEAT_MODE_ONE
    }

    internal val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer()
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) rebuildQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val p = controller ?: return
                if (p.repeatMode == Player.REPEAT_MODE_ONE) return
                if (p.currentMediaItemIndex + 1 < p.mediaItemCount) {
                    p.seekTo(p.currentMediaItemIndex + 1, 0L)
                    p.play()
                } else if (autoPlayEnabled) {
                    autoPlayNext()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) onPlayerMovedTo(mediaItem)
            applyCaptionSelection()
        }

        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            val text = cueGroup.cues
                .mapNotNull { cue -> cue.text?.toString()?.takeIf { it.isNotBlank() } }
                .joinToString("\n")
            captionText = text.ifBlank { null }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) = takeVideoSize(videoSize)

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            applyCaptionSelection()
            val vs = controller?.videoSize ?: return
            if (vs.width <= 0 || vs.height <= 0) return
            val w = (vs.width * vs.pixelWidthHeightRatio).roundToInt()
            describeFormat(w, vs.height)
        }

        override fun onRenderedFirstFrame() {
            Log.i("YTXTubeTime", "first frame +${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
            firstFrameRendered = true
        }

        override fun onPlayerError(e: PlaybackException) {
            val status = (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
            Log.w(TAG, "playback error ${e.errorCodeName}${status?.let { " (HTTP $it)" } ?: ""} for ${current?.id}", e)

            if (e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && StreamRecovery.recovering == current?.id) return
            error = when (e.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Lost the connection. Check your network and try again."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "YouTube refused the stream. Try again."
                else -> "This video couldn't be played."
            }
        }
    }

    private fun takeVideoSize(videoSize: VideoSize) {
        if (videoSize.width <= 0 || videoSize.height <= 0) return
        videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        aspectFromFrames = true

        val w = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt()
        describeFormat(w, videoSize.height)
    }


    private fun describeFormat(width: Int, height: Int) {
        val tier = qualityTier(width, height)
        if (decodedHeight != tier) {
            Log.i("YTXTubeTime", "now ${tier}p (${width}×${height}) at +${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
        }
        decodedHeight = tier
        val formats = controller?.currentTracks?.groups.orEmpty()
            .filter { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
            .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it) } }
        val format = formats.firstOrNull { it.height == height }
        if (format == null) {
            playingFormat = "${tier}p"
            return
        }
        run {
            val codec = when {
                format.codecs?.startsWith("avc") == true -> "H.264"
                format.codecs?.startsWith("vp9") == true || format.codecs?.startsWith("vp09") == true -> "VP9"
                format.codecs?.startsWith("av01") == true -> "AV1"
                else -> format.codecs ?: format.sampleMimeType ?: "?"
            }
            val auto = if (formats.size > 1) "Auto" else null
            val labelTier = qualityTier(format.width, format.height)
            playingFormat = listOfNotNull(auto, "${labelTier}p", codec, format.id?.substringAfterLast(":")?.let { "itag $it" }).joinToString(" · ")
        }
    }
}

@Composable
fun rememberPlayerState(): PlayerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { PlayerState(scope) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { controller ->
                state.controller = controller
                controller.addListener(state.listener)
                state.onConnected()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            state.controller?.removeListener(state.listener)
            MediaController.releaseFuture(future)
            state.controller = null
        }
    }

    LaunchedEffect(state.isPlaying) {
        while (isActive && state.isPlaying) {
            state.syncFromPlayer()
            delay(250)
        }
    }
    return state
}

enum class PlayerSheet { Quality, Comments, Queue, Settings, Speed, Audio }

private const val TAG = "YTXTube"
