package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import kotlinx.coroutines.Dispatchers
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.io.File
import java.util.Locale

/**
 * One selectable audio track from a video's adaptive audio list.
 *
 * [key] is stable across re-extractions because it is derived from YouTube's own track id,
 * language and itag rather than the (expiring) stream URL.
 */
data class AudioTrackOption(
    val key: String,
    val label: String,
    val detail: String?,
    val stream: AudioStream,
) {
    val isOriginal: Boolean
        get() = stream.audioTrackType == null || stream.audioTrackType == AudioTrackType.ORIGINAL
}

object AudioTrackPreference {
    private const val KEY = "audio_track_key"
    private const val LABEL = "audio_track_label"
    private var prefs: SharedPreferences? = null

    /** Null means Original, which is also the default for every new install. */
    var key by mutableStateOf<String?>(null)
        private set
    var label by mutableStateOf("Original")
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        key = prefs?.getString(KEY, null)
        label = prefs?.getString(LABEL, "Original") ?: "Original"
    }

    fun set(newKey: String?, newLabel: String) {
        key = newKey
        label = newLabel
        prefs?.edit()?.putString(KEY, newKey)?.putString(LABEL, newLabel)?.apply()
    }

    fun reset() = set(null, "Original")
}

object CaptionPreference {
    private const val KEY_ENABLED = "captions_enabled"
    private const val KEY_LANGUAGE = "captions_language"
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set
    var languageTag by mutableStateOf("en")
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY_ENABLED, false) == true
        languageTag = prefs?.getString(KEY_LANGUAGE, "en")?.ifBlank { "en" } ?: "en"
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }

    fun setLanguage(tag: String) {
        languageTag = tag.ifBlank { "en" }
        prefs?.edit()?.putString(KEY_LANGUAGE, languageTag)?.apply()
    }
}

/**
 * Landscape playback gestures: swipe up/down on the left half for brightness and on the right
 * half for volume. On by default because that is the YouTube behaviour, with a switch for
 * people who only want the seek/tap gestures.
 */
object VideoGestureSettings {
    private const val KEY_ENABLED = "video_gestures"

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY_ENABLED, true) ?: true
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }
}

/**
 * Shared Media3 cache. A Short replayed or a video re-opened plays from disk instead of
 * re-downloading, which is what makes loops and swipe-backs feel instant.
 */
@UnstableApi
object MediaCache {
    private const val MAX_BYTES = 320L * 1024L * 1024L

    @Volatile private var cache: SimpleCache? = null
    @Volatile private var factory: DataSource.Factory? = null

    @Synchronized
    fun cache(context: Context): SimpleCache? {
        cache?.let { return it }
        return runCatching {
            SimpleCache(
                File(context.cacheDir, "media"),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context),
            ).also { cache = it }
        }.getOrNull()
    }

    @Synchronized
    fun dataSourceFactory(context: Context): DataSource.Factory {
        factory?.let { return it }
        val upstream = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(Net.client).setUserAgent(Net.USER_AGENT),
        )
        val made = runCatching {
            cache(context)?.let { simple ->
                CacheDataSource.Factory()
                    .setCache(simple)
                    .setUpstreamDataSourceFactory(upstream)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            }
        }.getOrNull() ?: upstream
        factory = made
        return made
    }

    fun release() {
        runCatching { cache?.release() }
        cache = null
        factory = null
    }
}

/**
 * One logical audio track, not one encoding of it. YouTube serves the same dub at several
 * bitrates; grouping by track id / name / locale (and only then by kind) keeps the sheet to the
 * languages the video actually has instead of one row per itag.
 */
internal fun audioTrackKey(stream: AudioStream): String {
    stream.audioTrackId?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { return "id:${it.lowercase(Locale.ROOT)}" }
    val type = stream.audioTrackType?.name?.lowercase(Locale.ROOT).orEmpty()
    stream.audioTrackName?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { return "name:${it.lowercase(Locale.ROOT)}|$type" }
    stream.audioLocale?.toLanguageTag()?.takeIf { it.isNotBlank() }
        ?.let { return "language:${it.lowercase(Locale.ROOT)}|$type" }
    return "default"
}

internal fun audioTrackLabel(stream: AudioStream, original: Boolean): String {
    if (original) return "Original"
    stream.audioTrackName?.takeIf { it.isNotBlank() }?.let { return it }
    val locale = stream.audioLocale
    val language = locale?.getDisplayLanguage(Locale.getDefault())?.takeIf { it.isNotBlank() }
    return language?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: "Audio track"
}

internal fun audioTrackDetail(stream: AudioStream, original: Boolean): String = listOfNotNull(
    if (original) "Default audio" else stream.audioLocale?.getDisplayLanguage(Locale.getDefault())?.takeIf { it.isNotBlank() },
    if (stream.audioTrackType == AudioTrackType.DESCRIPTIVE) "Descriptive" else null,
    stream.format?.name?.replace('_', ' ')?.lowercase(Locale.US)?.replaceFirstChar { it.titlecase(Locale.US) },
    stream.averageBitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
).joinToString(" · ")

internal fun subtitleMimeType(sub: SubtitlesStream): String? = when (sub.extension.lowercase(Locale.US)) {
    "srt" -> MimeTypes.APPLICATION_SUBRIP
    "vtt", "webvtt" -> MimeTypes.TEXT_VTT
    "ttml", "xml" -> MimeTypes.APPLICATION_TTML
    else -> null
}

internal fun StreamInfo.hasDubbedAudio(): Boolean = audioStreams.any {
    it.audioTrackType != null && it.audioTrackType != AudioTrackType.ORIGINAL
}

