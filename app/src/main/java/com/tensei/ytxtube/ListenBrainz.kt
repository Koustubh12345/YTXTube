package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sends what you listen to in music mode to your own ListenBrainz account (https://listenbrainz.org),
 * an open alternative to Last.fm. Off until you paste your user token. A listen is sent once a
 * track has played for half its length or four minutes; if you are offline it is kept and sent
 * later. Your token is stored encrypted on the device.
 */
object ListenBrainz {
    private const val ENDPOINT = "https://api.listenbrainz.org/1/submit-listens"
    private const val MAX_PENDING = 500

    private var prefs: SharedPreferences? = null
    private var secure: SecureStore? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var pending = JSONArray()

    var enabled by mutableStateOf(false)
        private set
    var token by mutableStateOf("")
        private set
    var status by mutableStateOf("")
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("listenbrainz", Context.MODE_PRIVATE).also { prefs = it }
        secure = SecureStore(context, "listenbrainz_secure")
        enabled = p.getBoolean("enabled", false)
        token = runCatching { secure?.get("token") }.getOrNull().orEmpty()
        pending = runCatching { JSONArray(p.getString("pending", "[]")) }.getOrDefault(JSONArray())
        if (enabled && token.isNotBlank() && pending.length() > 0) flush()
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean("enabled", value)?.apply()
        if (value) flush()
    }

    fun updateToken(value: String) {
        token = value.trim()
        runCatching { secure?.put("token", token.ifBlank { null }) }
        status = if (token.isBlank()) "" else "Token saved"
        if (enabled) flush()
    }

    /** "Artist - Topic" is how YouTube Music names auto-generated artist channels. */
    private fun artistName(channel: String) = channel.removeSuffix(" - Topic").trim()

    fun listened(title: String, channel: String, durationSec: Long) {
        if (!enabled || token.isBlank() || title.isBlank()) return
        val entry = JSONObject()
            .put("listened_at", System.currentTimeMillis() / 1000L)
            .put(
                "track_metadata",
                JSONObject()
                    .put("artist_name", artistName(channel).ifBlank { "Unknown artist" })
                    .put("track_name", title)
                    .put(
                        "additional_info",
                        JSONObject()
                            .put("media_player", "YTXTube")
                            .put("submission_client", "YTXTube")
                            .put("duration_ms", durationSec * 1000L),
                    ),
            )
        synchronized(lock) {
            pending.put(entry)
            while (pending.length() > MAX_PENDING) pending.remove(0)
            save()
        }
        flush()
    }

    private fun save() {
        prefs?.edit()?.putString("pending", pending.toString())?.apply()
    }

    private fun flush() {
        val t = token
        if (t.isBlank()) return
        scope.launch {
            while (true) {
                val next = synchronized(lock) { if (pending.length() == 0) null else pending.getJSONObject(0) } ?: break
                val body = JSONObject().put("listen_type", "single").put("payload", JSONArray().put(next))
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .header("Authorization", "Token $t")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val code = runCatching { Net.client.newCall(request).execute().use { it.code } }.getOrNull()
                when {
                    code == 200 -> synchronized(lock) { pending.remove(0); save() }.also { status = "Last listen sent" }
                    code == 401 -> { status = "ListenBrainz rejected the token"; break }
                    code == 400 -> synchronized(lock) { pending.remove(0); save() } // unusable entry; drop it
                    else -> { status = if (code == null) "Offline: will send later" else "Server busy: will retry"; break }
                }
            }
        }
    }

    val queued: Int get() = synchronized(lock) { pending.length() }
}
