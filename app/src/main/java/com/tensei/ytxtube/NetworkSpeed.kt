package com.tensei.ytxtube

import android.content.Context
import android.net.ConnectivityManager

/**
 * Small, dependency-free bandwidth estimate for the "Auto" quality mode.
 *
 * [ConnectivityManager] exposes the downstream link speed of the active network
 * (Wi-Fi, cellular, Ethernet). That is a good enough ceiling for choosing the
 * first rendition: slow links start low instead of stalling on a 4K stream,
 * while fast links still get the highest available quality.
 */
object NetworkSpeed {
    private var manager: ConnectivityManager? = null

    @Volatile
    private var cachedKbps: Int = 0

    @Volatile
    private var cachedAt: Long = 0L

    fun init(context: Context) {
        manager = context.getSystemService(ConnectivityManager::class.java)
        refresh(force = true)
    }

    fun refresh(force: Boolean = false): Int {
        val now = System.currentTimeMillis()
        if (!force && now - cachedAt < 15_000L) return cachedKbps

        val cm = manager ?: return cachedKbps
        val kbps = runCatching {
            val network = cm.activeNetwork ?: return@runCatching 0
            val caps = cm.getNetworkCapabilities(network) ?: return@runCatching 0
            caps.linkDownstreamBandwidthKbps
        }.getOrDefault(0)

        if (kbps > 0) cachedKbps = kbps
        cachedAt = now
        return cachedKbps
    }

    /**
     * Downstream bitrate the player may spend while leaving room for audio, caption
     * requests and normal network jitter. Measured throughput wins; the link speed is
     * only used as a ceiling (and as a fallback before the player has sampled anything).
     */
    fun sustainableKbps(): Int {
        val link = refresh()
        val measured = PlaybackBandwidth.measuredKbps()
        val raw = when {
            measured > 0 && link > 0 -> minOf(measured, link)
            measured > 0 -> measured
            link > 0 -> minOf(link, LINK_ONLY_CAP_KBPS)
            else -> UNKNOWN_KBPS
        }
        return (raw * HEADROOM).toInt().coerceAtLeast(MIN_BUDGET_KBPS)
    }

    /**
     * Highest rendition whose typical bitrate fits the sustainable budget. When even the
     * lowest rung does not fit, the caller should fall back to the lowest available
     * rendition rather than the highest: starting on a stream the connection cannot feed
     * is exactly the buffering this estimate exists to prevent.
     */
    fun sustainableHeight(): Int {
        val budget = sustainableKbps()
        return TIER_KBPS.entries.lastOrNull { it.value <= budget }?.key ?: 144
    }

    /** Typical YouTube bitrate for a rendition, used when the extractor exposes none. */
    fun typicalKbps(height: Int): Int {
        val nearest = TIER_KBPS.keys.minByOrNull { kotlin.math.abs(it - height) } ?: 720
        return TIER_KBPS[nearest] ?: 3_000
    }

    /** The link-layer ceiling, used to bound adaptive ladders without capping them by a guess. */
    fun linkHeight(): Int {
        val kbps = refresh()
        return when {
            kbps <= 0 -> 1080
            kbps < 1_500 -> 360
            kbps < 3_000 -> 480
            kbps < 6_000 -> 720
            kbps < 12_000 -> 1080
            kbps < 24_000 -> 1440
            else -> 2160
        }
    }

    fun recommendedHeight(): Int = sustainableHeight()

    fun labelFor(height: Int): String = when {
        height >= 2160 -> "4K"
        height >= 1440 -> "QHD"
        height >= 1080 -> "Full HD"
        height >= 720 -> "HD"
        height <= 240 -> "Data saver"
        else -> "${height}p"
    }

    private const val HEADROOM = 0.65f
    private const val UNKNOWN_KBPS = 3_000
    private const val LINK_ONLY_CAP_KBPS = 20_000
    private const val MIN_BUDGET_KBPS = 300

    private val TIER_KBPS = linkedMapOf(
        144 to 120,
        240 to 300,
        360 to 700,
        480 to 1_400,
        720 to 2_800,
        1080 to 5_000,
        1440 to 10_000,
        2160 to 20_000,
        4320 to 45_000,
    )
}
