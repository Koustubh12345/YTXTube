package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * DOWNLOAD MANAGER ADVANCED — Features 96 to 110
 *
 * 96. Reorderable download queue
 * 97. Download priority
 * 98. Configurable simultaneous downloads
 * 99. Automatic retry
 * 100. Wi-Fi-only downloads
 * 101. Download scheduling
 * 102. Custom download locations
 * 103. Download categories
 * 104. Storage usage dashboard
 * 105. Automatic download cleanup
 * 106. Offline playlist support
 * 107. Download history
 * 108. Audio-only download profiles
 * 109. Download notification controls
 * 110. Smart storage management
 */

enum class AdvDownloadPriority(val label: String, val weight: Int) {
    High("High Priority", 3),
    Normal("Normal", 2),
    Low("Low Priority", 1)
}

enum class DownloadCategory(val label: String) {
    All("All Downloads"),
    Video("Videos"),
    Audio("Music / Audio"),
    Shorts("Shorts"),
    Playlists("Offline Playlists")
}

enum class AudioDownloadProfile(val label: String, val format: String, val bitrateKbps: Int) {
    M4aHigh("M4A High (AAC 256k)", "m4a", 256),
    OpusHigh("Opus Studio (160k)", "opus", 160),
    Mp3Legacy("MP3 Legacy (320k)", "mp3", 320)
}

data class DownloadHistoryItem(
    val id: String,
    val title: String,
    val channel: String,
    val fileSizeBytes: Long,
    val completedAt: Long,
    val format: String
)

data class OfflinePlaylist(
    val id: String,
    val title: String,
    val videoIds: MutableList<String> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)

object DownloadManagerAdvanced {
    private const val PREFS = "dl_manager_advanced_prefs"
    private var prefs: SharedPreferences? = null

    // 97. Priority mapping per task key
    val taskPriorities = mutableMapOf<String, AdvDownloadPriority>()

    // 98. Max Simultaneous Downloads (1 to 4)
    var maxSimultaneousDownloads by mutableIntStateOf(2)
        private set

    // 99. Auto-Retry failed chunks
    var autoRetryEnabled by mutableStateOf(true)
        private set
    var maxRetryCount by mutableIntStateOf(3)
        private set

    // 100. Wi-Fi Only Downloads
    var wifiOnlyDownloads by mutableStateOf(false)
        private set

    // 101. Download Scheduling (Hour range, e.g. 2 AM to 6 AM)
    var scheduleEnabled by mutableStateOf(false)
        private set
    var scheduleStartHour by mutableIntStateOf(2) // 2:00 AM
        private set
    var scheduleEndHour by mutableIntStateOf(6)   // 6:00 AM
        private set

    // 102. Custom Download Location
    var useExternalPublicDir by mutableStateOf(false)
        private set

    // 103. Active Category Filter
    var activeCategory by mutableStateOf(DownloadCategory.All)

    // 105. Auto Cleanup Days (0 = disabled, 30, 60, 90 days)
    var autoCleanupDays by mutableIntStateOf(0)
        private set
    var autoDeleteWatched by mutableStateOf(false)
        private set

    // 106. Offline Playlists
    val offlinePlaylists = mutableStateListOf<OfflinePlaylist>()

    // 107. Download History
    val history = mutableStateListOf<DownloadHistoryItem>()

    // 108. Audio Download Profile
    var audioProfile by mutableStateOf(AudioDownloadProfile.M4aHigh)
        private set

    // 109. Notification Controls
    var notifyOnComplete by mutableStateOf(true)
        private set
    var notifyProgress by mutableStateOf(true)
        private set

    // 110. Smart Storage Limit (in Gigabytes, 0 = unlimited, 5GB, 10GB, 20GB)
    var maxStorageLimitGb by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }

        maxSimultaneousDownloads = p.getInt("max_simultaneous", 2)
        autoRetryEnabled = p.getBoolean("auto_retry", true)
        maxRetryCount = p.getInt("max_retry_count", 3)
        wifiOnlyDownloads = p.getBoolean("wifi_only", false)

        scheduleEnabled = p.getBoolean("sched_enabled", false)
        scheduleStartHour = p.getInt("sched_start", 2)
        scheduleEndHour = p.getInt("sched_end", 6)

        useExternalPublicDir = p.getBoolean("ext_public_dir", false)
        autoCleanupDays = p.getInt("auto_cleanup_days", 0)
        autoDeleteWatched = p.getBoolean("auto_delete_watched", false)

        val profName = p.getString("audio_dl_prof", AudioDownloadProfile.M4aHigh.name)
        audioProfile = runCatching { AudioDownloadProfile.valueOf(profName ?: "") }.getOrDefault(AudioDownloadProfile.M4aHigh)

        notifyOnComplete = p.getBoolean("notify_complete", true)
        notifyProgress = p.getBoolean("notify_progress", true)
        maxStorageLimitGb = p.getInt("max_storage_gb", 0)

        loadOfflinePlaylists()
        loadHistory()
    }

    // 97. Set task priority
    fun setPriority(taskKey: String, priority: AdvDownloadPriority) {
        taskPriorities[taskKey] = priority
    }

    fun getPriority(taskKey: String): AdvDownloadPriority =
        taskPriorities[taskKey] ?: AdvDownloadPriority.Normal

    // 100. Can download now based on Wi-Fi and schedule
    fun isDownloadAllowedNow(context: Context): Boolean {
        if (wifiOnlyDownloads) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm?.isActiveNetworkMetered == true) return false
        }
        if (scheduleEnabled) {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (scheduleStartHour <= scheduleEndHour) {
                if (currentHour !in scheduleStartHour until scheduleEndHour) return false
            } else {
                // Overnight schedule (e.g. 23:00 to 06:00)
                if (currentHour !in scheduleStartHour..23 && currentHour !in 0 until scheduleEndHour) return false
            }
        }
        return true
    }

    // 106. Offline Playlist management
    fun createOfflinePlaylist(title: String) {
        if (title.isBlank()) return
        val pl = OfflinePlaylist(
            id = "offline_${System.currentTimeMillis()}",
            title = title.trim()
        )
        offlinePlaylists.add(pl)
        saveOfflinePlaylists()
    }

    fun addToOfflinePlaylist(playlistId: String, videoId: String) {
        val pl = offlinePlaylists.find { it.id == playlistId } ?: return
        if (videoId !in pl.videoIds) {
            pl.videoIds.add(videoId)
            saveOfflinePlaylists()
        }
    }

    fun removeFromOfflinePlaylist(playlistId: String, videoId: String) {
        val pl = offlinePlaylists.find { it.id == playlistId } ?: return
        pl.videoIds.remove(videoId)
        saveOfflinePlaylists()
    }

    // 107. Record completed download history
    fun recordDownloadHistory(title: String, channel: String, bytes: Long, format: String) {
        val item = DownloadHistoryItem(
            id = "hist_${System.currentTimeMillis()}",
            title = title,
            channel = channel,
            fileSizeBytes = bytes,
            completedAt = System.currentTimeMillis(),
            format = format
        )
        history.add(0, item)
        saveHistory()
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    // Updates
    fun updateSimultaneous(count: Int) {
        maxSimultaneousDownloads = count.coerceIn(1, 4)
        prefs?.edit()?.putInt("max_simultaneous", maxSimultaneousDownloads)?.apply()
    }

    fun toggleWifiOnly(enabled: Boolean) {
        wifiOnlyDownloads = enabled
        prefs?.edit()?.putBoolean("wifi_only", enabled)?.apply()
    }

    fun updateSchedule(enabled: Boolean, start: Int, end: Int) {
        scheduleEnabled = enabled
        scheduleStartHour = start.coerceIn(0, 23)
        scheduleEndHour = end.coerceIn(0, 23)
        prefs?.edit()
            ?.putBoolean("sched_enabled", enabled)
            ?.putInt("sched_start", scheduleStartHour)
            ?.putInt("sched_end", scheduleEndHour)
            ?.apply()
    }

    fun updateAudioProfile(profile: AudioDownloadProfile) {
        audioProfile = profile
        prefs?.edit()?.putString("audio_dl_prof", profile.name)?.apply()
    }

    fun updateCleanupSettings(days: Int, deleteWatched: Boolean) {
        autoCleanupDays = days
        autoDeleteWatched = deleteWatched
        prefs?.edit()
            ?.putInt("auto_cleanup_days", days)
            ?.putBoolean("auto_delete_watched", deleteWatched)
            ?.apply()
    }

    fun updateStorageQuota(limitGb: Int) {
        maxStorageLimitGb = limitGb.coerceAtLeast(0)
        prefs?.edit()?.putInt("max_storage_gb", maxStorageLimitGb)?.apply()
    }

    private fun loadOfflinePlaylists() {
        val json = prefs?.getString("offline_pl_json", null) ?: return
        runCatching {
            val array = JSONArray(json)
            offlinePlaylists.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val vidsArr = obj.optJSONArray("vids")
                val vids = mutableListOf<String>()
                if (vidsArr != null) {
                    for (j in 0 until vidsArr.length()) vids.add(vidsArr.getString(j))
                }
                offlinePlaylists.add(OfflinePlaylist(id, title, vids))
            }
        }
    }

    private fun saveOfflinePlaylists() {
        val array = JSONArray()
        offlinePlaylists.forEach { pl ->
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("title", pl.title)
            val vids = JSONArray()
            pl.videoIds.forEach { vids.put(it) }
            obj.put("vids", vids)
            array.put(obj)
        }
        prefs?.edit()?.putString("offline_pl_json", array.toString())?.apply()
    }

    private fun loadHistory() {
        val json = prefs?.getString("dl_history_json", null) ?: return
        runCatching {
            val array = JSONArray(json)
            history.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                history.add(
                    DownloadHistoryItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        channel = obj.optString("channel", ""),
                        fileSizeBytes = obj.getLong("bytes"),
                        completedAt = obj.getLong("time"),
                        format = obj.optString("fmt", "mp4")
                    )
                )
            }
        }
    }

    private fun saveHistory() {
        val array = JSONArray()
        history.take(50).forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("channel", item.channel)
            obj.put("bytes", item.fileSizeBytes)
            obj.put("time", item.completedAt)
            obj.put("fmt", item.format)
            array.put(obj)
        }
        prefs?.edit()?.putString("dl_history_json", array.toString())?.apply()
    }
}

/**
 * Feature 104: Storage Usage Dashboard Composable
 * Visual breakdown of device storage, video downloads, audio downloads, and cache.
 */
@Composable
fun StorageUsageDashboard(
    videoBytes: Long,
    audioBytes: Long,
    cacheBytes: Long,
    modifier: Modifier = Modifier
) {
    val statFs = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    val totalBytes = statFs?.let { it.blockCountLong * it.blockSizeLong } ?: (64L * 1024 * 1024 * 1024)
    val freeBytes = statFs?.let { it.availableBlocksLong * it.blockSizeLong } ?: (32L * 1024 * 1024 * 1024)

    val videoFrac = (videoBytes.toFloat() / totalBytes).coerceIn(0.01f, 1f)
    val audioFrac = (audioBytes.toFloat() / totalBytes).coerceIn(0.01f, 1f)
    val cacheFrac = (cacheBytes.toFloat() / totalBytes).coerceIn(0.01f, 1f)

    val c = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surfaceContainerLow)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Storage Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = c.onSurface
            )
            Text(
                text = "${formatStorage(videoBytes + audioBytes + cacheBytes)} used",
                style = MaterialTheme.typography.bodySmall,
                color = c.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))

        // Multi-segment storage bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(videoFrac)
                    .background(Color(0xFF00E5FF))
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(audioFrac)
                    .background(Color(0xFFFF007F))
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(cacheFrac)
                    .background(Color(0xFFFFD600))
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(1f - (videoFrac + audioFrac + cacheFrac).coerceAtMost(0.95f))
                    .background(Color.Transparent)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StorageLegendItem("Videos", formatStorage(videoBytes), Color(0xFF00E5FF))
            StorageLegendItem("Music", formatStorage(audioBytes), Color(0xFFFF007F))
            StorageLegendItem("Cache", formatStorage(cacheBytes), Color(0xFFFFD600))
            StorageLegendItem("Free", formatStorage(freeBytes), Color.Gray)
        }
    }
}

@Composable
private fun StorageLegendItem(label: String, value: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun formatStorage(bytes: Long): String {
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1024) String.format("%.1f GB", mb / 1024f) else String.format("%.0f MB", mb)
}
