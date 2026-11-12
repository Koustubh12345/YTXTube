package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================================
// CATEGORY 2: ADVANCED VIDEO EXPERIENCE (Features 16–30)
// ============================================================================

/** Feature 26: Video brightness presets. */
enum class VideoBrightnessPreset(val label: String, val brightnessOffset: Float, val windowBrightness: Float) {
    Default("Default", 0f, -1f),
    NightDim("Night Dim", -0.22f, 0.15f),
    IndoorComfort("Indoor", -0.05f, 0.45f),
    CinemaRoom("Cinema", -0.08f, 0.35f),
    DaylightBoost("Daylight Boost", 0.18f, 0.95f),
}

/** Feature 24: Video colour adjustment presets. */
enum class VideoColorPreset(
    val label: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val warmth: Float,
) {
    Natural("Natural", 1.0f, 1.0f, 0.0f, 0.0f),
    Vivid("Vivid Boost", 1.38f, 1.12f, 0.02f, 0.03f),
    CinemaWarm("Cinema Warm", 1.14f, 1.15f, -0.02f, 0.12f),
    CoolCrisp("Cool Crisp", 1.10f, 1.14f, 0.01f, -0.12f),
    HighContrast("High Contrast", 1.05f, 1.35f, -0.03f, 0.0f),
    SoftPastel("Soft Pastel", 0.82f, 0.90f, 0.04f, 0.04f),
    Monochrome("Monochrome", 0.0f, 1.18f, 0.0f, 0.0f),
    Custom("Custom", 1.0f, 1.0f, 0.0f, 0.0f),
}

/**
 * Pure math for Feature 24, 25, 26: builds a 20-element 4x5 RGBA ColorMatrix array from
 * saturation (0..2.5), contrast (0.5..2.0), brightness (-0.5..0.5), and warmth (-0.3..0.3).
 * Kept free of Android UI dependencies so it can be unit tested deterministically.
 */
object VideoColorMath {
    fun buildMatrixValues(
        saturation: Float,
        contrast: Float,
        brightness: Float,
        warmth: Float,
    ): FloatArray {
        val sat = saturation.coerceIn(0f, 2.5f)
        val con = contrast.coerceIn(0.4f, 2.2f)
        val bri = brightness.coerceIn(-0.5f, 0.5f) * 255f
        val w = warmth.coerceIn(-0.4f, 0.4f)

        // Rec. 709 luma weights for saturation matrix
        val invSat = 1f - sat
        val rLum = 0.2126f * invSat
        val gLum = 0.7152f * invSat
        val bLum = 0.0722f * invSat

        val rWarm = 1f + w * 0.18f
        val gWarm = 1f + w * 0.03f
        val bWarm = 1f - w * 0.18f

        val translate = 128f * (1f - con) + bri

        return floatArrayOf(
            (rLum + sat) * con * rWarm, gLum * con * rWarm, bLum * con * rWarm, 0f, translate,
            rLum * con * gWarm, (gLum + sat) * con * gWarm, bLum * con * gWarm, 0f, translate,
            rLum * con * bWarm, gLum * con * bWarm, (bLum + sat) * con * bWarm, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun isIdentity(saturation: Float, contrast: Float, brightness: Float, warmth: Float): Boolean =
        abs(saturation - 1f) < 0.005f &&
            abs(contrast - 1f) < 0.005f &&
            abs(brightness) < 0.005f &&
            abs(warmth) < 0.005f
}

/**
 * Features 24, 25, 26: Live video colour, saturation, contrast, warmth, and brightness engine.
 */
object VideoColorEngine {
    private const val PREFS = "video_color_engine"
    private var prefs: SharedPreferences? = null

    var preset by mutableStateOf(VideoColorPreset.Natural)
        private set
    var saturation by mutableFloatStateOf(1.0f)
        private set
    var contrast by mutableFloatStateOf(1.0f)
        private set
    var brightness by mutableFloatStateOf(0.0f)
        private set
    var warmth by mutableFloatStateOf(0.0f)
        private set
    var brightnessPreset by mutableStateOf(VideoBrightnessPreset.Default)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        preset = VideoColorPreset.entries.firstOrNull { it.name == p.getString("preset", "Natural") }
            ?: VideoColorPreset.Natural
        saturation = p.getFloat("saturation", preset.saturation).coerceIn(0f, 2.5f)
        contrast = p.getFloat("contrast", preset.contrast).coerceIn(0.5f, 2.0f)
        brightness = p.getFloat("brightness", preset.brightness).coerceIn(-0.5f, 0.5f)
        warmth = p.getFloat("warmth", preset.warmth).coerceIn(-0.3f, 0.3f)
        brightnessPreset = VideoBrightnessPreset.entries.firstOrNull {
            it.name == p.getString("brightness_preset", "Default")
        } ?: VideoBrightnessPreset.Default
    }

    fun applyPreset(target: VideoColorPreset) {
        preset = target
        if (target != VideoColorPreset.Custom) {
            saturation = target.saturation
            contrast = target.contrast
            brightness = target.brightness
            warmth = target.warmth
        }
        save()
    }

    fun updateCustom(
        newSaturation: Float = saturation,
        newContrast: Float = contrast,
        newBrightness: Float = brightness,
        newWarmth: Float = warmth,
    ) {
        preset = VideoColorPreset.Custom
        saturation = newSaturation.coerceIn(0f, 2.5f)
        contrast = newContrast.coerceIn(0.5f, 2.0f)
        brightness = newBrightness.coerceIn(-0.5f, 0.5f)
        warmth = newWarmth.coerceIn(-0.3f, 0.3f)
        save()
    }

    fun applyBrightnessPreset(target: VideoBrightnessPreset) {
        brightnessPreset = target
        prefs?.edit()?.putString("brightness_preset", target.name)?.apply()
    }

    fun reset() {
        applyPreset(VideoColorPreset.Natural)
        applyBrightnessPreset(VideoBrightnessPreset.Default)
    }

    val effectiveBrightness: Float
        get() = (brightness + brightnessPreset.brightnessOffset).coerceIn(-0.5f, 0.5f)

    val isActive: Boolean
        get() = !VideoColorMath.isIdentity(saturation, contrast, effectiveBrightness, warmth)

    fun activeColorFilter(): ColorFilter? {
        if (!isActive) return null
        val values = VideoColorMath.buildMatrixValues(saturation, contrast, effectiveBrightness, warmth)
        return ColorFilter.colorMatrix(ColorMatrix(values))
    }

    private fun save() {
        prefs?.edit()
            ?.putString("preset", preset.name)
            ?.putFloat("saturation", saturation)
            ?.putFloat("contrast", contrast)
            ?.putFloat("brightness", brightness)
            ?.putFloat("warmth", warmth)
            ?.apply()
    }
}

/** Feature 27: Custom playback control layouts. */
enum class PlayerControlLayout(val label: String, val description: String) {
    Standard("Standard", "Classic centered transport controls and bottom time row"),
    Minimal("Minimal", "Clean center play button only; swipe or double-tap to seek"),
    Cinema("Cinema Bottom", "All transport and quick actions docked in a sleek bottom glass bar"),
    LeftHanded("Left-Handed", "Primary actions and settings mirrored to the left edge for one-hand reach"),
    ProHud("Dense Pro HUD", "Shows live codec/fps pill, frame-step trigger, AB-loop, and bookmark bar"),
}

/** Feature 17: Picture-in-Picture improvements & configuration. */
enum class PipAspectMode(val label: String, val fixedRatio: Float?) {
    MatchVideo("Match video aspect", null),
    Widescreen16x9("16:9 Widescreen", 16f / 9f),
    Vertical9x16("9:16 Vertical", 9f / 16f),
    Classic4x3("4:3 Compact", 4f / 3f),
    Ultrawide21x9("21:9 Cinema", 21f / 9f),
}

/** Feature 18 & 144: Advanced and User-Created Playback Presets. */
@Immutable
data class PlaybackPreset(
    val id: String,
    val name: String,
    val speed: Float,
    val skipSilence: Boolean,
    val autoCaptions: Boolean,
    val audioOnly: Boolean,
    val eqPreset: AudioEffects.Preset,
    val boostIndex: Int,
    val colorPreset: VideoColorPreset,
    val maxQualityHeight: Int?,
    val skipFirstSec: Int = 0,
    val builtIn: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("speed", speed.toDouble())
        .put("skipSilence", skipSilence)
        .put("autoCaptions", autoCaptions)
        .put("audioOnly", audioOnly)
        .put("eqPreset", eqPreset.name)
        .put("boostIndex", boostIndex)
        .put("colorPreset", colorPreset.name)
        .put("maxQualityHeight", maxQualityHeight ?: 0)
        .put("skipFirstSec", skipFirstSec)
        .put("builtIn", builtIn)

    companion object {
        fun fromJson(o: JSONObject): PlaybackPreset = PlaybackPreset(
            id = o.optString("id", "custom_${System.currentTimeMillis()}"),
            name = o.optString("name", "Custom Preset").take(40),
            speed = o.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 3.0f),
            skipSilence = o.optBoolean("skipSilence", false),
            autoCaptions = o.optBoolean("autoCaptions", false),
            audioOnly = o.optBoolean("audioOnly", false),
            eqPreset = AudioEffects.Preset.entries.firstOrNull { it.name == o.optString("eqPreset") }
                ?: AudioEffects.Preset.Flat,
            boostIndex = o.optInt("boostIndex", 0).coerceIn(0, AudioEffects.boostSteps.lastIndex),
            colorPreset = VideoColorPreset.entries.firstOrNull { it.name == o.optString("colorPreset") }
                ?: VideoColorPreset.Natural,
            maxQualityHeight = o.optInt("maxQualityHeight", 0).takeIf { it > 0 },
            skipFirstSec = o.optInt("skipFirstSec", 0).coerceIn(0, 120),
            builtIn = o.optBoolean("builtIn", false),
        )
    }
}

/** Feature 20: Per-channel playback preferences. */
@Immutable
data class ChannelPlaybackPref(
    val channelKey: String,
    val channelName: String,
    val speed: Float = 1.0f,
    val preferredHeight: Int? = null,
    val skipFirstSec: Int = 0,
    val autoCaptions: Boolean = false,
    val audioOnly: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("channelKey", channelKey)
        .put("channelName", channelName)
        .put("speed", speed.toDouble())
        .put("preferredHeight", preferredHeight ?: 0)
        .put("skipFirstSec", skipFirstSec)
        .put("autoCaptions", autoCaptions)
        .put("audioOnly", audioOnly)

    companion object {
        fun fromJson(o: JSONObject): ChannelPlaybackPref = ChannelPlaybackPref(
            channelKey = o.optString("channelKey"),
            channelName = o.optString("channelName"),
            speed = o.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 3.0f),
            preferredHeight = o.optInt("preferredHeight", 0).takeIf { it > 0 },
            skipFirstSec = o.optInt("skipFirstSec", 0).coerceIn(0, 180),
            autoCaptions = o.optBoolean("autoCaptions", false),
            audioOnly = o.optBoolean("audioOnly", false),
        )
    }
}

/** Feature 22: Network-aware playback profiles. */
enum class NetworkProfileKind(
    val label: String,
    val wifiMaxHeight: Int?,
    val cellularMaxHeight: Int?,
    val preloadOnWifi: Boolean,
    val preloadOnCellular: Boolean,
    val lowSignalAudioFallback: Boolean,
) {
    Adaptive("Smart Adaptive", null, 720, true, false, false),
    MaxQuality("Unrestricted 4K HDR", 2160, 1440, true, true, false),
    Balanced("Balanced HD", 1080, 720, true, false, false),
    CommuterSaver("Commuter Data Saver", 720, 360, false, false, true),
    AudioFirst("Ultra Low Bandwidth", 480, 240, false, false, true),
}

/** Feature 29 & 30: Video Bookmarks & Timestamped Annotations. */
@Immutable
data class VideoBookmarkItem(
    val id: String,
    val videoId: String,
    val videoTitle: String,
    val channel: String,
    val thumbnail: String?,
    val positionMs: Long,
    val collection: String,
    val note: String,
    val createdAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("videoId", videoId)
        .put("videoTitle", videoTitle)
        .put("channel", channel)
        .put("thumbnail", thumbnail.orEmpty())
        .put("positionMs", positionMs)
        .put("collection", collection)
        .put("note", note)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject): VideoBookmarkItem = VideoBookmarkItem(
            id = o.optString("id"),
            videoId = o.optString("videoId"),
            videoTitle = o.optString("videoTitle"),
            channel = o.optString("channel"),
            thumbnail = o.optString("thumbnail").ifBlank { null },
            positionMs = o.optLong("positionMs", 0L).coerceAtLeast(0L),
            collection = o.optString("collection", "Highlights").ifBlank { "Highlights" },
            note = o.optString("note", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

/** Feature 16: Multi-video comparison model & scoring. */
@Immutable
data class VideoComparisonResult(
    val primary: VideoItem,
    val secondary: VideoItem,
    val durationDiffSec: Long,
    val viewsRatio: Float,
    val primaryEngagementHint: String,
    val secondaryEngagementHint: String,
    val sharedKeywords: List<String>,
    val recommendation: String,
)

object VideoComparisonAnalyzer {
    fun compare(primary: VideoItem, secondary: VideoItem): VideoComparisonResult {
        val durDiff = primary.durationSec - secondary.durationSec
        val pViews = primary.views.coerceAtLeast(1L)
        val sViews = secondary.views.coerceAtLeast(1L)
        val ratio = pViews.toFloat() / sViews.toFloat()
        val pTokens = DiscoveryRanker.tokens(primary.title).toSet()
        val sTokens = DiscoveryRanker.tokens(secondary.title).toSet()
        val shared = pTokens.intersect(sTokens).toList()

        val pHint = when {
            primary.durationSec >= 1200 -> "Deep-dive long-form (${primary.durationSec / 60} min)"
            primary.durationSec in 1..240 -> "Concise overview (${primary.durationSec / 60}m ${primary.durationSec % 60}s)"
            else -> "Standard length (${primary.durationSec / 60} min)"
        }
        val sHint = when {
            secondary.durationSec >= 1200 -> "Deep-dive long-form (${secondary.durationSec / 60} min)"
            secondary.durationSec in 1..240 -> "Concise overview (${secondary.durationSec / 60}m ${secondary.durationSec % 60}s)"
            else -> "Standard length (${secondary.durationSec / 60} min)"
        }
        val rec = when {
            primary.durationSec in 60..420 && secondary.durationSec > 900 ->
                "Watch '${primary.title.take(28)}' first for a quick summary, then '${secondary.title.take(28)}' for depth."
            secondary.durationSec in 60..420 && primary.durationSec > 900 ->
                "'${secondary.title.take(28)}' is ${maxOf(1L, primary.durationSec / secondary.durationSec.coerceAtLeast(1L))}× shorter for a fast overview."
            primary.views > secondary.views * 2 ->
                "'${primary.title.take(28)}' is the broader community benchmark (${formatCount(primary.views)} views)."
            else ->
                "Both cover similar scope (${shared.size} shared topic keywords); use A/B switch to compare delivery."
        }
        return VideoComparisonResult(
            primary = primary,
            secondary = secondary,
            durationDiffSec = durDiff,
            viewsRatio = ratio,
            primaryEngagementHint = pHint,
            secondaryEngagementHint = sHint,
            sharedKeywords = shared,
            recommendation = rec,
        )
    }
}

/**
 * Central state manager for Category 2: Advanced Video Experience (Features 16–30).
 */
object VideoExperience {
    private const val PREFS = "video_experience_v292"
    private var prefs: SharedPreferences? = null

    val builtInPresets = listOf(
        PlaybackPreset(
            id = "standard",
            name = "Standard",
            speed = 1.0f,
            skipSilence = false,
            autoCaptions = false,
            audioOnly = false,
            eqPreset = AudioEffects.Preset.Flat,
            boostIndex = 0,
            colorPreset = VideoColorPreset.Natural,
            maxQualityHeight = null,
        ),
        PlaybackPreset(
            id = "lecture",
            name = "Lecture & Study",
            speed = 1.5f,
            skipSilence = true,
            autoCaptions = true,
            audioOnly = false,
            eqPreset = AudioEffects.Preset.Vocal,
            boostIndex = 1,
            colorPreset = VideoColorPreset.CoolCrisp,
            maxQualityHeight = 1080,
        ),
        PlaybackPreset(
            id = "cinema",
            name = "Cinema HDR",
            speed = 1.0f,
            skipSilence = false,
            autoCaptions = false,
            audioOnly = false,
            eqPreset = AudioEffects.Preset.Cinema,
            boostIndex = 1,
            colorPreset = VideoColorPreset.CinemaWarm,
            maxQualityHeight = 2160,
        ),
        PlaybackPreset(
            id = "podcast",
            name = "Podcast Audio",
            speed = 1.25f,
            skipSilence = true,
            autoCaptions = false,
            audioOnly = true,
            eqPreset = AudioEffects.Preset.Vocal,
            boostIndex = 2,
            colorPreset = VideoColorPreset.Natural,
            maxQualityHeight = 360,
        ),
        PlaybackPreset(
            id = "night",
            name = "Late Night",
            speed = 1.0f,
            skipSilence = false,
            autoCaptions = true,
            audioOnly = false,
            eqPreset = AudioEffects.Preset.Vocal,
            boostIndex = 0,
            colorPreset = VideoColorPreset.SoftPastel,
            maxQualityHeight = 720,
        ),
        PlaybackPreset(
            id = "speedrun",
            name = "Speed Run 2×",
            speed = 2.0f,
            skipSilence = true,
            autoCaptions = true,
            audioOnly = false,
            eqPreset = AudioEffects.Preset.Vocal,
            boostIndex = 1,
            colorPreset = VideoColorPreset.Natural,
            maxQualityHeight = 720,
            skipFirstSec = 10,
        ),
    )

    var activePresetId by mutableStateOf("standard")
        private set
    val customPresets = mutableStateListOf<PlaybackPreset>()

    var controlLayout by mutableStateOf(PlayerControlLayout.Standard)
        private set
    var floatingToolbarEnabled by mutableStateOf(false)
        private set

    // Feature 17: PiP settings
    var pipAutoEnter by mutableStateOf(true)
        private set
    var pipAspectMode by mutableStateOf(PipAspectMode.MatchVideo)
        private set
    var pipPauseOnClose by mutableStateOf(true)
        private set

    // Feature 19: Custom playback shortcuts
    var doubleTapSeekSec by mutableIntStateOf(10)
        private set
    var twoFingerTapAction by mutableStateOf("Bookmark")
        private set

    // Feature 20: Per-channel playback preferences
    val channelPrefs = mutableStateListOf<ChannelPlaybackPref>()

    // Feature 22: Network-aware playback profile
    var networkProfile by mutableStateOf(NetworkProfileKind.Adaptive)
        private set

    // Feature 29 & 30: Bookmarks & Timestamped Annotations
    val bookmarks = mutableStateListOf<VideoBookmarkItem>()
    val collections = mutableStateListOf("Highlights", "Study Notes", "Tutorials", "Watch Again", "Favorite Moments")

    // Feature 16: Multi-video comparison state
    var comparisonCandidate by mutableStateOf<VideoItem?>(null)
    private val comparisonPositions = HashMap<String, Long>()

    // AB-Loop state (used by Floating Toolbar & Experimental Control Plugin)
    var abLoopStartMs by mutableStateOf<Long?>(null)
    var abLoopEndMs by mutableStateOf<Long?>(null)

    fun init(context: Context) {
        VideoColorEngine.init(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        activePresetId = p.getString("active_preset", "standard") ?: "standard"
        controlLayout = PlayerControlLayout.entries.firstOrNull {
            it.name == p.getString("control_layout", "Standard")
        } ?: PlayerControlLayout.Standard
        floatingToolbarEnabled = p.getBoolean("floating_toolbar", false)
        pipAutoEnter = p.getBoolean("pip_auto_enter", true)
        pipAspectMode = PipAspectMode.entries.firstOrNull {
            it.name == p.getString("pip_aspect_mode", "MatchVideo")
        } ?: PipAspectMode.MatchVideo
        pipPauseOnClose = p.getBoolean("pip_pause_on_close", true)
        doubleTapSeekSec = p.getInt("double_tap_seek_sec", Tweaks.seekStepSec.value)
        twoFingerTapAction = p.getString("two_finger_action", "Bookmark") ?: "Bookmark"
        networkProfile = NetworkProfileKind.entries.firstOrNull {
            it.name == p.getString("network_profile", "Adaptive")
        } ?: NetworkProfileKind.Adaptive

        customPresets.clear()
        runCatching {
            val arr = JSONArray(p.getString("custom_presets", "[]"))
            for (i in 0 until arr.length()) {
                customPresets.add(PlaybackPreset.fromJson(arr.getJSONObject(i)))
            }
        }

        channelPrefs.clear()
        runCatching {
            val arr = JSONArray(p.getString("channel_prefs", "[]"))
            for (i in 0 until arr.length()) {
                channelPrefs.add(ChannelPlaybackPref.fromJson(arr.getJSONObject(i)))
            }
        }

        bookmarks.clear()
        runCatching {
            val arr = JSONArray(p.getString("bookmarks", "[]"))
            for (i in 0 until arr.length()) {
                bookmarks.add(VideoBookmarkItem.fromJson(arr.getJSONObject(i)))
            }
        }

        runCatching {
            val cols = p.getString("collections", null)
            if (!cols.isNullOrBlank()) {
                val parsed = cols.split("|").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                if (parsed.isNotEmpty()) {
                    collections.clear()
                    collections.addAll(parsed)
                }
            }
        }
    }

    val allPresets: List<PlaybackPreset>
        get() = builtInPresets + customPresets

    fun applyPreset(preset: PlaybackPreset, player: PlayerState?) {
        activePresetId = preset.id
        prefs?.edit()?.putString("active_preset", preset.id)?.apply()
        Tweaks.defaultSpeedX100.update((preset.speed * 100).roundToInt())
        if (SkipSilence.enabled != preset.skipSilence) SkipSilence.toggle()
        Tweaks.autoCaptions.update(preset.autoCaptions)
        AudioEffects.updatePreset(preset.eqPreset)
        AudioEffects.setBoost(preset.boostIndex)
        VideoColorEngine.applyPreset(preset.colorPreset)
        if (preset.skipFirstSec > 0) Tweaks.skipFirstSec.update(preset.skipFirstSec)
        player?.let { state ->
            state.commitSpeed(preset.speed)
            state.setAudioOnly(preset.audioOnly)
            state.setCaptions(preset.autoCaptions)
            if (preset.maxQualityHeight != null) {
                QualityPreference.set(preset.maxQualityHeight)
            }
            state.actionMessage = "Applied preset: ${preset.name}"
        }
    }

    fun addCustomPreset(
        name: String,
        speed: Float,
        skipSilence: Boolean,
        autoCaptions: Boolean,
        audioOnly: Boolean,
        eqPreset: AudioEffects.Preset,
        boostIndex: Int,
        colorPreset: VideoColorPreset,
        maxQualityHeight: Int?,
    ): PlaybackPreset {
        val cleanName = name.trim().ifBlank { "Custom ${customPresets.size + 1}" }.take(36)
        val created = PlaybackPreset(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            speed = speed,
            skipSilence = skipSilence,
            autoCaptions = autoCaptions,
            audioOnly = audioOnly,
            eqPreset = eqPreset,
            boostIndex = boostIndex,
            colorPreset = colorPreset,
            maxQualityHeight = maxQualityHeight,
            builtIn = false,
        )
        customPresets.removeAll { it.name.equals(cleanName, ignoreCase = true) }
        customPresets.add(created)
        persistCustomPresets()
        return created
    }

    fun removeCustomPreset(id: String) {
        if (customPresets.removeAll { it.id == id }) {
            if (activePresetId == id) activePresetId = "standard"
            persistCustomPresets()
        }
    }

    fun exportPresetsJson(): String {
        val arr = JSONArray()
        customPresets.forEach { arr.put(it.toJson()) }
        return JSONObject()
            .put("type", "ytx_playback_presets")
            .put("version", 1)
            .put("presets", arr)
            .toString(2)
    }

    fun importPresetsJson(raw: String): Int {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("presets") ?: JSONArray(raw)
        var imported = 0
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val preset = PlaybackPreset.fromJson(item)
            customPresets.removeAll { it.id == preset.id || it.name.equals(preset.name, ignoreCase = true) }
            customPresets.add(preset)
            imported++
        }
        if (imported > 0) persistCustomPresets()
        return imported
    }

    private fun persistCustomPresets() {
        val arr = JSONArray()
        customPresets.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString("custom_presets", arr.toString())?.apply()
    }

    fun updateControlLayout(layout: PlayerControlLayout) {
        controlLayout = layout
        prefs?.edit()?.putString("control_layout", layout.name)?.apply()
    }

    fun updateFloatingToolbar(enabled: Boolean) {
        floatingToolbarEnabled = enabled
        prefs?.edit()?.putBoolean("floating_toolbar", enabled)?.apply()
    }

    fun updatePipSettings(autoEnter: Boolean = pipAutoEnter, aspectMode: PipAspectMode = pipAspectMode, pauseOnClose: Boolean = pipPauseOnClose) {
        pipAutoEnter = autoEnter
        pipAspectMode = aspectMode
        pipPauseOnClose = pauseOnClose
        prefs?.edit()
            ?.putBoolean("pip_auto_enter", autoEnter)
            ?.putString("pip_aspect_mode", aspectMode.name)
            ?.putBoolean("pip_pause_on_close", pauseOnClose)
            ?.apply()
    }

    fun effectivePipAspect(videoAspect: Float): Float =
        (pipAspectMode.fixedRatio ?: videoAspect.takeIf { it > 0f } ?: (16f / 9f)).coerceIn(0.42f, 2.38f)

    fun updateShortcuts(seekSec: Int, twoFinger: String) {
        doubleTapSeekSec = seekSec.coerceIn(3, 60)
        twoFingerTapAction = twoFinger
        Tweaks.seekStepSec.update(doubleTapSeekSec)
        prefs?.edit()
            ?.putInt("double_tap_seek_sec", doubleTapSeekSec)
            ?.putString("two_finger_action", twoFinger)
            ?.apply()
    }

    fun updateNetworkProfile(profile: NetworkProfileKind) {
        networkProfile = profile
        Tweaks.cellularCapP.update(profile.cellularMaxHeight ?: 0)
        Tweaks.preload.update(if (Tweaks.onMeteredNetwork()) profile.preloadOnCellular else profile.preloadOnWifi)
        prefs?.edit()?.putString("network_profile", profile.name)?.apply()
    }

    // Feature 20: Per-channel playback preferences
    fun prefForChannel(video: VideoItem): ChannelPlaybackPref? {
        val cid = video.channelId()
        val cname = video.channel.trim()
        return channelPrefs.firstOrNull {
            (cid != null && it.channelKey.equals(cid, ignoreCase = true)) ||
                (cname.isNotEmpty() && it.channelName.equals(cname, ignoreCase = true))
        }
    }

    fun saveChannelPref(pref: ChannelPlaybackPref) {
        channelPrefs.removeAll {
            it.channelKey.equals(pref.channelKey, ignoreCase = true) ||
                it.channelName.equals(pref.channelName, ignoreCase = true)
        }
        channelPrefs.add(0, pref)
        persistChannelPrefs()
    }

    fun removeChannelPref(channelKeyOrName: String) {
        if (channelPrefs.removeAll {
                it.channelKey.equals(channelKeyOrName, ignoreCase = true) ||
                    it.channelName.equals(channelKeyOrName, ignoreCase = true)
            }
        ) {
            persistChannelPrefs()
        }
    }

    private fun persistChannelPrefs() {
        val arr = JSONArray()
        channelPrefs.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString("channel_prefs", arr.toString())?.apply()
    }

    // Feature 29 & 30: Bookmarks & Timestamped Annotations
    fun addBookmark(
        video: VideoItem,
        positionMs: Long,
        collection: String = "Highlights",
        note: String = "",
    ): VideoBookmarkItem {
        val cleanCol = collection.trim().ifBlank { "Highlights" }
        if (cleanCol !in collections) {
            collections.add(cleanCol)
            prefs?.edit()?.putString("collections", collections.joinToString("|"))?.apply()
        }
        val item = VideoBookmarkItem(
            id = "${video.id}_${positionMs / 1000}_${System.currentTimeMillis() % 10000}",
            videoId = video.id,
            videoTitle = video.title,
            channel = video.channel,
            thumbnail = video.thumbnail,
            positionMs = positionMs.coerceAtLeast(0L),
            collection = cleanCol,
            note = note.trim(),
            createdAt = System.currentTimeMillis(),
        )
        bookmarks.add(0, item)
        persistBookmarks()
        return item
    }

    fun removeBookmark(id: String) {
        if (bookmarks.removeAll { it.id == id }) persistBookmarks()
    }

    fun bookmarksForVideo(videoId: String): List<VideoBookmarkItem> =
        bookmarks.filter { it.videoId == videoId }.sortedBy { it.positionMs }

    fun bookmarksForCollection(collection: String?): List<VideoBookmarkItem> =
        if (collection.isNullOrBlank() || collection == "All") bookmarks.toList()
        else bookmarks.filter { it.collection.equals(collection, ignoreCase = true) }

    fun exportNotesMarkdown(videoId: String? = null): String {
        val list = if (videoId != null) bookmarksForVideo(videoId) else bookmarks.toList()
        if (list.isEmpty()) return "# YTXTube Video Notes\n\nNo bookmarks or annotations saved yet."
        return buildString {
            appendLine("# YTXTube Video Bookmarks & Timestamped Annotations")
            appendLine()
            list.groupBy { it.videoId to it.videoTitle }.forEach { (pair, items) ->
                val (vid, title) = pair
                appendLine("## ${title.ifBlank { vid }}")
                appendLine("- Video URL: https://youtu.be/$vid")
                items.sortedBy { it.positionMs }.forEach { b ->
                    val ts = formatDuration(b.positionMs)
                    val noteSuffix = if (b.note.isNotBlank()) " — ${b.note}" else ""
                    appendLine("- **[$ts](https://youtu.be/$vid?t=${b.positionMs / 1000})** [${b.collection}]$noteSuffix")
                }
                appendLine()
            }
        }
    }

    private fun persistBookmarks() {
        val arr = JSONArray()
        bookmarks.take(500).forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString("bookmarks", arr.toString())?.apply()
    }

    // Feature 16: Multi-video A/B comparison helpers
    fun stageForComparison(video: VideoItem, currentPosMs: Long = 0L) {
        if (currentPosMs > 0L) comparisonPositions[video.id] = currentPosMs
        comparisonCandidate = video
    }

    fun swapComparisonVideo(player: PlayerState) {
        val current = player.current ?: return
        val target = comparisonCandidate ?: return
        comparisonPositions[current.id] = player.positionMs
        comparisonCandidate = current
        val resumeAt = comparisonPositions[target.id] ?: 0L
        player.open(target, expand = true)
        if (resumeAt > 0L) {
            player.seekTo(resumeAt)
        }
        player.actionMessage = "Switched to A/B comparison: ${target.title.take(28)}"
    }

    // AB-Loop check called from player sync loop
    fun checkAbLoop(player: PlayerState) {
        val start = abLoopStartMs ?: return
        val end = abLoopEndMs ?: return
        if (end > start + 500L && player.positionMs >= end) {
            player.seekTo(start)
        }
    }

    fun clearAbLoop() {
        abLoopStartMs = null
        abLoopEndMs = null
    }

    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        VideoColorEngine.reset()
        activePresetId = "standard"
        controlLayout = PlayerControlLayout.Standard
        floatingToolbarEnabled = false
        pipAutoEnter = true
        pipAspectMode = PipAspectMode.MatchVideo
        pipPauseOnClose = true
        doubleTapSeekSec = 10
        twoFingerTapAction = "Bookmark"
        networkProfile = NetworkProfileKind.Adaptive
        customPresets.clear()
        channelPrefs.clear()
        clearAbLoop()
    }
}

/**
 * Feature 28: Draggable Floating Playback Toolbar inside the video player.
 * Provides instant one-tap access to Frame Step, AB-Loop, Bookmark, Timestamp Note, Color Filter, and Speed.
 */
@Composable
fun FloatingPlaybackToolbar(
    state: PlayerState,
    onOpenStudioSheet: () -> Unit,
    onOpenBookmarksSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!VideoExperience.floatingToolbarEnabled) return
    val c = MaterialTheme.colorScheme
    var offset by remember { mutableStateOf(Offset.Zero) }
    val item = state.current ?: return

    Row(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset += drag
                }
            }
            .clip(CircleShape)
            .background(Color(0xD90E111B))
            .border(1.dp, Color(0xFF7C3AED).copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Frame Step toggle & step size
        FloatingToolPill(
            label = if (FrameStep.enabled) "${FrameStep.stepFrames}F" else "Step",
            active = FrameStep.enabled,
            onClick = {
                val on = FrameStep.toggle()
                state.actionMessage = if (on) "Frame step on (${FrameStep.stepFrames}f)" else "Frame step off"
            },
        )
        // 2. AB-Loop
        val abLabel = when {
            VideoExperience.abLoopStartMs == null -> "A-B"
            VideoExperience.abLoopEndMs == null -> "Set B"
            else -> "Loop✓"
        }
        FloatingToolPill(
            label = abLabel,
            active = VideoExperience.abLoopStartMs != null,
            onClick = {
                when {
                    VideoExperience.abLoopStartMs == null -> {
                        VideoExperience.abLoopStartMs = state.positionMs
                        state.actionMessage = "Loop A set at ${formatDuration(state.positionMs)}"
                    }
                    VideoExperience.abLoopEndMs == null -> {
                        if (state.positionMs > (VideoExperience.abLoopStartMs ?: 0L) + 500L) {
                            VideoExperience.abLoopEndMs = state.positionMs
                            state.actionMessage = "A-B Loop active (${formatDuration(VideoExperience.abLoopStartMs!!)}–${formatDuration(state.positionMs)})"
                        } else {
                            VideoExperience.clearAbLoop()
                            state.actionMessage = "A-B Loop cleared"
                        }
                    }
                    else -> {
                        VideoExperience.clearAbLoop()
                        state.actionMessage = "A-B Loop cleared"
                    }
                }
            },
        )
        // 3. Instant Bookmark
        FloatingToolPill(
            label = "+Pin",
            active = VideoExperience.bookmarksForVideo(item.id).isNotEmpty(),
            onClick = {
                VideoExperience.addBookmark(item, state.positionMs, "Highlights", "Pinned from floating toolbar")
                state.actionMessage = "Bookmarked ${formatDuration(state.positionMs)}"
            },
        )
        // 4. Notes & Bookmarks Sheet
        FloatingToolPill(
            label = "Notes",
            active = false,
            onClick = onOpenBookmarksSheet,
        )
        // 5. Color / Preset Studio
        FloatingToolPill(
            label = if (VideoColorEngine.isActive) VideoColorEngine.preset.label.take(5) else "Color",
            active = VideoColorEngine.isActive,
            onClick = onOpenStudioSheet,
        )
    }
}

@Composable
private fun FloatingToolPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Color(0xFF7C3AED) else Color.White.copy(alpha = 0.12f))
            .pressable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Comprehensive modal sheet for Category 2:
 * - Playback Presets (built-in + custom creator)
 * - Video Color Matrix (Presets + Saturation / Contrast / Brightness / Warmth sliders)
 * - Per-Channel Playback Preferences
 * - Control Layout & Floating Toolbar
 * - PiP & Network-Aware Profiles
 * - Multi-Video Comparison (Feature 16)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoExperienceStudioSheet(
    player: PlayerState,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Presets", "Color & Bright", "Channel Prefs", "Compare A/B", "Controls & PiP")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Advanced Video Experience",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
            Text(
                "Playback presets, live color grading, per-channel preferences & A/B comparison",
                color = c.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { index, label ->
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> PlaybackPresetsTab(player)
                1 -> VideoColorGradingTab()
                2 -> PerChannelPrefsTab(player)
                3 -> MultiVideoCompareTab(player, onDismiss)
                4 -> ControlsPipAndNetworkTab()
            }
        }
    }
}

@Composable
private fun PlaybackPresetsTab(player: PlayerState) {
    val c = MaterialTheme.colorScheme
    var customName by remember { mutableStateOf("") }

    Text("Active Playback Preset", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(8.dp))

    VideoExperience.allPresets.forEach { preset ->
        val selected = VideoExperience.activePresetId == preset.id
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) c.primaryContainer else c.surfaceContainerHigh)
                .pressable { VideoExperience.applyPreset(preset, player) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    preset.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (selected) c.onPrimaryContainer else c.onSurface,
                )
                val detail = listOfNotNull(
                    "${preset.speed}× speed",
                    if (preset.skipSilence) "Skip silence" else null,
                    if (preset.autoCaptions) "Auto CC" else null,
                    if (preset.audioOnly) "Audio-only" else null,
                    "EQ: ${preset.eqPreset.label}",
                    preset.maxQualityHeight?.let { "Cap ${it}p" },
                ).joinToString(" · ")
                Text(
                    detail,
                    fontSize = 11.5.sp,
                    color = if (selected) c.onPrimaryContainer.copy(alpha = 0.8f) else c.onSurfaceVariant,
                )
            }
            if (!preset.builtIn) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .pressable { VideoExperience.removeCustomPreset(preset.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Delete, "Delete preset", tint = c.error, modifier = Modifier.size(18.dp))
                }
            } else if (selected) {
                Icon(Icons.Rounded.Check, "Active", tint = c.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
    Spacer(Modifier.height(10.dp))
    Text("Save Current Player State as Custom Preset", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = customName,
            onValueChange = { customName = it },
            singleLine = true,
            placeholder = { Text("Preset name (e.g. Coding Tutorial)") },
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                val created = VideoExperience.addCustomPreset(
                    name = customName,
                    speed = player.speed,
                    skipSilence = SkipSilence.enabled,
                    autoCaptions = player.selectedSubtitleLanguage != null,
                    audioOnly = AudioOnly.enabled,
                    eqPreset = AudioEffects.preset,
                    boostIndex = AudioEffects.boostIndex,
                    colorPreset = VideoColorEngine.preset,
                    maxQualityHeight = QualityPreference.height,
                )
                VideoExperience.applyPreset(created, player)
                customName = ""
            },
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun VideoColorGradingTab() {
    val c = MaterialTheme.colorScheme
    val activity = LocalContext.current as? android.app.Activity

    Text("Color Grading Presets", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VideoColorPreset.entries.filter { it != VideoColorPreset.Custom }.forEach { p ->
            FilterChip(
                selected = VideoColorEngine.preset == p,
                onClick = { VideoColorEngine.applyPreset(p) },
                label = { Text(p.label) },
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Custom Color Matrix Controls", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)

    ColorSliderRow(
        label = "Saturation",
        value = VideoColorEngine.saturation,
        range = 0f..2.5f,
        readout = "${(VideoColorEngine.saturation * 100).roundToInt()}%",
        onChange = { VideoColorEngine.updateCustom(newSaturation = it) },
    )
    ColorSliderRow(
        label = "Contrast",
        value = VideoColorEngine.contrast,
        range = 0.5f..2.0f,
        readout = "${(VideoColorEngine.contrast * 100).roundToInt()}%",
        onChange = { VideoColorEngine.updateCustom(newContrast = it) },
    )
    ColorSliderRow(
        label = "Video Brightness",
        value = VideoColorEngine.brightness,
        range = -0.4f..0.4f,
        readout = "${if (VideoColorEngine.brightness >= 0) "+" else ""}${(VideoColorEngine.brightness * 100).roundToInt()}%",
        onChange = { VideoColorEngine.updateCustom(newBrightness = it) },
    )
    ColorSliderRow(
        label = "Warmth / Tint",
        value = VideoColorEngine.warmth,
        range = -0.3f..0.3f,
        readout = "${if (VideoColorEngine.warmth >= 0) "+" else ""}${(VideoColorEngine.warmth * 100).roundToInt()}%",
        onChange = { VideoColorEngine.updateCustom(newWarmth = it) },
    )

    Spacer(Modifier.height(10.dp))
    Text("Brightness Environment Presets", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VideoBrightnessPreset.entries.forEach { bp ->
            FilterChip(
                selected = VideoColorEngine.brightnessPreset == bp,
                onClick = {
                    VideoColorEngine.applyBrightnessPreset(bp)
                    if (activity != null) {
                        val lp = activity.window.attributes
                        lp.screenBrightness = bp.windowBrightness
                        activity.window.attributes = lp
                    }
                },
                label = { Text(bp.label) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = { VideoColorEngine.reset() }) {
        Text("Reset Color & Brightness to Natural")
    }
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onChange: (Float) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(readout, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = c.primary, fontFamily = FontFamily.Monospace)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun PerChannelPrefsTab(player: PlayerState) {
    val c = MaterialTheme.colorScheme
    val currentVideo = player.current
    val currentChannelName = currentVideo?.channel.orEmpty()
    val currentChannelKey = currentVideo?.channelId() ?: currentChannelName

    if (currentChannelName.isNotBlank()) {
        val existing = VideoExperience.prefForChannel(currentVideo!!)
        var speed by remember(existing) { mutableFloatStateOf(existing?.speed ?: player.speed) }
        var skipSec by remember(existing) { mutableIntStateOf(existing?.skipFirstSec ?: Tweaks.skipFirstSec.value) }
        var autoCc by remember(existing) { mutableStateOf(existing?.autoCaptions ?: (player.selectedSubtitleLanguage != null)) }
        var capHeight by remember(existing) { mutableStateOf(existing?.preferredHeight ?: QualityPreference.height) }

        Text("Preferences for $currentChannelName", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
        Spacer(Modifier.height(6.dp))
        Text("Automatically applied whenever you open a video from this channel.", fontSize = 12.sp, color = c.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        Text("Channel Default Speed: ${PlaybackSpeed.label(speed)}×", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { s ->
                FilterChip(selected = abs(speed - s) < 0.02f, onClick = { speed = s }, label = { Text("${s}×") })
            }
        }

        Spacer(Modifier.height(6.dp))
        Text("Skip Intro / First Seconds: ${skipSec}s", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 5, 10, 15, 30, 60).forEach { sec ->
                FilterChip(selected = skipSec == sec, onClick = { skipSec = sec }, label = { Text(if (sec == 0) "Off" else "${sec}s") })
            }
        }

        Spacer(Modifier.height(6.dp))
        Text("Preferred Resolution", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null to "Auto", 720 to "720p", 1080 to "1080p", 1440 to "1440p", 2160 to "4K").forEach { (h, label) ->
                FilterChip(selected = capHeight == h, onClick = { capHeight = h }, label = { Text(label) })
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Always enable Captions for this channel", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Switch(checked = autoCc, onCheckedChange = { autoCc = it })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val saved = ChannelPlaybackPref(
                        channelKey = currentChannelKey,
                        channelName = currentChannelName,
                        speed = speed,
                        preferredHeight = capHeight,
                        skipFirstSec = skipSec,
                        autoCaptions = autoCc,
                    )
                    VideoExperience.saveChannelPref(saved)
                    player.commitSpeed(speed)
                    player.setCaptions(autoCc)
                    if (capHeight != null) player.setQuality(capHeight)
                    player.actionMessage = "Saved rules for $currentChannelName"
                },
            ) {
                Text("Save Channel Rule")
            }
            if (existing != null) {
                OutlinedButton(onClick = { VideoExperience.removeChannelPref(currentChannelKey) }) {
                    Text("Remove Rule")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
        Spacer(Modifier.height(10.dp))
    }

    Text("Saved Channel Rules (${VideoExperience.channelPrefs.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    if (VideoExperience.channelPrefs.isEmpty()) {
        Text("No per-channel overrides saved yet.", fontSize = 12.5.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    } else {
        VideoExperience.channelPrefs.forEach { pref ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(pref.channelName, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Text(
                        "${pref.speed}× · ${pref.preferredHeight?.let { "${it}p" } ?: "Auto"} · Skip ${pref.skipFirstSec}s" +
                            if (pref.autoCaptions) " · Auto CC" else "",
                        fontSize = 11.5.sp,
                        color = c.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier.size(30.dp).clip(CircleShape).pressable { VideoExperience.removeChannelPref(pref.channelKey) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Delete, "Remove", tint = c.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MultiVideoCompareTab(player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val primary = player.current
    val candidate = VideoExperience.comparisonCandidate
        ?: player.details?.related?.firstOrNull { it.id != primary?.id }

    Text("Multi-Video Side-by-Side Comparison", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(4.dp))
    Text("Compare duration, audience reach, topic overlap, and A/B switch between two videos while preserving playback positions.", fontSize = 12.sp, color = c.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))

    if (primary == null) {
        Text("Open a video first to start comparing.", color = c.onSurfaceVariant, fontSize = 13.sp)
        return
    }

    val candidates = (player.queue + player.details?.related.orEmpty()).distinctBy { it.id }.filter { it.id != primary.id }.take(8)
    if (candidates.isNotEmpty()) {
        Text("Pick Video B from Queue / Related:", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            candidates.forEach { v ->
                FilterChip(
                    selected = candidate?.id == v.id,
                    onClick = { VideoExperience.stageForComparison(v) },
                    label = { Text(v.title.take(22), maxLines = 1) },
                )
            }
        }
    }

    if (candidate != null) {
        val analysis = remember(primary.id, candidate.id) { VideoComparisonAnalyzer.compare(primary, candidate) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompareVideoColumn("VIDEO A (PLAYING)", primary, analysis.primaryEngagementHint, Modifier.weight(1f))
            CompareVideoColumn("VIDEO B (STAGED)", candidate, analysis.secondaryEngagementHint, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceContainerHigh)
                .padding(12.dp),
        ) {
            Text("Comparison Insights", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary)
            Spacer(Modifier.height(4.dp))
            Text(analysis.recommendation, fontSize = 12.5.sp)
            if (analysis.sharedKeywords.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Shared topics: ${analysis.sharedKeywords.joinToString(", ")}", fontSize = 11.5.sp, color = c.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                VideoExperience.stageForComparison(candidate, player.positionMs)
                VideoExperience.swapComparisonVideo(player)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("A/B Switch to Video B (Keep Position)")
        }
    } else {
        Text("Long-press any video or wait for related videos to load to compare.", fontSize = 12.5.sp, color = c.onSurfaceVariant)
    }
}

@Composable
private fun CompareVideoColumn(badge: String, video: VideoItem, hint: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceContainerHigh)
            .padding(10.dp),
    ) {
        Text(badge, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = c.primary)
        Spacer(Modifier.height(4.dp))
        Text(video.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(video.channel, fontSize = 11.5.sp, color = c.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text("Length: ${formatDuration(video.durationSec * 1000)}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        if (video.views > 0) {
            Text("Views: ${formatCount(video.views)}", fontSize = 11.5.sp, color = c.onSurfaceVariant)
        }
        Text(hint, fontSize = 11.sp, color = c.primary)
    }
}

@Composable
private fun ControlsPipAndNetworkTab() {
    val c = MaterialTheme.colorScheme

    Text("Player Control Layout", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    PlayerControlLayout.entries.forEach { layout ->
        val selected = VideoExperience.controlLayout == layout
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) c.primaryContainer else c.surfaceContainerHigh)
                .pressable { VideoExperience.updateControlLayout(layout) }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(layout.label, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(layout.description, fontSize = 11.5.sp, color = c.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Rounded.Check, null, tint = c.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Floating Playback Toolbar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Draggable mini-bar for Frame Step, A-B Loop, Pin, Notes & Color", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Switch(checked = VideoExperience.floatingToolbarEnabled, onCheckedChange = VideoExperience::updateFloatingToolbar)
    }

    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
    Spacer(Modifier.height(10.dp))

    Text("Network-Aware Playback Profile", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NetworkProfileKind.entries.forEach { prof ->
            FilterChip(
                selected = VideoExperience.networkProfile == prof,
                onClick = { VideoExperience.updateNetworkProfile(prof) },
                label = { Text(prof.label) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Picture-in-Picture Aspect Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PipAspectMode.entries.forEach { mode ->
            FilterChip(
                selected = VideoExperience.pipAspectMode == mode,
                onClick = { VideoExperience.updatePipSettings(aspectMode = mode) },
                label = { Text(mode.label) },
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Auto-enter PiP when leaving app", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Switch(checked = VideoExperience.pipAutoEnter, onCheckedChange = { VideoExperience.updatePipSettings(autoEnter = it) })
    }
}

/**
 * Features 29, 30 & 147: Video Bookmark Collections & Timestamped Annotations / Notes Workspace Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoBookmarksAndNotesSheet(
    player: PlayerState,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val current = player.current
    var selectedCollection by remember { mutableStateOf("All") }
    var noteText by remember { mutableStateOf("") }
    var targetCollection by remember { mutableStateOf("Highlights") }
    var currentVideoOnly by remember { mutableStateOf(current != null) }

    val items = remember(VideoExperience.bookmarks.size, selectedCollection, currentVideoOnly, current?.id) {
        val base = if (currentVideoOnly && current != null) {
            VideoExperience.bookmarksForVideo(current.id)
        } else {
            VideoExperience.bookmarksForCollection(selectedCollection)
        }
        if (selectedCollection == "All") base else base.filter { it.collection.equals(selectedCollection, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bookmarks & Timestamped Notes", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Text("Pin exact video timestamps with markdown notes & collections", fontSize = 12.sp, color = c.onSurfaceVariant)
                }
                OutlinedButton(
                    onClick = {
                        val md = VideoExperience.exportNotesMarkdown(if (currentVideoOnly) current?.id else null)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("YTXTube Video Notes", md))
                        player.actionMessage = "Copied Markdown notes to clipboard"
                    },
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export MD", fontSize = 12.sp)
                }
            }

            if (current != null) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surfaceContainerHigh)
                        .padding(12.dp),
                ) {
                    Text(
                        "Add Annotation at ${formatDuration(player.positionMs)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = c.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoExperience.collections.forEach { col ->
                            FilterChip(
                                selected = targetCollection == col,
                                onClick = { targetCollection = col },
                                label = { Text(col, fontSize = 11.5.sp) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Write timestamped note or takeaway…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                VideoExperience.addBookmark(current, player.positionMs, targetCollection, noteText)
                                noteText = ""
                                player.actionMessage = "Saved note at ${formatDuration(player.positionMs)}"
                            },
                        ) {
                            Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pin Timestamp (${formatDuration(player.positionMs)})")
                        }
                        OutlinedButton(
                            onClick = {
                                noteText = "$noteText [${formatDuration(player.positionMs)}] ".trimStart()
                            },
                        ) {
                            Text("+ [mm:ss]")
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (current != null) {
                    FilterChip(
                        selected = currentVideoOnly,
                        onClick = { currentVideoOnly = !currentVideoOnly },
                        label = { Text(if (currentVideoOnly) "This Video" else "All Videos") },
                    )
                }
                (listOf("All") + VideoExperience.collections).forEach { col ->
                    FilterChip(
                        selected = selectedCollection == col,
                        onClick = { selectedCollection = col },
                        label = { Text(col) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Text(
                    "No bookmarks or timestamped annotations in this view yet.",
                    color = c.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(items, key = { it.id }) { bm ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.surfaceContainerHigh)
                                .pressable {
                                    if (player.current?.id == bm.videoId) {
                                        player.seekTo(bm.positionMs)
                                    } else {
                                        player.open(
                                            VideoItem(
                                                id = bm.videoId,
                                                title = bm.videoTitle,
                                                channel = bm.channel,
                                                channelUrl = null,
                                                thumbnail = bm.thumbnail,
                                                avatar = null,
                                                durationSec = 0L,
                                                views = -1L,
                                                uploaded = null,
                                            ),
                                        )
                                        player.seekTo(bm.positionMs)
                                    }
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    formatDuration(bm.positionMs),
                                    color = c.onPrimaryContainer,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    bm.note.ifBlank { bm.videoTitle.ifBlank { "Bookmarked moment" } },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${bm.collection} · ${bm.videoTitle.take(32)}",
                                    fontSize = 11.5.sp,
                                    color = c.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .pressable { VideoExperience.removeBookmark(bm.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Delete, "Delete", tint = c.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
