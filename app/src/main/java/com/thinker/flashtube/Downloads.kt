package com.thinker.flashtube

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat as AndroidMediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat as StreamFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

private const val TAG = "FlashTubeDl"

enum class DownloadStatus { Queued, Downloading, Paused, Complete, Failed }





@Stable
class DownloadTask(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnail: String?,
    val durationSec: Long,
    val height: Int,
    val fileName: String,
    downloaded: Long = 0L,
    total: Long = -1L,
    status: DownloadStatus = DownloadStatus.Queued,
) {
    val key: String get() = "$videoId@$height"

    var downloaded by mutableStateOf(downloaded)
        internal set
    var total by mutableStateOf(total)
        internal set
    var status by mutableStateOf(status)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    val progress: Float
        get() = when {
            status == DownloadStatus.Complete -> 1f
            total > 0L -> (downloaded.toFloat() / total).coerceIn(0f, 1f)
            else -> 0f
        }

    val file: File get() = Downloads.fileFor(fileName)
    val localUri: String get() = Uri.fromFile(file).toString()
    val played: Boolean get() = status == DownloadStatus.Complete && file.exists()
}


data class DownloadOption(
    val height: Int,
    val muxed: VideoStream?,
    val video: VideoStream?,
    val audio: AudioStream?,
    val estimatedBytes: Long,
) {
    val needsMux: Boolean get() = muxed == null
    val label: String get() = "${height}p"
}









object Downloads {
    private const val MAX_ACTIVE = 2
    private const val PERSIST_KEY = "tasks"
    private const val PREFS = "downloads"

    val tasks: SnapshotStateList<DownloadTask> = mutableStateListOf()
    var initialized by mutableStateOf(false)
        private set

    val busy: Boolean
        get() = tasks.any { it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued }


    val progress: Float
        get() {
            val active = tasks.filter { it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued }
            return if (active.isEmpty()) 0f else active.map { it.progress }.average().toFloat()
        }

    private lateinit var dir: File
    private lateinit var prefs: android.content.SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val pumpLock = Any()
    private val lastPersist = ConcurrentHashMap<String, Long>()
    private val lastReport = ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        if (initialized) return
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        dir = File(base, "FlashTube").apply { mkdirs() }
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        initialized = true
        restore()

        persist()
    }

    fun fileFor(name: String): File = File(dir, name)


    suspend fun options(id: String): List<DownloadOption> = withContext(Dispatchers.IO) {
        optionsFor(StreamCache.getOrLoad(id))
    }

    private fun optionsFor(info: StreamInfo): List<DownloadOption> {
        val audio = bestAudio(info)
        val heights = sortedSetOf<Int>()
        info.videoStreams.filter { it.format == StreamFormat.MPEG_4 && it.codec.orEmpty().startsWith("avc") && it.height > 0 }
            .forEach { heights.add(qualityTier(it.width, it.height)) }
        info.videoOnlyStreams.filter { it.format == StreamFormat.MPEG_4 && it.codec.orEmpty().startsWith("avc") && it.height > 0 }
            .forEach { heights.add(qualityTier(it.width, it.height)) }

        return heights.sortedDescending().mapNotNull { height ->
            val muxed = info.videoStreams
                .filter {
                    it.format == StreamFormat.MPEG_4 && it.codec.orEmpty().startsWith("avc") &&
                        qualityTier(it.width, it.height) == height
                }
                .maxByOrNull { it.bitrate }
            val video = info.videoOnlyStreams
                .filter {
                    it.format == StreamFormat.MPEG_4 && it.codec.orEmpty().startsWith("avc") &&
                        qualityTier(it.width, it.height) == height
                }
                .maxByOrNull { it.bitrate }
            if (muxed == null && (video == null || audio == null)) return@mapNotNull null
            val bits = if (muxed != null) muxed.bitrate.toLong()
            else (video?.bitrate ?: 0).toLong() + (audio?.averageBitrate ?: 0)
            val estimate = if (bits > 0 && info.duration > 0) bits * info.duration / 8 else -1L
            DownloadOption(height, muxed, video, audio, estimate)
        }
    }

    private fun bestAudio(info: StreamInfo): AudioStream? = info.audioStreams
        .filter { it.format == StreamFormat.M4A }
        .maxByOrNull { max(it.averageBitrate, it.bitrate) }

    val musicDownloads: List<DownloadTask>
        get() = tasks.filter { (it.height == 0 || it.fileName.endsWith(".m4a")) && it.status == DownloadStatus.Complete }

    fun enqueueMusic(video: VideoItem): Boolean = enqueueAudio(video)

    fun enqueueAudio(video: VideoItem): Boolean {
        if (!initialized) return false
        val key = "${video.id}@0"
        if (tasks.any { it.key == key }) return false
        val name = fileNameFor(video, 0)
        val task = DownloadTask(
            videoId = video.id,
            title = video.title,
            channel = video.channel,
            thumbnail = video.thumbnail,
            durationSec = video.durationSec,
            height = 0,
            fileName = name,
            status = DownloadStatus.Queued,
        )
        tasks.add(0, task)
        persist()
        pump()
        return true
    }

    fun enqueue(video: VideoItem, height: Int): Boolean {
        if (!initialized) return false
        val key = "${video.id}@$height"
        if (tasks.any { it.key == key }) return false
        val name = fileNameFor(video, height)
        val task = DownloadTask(
            videoId = video.id,
            title = video.title,
            channel = video.channel,
            thumbnail = video.thumbnail,
            durationSec = video.durationSec,
            height = height,
            fileName = name,
            status = DownloadStatus.Queued,
        )
        tasks.add(0, task)
        persist()
        pump()
        return true
    }

    fun pause(task: DownloadTask) {
        if (task.status != DownloadStatus.Downloading && task.status != DownloadStatus.Queued) return
        task.status = DownloadStatus.Paused
        jobs.remove(task.key)?.cancel()
        persist()
        pump()
    }

    fun resume(task: DownloadTask) {
        if (task.status != DownloadStatus.Paused && task.status != DownloadStatus.Failed) return
        deleteWork(task)
        task.downloaded = 0L
        task.total = -1L
        task.error = null
        task.status = DownloadStatus.Queued
        persist()
        pump()
    }

    fun remove(task: DownloadTask) {
        jobs.remove(task.key)?.cancel()
        tasks.remove(task)
        deleteWork(task)
        task.file.delete()
        persist()
        pump()
    }


    fun clearAll() {
        if (!initialized) return
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        tasks.toList().forEach { task ->
            deleteWork(task)
            runCatching { task.file.delete() }
        }
        tasks.clear()
        lastPersist.clear()
        lastReport.clear()
        prefs.edit().putString(PERSIST_KEY, "[]").apply()

        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun pump() {
        synchronized(pumpLock) {
            while (jobs.size < MAX_ACTIVE) {
                val next = tasks.firstOrNull { it.status == DownloadStatus.Queued } ?: return
                next.status = DownloadStatus.Downloading
                val job = scope.launch(start = CoroutineStart.LAZY) { execute(next) }
                jobs[next.key] = job
                job.start()
            }
        }
    }

    private suspend fun execute(task: DownloadTask) {
        var attempt = 0
        try {
            while (true) {
                try {
                    runDownload(task)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    if (attempt < 2) {
                        attempt++
                        StreamCache.invalidate(task.videoId)
                        deleteWork(task)
                        task.downloaded = 0L
                        kotlinx.coroutines.delay(1000L * attempt)
                        continue
                    }
                    throw t
                }
            }
        } catch (e: CancellationException) {
            if (task.status == DownloadStatus.Downloading) task.status = DownloadStatus.Paused
        } catch (t: Throwable) {
            Log.w(TAG, "download ${task.key} failed", t)
            task.error = t.message ?: "Download failed"
            task.status = DownloadStatus.Failed
            deleteWork(task)
        } finally {
            jobs.remove(task.key)
            persist()
            pump()
        }
    }

    private suspend fun runDownload(task: DownloadTask) {
        StreamCache.invalidate(task.videoId)
        val info = StreamCache.getOrLoad(task.videoId)

        if (task.height == 0) {
            val audio = bestAudio(info) ?: throw IOException("Audio stream not available")
            val work = workFile(task, "audio")
            work.delete()
            val remote = probe(audio.content)
            task.total = remote.total
            transfer(audio.content, work, remote) { done -> report(task, done) }
            if (work.length() <= 0L) throw IOException("Empty audio download")
            val target = task.file
            target.delete()
            if (!work.renameTo(target)) {
                work.copyTo(target, overwrite = true)
                work.delete()
            }
            task.total = target.length()
            task.downloaded = task.total
            task.status = DownloadStatus.Complete
            return
        }

        val option = optionsFor(info).firstOrNull { it.height == task.height }
            ?: throw IOException("That quality is no longer available")
        val muxed = option.muxed

        if (muxed != null) {
            task.total = option.estimatedBytes
            val work = workFile(task, "video")
            work.delete()
            val remote = probe(muxed.content)
            task.total = remote.total
            transfer(muxed.content, work, remote) { done -> report(task, done) }
            if (work.length() <= 0L) throw IOException("Empty download")
            val target = task.file
            target.delete()
            if (!work.renameTo(target)) {
                work.copyTo(target, overwrite = true)
                work.delete()
            }
            task.total = target.length()
            task.downloaded = task.total
        } else {
            val video = option.video ?: throw IOException("No video stream")
            val audio = option.audio ?: throw IOException("No audio stream")
            task.total = option.estimatedBytes
            val vWork = workFile(task, "video")
            val aWork = workFile(task, "audio")
            vWork.delete()
            aWork.delete()

            val vRemote = probe(video.content)
            val aRemote = probe(audio.content)
            task.total = vRemote.total + aRemote.total
            val vDone = AtomicLong(0)
            val aDone = AtomicLong(0)
            coroutineScope {
                launch {
                    transfer(video.content, vWork, vRemote) { n ->
                        report(task, vDone.addAndGet(n) + aDone.get())
                    }
                }
                launch {
                    transfer(audio.content, aWork, aRemote) { n ->
                        report(task, aDone.addAndGet(n) + vDone.get())
                    }
                }
            }
            val target = task.file
            target.delete()
            mux(vWork, aWork, target)
            vWork.delete()
            aWork.delete()
            task.total = target.length()
            task.downloaded = task.total
        }

        task.error = null
        task.status = DownloadStatus.Complete
        persist()
        Log.i(TAG, "download ${task.key} complete: ${task.file.length()} bytes")
    }

    private fun report(task: DownloadTask, done: Long) {
        val now = SystemClock.elapsedRealtime()
        val last = lastReport[task.key] ?: 0L
        if (now - last >= 120L) {
            lastReport[task.key] = now
            task.downloaded = done.coerceAtMost(if (task.total > 0) task.total else done)
            val lastSaved = lastPersist[task.key] ?: 0L
            if (now - lastSaved >= 2_000L) {
                lastPersist[task.key] = now
                persist()
            }
        }
    }



    private data class Remote(val total: Long, val ranges: Boolean)


    private fun probe(url: String): Remote {
        fun makeProbeRequest(u: String, addRangeQuery: Boolean): Request {
            val finalUrl = if (addRangeQuery) withRange(u, 0L, 0L) else u
            return Request.Builder()
                .url(finalUrl)
                .header("Range", "bytes=0-0")
                .header("User-Agent", Net.USER_AGENT)
                .header("Referer", "https://www.youtube.com/")
                .build()
        }

        var res = runCatching { Net.client.newCall(makeProbeRequest(url, false)).execute() }.getOrNull()
        if (res == null || !res.isSuccessful) {
            res?.close()
            res = runCatching { Net.client.newCall(makeProbeRequest(url, true)).execute() }.getOrNull()
        }
        val response = res ?: throw IOException("Could not connect to video server")
        response.use {
            if (!it.isSuccessful) throw HttpStatusException(it.code)
            val contentRange = it.header("Content-Range")
            val total = contentRange?.substringAfterLast('/')?.toLongOrNull()
                ?: it.body?.contentLength()?.takeIf { len -> len > 0L }
                ?: -1L
            if (total <= 0L) throw IOException("Could not determine the video size")
            return Remote(total, ranges = it.code == 206)
        }
    }

    private suspend fun transfer(
        url: String,
        file: File,
        remote: Remote,
        onProgress: (Long) -> Unit,
    ) {
        if (!remote.ranges) {
            downloadSequential(url, file, onProgress)
            return
        }
        val chunks = when {
            remote.total >= 32L * 1024 * 1024 -> 8
            remote.total >= 8L * 1024 * 1024 -> 6
            remote.total >= 2L * 1024 * 1024 -> 4
            else -> 2
        }
        RandomAccessFile(file, "rw").use { it.setLength(remote.total) }
        val span = remote.total / chunks
        coroutineScope {
            (0 until chunks).map { index ->
                val start = index * span
                val end = if (index == chunks - 1) remote.total - 1 else (index + 1) * span - 1
                async(Dispatchers.IO) { downloadRange(url, file, start, end, onProgress) }
            }.awaitAll()
        }
        if (file.length() != remote.total) RandomAccessFile(file, "rw").use { it.setLength(remote.total) }
    }

    private fun downloadRange(url: String, file: File, start: Long, end: Long, onProgress: (Long) -> Unit) {
        fun makeRangeReq(u: String, addRangeQuery: Boolean): Request {
            val finalUrl = if (addRangeQuery) withRange(u, start, end) else u
            return Request.Builder()
                .url(finalUrl)
                .header("Range", "bytes=$start-$end")
                .header("User-Agent", Net.USER_AGENT)
                .header("Referer", "https://www.youtube.com/")
                .build()
        }

        var res = runCatching { Net.client.newCall(makeRangeReq(url, false)).execute() }.getOrNull()
        if (res == null || !res.isSuccessful) {
            res?.close()
            res = runCatching { Net.client.newCall(makeRangeReq(url, true)).execute() }.getOrNull()
        }
        val response = res ?: throw IOException("Range request failed")
        response.use { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code)
            val body = r.body ?: throw IOException("Empty response")
            RandomAccessFile(file, "rw").use { out ->
                out.seek(start)
                var position = start
                val buffer = ByteArray(512 * 1024)
                val source = body.source()
                while (position <= end) {
                    val want = min(buffer.size.toLong(), end - position + 1).toInt()
                    val read = source.read(buffer, 0, want)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    position += read
                    onProgress(read.toLong())
                }
            }
        }
    }

    private fun downloadSequential(url: String, file: File, onProgress: (Long) -> Unit) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", Net.USER_AGENT)
            .header("Referer", "https://www.youtube.com/")
            .build()
        Net.client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body ?: throw IOException("Empty response")
            file.outputStream().buffered(256 * 1024).use { out ->
                val source = body.source()
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    onProgress(read.toLong())
                }
            }
        }
    }

    private fun withRange(url: String, start: Long, end: Long): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        return parsed.newBuilder()
            .removeAllQueryParameters("range")
            .addQueryParameter("range", "$start-$end")
            .build()
            .toString()
    }



    private fun mux(video: File, audio: File, out: File) {
        val videoEx = MediaExtractor()
        val audioEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            videoEx.setDataSource(video.absolutePath)
            audioEx.setDataSource(audio.absolutePath)
            val vTrack = findTrack(videoEx, "video/") ?: throw IOException("No video track")
            val aTrack = findTrack(audioEx, "audio/") ?: throw IOException("No audio track")
            videoEx.selectTrack(vTrack)
            audioEx.selectTrack(aTrack)

            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val m = muxer
            val vOut = m.addTrack(videoEx.getTrackFormat(vTrack))
            val aOut = m.addTrack(audioEx.getTrackFormat(aTrack))
            m.start()

            val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var vFirst = -1L
            var aFirst = -1L

            while (true) {
                val size = videoEx.readSampleData(buffer, 0)
                if (size < 0) break
                val time = videoEx.sampleTime
                if (vFirst < 0) vFirst = time
                info.set(0, size, (time - vFirst).coerceAtLeast(0L), videoEx.sampleFlags)
                m.writeSampleData(vOut, buffer, info)
                videoEx.advance()
            }
            while (true) {
                val size = audioEx.readSampleData(buffer, 0)
                if (size < 0) break
                val time = audioEx.sampleTime
                if (aFirst < 0) aFirst = time
                info.set(0, size, (time - aFirst).coerceAtLeast(0L), audioEx.sampleFlags)
                m.writeSampleData(aOut, buffer, info)
                audioEx.advance()
            }

            m.stop()
        } catch (t: Throwable) {
            out.delete()
            throw IOException("Could not combine video and audio", t)
        } finally {
            runCatching { muxer?.release() }
            runCatching { videoEx.release() }
            runCatching { audioEx.release() }
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(AndroidMediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(prefix)) return i
        }
        return null
    }



    private fun fileNameFor(video: VideoItem, height: Int): String {
        val safe = video.title
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(64)
            .ifBlank { video.id }
        if (height == 0) return "$safe [Audio] ${video.id}.m4a"
        return "$safe [${height}p] ${video.id}.mp4"
    }

    private fun workFile(task: DownloadTask, kind: String): File =
        File(dir, "${task.videoId}_${task.height}_$kind.part")

    private fun deleteWork(task: DownloadTask) {
        workFile(task, "video").delete()
        workFile(task, "audio").delete()
    }

    private fun persist() {
        if (!initialized) return
        runCatching {
            val array = JSONArray()
            tasks.forEach { task ->
                array.put(
                    JSONObject()
                        .put("id", task.videoId)
                        .put("title", task.title)
                        .put("channel", task.channel)
                        .put("thumb", task.thumbnail)
                        .put("duration", task.durationSec)
                        .put("height", task.height)
                        .put("file", task.fileName)
                        .put("downloaded", task.downloaded)
                        .put("total", task.total)
                        .put("status", task.status.name),
                )
            }
            prefs.edit().putString(PERSIST_KEY, array.toString()).apply()
        }
    }

    private fun restore() {
        val raw = prefs.getString(PERSIST_KEY, null)
        if (raw != null) runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                val height = json.optInt("height")
                val name = json.optString("file")

                if (height < 0 || name.isBlank()) continue
                val status = runCatching {
                    DownloadStatus.valueOf(json.optString("status"))
                }.getOrDefault(DownloadStatus.Paused)
                val task = DownloadTask(
                    videoId = json.optString("id"),
                    title = json.optString("title"),
                    channel = json.optString("channel"),
                    thumbnail = json.optString("thumb").takeIf { it.isNotBlank() && it != "null" },
                    durationSec = json.optLong("duration"),
                    height = height,
                    fileName = name,
                    downloaded = json.optLong("downloaded"),
                    total = json.optLong("total", -1L),
                    status = if (status == DownloadStatus.Complete) status else DownloadStatus.Paused,
                )
                if (status == DownloadStatus.Complete && !task.file.exists()) continue
                if (tasks.any { it.fileName == name }) continue
                tasks.add(task)
            }
        }.onFailure { Log.w(TAG, "could not restore downloads", it) }



        runCatching {
            val existing = tasks.mapTo(HashSet()) { it.fileName }
            dir.listFiles()?.forEach { f ->
                if (!f.isFile || f.length() <= 0L) return@forEach
                val name = f.name
                if (name.endsWith(".part") || name.startsWith(".")) return@forEach
                if (!existing.add(name)) return@forEach
                val audio = name.endsWith(".m4a")
                val ext = if (audio) ".m4a" else ".mp4"
                if (!name.endsWith(ext)) return@forEach
                val stem = name.removeSuffix(ext)
                val id = stem.takeLast(11)
                if (id.length != 11) return@forEach
                val label = stem.dropLast(12).trim()
                val cut = label.lastIndexOf(" [")
                val title = (if (cut > 0) label.substring(0, cut) else label).trim()
                val height = Regex("\\[(\\d+)p]").find(label)?.groupValues?.get(1)?.toIntOrNull()
                    ?: if (audio) 0 else -1
                if (height < 0) return@forEach
                tasks.add(
                    DownloadTask(
                        videoId = id,
                        title = title.ifBlank { "Downloaded video" },
                        channel = "",
                        thumbnail = feedThumbnailUrl(id),
                        durationSec = 0L,
                        height = height,
                        fileName = name,
                        downloaded = f.length(),
                        total = f.length(),
                        status = DownloadStatus.Complete,
                    ),
                )
            }
        }.onFailure { Log.w(TAG, "could not scan downloads", it) }
    }

    fun clearFinished() {
        tasks.filter { it.status == DownloadStatus.Complete }.forEach { task ->
            tasks.remove(task)
            task.file.delete()
        }
        persist()
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")
}


fun DownloadTask.toVideoItem(): VideoItem = VideoItem(
    id = videoId,
    title = title,
    channel = channel,
    channelUrl = null,
    thumbnail = thumbnail,
    avatar = null,
    durationSec = durationSec,
    views = 0L,
    uploaded = null,
    localUri = localUri,
)
