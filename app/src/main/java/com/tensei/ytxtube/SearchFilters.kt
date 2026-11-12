package com.tensei.ytxtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DurationFilter(val label: String, private val minSec: Long, private val maxSec: Long) {
    AnyLength("Any length", 0L, Long.MAX_VALUE),
    Under4("Under 4 min", 0L, 239L),
    From4To20("4\u201320 min", 240L, 1_200L),
    Over20("Over 20 min", 1_201L, Long.MAX_VALUE);

    fun matches(seconds: Long): Boolean = seconds in minSec..maxSec
}

enum class UploadFilter(val label: String, private val maxAgeDays: Double?) {
    AnyTime("Any time", null),
    Day("Last 24 hours", 1.0),
    Week("This week", 7.0),
    Month("This month", 30.0),
    Year("This year", 365.0);

    /** Videos whose age is unknown never match a real time window. */
    fun matches(ageDays: Double?): Boolean =
        maxAgeDays == null || (ageDays != null && ageDays <= maxAgeDays)
}

enum class SortOrder(val label: String) {
    Relevance("Relevance"),
    UploadDate("Newest first"),
    ViewCount("Most viewed"),
}

data class SearchFilter(
    val duration: DurationFilter = DurationFilter.AnyLength,
    val upload: UploadFilter = UploadFilter.AnyTime,
    val liveOnly: Boolean = false,
    val sort: SortOrder = SortOrder.Relevance,
) {
    val isDefault: Boolean get() = this == SearchFilter()
}

/** Turns YouTube's relative dates ("3 days ago", "Streamed 2 weeks ago") into an age in days. */
object UploadAge {
    private val pattern = Regex("(\\d+)\\s*(second|minute|hour|day|week|month|year)s?", RegexOption.IGNORE_CASE)

    fun days(text: String?): Double? {
        val m = pattern.find(text.orEmpty()) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "second" -> n / 86_400.0
            "minute" -> n / 1_440.0
            "hour" -> n / 24.0
            "day" -> n
            "week" -> n * 7
            "month" -> n * 30
            "year" -> n * 365
            else -> null
        }
    }
}

/**
 * Filters and sorts a result list on the device. YouTube's own search filters are not used, so this
 * works on any list of videos (search, channel, playlist). Live streams have no duration, so they
 * are hidden whenever a duration filter is active.
 */
fun SearchFilter.apply(videos: List<VideoItem>): List<VideoItem> =
    filterAndSort(videos, this, { it.durationSec }, { it.isLive }, { it.views }, { it.uploaded })

internal fun <T> filterAndSort(
    items: List<T>,
    filter: SearchFilter,
    durationSec: (T) -> Long,
    isLive: (T) -> Boolean,
    views: (T) -> Long,
    uploaded: (T) -> String?,
): List<T> {
    val kept = items.filter { item ->
        val live = isLive(item)
        (!filter.liveOnly || live) &&
            (filter.duration == DurationFilter.AnyLength || (!live && filter.duration.matches(durationSec(item)))) &&
            filter.upload.matches(UploadAge.days(uploaded(item)))
    }
    return when (filter.sort) {
        SortOrder.Relevance -> kept
        SortOrder.UploadDate -> kept.sortedBy { UploadAge.days(uploaded(it)) ?: Double.MAX_VALUE }
        SortOrder.ViewCount -> kept.sortedByDescending { views(it) }
    }
}

/** A row of chips above the results. Tapping a chip steps it to its next value. */
@Composable
fun SearchFilterRow(filter: SearchFilter, onChange: (SearchFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPill(filter.sort.label, filter.sort != SortOrder.Relevance) {
            onChange(filter.copy(sort = SortOrder.entries[(filter.sort.ordinal + 1) % SortOrder.entries.size]))
        }
        FilterPill(filter.upload.label, filter.upload != UploadFilter.AnyTime) {
            onChange(filter.copy(upload = UploadFilter.entries[(filter.upload.ordinal + 1) % UploadFilter.entries.size]))
        }
        FilterPill(filter.duration.label, filter.duration != DurationFilter.AnyLength) {
            onChange(filter.copy(duration = DurationFilter.entries[(filter.duration.ordinal + 1) % DurationFilter.entries.size]))
        }
        FilterPill("Live", filter.liveOnly) { onChange(filter.copy(liveOnly = !filter.liveOnly)) }
        if (!filter.isDefault) FilterPill("Reset", false) { onChange(SearchFilter()) }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) c.primaryContainer else c.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) c.onPrimaryContainer else c.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
