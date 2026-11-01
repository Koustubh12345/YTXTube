package com.tensei.ytxtube

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * YouTube only hands HDR renditions to its own player clients. NewPipe's extractor
 * deliberately drops every itag that is not in its static table, and its Android/iOS
 * player responses do not include the 10-bit VP9.2 / AV1 HDR formats either.
 *
 * This asks the iOS InnerTube player directly for the same video and merges the HDR
 * video-only renditions into the extractor's [StreamInfo]. Everything downstream
 * (quality picker, ExoPlayer source, downloader) then sees them like normal streams.
 */
object HdrFormats {

    private const val TAG = "YTXTube"
    private const val MAX_AGE_MS = 45 * 60 * 1000L
    private const val EMPTY_AGE_MS = 10 * 60 * 1000L
    private const val WAIT_MS = 2_500L
    private const val CLIENT_VERSION = "20.10.4"
    private const val USER_AGENT =
        "com.google.ios.youtube/20.10.4 (iPhone16,3; U; CPU iOS 18_3_1 like Mac OS X;)"

    private class Entry(val streams: List<VideoStream>, val loadedAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val inflight = ConcurrentHashMap<String, FutureTask<List<VideoStream>>>()
    private val pool = Executors.newFixedThreadPool(2)

    // If the iOS client is blocked or unreachable, stop paying its timeout on every
    // video start. Three failures in a row disable HDR lookups for a few minutes.
    @Volatile
    private var failures = 0

    @Volatile
    private var disabledUntil = 0L

    private fun fresh(entry: Entry, now: Long): Boolean =
        now - entry.loadedAt < if (entry.streams.isEmpty()) EMPTY_AGE_MS else MAX_AGE_MS

    private fun available(): Boolean = System.currentTimeMillis() >= disabledUntil

    private fun noteFailure() {
        if (++failures >= 3) {
            disabledUntil = System.currentTimeMillis() + 5 * 60 * 1000L
            failures = 0
        }
    }

    private val streamConstructor: java.lang.reflect.Constructor<*>? = runCatching {
        VideoStream::class.java.declaredConstructors
            .firstOrNull { it.parameterCount == 9 }
            ?.apply { isAccessible = true }
    }.getOrNull()

    /** Start the request while NewPipe is still extracting, so it costs no extra wall time. */
    fun prefetch(id: String) {
        if (id.isBlank() || !available()) return
        val now = System.currentTimeMillis()
        val cached = cache[id]
        if (cached != null && fresh(cached, now)) return
        if (inflight.containsKey(id)) return
        val task = FutureTask {
            try {
                val streams = fetch(id)
                if (streams.isNotEmpty()) failures = 0
                cache[id] = Entry(streams, System.currentTimeMillis())
                streams
            } catch (t: Throwable) {
                noteFailure()
                throw t
            }
        }
        if (inflight.putIfAbsent(id, task) == null) {
            pool.execute {
                try {
                    task.run()
                } finally {
                    inflight.remove(id, task)
                }
            }
        }
    }

    /**
     * Merge HDR renditions into [info]. Only videos that already reach 1440p or devices
     * that can display HDR are worth the extra player request; Shorts never call this.
     */
    fun install(id: String, info: StreamInfo) {
        if (id.isBlank() || info.streamType != StreamType.VIDEO_STREAM) return
        val existing = info.videoOnlyStreams
        if (existing.any { isHdr(it) }) return
        val tallest = existing.maxOfOrNull { it.height } ?: 0
        if (tallest < 1440 && !HdrSupport.isSupported) return

        val now = System.currentTimeMillis()
        var streams = cache[id]?.takeIf { fresh(it, now) }?.streams
        if (streams == null) {
            prefetch(id)
            val task = inflight[id]
            if (task != null) {
                streams = runCatching { task.get(WAIT_MS, TimeUnit.MILLISECONDS) }.getOrElse {
                    Log.w(TAG, "HDR formats unavailable for $id", it)
                    return
                }
            } else {
                // The request may have finished between the cache read and prefetch().
                streams = cache[id]?.takeIf { fresh(it, System.currentTimeMillis()) }?.streams
            }
        }
        if (streams.isNullOrEmpty()) return

        val known = existing.mapTo(HashSet()) { it.itag }
        val merged = ArrayList(existing)
        streams.forEach { stream -> if (known.add(stream.itag)) merged.add(stream) }
        if (merged.size == existing.size) return
        info.setVideoOnlyStreams(merged)
        Log.w(TAG, "HDR merged for $id: ${streams.joinToString { "${it.itag}/${it.height}p" }}")
    }

    /** HDR rows are shown whenever the video has HDR formats, even on an SDR display. */
    fun hdrHeights(info: StreamInfo): List<Int> = info.videoOnlyStreams
        .filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                it.height > 0 && isHdr(it)
        }
        .map { qualityTier(it.width, it.height) }
        .filter { it >= 720 }
        .distinct()
        .sortedDescending()

    fun isHdr(stream: VideoStream): Boolean = HdrSupport.isHdrCodec(stream)

    private fun fetch(id: String): List<VideoStream> {
        val ctor = streamConstructor ?: run {
            Log.w(TAG, "VideoStream constructor unavailable; HDR skipped")
            return emptyList()
        }
        val client = JSONObject()
            .put("clientName", "IOS")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", "en")
            .put("gl", "US")
            .put("utcOffsetMinutes", 0)
            .put("deviceMake", "Apple")
            .put("deviceModel", "iPhone16,3")
            .put("osName", "iPhone")
            .put("osVersion", "18.3.1.22D72")
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", id)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .toString()

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", "5")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .build()

        val text = Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        if (text.isBlank()) throw IOException("Empty player response")
        val root = runCatching { JSONObject(text) }.getOrNull() ?: throw IOException("Malformed player response")
        if (root.optJSONObject("playabilityStatus")?.optString("status") != "OK") return emptyList()
        val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
            ?: return emptyList()

        val streams = ArrayList<VideoStream>()
        for (i in 0 until formats.length()) {
            val format = formats.optJSONObject(i) ?: continue
            if (!format.optString("mimeType").startsWith("video/")) continue
            if (!isHdrFormat(format)) continue
            val url = format.optString("url").takeIf { it.isNotBlank() } ?: continue
            runCatching { toStream(format, url, ctor) }
                .onSuccess { streams.add(it) }
                .onFailure { Log.w(TAG, "could not build HDR stream", it) }
        }
        return streams
    }

    private fun isHdrFormat(format: JSONObject): Boolean {
        val color = format.optJSONObject("colorInfo")
        val transfer = color?.optString("transferCharacteristics").orEmpty().uppercase(Locale.US)
        val primaries = color?.optString("primaries").orEmpty().uppercase(Locale.US)
        if (transfer.contains("2084") || transfer.contains("HLG") ||
            transfer.contains("ARIB_STD_B67") || primaries.contains("BT2020")
        ) {
            return true
        }
        val mime = format.optString("mimeType").lowercase(Locale.US)
        val codec = mime.substringAfter("codecs=\"", "").substringBefore('"')
        return codec.contains("vp09.02") || codec.contains("vp9.2") ||
            (codec.startsWith("av01") && av1BitDepth(codec) >= 10)
    }

    private fun toStream(
        format: JSONObject,
        url: String,
        ctor: java.lang.reflect.Constructor<*>,
    ): VideoStream {
        val mime = format.optString("mimeType")
        val mediaFormat = if (mime.contains("webm", ignoreCase = true)) {
            MediaFormat.WEBM
        } else {
            MediaFormat.MPEG_4
        }
        val height = format.optInt("height")
        val width = format.optInt("width")
        val fps = format.optInt("fps", 30)
        val bitrate = format.optInt("bitrate")
        val codec = mime.substringAfter("codecs=\"", "").substringBefore('"')
            .takeIf { it.isNotBlank() }
        val resolution = format.optString("qualityLabel").ifBlank { "${height}p$fps" }
        val itag = ItagItem(
            format.optInt("itag"),
            ItagItem.ItagType.VIDEO_ONLY,
            mediaFormat,
            resolution,
        ).apply {
            this.codec = codec
            this.width = width
            this.height = height
            this.fps = fps
            this.bitrate = bitrate
            this.quality = format.optString("quality")
            this.contentLength = format.optString("contentLength").toLongOrNull() ?: -1L
            this.approxDurationMs = format.optString("approxDurationMs").toLongOrNull() ?: -1L
            format.optJSONObject("initRange")?.let {
                initStart = it.optString("start").toIntOrNull() ?: 0
                initEnd = it.optString("end").toIntOrNull() ?: 0
            }
            format.optJSONObject("indexRange")?.let {
                indexStart = it.optString("start").toIntOrNull() ?: 0
                indexEnd = it.optString("end").toIntOrNull() ?: 0
            }
        }
        return ctor.newInstance(
            format.optInt("itag").toString(),
            url,
            true,
            mediaFormat,
            DeliveryMethod.PROGRESSIVE_HTTP,
            null,
            true,
            resolution,
            itag,
        ) as VideoStream
    }

    private fun av1BitDepth(codec: String): Int =
        codec.split('.').getOrNull(3)?.take(2)?.toIntOrNull() ?: 0
}
