package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartDiscoveryAndSubsTest {

    private val sampleVideos = listOf(
        VideoItem("v1111111111", "Kotlin Compose UI Masterclass", "JetBrains", "https://www.youtube.com/channel/UC1", null, null, 2400, 120_000, "2 hours ago"),
        VideoItem("v2222222222", "Hidden Gem Rust OS Kernel Guide", "IndieSystems", "https://www.youtube.com/channel/UC2", null, null, 900, 4_200, "3 days ago"),
        VideoItem("v3333333333", "Quick 3 Minute Git Fix", "DevTips", "https://www.youtube.com/channel/UC3", null, null, 180, 18_000, "12 hours ago"),
    )

    @Test
    fun lowViewGemsAndLongFormLensesFilterAccurately() {
        val gems = SmartDiscoveryLogic.filterByLens(sampleVideos, DiscoveryLens.LowViewGems)
        assertEquals(listOf("v2222222222", "v3333333333"), gems.map { it.id })

        val longForm = SmartDiscoveryLogic.filterByLens(sampleVideos, DiscoveryLens.LongForm)
        assertEquals(listOf("v1111111111"), longForm.map { it.id })

        val fresh = SmartDiscoveryLogic.filterByLens(sampleVideos, DiscoveryLens.NewChannels, knownChannels = setOf("JetBrains"))
        assertEquals(2, fresh.size)
    }

    @Test
    fun trendingChannelsAggregatesViewsAndTopics() {
        val ranked = SmartDiscoveryLogic.discoverTrendingChannels(sampleVideos)
        assertEquals("JetBrains", ranked.first().name)
        assertEquals("Software & Coding", ranked.first().primaryTopic)
    }

    @Test
    fun newPipeJsonAndCsvSubscriptionImportExportRoundTrip() {
        val subs = listOf(
            ManagedSubscription("UC1", "JetBrains", "https://www.youtube.com/channel/UC1", folder = "Tech"),
            ManagedSubscription("UC2", "Veritasium", "https://www.youtube.com/channel/UC2", folder = "Science"),
        )
        val json = SubscriptionImportExportCodec.exportNewPipeJson(subs)
        val parsedJson = SubscriptionImportExportCodec.parseAny(json)
        assertEquals(2, parsedJson.size)
        assertEquals("JetBrains", parsedJson[0].name)
        assertEquals("Tech", parsedJson[0].folder)

        val csv = SubscriptionImportExportCodec.exportCsv(subs)
        val parsedCsv = SubscriptionImportExportCodec.parseAny(csv)
        assertEquals(2, parsedCsv.size)
        assertEquals("Veritasium", parsedCsv[1].name)
    }

    @Test
    fun searchHistoryIntelligenceClustersTopics() {
        val insight = SmartDiscoveryLogic.analyzeSearchHistory(
            listOf("kotlin compose tutorial", "android media3 exoplayer", "acoustic guitar chords"),
        )
        assertTrue(insight.topTopics.isNotEmpty())
        assertEquals("Software & Coding", insight.topTopics.first().first)
        assertTrue(insight.suggestedNextQueries.isNotEmpty())
    }
}
