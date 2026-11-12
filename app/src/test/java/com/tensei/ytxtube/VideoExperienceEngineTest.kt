package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExperienceEngineTest {

    @Test
    fun naturalColorPresetIsIdentity() {
        assertTrue(VideoColorMath.isIdentity(1.0f, 1.0f, 0.0f, 0.0f))
        assertFalse(VideoColorMath.isIdentity(1.38f, 1.12f, 0.02f, 0.03f))
    }

    @Test
    fun colorMatrixHas20ElementsAndPreservesAlpha() {
        val m = VideoColorMath.buildMatrixValues(1.2f, 1.1f, 0.05f, 0.1f)
        assertEquals(20, m.size)
        assertEquals(1.0f, m[18], 0.0001f)
        assertEquals(0.0f, m[19], 0.0001f)
    }

    @Test
    fun monochromeMatrixEqualizesRgbChannels() {
        val m = VideoColorMath.buildMatrixValues(0.0f, 1.0f, 0.0f, 0.0f)
        // With saturation = 0 and warmth = 0, R, G, B rows have identical luma weights
        assertEquals(m[0], m[5], 0.0001f)
        assertEquals(m[5], m[10], 0.0001f)
        assertEquals(m[1], m[6], 0.0001f)
    }

    @Test
    fun playbackPresetJsonRoundTrip() {
        val original = PlaybackPreset(
            id = "custom_test",
            name = "Deep Coding",
            speed = 1.75f,
            skipSilence = true,
            autoCaptions = true,
            audioOnly = false,
            eqPreset = AudioEffects.Preset.Vocal,
            boostIndex = 2,
            colorPreset = VideoColorPreset.CoolCrisp,
            maxQualityHeight = 1440,
            skipFirstSec = 15,
            builtIn = false,
        )
        val decoded = PlaybackPreset.fromJson(original.toJson())
        assertEquals(original, decoded)
    }

    @Test
    fun videoComparisonIdentifiesSharedTopicsAndDurationRatio() {
        val a = VideoItem("v1111111111", "Kotlin Coroutines Crash Course", "JetBrains", null, null, null, 180, 50_000, "2 days ago")
        val b = VideoItem("v2222222222", "Kotlin Coroutines Deep Architecture Masterclass", "JetBrains", null, null, null, 1800, 10_000, "1 week ago")
        val cmp = VideoComparisonAnalyzer.compare(a, b)
        assertTrue(cmp.sharedKeywords.contains("kotlin"))
        assertTrue(cmp.sharedKeywords.contains("coroutines"))
        assertEquals(-1620L, cmp.durationDiffSec)
        assertEquals(5.0f, cmp.viewsRatio, 0.01f)
    }

    @Test
    fun frameStepReadoutFormatting() {
        val readout = FrameStep.formatFrameReadout(65_500L, 60f)
        assertTrue(readout.startsWith("01:05.500"))
        assertTrue(readout.contains("#F3930"))
        assertTrue(readout.contains("60fps"))
    }
}
