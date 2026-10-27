package com.thinker.flashtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject





object RecentMusic {
    private const val PREFS = "recent_music"
    private const val KEY = "tracks"
    private const val MAX = 30

    private val prefs by lazy {
        FlashTubeApplication.appContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    }

    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            val out = ArrayList<VideoItem>(array.length())
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                val id = json.optString("id")
                if (id.isBlank()) continue
                out += VideoItem(
                    id = id,
                    title = json.optString("title"),
                    channel = json.optString("channel"),
                    channelUrl = null,
                    thumbnail = json.optString("thumb").takeIf { it.isNotBlank() && it != "null" },
                    avatar = null,
                    durationSec = json.optLong("duration"),
                    views = -1,
                    uploaded = "YouTube Music",
                )
            }
            items = out
        }
    }

    fun record(video: VideoItem) {
        if (video.id.isBlank() || video.id.length != 11) return
        load()
        val fresh = video.copy(localUri = null)
        items = (listOf(fresh) + items.filter { it.id != fresh.id }).take(MAX)
        runCatching {
            val array = JSONArray()
            items.forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("channel", item.channel)
                        .put("thumb", item.thumbnail ?: "")
                        .put("duration", item.durationSec),
                )
            }
            prefs.edit().putString(KEY, array.toString()).apply()
        }
    }
}
