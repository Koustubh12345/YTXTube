package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * DeArrow: crowdsourced, less clickbaity video titles (https://dearrow.ajay.app).
 *
 * Lookups use the server's hash-prefix endpoint, so only the first four hex characters of the
 * SHA-256 of a video ID leave the device, never the ID itself (the same privacy approach the
 * SponsorBlock lookup in this app already uses). When no community title exists, or the server
 * can't be reached, the original title is shown untouched.
 */
object DeArrow {
    private const val PREFS = "appearance"
    private const val KEY = "dearrow_titles"

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean(KEY, true)
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY, value)?.apply()
    }

    /** Videos that have a community title. */
    private val titles = ConcurrentHashMap<String, String>()

    /** Videos already looked up, including those with no community title. */
    private val checked: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val locks = ConcurrentHashMap<String, Any>()

    fun peek(videoId: String): String? = titles[videoId]

    suspend fun titleFor(videoId: String): String? = withContext(Dispatchers.IO) {
        titles[videoId]?.let { return@withContext it }
        if (videoId in checked) return@withContext null
        val prefix = prefixOf(videoId)
        // One request per prefix at a time: a second card for the same video waits, then reads the cache.
        synchronized(locks.computeIfAbsent(prefix) { Any() }) {
            titles[videoId]?.let { return@synchronized it }
            if (videoId in checked) return@synchronized null
            runCatching { fetch(prefix, videoId) }
                .onFailure { Log.w("YTXTube", "DeArrow lookup failed for $prefix", it) }
            titles[videoId]
        }
    }

    private fun prefixOf(videoId: String): String =
        MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(4)

    private fun fetch(prefix: String, wanted: String) {
        val request = Request.Builder()
            .url("https://sponsor.ajay.app/api/branding/$prefix")
            .header("User-Agent", "YTXTube/${BuildConfig.VERSION_NAME}")
            .build()
        Net.client.newCall(request).execute().use { r ->
            if (r.code == 404) {
                checked += wanted
                return
            }
            if (!r.isSuccessful) throw java.io.IOException("DeArrow ${r.code}")
            val root = JSONObject(r.body?.string().orEmpty())
            val ids = root.keys()
            while (ids.hasNext()) {
                val id = ids.next()
                val branding = root.optJSONObject(id) ?: continue
                checked += id
                // The server sorts titles best first (locked, then by votes).
                val list = branding.optJSONArray("titles") ?: continue
                for (i in 0 until list.length()) {
                    val entry = list.optJSONObject(i) ?: continue
                    if (entry.optBoolean("original")) continue
                    val title = entry.optString("title").trim()
                    if (title.isNotEmpty()) {
                        titles[id] = title
                        break
                    }
                }
            }
            checked += wanted
        }
    }
}

/** The community title for [videoId] once it arrives, or [original] until then (and when there is none). */
@Composable
fun rememberDeArrowTitle(videoId: String, original: String): String {
    val enabled = DeArrow.enabled
    var title by remember(videoId, original, enabled) {
        mutableStateOf(if (enabled) DeArrow.peek(videoId) ?: original else original)
    }
    LaunchedEffect(videoId, enabled) {
        if (enabled && videoId.isNotBlank()) DeArrow.titleFor(videoId)?.let { title = it }
    }
    return title
}
