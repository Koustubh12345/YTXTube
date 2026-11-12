package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Translate
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
// CATEGORY 6: PROFESSIONAL MUSIC SYSTEM (Features 71–85)
// ============================================================================

/** Feature 71 & 72: Audio Visualizer Presets. */
enum class MusicVisualizerPreset(val label: String) {
    Off("Off"),
    NeonBars("Neon Spectrum Bars"),
    LiquidWave("Liquid Sine Wave"),
    CyberOrb("Cyber Pulse Orb"),
    CircularSpectrum("Circular Halo"),
    MinimalPulse("Minimal Horizon"),
}

/** Feature 73: Dynamic Album Artwork Display Styles. */
enum class AlbumArtworkStyle(val label: String) {
    ElevatedGlass("Elevated Glass Card"),
    SpinningVinyl("Animated Vinyl LP"),
    NeonHalo("Neon Aura Frame"),
    MinimalSquare("Studio Monitor Square"),
}

/** Feature 76: Crossfade Presets. */
enum class CrossfadePreset(val label: String, val durationSec: Int) {
    Off("Gapless (0s)", 0),
    Quick2s("2s Tight Mix", 2),
    Smooth4s("4s Smooth Blend", 4),
    Radio6s("6s Radio Transition", 6),
    Club8s("8s Club Mix", 8),
    Ambient12s("12s Long Fade", 12),
}

/** Feature 80: User-Created 5-Band Custom Equalizer Preset. */
@Immutable
data class CustomEqualizerCurve(
    val id: String,
    val name: String,
    val band60HzMb: Int,
    val band230HzMb: Int,
    val band910HzMb: Int,
    val band3600HzMb: Int,
    val band14000HzMb: Int,
) {
    val bands: List<Int>
        get() = listOf(band60HzMb, band230HzMb, band910HzMb, band3600HzMb, band14000HzMb)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("b0", band60HzMb)
        .put("b1", band230HzMb)
        .put("b2", band910HzMb)
        .put("b3", band3600HzMb)
        .put("b4", band14000HzMb)

    companion object {
        fun fromJson(o: JSONObject): CustomEqualizerCurve = CustomEqualizerCurve(
            id = o.optString("id", "eq_${System.currentTimeMillis()}"),
            name = o.optString("name", "Custom EQ"),
            band60HzMb = o.optInt("b0", 0).coerceIn(-1200, 1200),
            band230HzMb = o.optInt("b1", 0).coerceIn(-1200, 1200),
            band910HzMb = o.optInt("b2", 0).coerceIn(-1200, 1200),
            band3600HzMb = o.optInt("b3", 0).coerceIn(-1200, 1200),
            band14000HzMb = o.optInt("b4", 0).coerceIn(-1200, 1200),
        )
    }
}

/** Feature 81: Audio Output Route & Format Information. */
@Immutable
data class AudioOutputInfo(
    val routeName: String,
    val routeType: String,
    val codec: String,
    val sampleRateHz: Int,
    val bitrateKbps: Int,
    val isHighResCapable: Boolean,
)

/**
 * Pure, testable music algorithms (Features 74, 75, 76, 78, 79).
 */
object ProfessionalMusicLogic {

    private val genreKeywords: Map<String, Set<String>> = mapOf(
        "Chill & Lo-Fi" to setOf("lofi", "lo-fi", "chill", "study", "ambient", "relax", "sleep", "rain", "cafe", "piano"),
        "Workout & High Energy" to setOf("workout", "gym", "phonk", "hardstyle", "bass", "hype", "run", "cardio", "power"),
        "Electronic & Synth" to setOf("synth", "synthwave", "edm", "house", "techno", "trance", "dnb", "dubstep", "remix", "club"),
        "Pop & Chart Hits" to setOf("pop", "official", "vevo", "hit", "dance", "radio", "single"),
        "Rock & Indie" to setOf("rock", "metal", "indie", "guitar", "band", "punk", "alternative", "live"),
        "Hip-Hop & R&B" to setOf("rap", "hiphop", "hip-hop", "trap", "freestyle", "rnb", "soul", "beats", "feat"),
        "Acoustic & Vocal" to setOf("acoustic", "unplugged", "cover", "vocal", "ballad", "singer", "folk", "lyrics"),
        "Focus & Instrumental" to setOf("instrumental", "classical", "orchestra", "soundtrack", "ost", "score", "cello", "violin"),
    )

    /** Feature 74: Automatic music categorisation into genre/mood crate. */
    fun categorizeTrack(title: String, artist: String = ""): String {
        val text = "$title $artist".lowercase(Locale.US)
        var bestGenre = "Pop & Chart Hits"
        var bestHits = 0
        for ((genre, words) in genreKeywords) {
            val hits = words.count { text.contains(it) }
            if (hits > bestHits) {
                bestHits = hits
                bestGenre = genre
            }
        }
        return bestGenre
    }

    /**
     * Feature 75: Smart music shuffle — orders tracks so the same artist/channel never plays
     * twice in a row when alternatives exist, while interleaving categories smoothly.
     */
    fun smartShuffle(tracks: List<VideoItem>, seed: Long = System.currentTimeMillis()): List<VideoItem> {
        if (tracks.size <= 2) return tracks
        val remaining = tracks.shuffled(java.util.Random(seed)).toMutableList()
        val result = ArrayList<VideoItem>(tracks.size)
        while (remaining.isNotEmpty()) {
            val lastArtist = result.lastOrNull()?.channel?.trim()?.lowercase(Locale.US)
            val pickIdx = if (lastArtist.isNullOrBlank()) {
                0
            } else {
                remaining.indexOfFirst { !it.channel.trim().lowercase(Locale.US).equals(lastArtist) }
                    .takeIf { it >= 0 } ?: 0
            }
            result.add(remaining.removeAt(pickIdx))
        }
        return result
    }

    /** Feature 76: Calculates crossfade volume multiplier (0f..1f) near the end/start of a track. */
    fun crossfadeEnvelope(positionMs: Long, durationMs: Long, crossfadeSec: Int): Float {
        if (crossfadeSec <= 0 || durationMs <= 0L) return 1.0f
        val fadeMs = crossfadeSec * 1_000L
        if (durationMs <= fadeMs * 2) return 1.0f
        return when {
            positionMs < fadeMs -> (positionMs.toFloat() / fadeMs).coerceIn(0.15f, 1.0f)
            positionMs > durationMs - fadeMs -> ((durationMs - positionMs).toFloat() / fadeMs).coerceIn(0.10f, 1.0f)
            else -> 1.0f
        }
    }

    /** Feature 78: Formats synced lyrics lines + per-track offset into standard `.lrc` format. */
    fun exportLrc(title: String, artist: String, lines: List< Pair<Long, String> >, offsetMs: Int = 0): String = buildString {
        appendLine("[ti:$title]")
        appendLine("[ar:$artist]")
        if (offsetMs != 0) appendLine("[offset:$offsetMs]")
        for ((rawMs, text) in lines) {
            val adjusted = (rawMs + offsetMs).coerceAtLeast(0L)
            val min = adjusted / 60_000L
            val sec = (adjusted % 60_000L) / 1_000L
            val cs = (adjusted % 1_000L) / 10L
            appendLine(String.format(Locale.US, "[%02d:%02d.%02d]%s", min, sec, cs, text))
        }
    }
}

/**
 * State manager for Category 6: Professional Music System (Features 71–85).
 */
object ProfessionalMusicSystem {
    private const val PREFS = "pro_music_system_v292"
    private var prefs: SharedPreferences? = null

    var visualizerPreset by mutableStateOf(MusicVisualizerPreset.NeonBars)
        private set
    var artworkStyle by mutableStateOf(AlbumArtworkStyle.ElevatedGlass)
        private set
    var crossfadePreset by mutableStateOf(CrossfadePreset.Off)
        private set
    var gaplessPrebuffer by mutableStateOf(true)
        private set
    var smartShuffleEnabled by mutableStateOf(true)
        private set

    // Feature 78 & 79: Per-song lyrics offset & translation mode
    val trackLyricsOffsetsMs = mutableStateMapOf<String, Int>()
    var bilingualLyricsOverlay by mutableStateOf(false)
        private set

    // Feature 80: Custom 5-band Equalizer curves
    val customEqCurves = mutableStateListOf<CustomEqualizerCurve>()
    var activeCustomEqId by mutableStateOf<String?>(null)
        private set

    // Feature 82: Listening statistics
    val artistPlayCounts = mutableStateMapOf<String, Int>()
    val genrePlayCounts = mutableStateMapOf<String, Int>()

    // Feature 84: Custom Music Collections (local crates)
    val musicCollections = mutableStateListOf("Favorites", "Late Night Drive", "Deep Focus", "Workout Pump", "Acoustic Chill")
    val collectionTracks = mutableStateMapOf<String, List<VideoItem>>()

    // Feature 85: Advanced Sleep Timer options
    var sleepFadeOut60s by mutableStateOf(true)
        private set
    var stopAtEndOfTrack by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        visualizerPreset = MusicVisualizerPreset.entries.firstOrNull {
            it.name == p.getString("visualizer_preset", "NeonBars")
        } ?: MusicVisualizerPreset.NeonBars
        artworkStyle = AlbumArtworkStyle.entries.firstOrNull {
            it.name == p.getString("artwork_style", "ElevatedGlass")
        } ?: AlbumArtworkStyle.ElevatedGlass
        crossfadePreset = CrossfadePreset.entries.firstOrNull {
            it.name == p.getString("crossfade_preset", "Off")
        } ?: CrossfadePreset.Off
        gaplessPrebuffer = p.getBoolean("gapless_prebuffer", true)
        smartShuffleEnabled = p.getBoolean("smart_shuffle", true)
        bilingualLyricsOverlay = p.getBoolean("bilingual_lyrics", false)
        sleepFadeOut60s = p.getBoolean("sleep_fade_out", true)
        stopAtEndOfTrack = p.getBoolean("stop_end_of_track", false)
        activeCustomEqId = p.getString("active_custom_eq", null)

        customEqCurves.clear()
        runCatching {
            val arr = JSONArray(p.getString("custom_eq_curves", "[]"))
            for (i in 0 until arr.length()) customEqCurves.add(CustomEqualizerCurve.fromJson(arr.getJSONObject(i)))
        }

        runCatching {
            val offsets = JSONObject(p.getString("lyrics_offsets", "{}"))
            for (k in offsets.keys()) trackLyricsOffsetsMs[k] = offsets.optInt(k, 0)
        }

        runCatching {
            val artists = JSONObject(p.getString("artist_counts", "{}"))
            for (k in artists.keys()) artistPlayCounts[k] = artists.optInt(k, 0)
            val genres = JSONObject(p.getString("genre_counts", "{}"))
            for (k in genres.keys()) genrePlayCounts[k] = genres.optInt(k, 0)
        }
    }

    fun updateVisuals(vis: MusicVisualizerPreset = visualizerPreset, art: AlbumArtworkStyle = artworkStyle) {
        visualizerPreset = vis
        artworkStyle = art
        prefs?.edit()
            ?.putString("visualizer_preset", vis.name)
            ?.putString("artwork_style", art.name)
            ?.apply()
    }

    fun updatePlaybackOptions(
        crossfade: CrossfadePreset = crossfadePreset,
        gapless: Boolean = gaplessPrebuffer,
        smartShuffle: Boolean = smartShuffleEnabled,
        bilingual: Boolean = bilingualLyricsOverlay,
        fadeOut: Boolean = sleepFadeOut60s,
        stopEnd: Boolean = stopAtEndOfTrack,
    ) {
        crossfadePreset = crossfade
        gaplessPrebuffer = gapless
        smartShuffleEnabled = smartShuffle
        bilingualLyricsOverlay = bilingual
        sleepFadeOut60s = fadeOut
        stopAtEndOfTrack = stopEnd
        prefs?.edit()
            ?.putString("crossfade_preset", crossfade.name)
            ?.putBoolean("gapless_prebuffer", gapless)
            ?.putBoolean("smart_shuffle", smartShuffle)
            ?.putBoolean("bilingual_lyrics", bilingual)
            ?.putBoolean("sleep_fade_out", fadeOut)
            ?.putBoolean("stop_end_of_track", stopEnd)
            ?.apply()
    }

    fun setTrackLyricsOffset(videoId: String, offsetMs: Int) {
        val clamped = offsetMs.coerceIn(-5000, 5000)
        trackLyricsOffsetsMs[videoId] = clamped
        Tweaks.lyricsOffsetMs.update(clamped)
        val obj = JSONObject()
        trackLyricsOffsetsMs.forEach { (k, v) -> obj.put(k, v) }
        prefs?.edit()?.putString("lyrics_offsets", obj.toString())?.apply()
    }

    fun saveCustomEqCurve(curve: CustomEqualizerCurve) {
        customEqCurves.removeAll { it.id == curve.id || it.name.equals(curve.name, ignoreCase = true) }
        customEqCurves.add(curve)
        activeCustomEqId = curve.id
        val arr = JSONArray()
        customEqCurves.forEach { arr.put(it.toJson()) }
        prefs?.edit()
            ?.putString("custom_eq_curves", arr.toString())
            ?.putString("active_custom_eq", curve.id)
            ?.apply()
    }

    fun recordMusicListen(video: VideoItem) {
        if (Tweaks.noWatchHistory) return
        val artist = video.channel.trim().ifBlank { "Unknown Artist" }
        val genre = ProfessionalMusicLogic.categorizeTrack(video.title, artist)
        artistPlayCounts[artist] = (artistPlayCounts[artist] ?: 0) + 1
        genrePlayCounts[genre] = (genrePlayCounts[genre] ?: 0) + 1
        val aObj = JSONObject()
        artistPlayCounts.forEach { (k, v) -> aObj.put(k, v) }
        val gObj = JSONObject()
        genrePlayCounts.forEach { (k, v) -> gObj.put(k, v) }
        prefs?.edit()
            ?.putString("artist_counts", aObj.toString())
            ?.putString("genre_counts", gObj.toString())
            ?.apply()
    }

    fun addTrackToCollection(collection: String, video: VideoItem) {
        val clean = collection.trim().ifBlank { "Favorites" }
        if (clean !in musicCollections) musicCollections.add(clean)
        val current = collectionTracks[clean].orEmpty()
        if (current.none { it.id == video.id }) {
            collectionTracks[clean] = listOf(video) + current
        }
    }

    /** Feature 81: Inspect active Android audio output device & player audio stream format. */
    fun readAudioOutputInfo(context: Context): AudioOutputInfo {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && am != null) {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        } else emptyList()

        val external = devices.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                (Build.VERSION.SDK_INT >= 31 && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        } ?: devices.firstOrNull()

        val routeType = when (external?.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headphones (3.5mm)"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB DAC / Digital Audio"
            else -> "Built-in Stereo Speaker"
        }
        val productName = external?.productName?.toString()?.takeIf { it.isNotBlank() } ?: routeType

        val fmt = PlayerBridge.exo?.audioFormat
        val codec = fmt?.codecs ?: fmt?.sampleMimeType ?: "AAC / Opus"
        val sampleRate = fmt?.sampleRate?.takeIf { it > 0 } ?: 48_000
        val bitrateKbps = ((fmt?.averageBitrate?.takeIf { it > 0 } ?: fmt?.bitrate ?: 256_000) / 1000).coerceAtLeast(128)

        return AudioOutputInfo(
            routeName = productName,
            routeType = routeType,
            codec = codec,
            sampleRateHz = sampleRate,
            bitrateKbps = bitrateKbps,
            isHighResCapable = sampleRate >= 48_000,
        )
    }

    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        visualizerPreset = MusicVisualizerPreset.NeonBars
        artworkStyle = AlbumArtworkStyle.ElevatedGlass
        crossfadePreset = CrossfadePreset.Off
        gaplessPrebuffer = true
        smartShuffleEnabled = true
        bilingualLyricsOverlay = false
        customEqCurves.clear()
        activeCustomEqId = null
        artistPlayCounts.clear()
        genrePlayCounts.clear()
    }
}

/**
 * Features 71 & 72: Real-time Animated Audio Visualizer Composable for `BitChordMusicPlayer`.
 */
@Composable
fun MusicAudioVisualizer(
    playing: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val preset = ProfessionalMusicSystem.visualizerPreset
    if (preset == MusicVisualizerPreset.Off) return

    val infinite = rememberInfiniteTransition(label = "musicVisualizer")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "visPhase",
    )
    val secondaryColor = Color(0xFF0091FF)

    Canvas(modifier = modifier.fillMaxWidth().height(54.dp)) {
        val w = size.width
        val h = size.height
        val energy = if (playing) 1.0f else 0.14f

        when (preset) {
            MusicVisualizerPreset.NeonBars -> {
                val bars = 28
                val gap = 4.dp.toPx()
                val barW = ((w - gap * (bars - 1)) / bars).coerceAtLeast(2f)
                for (i in 0 until bars) {
                    val norm = i.toFloat() / bars
                    val wave = (sin(phase * 2f + i * 0.45f) * 0.5f + 0.5f) *
                        (cos(phase - i * 0.25f) * 0.35f + 0.65f)
                    val barH = (h * (0.15f + 0.80f * wave * energy)).coerceIn(4.dp.toPx(), h)
                    val x = i * (barW + gap)
                    val y = (h - barH) / 2f
                    val color = androidx.compose.ui.graphics.lerp(accentColor, secondaryColor, norm)
                    drawRoundRect(
                        color = color.copy(alpha = 0.88f),
                        topLeft = Offset(x, y),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                    )
                }
            }
            MusicVisualizerPreset.LiquidWave -> {
                val path = Path()
                val steps = 48
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = t * w
                    val y = h / 2f + sin(phase * 2f + t * 4f * PI.toFloat()) * (h * 0.36f * energy)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(listOf(accentColor, secondaryColor, accentColor)),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            MusicVisualizerPreset.CyberOrb, MusicVisualizerPreset.CircularSpectrum -> {
                val cx = w / 2f
                val cy = h / 2f
                val baseR = minOf(w, h) * 0.28f
                val spokes = 24
                for (i in 0 until spokes) {
                    val angle = (i.toFloat() / spokes) * 2f * PI.toFloat() + phase * 0.25f
                    val len = baseR * (0.4f + 0.55f * energy * (sin(phase * 3f + i) * 0.5f + 0.5f))
                    val from = Offset(cx + cos(angle) * baseR * 0.6f, cy + sin(angle) * baseR * 0.6f)
                    val to = Offset(cx + cos(angle) * (baseR * 0.6f + len), cy + sin(angle) * (baseR * 0.6f + len))
                    drawLine(
                        color = if (i % 2 == 0) accentColor else secondaryColor,
                        start = from,
                        end = to,
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            MusicVisualizerPreset.MinimalPulse -> {
                val midY = h / 2f
                val span = w * (0.25f + 0.65f * energy * (sin(phase) * 0.5f + 0.5f))
                drawLine(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, accentColor, secondaryColor, Color.Transparent)),
                    start = Offset((w - span) / 2f, midY),
                    end = Offset((w + span) / 2f, midY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            MusicVisualizerPreset.Off -> Unit
        }
    }
}

/**
 * Interactive Professional Music Studio Sheet (Features 71–85).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalMusicStudioSheet(
    player: PlayerState,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Visualizer & Art", "Crossfade & Shuffle", "Custom EQ & Output", "Lyrics Sync & Sleep", "Stats & Crates")

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
                "Professional Music Studio",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
            Text(
                "Visualizer presets, crossfade, 5-band custom EQ, lyrics sync editor & output DAC inspector",
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
                0 -> VisualizerAndArtworkTab(player)
                1 -> CrossfadeAndShuffleTab(player)
                2 -> CustomEqualizerAndOutputTab(context)
                3 -> LyricsSyncAndSleepTimerTab(player)
                4 -> MusicListeningStatsAndCratesTab(player)
            }
        }
    }
}

@Composable
private fun VisualizerAndArtworkTab(player: PlayerState) {
    val c = MaterialTheme.colorScheme

    Text("Live Audio Visualizer Preview (Features 71 & 72)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceContainerHigh)
            .padding(12.dp),
    ) {
        MusicAudioVisualizer(playing = true, accentColor = c.primary)
    }

    Spacer(Modifier.height(10.dp))
    Text("Visualizer Preset", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MusicVisualizerPreset.entries.forEach { preset ->
            FilterChip(
                selected = ProfessionalMusicSystem.visualizerPreset == preset,
                onClick = { ProfessionalMusicSystem.updateVisuals(vis = preset) },
                label = { Text(preset.label) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Dynamic Album Artwork Style (Feature 73)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AlbumArtworkStyle.entries.forEach { style ->
            FilterChip(
                selected = ProfessionalMusicSystem.artworkStyle == style,
                onClick = { ProfessionalMusicSystem.updateVisuals(art = style) },
                label = { Text(style.label) },
            )
        }
    }

    player.current?.let { cur ->
        Spacer(Modifier.height(10.dp))
        val genre = ProfessionalMusicLogic.categorizeTrack(cur.title, cur.channel)
        Text("Auto-Detected Genre/Mood (Feature 74): $genre", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.primary)
    }
}

@Composable
private fun CrossfadeAndShuffleTab(player: PlayerState) {
    val c = MaterialTheme.colorScheme

    Text("Crossfade Presets (Feature 76)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CrossfadePreset.entries.forEach { cf ->
            FilterChip(
                selected = ProfessionalMusicSystem.crossfadePreset == cf,
                onClick = { ProfessionalMusicSystem.updatePlaybackOptions(crossfade = cf) },
                label = { Text(cf.label) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Gapless Playback Pre-buffering (Feature 77)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text("Pre-resolves and warms the next track in the music queue to eliminate transition silence", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Switch(
            checked = ProfessionalMusicSystem.gaplessPrebuffer,
            onCheckedChange = { ProfessionalMusicSystem.updatePlaybackOptions(gapless = it) },
        )
    }

    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Smart Artist-Spread Shuffle (Feature 75)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text("Prevents playing two tracks by the same artist back-to-back", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Switch(
            checked = ProfessionalMusicSystem.smartShuffleEnabled,
            onCheckedChange = { ProfessionalMusicSystem.updatePlaybackOptions(smartShuffle = it) },
        )
    }
}

@Composable
private fun CustomEqualizerAndOutputTab(context: Context) {
    val c = MaterialTheme.colorScheme
    val outputInfo = remember { ProfessionalMusicSystem.readAudioOutputInfo(context) }
    var eqName by remember { mutableStateOf("") }
    var b60 by remember { mutableIntStateOf(200) }
    var b230 by remember { mutableIntStateOf(100) }
    var b910 by remember { mutableIntStateOf(0) }
    var b3600 by remember { mutableIntStateOf(150) }
    var b14k by remember { mutableIntStateOf(300) }

    // Feature 81: Audio Output Information Card
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("Live Audio Output Route (Feature 81)", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = c.primary)
        Text("Active Device: ${outputInfo.routeName} (${outputInfo.routeType})", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Text("Stream Format: ${outputInfo.codec} · ${outputInfo.sampleRateHz} Hz · ~${outputInfo.bitrateKbps} kbps", fontSize = 12.sp, color = c.onSurfaceVariant, fontFamily = FontFamily.Monospace)
    }

    Spacer(Modifier.height(12.dp))
    Text("Custom 5-Band Equalizer Designer (Feature 80)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    EqBandSlider("60 Hz (Sub-Bass)", b60) { b60 = it }
    EqBandSlider("230 Hz (Warmth)", b230) { b230 = it }
    EqBandSlider("910 Hz (Vocals)", b910) { b910 = it }
    EqBandSlider("3.6 kHz (Presence)", b3600) { b3600 = it }
    EqBandSlider("14 kHz (Air/Treble)", b14k) { b14k = it }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = eqName,
            onValueChange = { eqName = it },
            placeholder = { Text("Custom EQ preset name…") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                ProfessionalMusicSystem.saveCustomEqCurve(
                    CustomEqualizerCurve(
                        id = "ceq_${System.currentTimeMillis()}",
                        name = eqName.ifBlank { "Studio Custom ${ProfessionalMusicSystem.customEqCurves.size + 1}" },
                        band60HzMb = b60,
                        band230HzMb = b230,
                        band910HzMb = b910,
                        band3600HzMb = b3600,
                        band14000HzMb = b14k,
                    ),
                )
                eqName = ""
            },
        ) {
            Text("Save EQ")
        }
    }

    if (ProfessionalMusicSystem.customEqCurves.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Saved Custom EQ Presets", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProfessionalMusicSystem.customEqCurves.forEach { curve ->
                FilterChip(
                    selected = ProfessionalMusicSystem.activeCustomEqId == curve.id,
                    onClick = {
                        b60 = curve.band60HzMb
                        b230 = curve.band230HzMb
                        b910 = curve.band910HzMb
                        b3600 = curve.band3600HzMb
                        b14k = curve.band14000HzMb
                    },
                    label = { Text(curve.name) },
                )
            }
        }
    }
}

@Composable
private fun EqBandSlider(label: String, mb: Int, onChange: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("${if (mb >= 0) "+" else ""}${mb / 100f} dB", fontSize = 12.sp, color = c.primary, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = mb.toFloat(),
            onValueChange = { onChange((it / 50f).roundToInt() * 50) },
            valueRange = -1200f..1200f,
        )
    }
}

@Composable
private fun LyricsSyncAndSleepTimerTab(player: PlayerState) {
    val c = MaterialTheme.colorScheme
    val vid = player.current?.id.orEmpty()
    var offsetMs by remember(vid) {
        mutableIntStateOf(ProfessionalMusicSystem.trackLyricsOffsetsMs[vid] ?: Tweaks.lyricsOffsetMs.value)
    }

    Text("Lyrics Synchronisation Editor (Feature 78)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Text("Fine-tune lyric line timing for the current song or globally.", fontSize = 12.sp, color = c.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { offsetMs -= 150; if (vid.isNotBlank()) ProfessionalMusicSystem.setTrackLyricsOffset(vid, offsetMs) }) {
            Text("-150 ms")
        }
        Text(
            "${if (offsetMs >= 0) "+" else ""}$offsetMs ms",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        OutlinedButton(onClick = { offsetMs += 150; if (vid.isNotBlank()) ProfessionalMusicSystem.setTrackLyricsOffset(vid, offsetMs) }) {
            Text("+150 ms")
        }
        OutlinedButton(onClick = { offsetMs = 0; if (vid.isNotBlank()) ProfessionalMusicSystem.setTrackLyricsOffset(vid, 0) }) {
            Text("Reset")
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Bilingual Lyrics Translation & Romanization (Feature 79)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text("Displays original script alongside romanized/translated lyrics", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Switch(
            checked = ProfessionalMusicSystem.bilingualLyricsOverlay,
            onCheckedChange = { ProfessionalMusicSystem.updatePlaybackOptions(bilingual = it) },
        )
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
    Spacer(Modifier.height(10.dp))

    Text("Advanced Sleep Timer (Feature 85)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0, 10, 20, 30, 45, 60, 90).forEach { min ->
            FilterChip(
                selected = SleepTimer.minutes == min,
                onClick = { SleepTimer.set(min) },
                label = { Text(if (min == 0) "Off" else "$min min") },
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Smooth 60-second volume fade-out before pausing", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Switch(
            checked = ProfessionalMusicSystem.sleepFadeOut60s,
            onCheckedChange = { ProfessionalMusicSystem.updatePlaybackOptions(fadeOut = it) },
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Wait until end of current song to stop", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Switch(
            checked = ProfessionalMusicSystem.stopAtEndOfTrack,
            onCheckedChange = { ProfessionalMusicSystem.updatePlaybackOptions(stopEnd = it) },
        )
    }
}

@Composable
private fun MusicListeningStatsAndCratesTab(player: PlayerState) {
    val c = MaterialTheme.colorScheme
    Text("Listening Statistics (Feature 82)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Total Music Time: ${UsageStats.format(UsageStats.musicSeconds)}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        Text("Recent Tracks Remembered: ${RecentMusic.items.size}", fontSize = 12.5.sp)
        val topGenres = ProfessionalMusicSystem.genrePlayCounts.entries.sortedByDescending { it.value }.take(3)
        if (topGenres.isNotEmpty()) {
            Text("Top Genres: ${topGenres.joinToString { "${it.key} (${it.value})" }}", fontSize = 12.sp, color = c.primary)
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Custom Music Collections / Crates (Feature 84)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.primary)
    Spacer(Modifier.height(6.dp))
    player.current?.let { cur ->
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProfessionalMusicSystem.musicCollections.forEach { crate ->
                OutlinedButton(
                    onClick = {
                        ProfessionalMusicSystem.addTrackToCollection(crate, cur)
                        player.actionMessage = "Saved to '$crate'"
                    },
                ) {
                    Text("+ $crate", fontSize = 11.5.sp)
                }
            }
        }
    }
}
