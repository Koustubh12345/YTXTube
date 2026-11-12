package com.tensei.ytxtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeEngineAndNavigationTest {

    @Test
    fun testNeonAccentList() {
        assertTrue(NEON_ACCENTS.isNotEmpty())
        assertTrue(NEON_ACCENTS.any { it.name.contains("Cyan") })
        assertTrue(NEON_ACCENTS.any { it.hex.startsWith("#") })
    }

    @Test
    fun testTypographyPresets() {
        val presets = TypographyPreset.entries
        assertEquals(5, presets.size)
        assertTrue(presets.contains(TypographyPreset.RoundedHeavy))
        assertTrue(presets.contains(TypographyPreset.MonospaceTech))
    }

    @Test
    fun testNavigationTabOrdering() {
        val initial = listOf(Tab.Home, Tab.Music, Tab.Shorts, Tab.Library, Tab.Downloads)
        val mutable = initial.toMutableList()
        val moved = mutable.removeAt(0)
        mutable.add(1, moved)
        assertEquals(Tab.Music, mutable[0])
        assertEquals(Tab.Home, mutable[1])
    }

    @Test
    fun testKeywordBlacklist() {
        val blocked = listOf("clickbait", "spoiler", "prank")
        fun allows(title: String): Boolean {
            val lower = title.lowercase()
            return blocked.none { it in lower }
        }

        assertTrue(allows("Clean tutorial on Jetpack Compose"))
        assertFalse(allows("Huge SPOILER for the new movie"))
        assertFalse(allows("Craziest prank in public"))
    }

    @Test
    fun testThemeJsonEncodingAndDecoding() {
        val json = JSONObject()
        json.put("neon_hex", "#00F5FF")
        json.put("blur_radius", 18.0)
        json.put("surface_alpha", 0.45)
        json.put("corner_radius", 26)

        assertEquals("#00F5FF", json.getString("neon_hex"))
        assertEquals(18.0, json.getDouble("blur_radius"), 0.001)
        assertEquals(0.45, json.getDouble("surface_alpha"), 0.001)
        assertEquals(26, json.getInt("corner_radius"))
    }
}
