package com.tensei.ytxtube

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter

/**
 * One bandwidth meter shared by the main player and the Shorts player.
 *
 * [DefaultBandwidthMeter] learns the real download throughput from the bytes ExoPlayer
 * actually fetches, which is the only trustworthy signal for "what can this connection
 * sustain right now". The [ConnectivityManager] link speed is just a link-layer ceiling:
 * on Wi-Fi it often reports hundreds of megabits while the internet connection behind it
 * is far slower.
 *
 * The initial estimate is deliberately conservative. ExoPlayer's adaptive selector starts
 * from it and climbs as measurements arrive, so a fast connection reaches high quality
 * within a couple of seconds while a slow one never starts on a rendition it cannot feed.
 */
@OptIn(UnstableApi::class)
object PlaybackBandwidth {
    private const val INITIAL_BITRATE_BPS = 2_500_000L

    @Volatile
    private var meter: DefaultBandwidthMeter? = null

    fun init(context: Context) {
        get(context.applicationContext)
    }

    fun get(): BandwidthMeter = get(YTXTubeApplication.appContext)

    private fun get(context: Context): DefaultBandwidthMeter {
        meter?.let { return it }
        synchronized(this) {
            meter?.let { return it }
            val built = DefaultBandwidthMeter.Builder(context)
                .setInitialBitrateEstimate(INITIAL_BITRATE_BPS)
                .setResetOnNetworkTypeChange(true)
                .build()
            meter = built
            return built
        }
    }

    /** Current downstream estimate in kbps, including the initial estimate before any sample. */
    fun measuredKbps(): Int {
        val bps = meter?.bitrateEstimate ?: 0L
        return (bps / 1000L).toInt().coerceAtLeast(0)
    }
}
