package com.tensei.ytxtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where a settings search result leads. */
enum class SettingTarget { APPEARANCE, GENERAL, STORAGE, AMBIENT, TWEAKS, PRIVACY, PROFILES }

/**
 * One searchable setting. [target] is null for options that live inside the video player; those
 * show [where] so the user knows how to reach them.
 */
class SettingEntry(val title: String, val where: String, val keywords: String, val target: SettingTarget?)

private val SettingIndex = listOf(
    SettingEntry("Theme mode and colours", "Appearance & Theme", "appearance theme dark light amoled black colour color palette accent font glass blur style", SettingTarget.APPEARANCE),
    SettingEntry("Video gestures", "General Modification", "gestures swipe brightness volume", SettingTarget.GENERAL),
    SettingEntry("Gesture sensitivity", "YTX Tweaks", "gestures swipe brightness volume sensitivity", SettingTarget.TWEAKS),
    SettingEntry("Video and Shorts quality", "General Modification", "playback quality resolution hdr 4k 1080 fixed lock", SettingTarget.GENERAL),
    SettingEntry("Download folder and quality", "General Modification", "downloads folder location quality offline", SettingTarget.GENERAL),
    SettingEntry("Blocked keywords and channels", "General Modification", "block hide filter keywords channels", SettingTarget.GENERAL),
    SettingEntry("DeArrow titles", "General Modification", "dearrow clickbait titles thumbnails", SettingTarget.GENERAL),
    SettingEntry("Ambient light", "General Modification", "ambient light glow colours appearance intensity status bar fullscreen", SettingTarget.AMBIENT),
    SettingEntry("Feed filters", "YTX Tweaks", "hide shorts live watched length views filter feed search", SettingTarget.TWEAKS),
    SettingEntry("Seek step, hold speed and controls", "YTX Tweaks", "playback double tap seek hold speed controls hide delay remaining time haptics resume", SettingTarget.TWEAKS),
    SettingEntry("Watch page buttons", "YTX Tweaks", "share copy link browser mark watched download chapters comments related", SettingTarget.TWEAKS),
    SettingEntry("Incognito and history pausing", "YTX Tweaks", "incognito private history pause search watch resume privacy", SettingTarget.TWEAKS),
    SettingEntry("Data saver", "YTX Tweaks", "data saver mobile cellular quality cap preload", SettingTarget.TWEAKS),
    SettingEntry("Text and interface size", "YTX Tweaks", "text size font scale zoom interface appearance accessibility", SettingTarget.TWEAKS),
    SettingEntry("Video list layout", "YTX Tweaks", "thumbnail size title lines views upload date duration appearance", SettingTarget.TWEAKS),
    SettingEntry("Default speed, captions and skip", "YTX Tweaks", "default speed captions subtitles skip intro playback", SettingTarget.TWEAKS),
    SettingEntry("Start tab", "YTX Tweaks", "start tab home launch page", SettingTarget.TWEAKS),
    SettingEntry("Battery and notification settings", "YTX Tweaks", "battery optimisation background notifications permissions app info debug", SettingTarget.TWEAKS),
    SettingEntry("UI profiles", "Profiles and backup", "profile preset minimal liquid glass amoled cinematic music look theme custom", SettingTarget.PROFILES),
    SettingEntry("Accent colour (hex)", "Profiles and backup", "accent colour color hex theme custom primary", SettingTarget.PROFILES),
    SettingEntry("Back up and restore settings", "Profiles and backup", "backup restore export import copy paste settings theme", SettingTarget.PROFILES),
    SettingEntry("Subtitle style", "YTX Tweaks", "subtitles captions size colour color outline shadow background font", SettingTarget.TWEAKS),
    SettingEntry("Loading animation", "YTX Tweaks", "loading spinner animation style speed", SettingTarget.TWEAKS),
    SettingEntry("Lite mode and battery saver", "YTX Tweaks", "performance lite battery saver reduce motion ambient", SettingTarget.TWEAKS),
    SettingEntry("Screenshot", "YTX Tweaks", "screenshot capture frame save picture", SettingTarget.TWEAKS),
    SettingEntry("Icon-only navigation", "YTX Tweaks", "navigation bar labels icons only", SettingTarget.TWEAKS),
    SettingEntry("Lyrics timing", "YTX Tweaks", "lyrics offset sync bluetooth delay timing music", SettingTarget.TWEAKS),
    SettingEntry("Personalised related videos", "YTX Tweaks", "recommendations discovery related personalise personalize for you", SettingTarget.TWEAKS),
    SettingEntry("ListenBrainz scrobbling", "YTX Tweaks", "scrobble listenbrainz listens last.fm music history", SettingTarget.TWEAKS),
    SettingEntry("Frame step", "In the player: Settings", "playback frame step frame by frame pause", null),
    SettingEntry("Privacy dashboard", "Data & Privacy", "privacy history watch search clear delete cache data local remote logging", SettingTarget.PRIVACY),
    SettingEntry("Storage and cache", "Storage Usage", "storage cache clear space memory downloads", SettingTarget.STORAGE),
    SettingEntry("Equalizer", "In the player: Settings", "equalizer audio bass loudness boost presets", null),
    SettingEntry("Subtitles", "In the player: Settings", "subtitles captions cc language", null),
    SettingEntry("Playback speed", "In the player: Settings", "playback speed rate", null),
    SettingEntry("Skip silence", "In the player: Settings", "playback skip silence audio", null),
    SettingEntry("Loop video", "In the player: Settings", "playback loop repeat", null),
    SettingEntry("Stats for nerds", "In the player: Settings", "playback stats network buffer codec bitrate", null),
    SettingEntry("Sleep timer", "In the player: Settings", "sleep timer stop playback", null),
    SettingEntry("SponsorBlock", "In the player: Settings", "sponsorblock sponsor skip segments", null),
    SettingEntry("Lyrics", "In the music player", "lyrics synced music", null),
)

/** Settings you starred and the ones you opened most recently, kept on the device. */
object SettingsMemory {
    private var prefs: android.content.SharedPreferences? = null
    var favourites by mutableStateOf<Set<String>>(emptySet())
        private set
    var recents by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("settings_memory", Context.MODE_PRIVATE).also { prefs = it }
        favourites = p.getStringSet("fav", emptySet()).orEmpty().toSet()
        recents = p.getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() }
    }

    fun toggleFavourite(title: String) {
        favourites = if (title in favourites) favourites - title else favourites + title
        prefs?.edit()?.putStringSet("fav", favourites)?.apply()
    }

    fun opened(title: String) {
        recents = (listOf(title) + recents.filter { it != title }).take(5)
        prefs?.edit()?.putString("recent", recents.joinToString("\n"))?.apply()
    }
}

private fun search(query: String): List<SettingEntry> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    val matches = SettingIndex.filter { e ->
        val hay = (e.title + " " + e.where + " " + e.keywords).lowercase()
        words.all { it in hay }
    }
    // Starred first, then recently opened, then the rest in their usual order.
    return matches.sortedWith(
        compareBy<SettingEntry>({ if (it.title in SettingsMemory.favourites) 0 else 1 }, {
            val i = SettingsMemory.recents.indexOf(it.title)
            if (i < 0) Int.MAX_VALUE else i
        }),
    )
}

/** Type a word, get the settings that match it, tap one to jump straight there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSearchSheet(onPick: (SettingTarget) -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val results = remember(query) { search(query) }
    FloatingSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 20.dp)) {
            Text("Search settings", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Playback, HDR, gestures, privacy…") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (results.isEmpty()) {
                Text("No setting matches “$query”.", color = c.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
            }
            LazyColumn(Modifier.height(380.dp)) {
                items(results) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .pressable(pressedScale = 0.98f) {
                                SettingsMemory.opened(entry.title)
                                if (entry.target != null) onPick(entry.target) else onDismiss()
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            val recent = entry.title in SettingsMemory.recents
                            Text(entry.where + if (recent) " · recent" else "", color = c.onSurfaceVariant, fontSize = 12.sp)
                        }
                        val starred = entry.title in SettingsMemory.favourites
                        Text(
                            if (starred) "★" else "☆",
                            color = if (starred) c.primary else c.onSurfaceVariant,
                            fontSize = 22.sp,
                            modifier = Modifier.pressable { SettingsMemory.toggleFavourite(entry.title) }.padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Everything the app keeps about what you do, with one-tap clearing, and what leaves the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    FloatingSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 18.dp, bottom = 22.dp)) {
            Text("Privacy", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Spacer(Modifier.height(4.dp))
            Text("Stored only on this device", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

            ClearRow("Search history", "${SearchHistory.queries.size} searches", SearchHistory.queries.isNotEmpty()) { SearchHistory.clear() }
            ClearRow("Watched videos", "${Watched.count} remembered as watched", Watched.count > 0) { Watched.clear() }
            ClearRow("Resume positions", "${ResumeStore.positions.size} videos with a saved position", ResumeStore.positions.isNotEmpty()) { ResumeStore.clearAll() }
            ClearRow("Watch time", "Video ${UsageStats.format(UsageStats.videoSeconds)} · Music ${UsageStats.format(UsageStats.musicSeconds)}", UsageStats.videoSeconds + UsageStats.musicSeconds > 0) { UsageStats.reset() }
            ClearRow("Personalisation data", "${Discovery.count} watches used for related videos", Discovery.count > 0) { Discovery.clear() }
            ClearRow("Image and video cache", "Temporary files; they are downloaded again when needed", true) {
                context.cacheDir.deleteRecursively()
                context.externalCacheDir?.deleteRecursively()
            }

            Text("Leaves the device", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
            Disclosure("YouTube", "Every video, search and feed is fetched from YouTube through NewPipe Extractor, so YouTube sees those requests. If you sign in, they are tied to your Google account.")
            Disclosure("SponsorBlock", "Sends the first 4 characters of a hash of the video ID to sponsor.ajay.app to look up skippable segments. Set every category to ignore to stop it.")
            Disclosure("DeArrow", if (DeArrow.enabled) "On: sends the same short hash prefix to sponsor.ajay.app." else "Off: nothing is sent.")
            Disclosure("Return YouTube Dislike", if (ReturnDislike.enabled) "On: sends the video ID to returnyoutubedislikeapi.com." else "Off: nothing is sent.")
            Disclosure("ListenBrainz", if (ListenBrainz.enabled) "On: sends the title, artist and time of each music listen to api.listenbrainz.org with your token." else "Off: nothing is sent.")
            Disclosure("Updates", "Checking for updates asks api.github.com for the latest release of this app.")
            Spacer(Modifier.height(10.dp))
            Text("There is no analytics or crash reporting in this app.", color = c.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ClearRow(title: String, detail: String, enabled: Boolean, onClear: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(if (done) "Cleared" else detail, color = c.onSurfaceVariant, fontSize = 12.sp)
        }
        OutlinedButton(
            enabled = enabled && !done,
            onClick = {
                if (confirm) { onClear(); done = true; confirm = false } else confirm = true
            },
        ) { Text(if (confirm) "Tap to confirm" else "Clear") }
    }
}

@Composable
private fun Disclosure(title: String, body: String) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHigh).padding(12.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(body, color = c.onSurfaceVariant, fontSize = 12.5.sp)
    }
}
