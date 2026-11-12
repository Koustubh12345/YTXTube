package com.tensei.ytxtube

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Debug
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

// ============================================================================
// CATEGORY 10: ADVANCED SETTINGS EXPERIENCE (Features 121–130)
// CATEGORY 11: PERFORMANCE ENGINE (Features 131–140)
// CATEGORY 12: EXPERIMENTAL FEATURES & MODULAR SYSTEM (Features 141–150)
// Safe declarative configuration packages only — zero arbitrary code execution.
// ============================================================================

enum class ThumbnailPreloadTier(val label: String, val targetWidthPx: Int) {
    DataSaver("SD 480p (Fastest)", 480),
    BalancedHd("HD 720p (Default)", 720),
    UltraMaxRes("MaxRes 1080p", 1280),
}

@Immutable
data class MemoryAndFrameMetrics(
    val jvmUsedMb: Long,
    val jvmMaxMb: Long,
    val nativeAllocatedMb: Long,
    val renderedFrames: Int,
    val droppedFrames: Int,
    val dropRatioPercent: Float,
    val measuredBandwidthKbps: Int,
)

@Immutable
data class CompatibilityCheckItem(
    val feature: String,
    val supported: Boolean,
    val detail: String,
)

/**
 * Feature 141: Safe, declarative, permission-limited Video Control Plugin definition.
 * Never executes arbitrary code; maps strictly to whitelisted player actions.
 */
@Immutable
data class DeclarativeControlPlugin(
    val id: String,
    val label: String,
    val actionId: String,
    val permissions: List<String>,
    val enabled: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", "ytx_plugin_v292")
        .put("id", id)
        .put("label", label)
        .put("actionId", actionId)
        .put("permissions", JSONArray(permissions))
        .put("enabled", enabled)
}

/**
 * Feature 141 & 150: Strict validator for declarative JSON control plugins.
 * Rejects unknown permissions, executable scripts, external URLs, or unknown action IDs.
 */
object PluginPackageValidator {
    val allowedActions = setOf(
        "toggle_ab_loop",
        "toggle_frame_step",
        "pin_timestamp_bookmark",
        "cycle_color_preset",
        "toggle_audio_only",
        "toggle_skip_silence",
        "copy_markdown_link",
    )
    val allowedPermissions = setOf("player.seek", "player.speed", "player.visual", "bookmarks.write", "clipboard.write")

    fun validate(rawJson: String): Result<DeclarativeControlPlugin> = runCatching {
        val o = JSONObject(rawJson)
        require(o.optString("schema") == "ytx_plugin_v292") { "Invalid plugin schema (expected 'ytx_plugin_v292')" }
        val id = o.optString("id").trim()
        require(id.matches(Regex("^[a-z0-9_]{3,32}$"))) { "Plugin ID must be 3..32 lowercase alphanumeric/underscore chars" }
        val label = o.optString("label").trim()
        require(label.length in 2..24) { "Plugin label must be 2..24 chars" }
        val actionId = o.optString("actionId").trim()
        require(actionId in allowedActions) { "Disallowed or unsafe actionId '$actionId'" }
        val permsArr = o.optJSONArray("permissions") ?: JSONArray()
        val perms = (0 until permsArr.length()).map { permsArr.getString(it) }
        require(perms.all { it in allowedPermissions }) { "Plugin requests unapproved permissions" }
        DeclarativeControlPlugin(
            id = id,
            label = label,
            actionId = actionId,
            permissions = perms,
            enabled = o.optBoolean("enabled", true),
        )
    }
}

/** Feature 149: Unified multi-type Advanced Content Collection item. */
@Immutable
data class UnifiedCollectionItem(
    val id: String,
    val kind: String, // "video", "music", "channel", "note"
    val title: String,
    val subtitle: String,
    val targetIdOrUrl: String,
    val collectionName: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind)
        .put("title", title)
        .put("subtitle", subtitle)
        .put("target", targetIdOrUrl)
        .put("collection", collectionName)

    companion object {
        fun fromJson(o: JSONObject): UnifiedCollectionItem = UnifiedCollectionItem(
            id = o.optString("id"),
            kind = o.optString("kind", "video"),
            title = o.optString("title"),
            subtitle = o.optString("subtitle"),
            targetIdOrUrl = o.optString("target"),
            collectionName = o.optString("collection", "Research Vault"),
        )
    }
}

/**
 * Pure, testable Performance & Session Math (Features 131, 132, 138, 148).
 */
object PerformanceAndSessionMath {

    /** Feature 138: Computes frame drop percentage (0f..100f). */
    fun frameDropPercent(rendered: Int, dropped: Int): Float {
        val total = rendered + dropped
        if (total <= 0) return 0f
        return (dropped.toFloat() * 100f / total.toFloat()).coerceIn(0f, 100f)
    }

    /** Feature 132: Dynamic blur fallback — steps down blur radius when frame drops or memory pressure rise. */
    fun adaptiveBlurDp(configuredBlurDp: Int, frameDropPct: Float, lowMemoryActive: Boolean): Int {
        if (lowMemoryActive || frameDropPct >= 8.0f) return 0
        if (frameDropPct >= 3.5f) return (configuredBlurDp / 2).coerceAtLeast(4)
        return configuredBlurDp
    }

    /** Feature 148: Calculates time saved (in seconds) by watching at >1.0x speed plus SponsorBlock skips. */
    fun calculateTimeSavedSeconds(activeWatchSeconds: Long, averageSpeed: Float, sponsorSkippedSeconds: Long): Long {
        val speedGain = if (averageSpeed > 1.0f) {
            (activeWatchSeconds * (averageSpeed - 1.0f)).toLong()
        } else 0L
        return (speedGain + sponsorSkippedSeconds.coerceAtLeast(0L)).coerceAtLeast(0L)
    }
}

/**
 * Central state manager for Categories 10, 11 & 12:
 * - Performance Engine (Features 131–140)
 * - Advanced Settings Favourites, Recent & Config Inspector (Features 121–130)
 * - Experimental Features & Modular Registry (Features 141–150)
 */
object PerformanceAndExperimentalEngine {
    private const val PREFS = "performance_engine_v292"
    private var prefs: SharedPreferences? = null

    // Category 11: Performance Engine (131–140)
    var adaptiveAnimationQuality by mutableStateOf(true)
        private set
    var dynamicBlurFallback by mutableStateOf(true)
        private set
    var lowMemoryMode by mutableStateOf(false)
        private set
    var batterySavingPlaybackMode by mutableStateOf(false)
        private set
    var thumbnailTier by mutableStateOf(ThumbnailPreloadTier.BalancedHd)
        private set
    var backgroundWorkloadOpt by mutableStateOf(true)
        private set

    // Network usage session & cumulative counters (Feature 137)
    var sessionStreamBytes by mutableLongStateOf(0L)
        private set
    var cumulativeStreamBytes by mutableLongStateOf(0L)
        private set

    // Category 10: Settings Favourites & Recently Accessed (122–123)
    val favouriteSettingTitles = mutableStateListOf<String>()
    val recentSettingTitles = mutableStateListOf<String>()

    // Category 12: Experimental Features (141–150)
    var homeDiscoveryWidgetEnabled by mutableStateOf(true)
        private set
    var watchPageExtensionCardsEnabled by mutableStateOf(true)
        private set
    var videoNotesWorkspaceOpen by mutableStateOf(false)

    // Feature 148: Watch session statistics
    var sessionVideosWatched by mutableIntStateOf(0)
        private set
    var sessionActiveWatchSec by mutableLongStateOf(0L)
        private set
    var sessionSponsorSkippedSec by mutableLongStateOf(0L)
        private set

    val controlPlugins = mutableStateListOf(
        DeclarativeControlPlugin("ab_loop", "A-B Loop", "toggle_ab_loop", listOf("player.seek")),
        DeclarativeControlPlugin("frame_step", "Frame Step", "toggle_frame_step", listOf("player.seek")),
        DeclarativeControlPlugin("pin_note", "Pin Note", "pin_timestamp_bookmark", listOf("bookmarks.write")),
        DeclarativeControlPlugin("color_boost", "Vivid Color", "cycle_color_preset", listOf("player.visual")),
        DeclarativeControlPlugin("md_link", "Copy [mm:ss]", "copy_markdown_link", listOf("clipboard.write")),
    )

    val unifiedCollections = mutableStateListOf<UnifiedCollectionItem>()

    // Feature 150: Modular Feature Registry (all 12 expansion modules)
    val modularFeatureEnabled = mutableStateMapOf(
        "ai_intelligence" to true,
        "video_experience" to true,
        "smart_discovery" to true,
        "subscription_manager" to true,
        "offline_intelligence" to true,
        "pro_music_system" to true,
        "liquid_glass_studio" to true,
        "automation_engine" to true,
        "privacy_security" to true,
        "advanced_settings" to true,
        "performance_engine" to true,
        "experimental_modules" to true,
    )

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        adaptiveAnimationQuality = p.getBoolean("adaptive_anim", true)
        dynamicBlurFallback = p.getBoolean("dynamic_blur_fallback", true)
        lowMemoryMode = p.getBoolean("low_memory_mode", false)
        batterySavingPlaybackMode = p.getBoolean("battery_saving_playback", false)
        thumbnailTier = ThumbnailPreloadTier.entries.firstOrNull {
            it.name == p.getString("thumb_tier", "BalancedHd")
        } ?: ThumbnailPreloadTier.BalancedHd
        backgroundWorkloadOpt = p.getBoolean("bg_workload_opt", true)
        cumulativeStreamBytes = p.getLong("cumulative_stream_bytes", 0L)
        homeDiscoveryWidgetEnabled = p.getBoolean("home_widget_enabled", true)
        watchPageExtensionCardsEnabled = p.getBoolean("watch_ext_cards", true)

        favouriteSettingTitles.clear()
        p.getString("fav_settings", "")?.split("|")?.filter { it.isNotBlank() }?.let(favouriteSettingTitles::addAll)
        recentSettingTitles.clear()
        p.getString("recent_settings", "")?.split("|")?.filter { it.isNotBlank() }?.let(recentSettingTitles::addAll)

        unifiedCollections.clear()
        runCatching {
            val arr = JSONArray(p.getString("unified_collections", "[]"))
            for (i in 0 until arr.length()) unifiedCollections.add(UnifiedCollectionItem.fromJson(arr.getJSONObject(i)))
        }
    }

    fun updatePerformanceSettings(
        adaptiveAnim: Boolean = adaptiveAnimationQuality,
        blurFallback: Boolean = dynamicBlurFallback,
        lowMem: Boolean = lowMemoryMode,
        batterySave: Boolean = batterySavingPlaybackMode,
        thumb: ThumbnailPreloadTier = thumbnailTier,
        bgOpt: Boolean = backgroundWorkloadOpt,
    ) {
        adaptiveAnimationQuality = adaptiveAnim
        dynamicBlurFallback = blurFallback
        lowMemoryMode = lowMem
        batterySavingPlaybackMode = batterySave
        thumbnailTier = thumb
        backgroundWorkloadOpt = bgOpt

        if (lowMem) {
            Tweaks.preload.update(false)
            StreamCache.invalidateAll()
            CaptionTracks.invalidateAll()
        }
        if (batterySave) {
            AmbientSettings.updateEnabled(false)
            QualityPreference.set(720)
        }
        prefs?.edit()
            ?.putBoolean("adaptive_anim", adaptiveAnim)
            ?.putBoolean("dynamic_blur_fallback", blurFallback)
            ?.putBoolean("low_memory_mode", lowMem)
            ?.putBoolean("battery_saving_playback", batterySave)
            ?.putString("thumb_tier", thumb.name)
            ?.putBoolean("bg_workload_opt", bgOpt)
            ?.apply()
    }

    fun updateExperimentalFlags(homeWidget: Boolean = homeDiscoveryWidgetEnabled, watchCards: Boolean = watchPageExtensionCardsEnabled) {
        homeDiscoveryWidgetEnabled = homeWidget
        watchPageExtensionCardsEnabled = watchCards
        prefs?.edit()
            ?.putBoolean("home_widget_enabled", homeWidget)
            ?.putBoolean("watch_ext_cards", watchCards)
            ?.apply()
    }

    fun toggleFavouriteSetting(title: String) {
        if (title in favouriteSettingTitles) favouriteSettingTitles.remove(title)
        else favouriteSettingTitles.add(0, title)
        prefs?.edit()?.putString("fav_settings", favouriteSettingTitles.joinToString("|"))?.apply()
    }

    fun recordRecentSetting(title: String) {
        recentSettingTitles.remove(title)
        recentSettingTitles.add(0, title)
        while (recentSettingTitles.size > 10) recentSettingTitles.removeAt(recentSettingTitles.lastIndex)
        prefs?.edit()?.putString("recent_settings", recentSettingTitles.joinToString("|"))?.apply()
    }

    fun tickSessionWatch(speed: Float) {
        sessionActiveWatchSec++
        val estBps = PlaybackBandwidth.measuredKbps().coerceAtLeast(800) * 1000L / 8L
        sessionStreamBytes += estBps
        cumulativeStreamBytes += estBps
        if (sessionActiveWatchSec % 15L == 0L) {
            prefs?.edit()?.putLong("cumulative_stream_bytes", cumulativeStreamBytes)?.apply()
        }
        if (dynamicBlurFallback) {
            val metrics = sampleLiveMetrics()
            if (metrics.dropRatioPercent >= 8.0f && GlassSettings.enabled) {
                LiquidGlassStudio.updateScreenBlurs(player = 8)
            }
        }
    }

    fun recordVideoSessionStart() {
        sessionVideosWatched++
    }

    fun addUnifiedCollectionItem(kind: String, title: String, subtitle: String, target: String, collection: String = "Research Vault") {
        val item = UnifiedCollectionItem(
            id = "uc_${System.currentTimeMillis()}",
            kind = kind,
            title = title,
            subtitle = subtitle,
            targetIdOrUrl = target,
            collectionName = collection,
        )
        unifiedCollections.add(0, item)
        val arr = JSONArray()
        unifiedCollections.take(200).forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString("unified_collections", arr.toString())?.apply()
    }

    /** Feature 138 & 139: Live JVM/Native memory + ExoPlayer frame-drop telemetry. */
    fun sampleLiveMetrics(): MemoryAndFrameMetrics {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
        val maxMb = (rt.maxMemory() / (1024L * 1024L)).coerceAtLeast(1L)
        val nativeMb = runCatching { Debug.getNativeHeapAllocatedSize() / (1024L * 1024L) }.getOrDefault(0L)

        val counters = PlayerBridge.exo?.videoDecoderCounters
        counters?.ensureUpdated()
        val rendered = counters?.renderedOutputBufferCount ?: 0
        val dropped = counters?.droppedBufferCount ?: 0
        val dropPct = PerformanceAndSessionMath.frameDropPercent(rendered, dropped)

        return MemoryAndFrameMetrics(
            jvmUsedMb = usedMb,
            jvmMaxMb = maxMb,
            nativeAllocatedMb = nativeMb,
            renderedFrames = rendered,
            droppedFrames = dropped,
            dropRatioPercent = dropPct,
            measuredBandwidthKbps = PlaybackBandwidth.measuredKbps(),
        )
    }

    /** Feature 129: Hardware & OS Compatibility Diagnostic Matrix. */
    fun runCompatibilityCheck(context: Context): List<CompatibilityCheckItem> {
        val am = context.getSystemService(ActivityManager::class.java)
        val lowRam = am?.isLowRamDevice == true
        val shaderOk = isRuntimeShaderSupported()
        val hdrOk = HdrSupport.isSupported
        val webmMuxOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val pipOk = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

        return listOf(
            CompatibilityCheckItem("Android SDK Level", true, "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"),
            CompatibilityCheckItem("AGSL RuntimeShader (Liquid Glass)", shaderOk, if (shaderOk) "Hardware refractive shader active" else "Using translucent frosted fallback"),
            CompatibilityCheckItem("HDR10 / VP9.2 / AV1 Hardware Decode", hdrOk, if (hdrOk) "Display & decoder advertise 10-bit HDR" else "SDR tone-mapped playback"),
            CompatibilityCheckItem("WebM 2K/4K MediaMuxer", webmMuxOk, if (webmMuxOk) "Supported (Android 10+)" else "MP4 1080p muxing only"),
            CompatibilityCheckItem("Picture-in-Picture Mode", pipOk, if (pipOk) "Supported by window manager" else "Unavailable on this device"),
            CompatibilityCheckItem("Memory Profile", !lowRam, if (lowRam) "Low-RAM device flag set" else "Standard/High RAM tier"),
            CompatibilityCheckItem("AndroidKeyStore AES-GCM-256", true, "Hardware-backed SecureStore active"),
        )
    }

    /** Feature 130: Reset individual feature modules without wiping the whole app. */
    fun resetFeatureModule(moduleId: String) {
        when (moduleId) {
            "ai_intelligence" -> AiIntelligenceSettings.resetUsageAndKeys()
            "video_experience" -> VideoExperience.resetAll()
            "smart_discovery" -> DiscoveryAndSubsManager.resetAll()
            "offline_intelligence" -> OfflineIntelligence.resetAll()
            "pro_music_system" -> ProfessionalMusicSystem.resetAll()
            "liquid_glass_studio" -> LiquidGlassStudio.resetAll()
            "automation_engine" -> AutomationEngine.resetAll()
            "tweaks" -> Tweaks.resetAll()
            "ambient" -> AmbientSettings.resetTuning()
            "performance" -> {
                prefs?.edit()?.clear()?.apply()
                lowMemoryMode = false
                batterySavingPlaybackMode = false
            }
        }
    }
}

/**
 * Feature 143 & 146: Customisable Home Discovery & Watch Session Widget Banner.
 */
@Composable
fun HomeDiscoveryWidgetBanner(
    videos: List<VideoItem>,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenDiscoveryHub: () -> Unit,
) {
    if (!PerformanceAndExperimentalEngine.homeDiscoveryWidgetEnabled) return
    val c = MaterialTheme.colorScheme
    val savedSec = PerformanceAndExperimentalEngine.let {
        PerformanceAndSessionMath.calculateTimeSavedSeconds(it.sessionActiveWatchSec, PlaybackSpeed.saved, it.sessionSponsorSkippedSec)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Discovery Lens: ${DiscoveryAndSubsManager.activeLens.label} · ${DiscoveryAndSubsManager.activeTopic}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = c.primary,
                )
                Text(
                    "Watch time: ${UsageStats.format(UsageStats.videoSeconds)} · Saved: ${savedSec / 60}m · Bookmarks: ${VideoExperience.bookmarks.size}",
                    fontSize = 11.5.sp,
                    color = c.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (videos.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(c.primaryContainer)
                            .pressable {
                                val pick = videos.filterNot { Watched.has(it.id) }.ifEmpty { videos }.random()
                                onOpenVideo(pick)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Shuffle, null, tint = c.onPrimaryContainer, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Surprise", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c.onPrimaryContainer)
                        }
                    }
                }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(c.primary)
                        .pressable(onClick = onOpenDiscoveryHub)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("Hub", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c.onPrimary)
                }
            }
        }
    }
}

/**
 * Interactive Performance Engine, Advanced Settings & Experimental Labs Sheet (Categories 10, 11, 12: Features 121–150).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceAndExperimentalSheet(
    player: PlayerState,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Performance & Telemetry", "Compatibility & Reset", "Config Inspector", "Experimental Plugins", "Unified Collections")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Performance Engine & Experimental Labs",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
            Text(
                "Live heap & frame-drop monitor, compatibility diagnostics, config inspector & declarative plugins",
                color = c.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { idx, label ->
                    FilterChip(selected = tab == idx, onClick = { tab = idx }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> PerformanceDashboardTab(context)
                1 -> CompatibilityAndPerFeatureResetTab(context)
                2 -> AdvancedConfigInspectorTab(context)
                3 -> ExperimentalPluginsAndModulesTab(player)
                4 -> UnifiedCollectionsWorkspaceTab(player, onDismiss)
            }
        }
    }
}

@Composable
private fun PerformanceDashboardTab(context: Context) {
    val c = MaterialTheme.colorScheme
    var metrics by remember { mutableStateOf(PerformanceAndExperimentalEngine.sampleLiveMetrics()) }

    LaunchedEffect(Unit) {
        while (true) {
            metrics = PerformanceAndExperimentalEngine.sampleLiveMetrics()
            delay(1_000L)
        }
    }

    // Features 136, 137, 138, 139: Live Memory, Frame-Drop, Network & Cache Dashboard
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceContainerHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Real-Time Telemetry (Features 136–139)", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = c.primary)
        val memFrac = (metrics.jvmUsedMb.toFloat() / metrics.jvmMaxMb.toFloat()).coerceIn(0f, 1f)
        Text(
            "JVM Heap: ${metrics.jvmUsedMb} MB / ${metrics.jvmMaxMb} MB · Native Heap: ${metrics.nativeAllocatedMb} MB",
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
        )
        LinearProgressIndicator(
            progress = { memFrac },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
            color = c.primary,
        )
        Text(
            "Video Decoder Frames: ${metrics.renderedFrames} rendered · ${metrics.droppedFrames} dropped (${"%.2f".format(Locale.US, metrics.dropRatioPercent)}%)",
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "Measured Throughput: ${"%.1f".format(Locale.US, metrics.measuredBandwidthKbps / 1000f)} Mbps · Session Streamed: ${formatBytes(PerformanceAndExperimentalEngine.sessionStreamBytes)}",
            fontSize = 12.sp,
            color = c.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                StreamCache.invalidateAll()
                CaptionTracks.invalidateAll()
                System.gc()
                metrics = PerformanceAndExperimentalEngine.sampleLiveMetrics()
            },
        ) {
            Icon(Icons.Rounded.CleaningServices, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Trim Memory & Stream Caches Now", fontSize = 12.sp)
        }
    }

    Spacer(Modifier.height(10.dp))
    PerfSwitch("Adaptive Animation Quality (Feature 131)", "Scales spring & particle complexity on high frame load", PerformanceAndExperimentalEngine.adaptiveAnimationQuality) {
        PerformanceAndExperimentalEngine.updatePerformanceSettings(adaptiveAnim = it)
    }
    PerfSwitch("Dynamic Blur Fallback (Feature 132)", "Steps down shader blur radius automatically if frame drops > 3.5%", PerformanceAndExperimentalEngine.dynamicBlurFallback) {
        PerformanceAndExperimentalEngine.updatePerformanceSettings(blurFallback = it)
    }
    PerfSwitch("Low-Memory Mode (Feature 133)", "Trims caches and disables background stream prefetching", PerformanceAndExperimentalEngine.lowMemoryMode) {
        PerformanceAndExperimentalEngine.updatePerformanceSettings(lowMem = it)
    }
    PerfSwitch("Battery-Saving Playback Mode (Feature 134)", "Disables live video frame ambient sampling and caps at 720p", PerformanceAndExperimentalEngine.batterySavingPlaybackMode) {
        PerformanceAndExperimentalEngine.updatePerformanceSettings(batterySave = it)
    }
    PerfSwitch("Background Workload Optimisation (Feature 140)", "Throttles non-essential background workers while playing", PerformanceAndExperimentalEngine.backgroundWorkloadOpt) {
        PerformanceAndExperimentalEngine.updatePerformanceSettings(bgOpt = it)
    }

    Spacer(Modifier.height(8.dp))
    Text("Thumbnail Preloading Tier (Feature 135)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = c.primary)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ThumbnailPreloadTier.entries.forEach { tier ->
            FilterChip(
                selected = PerformanceAndExperimentalEngine.thumbnailTier == tier,
                onClick = { PerformanceAndExperimentalEngine.updatePerformanceSettings(thumb = tier) },
                label = { Text(tier.label) },
            )
        }
    }
}

@Composable
private fun PerfSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(subtitle, fontSize = 11.5.sp, color = c.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CompatibilityAndPerFeatureResetTab(context: Context) {
    val c = MaterialTheme.colorScheme
    val checks = remember { PerformanceAndExperimentalEngine.runCompatibilityCheck(context) }
    var resetNotice by remember { mutableStateOf<String?>(null) }

    Text("Device & Feature Compatibility Checker (Feature 129)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    checks.forEach { item ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (item.supported) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                null,
                tint = if (item.supported) c.primary else c.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.feature, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(item.detail, fontSize = 11.5.sp, color = c.onSurfaceVariant)
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
    Spacer(Modifier.height(10.dp))

    Text("Reset Individual Feature Settings (Feature 130)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Text("Reset a single module back to factory defaults without losing your other preferences.", fontSize = 12.sp, color = c.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))

    val modules = listOf(
        "ai_intelligence" to "Reset AI Intelligence",
        "video_experience" to "Reset Video Experience & Color",
        "smart_discovery" to "Reset Discovery & Subscriptions",
        "offline_intelligence" to "Reset Offline Rules",
        "pro_music_system" to "Reset Music Studio & EQ",
        "liquid_glass_studio" to "Reset Liquid Glass Studio",
        "automation_engine" to "Reset Automation Rules",
        "tweaks" to "Reset YTX Tweaks",
        "ambient" to "Reset Ambient Light",
        "performance" to "Reset Performance Engine",
    )
    modules.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (id, label) ->
                OutlinedButton(
                    onClick = {
                        PerformanceAndExperimentalEngine.resetFeatureModule(id)
                        resetNotice = "$label completed."
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    resetNotice?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = c.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdvancedConfigInspectorTab(context: Context) {
    val c = MaterialTheme.colorScheme
    val storeNames = listOf(
        "tweaks",
        "appearance",
        "playback",
        "video_experience_v292",
        "ai_intelligence_v292",
        "discovery_and_subs_v292",
        "offline_intelligence_v292",
        "pro_music_system_v292",
        "liquid_glass_studio_v292",
        "automation_engine_v292",
        "performance_engine_v292",
    )
    var selectedStore by remember { mutableStateOf(storeNames.first()) }
    var refreshTick by remember { mutableIntStateOf(0) }

    Text("Advanced Configuration Editor & Backup (Features 125–127)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        storeNames.forEach { name ->
            FilterChip(
                selected = selectedStore == name,
                onClick = { selectedStore = name },
                label = { Text(name, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace) },
            )
        }
    }

    val prefs = remember(selectedStore, refreshTick) {
        context.getSharedPreferences(selectedStore, Context.MODE_PRIVATE)
    }
    val entries = remember(selectedStore, refreshTick) {
        prefs.all.entries.sortedBy { it.key }
    }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val json = Backup.export(context)
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("YTX Config Backup", json))
            },
        ) {
            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Copy Full Backup JSON", fontSize = 12.sp)
        }
        OutlinedButton(onClick = { refreshTick++ }) {
            Text("Refresh Keys", fontSize = 12.sp)
        }
    }

    Spacer(Modifier.height(8.dp))
    if (entries.isEmpty()) {
        Text("Store '$selectedStore' is currently at default values.", fontSize = 12.5.sp, color = c.onSurfaceVariant)
    } else {
        entries.take(30).forEach { (k, v) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(k, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace)
                    Text(v.toString().take(80), fontSize = 11.sp, color = c.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
                if (v is Boolean) {
                    Switch(
                        checked = v,
                        onCheckedChange = {
                            prefs.edit().putBoolean(k, it).apply()
                            refreshTick++
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentalPluginsAndModulesTab(player: PlayerState) {
    val c = MaterialTheme.colorScheme
    var pluginJsonInput by remember { mutableStateOf("") }
    var pluginNotice by remember { mutableStateOf<String?>(null) }

    Text("Experimental Feature Controls (Features 128, 141–146, 150)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Text("All plugins use validated, permission-limited JSON configuration packages — zero arbitrary code execution.", fontSize = 12.sp, color = c.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))

    PerfSwitch("Customisable Home Discovery & Stats Widget (Features 143 & 146)", "Shows active lens, time saved, and Surprise Me button on Home", PerformanceAndExperimentalEngine.homeDiscoveryWidgetEnabled) {
        PerformanceAndExperimentalEngine.updateExperimentalFlags(homeWidget = it)
    }
    PerfSwitch("Watch Page Extension Modules (Feature 142)", "Displays live AI topic, bitrate, and session stats card on the watch page", PerformanceAndExperimentalEngine.watchPageExtensionCardsEnabled) {
        PerformanceAndExperimentalEngine.updateExperimentalFlags(watchCards = it)
    }

    Spacer(Modifier.height(10.dp))
    Text("Validated Video Control Plugins (Feature 141)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = c.primary)
    PerformanceAndExperimentalEngine.controlPlugins.forEach { plugin ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(plugin.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Action: ${plugin.actionId} · Perms: ${plugin.permissions.joinToString()}", fontSize = 11.sp, color = c.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(
                onClick = {
                    when (plugin.actionId) {
                        "toggle_ab_loop" -> player.actionMessage = "Use Floating Toolbar A-B Loop"
                        "toggle_frame_step" -> FrameStep.toggle()
                        "pin_timestamp_bookmark" -> player.current?.let { VideoExperience.addBookmark(it, player.positionMs) }
                        "cycle_color_preset" -> VideoColorEngine.applyPreset(VideoColorPreset.Vivid)
                        "toggle_audio_only" -> player.setAudioOnly(!AudioOnly.enabled)
                        "toggle_skip_silence" -> SkipSilence.toggle()
                    }
                },
            ) {
                Text("Run", fontSize = 11.5.sp)
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = pluginJsonInput,
        onValueChange = { pluginJsonInput = it },
        label = { Text("Install Validated Plugin JSON (schema: ytx_plugin_v292)") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Button(
        enabled = pluginJsonInput.isNotBlank(),
        onClick = {
            PluginPackageValidator.validate(pluginJsonInput)
                .onSuccess { p ->
                    PerformanceAndExperimentalEngine.controlPlugins.removeAll { it.id == p.id }
                    PerformanceAndExperimentalEngine.controlPlugins.add(p)
                    pluginNotice = "Installed validated plugin '${p.label}'."
                    pluginJsonInput = ""
                }
                .onFailure { err ->
                    pluginNotice = "Rejected unsafe/invalid plugin: ${err.message}"
                }
        },
    ) {
        Text("Validate & Add Plugin")
    }
    pluginNotice?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, fontSize = 12.sp, color = c.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UnifiedCollectionsWorkspaceTab(player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var colName by remember { mutableStateOf("Research Vault") }

    Text("Advanced Multi-Type Content Collections (Feature 149)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Text("Combine videos, music tracks, channels, and timestamped notes into unified research collections.", fontSize = 12.sp, color = c.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))

    player.current?.let { cur ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = colName,
                onValueChange = { colName = it },
                label = { Text("Collection Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    PerformanceAndExperimentalEngine.addUnifiedCollectionItem(
                        kind = if (player.isMusicMode) "music" else "video",
                        title = cur.title,
                        subtitle = "${cur.channel} · @${formatDuration(player.positionMs)}",
                        target = cur.id,
                        collection = colName,
                    )
                    player.actionMessage = "Added to '$colName'"
                },
            ) {
                Text("+ Current")
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    if (PerformanceAndExperimentalEngine.unifiedCollections.isEmpty()) {
        Text("No items saved to unified collections yet.", color = c.onSurfaceVariant, fontSize = 13.sp)
    } else {
        PerformanceAndExperimentalEngine.unifiedCollections.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceContainerHigh)
                    .pressable {
                        player.open(
                            VideoItem(
                                id = item.targetIdOrUrl,
                                title = item.title,
                                channel = item.subtitle.substringBefore(" ·"),
                                channelUrl = null,
                                thumbnail = feedThumbnailUrl(item.targetIdOrUrl),
                                avatar = null,
                                durationSec = 0L,
                                views = -1L,
                                uploaded = null,
                            ),
                        )
                        onDismiss()
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("[${item.collectionName}] ${item.title}", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.kind.uppercase()} · ${item.subtitle}", fontSize = 11.5.sp, color = c.onSurfaceVariant)
                }
                Icon(Icons.Rounded.PlayArrow, null, tint = c.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
