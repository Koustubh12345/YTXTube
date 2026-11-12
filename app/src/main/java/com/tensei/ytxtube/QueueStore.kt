package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers the last queue (the playing video and what was lined up after it) so it can be put
 * back after the app is closed. It is kept only on the device and updated whenever the queue has
 * something in it; clearing the queue by hand forgets it.
 */
object QueueStore {
    private const val MAX = 100
    private var prefs: SharedPreferences? = null

    var saved by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("queue_store", Context.MODE_PRIVATE).also { prefs = it }
        saved = runCatching {
            val arr = JSONArray(p.getString("items", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VideoItem(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    channel = o.optString("channel"),
                    channelUrl = o.optString("channelUrl").ifBlank { null },
                    thumbnail = o.optString("thumbnail").ifBlank { null },
                    avatar = null,
                    durationSec = o.optLong("dur"),
                    views = -1,
                    uploaded = null,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun remember(current: VideoItem?, queue: List<VideoItem>) {
        val items = (listOfNotNull(current) + queue).distinctBy { it.id }.take(MAX)
        if (queue.isEmpty() || items.isEmpty() || Tweaks.noWatchHistory) return
        saved = items
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("channel", it.channel)
                    .put("channelUrl", it.channelUrl.orEmpty()).put("thumbnail", it.thumbnail.orEmpty()).put("dur", it.durationSec),
            )
        }
        prefs?.edit()?.putString("items", arr.toString())?.apply()
    }

    fun forget() {
        saved = emptyList()
        prefs?.edit()?.remove("items")?.apply()
    }
}
