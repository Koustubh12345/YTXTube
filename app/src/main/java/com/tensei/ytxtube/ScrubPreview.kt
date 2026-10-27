package com.tensei.ytxtube

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.Frameset
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min





object ScrubStoryboards {
    private const val TAG = "FlashTube"
    private val framesets = ConcurrentHashMap<String, Frameset>()
    private val sprites = ConcurrentHashMap<String, Bitmap>()

    suspend fun preload(videoId: String) {
        withContext(Dispatchers.IO) { resolveFrameset(videoId) }
    }

    suspend fun frameBitmap(
        @Suppress("UNUSED_PARAMETER") context: Context,
        videoId: String,
        positionMs: Long,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val set = resolveFrameset(videoId) ?: return@withContext null
        val b = set.getFrameBoundsAt(positionMs)
        val url = set.urls.getOrNull(b[0]) ?: return@withContext null
        val sprite = sprites[url] ?: fetchBitmap(url)?.also { sprites[url] = it } ?: return@withContext null
        val left = b[1].coerceIn(0, sprite.width - 1)
        val top = b[2].coerceIn(0, sprite.height - 1)
        val right = b[3].coerceIn(left + 1, sprite.width)
        val bottom = b[4].coerceIn(top + 1, sprite.height)
        val w = min(right - left, set.frameWidth)
        val h = min(bottom - top, set.frameHeight)
        if (w <= 0 || h <= 0) return@withContext null
        Bitmap.createBitmap(sprite, left, top, w, h)
    }

    private fun resolveFrameset(videoId: String): Frameset? {
        framesets[videoId]?.let { return it }
        val info = runCatching {
            StreamCache.get(videoId) ?: StreamCache.getOrLoad(videoId)
        }.getOrNull()
        val sets = info?.previewFrames ?: emptyList()
        if (sets.isEmpty()) {
            Log.i(TAG, "scrub preview: no storyboard frames for $videoId")
            return null
        }
        val byArea = sets.sortedBy { it.frameWidth * it.frameHeight }

        val pick = byArea.getOrNull(1) ?: byArea.firstOrNull() ?: return null
        framesets[videoId] = pick
        Log.i(
            TAG,
            "scrub preview: ${pick.frameWidth}×${pick.frameHeight}, " +
                "${pick.totalCount} frames / ${pick.durationPerFrame}ms for $videoId",
        )
        return pick
    }

    private fun fetchBitmap(url: String): Bitmap? {
        val bytes = runCatching {
            Net.client.newCall(
                Request.Builder().url(url).header("User-Agent", Net.USER_AGENT).build(),
            ).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

@Composable
fun ScrubVideoPreview(
    videoId: String?,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videoId, positionMs) {
        bitmap = if (videoId == null) null else ScrubStoryboards.frameBitmap(context, videoId, positionMs)
    }
    val frame = bitmap
    if (frame != null) {
        Image(
            frame.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {

        Box(modifier.fillMaxSize())
    }
}
