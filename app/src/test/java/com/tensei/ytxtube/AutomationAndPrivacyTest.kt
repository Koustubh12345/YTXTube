package com.tensei.ytxtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationAndPrivacyTest {

    @Test
    fun selectsAutoPresetBasedOnVideoTopicAndLength() {
        val tutorial = VideoItem("t1", "Complete Kotlin Compiler Course Tutorial", "JetBrains", null, null, null, 1800, 10_000, null)
        assertEquals("lecture", AutomationLogic.selectAutoPresetForVideo(tutorial))

        val hdrDemo = VideoItem("h1", "4K HDR OLED Demo Reel", "LG", null, null, null, 240, 100_000, null)
        assertEquals("cinema", AutomationLogic.selectAutoPresetForVideo(hdrDemo))
    }

    @Test
    fun resolvesTimeBasedThemeAcrossHours() {
        assertEquals("light", AutomationLogic.resolveTimeBasedTheme(10))
        assertEquals("dark", AutomationLogic.resolveTimeBasedTheme(20))
        assertEquals("amoled", AutomationLogic.resolveTimeBasedTheme(23))
        assertEquals("amoled", AutomationLogic.resolveTimeBasedTheme(3))
    }

    @Test
    fun triggersBatteryAndVolumeAutomationAccurately() {
        assertTrue(AutomationLogic.shouldActivateBatteryProfile(15, isCharging = false, thresholdPercent = 20))
        assertFalse(AutomationLogic.shouldActivateBatteryProfile(15, isCharging = true, thresholdPercent = 20))
        assertTrue(AutomationLogic.shouldAutoEnableSubtitlesForVolume(1, 15))
        assertFalse(AutomationLogic.shouldAutoEnableSubtitlesForVolume(8, 15))
    }

    @Test
    fun pinHashingIsDeterministicAndSalted() {
        val h1 = PrivacyAndSecurityLogic.hashPin("1234")
        val h2 = PrivacyAndSecurityLogic.hashPin("1234")
        val h3 = PrivacyAndSecurityLogic.hashPin("9999")
        assertEquals(h1, h2)
        assertNotEquals(h1, h3)
        assertEquals(64, h1.length)
    }

    @Test
    fun localDataBundleEncodesAllCollections() {
        val json = PrivacyAndSecurityLogic.encodeLocalDataBundle(
            searches = listOf("kotlin", "compose"),
            watchedIds = listOf("abc12345678"),
            bookmarks = listOf(VideoBookmarkItem("b1", "abc12345678", "Title", "Chan", null, 15_000L, "Highlights", "Great moment", 1L)),
            subs = listOf(ManagedSubscription("UC1", "JetBrains", "https://www.youtube.com/channel/UC1")),
        )
        val root = JSONObject(json)
        assertEquals("ytx_local_data_v292", root.getString("bundleSchema"))
        assertEquals(2, root.getJSONArray("searches").length())
        assertEquals(1, root.getJSONArray("bookmarks").length())
        assertEquals(1, root.getJSONArray("subscriptions").length())
    }
}
