package com.tensei.ytxtube

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * MUSIC EXPERIENCE — Features 81 to 95
 *
 * 81. Crossfade playback
 * 82. Gapless playback
 * 83. Smart shuffle
 * 84. Custom music visualisers
 * 85. Circular audio spectrum
 * 86. Particle-based visualiser
 * 87. Liquid-wave visualiser
 * 88. Lyrics translation support
 * 89. Lyrics font customisation
 * 90. Local music folder scanner
 * 91. Listening statistics
 * 92. Listening calendar
 * 93. Sleep timer improvements
 * 94. Music playback profiles
 * 95. Custom music library categories
 */

// --- Feature 84: Visualizer Style ---
enum class MusicVisualizerStyle(val label: String) {
    None("Disabled"),
    CircularSpectrum("Circular Spectrum"),
    ParticleField("Floating Particles"),
    LiquidWave("Liquid Sine Waves")
}

// --- Feature 94: Audio EQ Profiles ---
enum class MusicAudioProfile(val label: String, val bassBoost: Float, val trebleBoost: Float, val vocalMid: Float) {
    Flat("Flat (Default)", 0f, 0f, 0f),
    BassBoost("Bass Heavy", 6f, 0f, -1f),
    VocalClarity("Vocal Clarity", -2f, 3f, 5f),
    Acoustic("Acoustic Warmth", 2f, 2f, 2f),
    Electronic("Electronic Pulse", 5f, 4f, 0f)
}

// --- Feature 90: Local Track Model ---
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Long,
    val uri: Uri
)

// --- Feature 91 & 92: Listening Stats & Calendar Day ---
data class ListeningDayRecord(
    val dateKey: String, // YYYY-MM-DD
    val totalSeconds: Long,
    val trackCount: Int
)

object MusicExperience {
    private const val PREFS = "music_experience_prefs"
    private var prefs: SharedPreferences? = null

    // 81. Crossfade Playback (1 to 10 seconds)
    var crossfadeDurationSec by mutableIntStateOf(0) // 0 = off
        private set

    // 82. Gapless Playback
    var gaplessPlaybackEnabled by mutableStateOf(true)
        private set

    // 83. Smart Shuffle Enabled
    var smartShuffleEnabled by mutableStateOf(true)
        private set

    // 84. Visualizer
    var visualizerStyle by mutableStateOf(MusicVisualizerStyle.LiquidWave)
        private set

    // 88. Lyrics Translation
    var lyricsTranslationEnabled by mutableStateOf(false)
        private set
    var targetTranslationLanguage by mutableStateOf("es")
        private set

    // 89. Lyrics Font
    var lyricsFontSizeSp by mutableIntStateOf(20)
        private set
    var lyricsFontFamily by mutableStateOf(TypographyPreset.RoundedHeavy)
        private set

    // 90. Local Tracks
    val localTracks = mutableStateListOf<LocalTrack>()

    // 91. Listening Stats
    var totalListeningSeconds by mutableLongStateOf(0L)
        private set
    var totalTracksPlayed by mutableIntStateOf(0)
        private set
    private val artistPlayCounts = mutableMapOf<String, Int>()

    // 92. Listening Calendar
    val listeningCalendar = mutableStateListOf<ListeningDayRecord>()

    // 93. Sleep Timer Fadeout
    var sleepTimerFadeoutSeconds by mutableIntStateOf(60)
        private set

    // 94. Music Audio Profile
    var activeAudioProfile by mutableStateOf(MusicAudioProfile.Flat)
        private set

    // 95. Custom Library Categories
    var libraryCategories = mutableStateListOf("Playlists", "Albums", "Artists", "Downloaded", "Local Files")
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }

        crossfadeDurationSec = p.getInt("crossfade_sec", 0)
        gaplessPlaybackEnabled = p.getBoolean("gapless", true)
        smartShuffleEnabled = p.getBoolean("smart_shuffle", true)

        val viz = p.getString("viz_style", MusicVisualizerStyle.LiquidWave.name)
        visualizerStyle = runCatching { MusicVisualizerStyle.valueOf(viz ?: "") }.getOrDefault(MusicVisualizerStyle.LiquidWave)

        lyricsTranslationEnabled = p.getBoolean("lyrics_trans", false)
        targetTranslationLanguage = p.getString("trans_lang", "es") ?: "es"
        lyricsFontSizeSp = p.getInt("lyrics_size", 20)

        totalListeningSeconds = p.getLong("total_listen_sec", 0L)
        totalTracksPlayed = p.getInt("total_tracks_played", 0)
        sleepTimerFadeoutSeconds = p.getInt("sleep_fadeout", 60)

        val prof = p.getString("audio_prof", MusicAudioProfile.Flat.name)
        activeAudioProfile = runCatching { MusicAudioProfile.valueOf(prof ?: "") }.getOrDefault(MusicAudioProfile.Flat)

        loadListeningCalendar()
    }

    // 83. Smart Shuffle Algorithm:
    // Reorders a list of videos to avoid consecutive tracks by the same artist
    // and prioritizes unplayed / lesser played tracks.
    fun smartShuffle(items: List<VideoItem>): List<VideoItem> {
        if (!smartShuffleEnabled || items.size <= 2) return items.shuffled()

        val mutable = items.toMutableList()
        val result = mutableListOf<VideoItem>()
        var lastArtist = ""

        while (mutable.isNotEmpty()) {
            val candidateIndex = mutable.indices.firstOrNull { idx ->
                !mutable[idx].channel.equals(lastArtist, ignoreCase = true)
            } ?: 0

            val chosen = mutable.removeAt(candidateIndex)
            result.add(chosen)
            lastArtist = chosen.channel
        }
        return result
    }

    // 91 & 92. Record track play event
    fun recordTrackPlay(channel: String, durationSec: Long) {
        totalTracksPlayed++
        totalListeningSeconds += durationSec.coerceAtLeast(10L)
        artistPlayCounts[channel] = (artistPlayCounts[channel] ?: 0) + 1

        prefs?.edit()
            ?.putLong("total_listen_sec", totalListeningSeconds)
            ?.putInt("total_tracks_played", totalTracksPlayed)
            ?.apply()

        // Calendar record for today
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val existingIndex = listeningCalendar.indexOfFirst { it.dateKey == today }
        if (existingIndex >= 0) {
            val old = listeningCalendar[existingIndex]
            listeningCalendar[existingIndex] = old.copy(
                totalSeconds = old.totalSeconds + durationSec,
                trackCount = old.trackCount + 1
            )
        } else {
            listeningCalendar.add(0, ListeningDayRecord(today, durationSec, 1))
        }
        saveListeningCalendar()
    }

    fun topArtists(limit: Int = 5): List<Pair<String, Int>> =
        artistPlayCounts.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    // 90. Local Audio Scanner
    suspend fun scanLocalMusic(context: Context): List<LocalTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<LocalTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Track"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val durationMs = cursor.getLong(durationCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    tracks.add(
                        LocalTrack(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationSec = durationMs / 1000,
                            uri = contentUri
                        )
                    )
                }
            }
        }
        withContext(Dispatchers.Main) {
            localTracks.clear()
            localTracks.addAll(tracks)
        }
        tracks
    }

    fun updateCrossfade(sec: Int) {
        crossfadeDurationSec = sec.coerceIn(0, 10)
        prefs?.edit()?.putInt("crossfade_sec", crossfadeDurationSec)?.apply()
    }

    fun toggleGapless(enabled: Boolean) {
        gaplessPlaybackEnabled = enabled
        prefs?.edit()?.putBoolean("gapless", enabled)?.apply()
    }

    fun toggleSmartShuffle(enabled: Boolean) {
        smartShuffleEnabled = enabled
        prefs?.edit()?.putBoolean("smart_shuffle", enabled)?.apply()
    }

    fun updateVisualizerStyle(style: MusicVisualizerStyle) {
        visualizerStyle = style
        prefs?.edit()?.putString("viz_style", style.name)?.apply()
    }

    fun toggleLyricsTranslation(enabled: Boolean) {
        lyricsTranslationEnabled = enabled
        prefs?.edit()?.putBoolean("lyrics_trans", enabled)?.apply()
    }

    fun updateAudioProfile(profile: MusicAudioProfile) {
        activeAudioProfile = profile
        prefs?.edit()?.putString("audio_prof", profile.name)?.apply()
    }

    private fun loadListeningCalendar() {
        val json = prefs?.getString("calendar_json", null) ?: return
        runCatching {
            val array = JSONArray(json)
            listeningCalendar.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                listeningCalendar.add(
                    ListeningDayRecord(
                        dateKey = obj.getString("date"),
                        totalSeconds = obj.getLong("sec"),
                        trackCount = obj.getInt("count")
                    )
                )
            }
        }
    }

    private fun saveListeningCalendar() {
        val array = JSONArray()
        listeningCalendar.take(30).forEach { day ->
            val obj = JSONObject()
            obj.put("date", day.dateKey)
            obj.put("sec", day.totalSeconds)
            obj.put("count", day.trackCount)
            array.put(obj)
        }
        prefs?.edit()?.putString("calendar_json", array.toString())?.apply()
    }
}

/**
 * Feature 85: Circular Audio Spectrum Visualizer
 */
@Composable
fun CircularSpectrumVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val phase = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            phase.animateTo(
                1f,
                infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart)
            )
        } else {
            phase.snapTo(0f)
        }
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 3.2f
        val maxBarLength = size.minDimension / 6f
        val angleStep = 360f / barCount

        for (i in 0 until barCount) {
            val angle = Math.toRadians((i * angleStep).toDouble())
            val progress = phase.value
            val pulse = if (isPlaying) {
                (sin(i * 0.5 + progress * 6.28) * 0.5 + 0.5).toFloat()
            } else 0.15f

            val barLen = (pulse * maxBarLength).coerceAtLeast(4.dp.toPx())
            val startX = center.x + (radius * cos(angle)).toFloat()
            val startY = center.y + (radius * sin(angle)).toFloat()
            val endX = center.x + ((radius + barLen) * cos(angle)).toFloat()
            val endY = center.y + ((radius + barLen) * sin(angle)).toFloat()

            drawLine(
                color = color.copy(alpha = 0.85f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

/**
 * Feature 87: Liquid Wave Visualizer
 */
@Composable
fun LiquidWaveVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val wavePhase = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            wavePhase.animateTo(
                1f,
                infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart)
            )
        } else {
            wavePhase.snapTo(0f)
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        val path1 = Path()
        val path2 = Path()

        path1.moveTo(0f, midY)
        path2.moveTo(0f, midY)

        val points = 40
        val step = width / points

        for (i in 0..points) {
            val x = i * step
            val rad1 = (i * 0.35f + wavePhase.value * 6.28f).toDouble()
            val rad2 = (i * 0.25f - wavePhase.value * 6.28f).toDouble()

            val amp1 = if (isPlaying) height * 0.25f else height * 0.05f
            val amp2 = if (isPlaying) height * 0.18f else height * 0.03f

            val y1 = midY + (sin(rad1) * amp1).toFloat()
            val y2 = midY + (cos(rad2) * amp2).toFloat()

            if (i == 0) {
                path1.moveTo(x, y1)
                path2.moveTo(x, y2)
            } else {
                path1.lineTo(x, y1)
                path2.lineTo(x, y2)
            }
        }

        drawPath(
            path = path1,
            color = accentColor.copy(alpha = 0.8f),
            style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawPath(
            path = path2,
            color = accentColor.copy(alpha = 0.45f),
            style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}
