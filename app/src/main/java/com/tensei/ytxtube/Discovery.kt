package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One watched video, reduced to what the ranker needs. */
data class WatchSignal(val channel: String, val tokens: List<String>, val at: Long)

/** A related video with the reason it was moved up, if it was. */
data class Ranked(val video: VideoItem, val why: String?)

/**
 * Pure ranking logic, kept free of Android so it can be unit tested. A candidate scores higher the
 * more of its title words you have watched before (recent watches count more), plus a bonus for
 * channels you watch, which fades once a channel is overplayed so one creator cannot take over.
 * Ties keep YouTube's own order.
 */
object DiscoveryRanker {
    private val stop = setOf(
        "this", "that", "with", "from", "your", "have", "what", "when", "will", "into", "about", "they", "their",
        "them", "then", "than", "just", "more", "most", "best", "video", "official", "full", "new", "part",
        "episode", "live", "trailer", "music", "lyrics", "how", "why", "the", "and", "for", "you", "are", "was",
    )

    fun tokens(title: String): List<String> =
        title.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 4 && it !in stop && !it.all(Char::isDigit) }
            .distinct()
            .take(12)

    fun rank(signals: List<WatchSignal>, candidates: List<VideoItem>, now: Long = System.currentTimeMillis()): List<Ranked> {
        if (signals.isEmpty()) return candidates.map { Ranked(it, null) }
        val tagWeight = HashMap<String, Float>()
        val channelCount = HashMap<String, Int>()
        for (s in signals) {
            val ageDays = max(0f, (now - s.at) / 86_400_000f)
            val decay = 1f / (1f + ageDays * 0.15f)
            s.tokens.forEach { tagWeight[it] = (tagWeight[it] ?: 0f) + decay }
            channelCount[s.channel.lowercase()] = (channelCount[s.channel.lowercase()] ?: 0) + 1
        }
        return candidates.map { video ->
            val matched = tokens(video.title).mapNotNull { t -> tagWeight[t]?.let { t to it } }.sortedByDescending { it.second }
            val affinity = matched.take(3).sumOf { it.second.toDouble() }.toFloat()
            val n = channelCount[video.channel.lowercase()] ?: 0
            val channelBonus = if (n == 0) 0f else 1.5f * min(n, 3) / (1f + max(0, n - 3))
            val score = affinity + channelBonus
            val why = when {
                score <= 0f -> null
                channelBonus >= affinity && n > 0 -> "Because you watch ${video.channel}"
                matched.isNotEmpty() -> "Matches your interest in ${matched.first().first}"
                else -> null
            }
            Triple(video, score, why)
        }.sortedByDescending { it.second }.map { Ranked(it.first, it.third) }
    }

    @Suppress("unused")
    private fun norm(x: Float) = sqrt(x)
}

/**
 * Optional on-device personalisation of the "More videos" list on the watch page. Off by default.
 * A video counts after 30 seconds of watching. Only the channel name and a few title words are
 * stored, locally, newest 200 only, and nothing is ever sent anywhere.
 */
object Discovery {
    private const val MAX = 200
    private var prefs: SharedPreferences? = null
    private var signals: List<WatchSignal> = emptyList()
    private var lastId: String? = null

    var enabled by mutableStateOf(false)
        private set
    var count by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("discovery", Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean("enabled", false)
        signals = runCatching {
            val arr = JSONArray(p.getString("signals", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WatchSignal(
                    o.getString("c"),
                    o.getJSONArray("t").let { t -> (0 until t.length()).map(t::getString) },
                    o.getLong("a"),
                )
            }
        }.getOrDefault(emptyList())
        count = signals.size
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean("enabled", value)?.apply()
    }

    fun record(video: VideoItem) {
        if (!enabled || Tweaks.noWatchHistory || video.id == lastId) return
        lastId = video.id
        val tokens = DiscoveryRanker.tokens(video.title)
        if (tokens.isEmpty() && video.channel.isBlank()) return
        signals = (signals + WatchSignal(video.channel, tokens, System.currentTimeMillis())).takeLast(MAX)
        count = signals.size
        val arr = JSONArray()
        signals.forEach { s ->
            arr.put(JSONObject().put("c", s.channel).put("t", JSONArray(s.tokens)).put("a", s.at))
        }
        prefs?.edit()?.putString("signals", arr.toString())?.apply()
    }

    fun clear() {
        signals = emptyList()
        count = 0
        lastId = null
        prefs?.edit()?.remove("signals")?.apply()
    }

    fun rank(candidates: List<VideoItem>): List<Ranked> =
        if (enabled) DiscoveryRanker.rank(signals, candidates) else candidates.map { Ranked(it, null) }
}
