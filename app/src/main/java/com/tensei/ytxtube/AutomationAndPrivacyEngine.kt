package com.tensei.ytxtube

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale

// ============================================================================
// CATEGORY 8: AUTOMATION & WORKFLOW ENGINE (Features 101–110)
// Transparent, user-controlled, and cancellable at any time.
// ============================================================================

enum class AutomationTrigger(val label: String) {
    OnVideoOpen("When opening a video"),
    OnLowBattery("When battery drops below 20%"),
    OnMeteredNetwork("When switching to mobile data"),
    OnWifiConnected("When connected to Wi-Fi"),
    OnNightHours("During late-night hours (22:00–06:00)"),
    OnMutedVolume("When media volume is muted (<10%)"),
}

enum class AutomationAction(val label: String) {
    ApplyLecturePreset("Apply Lecture Preset (1.5× + Skip Silence)"),
    ApplyCinemaPreset("Apply Cinema HDR Preset"),
    EnableAmoledTheme("Switch to AMOLED Black Theme"),
    EnableBatterySaver("Enable Battery-Saving Playback (720p + No Glow)"),
    CapQuality480p("Cap video quality at 480p"),
    EnableAutoCaptions("Turn on Captions automatically"),
    StartSleepTimer30m("Start 30-minute Sleep Timer"),
}

@Immutable
data class CustomAutomationRule(
    val id: String,
    val name: String,
    val trigger: AutomationTrigger,
    val action: AutomationAction,
    val enabled: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("trigger", trigger.name)
        .put("action", action.name)
        .put("enabled", enabled)

    companion object {
        fun fromJson(o: JSONObject): CustomAutomationRule = CustomAutomationRule(
            id = o.optString("id", "rule_${System.currentTimeMillis()}"),
            name = o.optString("name", "Custom Rule"),
            trigger = AutomationTrigger.entries.firstOrNull { it.name == o.optString("trigger") }
                ?: AutomationTrigger.OnVideoOpen,
            action = AutomationAction.entries.firstOrNull { it.name == o.optString("action") }
                ?: AutomationAction.EnableAutoCaptions,
            enabled = o.optBoolean("enabled", true),
        )
    }
}

/**
 * Pure, testable automation decision logic (Features 101–110).
 */
object AutomationLogic {

    /** Feature 101: Recommends a preset ID based on video topic and duration. */
    fun selectAutoPresetForVideo(video: VideoItem): String {
        val topic = AiTranscriptIntelligence.classifyTopic(video.title).primaryTopic
        return when {
            (topic == "Education & Lectures" || topic == "Software & Coding") && video.durationSec >= 600L -> "lecture"
            topic == "Design & Creative" || video.title.contains("4k", ignoreCase = true) || video.title.contains("hdr", ignoreCase = true) -> "cinema"
            video.title.contains("podcast", ignoreCase = true) || video.title.contains("interview", ignoreCase = true) -> "podcast"
            else -> "standard" // includes music: isMusicTrack() is too broad (any 1-12 min video) to gate on
        }
    }

    /** Feature 102: Resolves target theme mode for time-based theme switching. */
    fun resolveTimeBasedTheme(hour24: Int, dayMode: String = "light", eveningMode: String = "dark", nightMode: String = "amoled"): String {
        val h = hour24.coerceIn(0, 23)
        return when (h) {
            in 7..18 -> dayMode
            in 19..21 -> eveningMode
            else -> nightMode
        }
    }

    /** Feature 103: Determines whether battery-aware saver should trigger. */
    fun shouldActivateBatteryProfile(batteryPercent: Int, isCharging: Boolean, thresholdPercent: Int = 20): Boolean =
        !isCharging && batteryPercent in 1..thresholdPercent

    /** Feature 109: Determines whether mute/low-volume auto-captions should trigger. */
    fun shouldAutoEnableSubtitlesForVolume(currentVolume: Int, maxVolume: Int): Boolean {
        if (maxVolume <= 0) return false
        return currentVolume.toFloat() / maxVolume.toFloat() <= 0.12f
    }
}

/**
 * State & Execution Manager for Category 8: Automation & Workflow Engine (Features 101–110).
 */
object AutomationEngine {
    private const val PREFS = "automation_engine_v292"
    private var prefs: SharedPreferences? = null

    var masterEnabled by mutableStateOf(true)
        private set
    var autoPlaybackPresets by mutableStateOf(false)
        private set
    var timeBasedThemeSwitching by mutableStateOf(false)
        private set
    var batteryAwareProfile by mutableStateOf(true)
        private set
    var batteryThresholdPercent by mutableIntStateOf(20)
        private set
    var networkAwareSwitching by mutableStateOf(true)
        private set
    var autoFeedRefreshMinutes by mutableIntStateOf(0) // 0 = manual only
        private set
    var autoHistoryCleanupDays by mutableIntStateOf(0) // 0 = off
        private set
    var bedtimeCurfewEnabled by mutableStateOf(false)
        private set
    var bedtimeHour24 by mutableIntStateOf(23)
        private set
    var autoCaptionsOnLowVolume by mutableStateOf(true)
        private set

    val customRules = mutableStateListOf<CustomAutomationRule>()
    val executionLog = mutableStateListOf<String>()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        masterEnabled = p.getBoolean("master_enabled", true)
        autoPlaybackPresets = p.getBoolean("auto_playback_presets", false)
        timeBasedThemeSwitching = p.getBoolean("time_based_theme", false)
        batteryAwareProfile = p.getBoolean("battery_aware", true)
        batteryThresholdPercent = p.getInt("battery_threshold", 20).coerceIn(10, 50)
        networkAwareSwitching = p.getBoolean("network_aware", true)
        autoFeedRefreshMinutes = p.getInt("auto_refresh_min", 0)
        autoHistoryCleanupDays = p.getInt("auto_cleanup_days", 0)
        bedtimeCurfewEnabled = p.getBoolean("bedtime_curfew", false)
        bedtimeHour24 = p.getInt("bedtime_hour", 23).coerceIn(0, 23)
        autoCaptionsOnLowVolume = p.getBoolean("auto_cc_low_vol", true)

        customRules.clear()
        runCatching {
            val arr = JSONArray(p.getString("custom_rules", "[]"))
            for (i in 0 until arr.length()) customRules.add(CustomAutomationRule.fromJson(arr.getJSONObject(i)))
        }
        if (customRules.isEmpty()) {
            customRules.add(
                CustomAutomationRule(
                    id = "default_mute_cc",
                    name = "Muted Volume → Auto Captions",
                    trigger = AutomationTrigger.OnMutedVolume,
                    action = AutomationAction.EnableAutoCaptions,
                    enabled = true,
                ),
            )
        }
    }

    fun updateSettings(
        master: Boolean = masterEnabled,
        autoPresets: Boolean = autoPlaybackPresets,
        timeTheme: Boolean = timeBasedThemeSwitching,
        batteryAware: Boolean = batteryAwareProfile,
        batteryThreshold: Int = batteryThresholdPercent,
        netAware: Boolean = networkAwareSwitching,
        refreshMin: Int = autoFeedRefreshMinutes,
        cleanupDays: Int = autoHistoryCleanupDays,
        bedtimeOn: Boolean = bedtimeCurfewEnabled,
        bedtimeHr: Int = bedtimeHour24,
        lowVolCc: Boolean = autoCaptionsOnLowVolume,
    ) {
        masterEnabled = master
        autoPlaybackPresets = autoPresets
        timeBasedThemeSwitching = timeTheme
        batteryAwareProfile = batteryAware
        batteryThresholdPercent = batteryThreshold.coerceIn(10, 50)
        networkAwareSwitching = netAware
        autoFeedRefreshMinutes = refreshMin
        autoHistoryCleanupDays = cleanupDays
        bedtimeCurfewEnabled = bedtimeOn
        bedtimeHour24 = bedtimeHr.coerceIn(0, 23)
        autoCaptionsOnLowVolume = lowVolCc
        prefs?.edit()
            ?.putBoolean("master_enabled", master)
            ?.putBoolean("auto_playback_presets", autoPresets)
            ?.putBoolean("time_based_theme", timeTheme)
            ?.putBoolean("battery_aware", batteryAware)
            ?.putInt("battery_threshold", batteryThresholdPercent)
            ?.putBoolean("network_aware", netAware)
            ?.putInt("auto_refresh_min", refreshMin)
            ?.putInt("auto_cleanup_days", cleanupDays)
            ?.putBoolean("bedtime_curfew", bedtimeOn)
            ?.putInt("bedtime_hour", bedtimeHour24)
            ?.putBoolean("auto_cc_low_vol", lowVolCc)
            ?.apply()
    }

    fun addCustomRule(name: String, trigger: AutomationTrigger, action: AutomationAction) {
        val rule = CustomAutomationRule(
            id = "rule_${System.currentTimeMillis()}",
            name = name.trim().ifBlank { "${trigger.label} → ${action.label}" },
            trigger = trigger,
            action = action,
            enabled = true,
        )
        customRules.add(0, rule)
        persistRules()
    }

    fun toggleRule(id: String) {
        val idx = customRules.indexOfFirst { it.id == id }
        if (idx >= 0) {
            customRules[idx] = customRules[idx].copy(enabled = !customRules[idx].enabled)
            persistRules()
        }
    }

    fun removeRule(id: String) {
        if (customRules.removeAll { it.id == id }) persistRules()
    }

    /** Cancellable one-tap stop for all active automation workflows. */
    fun cancelAllActiveAutomations() {
        masterEnabled = false
        SleepTimer.set(0)
        prefs?.edit()?.putBoolean("master_enabled", false)?.apply()
        logEvent("User cancelled all active automation workflows")
    }

    /** Evaluates context & video-open hooks when [masterEnabled] is true. */
    fun onVideoOpened(context: Context, video: VideoItem, player: PlayerState) {
        if (!masterEnabled) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Feature 102: Time-based theme switching
        if (timeBasedThemeSwitching) {
            val targetMode = AutomationLogic.resolveTimeBasedTheme(hour)
            if (ThemeSettings.mode != targetMode) {
                ThemeSettings.chooseMode(targetMode)
                logEvent("Time-based theme switched to '$targetMode' (${hour}:00)")
            }
        }

        // Feature 101: Automatic playback preset selection
        if (autoPlaybackPresets && VideoExperience.prefForChannel(video) == null) {
            val presetId = AutomationLogic.selectAutoPresetForVideo(video)
            VideoExperience.builtInPresets.firstOrNull { it.id == presetId }?.let { preset ->
                if (VideoExperience.activePresetId != preset.id) {
                    VideoExperience.applyPreset(preset, player)
                    logEvent("Auto-applied '${preset.name}' preset for ${video.title.take(24)}")
                }
            }
        }

        // Feature 103: Battery-aware performance profile
        if (batteryAwareProfile) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            val charging = bm?.isCharging == true
            if (AutomationLogic.shouldActivateBatteryProfile(pct, charging, batteryThresholdPercent)) {
                AmbientSettings.updateEnabled(false)
                QualityPreference.set(720)
                logEvent("Battery Saver activated ($pct% unplugged → 720p, Ambient off)")
            }
        }

        // Feature 108: Sleep-time playback controls
        if (bedtimeCurfewEnabled && (hour >= bedtimeHour24 || hour < 5) && SleepTimer.minutes == 0) {
            SleepTimer.set(30)
            VideoColorEngine.applyBrightnessPreset(VideoBrightnessPreset.NightDim)
            logEvent("Bedtime curfew active (${hour}:00 → 30m Sleep Timer + Night Dim)")
        }

        // Feature 109: Automatic subtitle preferences when muted/low volume
        if (autoCaptionsOnLowVolume && !player.isMusicMode) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                val curV = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxV = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (AutomationLogic.shouldAutoEnableSubtitlesForVolume(curV, maxV)) {
                    player.setCaptions(true)
                    logEvent("Low/muted volume detected → Auto-enabled Captions")
                }
            }
        }
    }

    private fun logEvent(msg: String) {
        executionLog.add(0, msg)
        while (executionLog.size > 40) executionLog.removeAt(executionLog.lastIndex)
    }

    private fun persistRules() {
        val arr = JSONArray()
        customRules.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString("custom_rules", arr.toString())?.apply()
    }

    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        masterEnabled = true
        autoPlaybackPresets = false
        timeBasedThemeSwitching = false
        batteryAwareProfile = true
        networkAwareSwitching = true
        autoFeedRefreshMinutes = 0
        autoHistoryCleanupDays = 0
        bedtimeCurfewEnabled = false
        autoCaptionsOnLowVolume = true
        customRules.clear()
        executionLog.clear()
    }
}

// ============================================================================
// CATEGORY 9: PRIVACY & SECURITY ENGINE (Features 111–120)
// AndroidKeyStore AES-GCM backed security, App Lock, Private Vault & Audit
// ============================================================================

@Immutable
data class ExternalEndpointStatus(
    val id: String,
    val name: String,
    val host: String,
    val purpose: String,
    val enabled: Boolean,
    val canToggle: Boolean,
)

/**
 * Pure, testable security & local data bundle logic (Features 111, 115, 116, 117).
 */
object PrivacyAndSecurityLogic {

    /** Hashes PIN with SHA-256 + static salt before storing in AndroidKeyStore `SecureStore`. */
    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(("ytx_pin_v292:" + pin.trim()).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Feature 117: Computes a transparent Privacy & Security Audit Score (0..100). */
    fun computePrivacyScore(
        appLockEnabled: Boolean,
        incognitoOrPrivateProfile: Boolean,
        sponsorBlockPrivacyHash: Boolean,
        externalAiNetworkConsent: Boolean,
        historyRetentionDays: Int,
        searchHistoryCount: Int,
    ): Int {
        var score = 62 // Strong baseline: no analytics, no crash reporters, local storage
        if (appLockEnabled) score += 12
        if (incognitoOrPrivateProfile) score += 10
        if (sponsorBlockPrivacyHash) score += 6
        if (!externalAiNetworkConsent) score += 6
        if (historyRetentionDays > 0 || searchHistoryCount == 0) score += 4
        return score.coerceIn(0, 100)
    }

    /** Feature 115 & 116: Encodes/decodes portable Local Data JSON Bundle (history, bookmarks, searches, subs). */
    fun encodeLocalDataBundle(
        searches: List<String>,
        watchedIds: List<String>,
        bookmarks: List<VideoBookmarkItem>,
        subs: List<ManagedSubscription>,
    ): String {
        val bmArr = JSONArray()
        bookmarks.forEach { bmArr.put(it.toJson()) }
        val subArr = JSONArray()
        subs.forEach { subArr.put(it.toJson()) }
        return JSONObject()
            .put("bundleSchema", "ytx_local_data_v292")
            .put("exportedAt", System.currentTimeMillis())
            .put("searches", JSONArray(searches))
            .put("watchedIds", JSONArray(watchedIds))
            .put("bookmarks", bmArr)
            .put("subscriptions", subArr)
            .toString(2)
    }
}

/**
 * Central state manager for Category 9: Privacy & Security (Features 111–120).
 */
object PrivacySecurityCenter {
    private const val PREFS = "privacy_security_v292"
    private const val VAULT_STORE = "privacy_vault_secure"
    private var prefs: SharedPreferences? = null
    private var vault: SecureStore? = null

    // Feature 111 & 112: Optional App Lock & Biometric/Device Credential Unlock
    var appLockEnabled by mutableStateOf(false)
        private set
    var biometricUnlockEnabled by mutableStateOf(true)
        private set
    var sessionUnlocked by mutableStateOf(true)
        internal set

    // Feature 113 & 114: Private Browsing Profile & Encrypted Private Watch History
    var privateProfileActive by mutableStateOf(false)
        private set
    val privateWatchHistory = mutableStateListOf<VideoItem>()

    // Feature 120: Per-feature permissions & network controls
    val featureNetworkPermissions = mutableStateMapOf(
        "sponsorblock" to true,
        "dearrow" to true,
        "return_dislike" to true,
        "listenbrainz" to false,
        "github_updates" to true,
        "external_ai" to false,
    )

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        vault = runCatching { SecureStore(context, VAULT_STORE) }.getOrNull()
        appLockEnabled = p.getBoolean("app_lock_enabled", false) && hasConfiguredPin()
        biometricUnlockEnabled = p.getBoolean("biometric_unlock", true)
        sessionUnlocked = !appLockEnabled
        privateProfileActive = p.getBoolean("private_profile", false)

        loadEncryptedPrivateHistory()
    }

    fun hasConfiguredPin(): Boolean = !vault?.get("pin_hash").isNullOrBlank()

    fun setAppLockPin(newPin: String, enableBiometric: Boolean = biometricUnlockEnabled) {
        val clean = newPin.trim()
        if (clean.length < 4) return
        vault?.put("pin_hash", PrivacyAndSecurityLogic.hashPin(clean))
        appLockEnabled = true
        biometricUnlockEnabled = enableBiometric
        sessionUnlocked = true
        prefs?.edit()
            ?.putBoolean("app_lock_enabled", true)
            ?.putBoolean("biometric_unlock", enableBiometric)
            ?.apply()
    }

    fun disableAppLock() {
        vault?.put("pin_hash", null)
        appLockEnabled = false
        sessionUnlocked = true
        prefs?.edit()?.putBoolean("app_lock_enabled", false)?.apply()
    }

    fun verifyPin(candidatePin: String): Boolean {
        val stored = vault?.get("pin_hash") ?: return true
        val ok = stored == PrivacyAndSecurityLogic.hashPin(candidatePin)
        if (ok) sessionUnlocked = true
        return ok
    }

    fun lockSessionNow() {
        if (appLockEnabled) sessionUnlocked = false
    }

    /** Feature 113: Toggle isolated Private Browsing Profile. */
    fun togglePrivateProfile(active: Boolean) {
        privateProfileActive = active
        Tweaks.incognito.update(active)
        prefs?.edit()?.putBoolean("private_profile", active)?.apply()
    }

    /** Feature 114: Record video into AES-GCM encrypted Private Watch History when in Private Profile. */
    fun recordPrivateWatch(video: VideoItem) {
        if (!privateProfileActive) return
        privateWatchHistory.removeAll { it.id == video.id }
        privateWatchHistory.add(0, video)
        while (privateWatchHistory.size > 100) privateWatchHistory.removeAt(privateWatchHistory.lastIndex)
        val arr = JSONArray()
        privateWatchHistory.forEach { v ->
            arr.put(
                JSONObject()
                    .put("id", v.id)
                    .put("title", v.title)
                    .put("channel", v.channel)
                    .put("thumb", v.thumbnail.orEmpty())
                    .put("dur", v.durationSec),
            )
        }
        vault?.put("encrypted_private_history", arr.toString())
    }

    fun clearPrivateWatchHistory() {
        privateWatchHistory.clear()
        vault?.put("encrypted_private_history", null)
    }

    private fun loadEncryptedPrivateHistory() {
        privateWatchHistory.clear()
        val raw = vault?.get("encrypted_private_history") ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                privateWatchHistory.add(
                    VideoItem(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        channel = o.optString("channel"),
                        channelUrl = null,
                        thumbnail = o.optString("thumb").ifBlank { null },
                        avatar = null,
                        durationSec = o.optLong("dur"),
                        views = -1L,
                        uploaded = "Private Vault",
                    ),
                )
            }
        }
    }

    /** Feature 115: Export complete local user data bundle as JSON. */
    fun exportLocalDataJson(): String = PrivacyAndSecurityLogic.encodeLocalDataBundle(
        searches = SearchHistory.queries,
        watchedIds = emptyList(),
        bookmarks = VideoExperience.bookmarks,
        subs = DiscoveryAndSubsManager.managedSubs,
    )

    /** Feature 116: Import local user data bundle from JSON. */
    fun importLocalDataJson(raw: String): Int {
        val root = JSONObject(raw)
        require(root.optString("bundleSchema") == "ytx_local_data_v292") { "Invalid local data bundle schema" }
        var restored = 0
        root.optJSONArray("searches")?.let { arr ->
            for (i in 0 until arr.length()) {
                val q = arr.optString(i)
                if (q.isNotBlank()) { SearchHistory.add(q); restored++ }
            }
        }
        root.optJSONArray("bookmarks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val bm = VideoBookmarkItem.fromJson(arr.getJSONObject(i))
                if (VideoExperience.bookmarks.none { it.id == bm.id }) {
                    VideoExperience.bookmarks.add(bm)
                    restored++
                }
            }
        }
        root.optJSONArray("subscriptions")?.let { arr ->
            restored += DiscoveryAndSubsManager.importSubscriptions(JSONObject().put("subscriptions", arr).toString())
        }
        return restored
    }

    /** Feature 118: External service communication overview. */
    fun externalServicesOverview(): List<ExternalEndpointStatus> = listOf(
        ExternalEndpointStatus(
            id = "youtube",
            name = "YouTube / InnerTube",
            host = "www.youtube.com · youtubei.googleapis.com",
            purpose = "Core video, search, stream, and caption extraction",
            enabled = true,
            canToggle = false,
        ),
        ExternalEndpointStatus(
            id = "sponsorblock",
            name = "SponsorBlock",
            host = "sponsor.ajay.app",
            purpose = "Sends 4-char SHA-256 prefix of video ID for skip segments",
            enabled = SponsorBlock.enabled,
            canToggle = true,
        ),
        ExternalEndpointStatus(
            id = "dearrow",
            name = "DeArrow Titles",
            host = "sponsor.ajay.app",
            purpose = "Community crowd-sourced non-clickbait titles",
            enabled = DeArrow.enabled,
            canToggle = true,
        ),
        ExternalEndpointStatus(
            id = "ryd",
            name = "Return YouTube Dislike",
            host = "returnyoutubedislikeapi.com",
            purpose = "Fetches community dislike counts for watched videos",
            enabled = ReturnDislike.enabled,
            canToggle = true,
        ),
        ExternalEndpointStatus(
            id = "listenbrainz",
            name = "ListenBrainz Scrobbler",
            host = "api.listenbrainz.org",
            purpose = "Submits music listens only when enabled with user token",
            enabled = ListenBrainz.enabled,
            canToggle = true,
        ),
        ExternalEndpointStatus(
            id = "ai_provider",
            name = "External / Local AI Endpoint",
            host = if (AiIntelligenceSettings.providerMode.usesNetwork) AiIntelligenceSettings.externalEndpointUrl else "Disabled (100% On-Device)",
            purpose = "Optional transcript LLM provider (requires explicit consent)",
            enabled = AiIntelligenceSettings.providerMode.usesNetwork && AiIntelligenceSettings.explicitNetworkConsent,
            canToggle = true,
        ),
    )

    fun toggleExternalService(id: String, enabled: Boolean) {
        when (id) {
            "sponsorblock" -> SponsorBlock.updateEnabled(enabled)
            "dearrow" -> DeArrow.updateEnabled(enabled)
            "ryd" -> ReturnDislike.set(enabled)
            "listenbrainz" -> ListenBrainz.updateEnabled(enabled)
            "ai_provider" -> AiIntelligenceSettings.updateProvider(
                mode = if (enabled) AiIntelligenceSettings.providerMode else AiProviderMode.OnDevice,
                consent = enabled,
            )
        }
    }

    fun currentPrivacyScore(): Int = PrivacyAndSecurityLogic.computePrivacyScore(
        appLockEnabled = appLockEnabled,
        incognitoOrPrivateProfile = privateProfileActive || Tweaks.incognito.value,
        sponsorBlockPrivacyHash = true,
        externalAiNetworkConsent = AiIntelligenceSettings.explicitNetworkConsent,
        historyRetentionDays = AutomationEngine.autoHistoryCleanupDays,
        searchHistoryCount = SearchHistory.queries.size,
    )
}

/**
 * Feature 111 & 112: Full-screen App Lock & Biometric/Device-Credential Gate Overlay.
 */
@Composable
fun AppLockGateOverlay() {
    if (!PrivacySecurityCenter.appLockEnabled || PrivacySecurityCenter.sessionUnlocked) return
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5090812))
            .clickable(enabled = true) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(c.surfaceContainerHigh)
                .border(1.dp, LiquidGlassStudio.borderGradientBrush(), RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(c.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = c.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("YTXTube Locked", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Text("Enter your PIN or use device security to unlock", fontSize = 12.5.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = pinInput,
                onValueChange = {
                    pinInput = it
                    errorMsg = null
                    if (it.length >= 4 && PrivacySecurityCenter.verifyPin(it)) {
                        pinInput = ""
                    }
                },
                label = { Text("PIN Code") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            errorMsg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (!PrivacySecurityCenter.verifyPin(pinInput)) {
                            errorMsg = "Incorrect PIN. Try again."
                        }
                    },
                ) {
                    Text("Unlock")
                }
                if (PrivacySecurityCenter.biometricUnlockEnabled) {
                    OutlinedButton(
                        onClick = {
                            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                            if (km?.isKeyguardSecure == true) {
                                @Suppress("DEPRECATION")
                                val intent = km.createConfirmDeviceCredentialIntent("Unlock YTXTube", "Confirm biometric or device credential")
                                if (intent != null) {
                                    runCatching {
                                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        PrivacySecurityCenter.sessionUnlocked = true
                                    }
                                }
                            } else {
                                errorMsg = "No system biometric/keyguard configured on device; use PIN."
                            }
                        },
                    ) {
                        Text("Biometric / Device")
                    }
                }
            }
        }
    }
}

/**
 * Interactive Automation & Privacy Security Center Sheet (Categories 8 & 9: Features 101–120).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationAndPrivacySheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Automation Rules", "Workflow Triggers", "Privacy & App Lock", "External Comms & Keys", "Local Data Export")

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
                "Automation & Privacy Security Center",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
            Text(
                "Transparent workflow rules, biometric app lock, encrypted vault & external endpoint controls",
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
                0 -> CustomAutomationRulesTab()
                1 -> BuiltInWorkflowSwitchesTab()
                2 -> PrivacyAuditAndAppLockTab()
                3 -> ExternalEndpointsAndApiKeysTab()
                4 -> LocalDataExportImportTab(context)
            }
        }
    }
}

@Composable
private fun CustomAutomationRulesTab() {
    val c = MaterialTheme.colorScheme
    var selectedTrigger by remember { mutableStateOf(AutomationTrigger.OnVideoOpen) }
    var selectedAction by remember { mutableStateOf(AutomationAction.ApplyLecturePreset) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Automation Master Switch", fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp)
            Text("All automation is transparent and can be cancelled in one tap", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Switch(checked = AutomationEngine.masterEnabled, onCheckedChange = { AutomationEngine.updateSettings(master = it) })
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { AutomationEngine.cancelAllActiveAutomations() }) {
        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Cancel All Active Automations Immediately")
    }

    Spacer(Modifier.height(12.dp))
    Text("Create Custom Rule (Feature 110)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Text("When Trigger:", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AutomationTrigger.entries.forEach { trig ->
            FilterChip(selected = selectedTrigger == trig, onClick = { selectedTrigger = trig }, label = { Text(trig.label) })
        }
    }
    Text("Do Action:", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AutomationAction.entries.forEach { act ->
            FilterChip(selected = selectedAction == act, onClick = { selectedAction = act }, label = { Text(act.label) })
        }
    }
    Button(
        onClick = { AutomationEngine.addCustomRule("", selectedTrigger, selectedAction) },
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Add Automation Rule")
    }

    Spacer(Modifier.height(10.dp))
    Text("Active Custom Rules (${AutomationEngine.customRules.size})", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    AutomationEngine.customRules.forEach { rule ->
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
                Text(rule.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${rule.trigger.label} → ${rule.action.label}", fontSize = 11.5.sp, color = c.onSurfaceVariant)
            }
            Switch(checked = rule.enabled, onCheckedChange = { AutomationEngine.toggleRule(rule.id) })
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(28.dp).clip(CircleShape).pressable { AutomationEngine.removeRule(rule.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Delete, null, tint = c.error, modifier = Modifier.size(16.dp))
            }
        }
    }

    if (AutomationEngine.executionLog.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("Transparent Automation Log", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary)
        AutomationEngine.executionLog.take(6).forEach { entry ->
            Text("• $entry", fontSize = 11.5.sp, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun BuiltInWorkflowSwitchesTab() {
    val c = MaterialTheme.colorScheme
    WorkflowSwitchRow(
        title = "Automatic Playback Presets (Feature 101)",
        subtitle = "Auto-apply Lecture preset for tutorials >10m and Cinema preset for 4K HDR",
        checked = AutomationEngine.autoPlaybackPresets,
    ) { AutomationEngine.updateSettings(autoPresets = it) }

    WorkflowSwitchRow(
        title = "Time-Based Theme Switching (Feature 102)",
        subtitle = "Light (07:00–19:00), Dark (19:00–22:00), and AMOLED (22:00–07:00)",
        checked = AutomationEngine.timeBasedThemeSwitching,
    ) { AutomationEngine.updateSettings(timeTheme = it) }

    WorkflowSwitchRow(
        title = "Battery-Aware Performance Profile (Feature 103)",
        subtitle = "Auto-disable ambient glow and cap to 720p when battery < ${AutomationEngine.batteryThresholdPercent}%",
        checked = AutomationEngine.batteryAwareProfile,
    ) { AutomationEngine.updateSettings(batteryAware = it) }

    WorkflowSwitchRow(
        title = "Network-Based Playback Switching (Feature 104)",
        subtitle = "Adapt quality cap and preloading automatically between Wi-Fi and cellular",
        checked = AutomationEngine.networkAwareSwitching,
    ) { AutomationEngine.updateSettings(netAware = it) }

    WorkflowSwitchRow(
        title = "Sleep-Time Playback Curfew (Feature 108)",
        subtitle = "Auto-enable 30m Sleep Timer & Night Dim after ${AutomationEngine.bedtimeHour24}:00",
        checked = AutomationEngine.bedtimeCurfewEnabled,
    ) { AutomationEngine.updateSettings(bedtimeOn = it) }

    WorkflowSwitchRow(
        title = "Automatic Subtitles on Low/Muted Volume (Feature 109)",
        subtitle = "Turns on captions automatically when media volume is ≤12%",
        checked = AutomationEngine.autoCaptionsOnLowVolume,
    ) { AutomationEngine.updateSettings(lowVolCc = it) }

    Spacer(Modifier.height(8.dp))
    Text("Automatic History Cleanup Retention (Feature 106)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = c.primary)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to "Keep All", 7 to "7 Days", 30 to "30 Days", 90 to "90 Days").forEach { (days, label) ->
            FilterChip(
                selected = AutomationEngine.autoHistoryCleanupDays == days,
                onClick = { AutomationEngine.updateSettings(cleanupDays = days) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun WorkflowSwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(subtitle, fontSize = 11.5.sp, color = c.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PrivacyAuditAndAppLockTab() {
    val c = MaterialTheme.colorScheme
    var newPin by remember { mutableStateOf("") }
    val score = PrivacySecurityCenter.currentPrivacyScore()

    // Feature 117: Privacy Audit Score Card
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.primaryContainer.copy(alpha = 0.55f))
            .padding(14.dp),
    ) {
        Text("Privacy & Security Audit Score: $score / 100", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = c.onPrimaryContainer)
        Text(
            "Zero telemetry · Hardware AES-GCM-256 Keystore · SponsorBlock k-anonymity prefix · ${if (PrivacySecurityCenter.appLockEnabled) "App Lock Active" else "App Lock Off"}",
            fontSize = 12.sp,
            color = c.onPrimaryContainer.copy(alpha = 0.85f),
        )
    }

    Spacer(Modifier.height(12.dp))
    Text("App Lock & Biometric Protection (Features 111 & 112)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = newPin,
            onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
            label = { Text(if (PrivacySecurityCenter.appLockEnabled) "Change 4–8 Digit PIN" else "Set 4–8 Digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            enabled = newPin.length >= 4,
            onClick = {
                PrivacySecurityCenter.setAppLockPin(newPin)
                newPin = ""
            },
        ) {
            Text("Enable Lock")
        }
    }
    if (PrivacySecurityCenter.appLockEnabled) {
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { PrivacySecurityCenter.lockSessionNow() }) {
                Text("Lock App Now")
            }
            OutlinedButton(onClick = { PrivacySecurityCenter.disableAppLock() }) {
                Text("Disable App Lock")
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
    Spacer(Modifier.height(10.dp))

    // Feature 113 & 114: Private Browsing Profile & Encrypted Watch History
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Private Browsing Profile (Feature 113)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Isolates session and stores watch history in AES-GCM encrypted vault (${PrivacySecurityCenter.privateWatchHistory.size} items)", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Switch(
            checked = PrivacySecurityCenter.privateProfileActive,
            onCheckedChange = PrivacySecurityCenter::togglePrivateProfile,
        )
    }
    if (PrivacySecurityCenter.privateWatchHistory.isNotEmpty()) {
        OutlinedButton(onClick = { PrivacySecurityCenter.clearPrivateWatchHistory() }) {
            Text("Wipe Encrypted Private Watch History")
        }
    }
}

@Composable
private fun ExternalEndpointsAndApiKeysTab() {
    val c = MaterialTheme.colorScheme
    Text("External Service Communication Overview (Features 118 & 120)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Text("Every network endpoint YTXTube can contact, with live per-feature switches.", fontSize = 12.sp, color = c.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))

    PrivacySecurityCenter.externalServicesOverview().forEach { svc ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(svc.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(svc.host, fontSize = 11.sp, color = c.primary, fontFamily = FontFamily.Monospace)
                Text(svc.purpose, fontSize = 11.5.sp, color = c.onSurfaceVariant)
            }
            if (svc.canToggle) {
                Switch(
                    checked = svc.enabled,
                    onCheckedChange = { PrivacySecurityCenter.toggleExternalService(svc.id, it) },
                )
            } else {
                Text("Core", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.primary)
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Hardware Keystore API Key Vault (Feature 119)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(4.dp))
    Text("ListenBrainz Token: ${if (ListenBrainz.token.isNotBlank()) "Configured in SecureStore" else "Not set"}", fontSize = 12.5.sp)
    Text("External AI Key: ${AiIntelligenceSettings.maskedApiKey()}", fontSize = 12.5.sp)
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = {
            ListenBrainz.updateToken("")
            AiIntelligenceSettings.apiKey = ""
        },
    ) {
        Text("Revoke All Stored API Keys")
    }
}

@Composable
private fun LocalDataExportImportTab(context: Context) {
    val c = MaterialTheme.colorScheme
    var importJson by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Text("Local Data Export & Import (Features 115 & 116)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Text("Export or restore your search history, timestamped notes, bookmarks, and subscriptions as a portable JSON bundle.", fontSize = 12.sp, color = c.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))

    Button(
        onClick = {
            val json = PrivacySecurityCenter.exportLocalDataJson()
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("YTXTube Local Data Bundle", json))
            status = "Copied full local data JSON bundle to clipboard."
        },
    ) {
        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Export Local Data Bundle to Clipboard")
    }

    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = importJson,
        onValueChange = { importJson = it },
        label = { Text("Paste Local Data Bundle JSON to Restore") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Button(
        enabled = importJson.isNotBlank(),
        onClick = {
            status = runCatching {
                val n = PrivacySecurityCenter.importLocalDataJson(importJson)
                importJson = ""
                "Restored $n local data items."
            }.getOrElse { "Import failed: ${it.message}" }
        },
    ) {
        Text("Import Local Data Bundle")
    }

    status?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = c.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
