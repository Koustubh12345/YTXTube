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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Note
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * ADVANCED VIDEO PLAYER — Features 41 to 60
 *
 * 41. Custom player control positioning
 * 42. Multiple player layouts
 * 43. Custom fullscreen layout
 * 44. Floating glass controls
 * 45. Custom seekbar designs
 * 46. Chapter snapping
 * 47. Customisable chapter markers
 * 48. Video aspect-ratio presets
 * 49. Smart zoom and crop
 * 50. Custom video playback profiles
 * 51. Advanced AB looping
 * 52. Scene bookmarks
 * 53. Timestamped video notes
 * 54. Customisable player button grid
 * 55. Playback statistics HUD
 * 56. Advanced subtitle positioning
 * 57. Custom subtitle backgrounds
 * 58. Subtitle outline and shadow
 * 59. Custom subtitle fonts
 * 60. Subtitle offset presets
 */

// --- Feature 41: Player Control Positioning ---
enum class PlayerControlPosition(val label: String) {
    Center("Center Controls"),
    BottomAnchored("Bottom-Anchored Bar"),
    ErgonomicSplit("Ergonomic Split (Corners)")
}

// --- Feature 42: Multiple Player Layouts ---
enum class PlayerLayoutPreset(val label: String) {
    Standard("Standard Liquid Glass"),
    Minimalist("Minimalist Clean"),
    StudioPro("Studio Pro (Stats HUD)"),
    CinemaAmbient("Cinema Ambient")
}

// --- Feature 45: Custom Seekbar Designs ---
enum class SeekbarStyle(val label: String) {
    ClassicHairline("Classic Hairline"),
    ChunkyPill("Chunky Glass Pill"),
    GlowingGradient("Glowing Neon Gradient"),
    SegmentedChapters("Segmented Chapter Bar")
}

// --- Feature 48: Video Aspect-Ratio Presets ---
enum class AspectRatioPreset(val label: String, val aspect: Float) {
    Fit16x9("16:9 Standard", 16f / 9f),
    FillCrop("Crop to Fill (18:9)", 18f / 9f),
    Ultrawide21x9("21:9 CinemaScope", 21f / 9f),
    Classic4x3("4:3 Vintage", 4f / 3f),
    Square1x1("1:1 Square", 1f / 1f)
}

// --- Feature 50: Custom Playback Profiles ---
enum class PlaybackProfile(val label: String, val speed: Float, val skipSilence: Boolean, val loudness: Boolean) {
    Default("Normal", 1.0f, false, false),
    PodcastSpeech("Podcast / Lecture", 1.35f, true, true),
    CinemaFilm("Cinema Experience", 1.0f, false, false),
    SpeedLearner("Speed Learner", 1.75f, true, true),
    MusicFidelity("Music Audio", 1.0f, false, false)
}

// --- Feature 52: Scene Bookmarks Model ---
data class SceneBookmark(
    val id: String,
    val videoId: String,
    val positionMs: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

// --- Feature 53: Timestamped Video Notes Model ---
data class VideoNote(
    val id: String,
    val videoId: String,
    val positionMs: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

// --- Feature 56-59: Subtitle Customization Models ---
enum class SubtitleVerticalPosition(val label: String, val bottomPaddingDp: Int) {
    BottomStandard("Bottom (Standard)", 24),
    BottomHigh("Bottom (Above Controls)", 64),
    Middle("Middle Center", 180),
    Top("Top of Screen", 320)
}

enum class SubtitleBackgroundStyle(val label: String) {
    Transparent("Transparent"),
    TranslucentBlack("Translucent Black"),
    GlassFrosted("Liquid Glass Frosted"),
    SolidBlack("Solid Black"),
    NeonPill("Neon Pill Glow")
}

enum class SubtitleFontStyle(val label: String, val fontFamily: FontFamily) {
    Sans("Clean Sans-Serif", FontFamily.Default),
    Serif("Classic Serif", FontFamily.Serif),
    Monospace("Tech Monospace", FontFamily.Monospace),
    Rounded("Expressive Rounded", AppRoundedHeavy)
}

/**
 * Singleton managing all Advanced Video Player enhancements and state.
 */
object PlayerEnhancements {
    private const val PREFS = "player_enhancements_prefs"
    private var prefs: SharedPreferences? = null

    // 41. Control Positioning
    var controlPosition by mutableStateOf(PlayerControlPosition.Center)
        private set

    // 42. Layout Preset
    var layoutPreset by mutableStateOf(PlayerLayoutPreset.Standard)
        private set

    // 45. Seekbar Style
    var seekbarStyle by mutableStateOf(SeekbarStyle.GlowingGradient)
        private set

    // 46. Chapter Snapping (snap to chapter boundary if within 2500ms)
    var chapterSnappingEnabled by mutableStateOf(true)
        private set

    // 48. Video Aspect-Ratio
    var activeAspectPreset by mutableStateOf(AspectRatioPreset.Fit16x9)
        private set

    // 49. Smart Zoom Scale & Pan (X, Y)
    var panOffsetX by mutableFloatStateOf(0f)
    var panOffsetY by mutableFloatStateOf(0f)

    // 50. Playback Profile
    var activeProfile by mutableStateOf(PlaybackProfile.Default)
        private set

    // 51. Advanced AB Looping
    var abLoopEnabled by mutableStateOf(false)
        private set
    var loopPointAMs by mutableLongStateOf(-1L)
        private set
    var loopPointBMs by mutableLongStateOf(-1L)
        private set
    var loopIterationCount by mutableIntStateOf(0)
        private set

    // 52. Scene Bookmarks
    val bookmarks = mutableStateListOf<SceneBookmark>()

    // 53. Timestamped Video Notes
    val notes = mutableStateListOf<VideoNote>()

    // 54. Customizable Player Button Grid (loop, speed, pip, audioOnly, dislike, bookmark, abLoop)
    var showAbLoopButton by mutableStateOf(true)
    var showBookmarkButton by mutableStateOf(true)
    var showNotesButton by mutableStateOf(true)
    var showAudioOnlyButton by mutableStateOf(true)
    var showSpeedButton by mutableStateOf(true)
    var showStatsButton by mutableStateOf(true)

    // 56-59. Subtitle Styling
    var subtitlePosition by mutableStateOf(SubtitleVerticalPosition.BottomStandard)
        private set
    var subtitleBackground by mutableStateOf(SubtitleBackgroundStyle.TranslucentBlack)
        private set
    var subtitleFont by mutableStateOf(SubtitleFontStyle.Rounded)
        private set
    var subtitleTextSizeSp by mutableIntStateOf(16)
        private set

    // 60. Subtitle Offset (in ms)
    var subtitleOffsetMs by mutableLongStateOf(0L)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }

        val cpName = p.getString("ctrl_pos", PlayerControlPosition.Center.name)
        controlPosition = runCatching { PlayerControlPosition.valueOf(cpName ?: "") }.getOrDefault(PlayerControlPosition.Center)

        val lpName = p.getString("layout_preset", PlayerLayoutPreset.Standard.name)
        layoutPreset = runCatching { PlayerLayoutPreset.valueOf(lpName ?: "") }.getOrDefault(PlayerLayoutPreset.Standard)

        val sbName = p.getString("seekbar_style", SeekbarStyle.GlowingGradient.name)
        seekbarStyle = runCatching { SeekbarStyle.valueOf(sbName ?: "") }.getOrDefault(SeekbarStyle.GlowingGradient)

        chapterSnappingEnabled = p.getBoolean("chapter_snapping", true)

        val aspName = p.getString("aspect_preset", AspectRatioPreset.Fit16x9.name)
        activeAspectPreset = runCatching { AspectRatioPreset.valueOf(aspName ?: "") }.getOrDefault(AspectRatioPreset.Fit16x9)

        val profName = p.getString("playback_profile", PlaybackProfile.Default.name)
        activeProfile = runCatching { PlaybackProfile.valueOf(profName ?: "") }.getOrDefault(PlaybackProfile.Default)

        showAbLoopButton = p.getBoolean("btn_ab_loop", true)
        showBookmarkButton = p.getBoolean("btn_bookmark", true)
        showNotesButton = p.getBoolean("btn_notes", true)
        showAudioOnlyButton = p.getBoolean("btn_audio_only", true)
        showSpeedButton = p.getBoolean("btn_speed", true)
        showStatsButton = p.getBoolean("btn_stats", true)

        val subPos = p.getString("sub_pos", SubtitleVerticalPosition.BottomStandard.name)
        subtitlePosition = runCatching { SubtitleVerticalPosition.valueOf(subPos ?: "") }.getOrDefault(SubtitleVerticalPosition.BottomStandard)

        val subBg = p.getString("sub_bg", SubtitleBackgroundStyle.TranslucentBlack.name)
        subtitleBackground = runCatching { SubtitleBackgroundStyle.valueOf(subBg ?: "") }.getOrDefault(SubtitleBackgroundStyle.TranslucentBlack)

        val subFont = p.getString("sub_font", SubtitleFontStyle.Rounded.name)
        subtitleFont = runCatching { SubtitleFontStyle.valueOf(subFont ?: "") }.getOrDefault(SubtitleFontStyle.Rounded)

        subtitleTextSizeSp = p.getInt("sub_size", 16)
        subtitleOffsetMs = p.getLong("sub_offset", 0L)

        loadBookmarks()
        loadNotes()
    }

    // --- AB Looping Methods ---
    fun setPointA(posMs: Long) {
        loopPointAMs = posMs
        abLoopEnabled = (loopPointAMs >= 0 && loopPointBMs > loopPointAMs)
        loopIterationCount = 0
    }

    fun setPointB(posMs: Long) {
        loopPointBMs = posMs
        abLoopEnabled = (loopPointAMs >= 0 && loopPointBMs > loopPointAMs)
        loopIterationCount = 0
    }

    fun clearAbLoop() {
        abLoopEnabled = false
        loopPointAMs = -1L
        loopPointBMs = -1L
        loopIterationCount = 0
    }

    fun checkAbLoopBoundary(currentMs: Long, onSeekToA: (Long) -> Unit) {
        if (!abLoopEnabled || loopPointAMs < 0 || loopPointBMs <= loopPointAMs) return
        if (currentMs >= loopPointBMs) {
            loopIterationCount++
            onSeekToA(loopPointAMs)
        }
    }

    // --- Scene Bookmarks ---
    fun addBookmark(videoId: String, posMs: Long, title: String) {
        val bm = SceneBookmark(
            id = "${videoId}_$posMs",
            videoId = videoId,
            positionMs = posMs,
            title = title.ifBlank { "Bookmark at ${formatClock(posMs / 1000)}" }
        )
        bookmarks.add(0, bm)
        saveBookmarks()
    }

    fun removeBookmark(id: String) {
        bookmarks.removeAll { it.id == id }
        saveBookmarks()
    }

    fun bookmarksForVideo(videoId: String): List<SceneBookmark> =
        bookmarks.filter { it.videoId == videoId }

    // --- Timestamped Notes ---
    fun addNote(videoId: String, posMs: Long, text: String) {
        if (text.isBlank()) return
        val note = VideoNote(
            id = "${videoId}_${System.currentTimeMillis()}",
            videoId = videoId,
            positionMs = posMs,
            text = text.trim()
        )
        notes.add(0, note)
        saveNotes()
    }

    fun removeNote(id: String) {
        notes.removeAll { it.id == id }
        saveNotes()
    }

    fun notesForVideo(videoId: String): List<VideoNote> =
        notes.filter { it.videoId == videoId }

    // --- Subtitle Offsets ---
    fun adjustSubtitleOffset(deltaMs: Long) {
        subtitleOffsetMs += deltaMs
        prefs?.edit()?.putLong("sub_offset", subtitleOffsetMs)?.apply()
    }

    fun resetSubtitleOffset() {
        subtitleOffsetMs = 0L
        prefs?.edit()?.putLong("sub_offset", 0L)?.apply()
    }

    // Settings modifiers
    fun updateControlPosition(pos: PlayerControlPosition) {
        controlPosition = pos
        prefs?.edit()?.putString("ctrl_pos", pos.name)?.apply()
    }

    fun updateLayoutPreset(preset: PlayerLayoutPreset) {
        layoutPreset = preset
        prefs?.edit()?.putString("layout_preset", preset.name)?.apply()
    }

    fun updateSeekbarStyle(style: SeekbarStyle) {
        seekbarStyle = style
        prefs?.edit()?.putString("seekbar_style", style.name)?.apply()
    }

    fun updateAspectPreset(preset: AspectRatioPreset) {
        activeAspectPreset = preset
        prefs?.edit()?.putString("aspect_preset", preset.name)?.apply()
    }

    fun updateProfile(profile: PlaybackProfile) {
        activeProfile = profile
        prefs?.edit()?.putString("playback_profile", profile.name)?.apply()
    }

    fun toggleChapterSnapping(enabled: Boolean) {
        chapterSnappingEnabled = enabled
        prefs?.edit()?.putBoolean("chapter_snapping", enabled)?.apply()
    }

    private fun loadBookmarks() {
        val jsonStr = prefs?.getString("bookmarks_json", null) ?: return
        runCatching {
            val array = JSONArray(jsonStr)
            bookmarks.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                bookmarks.add(
                    SceneBookmark(
                        id = obj.getString("id"),
                        videoId = obj.getString("videoId"),
                        positionMs = obj.getLong("pos"),
                        title = obj.getString("title"),
                        createdAt = obj.optLong("time", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun saveBookmarks() {
        val array = JSONArray()
        bookmarks.forEach { bm ->
            val obj = JSONObject()
            obj.put("id", bm.id)
            obj.put("videoId", bm.videoId)
            obj.put("pos", bm.positionMs)
            obj.put("title", bm.title)
            obj.put("time", bm.createdAt)
            array.put(obj)
        }
        prefs?.edit()?.putString("bookmarks_json", array.toString())?.apply()
    }

    private fun loadNotes() {
        val jsonStr = prefs?.getString("notes_json", null) ?: return
        runCatching {
            val array = JSONArray(jsonStr)
            notes.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                notes.add(
                    VideoNote(
                        id = obj.getString("id"),
                        videoId = obj.getString("videoId"),
                        positionMs = obj.getLong("pos"),
                        text = obj.getString("text"),
                        createdAt = obj.optLong("time", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun saveNotes() {
        val array = JSONArray()
        notes.forEach { note ->
            val obj = JSONObject()
            obj.put("id", note.id)
            obj.put("videoId", note.videoId)
            obj.put("pos", note.positionMs)
            obj.put("text", note.text)
            obj.put("time", note.createdAt)
            array.put(obj)
        }
        prefs?.edit()?.putString("notes_json", array.toString())?.apply()
    }
}

private fun formatClock(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
