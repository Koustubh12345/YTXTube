package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileRulesTest {
    private val tweakTypes = mapOf("hide_shorts" to 'b', "seek_step_sec" to 'i', "caption_lang" to 's')

    private fun typed(key: String, value: Any?) = ProfileRules.typedValue(key, value, tweakTypes)

    @Test
    fun integerLookingFloatBecomesFloat() {
        // A float of 1.0 is written to JSON as "1" and read back as an Int; it must stay a Float.
        assertEquals(1.0f, typed("a:ambient_intensity", 1))
        assertEquals(1.5f, typed("a:ambient_reach", 1.5))
    }

    @Test
    fun wholeDoubleBecomesInt() {
        assertEquals(10, typed("t:seek_step_sec", 10.0))
        assertEquals(10, typed("t:seek_step_sec", 10L))
    }

    @Test
    fun wrongTypesAreSkipped() {
        assertNull(typed("t:seek_step_sec", "ten"))
        assertNull(typed("t:seek_step_sec", 10.5))
        assertNull(typed("t:hide_shorts", 1))
        assertNull(typed("t:caption_lang", true))
        assertNull(typed("a:ambient_light", "yes"))
        assertNull(typed("a:ambient_intensity", Double.NaN))
        assertNull(typed("t:seek_step_sec", Long.MAX_VALUE))
    }

    @Test
    fun correctTypesPassThrough() {
        assertEquals(true, typed("t:hide_shorts", true))
        assertEquals("en", typed("t:caption_lang", "en"))
        assertEquals("amoled", typed("a:mode", "amoled"))
    }

    @Test
    fun unknownKeysAndScopesAreRejected() {
        assertNull(typed("t:does_not_exist", true))
        assertNull(typed("a:use_system_font", true))
        assertNull(typed("x:hide_shorts", true))
        assertNull(typed("hide_shorts", true))
    }

    @Test
    fun overlongStringsAreRejected() {
        assertNull(typed("t:caption_lang", "x".repeat(65)))
    }
}
