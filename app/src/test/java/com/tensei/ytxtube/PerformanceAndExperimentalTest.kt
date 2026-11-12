package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceAndExperimentalTest {

    @Test
    fun calculatesFrameDropPercentageAndAdaptiveBlurFallback() {
        assertEquals(0f, PerformanceAndSessionMath.frameDropPercent(0, 0), 0.01f)
        assertEquals(5.0f, PerformanceAndSessionMath.frameDropPercent(950, 50), 0.01f)

        assertEquals(20, PerformanceAndSessionMath.adaptiveBlurDp(20, 1.0f, lowMemoryActive = false))
        assertEquals(10, PerformanceAndSessionMath.adaptiveBlurDp(20, 5.0f, lowMemoryActive = false))
        assertEquals(0, PerformanceAndSessionMath.adaptiveBlurDp(20, 10.0f, lowMemoryActive = false))
        assertEquals(0, PerformanceAndSessionMath.adaptiveBlurDp(20, 0.0f, lowMemoryActive = true))
    }

    @Test
    fun calculatesWatchSessionTimeSavedFromSpeedAndSponsorBlock() {
        val saved = PerformanceAndSessionMath.calculateTimeSavedSeconds(
            activeWatchSeconds = 1200L, // 20 min watched at 1.5x -> 600s saved
            averageSpeed = 1.5f,
            sponsorSkippedSeconds = 90L,
        )
        assertEquals(690L, saved)
    }

    @Test
    fun validatesSafeDeclarativeControlPluginAndRejectsUnsafeCode() {
        val validJson = """
            {
              "schema": "ytx_plugin_v292",
              "id": "custom_ab_loop",
              "label": "Quick Loop",
              "actionId": "toggle_ab_loop",
              "permissions": ["player.seek"],
              "enabled": true
            }
        """.trimIndent()
        val res = PluginPackageValidator.validate(validJson)
        assertTrue(res.isSuccess)
        assertEquals("Quick Loop", res.getOrThrow().label)

        val unsafeJson = """
            {
              "schema": "ytx_plugin_v292",
              "id": "evil_plugin",
              "label": "Run Shell",
              "actionId": "exec_arbitrary_code",
              "permissions": ["root.shell"]
            }
        """.trimIndent()
        assertFalse(PluginPackageValidator.validate(unsafeJson).isSuccess)
    }
}
