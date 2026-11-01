package com.tensei.ytxtube

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat as AndroidMediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
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

private const val TAG = "YTXTubeDl"

enum class DownloadStatus { Queued, Downloading, Processing, Paused, Complete, Failed }





@Stable
class DownloadTask(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnail: String?,
    val durationSec: Long,
    val height: Int,
    val fileName: String,
    val container: String = "mp4",
    val hdr: Boolean = false,
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
            status == DownloadStatus.Complete || status == DownloadStatus.Processing -> 1f
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
    val container: String = "mp4",
    val hdr: Boolean = false,
) {
    val needsMux: Boolean get() = muxed == null
    val label: String get() = "${height}p${if (hdr) " HDR" else ""}"
    val containerLabel: String get() = if (container == "webm") "WebM" else "MP4"
}









object Downloads {
    private const val MAX_ACTIVE = 2
    private const val PERSIST_KEY = "tasks"
    private const val PREFS = "downloads"

    val tasks: SnapshotStateList<DownloadTask> = mutableStateListOf()
    var initialized by mutableStateOf(false)
        private set

    val busy: Boolean
        get() = tasks.any {
            it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued ||
                it.status == DownloadStatus.Processing
        }


    val progress: Float
        get() {
            val active = tasks.filter {
                it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued ||
                    it.status == DownloadStatus.Processing
            }
            return if (active.isEmpty()) 0f else active.map { it.progress }.average().toFloat()
        }

    private lateinit var dir: File
    private lateinit var prefs: android.content.SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val pumpLock = Any()
    private val lastPersist = ConcurrentHashMap<String, Long>()
    private val lastReport = ConcurrentHashMap<String, Long>()

    private const val KEY_CUSTOM_DIR = "custom_download_dir"

    val currentDownloadDir: File get() = if (::dir.isInitialized) dir else defaultDownloadDir()

    fun defaultDownloadDir(): File {
        val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(pubDownloads, "YTXTube Downloads")
    }

    fun init(context: Context) {
        if (initialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val customPath = prefs.getString(KEY_CUSTOM_DIR, null)
        val targetDir = if (!customPath.isNullOrBlank()) {
            File(customPath)
        } else {
            defaultDownloadDir()
        }
        dir = targetDir.apply { runCatching { mkdirs() } }
        initialized = true
        restore()
        persist()
    }

    fun setCustomDownloadDir(newDir: File) {
        runCatching { newDir.mkdirs() }
        dir = newDir
        prefs.edit().putString(KEY_CUSTOM_DIR, newDir.absolutePath).apply()
    }

    fun resetToDefaultDownloadDir() {
        val def = defaultDownloadDir().apply { runCatching { mkdirs() } }
        dir = def
        prefs.edit().remove(KEY_CUSTOM_DIR).apply()
    }

    fun fileFor(name: String): File = File(dir, name)


    suspend fun options(id: String): List<DownloadOption> = withContext(Dispatchers.IO) {
        val info = StreamCache.getOrLoad(id)
        HdrFormats.install(id, info)
        optionsFor(info)
    }

    private fun optionsFor(info: StreamInfo): List<DownloadOption> {
        val audioMp4 = bestAudio(info, webm = false)
        val audioOpus = bestAudio(info, webm = true)
        val muxed = info.videoStreams.filter {
            it.isUrl && it.height > 0 && it.format == StreamFormat.MPEG_4 &&
                it.codec.orEmpty().startsWith("avc")
        }
        val videos = info.videoOnlyStreams.filter {
            it.isUrl && it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP &&
                it.height > 0
        }
        val heights = sortedSetOf<Int>()
        muxed.forEach { heights.add(qualityTier(it.width, it.height)) }
        videos.forEach { heights.add(qualityTier(it.width, it.height)) }

        return heights.sortedDescending().mapNotNull { height ->
            val atHeight = videos.filter { qualityTier(it.width, it.height) == height }
            val sdr = atHeight.filterNot(HdrSupport::isHdrCodec)
            val hdr = atHeight.filter(HdrSupport::isHdrCodec)

            val muxedAt = muxed.filter { qualityTier(it.width, it.height) == height }
                .maxByOrNull { it.bitrate }
            if (muxedAt != null) {
                return@mapNotNull option(height, muxedAt, null, null, info, "mp4", false)
            }

            // MP4/H.264 is the most compatible choice through 1080p.
            val avc = sdr.filter {
                it.format == StreamFormat.MPEG_4 && it.codec.orEmpty().startsWith("avc")
            }.maxByOrNull { it.bitrate }
            if (height <= 1080 && avc != null && audioMp4 != null) {
                return@mapNotNull option(height, null, avc, audioMp4, info, "mp4", false)
            }

            // 1440p/2160p: VP9 WebM + Opus, muxed by MediaMuxer on Android 10+.
            val vp9 = sdr.filter {
                it.format == StreamFormat.WEBM && it.codec.orEmpty().startsWith("vp9")
            }.maxByOrNull { it.bitrate }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vp9 != null && audioOpus != null) {
                return@mapNotNull option(height, null, vp9, audioOpus, info, "webm", false)
            }

            val av1 = sdr.filter {
                it.format == StreamFormat.MPEG_4 && it.codec.orEmpty().startsWith("av01")
            }.maxByOrNull { it.bitrate }
            if (Build.VERSION.SDK_INT >= 34 && av1 != null && audioMp4 != null) {
                return@mapNotNull option(height, null, av1, audioMp4, info, "mp4", false)
            }

            // A video that only ships HDR at this height is still downloadable.
            val hdrVp9 = hdr.filter {
                it.format == StreamFormat.WEBM && it.codec.orEmpty().startsWith("vp9")
            }.maxByOrNull { it.bitrate }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hdrVp9 != null && audioOpus != null) {
                return@mapNotNull option(height, null, hdrVp9, audioOpus, info, "webm", true)
            }
            val hdrAv1 = hdr.filter {
                it.format == StreamFormat.MPEG_4 && it.codec.orEmpty().startsWith("av01")
            }.maxByOrNull { it.bitrate }
            if (Build.VERSION.SDK_INT >= 34 && hdrAv1 != null && audioMp4 != null) {
                return@mapNotNull option(height, null, hdrAv1, audioMp4, info, "mp4", true)
            }
            null
        }
    }

    private fun option(
        height: Int,
        muxed: VideoStream?,
        video: VideoStream?,
        audio: AudioStream?,
        info: StreamInfo,
        container: String,
        hdr: Boolean,
    ): DownloadOption {
        val bits = if (muxed != null) muxed.bitrate.toLong()
        else (video?.bitrate ?: 0).toLong() + (audio?.averageBitrate ?: 0)
        val estimate = if (bits > 0 && info.duration > 0) bits * info.duration / 8 else -1L
        return DownloadOption(height, muxed, video, audio, estimate, container, hdr)
    }

    private fun bestAudio(info: StreamInfo, webm: Boolean): AudioStream? = info.audioStreams
        .filter {
            if (webm) {
                it.format == StreamFormat.WEBMA_OPUS || it.format == StreamFormat.OPUS ||
                    (it.format == StreamFormat.WEBMA && it.codec.orEmpty().startsWith("opus"))
            } else {
                it.format == StreamFormat.M4A
            }
        }
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

    fun enqueue(video: VideoItem, option: DownloadOption): Boolean {
        if (!initialized) return false
        val key = "${video.id}@${option.height}"
        if (tasks.any { it.key == key }) return false
        val name = fileNameFor(video, option.height, option.container, option.hdr)
        val task = DownloadTask(
            videoId = video.id,
            title = video.title,
            channel = video.channel,
            thumbnail = video.thumbnail,
            durationSec = video.durationSec,
            height = option.height,
            fileName = name,
            container = option.container,
            hdr = option.hdr,
            status = DownloadStatus.Queued,
        )
        tasks.add(0, task)
        persist()
        pump()
        return true
    }

    fun pause(task: DownloadTask) {
        if (task.status != DownloadStatus.Downloading && task.status != DownloadStatus.Queued &&
            task.status != DownloadStatus.Processing
        ) return
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

        runCatching { dir.listFiles()?.forEach { if (it.isFile && it.name.endsWith(".part")) it.delete() } }
    }

    /** Permanently deletes all downloaded video/music files and clears all tasks. */
    fun deleteAllDownloadsAndWork() {
        clearAll()
        runCatching {
            val d = currentDownloadDir
            if (d.exists() && d.isDirectory) {
                d.listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            }
        }
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
            if (task.status == DownloadStatus.Downloading || task.status == DownloadStatus.Processing) {
                task.status = DownloadStatus.Paused
            }
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
        task.status = DownloadStatus.Downloading
        StreamCache.invalidate(task.videoId)
        val info = StreamCache.getOrLoad(task.videoId)
        HdrFormats.install(task.videoId, info)

        if (task.height == 0) {
            val audio = bestAudio(info, webm = false) ?: throw IOException("Audio stream not available")
            val work = workFile(task, "audio")
            work.delete()
            val remote = probe(audio.content)
            task.total = remote.total
            val received = transfer(audio.content, work, remote) { done -> report(task, done) }
            if (received != remote.total || work.length() != remote.total) {
                throw IOException("Audio download incomplete (${work.length()} of ${remote.total} bytes)")
            }
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
            val received = transfer(muxed.content, work, remote) { done -> report(task, done) }
            if (received != remote.total || work.length() != remote.total) {
                throw IOException("Download incomplete (${work.length()} of ${remote.total} bytes)")
            }
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
                    val n = transfer(video.content, vWork, vRemote) { n ->
                        report(task, vDone.addAndGet(n) + aDone.get())
                    }
                    if (n != vRemote.total || vWork.length() != vRemote.total) {
                        throw IOException("Video download incomplete (${vWork.length()} of ${vRemote.total} bytes)")
                    }
                }
                launch {
                    val n = transfer(audio.content, aWork, aRemote) { n ->
                        report(task, aDone.addAndGet(n) + vDone.get())
                    }
                    if (n != aRemote.total || aWork.length() != aRemote.total) {
                        throw IOException("Audio download incomplete (${aWork.length()} of ${aRemote.total} bytes)")
                    }
                }
            }
            task.downloaded = task.total
            task.status = DownloadStatus.Processing
            persist()

            val target = task.file
            target.delete()
            mux(vWork, aWork, target, option.container)
            vWork.delete()
            aWork.delete()
            if (!target.exists() || target.length() <= 0L) throw IOException("Could not finish the download")
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
    ): Long {
        if (!remote.ranges) return downloadSequential(url, file, remote, onProgress)
        // Eight lanes saturate a fast link without making YouTube's edge throttle the
        // last few percent, which is what used to leave a pre-sized file full of holes.
        val chunks = when {
            remote.total >= 16L * 1024 * 1024 -> 8
            remote.total >= 4L * 1024 * 1024 -> 6
            remote.total >= 1024L * 1024 -> 4
            else -> 2
        }
        RandomAccessFile(file, "rw").use { it.setLength(remote.total) }
        val span = remote.total / chunks
        val received = coroutineScope {
            (0 until chunks).map { index ->
                val start = index * span
                val end = if (index == chunks - 1) remote.total - 1 else (index + 1) * span - 1
                async(Dispatchers.IO) { downloadRange(url, file, start, end, onProgress) }
            }.awaitAll().sum()
        }
        if (received != remote.total) {
            throw IOException("Only $received of ${remote.total} bytes arrived")
        }
        return received
    }

    private fun downloadRange(
        url: String,
        file: File,
        start: Long,
        end: Long,
        onProgress: (Long) -> Unit,
    ): Long {
        var last: Throwable? = null
        repeat(3) { attempt ->
            try {
                return downloadRangeOnce(url, file, start, end, onProgress)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                last = t
                Thread.sleep(350L * (attempt + 1))
            }
        }
        throw IOException("Range $start-$end failed", last)
    }

    private fun downloadRangeOnce(
        url: String,
        file: File,
        start: Long,
        end: Long,
        onProgress: (Long) -> Unit,
    ): Long {
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
            if (r.code != 206) throw IOException("Server did not honour the byte range (HTTP ${r.code})")
            val contentRange = r.header("Content-Range").orEmpty()
            val rangeStart = contentRange.substringAfter("bytes ", "")
                .substringBefore('-').toLongOrNull()
            if (rangeStart != null && rangeStart != start) {
                throw IOException("Server returned range $rangeStart instead of $start")
            }
            val body = r.body ?: throw IOException("Empty response")
            var received = 0L
            RandomAccessFile(file, "rw").use { out ->
                out.seek(start)
                var position = start
                val buffer = ByteArray(256 * 1024)
                val source = body.source()
                while (position <= end) {
                    val want = min(buffer.size.toLong(), end - position + 1).toInt()
                    val read = source.read(buffer, 0, want)
                    if (read <= 0) throw IOException("Connection ended at ${position - start} of ${end - start + 1} bytes")
                    out.write(buffer, 0, read)
                    position += read
                    received += read
                    onProgress(read.toLong())
                }
            }
            return received
        }
    }

    private fun downloadSequential(
        url: String,
        file: File,
        remote: Remote,
        onProgress: (Long) -> Unit,
    ): Long {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", Net.USER_AGENT)
            .header("Referer", "https://www.youtube.com/")
            .build()
        Net.client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body ?: throw IOException("Empty response")
            var received = 0L
            file.outputStream().buffered(256 * 1024).use { out ->
                val source = body.source()
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    received += read
                    onProgress(read.toLong())
                }
            }
            if (remote.total > 0 && received != remote.total) {
                throw IOException("Only $received of ${remote.total} bytes arrived")
            }
            return received
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



    private fun mux(video: File, audio: File, out: File, container: String) {
        if (container == "webm" && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IOException("WebM downloads need Android 10 or newer")
        }
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

            val outputFormat = if (container == "webm") {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }
            muxer = MediaMuxer(out.absolutePath, outputFormat)
            val m = muxer
            val vOut = m.addTrack(videoEx.getTrackFormat(vTrack))
            val aOut = m.addTrack(audioEx.getTrackFormat(aTrack))
            m.start()

            val vBuffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val aBuffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var vSize = videoEx.readSampleData(vBuffer, 0)
            var aSize = audioEx.readSampleData(aBuffer, 0)
            val base = when {
                vSize >= 0 && aSize >= 0 -> min(videoEx.sampleTime, audioEx.sampleTime)
                vSize >= 0 -> videoEx.sampleTime
                aSize >= 0 -> audioEx.sampleTime
                else -> 0L
            }

            while (vSize >= 0 || aSize >= 0) {
                val vTime = if (vSize >= 0) videoEx.sampleTime else Long.MAX_VALUE
                val aTime = if (aSize >= 0) audioEx.sampleTime else Long.MAX_VALUE
                if (vTime <= aTime) {
                    info.set(0, vSize, (vTime - base).coerceAtLeast(0L), videoEx.sampleFlags)
                    m.writeSampleData(vOut, vBuffer, info)
                    videoEx.advance()
                    vSize = videoEx.readSampleData(vBuffer, 0)
                } else {
                    info.set(0, aSize, (aTime - base).coerceAtLeast(0L), audioEx.sampleFlags)
                    m.writeSampleData(aOut, aBuffer, info)
                    audioEx.advance()
                    aSize = audioEx.readSampleData(aBuffer, 0)
                }
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



    private fun fileNameFor(video: VideoItem, height: Int, container: String = "mp4", hdr: Boolean = false): String {
        val safe = video.title
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(64)
            .ifBlank { video.id }
        if (height == 0) return "$safe [Audio] ${video.id}.m4a"
        val hdrTag = if (hdr) " HDR" else ""
        val extension = if (container == "webm") "webm" else "mp4"
        return "$safe [${height}p$hdrTag] ${video.id}.$extension"
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
                        .put("container", task.container)
                        .put("hdr", task.hdr)
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
                val container = json.optString("container")
                    .ifBlank { if (name.endsWith(".webm")) "webm" else "mp4" }
                val task = DownloadTask(
                    videoId = json.optString("id"),
                    title = json.optString("title"),
                    channel = json.optString("channel"),
                    thumbnail = json.optString("thumb").takeIf { it.isNotBlank() && it != "null" },
                    durationSec = json.optLong("duration"),
                    height = height,
                    fileName = name,
                    container = container,
                    hdr = json.optBoolean("hdr", name.contains(" HDR]")),
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
                val ext = when {
                    audio -> ".m4a"
                    name.endsWith(".webm") -> ".webm"
                    else -> ".mp4"
                }
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
                        container = if (ext == ".webm") "webm" else "mp4",
                        hdr = label.contains(" HDR]"),
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
