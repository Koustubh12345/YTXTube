package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsFormatTest {
    @Test
    fun bitrates() {
        assertEquals("128 kbps", StatsFormat.kbps(128_000))
        assertEquals("12.3 Mbps", StatsFormat.mbps(12_300_000))
        assertEquals("0.5 Mbps", StatsFormat.mbps(500_000))
    }

    @Test
    fun bufferSeconds() {
        assertEquals("12.3 s", StatsFormat.seconds(12_345))
        assertEquals("0.0 s", StatsFormat.seconds(0))
        assertEquals("0.0 s", StatsFormat.seconds(-50))
    }

    @Test
    fun resolutionWithAndWithoutFrameRate() {
        assertEquals("1920\u00d71080 @ 30 fps", StatsFormat.resolution(1920, 1080, 29.97f))
        assertEquals("1280\u00d7720", StatsFormat.resolution(1280, 720, -1f))
    }
}
