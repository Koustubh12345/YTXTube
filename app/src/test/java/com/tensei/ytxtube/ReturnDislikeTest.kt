package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnDislikeTest {

    @Test
    fun smallCountsAreShownAsIs() {
        assertEquals("0", ReturnDislike.format(0))
        assertEquals("999", ReturnDislike.format(999))
    }

    @Test
    fun negativeCountsBecomeZero() {
        assertEquals("0", ReturnDislike.format(-5))
    }

    @Test
    fun thousandsUseOneDecimalUnderTen() {
        assertEquals("1K", ReturnDislike.format(1_000))
        assertEquals("1.2K", ReturnDislike.format(1_200))
        assertEquals("9.9K", ReturnDislike.format(9_999))
    }

    @Test
    fun thousandsDropTheDecimalFromTen() {
        assertEquals("12K", ReturnDislike.format(12_345))
        assertEquals("27K", ReturnDislike.format(27_326))
        assertEquals("498K", ReturnDislike.format(498_153))
        assertEquals("999K", ReturnDislike.format(999_999))
    }

    @Test
    fun millionsAndBillions() {
        assertEquals("1M", ReturnDislike.format(1_000_000))
        assertEquals("1.5M", ReturnDislike.format(1_500_000))
        assertEquals("2.3B", ReturnDislike.format(2_300_000_000L))
    }

    @Test
    fun videoIdValidation() {
        assertTrue(ReturnDislike.isVideoId("kxOuG8jMIgI"))
        assertTrue(ReturnDislike.isVideoId("a-b_c1234Zz"))
        assertFalse(ReturnDislike.isVideoId(""))
        assertFalse(ReturnDislike.isVideoId("tooshort"))
        assertFalse(ReturnDislike.isVideoId("has spaces!"))
        assertFalse(ReturnDislike.isVideoId("kxOuG8jMIgI&x=1"))
    }

    @Test
    fun likeRatio() {
        assertEquals(0.75f, VoteCounts(75, 25).likeRatio!!, 0.0001f)
        assertNull(VoteCounts(0, 0).likeRatio)
    }
}
