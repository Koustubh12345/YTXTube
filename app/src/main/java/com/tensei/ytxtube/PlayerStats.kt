@file:OptIn(UnstableApi::class)

package com.tensei.ytxtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * "Stats for nerds" for the live player. Every row comes from something the player actually
 * reports; a row with no data is left out rather than shown as zero or "unknown".
 *
 * Decoder names and the bandwidth estimate arrive through an analytics listener that follows
 * [PlayerBridge.exo]. Everything else is read from the player when the overlay refreshes.
 */
object PlayerStats {
    var visible by mutableStateOf(false)
        private set

    fun toggle() {
        visible = !visible
    }

    fun set(on: Boolean) {
        visible = on
    }

    @Volatile private var bandwidthBps = 0L
    @Volatile private var videoDecoder: String? = null
    @Volatile private var audioDecoder: String? = null

    private val listener = object : AnalyticsListener {
        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            bandwidthBps = bitrateEstimate
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            videoDecoder = decoderName
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            audioDecoder = decoderName
        }
    }

    /** Called by [PlayerBridge] when the live player is created. */
    fun attach(player: ExoPlayer?) {
        player?.addAnalyticsListener(listener)
    }

    /** Called by [PlayerBridge] when the live player is replaced or released. */
    fun detach(player: ExoPlayer?) {
        player?.removeAnalyticsListener(listener)
        bandwidthBps = 0L
        videoDecoder = null
        audioDecoder = null
    }

    /** Label/value rows for the current moment. Call on the main thread. */
    fun read(player: ExoPlayer): List<Pair<String, String>> {
        val rows = ArrayList<Pair<String, String>>()

        val v = player.videoFormat
        if (v != null && v.width > 0 && v.height > 0) {
            rows += "Resolution" to StatsFormat.resolution(v.width, v.height, v.frameRate)
        }
        (v?.codecs ?: v?.sampleMimeType)?.let { rows += "Video codec" to it }
        val videoBitrate = v?.let { if (it.averageBitrate > 0) it.averageBitrate else it.bitrate }
        if (videoBitrate != null && videoBitrate > 0) rows += "Video bitrate" to StatsFormat.kbps(videoBitrate)
        videoDecoder?.let { rows += "Video decoder" to it }

        val a = player.audioFormat
        (a?.codecs ?: a?.sampleMimeType)?.let { rows += "Audio codec" to it }
        val audioBitrate = a?.let { if (it.averageBitrate > 0) it.averageBitrate else it.bitrate }
        if (audioBitrate != null && audioBitrate > 0) rows += "Audio bitrate" to StatsFormat.kbps(audioBitrate)
        if (a != null && a.sampleRate > 0) rows += "Audio sample rate" to "${a.sampleRate} Hz"
        audioDecoder?.let { rows += "Audio decoder" to it }

        player.videoDecoderCounters?.let { counters ->
            counters.ensureUpdated()
            val dropped = counters.droppedBufferCount
            rows += "Dropped frames" to "$dropped of ${counters.renderedOutputBufferCount + dropped}"
        }

        rows += "Buffer health" to StatsFormat.seconds(player.totalBufferedDuration)
        if (bandwidthBps > 0) rows += "Bandwidth estimate" to StatsFormat.mbps(bandwidthBps)

        val speed = player.playbackParameters.speed
        if (speed != 1f) rows += "Speed" to "${speed}x"

        rows += "State" to when (player.playbackState) {
            Player.STATE_IDLE -> "Idle"
            Player.STATE_BUFFERING -> "Buffering"
            Player.STATE_READY -> if (player.isPlaying) "Playing" else "Paused"
            Player.STATE_ENDED -> "Ended"
            else -> "Unknown"
        }
        val duration = player.duration
        if (duration > 0) {
            rows += "Position" to "${formatDuration(player.currentPosition)} / ${formatDuration(duration)}"
        }
        return rows
    }
}

/** Number formatting for the overlay, kept free of Android so it can be unit tested. */
internal object StatsFormat {
    fun kbps(bitsPerSecond: Int): String = "${bitsPerSecond / 1000} kbps"

    fun mbps(bitsPerSecond: Long): String {
        val tenths = bitsPerSecond / 100_000L
        return "${tenths / 10}.${tenths % 10} Mbps"
    }

    fun seconds(ms: Long): String {
        val tenths = ms.coerceAtLeast(0L) / 100L
        return "${tenths / 10}.${tenths % 10} s"
    }

    /** "1920\u00d71080 @ 30 fps"; the frame rate is left out when the stream does not state one. */
    fun resolution(width: Int, height: Int, frameRate: Float): String {
        val size = "${width}\u00d7${height}"
        return if (frameRate > 0f) "$size @ ${frameRate.roundToInt()} fps" else size
    }
}

/** Draws the stats box while [PlayerStats.visible] is on; draws nothing otherwise. */
@Composable
fun StatsOverlay(modifier: Modifier = Modifier) {
    if (!PlayerStats.visible) return
    val rows by produceState(initialValue = emptyList<Pair<String, String>>()) {
        while (true) {
            value = PlayerBridge.exo?.let(PlayerStats::read).orEmpty()
            delay(1_000L)
        }
    }
    if (rows.isEmpty()) return
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .widthIn(max = 300.dp),
    ) {
        rows.forEach { (label, value) ->
            Row {
                Text(
                    "$label  ",
                    color = Color.White.copy(alpha = 0.65f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
                Text(
                    value,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
