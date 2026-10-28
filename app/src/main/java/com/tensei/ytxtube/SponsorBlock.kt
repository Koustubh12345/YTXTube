package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import okhttp3.Request
import org.json.JSONArray
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap








object SponsorBlock {
    enum class Category(val key: String, val label: String, val color: Color, val defaultAction: Action) {
        Sponsor("sponsor", "Sponsor", Color(0xFF00D400), Action.Auto),
        SelfPromo("selfpromo", "Self-promotion", Color(0xFFFFFF00), Action.Button),
        Interaction("interaction", "Like and subscribe reminder", Color(0xFFCC00FF), Action.Button),
        Intro("intro", "Intro", Color(0xFF00FFFF), Action.Mark),
        Outro("outro", "Outro and credits", Color(0xFF0202ED), Action.Mark),
        Preview("preview", "Preview and recap", Color(0xFF008FD6), Action.Mark),
        MusicOffTopic("music_offtopic", "Non-music part of a music video", Color(0xFFFF9900), Action.Off),
        Filler("filler", "Filler tangent", Color(0xFF7300FF), Action.Off);


        val short: String get() = when (this) {
            Sponsor -> "sponsor"; SelfPromo -> "self-promo"; Interaction -> "reminder"; Intro -> "intro"
            Outro -> "outro"; Preview -> "preview"; MusicOffTopic -> "non-music"; Filler -> "filler"
        }

        companion object { fun of(key: String) = entries.firstOrNull { it.key == key } }
    }


    enum class Action(val label: String) {
        Auto("Skip"), Button("Ask"), Mark("Show"), Off("Off")
    }

    @Immutable
    data class Segment(val uuid: String, val category: Category, val startMs: Long, val endMs: Long) {
        val action get() = actionFor(category)
        val lengthMs get() = endMs - startMs
    }


    @Immutable
    data class Skip(val videoId: String, val segment: Segment, val at: Long = System.currentTimeMillis())



    private var prefs: SharedPreferences? = null
    var enabled by mutableStateOf(true)
        private set
    private val actions = mutableStateMapOf<Category, Action>()

    fun init(context: Context) {
        val p = context.getSharedPreferences("sponsorblock", Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean("enabled", true)
        Category.entries.forEach { c ->
            actions[c] = p.getString("action_${c.key}", null)?.let { runCatching { Action.valueOf(it) }.getOrNull() } ?: c.defaultAction
        }
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean("enabled", value)?.apply()
    }

    fun actionFor(category: Category): Action = actions[category] ?: category.defaultAction

    fun setAction(category: Category, action: Action) {
        actions[category] = action
        prefs?.edit()?.putString("action_${category.key}", action.name)?.apply()
    }




    private val cache = ConcurrentHashMap<String, List<Segment>>()

    val forUi = mutableStateMapOf<String, List<Segment>>()
    var lastSkip by mutableStateOf<Skip?>(null)

    fun cached(videoId: String): List<Segment>? = cache[videoId]


    fun fetch(videoId: String): List<Segment> {
        cache[videoId]?.let { return it }
        val segments = runCatching { request(videoId) }
            .onFailure { Log.w("YTXTube", "SponsorBlock lookup failed for $videoId", it) }
            .getOrNull() ?: return emptyList()
        cache[videoId] = segments
        return segments
    }

    private fun request(videoId: String): List<Segment> {
        val prefix = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(4)
        val categories = JSONArray(Category.entries.map { it.key }).toString()
        val url = "https://sponsor.ajay.app/api/skipSegments/$prefix?categories=" +
            java.net.URLEncoder.encode(categories, "UTF-8") + "&actionType=skip"
        val request = Request.Builder().url(url).header("User-Agent", "YTXTube/${BuildConfig.VERSION_NAME}").build()
        Net.client.newCall(request).execute().use { r ->
            if (r.code == 404) return emptyList()
            if (!r.isSuccessful) throw java.io.IOException("SponsorBlock ${r.code}")
            val arr = JSONArray(r.body?.string().orEmpty())
            for (i in 0 until arr.length()) {
                val video = arr.optJSONObject(i) ?: continue
                if (video.optString("videoID") != videoId) continue
                val segs = video.optJSONArray("segments") ?: return emptyList()
                return (0 until segs.length()).mapNotNull { j ->
                    val s = segs.optJSONObject(j) ?: return@mapNotNull null
                    val cat = Category.of(s.optString("category")) ?: return@mapNotNull null
                    val range = s.optJSONArray("segment") ?: return@mapNotNull null
                    val start = (range.optDouble(0) * 1000).toLong()
                    val end = (range.optDouble(1) * 1000).toLong()
                    if (end - start < 1000) null else Segment(s.optString("UUID"), cat, start, end)
                }.sortedBy { it.startMs }
            }
            return emptyList()
        }
    }
}
