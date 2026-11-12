package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** Like and dislike totals for one video. */
data class VoteCounts(val likes: Long, val dislikes: Long) {
    /** Share of likes, 0f..1f, or null when nobody has voted. */
    val likeRatio: Float?
        get() = (likes + dislikes).takeIf { it > 0 }?.let { likes.toFloat() / it }
}

/**
 * Return YouTube Dislike (https://returnyoutubedislike.com): community-sourced dislike counts.
 *
 * Privacy: each lookup sends the video id to returnyoutubedislikeapi.com, so this is OFF by
 * default and must be switched on by the user. The API terms ask apps to credit the project, so
 * any screen that shows these numbers should link to returnyoutubedislike.com.
 *
 * The API allows about 100 requests a minute; on HTTP 429 lookups pause for [COOLDOWN_MS].
 */
object ReturnDislike {
    private const val PREFS = "playback"
    private const val KEY_ENABLED = "return_dislike"
    private const val BASE = "https://returnyoutubedislikeapi.com/votes?videoId="
    private const val COOLDOWN_MS = 60_000L
    private const val CACHE_SIZE = 64

    private var prefs: SharedPreferences? = null
    private var blockedUntil = 0L

    // Access-ordered LRU so scrolling back to a video never costs another request.
    private val cache = object : LinkedHashMap<String, VoteCounts>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VoteCounts>?) =
            size > CACHE_SIZE
    }

    var enabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY_ENABLED, false) == true
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }

    /**
     * Votes for [videoId], or null when the feature is off, the id is malformed, the video is
     * unknown, the service is rate-limiting or unreachable. Callers should just hide the number.
     */
    suspend fun votes(videoId: String): VoteCounts? {
        if (!enabled || !isVideoId(videoId)) return null
        synchronized(cache) { cache[videoId] }?.let { return it }
        if (System.currentTimeMillis() < blockedUntil) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(BASE + videoId)
                    .header("User-Agent", Net.USER_AGENT)
                    .build()
                Net.client.newCall(request).execute().use { response ->
                    when {
                        response.code == 429 -> {
                            blockedUntil = System.currentTimeMillis() + COOLDOWN_MS
                            null
                        }
                        !response.isSuccessful -> null
                        else -> {
                            val json = JSONObject(response.body?.string().orEmpty())
                            VoteCounts(json.optLong("likes"), json.optLong("dislikes"))
                        }
                    }
                }
            }.getOrNull()?.also { synchronized(cache) { cache[videoId] = it } }
        }
    }

    /** YouTube video ids are 11 characters of letters, digits, '-' and '_'. */
    internal fun isVideoId(id: String): Boolean =
        id.length == 11 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    /** Compact count for the UI, e.g. 999, 1.2K, 498K, 1.5M. Locale independent. */
    fun format(count: Long): String {
        val n = count.coerceAtLeast(0L)
        return when {
            n < 1_000L -> n.toString()
            n < 1_000_000L -> scaled(n / 100L, "K")
            n < 1_000_000_000L -> scaled(n / 100_000L, "M")
            else -> scaled(n / 100_000_000L, "B")
        }
    }

    // [tenths] is the value in tenths of the unit, floored so 9,999 shows 9.9K, never 10.0K.
    private fun scaled(tenths: Long, unit: String): String {
        val whole = tenths / 10
        val decimal = tenths % 10
        return if (whole >= 10 || decimal == 0L) "$whole$unit" else "$whole.$decimal$unit"
    }
}
