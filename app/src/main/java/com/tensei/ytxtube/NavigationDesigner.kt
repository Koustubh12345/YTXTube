package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import org.json.JSONArray

/**
 * NAVIGATION & INTERFACE — Features 16 to 25
 *
 * 16. Floating glass bottom navigation
 * 17. Animated liquid-lens indicator
 * 18. Drag-and-drop navigation tab ordering
 * 19. Enable or disable individual tabs
 * 20. Icon-only navigation
 * 21. Navigation auto-collapse
 * 22. Floating search button
 * 23. Custom navigation icon packs
 * 24. Independent screen layouts
 * 25. Global UI element visibility manager
 */

// --- Feature 23: Custom Navigation Icon Packs ---
enum class NavigationIconPack(val label: String) {
    RoundedMaterial("Rounded Material"),
    OutlinedMinimal("Outlined Minimal"),
    NeonDuotone("Neon Duotone"),
    BoldGlyph("Bold Glyph")
}

fun iconForTab(tab: Tab, pack: NavigationIconPack, selected: Boolean): ImageVector {
    return when (pack) {
        NavigationIconPack.RoundedMaterial, NavigationIconPack.NeonDuotone ->
            if (selected) tab.selectedIcon else tab.icon
        NavigationIconPack.OutlinedMinimal ->
            tab.icon
        NavigationIconPack.BoldGlyph ->
            tab.selectedIcon
    }
}

// --- Feature 24: Independent Screen Layouts ---
enum class ScreenLayoutMode(val label: String) {
    Standard("Standard List"),
    BentoGrid("2-Column Bento"),
    CompactRow("Compact Rows"),
    Cinema("Cinema Full-Width"),
    ThumbnailOnly("Visual Only")
}

/**
 * Navigation Settings singleton managing tab ordering, enablement,
 * icon packs, auto-collapse and floating controls.
 */
object NavigationSettings {
    private const val PREFS = "nav_designer_prefs"
    private var prefs: SharedPreferences? = null

    // 18. Tab Ordering
    var tabOrder = mutableStateListOf(Tab.Home, Tab.Music, Tab.Shorts, Tab.Library, Tab.Downloads)
        private set

    // 19. Tab Enablement (Tab.Home cannot be disabled)
    var enabledTabs by mutableStateOf(setOf(Tab.Home, Tab.Music, Tab.Shorts, Tab.Library, Tab.Downloads))
        private set

    // 20. Icon-only navigation
    var iconOnly by mutableStateOf(false)
        private set

    // 21. Navigation auto-collapse on scroll
    var autoCollapseOnScroll by mutableStateOf(false)
        private set

    // 22. Floating search button
    var floatingSearchButton by mutableStateOf(false)
        private set

    // 23. Icon Pack
    var iconPack by mutableStateOf(NavigationIconPack.RoundedMaterial)
        private set

    // 24. Per-Screen Layout Modes
    var homeLayout by mutableStateOf(ScreenLayoutMode.Standard)
        private set
    var libraryLayout by mutableStateOf(ScreenLayoutMode.Standard)
        private set
    var downloadsLayout by mutableStateOf(ScreenLayoutMode.Standard)
        private set

    // Auto-collapse animation offset tracker (0f = fully visible, 1f = fully collapsed)
    var navCollapseFraction by mutableFloatStateOf(0f)

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }

        iconOnly = p.getBoolean("icon_only", false)
        autoCollapseOnScroll = p.getBoolean("auto_collapse", false)
        floatingSearchButton = p.getBoolean("floating_search", false)

        val packName = p.getString("icon_pack", NavigationIconPack.RoundedMaterial.name)
        iconPack = runCatching { NavigationIconPack.valueOf(packName ?: "") }.getOrDefault(NavigationIconPack.RoundedMaterial)

        val savedOrderJson = p.getString("tab_order", null)
        if (savedOrderJson != null) {
            runCatching {
                val array = JSONArray(savedOrderJson)
                val list = mutableListOf<Tab>()
                for (i in 0 until array.length()) {
                    val name = array.getString(i)
                    runCatching { Tab.valueOf(name) }.getOrNull()?.let { list.add(it) }
                }
                Tab.entries.forEach { if (it !in list) list.add(it) }
                tabOrder.clear()
                tabOrder.addAll(list)
            }
        }

        val savedEnabled = p.getStringSet("enabled_tabs", null)
        if (savedEnabled != null) {
            val set = savedEnabled.mapNotNull { runCatching { Tab.valueOf(it) }.getOrNull() }.toMutableSet()
            set.add(Tab.Home) // Always ensure Home is enabled
            enabledTabs = set
        }

        val hLay = p.getString("home_layout", ScreenLayoutMode.Standard.name)
        homeLayout = runCatching { ScreenLayoutMode.valueOf(hLay ?: "") }.getOrDefault(ScreenLayoutMode.Standard)

        val lLay = p.getString("library_layout", ScreenLayoutMode.Standard.name)
        libraryLayout = runCatching { ScreenLayoutMode.valueOf(lLay ?: "") }.getOrDefault(ScreenLayoutMode.Standard)

        val dLay = p.getString("downloads_layout", ScreenLayoutMode.Standard.name)
        downloadsLayout = runCatching { ScreenLayoutMode.valueOf(dLay ?: "") }.getOrDefault(ScreenLayoutMode.Standard)
    }

    val visibleTabs: List<Tab>
        get() = tabOrder.filter { it in enabledTabs }

    fun reorderTabs(fromIndex: Int, toIndex: Int) {
        if (fromIndex in tabOrder.indices && toIndex in tabOrder.indices) {
            val item = tabOrder.removeAt(fromIndex)
            tabOrder.add(toIndex, item)
            saveTabOrder()
        }
    }

    fun setTabEnabled(tab: Tab, enabled: Boolean) {
        if (tab == Tab.Home) return // Home is mandatory
        val newSet = enabledTabs.toMutableSet()
        if (enabled) newSet.add(tab) else newSet.remove(tab)
        enabledTabs = newSet
        prefs?.edit()?.putStringSet("enabled_tabs", newSet.map { it.name }.toSet())?.apply()
    }

    fun updateIconOnly(value: Boolean) {
        iconOnly = value
        prefs?.edit()?.putBoolean("icon_only", value)?.apply()
    }

    fun updateAutoCollapse(value: Boolean) {
        autoCollapseOnScroll = value
        prefs?.edit()?.putBoolean("auto_collapse", value)?.apply()
        if (!value) navCollapseFraction = 0f
    }

    fun updateFloatingSearch(value: Boolean) {
        floatingSearchButton = value
        prefs?.edit()?.putBoolean("floating_search", value)?.apply()
    }

    fun updateIconPack(pack: NavigationIconPack) {
        iconPack = pack
        prefs?.edit()?.putString("icon_pack", pack.name)?.apply()
    }

    fun setScreenLayout(screen: String, mode: ScreenLayoutMode) {
        when (screen) {
            "home" -> {
                homeLayout = mode
                prefs?.edit()?.putString("home_layout", mode.name)?.apply()
            }
            "library" -> {
                libraryLayout = mode
                prefs?.edit()?.putString("library_layout", mode.name)?.apply()
            }
            "downloads" -> {
                downloadsLayout = mode
                prefs?.edit()?.putString("downloads_layout", mode.name)?.apply()
            }
        }
    }

    private fun saveTabOrder() {
        val array = JSONArray()
        tabOrder.forEach { array.put(it.name) }
        prefs?.edit()?.putString("tab_order", array.toString())?.apply()
    }
}

/**
 * Feature 21: Auto-collapse Nested Scroll Connection
 * Hides the bottom navigation bar gracefully when scrolling down,
 * reveals it when scrolling up.
 */
class AutoCollapseScrollConnection(
    private val onFractionChanged: (Float) -> Unit
) : NestedScrollConnection {
    private var currentFraction = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!NavigationSettings.autoCollapseOnScroll) return Offset.Zero

        val delta = available.y
        if (delta < -10f) {
            // Scrolling down -> collapse
            currentFraction = (currentFraction + 0.12f).coerceIn(0f, 1f)
            onFractionChanged(currentFraction)
        } else if (delta > 10f) {
            // Scrolling up -> expand
            currentFraction = (currentFraction - 0.12f).coerceIn(0f, 1f)
            onFractionChanged(currentFraction)
        }
        return Offset.Zero
    }
}

// --- Feature 25: Global UI Element Visibility Manager ---
object UiVisibilityManager {
    private const val PREFS = "ui_visibility_prefs"
    private var prefs: SharedPreferences? = null

    var showBottomBar by mutableStateOf(true)
    var showHeaderChips by mutableStateOf(true)
    var showQuickActionBars by mutableStateOf(true)
    var showStatsOverlay by mutableStateOf(false)
    var showChannelAvatars by mutableStateOf(true)
    var showBadges by mutableStateOf(true)

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        showBottomBar = p.getBoolean("show_bottom_bar", true)
        showHeaderChips = p.getBoolean("show_header_chips", true)
        showQuickActionBars = p.getBoolean("show_quick_actions", true)
        showStatsOverlay = p.getBoolean("show_stats_overlay", false)
        showChannelAvatars = p.getBoolean("show_channel_avatars", true)
        showBadges = p.getBoolean("show_badges", true)
    }

    fun toggleBottomBar(visible: Boolean) {
        showBottomBar = visible
        prefs?.edit()?.putBoolean("show_bottom_bar", visible)?.apply()
    }

    fun toggleHeaderChips(visible: Boolean) {
        showHeaderChips = visible
        prefs?.edit()?.putBoolean("show_header_chips", visible)?.apply()
    }

    fun toggleQuickActions(visible: Boolean) {
        showQuickActionBars = visible
        prefs?.edit()?.putBoolean("show_quick_actions", visible)?.apply()
    }

    fun toggleStatsOverlay(visible: Boolean) {
        showStatsOverlay = visible
        prefs?.edit()?.putBoolean("show_stats_overlay", visible)?.apply()
    }

    fun toggleChannelAvatars(visible: Boolean) {
        showChannelAvatars = visible
        prefs?.edit()?.putBoolean("show_channel_avatars", visible)?.apply()
    }

    fun toggleBadges(visible: Boolean) {
        showBadges = visible
        prefs?.edit()?.putBoolean("show_badges", visible)?.apply()
    }
}
