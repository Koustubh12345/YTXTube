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
     * Highest sensible rendition for the current connection. These thresholds
     * leave headroom for YouTube's progressive/DASH bitrates, which are higher
     * than the nominal resolution suggests.
     */
    fun recommendedHeight(): Int {
        val kbps = refresh()
        return when {
            kbps <= 0 -> 1080      // Unknown network: use a safe, common ceiling.
            kbps < 1_500 -> 360
            kbps < 3_000 -> 480
            kbps < 6_000 -> 720
            kbps < 12_000 -> 1080
            kbps < 24_000 -> 1440
            else -> 2160
        }
    }

    fun labelFor(height: Int): String = when {
        height >= 2160 -> "4K"
        height >= 1440 -> "QHD"
        height >= 1080 -> "Full HD"
        height >= 720 -> "HD"
        height <= 240 -> "Data saver"
        else -> "${height}p"
    }
}
