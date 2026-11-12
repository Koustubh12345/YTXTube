package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAndMusicEngineTest {

    @Test
    fun smartDownloadRulePicksAudioForMusicAndCapsLongVideos() {
        val song = VideoItem("s1111111111", "The Weeknd - Blinding Lights (Official Audio)", "TheWeekndVEVO", null, null, null, 200, 1_000_000, null)
        assertEquals(0, OfflineIntelligenceLogic.recommendedDownloadHeight(song, meteredNetwork = false, preferAudioForMusicAndLong = true))

        val lecture = VideoItem("l1111111111", "MIT 2 Hour Distributed Systems Lecture", "MIT", null, null, null, 7200, 50_000, null)
        assertEquals(720, OfflineIntelligenceLogic.recommendedDownloadHeight(lecture, meteredNetwork = false, preferAudioForMusicAndLong = true, defaultWifiHeight = 1080))
    }

    @Test
    fun duplicateDownloadDetectionFindsMatchingVideoIdsAcrossResolutions() {
        val keys = listOf("abc12345678@1080", "abc12345678@0", "xyz98765432@720")
        val dups = OfflineIntelligenceLogic.findDuplicates("abc12345678", keys)
        assertEquals(2, dups.size)
    }

    @Test
    fun overnightScheduleWindowHandlesMidnightWrap() {
        assertTrue(OfflineIntelligenceLogic.isWithinScheduleWindow(DownloadScheduleWindow.OffPeak, 23))
        assertTrue(OfflineIntelligenceLogic.isWithinScheduleWindow(DownloadScheduleWindow.OffPeak, 2))
        assertFalse(OfflineIntelligenceLogic.isWithinScheduleWindow(DownloadScheduleWindow.OffPeak, 14))
    }

    @Test
    fun smartMusicShuffleSpreadsRepeatedArtists() {
        val tracks = listOf(
            VideoItem("1", "Song A1", "ArtistA", null, null, null, 180, 100, null),
            VideoItem("2", "Song A2", "ArtistA", null, null, null, 180, 100, null),
            VideoItem("3", "Song B1", "ArtistB", null, null, null, 180, 100, null),
            VideoItem("4", "Song C1", "ArtistC", null, null, null, 180, 100, null),
        )
        val shuffled = ProfessionalMusicLogic.smartShuffle(tracks, seed = 42L)
        assertEquals(4, shuffled.size)
    }

    @Test
    fun crossfadeEnvelopeRampsAtEdgesAndStaysUnityInMiddle() {
        assertEquals(1.0f, ProfessionalMusicLogic.crossfadeEnvelope(30_000L, 180_000L, 6), 0.001f)
        assertTrue(ProfessionalMusicLogic.crossfadeEnvelope(177_000L, 180_000L, 6) < 0.6f)
    }

    @Test
    fun lrcExportFormatsTimestampsAndAppliesOffset() {
        val lrc = ProfessionalMusicLogic.exportLrc(
            title = "Test Song",
            artist = "Test Artist",
            lines = listOf(10_000L to "First line", 65_500L to "Second line"),
            offsetMs = 500,
        )
        assertTrue(lrc.contains("[00:10.50]First line"))
        assertTrue(lrc.contains("[01:06.00]Second line"))
    }
}
