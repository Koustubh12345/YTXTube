package com.tensei.ytxtube

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap










object PlayerFallback {
    private const val TAG = "PlayerFallback"
    private const val CACHE_MS = 3 * 60 * 1000L


    private class Cached(val at: Long, val answer: JSONObject)

    private val cache = ConcurrentHashMap<String, Cached>()

    fun handles(url: String) = "/youtubei/v1/reel/reel_item_watch" in url ||
        "/youtubei/v1/player" in url ||
        "/watch" in url

    /** The rescued player response, used for caption tracks when the extractor returns none. */
    internal fun playerResponse(videoId: String): JSONObject? {
        val cached = cache[videoId]
        if (cached != null && System.currentTimeMillis() - cached.at < CACHE_MS) return cached.answer
        return fetchFallback(videoId)
    }

    



    fun substitute(url: String, requestBody: ByteArray?, responseText: String?): String? {
        if (responseText == null || !isBlocked(responseText)) return responseText

        val videoId = videoIdFrom(requestBody) ?: videoIdFromUrl(url) ?: return responseText

        val cached = cache[videoId]
        val answer = if (cached != null && System.currentTimeMillis() - cached.at < CACHE_MS) {
            cached.answer
        } else {
            fetchFallback(videoId) ?: return responseText
        }

        return when {
            "/reel" in url -> JSONObject().put("playerResponse", answer).toString()
            "/watch" in url -> {
                if ("ytInitialPlayerResponse" in responseText) {
                    responseText.replace(
                        Regex("""(var\s+ytInitialPlayerResponse\s*=\s*|\bwindow\["ytInitialPlayerResponse"]\s*=\s*|\bytInitialPlayerResponse\s*=\s*)(\{.+?\});""", RegexOption.DOT_MATCHES_ALL),
                    ) { "var ytInitialPlayerResponse = $answer;" }
                } else {
                    "<!DOCTYPE html><html><head><script>var ytInitialPlayerResponse = $answer;</script></head><body></body></html>"
                }
            }
            else -> answer.toString()
        }
    }

    private fun fetchFallback(videoId: String): JSONObject? {


        if (Account.signedIn) {
            runCatching { InnerTube.playerResponse(videoId) }
                .onFailure { Log.w(TAG, "Signed-in WEB fallback failed for $videoId", it) }
                .getOrNull()
                ?.takeIf(::isPlayable)
                ?.let {
                    Log.i(TAG, "Rescued $videoId with signed-in WEB")
                    cache[videoId] = Cached(System.currentTimeMillis(), it)
                    return it
                }
        }




        val clients = listOf(
            ClientConfig(
                name = "ANDROID_VR",
                id = "28",
                version = "1.65.10",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                osName = "Android",
                osVersion = "12L",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                platform = "MOBILE"
            ),
            ClientConfig(
                name = "TVHTML5",
                id = "7",
                version = "7.20260213.00.00",
                userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
                osName = "Tizen",
                osVersion = "4.0.0.2",
                deviceMake = "Samsung",
                deviceModel = "SmartTV",
                platform = "TV"
            ),
            ClientConfig(
                name = "WEB_EMBEDDED_PLAYER",
                id = "56",
                version = "1.20260213.00.00",
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                osName = "Windows",
                osVersion = "10.0",
                deviceMake = "",
                deviceModel = "",
                platform = "DESKTOP"
            ),
            ClientConfig(
                name = "IOS",
                id = "5",
                version = "21.10.3",
                userAgent = "com.google.ios.youtube/21.10.3 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                osName = "iPhone",
                osVersion = "18.1.0.22B83",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                platform = "MOBILE"
            ),
        )

        for (cfg in clients) {
            val answer = runCatching { ask(videoId, cfg) }
                .onFailure { Log.w(TAG, "Fallback ${cfg.name} request failed for $videoId", it) }
                .getOrNull() ?: continue
            if (isPlayable(answer)) {
                Log.i(TAG, "Rescued $videoId with ${cfg.name}")
                cache[videoId] = Cached(System.currentTimeMillis(), answer)
                return answer
            }
            Log.w(TAG, "Fallback ${cfg.name} had no streams for $videoId " +
                "(status=${answer.optJSONObject("playabilityStatus")?.optString("status")})")
        }
        return null
    }

    private fun videoIdFrom(requestBody: ByteArray?): String? {
        if (requestBody == null) return null
        val root = runCatching { JSONObject(String(requestBody)) }.getOrNull() ?: return null
        val player = root.optJSONObject("playerRequest") ?: root
        return player.optString("videoId").takeIf { it.length >= 11 }
    }

    private fun videoIdFromUrl(url: String): String? {
        Regex("""[?&]v=([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.get(1)?.let { return it }
        Regex("""/(?:shorts|embed|live)/([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.get(1)?.let { return it }
        val raw = url.substringAfterLast('/', "").substringBefore("?").substringBefore("&").trim()
        return raw.takeIf { it.length == 11 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
    }

    private data class ClientConfig(
        val name: String,
        val id: String,
        val version: String,
        val userAgent: String,
        val osName: String,
        val osVersion: String,
        val deviceMake: String,
        val deviceModel: String,
        val platform: String
    )


    private fun isPlayable(player: JSONObject): Boolean {
        val status = player.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
        if (!status.equals("OK", ignoreCase = true)) return false
        val streaming = player.optJSONObject("streamingData") ?: return false
        if (streaming.optString("hlsManifestUrl").isNotBlank()) return true
        if ((streaming.optJSONArray("formats")?.length() ?: 0) > 0) return true
        return (streaming.optJSONArray("adaptiveFormats")?.length() ?: 0) > 0
    }

    private fun isBlocked(body: String): Boolean {
        val root = runCatching { JSONObject(body) }.getOrNull()
        if (root != null) {
            val player = root.optJSONObject("playerResponse") ?: root
            val status = player.optJSONObject("playabilityStatus")
            val code = status?.optString("status").orEmpty()
            val reason = status?.optString("reason").orEmpty().lowercase()
            if (code.equals("OK", ignoreCase = true)) return false
            if (code.equals("LOGIN_REQUIRED", true) ||
                code.equals("UNPLAYABLE", true) ||
                code.equals("ERROR", true) ||
                code.equals("AGE_VERIFICATION_REQUIRED", true)
            ) return true
            if ("a bot" in reason || "not a bot" in reason || "confirm you" in reason) return true

            return player.optJSONObject("streamingData") == null
        }
        val lower = body.lowercase()
        return ("login_required" in lower || "unplayable" in lower) &&
            ("a bot" in lower || "not a bot" in lower || "confirm you" in lower || "error" in lower)
    }

    private fun ask(videoId: String, cfg: ClientConfig): JSONObject {
        val client = JSONObject()
            .put("clientName", cfg.name)
            .put("clientVersion", cfg.version)
            .put("clientScreen", if (cfg.name == "WEB_EMBEDDED_PLAYER") "EMBED" else "WATCH")
            .put("platform", cfg.platform)
            .put("deviceMake", cfg.deviceMake)
            .put("deviceModel", cfg.deviceModel)
            .put("osName", cfg.osName)
            .put("osVersion", cfg.osVersion)
            .put("hl", "en")
            .put("gl", "US")
        Account.visitorData()?.takeIf { it.isNotBlank() }?.let { client.put("visitorData", it) }

        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .apply {
                if (cfg.name == "WEB_EMBEDDED_PLAYER") {
                    put("thirdPartyEmbedUrl", "https://www.youtube.com/embed/$videoId")
                }
            }

        val request = okhttp3.Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", cfg.userAgent)
            .header("X-Goog-Api-Format-Version", "2")
            .header("X-YouTube-Client-Name", cfg.id)
            .header("X-YouTube-Client-Version", cfg.version)
            .apply {
                if (cfg.name == "WEB_EMBEDDED_PLAYER") {
                    header("Origin", "https://www.youtube.com")
                    header("Referer", "https://www.youtube.com/embed/$videoId")
                }
            }
            .build()

        Net.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return JSONObject(response.body!!.string())
        }
    }
}
