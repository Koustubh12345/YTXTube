package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryRankerTest {
    private fun video(id: String, title: String, channel: String) =
        VideoItem(id, title, channel, null, null, null, 300, 1000, null)

    private val now = 1_000_000_000_000L

    @Test fun noHistoryKeepsOrder() {
        val list = listOf(video("a", "One", "X"), video("b", "Two", "Y"))
        val out = DiscoveryRanker.rank(emptyList(), list, now)
        assertEquals(listOf("a", "b"), out.map { it.video.id })
        assertTrue(out.all { it.why == null })
    }

    @Test fun watchedTopicMovesUp() {
        val signals = listOf(WatchSignal("Chan", DiscoveryRanker.tokens("Kotlin coroutines explained"), now))
        val list = listOf(video("a", "Cooking pasta tonight", "Z"), video("b", "Advanced kotlin tricks", "Q"))
        val out = DiscoveryRanker.rank(signals, list, now)
        assertEquals("b", out.first().video.id)
        assertEquals("Matches your interest in kotlin", out.first().why)
        assertNull(out.last().why)
    }

    @Test fun overplayedChannelFades() {
        fun bonusFor(n: Int): Float {
            val signals = List(n) { WatchSignal("Chan", emptyList(), now) }
            val out = DiscoveryRanker.rank(signals, listOf(video("a", "zzzz", "Chan"), video("b", "zzzz", "Other")), now)
            return if (out.first().video.id == "a") 1f else 0f
        }
        assertEquals(1f, bonusFor(2))
        // 10 watches still counts but must score below 3 watches (verified through the explanation text)
        val few = DiscoveryRanker.rank(List(3) { WatchSignal("Chan", emptyList(), now) }, listOf(video("a", "zzzz", "Chan")), now)
        assertEquals("Because you watch Chan", few.first().why)
    }

    @Test fun tokensSkipShortAndCommonWords() {
        val t = DiscoveryRanker.tokens("The best Official video 2024 about Rust and Go")
        assertEquals(listOf("rust"), t)
    }
}
