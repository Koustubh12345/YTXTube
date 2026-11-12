package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFiltersTest {
    private data class Item(val id: String, val sec: Long, val live: Boolean, val views: Long, val up: String?)

    private fun run(items: List<Item>, filter: SearchFilter) =
        filterAndSort(items, filter, { it.sec }, { it.live }, { it.views }, { it.up }).map { it.id }

    private val items = listOf(
        Item("short", 120, false, 500, "2 days ago"),
        Item("medium", 600, false, 9_000, "3 weeks ago"),
        Item("long", 3_600, false, 100, "1 year ago"),
        Item("live", 0, true, 42, "Streamed 2 hours ago"),
        Item("nodate", 300, false, 7, null),
    )

    @Test
    fun parsesRelativeDates() {
        assertEquals(3.0, UploadAge.days("3 days ago")!!, 0.0001)
        assertEquals(14.0, UploadAge.days("Streamed 2 weeks ago")!!, 0.0001)
        assertEquals(60.0, UploadAge.days("2 months ago")!!, 0.0001)
        assertEquals(365.0, UploadAge.days("1 year ago")!!, 0.0001)
        assertEquals(5 / 24.0, UploadAge.days("5 hours ago")!!, 0.0001)
        assertNull(UploadAge.days(null))
        assertNull(UploadAge.days("Premiered yesterday"))
    }

    @Test
    fun durationBucketsHaveExactBoundaries() {
        assertTrue(DurationFilter.Under4.matches(239))
        assertFalse(DurationFilter.Under4.matches(240))
        assertTrue(DurationFilter.From4To20.matches(240))
        assertTrue(DurationFilter.From4To20.matches(1_200))
        assertFalse(DurationFilter.From4To20.matches(1_201))
        assertTrue(DurationFilter.Over20.matches(1_201))
    }

    @Test
    fun unknownAgeNeverMatchesATimeWindow() {
        assertTrue(UploadFilter.AnyTime.matches(null))
        assertFalse(UploadFilter.Year.matches(null))
        assertTrue(UploadFilter.Week.matches(7.0))
        assertFalse(UploadFilter.Week.matches(7.5))
    }

    @Test
    fun defaultFilterKeepsEverythingInOrder() {
        assertEquals(listOf("short", "medium", "long", "live", "nodate"), run(items, SearchFilter()))
    }

    @Test
    fun liveOnlyKeepsLiveStreams() {
        assertEquals(listOf("live"), run(items, SearchFilter(liveOnly = true)))
    }

    @Test
    fun durationFilterHidesLiveStreams() {
        // "nodate" is 300 s long, inside the 4-20 min bucket, so it stays; the live stream is hidden.
        assertEquals(listOf("medium", "nodate"), run(items, SearchFilter(duration = DurationFilter.From4To20)))
    }

    @Test
    fun uploadWindowKeepsRecentAndDropsUnknown() {
        assertEquals(listOf("short", "live"), run(items, SearchFilter(upload = UploadFilter.Week)))
    }

    @Test
    fun sortByViewsIsDescending() {
        assertEquals(
            listOf("medium", "short", "long", "live", "nodate"),
            run(items, SearchFilter(sort = SortOrder.ViewCount)),
        )
    }

    @Test
    fun sortByDateIsNewestFirstWithUnknownLast() {
        assertEquals(
            listOf("live", "short", "medium", "long", "nodate"),
            run(items, SearchFilter(sort = SortOrder.UploadDate)),
        )
    }
}
