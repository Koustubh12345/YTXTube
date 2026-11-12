package com.tensei.ytxtube

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Pure retry rules, kept free of Android types so they are easy to unit test.
 */
object RecoveryPolicy {
    /** Delay before retry number [attempt] (1-based): 1 s, 2 s, 4 s, 8 s. */
    private val backoffMs = longArrayOf(1_000, 2_000, 4_000, 8_000)

    val maxAttempts: Int get() = backoffMs.size

    fun delayMs(attempt: Int): Long = backoffMs[(attempt - 1).coerceIn(0, backoffMs.lastIndex)]

    /**
     * Where to resume after a failure. Playback that failed right at the end is moved slightly
     * back so the retry has something left to play; very short videos clamp to the start.
     * Returns the same position when no adjustment is needed.
     */
    fun resumePosition(positionMs: Long, durationMs: Long, nearEndMs: Long = 500L): Long {
        val pos = positionMs.coerceAtLeast(0L)
        if (durationMs <= 0L || durationMs == C.TIME_UNSET) return pos
        return if (pos >= durationMs - nearEndMs) (durationMs - nearEndMs).coerceAtLeast(0L) else pos
    }
}

/**
 * Retries playback after temporary network failures.
 *
 * It deliberately ignores HTTP status errors (expired or refused stream URLs). Those need a fresh
 * extraction and are already handled by the stream recoverer in PlaybackService, so the two
 * never fight over the same error.
 *
 * ExoPlayer keeps the current position, selected tracks and playWhenReady across an error, so a
 * plain prepare() resumes where playback stopped. Retries use exponential backoff, wait while the
 * device is offline, and stop after [RecoveryPolicy.maxAttempts] failures that make no progress,
 * at which point a message is published through [StreamRecovery.failed].
 */
internal class NetworkRecoverer(private val player: ExoPlayer) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private var attempts = 0
    private var offlinePolls = 0
    private var failPositionMs = 0L
    private var failedId: String? = null
    private var pendingId: String? = null
    private var pendingLive = false
    private var ownsRecovering = false
    private val retryTick = Runnable { retry() }

    override fun onPlayerError(error: PlaybackException) {
        val item = player.currentMediaItem ?: return
        if (item.localConfiguration?.uri?.scheme == "file") return
        val live = error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
        val retryable = live || when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_TIMEOUT -> true
            else -> false
        }
        if (!retryable) return

        val id = item.mediaId
        val position = player.currentPosition
        // Playing for a while since the last failure means that failure was temporary.
        if (id != failedId || position > failPositionMs + PROGRESS_MS) attempts = 0
        failedId = id
        failPositionMs = position
        if (attempts >= RecoveryPolicy.maxAttempts) {
            giveUp(id)
            return
        }
        attempts++
        Log.i("YTXTube", "network error ${error.errorCodeName} for $id; retry $attempts of ${RecoveryPolicy.maxAttempts} from ${position / 1000}s")
        StreamRecovery.failed = null
        StreamRecovery.recovering = id
        ownsRecovering = true
        pendingId = id
        pendingLive = live
        main.removeCallbacks(retryTick)
        main.postDelayed(retryTick, RecoveryPolicy.delayMs(attempts))
    }

    private fun retry() {
        val id = pendingId ?: return
        // The user moved on, or something else already recovered the player.
        if (player.currentMediaItem?.mediaId != id || player.playerError == null) {
            clearRecovering()
            return
        }
        if (!isOnline()) {
            if (++offlinePolls > MAX_OFFLINE_POLLS) {
                giveUp(id)
            } else {
                main.postDelayed(retryTick, OFFLINE_POLL_MS)
            }
            return
        }
        offlinePolls = 0
        if (pendingLive) {
            player.seekToDefaultPosition()
        } else {
            val position = player.currentPosition
            val resume = RecoveryPolicy.resumePosition(position, player.duration)
            if (resume != position) player.seekTo(resume)
        }
        player.prepare()
    }

    private fun giveUp(id: String) {
        Log.w("YTXTube", "network recovery for $id gave up after $attempts attempts")
        main.removeCallbacks(retryTick)
        pendingId = null
        offlinePolls = 0
        StreamRecovery.recovering = null
        ownsRecovering = false
        StreamRecovery.failed = id to "Connection problem. Check your network and try again."
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            pendingId = null
            offlinePolls = 0
            clearRecovering()
        }
    }

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        main.removeCallbacks(retryTick)
        pendingId = null
        attempts = 0
        offlinePolls = 0
        failedId = null
        clearRecovering()
    }

    fun release() {
        main.removeCallbacks(retryTick)
        pendingId = null
        clearRecovering()
    }

    private fun clearRecovering() {
        if (ownsRecovering) {
            StreamRecovery.recovering = null
            ownsRecovering = false
        }
    }

    private fun isOnline(): Boolean {
        val cm = YTXTubeApplication.appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val PROGRESS_MS = 5_000L
        const val OFFLINE_POLL_MS = 2_000L
        const val MAX_OFFLINE_POLLS = 30 // about a minute offline before giving up
    }
}

/** Owns the single [NetworkRecoverer] and follows [PlayerBridge.exo]. Main thread only. */
object PlaybackRecovery {
    private var active: NetworkRecoverer? = null

    fun attach(player: ExoPlayer?) {
        if (player == null) return
        active?.release()
        active = NetworkRecoverer(player).also(player::addListener)
    }

    fun detach(player: ExoPlayer?) {
        val current = active ?: return
        player?.removeListener(current)
        current.release()
        active = null
    }
}
