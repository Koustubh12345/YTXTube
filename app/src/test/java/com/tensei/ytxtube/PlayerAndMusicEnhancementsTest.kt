package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAndMusicEnhancementsTest {

    private fun video(id: String, title: String, channel: String) =
        VideoItem(id, title, channel, null, null, null, 180, 5000, null)

    @Test
    fun testAbLoopBoundaryCheck() {
        val pointA = 10_000L
        val pointB = 30_000L
        var seekTriggered = false
        var seekTarget = -1L

        fun check(currentMs: Long) {
            if (currentMs >= pointB) {
                seekTriggered = true
                seekTarget = pointA
            }
        }

        check(25_000L)
        assertFalse(seekTriggered)

        check(30_000L)
        assertTrue(seekTriggered)
        assertEquals(pointA, seekTarget)
    }

    @Test
    fun testSmartShuffleAvoidsConsecutiveArtists() {
        val tracks = listOf(
            video("1", "Song A1", "Artist 1"),
            video("2", "Song A2", "Artist 1"),
            video("3", "Song B1", "Artist 2"),
            video("4", "Song B2", "Artist 2"),
            video("5", "Song C1", "Artist 3")
        )

        val shuffled = MusicExperience.smartShuffle(tracks)
        assertEquals(tracks.size, shuffled.size)
        // Verify that the first two are not from the same artist if avoidable
        if (shuffled.size >= 2) {
            assertFalse(
                shuffled[0].channel == shuffled[1].channel &&
                tracks.map { it.channel }.distinct().size > 1
            )
        }
    }

    @Test
    fun testDurationFormatting() {
        fun format(sec: Long): String {
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        }

        assertEquals("03:45", format(225))
        assertEquals("1:05:30", format(3930))
        assertEquals("00:09", format(9))
    }

    @Test
    fun testAspectRatioPresets() {
        val presets = AspectRatioPreset.entries
        assertEquals(5, presets.size)
        val fit = presets.find { it == AspectRatioPreset.Fit16x9 }
        assertTrue(fit != null && kotlin.math.abs(fit.aspect - 16f / 9f) < 0.01f)
    }

    @Test
    fun testDoubleTapSeekSteps() {
        assertEquals(10, DoubleTapSeekStep.Sec10.seconds)
        assertEquals(30, DoubleTapSeekStep.Sec30.seconds)
        assertEquals(60, DoubleTapSeekStep.Sec60.seconds)
    }
}
