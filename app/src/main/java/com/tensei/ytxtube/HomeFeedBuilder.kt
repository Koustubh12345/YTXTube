package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONArray

/**
 * HOME FEED BUILDER — Features 26 to 40
 *
 * 26. Drag-and-drop home sections
 * 27. Reorder content shelves
 * 28. Two-column Bento layout
 * 29. Compact horizontal cards
 * 30. Full-width cinema cards
 * 31. Thumbnail-only layout
 * 32. Custom card sizes
 * 33. Hide channel avatars
 * 34. Hide video duration
 * 35. Hide view counts
 * 36. Hide progress indicators
 * 37. Multiple home layout profiles
 * 38. Customisable home section visibility
 * 39. Subscription-based home feed
 * 40. Smart feed filtering
 */

enum class HomeSection(val label: String) {
    ContinueWatching("Continue Watching"),
    Subscriptions("Latest from Subscriptions"),
    Trending("Trending Now"),
    ShortsShelf("Shorts Shelf"),
    Recommended("Recommended for You"),
    WatchLater("Saved in Watch Later")
}

enum class CardSizePreset(val label: String, val thumbnailAspect: Float, val titleLines: Int) {
    Compact("Compact", 16f / 9f, 1),
    Standard("Standard", 16f / 9f, 2),
    Large("Large", 16f / 9f, 3),
    Cinema("Cinema 21:9", 21f / 9f, 2)
}

enum class HomeLayoutProfile(val label: String) {
    Standard("Standard Feed"),
    CinemaFocus("Cinema Focus"),
    MinimalistBento("Minimalist Bento"),
    DenseCatalog("Dense Catalog"),
    SubscriptionsOnly("Subscriptions Only")
}

object HomeFeedSettings {
    private const val PREFS = "home_feed_builder_prefs"
    private var prefs: SharedPreferences? = null

    // 26 & 27. Reorderable sections
    var sectionOrder = mutableStateListOf(
        HomeSection.ContinueWatching,
        HomeSection.Subscriptions,
        HomeSection.ShortsShelf,
        HomeSection.Trending,
        HomeSection.Recommended,
        HomeSection.WatchLater
    )
        private set

    // 38. Section Visibility
    var visibleSections by mutableStateOf(
        setOf(
            HomeSection.ContinueWatching,
            HomeSection.Subscriptions,
            HomeSection.ShortsShelf,
            HomeSection.Trending,
            HomeSection.Recommended
        )
    )
        private set

    // 28-31. Layout Style
    var layoutStyle by mutableStateOf(ScreenLayoutMode.Standard)
        private set

    // 32. Card Size
    var cardSize by mutableStateOf(CardSizePreset.Standard)
        private set

    // 33. Hide channel avatars
    var hideChannelAvatars by mutableStateOf(false)
        private set

    // 36. Hide progress indicators
    var hideProgressIndicators by mutableStateOf(false)
        private set

    // 37. Layout Profile
    var layoutProfile by mutableStateOf(HomeLayoutProfile.Standard)
        private set

    // 39. Subscriptions-based feed toggle
    var subscriptionOnlyMode by mutableStateOf(false)
        private set

    // 40. Smart Filtering rules
    var filterMaxDurationMinutes by mutableIntStateOf(0) // 0 = no cap
    var filterMinDurationMinutes by mutableIntStateOf(0)
    var filterMinViewsK by mutableIntStateOf(0)
    var blockedKeywords = mutableStateListOf<String>()
        private set
    var blockedChannels = mutableStateListOf<String>()
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }

        hideChannelAvatars = p.getBoolean("hide_avatars", false)
        hideProgressIndicators = p.getBoolean("hide_progress", false)
        subscriptionOnlyMode = p.getBoolean("sub_only_mode", false)

        val sizeName = p.getString("card_size", CardSizePreset.Standard.name)
        cardSize = runCatching { CardSizePreset.valueOf(sizeName ?: "") }.getOrDefault(CardSizePreset.Standard)

        val profName = p.getString("layout_profile", HomeLayoutProfile.Standard.name)
        layoutProfile = runCatching { HomeLayoutProfile.valueOf(profName ?: "") }.getOrDefault(HomeLayoutProfile.Standard)

        val layName = p.getString("layout_style", ScreenLayoutMode.Standard.name)
        layoutStyle = runCatching { ScreenLayoutMode.valueOf(layName ?: "") }.getOrDefault(ScreenLayoutMode.Standard)

        filterMaxDurationMinutes = p.getInt("filter_max_dur", 0)
        filterMinDurationMinutes = p.getInt("filter_min_dur", 0)
        filterMinViewsK = p.getInt("filter_min_views", 0)

        // Blocked keywords
        val kwSet = p.getStringSet("blocked_keywords", emptySet()) ?: emptySet()
        blockedKeywords.clear()
        blockedKeywords.addAll(kwSet)

        // Blocked channels
        val chSet = p.getStringSet("blocked_channels", emptySet()) ?: emptySet()
        blockedChannels.clear()
        blockedChannels.addAll(chSet)

        // Sections order
        val sectionsJson = p.getString("section_order", null)
        if (sectionsJson != null) {
            runCatching {
                val array = JSONArray(sectionsJson)
                val list = mutableListOf<HomeSection>()
                for (i in 0 until array.length()) {
                    val name = array.getString(i)
                    runCatching { HomeSection.valueOf(name) }.getOrNull()?.let { list.add(it) }
                }
                HomeSection.entries.forEach { if (it !in list) list.add(it) }
                sectionOrder.clear()
                sectionOrder.addAll(list)
            }
        }

        val visSecSet = p.getStringSet("visible_sections", null)
        if (visSecSet != null) {
            visibleSections = visSecSet.mapNotNull { runCatching { HomeSection.valueOf(it) }.getOrNull() }.toSet()
        }
    }

    fun applyProfile(profile: HomeLayoutProfile) {
        layoutProfile = profile
        prefs?.edit()?.putString("layout_profile", profile.name)?.apply()
        when (profile) {
            HomeLayoutProfile.Standard -> {
                layoutStyle = ScreenLayoutMode.Standard
                cardSize = CardSizePreset.Standard
                subscriptionOnlyMode = false
            }
            HomeLayoutProfile.CinemaFocus -> {
                layoutStyle = ScreenLayoutMode.Cinema
                cardSize = CardSizePreset.Cinema
                subscriptionOnlyMode = false
            }
            HomeLayoutProfile.MinimalistBento -> {
                layoutStyle = ScreenLayoutMode.BentoGrid
                cardSize = CardSizePreset.Compact
                subscriptionOnlyMode = false
            }
            HomeLayoutProfile.DenseCatalog -> {
                layoutStyle = ScreenLayoutMode.CompactRow
                cardSize = CardSizePreset.Compact
                subscriptionOnlyMode = false
            }
            HomeLayoutProfile.SubscriptionsOnly -> {
                layoutStyle = ScreenLayoutMode.Standard
                cardSize = CardSizePreset.Standard
                subscriptionOnlyMode = true
            }
        }
        prefs?.edit()
            ?.putString("layout_style", layoutStyle.name)
            ?.putString("card_size", cardSize.name)
            ?.putBoolean("sub_only_mode", subscriptionOnlyMode)
            ?.apply()
    }

    fun updateLayoutStyle(style: ScreenLayoutMode) {
        layoutStyle = style
        prefs?.edit()?.putString("layout_style", style.name)?.apply()
    }

    fun updateCardSize(size: CardSizePreset) {
        cardSize = size
        prefs?.edit()?.putString("card_size", size.name)?.apply()
    }

    fun toggleHideAvatars(hide: Boolean) {
        hideChannelAvatars = hide
        prefs?.edit()?.putBoolean("hide_avatars", hide)?.apply()
    }

    fun toggleHideProgress(hide: Boolean) {
        hideProgressIndicators = hide
        prefs?.edit()?.putBoolean("hide_progress", hide)?.apply()
    }

    fun toggleSubscriptionOnly(enabled: Boolean) {
        subscriptionOnlyMode = enabled
        prefs?.edit()?.putBoolean("sub_only_mode", enabled)?.apply()
    }

    fun setSectionVisibility(section: HomeSection, visible: Boolean) {
        val newSet = visibleSections.toMutableSet()
        if (visible) newSet.add(section) else newSet.remove(section)
        visibleSections = newSet
        prefs?.edit()?.putStringSet("visible_sections", newSet.map { it.name }.toSet())?.apply()
    }

    fun reorderSection(fromIndex: Int, toIndex: Int) {
        if (fromIndex in sectionOrder.indices && toIndex in sectionOrder.indices) {
            val item = sectionOrder.removeAt(fromIndex)
            sectionOrder.add(toIndex, item)
            val array = JSONArray()
            sectionOrder.forEach { array.put(it.name) }
            prefs?.edit()?.putString("section_order", array.toString())?.apply()
        }
    }

    fun addBlockedKeyword(word: String) {
        val trimmed = word.trim().lowercase()
        if (trimmed.isNotBlank() && trimmed !in blockedKeywords) {
            blockedKeywords.add(trimmed)
            prefs?.edit()?.putStringSet("blocked_keywords", blockedKeywords.toSet())?.apply()
        }
    }

    fun removeBlockedKeyword(word: String) {
        blockedKeywords.remove(word)
        prefs?.edit()?.putStringSet("blocked_keywords", blockedKeywords.toSet())?.apply()
    }

    fun addBlockedChannel(channel: String) {
        val trimmed = channel.trim()
        if (trimmed.isNotBlank() && trimmed !in blockedChannels) {
            blockedChannels.add(trimmed)
            prefs?.edit()?.putStringSet("blocked_channels", blockedChannels.toSet())?.apply()
        }
    }

    fun removeBlockedChannel(channel: String) {
        blockedChannels.remove(channel)
        prefs?.edit()?.putStringSet("blocked_channels", blockedChannels.toSet())?.apply()
    }

    // Feature 40: Smart Feed Filtering logic
    fun allowsVideo(video: VideoItem): Boolean {
        // Keyword blacklist
        val titleLower = video.title.lowercase()
        if (blockedKeywords.any { it in titleLower }) return false

        // Channel blacklist
        val chanLower = video.channel.lowercase()
        if (blockedChannels.any { it.equals(chanLower, ignoreCase = true) || (video.channelId() != null && it.equals(video.channelId(), ignoreCase = true)) }) return false

        // Min/max duration
        val durationMins = video.durationSec / 60
        if (filterMinDurationMinutes > 0 && durationMins < filterMinDurationMinutes && !video.isLive) return false
        if (filterMaxDurationMinutes > 0 && durationMins > filterMaxDurationMinutes && !video.isLive) return false

        // Min views
        if (filterMinViewsK > 0 && video.views > 0 && video.views < filterMinViewsK * 1000L) return false

        return true
    }
}

/**
 * Feature 28: Bento Video Card for 2-column home layouts
 */
@Composable
fun BentoVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerRadius = LiquidGlassThemeEngine.cornerRadiusDp.coerceAtMost(20).dp
    val shape = RoundedCornerShape(cornerRadius)
    val c = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceContainerLow)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )

            if (!Tweaks.hideDuration.value && video.durationSec > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(video.durationSec * 1000L),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!HomeFeedSettings.hideProgressIndicators && Watched.has(video.id)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0xFFFF0033))
                )
            }
        }

        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = LiquidGlassThemeEngine.typographyPreset.fontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = c.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = video.channel,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = c.onSurfaceVariant
            )
        }
    }
}

/**
 * Feature 30: Cinema Video Card for Full-width cinema layout
 */
@Composable
fun CinemaVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerRadius = LiquidGlassThemeEngine.cornerRadiusDp.coerceAtMost(24).dp
    val shape = RoundedCornerShape(cornerRadius)
    val c = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceContainerLow)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(21f / 9f)
                .clip(shape)
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(21f / 9f)
            )

            if (!Tweaks.hideDuration.value && video.durationSec > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = formatDuration(video.durationSec * 1000L),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!HomeFeedSettings.hideChannelAvatars && video.avatar != null) {
                AsyncImage(
                    model = video.avatar,
                    contentDescription = video.channel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = LiquidGlassThemeEngine.typographyPreset.fontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = c.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.channel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = c.onSurfaceVariant
                    )
                    if (!Tweaks.hideViews.value && video.views > 0) {
                        Text(
                            text = " · ${(video.views.takeIf { it > 0 }?.let { formatCount(it) + " views" } ?: "")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
