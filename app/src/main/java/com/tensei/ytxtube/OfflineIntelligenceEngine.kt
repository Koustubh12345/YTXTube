package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.compose.ui.graphics.Color
import android.os.StatFs
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ============================================================================
// CATEGORY 5: OFFLINE INTELLIGENCE ENGINE (Features 56–70)
// Respects source platform rules and available download permissions.
// ============================================================================

enum class DownloadPriority(val label: String, val weight: Int) {
    High("High Priority", 3),
    Normal("Standard", 2),
    Low("Background Saver", 1),
}

enum class DownloadExpirationPolicy(val label: String, val maxAgeDays: Int?) {
    Never("Keep Forever", null),
    AfterWatched48h("48h After Watched", 2),
    SevenDays("Expire After 7 Days", 7),
    ThirtyDays("Expire After 30 Days", 30),
}

enum class DownloadQueueProfile(val label: String, val maxConcurrent: Int, val description: String) {
    Turbo("Turbo (4 Lanes)", 4, "Maximum parallel throughput on fast Wi-Fi"),
    Balanced("Balanced (2 Lanes)", 2, "Default smooth background downloading"),
    Saver("Battery & Data Saver (1 Lane)", 1, "Single sequential stream to minimize CPU/radio wake"),
}

enum class DownloadScheduleWindow(val label: String, val startHour: Int?, val endHour: Int?) {
    Immediate("Download Immediately", null, null),
    Overnight("Overnight (01:00–06:00)", 1, 6),
    OffPeak("Off-Peak (23:00–07:00)", 23, 7),
    MorningCommute("Pre-Commute (05:00–08:00)", 5, 8),
}

@Immutable
data class DownloadNotificationEntry(
    val timestampMs: Long,
    val title: String,
    val event: String,
    val isError: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("ts", timestampMs)
        .put("title", title)
        .put("event", event)
        .put("err", isError)

    companion object {
        fun fromJson(o: JSONObject): DownloadNotificationEntry = DownloadNotificationEntry(
            timestampMs = o.optLong("ts", System.currentTimeMillis()),
            title = o.optString("title"),
            event = o.optString("event"),
            isError = o.optBoolean("err", false),
        )
    }
}

/**
 * Pure, testable offline intelligence logic (Features 56, 60, 61, 62, 67).
 */
object OfflineIntelligenceLogic {

    /** Feature 56: Smart download rule — recommends target height (0 = audio-only) given duration & network. */
    fun recommendedDownloadHeight(
        video: VideoItem,
        meteredNetwork: Boolean,
        preferAudioForMusicAndLong: Boolean,
        defaultWifiHeight: Int = 1080,
        defaultCellularHeight: Int = 480,
    ): Int {
        if (preferAudioForMusicAndLong && isMusicTrack(video)) return 0
        if (preferAudioForMusicAndLong && video.durationSec >= 2700L && meteredNetwork) return 0
        val base = if (meteredNetwork) defaultCellularHeight else defaultWifiHeight
        return when {
            video.durationSec >= 3600L -> minOf(base, 720)
            video.durationSec in 1L..300L && !meteredNetwork -> maxOf(base, 1080)
            else -> base
        }
    }

    /** Feature 62: Duplicate detection across tasks (matches same videoId regardless of height). */
    fun findDuplicates(videoId: String, existingKeys: Collection<String>): List<String> =
        existingKeys.filter { it.substringBefore('@') == videoId }

    /** Feature 67: Checks whether current hour (0..23) falls inside [window]. */
    fun isWithinScheduleWindow(window: DownloadScheduleWindow, currentHour24: Int): Boolean {
        val start = window.startHour ?: return true
        val end = window.endHour ?: return true
        val h = currentHour24.coerceIn(0, 23)
        return if (start <= end) {
            h in start until end
        } else {
            // Wraps around midnight (e.g., 23:00 to 07:00)
            h >= start || h < end
        }
    }

    /** Feature 61: Full-text search and filter over downloaded tasks. */
    fun <T> filterOfflineItems(
        items: List<T>,
        query: String,
        collectionFilter: String?,
        titleOf: (T) -> String,
        channelOf: (T) -> String,
        collectionOf: (T) -> String,
    ): List<T> {
        val q = query.trim().lowercase(Locale.US)
        return items.filter { item ->
            val colOk = collectionFilter.isNullOrBlank() || collectionFilter == "All" ||
                collectionOf(item).equals(collectionFilter, ignoreCase = true)
            val qOk = q.isEmpty() ||
                titleOf(item).lowercase(Locale.US).contains(q) ||
                channelOf(item).lowercase(Locale.US).contains(q)
            colOk && qOk
        }
    }
}

/**
 * State manager for Category 5: Offline Intelligence (Features 56–70).
 */
object OfflineIntelligence {
    private const val PREFS = "offline_intelligence_v292"
    private var prefs: SharedPreferences? = null

    var smartRulesEnabled by mutableStateOf(true)
        private set
    var defaultWifiHeight by mutableIntStateOf(1080)
        private set
    var defaultCellularHeight by mutableIntStateOf(480)
        private set
    var autoAudioForMusic by mutableStateOf(true)
        private set

    var expirationPolicy by mutableStateOf(DownloadExpirationPolicy.Never)
        private set
    var queueProfile by mutableStateOf(DownloadQueueProfile.Balanced)
        private set
    var scheduleWindow by mutableStateOf(DownloadScheduleWindow.Immediate)
        private set
    var wifiOnlyDownloads by mutableStateOf(false)
        private set
    var storageReserveGb by mutableIntStateOf(2)
        private set

    // Feature 63: Offline playback statistics
    var offlinePlayCount by mutableIntStateOf(0)
        private set
    var offlineListenSeconds by mutableLongStateOf(0L)
        private set
    var bytesSavedByOffline by mutableLongStateOf(0L)
        private set

    // Per-task metadata: priority, collection, pinned/starred, createdAt
    val taskPriorities = mutableStateMapOf<String, DownloadPriority>()
    val taskCollections = mutableStateMapOf<String, String>()
    val pinnedTaskKeys = mutableStateListOf<String>()
    val offlineCollections = mutableStateListOf("All", "General", "Commute", "Flights & Travel", "Study Pack", "Music Vault")
    val pinnedPlaylists = mutableStateListOf<String>()
    val notificationHistory = mutableStateListOf<DownloadNotificationEntry>()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        smartRulesEnabled = p.getBoolean("smart_rules", true)
        defaultWifiHeight = p.getInt("wifi_height", 1080)
        defaultCellularHeight = p.getInt("cell_height", 480)
        autoAudioForMusic = p.getBoolean("auto_audio_music", true)
        expirationPolicy = DownloadExpirationPolicy.entries.firstOrNull {
            it.name == p.getString("expiration_policy", "Never")
        } ?: DownloadExpirationPolicy.Never
        queueProfile = DownloadQueueProfile.entries.firstOrNull {
            it.name == p.getString("queue_profile", "Balanced")
        } ?: DownloadQueueProfile.Balanced
        scheduleWindow = DownloadScheduleWindow.entries.firstOrNull {
            it.name == p.getString("schedule_window", "Immediate")
        } ?: DownloadScheduleWindow.Immediate
        wifiOnlyDownloads = p.getBoolean("wifi_only", false)
        storageReserveGb = p.getInt("storage_reserve_gb", 2).coerceIn(1, 20)
        offlinePlayCount = p.getInt("offline_play_count", 0)
        offlineListenSeconds = p.getLong("offline_listen_sec", 0L)
        bytesSavedByOffline = p.getLong("bytes_saved", 0L)

        runCatching {
            val cols = JSONObject(p.getString("task_collections", "{}"))
            for (k in cols.keys()) taskCollections[k] = cols.optString(k, "General")
        }
        runCatching {
            val prios = JSONObject(p.getString("task_priorities", "{}"))
            for (k in prios.keys()) {
                taskPriorities[k] = DownloadPriority.entries.firstOrNull { it.name == prios.optString(k) }
                    ?: DownloadPriority.Normal
            }
        }
        pinnedTaskKeys.clear()
        p.getString("pinned_keys", "")?.split(",")?.filter { it.isNotBlank() }?.let(pinnedTaskKeys::addAll)

        notificationHistory.clear()
        runCatching {
            val arr = JSONArray(p.getString("notif_history", "[]"))
            for (i in 0 until arr.length()) {
                notificationHistory.add(DownloadNotificationEntry.fromJson(arr.getJSONObject(i)))
            }
        }
    }

    fun updateSmartRules(
        enabled: Boolean = smartRulesEnabled,
        wifiH: Int = defaultWifiHeight,
        cellH: Int = defaultCellularHeight,
        audioMusic: Boolean = autoAudioForMusic,
    ) {
        smartRulesEnabled = enabled
        defaultWifiHeight = wifiH
        defaultCellularHeight = cellH
        autoAudioForMusic = audioMusic
        prefs?.edit()
            ?.putBoolean("smart_rules", enabled)
            ?.putInt("wifi_height", wifiH)
            ?.putInt("cell_height", cellH)
            ?.putBoolean("auto_audio_music", audioMusic)
            ?.apply()
    }

    fun updatePolicies(
        expiration: DownloadExpirationPolicy = expirationPolicy,
        profile: DownloadQueueProfile = queueProfile,
        schedule: DownloadScheduleWindow = scheduleWindow,
        wifiOnly: Boolean = wifiOnlyDownloads,
        reserveGb: Int = storageReserveGb,
    ) {
        expirationPolicy = expiration
        queueProfile = profile
        scheduleWindow = schedule
        wifiOnlyDownloads = wifiOnly
        storageReserveGb = reserveGb.coerceIn(1, 20)
        prefs?.edit()
            ?.putString("expiration_policy", expiration.name)
            ?.putString("queue_profile", profile.name)
            ?.putString("schedule_window", schedule.name)
            ?.putBoolean("wifi_only", wifiOnly)
            ?.putInt("storage_reserve_gb", storageReserveGb)
            ?.apply()
    }

    /** Feature 60: Checks if device has enough free storage above the configured reserve floor. */
    fun hasStorageHeadroom(estimatedBytes: Long = 50L * 1024 * 1024): Boolean = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val reserveBytes = storageReserveGb * 1024L * 1024L * 1024L
        stat.availableBytes - estimatedBytes > reserveBytes
    }.getOrDefault(true)

    fun freeStorageBytes(): Long = runCatching {
        StatFs(Environment.getDataDirectory().path).availableBytes
    }.getOrDefault(0L)

    /** Feature 59: Assign priority and reorder queued tasks in `Downloads.tasks`. */
    fun setTaskPriority(taskKey: String, priority: DownloadPriority) {
        taskPriorities[taskKey] = priority
        val obj = JSONObject()
        taskPriorities.forEach { (k, v) -> obj.put(k, v.name) }
        prefs?.edit()?.putString("task_priorities", obj.toString())?.apply()
        sortQueuedTasksByPriority()
    }

    fun sortQueuedTasksByPriority() {
        val sorted = Downloads.tasks.sortedByDescending { task ->
            val p = taskPriorities[task.key] ?: DownloadPriority.Normal
            p.weight
        }
        Downloads.tasks.clear()
        Downloads.tasks.addAll(sorted)
    }

    /** Feature 64: Assign downloaded item to an offline collection. */
    fun setTaskCollection(taskKey: String, collection: String) {
        val clean = collection.trim().ifBlank { "General" }
        taskCollections[taskKey] = clean
        if (clean !in offlineCollections) offlineCollections.add(clean)
        val obj = JSONObject()
        taskCollections.forEach { (k, v) -> obj.put(k, v) }
        prefs?.edit()?.putString("task_collections", obj.toString())?.apply()
    }

    fun togglePinTask(taskKey: String) {
        if (taskKey in pinnedTaskKeys) pinnedTaskKeys.remove(taskKey) else pinnedTaskKeys.add(taskKey)
        prefs?.edit()?.putString("pinned_keys", pinnedTaskKeys.joinToString(","))?.apply()
    }

    /** Feature 63: Record an offline play and update saved bandwidth metrics. */
    fun recordOfflinePlayback(task: DownloadTask) {
        offlinePlayCount++
        offlineListenSeconds += task.durationSec.coerceAtLeast(60L)
        val fileBytes = task.file.length().takeIf { it > 0 } ?: task.total.coerceAtLeast(5_000_000L)
        bytesSavedByOffline += fileBytes
        prefs?.edit()
            ?.putInt("offline_play_count", offlinePlayCount)
            ?.putLong("offline_listen_sec", offlineListenSeconds)
            ?.putLong("bytes_saved", bytesSavedByOffline)
            ?.apply()
        logNotification(task.title, "Played offline (saved ${formatBytes(fileBytes)})", false)
    }

    /** Feature 65: Automatic storage cleanup (removes watched unpinned downloads & orphaned .part files). */
    fun runSmartStorageCleanup(): Long {
        var freedBytes = 0L
        val toRemove = Downloads.tasks.filter { task ->
            task.status == DownloadStatus.Complete &&
                task.key !in pinnedTaskKeys &&
                Watched.has(task.videoId)
        }
        toRemove.forEach { task ->
            freedBytes += task.file.length().coerceAtLeast(0L)
            Downloads.remove(task)
        }
        runCatching {
            Downloads.currentDownloadDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".part")) {
                    freedBytes += f.length()
                    f.delete()
                }
            }
        }
        logNotification("Smart Cleanup", "Removed ${toRemove.size} watched downloads (${formatBytes(freedBytes)} freed)", false)
        return freedBytes
    }

    /** Feature 66: Batch retry all failed downloads. */
    fun retryAllFailed(): Int {
        val failed = Downloads.tasks.filter { it.status == DownloadStatus.Failed }
        failed.forEach { Downloads.resume(it) }
        if (failed.isNotEmpty()) {
            logNotification("Batch Recovery", "Re-queued ${failed.size} failed downloads", false)
        }
        return failed.size
    }

    /** Feature 69: Download notification history logger. */
    fun logNotification(title: String, event: String, isError: Boolean) {
        notificationHistory.add(0, DownloadNotificationEntry(System.currentTimeMillis(), title, event, isError))
        while (notificationHistory.size > 60) notificationHistory.removeAt(notificationHistory.lastIndex)
        val arr = JSONArray()
        notificationHistory.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString("notif_history", arr.toString())?.apply()
    }

    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        smartRulesEnabled = true
        defaultWifiHeight = 1080
        defaultCellularHeight = 480
        autoAudioForMusic = true
        expirationPolicy = DownloadExpirationPolicy.Never
        queueProfile = DownloadQueueProfile.Balanced
        scheduleWindow = DownloadScheduleWindow.Immediate
        wifiOnlyDownloads = false
        storageReserveGb = 2
        offlinePlayCount = 0
        offlineListenSeconds = 0L
        bytesSavedByOffline = 0L
        taskPriorities.clear()
        taskCollections.clear()
        pinnedTaskKeys.clear()
        notificationHistory.clear()
    }
}

/**
 * Interactive Offline Intelligence Dashboard Sheet (Features 56–70).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineIntelligenceDashboardSheet(
    onOpenVideo: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Dashboard & Cleanup", "Smart Rules & Queue", "Search & Collections", "Notification Log")
    var cleanupNotice by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Offline Intelligence Center",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
            Text(
                "Smart download rules, duplicate guard, storage reserve, collections & offline telemetry",
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
                0 -> OfflineOverviewAndCleanupTab(cleanupNotice) { msg -> cleanupNotice = msg }
                1 -> SmartDownloadRulesAndScheduleTab()
                2 -> OfflineSearchAndCollectionsTab(onOpenVideo, onDismiss)
                3 -> DownloadNotificationHistoryTab()
            }
        }
    }
}

@Composable
private fun OfflineOverviewAndCleanupTab(
    cleanupNotice: String?,
    onNotice: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val tasks = Downloads.tasks
    val completed = tasks.filter { it.status == DownloadStatus.Complete }
    val failed = tasks.filter { it.status == DownloadStatus.Failed }
    val videoBytes = completed.filter { it.height > 0 }.sumOf { it.file.length().coerceAtLeast(0L) }
    val audioBytes = completed.filter { it.height == 0 }.sumOf { it.file.length().coerceAtLeast(0L) }
    val dupIds = tasks.groupBy { it.videoId }.filter { it.value.size > 1 }

    // Feature 70 & 63: Offline Library Dashboard & Playback Statistics
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceContainerHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Offline Library & Bandwidth Telemetry", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = c.primary)
        Text("Completed Files: ${completed.size} (${formatBytes(videoBytes)} video · ${formatBytes(audioBytes)} music)", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Text("Offline Plays: ${OfflineIntelligence.offlinePlayCount} sessions · ${OfflineIntelligence.offlineListenSeconds / 60} min played", fontSize = 12.5.sp)
        Text("Bandwidth Saved from Re-Streaming: ${formatBytes(OfflineIntelligence.bytesSavedByOffline)}", fontSize = 12.5.sp, color = c.primary, fontWeight = FontWeight.Bold)
        Text("Free Device Storage: ${formatBytes(OfflineIntelligence.freeStorageBytes())} (Reserve floor: ${OfflineIntelligence.storageReserveGb} GB)", fontSize = 12.sp, color = c.onSurfaceVariant)
        if (dupIds.isNotEmpty()) {
            Text("Duplicate Alert: ${dupIds.size} video(s) downloaded in multiple formats", fontSize = 12.sp, color = c.error, fontWeight = FontWeight.Bold)
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val freed = OfflineIntelligence.runSmartStorageCleanup()
                onNotice("Cleaned watched downloads & temp files (${formatBytes(freed)} freed)")
            },
        ) {
            Icon(Icons.Rounded.CleaningServices, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Smart Cleanup")
        }
        if (failed.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    val count = OfflineIntelligence.retryAllFailed()
                    onNotice("Re-queued $count failed downloads")
                },
            ) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry Failed (${failed.size})")
            }
        }
    }
    cleanupNotice?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = c.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmartDownloadRulesAndScheduleTab() {
    val c = MaterialTheme.colorScheme

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Smart Download Rules (Feature 56)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Auto-pick quality by duration, network & audio-only for music", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Switch(
            checked = OfflineIntelligence.smartRulesEnabled,
            onCheckedChange = { OfflineIntelligence.updateSmartRules(enabled = it) },
        )
    }

    Spacer(Modifier.height(8.dp))
    Text("Default Wi-Fi Download Quality: ${OfflineIntelligence.defaultWifiHeight}p", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(480, 720, 1080, 1440, 2160).forEach { h ->
            FilterChip(
                selected = OfflineIntelligence.defaultWifiHeight == h,
                onClick = { OfflineIntelligence.updateSmartRules(wifiH = h) },
                label = { Text("${h}p") },
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    Text("Default Cellular Download Quality: ${OfflineIntelligence.defaultCellularHeight}p", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(240, 360, 480, 720, 1080).forEach { h ->
            FilterChip(
                selected = OfflineIntelligence.defaultCellularHeight == h,
                onClick = { OfflineIntelligence.updateSmartRules(cellH = h) },
                label = { Text("${h}p") },
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Text("Queue Profile (Feature 68)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = c.primary)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DownloadQueueProfile.entries.forEach { prof ->
            FilterChip(
                selected = OfflineIntelligence.queueProfile == prof,
                onClick = { OfflineIntelligence.updatePolicies(profile = prof) },
                label = { Text(prof.label) },
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Text("Download Schedule Window (Feature 67)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = c.primary)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DownloadScheduleWindow.entries.forEach { win ->
            FilterChip(
                selected = OfflineIntelligence.scheduleWindow == win,
                onClick = { OfflineIntelligence.updatePolicies(schedule = win) },
                label = { Text(win.label) },
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Text("Expiration Policy (Feature 58)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = c.primary)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DownloadExpirationPolicy.entries.forEach { pol ->
            FilterChip(
                selected = OfflineIntelligence.expirationPolicy == pol,
                onClick = { OfflineIntelligence.updatePolicies(expiration = pol) },
                label = { Text(pol.label) },
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Text("Storage Reserve Floor (Feature 60): Keep ${OfflineIntelligence.storageReserveGb} GB Free", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1, 2, 5, 10).forEach { gb ->
            FilterChip(
                selected = OfflineIntelligence.storageReserveGb == gb,
                onClick = { OfflineIntelligence.updatePolicies(reserveGb = gb) },
                label = { Text("$gb GB") },
            )
        }
    }
}

@Composable
private fun OfflineSearchAndCollectionsTab(
    onOpenVideo: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var selectedCol by remember { mutableStateOf("All") }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search offline videos & music by title or channel…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OfflineIntelligence.offlineCollections.forEach { col ->
            FilterChip(selected = selectedCol == col, onClick = { selectedCol = col }, label = { Text(col) })
        }
    }

    val filtered = remember(Downloads.tasks.size, query, selectedCol, OfflineIntelligence.taskCollections.size) {
        OfflineIntelligenceLogic.filterOfflineItems(
            items = Downloads.tasks.toList(),
            query = query,
            collectionFilter = selectedCol,
            titleOf = { it.title },
            channelOf = { it.channel },
            collectionOf = { OfflineIntelligence.taskCollections[it.key] ?: "General" },
        )
    }

    Spacer(Modifier.height(8.dp))
    if (filtered.isEmpty()) {
        Text("No offline items match your filter.", color = c.onSurfaceVariant, fontSize = 13.sp)
    } else {
        filtered.forEach { task ->
            val col = OfflineIntelligence.taskCollections[task.key] ?: "General"
            val prio = OfflineIntelligence.taskPriorities[task.key] ?: DownloadPriority.Normal
            val pinned = task.key in OfflineIntelligence.pinnedTaskKeys
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceContainerHigh)
                    .pressable {
                        if (task.status == DownloadStatus.Complete) {
                            OfflineIntelligence.recordOfflinePlayback(task)
                            onOpenVideo(task.toVideoItem())
                            onDismiss()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${task.channel} · ${if (task.height == 0) "Audio" else "${task.height}p"} · [$col] · ${prio.label}",
                        fontSize = 11.5.sp,
                        color = c.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        val realCols = OfflineIntelligence.offlineCollections.filter { it != "All" }
                        val nextCol = realCols[(realCols.indexOf(col) + 1) % realCols.size]
                        OfflineIntelligence.setTaskCollection(task.key, nextCol)
                    },
                ) {
                    Text(col.take(8), fontSize = 10.5.sp)
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (pinned) c.primary else Color.Transparent)
                        .pressable { OfflineIntelligence.togglePinTask(task.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PushPin,
                        "Keep forever",
                        tint = if (pinned) c.onPrimary else c.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadNotificationHistoryTab() {
    val c = MaterialTheme.colorScheme
    Text("Download Activity & Recovery Log (Feature 69)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    if (OfflineIntelligence.notificationHistory.isEmpty()) {
        Text("No download events logged yet in this session.", color = c.onSurfaceVariant, fontSize = 13.sp)
    } else {
        OfflineIntelligence.notificationHistory.take(25).forEach { entry ->
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
                    if (entry.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.DownloadDone,
                    null,
                    tint = if (entry.isError) c.error else c.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.event, fontSize = 11.5.sp, color = c.onSurfaceVariant)
                }
            }
        }
    }
}
