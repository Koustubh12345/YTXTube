package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A persisted on/off tweak. Reads are observable, so the UI follows changes at once. */
class BoolTweak(val key: String, private val default: Boolean) {
    var value by mutableStateOf(default)
        private set

    fun load(p: SharedPreferences) { value = p.getBoolean(key, default) }
    fun update(v: Boolean) { value = v; Tweaks.prefs?.edit()?.putBoolean(key, v)?.apply() }
}

/** A persisted text tweak. */
class StringTweak(val key: String, private val default: String) {
    var value by mutableStateOf(default)
        private set

    fun load(p: SharedPreferences) { value = p.getString(key, default) ?: default }
    fun update(v: String) { value = v; Tweaks.prefs?.edit()?.putString(key, v)?.apply() }
}

/** A persisted number tweak. */
class IntTweak(val key: String, private val default: Int) {
    var value by mutableIntStateOf(default)
        private set

    fun load(p: SharedPreferences) { value = p.getInt(key, default) }
    fun update(v: Int) { value = v; Tweaks.prefs?.edit()?.putInt(key, v)?.apply() }
}

/**
 * Small everyday options gathered from other open-source YouTube clients (NewPipe, LibreTube,
 * SmartTube, ReVanced): feed filters, gesture tuning and watch-page actions. All of them are
 * stored on the device and applied live or the next time a feed loads.
 */
object Tweaks {
    internal var prefs: SharedPreferences? = null

    // Feed filters
    val hideShorts = BoolTweak("hide_shorts", false)
    val hideLive = BoolTweak("hide_live", false)
    val hideWatched = BoolTweak("hide_watched", false)
    val minLengthMin = IntTweak("min_length_min", 0)
    val maxLengthMin = IntTweak("max_length_min", 0)
    val minViewsK = IntTweak("min_views_k", 0)

    // Player
    val seekStepSec = IntTweak("seek_step_sec", 10)
    val holdSpeedX10 = IntTweak("hold_speed_x10", 20)
    val controlsHideSec = IntTweak("controls_hide_sec", 2)
    val gestureSensitivity = IntTweak("gesture_sensitivity", 100)
    val lyricsOffsetMs = IntTweak("lyrics_offset_ms", 0)
    val remainingTime = BoolTweak("remaining_time", false)
    val haptics = BoolTweak("haptics", true)
    val resume = BoolTweak("resume", true)

    // Playback defaults
    val defaultSpeedX100 = IntTweak("default_speed_x100", 100)
    val skipFirstSec = IntTweak("skip_first_sec", 0)
    val autoCaptions = BoolTweak("auto_captions", false)
    val captionLang = StringTweak("caption_lang", "en")
    val cellularCapP = IntTweak("cellular_cap_p", 0)
    val fullscreenClock = BoolTweak("fullscreen_clock", false)

    // Data and privacy
    val preload = BoolTweak("preload", true)
    val incognito = BoolTweak("incognito", false)
    val pauseSearchHistory = BoolTweak("pause_search_history", false)
    val pauseWatchHistory = BoolTweak("pause_watch_history", false)
    val maxAgeDays = IntTweak("max_age_days", 0)

    // Look
    val startTab = IntTweak("start_tab", 0)
    val textScale = IntTweak("text_scale", 100)
    val uiScale = IntTweak("ui_scale", 100)
    val thumbWidthDp = IntTweak("thumb_width_dp", 140)
    val titleLines = IntTweak("title_lines", 2)
    val hideViews = BoolTweak("hide_views", false)
    val showUploaded = BoolTweak("show_uploaded", false)
    val hideDuration = BoolTweak("hide_duration", false)

    // Subtitles, loading, performance
    val captionSize = IntTweak("caption_size", 15)
    val captionColor = StringTweak("caption_color", "white")
    val captionBgAlpha = IntTweak("caption_bg_alpha", 72)
    val captionOutline = BoolTweak("caption_outline", false)
    val captionShadow = BoolTweak("caption_shadow", false)
    val captionBold = BoolTweak("caption_bold", true)
    val spinnerStyle = StringTweak("spinner_style", "ring")
    val spinnerSpeed = IntTweak("spinner_speed", 100)
    val autoBatterySaver = BoolTweak("auto_battery_saver", true)
    val liteMode = BoolTweak("lite_mode", false)
    val hideProgressBars = BoolTweak("hide_progress", false)
    val iconOnlyNav = BoolTweak("icon_only_nav", false)
    val showScreenshotButton = BoolTweak("screenshot_button", true)

    // Gestures
    val brightnessSwipe = BoolTweak("brightness_swipe", true)
    val volumeSwipe = BoolTweak("volume_swipe", true)
    val pinchZoom = BoolTweak("pinch_zoom", true)
    val holdSpeedOn = BoolTweak("hold_speed_on", true)

    // Watch page
    val extraCopyButtons = BoolTweak("extra_copy_buttons", true)
    val showShareAtTime = BoolTweak("share_at_time", true)
    val showCopyLink = BoolTweak("copy_link", true)
    val showOpenBrowser = BoolTweak("open_browser", true)
    val showMarkWatched = BoolTweak("mark_watched", true)
    val hideDownload = BoolTweak("hide_download", false)
    val hideRelated = BoolTweak("hide_related", false)
    val hideCommentsCard = BoolTweak("hide_comments", false)
    val hideChaptersCard = BoolTweak("hide_chapters", false)

    private val bools = listOf(
        hideShorts, hideLive, hideWatched, remainingTime, haptics, resume, showShareAtTime, showCopyLink,
        showOpenBrowser, showMarkWatched, hideDownload, hideRelated, hideCommentsCard, hideChaptersCard,
        autoCaptions, fullscreenClock, preload, incognito, pauseSearchHistory, pauseWatchHistory,
        hideViews, showUploaded, hideDuration, brightnessSwipe, volumeSwipe, pinchZoom, holdSpeedOn, extraCopyButtons,
        captionOutline, captionShadow, captionBold, autoBatterySaver, liteMode, hideProgressBars, iconOnlyNav, showScreenshotButton,
    )
    private val ints = listOf(minLengthMin, maxLengthMin, minViewsK, seekStepSec, holdSpeedX10, controlsHideSec, gestureSensitivity, lyricsOffsetMs,
        defaultSpeedX100, skipFirstSec, cellularCapP, maxAgeDays, startTab, textScale, uiScale, thumbWidthDp, titleLines,
        captionSize, captionBgAlpha, spinnerSpeed,
    )

    /**
     * Every tweak key with its stored type ('b' boolean, 'i' int, 's' string). Used to validate
     * imported profiles so a wrong-typed value can never be written over a real setting.
     */
    internal val keyTypes: Map<String, Char> by lazy {
        buildMap {
            bools.forEach { put(it.key, 'b') }
            ints.forEach { put(it.key, 'i') }
            listOf(captionLang, captionColor, spinnerStyle).forEach { put(it.key, 's') }
        }
    }

    fun init(context: Context) {
        val p = context.getSharedPreferences("tweaks", Context.MODE_PRIVATE).also { prefs = it }
        bools.forEach { it.load(p) }
        ints.forEach { it.load(p) }
        captionLang.load(p)
        captionColor.load(p)
        spinnerStyle.load(p)
        appContext = context.applicationContext
    }

    private var appContext: Context? = null

    /** True when the heavy visual extras (ambient light, fullscreen fill) should stay off. */
    fun liteActive(): Boolean {
        if (liteMode.value) return true
        if (!autoBatterySaver.value) return false
        return runCatching { appContext?.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }.getOrDefault(false)
    }

    val noSearchHistory: Boolean get() = incognito.value || pauseSearchHistory.value
    val noWatchHistory: Boolean get() = incognito.value || pauseWatchHistory.value

    /** True on mobile data (or any metered connection). */
    fun onMeteredNetwork(): Boolean = runCatching {
        val cm = appContext?.getSystemService(android.net.ConnectivityManager::class.java)
        cm?.isActiveNetworkMetered == true
    }.getOrDefault(false)

    /** The quality to use: the user's choice, lowered to the mobile-data cap when on mobile data. */
    fun cappedHeight(chosen: Int?): Int? {
        val cap = cellularCapP.value
        if (cap <= 0 || !onMeteredNetwork()) return chosen
        return if (chosen == null || chosen > cap) cap else chosen
    }

    /** Puts every tweak back to its default. */
    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        prefs?.let { p ->
            bools.forEach { it.load(p) }
            ints.forEach { it.load(p) }
            captionLang.load(p)
            captionColor.load(p)
            spinnerStyle.load(p)
        }
    }

    /** Roughly how many days ago a video was uploaded, from text like "3 weeks ago"; null if unknown. */
    fun ageDays(uploaded: String?): Int? {
        val m = Regex("(\\d+)\\s+(second|minute|hour|day|week|month|year)", RegexOption.IGNORE_CASE).find(uploaded ?: return null) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "second", "minute", "hour" -> 0
            "day" -> n
            "week" -> n * 7
            "month" -> n * 30
            else -> n * 365
        }
    }

    val seekStepMs: Long get() = seekStepSec.value * 1000L
    val holdSpeed: Float get() = holdSpeedX10.value / 10f
    val controlsHideMs: Long get() = controlsHideSec.value * 1000L

    val anyFeedFilter: Boolean
        get() = hideShorts.value || hideLive.value || hideWatched.value ||
            minLengthMin.value > 0 || maxLengthMin.value > 0 || minViewsK.value > 0 || maxAgeDays.value > 0

    /** True when [video] passes every feed filter. Videos with an unknown length are never hidden by length. */
    fun allows(video: VideoItem): Boolean {
        if (hideLive.value && video.isLive) return false
        val len = video.durationSec
        if (len > 0 && !video.isLive) {
            if (hideShorts.value && len <= 60) return false
            if (minLengthMin.value > 0 && len < minLengthMin.value * 60L) return false
            if (maxLengthMin.value > 0 && len > maxLengthMin.value * 60L) return false
        }
        if (maxAgeDays.value > 0 && !video.isLive) {
            val age = ageDays(video.uploaded)
            if (age != null && age > maxAgeDays.value) return false
        }
        if (minViewsK.value > 0 && video.views > 0 && video.views < minViewsK.value * 1000L) return false
        if (hideWatched.value && Watched.has(video.id)) return false
        return true
    }
}

/**
 * Videos watched to the end (or marked by hand). ResumeStore forgets a video once it is finished,
 * so this keeps its own list, newest last, capped so it stays small.
 */
object Watched {
    private const val MAX = 3_000
    private var prefs: SharedPreferences? = null
    private val ids = androidx.compose.runtime.mutableStateListOf<String>()

    fun init(context: Context) {
        val p = context.getSharedPreferences("watched", Context.MODE_PRIVATE).also { prefs = it }
        ids.clear()
        ids.addAll(p.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() })
    }

    val count: Int get() = ids.size

    fun has(id: String): Boolean = id in ids

    fun clear() {
        ids.clear()
        save()
    }

    fun add(id: String) {
        if (id.isBlank() || id in ids) return
        ids.add(id)
        while (ids.size > MAX) ids.removeAt(0)
        save()
    }

    fun remove(id: String) {
        if (ids.remove(id)) save()
    }

    private fun save() {
        prefs?.edit()?.putString("ids", ids.joinToString(","))?.apply()
    }
}

/** Every tweak in one scrolling sheet, grouped like the sections above. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TweaksSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    FloatingSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 18.dp, bottom = 22.dp),
        ) {
            Text("YTX Tweaks", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Spacer(Modifier.height(4.dp))

            TweakHeader("Feeds and search")
            TweakSwitch("Hide Shorts-length videos", "Videos of a minute or less", Tweaks.hideShorts)
            TweakSwitch("Hide live streams", null, Tweaks.hideLive)
            TweakSwitch("Hide watched videos", "Videos you have watched to the end", Tweaks.hideWatched)
            TweakChoice("Hide videos shorter than", Tweaks.minLengthMin, listOf(0, 1, 3, 5, 10)) { if (it == 0) "Off" else "$it min" }
            TweakChoice("Hide videos longer than", Tweaks.maxLengthMin, listOf(0, 30, 60, 120, 240)) { if (it == 0) "Off" else "$it min" }
            TweakChoice("Hide videos with fewer views than", Tweaks.minViewsK, listOf(0, 1, 10, 100, 1000)) { if (it == 0) "Off" else "${it}K" }

            TweakChoice("Hide videos older than", Tweaks.maxAgeDays, listOf(0, 7, 30, 180, 365)) { if (it == 0) "Off" else if (it < 30) "$it days" else if (it < 365) "${it / 30} months" else "1 year" }

            TweakHeader("Playback defaults")
            TweakChoice("Default speed for new videos", Tweaks.defaultSpeedX100, listOf(75, 100, 125, 150, 175, 200)) { "${it / 100f}x".replace(".0x", "x") }
            TweakChoice("Skip the first seconds of each video", Tweaks.skipFirstSec, listOf(0, 5, 10, 15, 30)) { if (it == 0) "Off" else "$it s" }
            TweakSwitch("Turn captions on automatically", "When a video has them in your language", Tweaks.autoCaptions)
            TweakChoice("Caption language", Tweaks.captionLang, listOf("en", "es", "hi", "fr", "de", "pt", "ja", "ko", "ar", "ru")) { it.uppercase() }
            TweakChoice("Cap quality on mobile data", Tweaks.cellularCapP, listOf(0, 360, 480, 720, 1080)) { if (it == 0) "Off" else "${it}p" }
            TweakSwitch("Clock in fullscreen", "Shows the time in the corner", Tweaks.fullscreenClock)

            TweakHeader("Player")
            TweakChoice("Double-tap seek", Tweaks.seekStepSec, listOf(5, 10, 15, 20, 30)) { "$it s" }
            TweakChoice("Hold to speed up", Tweaks.holdSpeedX10, listOf(15, 20, 30, 40)) { "${it / 10f}x" }
            TweakChoice("Brightness and volume swipe sensitivity", Tweaks.gestureSensitivity, listOf(50, 75, 100, 150, 200)) { "$it%" }
            TweakChoice("Hide controls after", Tweaks.controlsHideSec, listOf(2, 3, 5, 8)) { "$it s" }
            TweakSwitch("Show remaining time", "Count down instead of showing the total length", Tweaks.remainingTime)
            TweakSwitch("Haptic feedback", "Small vibrations on taps", Tweaks.haptics)
            TweakSwitch("Resume where you left off", null, Tweaks.resume)

            TweakHeader("Music and personalisation")
            TweakChoice("Lyrics timing (positive = lyrics earlier)", Tweaks.lyricsOffsetMs, listOf(-1000, -500, -250, 0, 250, 500, 1000)) { if (it == 0) "In sync" else "${if (it > 0) "+" else ""}$it ms" }
            TweakSwitch("Personalised related videos", "Ranks More videos by what you watch, on this device only. ${Discovery.count} watches remembered", Discovery.enabled, Discovery::updateEnabled)
            TweakSwitch("Send listens to ListenBrainz", "Music mode only. ${if (ListenBrainz.status.isNotBlank()) ListenBrainz.status else "Needs your user token"}", ListenBrainz.enabled, ListenBrainz::updateEnabled)
            var lbToken by remember { mutableStateOf(ListenBrainz.token) }
            androidx.compose.material3.OutlinedTextField(
                value = lbToken,
                onValueChange = { lbToken = it; ListenBrainz.updateToken(it) },
                singleLine = true,
                label = { Text("ListenBrainz user token") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            TweakHeader("Gestures")
            TweakSwitch("Brightness swipe", "Left edge in fullscreen", Tweaks.brightnessSwipe)
            TweakSwitch("Volume swipe", "Right edge in fullscreen", Tweaks.volumeSwipe)
            TweakSwitch("Pinch to zoom", null, Tweaks.pinchZoom)
            TweakSwitch("Hold to speed up", null, Tweaks.holdSpeedOn)

            TweakHeader("Look")
            TweakChoice("Start on", Tweaks.startTab, listOf(0, 1, 2, 3, 4)) { listOf("Home", "Music", "Shorts", "Library", "Saved")[it] }
            TweakChoice("Text size", Tweaks.textScale, listOf(85, 100, 115, 130)) { "$it%" }
            TweakChoice("Interface size", Tweaks.uiScale, listOf(90, 100, 110)) { "$it%" }
            TweakChoice("Video thumbnail width", Tweaks.thumbWidthDp, listOf(110, 140, 170, 200)) { "$it dp" }
            TweakChoice("Title lines in lists", Tweaks.titleLines, listOf(1, 2, 3)) { "$it" }
            TweakSwitch("Hide view counts", null, Tweaks.hideViews)
            TweakSwitch("Show upload date", "In video lists", Tweaks.showUploaded)
            TweakSwitch("Hide duration badge", null, Tweaks.hideDuration)
            TweakSwitch("Hide progress bars", "The red resume bar on thumbnails", Tweaks.hideProgressBars)
            TweakSwitch("Icon-only navigation", "No labels under the bottom bar icons", Tweaks.iconOnlyNav)

            TweakHeader("Subtitles")
            TweakChoice("Subtitle size", Tweaks.captionSize, listOf(12, 15, 18, 22, 26)) { "$it" }
            TweakChoice("Subtitle colour", Tweaks.captionColor, listOf("white", "yellow", "cyan", "green", "pink")) { it.replaceFirstChar { c -> c.uppercase() } }
            TweakChoice("Subtitle background", Tweaks.captionBgAlpha, listOf(0, 25, 50, 72, 90)) { if (it == 0) "None" else "$it%" }
            TweakSwitch("Outline", "A dark edge around each letter", Tweaks.captionOutline)
            TweakSwitch("Drop shadow", null, Tweaks.captionShadow)
            TweakSwitch("Bold", null, Tweaks.captionBold)
            Text(
                "This is how subtitles will look",
                color = captionColorOf(Tweaks.captionColor.value),
                fontSize = Tweaks.captionSize.value.sp,
                fontWeight = if (Tweaks.captionBold.value) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp)
                    .androidxBackground(Tweaks.captionBgAlpha.value / 100f)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )

            TweakHeader("Loading animation")
            TweakChoice("Style", Tweaks.spinnerStyle, SpinnerStyles) { spinnerLabel(it) }
            TweakChoice("Speed", Tweaks.spinnerSpeed, listOf(50, 100, 150, 200)) { "${it / 100f}x".replace(".0x", "x") }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                SpinnerStyles.forEach { style -> YtxSpinner(MaterialTheme.colorScheme.primary, 30.dp, style = style) }
            }

            TweakHeader("Performance")
            TweakSwitch("Lite mode", "Turns off ambient light and reduces motion", Tweaks.liteMode)
            TweakSwitch("Follow battery saver", "Same as lite mode while Android battery saver is on", Tweaks.autoBatterySaver)

            TweakHeader("Data and history")
            TweakSwitch("Incognito mode", "Nothing is remembered: no search, watch, resume or usage history", Tweaks.incognito)
            TweakSwitch("Pause search history", null, Tweaks.pauseSearchHistory)
            TweakSwitch("Pause watch history", "Stops resume positions, watched marks and recent music", Tweaks.pauseWatchHistory)
            TweakSwitch("Preload upcoming videos", "Resolves the next videos ahead of the tap. Turn off to save data", Tweaks.preload)

            TweakHeader("Watch page")
            TweakSwitch("Share at current time button", null, Tweaks.showShareAtTime)
            TweakSwitch("Copy link button", null, Tweaks.showCopyLink)
            TweakSwitch("Open in browser button", null, Tweaks.showOpenBrowser)
            TweakSwitch("Mark as watched button", null, Tweaks.showMarkWatched)
            TweakSwitch("Copy title, channel link and description buttons", null, Tweaks.extraCopyButtons)
            TweakSwitch("Screenshot button", "Saves the current frame to Pictures (Android 10+)", Tweaks.showScreenshotButton)
            TweakSwitch("Hide Download button", null, Tweaks.hideDownload)
            TweakSwitch("Hide chapters card", null, Tweaks.hideChaptersCard)
            TweakSwitch("Hide comments card", null, Tweaks.hideCommentsCard)
            TweakSwitch("Hide related videos", null, Tweaks.hideRelated)

            TweakHeader("System")
            val context = androidx.compose.ui.platform.LocalContext.current
            SystemLink("App info and permissions") {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            SystemLink("Notification settings") {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
            SystemLink("Battery optimisation (for background playback)") {
                runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            SystemLink("Copy debug info") {
                val info = "YTXTube ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, ${android.os.Build.SUPPORTED_ABIS.firstOrNull()}"
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Debug info", info))
            }
            var confirmReset by remember { mutableStateOf(false) }
            SystemLink(if (confirmReset) "Tap again to reset every tweak" else "Reset all tweaks") {
                if (confirmReset) { Tweaks.resetAll(); confirmReset = false } else confirmReset = true
            }

            Spacer(Modifier.height(8.dp))
            Text("Feed filters apply the next time a feed loads or refreshes.", color = c.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

fun captionColorOf(name: String): androidx.compose.ui.graphics.Color = when (name) {
    "yellow" -> androidx.compose.ui.graphics.Color(0xFFFFE94A)
    "cyan" -> androidx.compose.ui.graphics.Color(0xFF4DE8FF)
    "green" -> androidx.compose.ui.graphics.Color(0xFF6BFF8E)
    "pink" -> androidx.compose.ui.graphics.Color(0xFFFF7BC8)
    else -> androidx.compose.ui.graphics.Color.White
}

private fun Modifier.androidxBackground(alpha: Float): Modifier =
    this.then(Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = alpha), RoundedCornerShape(7.dp)))

@Composable
private fun SystemLink(title: String, onClick: () -> Unit) {
    Text(
        title,
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onClick).padding(vertical = 11.dp),
    )
}

@Composable
private fun TweakHeader(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun TweakSwitch(title: String, subtitle: String?, tweak: BoolTweak) =
    TweakSwitch(title, subtitle, tweak.value) { tweak.update(it) }

@Composable
private fun TweakSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TweakChoice(title: String, tweak: StringTweak, options: List<String>, label: (String) -> String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(selected = tweak.value == option, onClick = { tweak.update(option) }, label = { Text(label(option)) })
            }
        }
    }
}

@Composable
private fun TweakChoice(title: String, tweak: IntTweak, options: List<Int>, label: (Int) -> String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = tweak.value == option,
                    onClick = { tweak.update(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

/** How long you have watched and listened, kept on the device. Shown in Privacy, resettable. */
object UsageStats {
    private var prefs: SharedPreferences? = null
    private var pendingVideo = 0
    private var pendingMusic = 0

    var videoSeconds by mutableIntStateOf(0)
        private set
    var musicSeconds by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE).also { prefs = it }
        videoSeconds = p.getInt("video", 0)
        musicSeconds = p.getInt("music", 0)
    }

    fun tick(music: Boolean) {
        if (music) { musicSeconds++; pendingMusic++ } else { videoSeconds++; pendingVideo++ }
        if (pendingVideo + pendingMusic >= 30) {
            pendingVideo = 0
            pendingMusic = 0
            prefs?.edit()?.putInt("video", videoSeconds)?.putInt("music", musicSeconds)?.apply()
        }
    }

    fun reset() {
        videoSeconds = 0
        musicSeconds = 0
        pendingVideo = 0
        pendingMusic = 0
        prefs?.edit()?.clear()?.apply()
    }

    fun format(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        return if (h > 0) "${h} h ${m} min" else "${m} min"
    }
}
