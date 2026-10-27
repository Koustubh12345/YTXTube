package com.tensei.ytxtube

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import kotlin.random.Random

@Immutable
data class Profile(val name: String, val handle: String?, val avatar: String?)

data class PlaylistRef(val id: String, val title: String, val thumbnail: String?, val subtitle: String?)

fun highResMusicThumbnail(url: String?): String? {
    if (url.isNullOrBlank()) return null
    var u = url
    if (u.contains("googleusercontent.com")) {
        u = u.replace(Regex("=w\\d+-h\\d+[^&?]*"), "=w800-h800-l90-rj")
             .replace(Regex("=s\\d+[^&?]*"), "=s800-c")
    } else if (u.contains("ytimg.com/vi/")) {
        u = u.replace("/default.jpg", "/maxresdefault.jpg")
             .replace("/mqdefault.jpg", "/maxresdefault.jpg")
             .replace("/hqdefault.jpg", "/maxresdefault.jpg")
             .replace("/sddefault.jpg", "/maxresdefault.jpg")
    }
    return u
}

@Immutable
data class MusicShelfItem(
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val videoId: String?,
    val browseId: String?,
    val playlistId: String? = null,
) {
    fun toVideoItem(): VideoItem? = videoId?.let { vid ->
        VideoItem(
            id = vid,
            title = title,
            channel = subtitle,
            channelUrl = null,
            thumbnail = highResMusicThumbnail(thumbnail),
            avatar = null,
            durationSec = 0L,
            views = -1L,
            uploaded = "YouTube Music",
        )
    }
}

@Immutable
data class MusicShelf(
    val title: String,
    val subtitle: String? = null,
    val items: List<MusicShelfItem>,
)

@Immutable
data class ChannelRef(val id: String, val name: String, val avatar: String?) {
    val url: String get() = "https://www.youtube.com/channel/$id"
}


@Immutable
data class FeedPage(val videos: List<VideoItem>, val next: String?, val channelHeader: ChannelHeaderMeta? = null)


@Immutable
data class LyricLine(val timeMs: Long, val text: String)


@Immutable
data class LyricsDoc(val lines: List<LyricLine>) {
    val synced: Boolean get() = lines.any { it.timeMs >= 0 }
    val plain: String get() = lines.joinToString("\n") { it.text }
}

@Immutable
data class ChannelHeaderMeta(val handle: String?, val subscribers: Long, val videoCount: Long, val verified: Boolean = false)

enum class LikeStatus { Like, Dislike, None }

@Immutable
data class VideoAccountState(val like: LikeStatus, val subscribed: Boolean?, val channelId: String?)

class NotSignedInException : IOException("Not signed in")









object Account {
    var profile by mutableStateOf<Profile?>(null)
        private set
    val signedIn get() = profile != null

    private var store: SecureStore? = null
    private val cookies = linkedMapOf<String, String>()
    private var clientVersion = FALLBACK_CLIENT_VERSION
    private var visitorData: String? = null

    fun init(context: Context) {
        val s = SecureStore(context, "account").also { store = it }
        s.get(KEY_COOKIES)?.let { parseCookieHeader(it, cookies) }
        s.get(KEY_CLIENT_VERSION)?.let { clientVersion = it }
        visitorData = s.get(KEY_VISITOR)
        val name = s.get(KEY_NAME)
        if (name != null && (cookies.containsKey("SAPISID") || cookies.containsKey("__Secure-3PAPISID") || cookies.containsKey("SID"))) {
            profile = Profile(name, s.get(KEY_HANDLE), s.get(KEY_AVATAR))
        }
    }


    fun looksSignedIn(cookieHeader: String?): Boolean {
        val names = cookieHeader.orEmpty().split(';').map { it.substringBefore('=').trim() }.toSet()
        return "SAPISID" in names || "__Secure-3PAPISID" in names || "SID" in names
    }


    suspend fun completeSignIn(cookieHeader: String) = withContext(Dispatchers.IO) {
        synchronized(cookies) {
            cookies.clear()
            parseCookieHeader(cookieHeader, cookies)
        }
        persistCookies()
        refreshClientConfig()
        var found: Profile? = null
        runCatching {
            val menu = InnerTube.post("account/account_menu", JSONObject())
            menu.walk { key, obj ->
                if (key == "activeAccountHeaderRenderer" && found == null) {
                    found = Profile(
                        name = obj.text("accountName") ?: "YouTube account",
                        handle = obj.text("channelHandle"),
                        avatar = obj.optJSONObject("accountPhoto")?.thumbnails()?.lastOrNull(),
                    )
                }
            }
        }
        val p = found ?: Profile("YouTube account", null, null)
        store?.apply {
            put(KEY_NAME, p.name); put(KEY_HANDLE, p.handle); put(KEY_AVATAR, p.avatar)
        }
        withContext(Dispatchers.Main) { profile = p }
    }

    fun signOut() {
        synchronized(cookies) { cookies.clear() }
        store?.clear()
        profile = null
        android.webkit.CookieManager.getInstance().apply { removeAllCookies(null); flush() }
    }


    private fun refreshClientConfig() {
        runCatching {
            val request = Request.Builder().url("https://www.youtube.com/?hl=en")
                .header("User-Agent", Net.USER_AGENT)
                .header("Cookie", cookieHeader())
                .build()
            Net.client.newCall(request).execute().use { r ->
                absorbSetCookies(r.headers("Set-Cookie"))
                val html = r.body?.string().orEmpty()
                Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let { clientVersion = it }
                Regex("\"VISITOR_DATA\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let { visitorData = it }
            }
            store?.put(KEY_CLIENT_VERSION, clientVersion)
            store?.put(KEY_VISITOR, visitorData)
        }
    }

    internal fun clientVersion() = clientVersion
    internal fun visitorData() = visitorData

    internal fun cookieHeader(): String = synchronized(cookies) { cookies.entries.joinToString("; ") { "${it.key}=${it.value}" } }


    internal fun authorization(origin: String = ORIGIN): String? {
        val ts = System.currentTimeMillis() / 1000
        fun hash(secret: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest("$ts $secret $origin".toByteArray())
            return "${ts}_" + digest.joinToString("") { "%02x".format(it) }
        }
        val (sapisid, p1, p3) = synchronized(cookies) {
            Triple(cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"], cookies["__Secure-1PAPISID"], cookies["__Secure-3PAPISID"])
        }
        sapisid ?: return null
        return buildString {
            append("SAPISIDHASH ").append(hash(sapisid))
            p1?.let { append(" SAPISID1PHASH ").append(hash(it)) }
            p3?.let { append(" SAPISID3PHASH ").append(hash(it)) }
        }
    }


    internal fun absorbSetCookies(setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        var changed = false
        synchronized(cookies) {
            setCookies.forEach { header ->
                val pair = header.substringBefore(';')
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isEmpty()) return@forEach
                if (cookies[name] != value) { cookies[name] = value; changed = true }
            }
        }
        if (changed) persistCookies()
    }

    private fun persistCookies() = store?.put(KEY_COOKIES, cookieHeader())

    private fun parseCookieHeader(header: String, into: MutableMap<String, String>) {
        header.split(';').forEach { part ->
            val name = part.substringBefore('=').trim()
            if (name.isNotEmpty()) into[name] = part.substringAfter('=', "").trim()
        }
    }

    const val ORIGIN = "https://www.youtube.com"
    private const val FALLBACK_CLIENT_VERSION = "2.20260901.00.00"
    private const val KEY_COOKIES = "cookies"
    private const val KEY_CLIENT_VERSION = "client_version"
    private const val KEY_VISITOR = "visitor"
    private const val KEY_NAME = "name"
    private const val KEY_HANDLE = "handle"
    private const val KEY_AVATAR = "avatar"
}






object InnerTube {
    private const val BASE = "https://www.youtube.com/youtubei/v1/"
    private val JSON_TYPE = "application/json".toMediaType()


    internal fun postAny(endpoint: String, body: JSONObject): JSONObject =
        if (Account.signedIn) post(endpoint, body) else post(endpoint, body, anonymous = true)

    




    internal fun playerResponse(videoId: String): JSONObject = postAny(
        "player",
        JSONObject()
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true),
    )

    internal fun post(endpoint: String, body: JSONObject, anonymous: Boolean = false): JSONObject {
        val auth = if (anonymous) null else Account.authorization() ?: throw NotSignedInException()
        body.put(
            "context",
            JSONObject().put(
                "client",
                JSONObject()
                    .put("clientName", "WEB")
                    .put("clientVersion", Account.clientVersion())
                    .put("hl", "en")
                    .put("gl", "US")
                    .apply { Account.visitorData()?.let { put("visitorData", it) } },
            ),
        )
        val request = Request.Builder()
            .url("$BASE$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("User-Agent", Net.USER_AGENT)
            .apply { if (auth != null) { header("Cookie", Account.cookieHeader()); header("Authorization", auth) } }
            .header("Origin", Account.ORIGIN)
            .header("X-Origin", Account.ORIGIN)
            .header("Referer", Account.ORIGIN + "/")
            .header("X-Goog-AuthUser", "0")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", Account.clientVersion())
            .apply { Account.visitorData()?.let { header("X-Goog-Visitor-Id", it) } }
            .build()
        Net.client.newCall(request).execute().use { r ->
            if (auth != null) Account.absorbSetCookies(r.headers("Set-Cookie"))
            val text = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) throw IOException("YouTube rejected the session (${r.code}). Try signing in again.")
            if (!r.isSuccessful) throw IOException("YouTube returned ${r.code}")
            return JSONObject(text)
        }
    }

    private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1/"
    private const val MUSIC_ORIGIN = "https://music.youtube.com"
    private const val WEB_REMIX_CLIENT_NAME = "67"
    private const val WEB_REMIX_CLIENT_VERSION = "1.20250101.01.00"

    internal fun postMusic(endpoint: String, body: JSONObject = JSONObject(), anonymous: Boolean = false): JSONObject {
        val auth = if (anonymous || !Account.signedIn) null else Account.authorization(MUSIC_ORIGIN)
        val clientContext = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", WEB_REMIX_CLIENT_VERSION)
            .put("hl", "en")
            .put("gl", "US")
        Account.visitorData()?.let { clientContext.put("visitorData", it) }

        body.put(
            "context",
            JSONObject()
                .put("client", clientContext)
                .put("user", JSONObject().put("lockedSafetyMode", false))
        )

        val reqBuilder = Request.Builder()
            .url("$MUSIC_BASE$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("User-Agent", Net.USER_AGENT)
            .header("Origin", MUSIC_ORIGIN)
            .header("X-Origin", MUSIC_ORIGIN)
            .header("Referer", "$MUSIC_ORIGIN/")
            .header("X-YouTube-Client-Name", WEB_REMIX_CLIENT_NAME)
            .header("X-YouTube-Client-Version", WEB_REMIX_CLIENT_VERSION)
            .header("X-Goog-AuthUser", "0")

        Account.visitorData()?.let { reqBuilder.header("X-Goog-Visitor-Id", it) }
        if (auth != null) {
            reqBuilder.header("Cookie", Account.cookieHeader())
            reqBuilder.header("Authorization", auth)
        }

        Net.client.newCall(reqBuilder.build()).execute().use { r ->
            if (auth != null) Account.absorbSetCookies(r.headers("Set-Cookie"))
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("YouTube Music returned ${r.code}")
            return JSONObject(text)
        }
    }


    suspend fun musicHome(): List<MusicShelf> = withContext(Dispatchers.IO) {
        val root = postMusic("browse", JSONObject().put("browseId", "FEmusic_home"))
        parseMusicShelves(root)
    }


    suspend fun musicHistory(): List<VideoItem> = withContext(Dispatchers.IO) {
        if (!Account.signedIn) return@withContext emptyList()
        val root = runCatching { postMusic("browse", JSONObject().put("browseId", "FEmusic_history")) }.getOrNull()
            ?: return@withContext emptyList()
        collectMusicSongs(root).distinctBy { it.id }
    }


    suspend fun musicLiked(): List<VideoItem> = withContext(Dispatchers.IO) {
        if (!Account.signedIn) return@withContext emptyList()
        val list = runCatching { playlist("LM").videos.take(50) }
            .getOrElse {
                runCatching { playlist("LL").videos.filter { isMusicTrack(it) }.take(50) }
                    .getOrDefault(emptyList())
            }
        list.map { it.copy(thumbnail = highResMusicThumbnail(it.thumbnail)) }
    }

    




    suspend fun musicLyrics(videoId: String, title: String, artist: String): LyricsDoc? = withContext(Dispatchers.IO) {
        val cleanTitle = title.replace(Regex("(?i)\\(official.*?\\)|\\[official.*?\\]|\\(audio\\)|\\[audio\\]|\\(video\\)|\\[video\\]|\\(lyric.*?\\)"), "")
            .replace(Regex("(?i)ft\\..*|feat\\..*"), "")
            .trim()
        val cleanArtist = artist.split(",", "·", "&", "feat.", "ft.", " - ").firstOrNull()?.trim() ?: artist.trim()


        var plainFallback: LyricsDoc? = null
        runCatching {
            val encT = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
            val encA = java.net.URLEncoder.encode(cleanArtist, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$encT&artist_name=$encA"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "YTXMusic/1.0 (https://github.com/thinker/flashtube)")
                .build()
            Net.client.newCall(req).execute().use { r ->
                if (r.isSuccessful) {
                    val json = JSONObject(r.body?.string().orEmpty())
                    json.optString("syncedLyrics").takeIf { it.isNotBlank() }?.let { lrc ->
                        val doc = parseLyrics(lrc)
                        if (doc.synced) return@withContext doc
                    }
                    json.optString("plainLyrics").takeIf { it.isNotBlank() }?.let { plain ->
                        plainFallback = parseLyrics(plain)
                    }
                }
            }
        }


        runCatching {
            val nextBody = JSONObject().put("videoId", videoId)
            val nextResp = postMusic("next", nextBody)
            val tabs = nextResp.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs")
            var lyricsBrowseId: String? = null
            if (tabs != null) {
                for (i in 0 until tabs.length()) {
                    val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                    val endpoint = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")
                    val bId = endpoint?.optString("browseId")
                    val tName = tab.optString("title")
                    if (bId != null && (bId.startsWith("MPLYt") || tName.equals("Lyrics", ignoreCase = true))) {
                        lyricsBrowseId = bId
                        break
                    }
                }
            }
            if (lyricsBrowseId != null) {
                val browseResp = postMusic("browse", JSONObject().put("browseId", lyricsBrowseId))
                val sectionList = browseResp.optJSONObject("contents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
                val shelf = sectionList?.optJSONObject(0)?.optJSONObject("musicDescriptionShelfRenderer")
                val runs = shelf?.optJSONObject("description")?.optJSONArray("runs")
                if (runs != null && runs.length() > 0) {
                    val sb = java.lang.StringBuilder()
                    for (k in 0 until runs.length()) {
                        sb.append(runs.optJSONObject(k)?.optString("text").orEmpty())
                    }
                    val ytLyrics = sb.toString().trim()
                    if (ytLyrics.isNotBlank() && !ytLyrics.startsWith("Provided to YouTube by")) {
                        return@withContext parseLyrics(ytLyrics)
                    }
                }
            }
        }

        plainFallback?.let { return@withContext it }


        runCatching {
            val encT = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
            val encA = java.net.URLEncoder.encode(cleanArtist, "UTF-8")
            val searchUrl = "https://lrclib.net/api/search?q=" + java.net.URLEncoder.encode("$encT $encA", "UTF-8")
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "YTXMusic/1.0 (https://github.com/thinker/flashtube)")
                .build()
            Net.client.newCall(searchReq).execute().use { r ->
                if (r.isSuccessful) {
                    val text = r.body?.string().orEmpty()
                    if (text.startsWith("[")) {
                        val arr = JSONArray(text)
                        var plain: LyricsDoc? = null
                        for (i in 0 until arr.length()) {
                            val entry = arr.optJSONObject(i) ?: continue
                            val doc = entry.optString("syncedLyrics").takeIf { it.isNotBlank() }?.let(::parseLyrics)
                            if (doc != null && doc.synced) return@withContext doc
                            if (plain == null) {
                                plain = entry.optString("plainLyrics").takeIf { it.isNotBlank() }?.let(::parseLyrics)
                            }
                        }
                        if (plain != null) return@withContext plain
                    }
                }
            }
        }

        return@withContext null
    }

    



    private fun parseLyrics(raw: String): LyricsDoc {
        val timeTag = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()
        raw.lineSequence().forEach { row ->
            val matches = timeTag.findAll(row).toList()
            val text = row.replace(timeTag, "").trim()
            if (matches.isNotEmpty()) {
                if (text.isBlank()) return@forEach
                matches.forEach { m ->
                    val min = m.groupValues[1].toLongOrNull() ?: return@forEach
                    val sec = m.groupValues[2].toLongOrNull() ?: return@forEach
                    val fracRaw = m.groupValues[3]
                    val frac = when (fracRaw.length) {
                        0 -> 0L
                        1 -> fracRaw.toLong() * 100
                        2 -> fracRaw.toLong() * 10
                        else -> fracRaw.take(3).toLong()
                    }
                    timed += LyricLine(min * 60_000 + sec * 1000 + frac, text)
                }
            } else {

                if (text.isBlank() || row.trim().matches(Regex("^\\[[a-zA-Z#]+:.*]$"))) return@forEach
                plain += text
            }
        }
        return if (timed.isNotEmpty()) {
            LyricsDoc(timed.sortedBy { it.timeMs })
        } else {
            LyricsDoc(plain.map { LyricLine(-1, it) })
        }
    }

    







    suspend fun shorts(next: String? = null): FeedPage = withContext(Dispatchers.IO) {
        if (Account.signedIn) {
            if (next != null) {

                val page = runCatching {
                    parseShorts(post("browse", JSONObject().put("continuation", next)))
                }.getOrNull()
                return@withContext page ?: FeedPage(emptyList(), null)
            }

            val sources = listOf("FEshorts", "FEwhat_to_watch", "FEsubscriptions", "FEhistory")
            val pages = sources
                .map { browseId ->
                    async(Dispatchers.IO) {
                        runCatching {
                            parseShorts(post("browse", JSONObject().put("browseId", browseId)))
                        }.getOrNull()
                    }
                }
                .awaitAll()

            val merged = LinkedHashMap<String, VideoItem>()
            var token: String? = null
            pages.filterNotNull().forEach { page ->
                page.videos.forEach { merged.putIfAbsent(it.id, it) }
                if (token == null) token = page.next
            }

            if (merged.isNotEmpty()) return@withContext FeedPage(merged.values.toList(), token)
        }

        val pool = runCatching { YouTube.search("#shorts") }.getOrDefault(emptyList())
        val trending = runCatching { YouTube.trending().videos.filter { it.durationSec in 1..180 } }.getOrDefault(emptyList())
        val mixed = (pool + trending).distinctBy { it.id }.shuffled()
        FeedPage(mixed, null)
    }

    internal fun parseShorts(root: JSONObject): FeedPage {
        val out = LinkedHashMap<String, VideoItem>()
        var next: String? = null
        root.walk { key, obj ->
            when (key) {
                "reelItemRenderer" -> {
                    val id = obj.optString("videoId").ifBlank { return@walk }
                    val title = obj.text("headline") ?: obj.text("title") ?: "Short"
                    out.getOrPut(id) {
                        VideoItem(
                            id = id,
                            title = title,
                            channel = obj.text("shortBylineText")?.substringBefore(" • ").orEmpty(),
                            channelUrl = obj.collectStrings("browseId").firstOrNull { it.startsWith("UC") }
                                ?.let { "https://www.youtube.com/channel/$it" },
                            thumbnail = obj.bestThumbnailUrl() ?: feedThumbnailUrl(id),
                            avatar = null,
                            durationSec = obj.text("lengthText")?.let(::parseDuration) ?: 0L,
                            views = parseViews(obj.text("viewCountText")),
                            uploaded = null,
                        )
                    }
                }
                "shortsLockupViewModel" -> {
                    val endpoint = obj.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
                    val entity = obj.optString("entityId")
                    val id = endpoint?.optJSONObject("reelWatchEndpoint")?.optString("videoId")
                        ?: endpoint?.optJSONObject("watchEndpoint")?.optString("videoId")
                        ?: obj.collectStrings("videoId").firstOrNull { it.length == 11 }
                        ?: entity.substringAfter("shorts-shelf-item-", entity).takeIf { it.length == 11 }
                        ?: return@walk
                    val meta = obj.optJSONObject("overlayMetadata")
                    val title = meta?.optJSONObject("primaryText")?.optString("content")?.ifBlank { null } ?: "Short"
                    val viewsText = meta?.optJSONObject("secondaryText")?.optString("content")
                    out.getOrPut(id) {
                        VideoItem(
                            id = id,
                            title = title,
                            channel = "",
                            channelUrl = null,
                            thumbnail = obj.bestThumbnailUrl() ?: feedThumbnailUrl(id),
                            avatar = null,
                            durationSec = 0L,
                            views = parseViews(viewsText),
                            uploaded = null,
                        )
                    }
                }

                "lockupViewModel" -> {
                    val command = obj.optJSONObject("rendererContext")?.optJSONObject("commandContext")
                        ?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
                        ?: obj.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
                    val reel = command?.optJSONObject("reelWatchEndpoint")
                    val url = command?.optJSONObject("commandMetadata")
                        ?.optJSONObject("webCommandMetadata")?.optString("url").orEmpty()
                    val sources = obj.optJSONObject("contentImage")
                        ?.optJSONObject("thumbnailViewModel")
                        ?.optJSONObject("image")?.optJSONArray("sources")
                    val sample = sources?.optJSONObject((sources.length() - 1).coerceAtLeast(0))
                    val thumbWidth = sample?.optInt("width") ?: 0
                    val thumbHeight = sample?.optInt("height") ?: 0
                    val contentType = obj.optString("contentType")
                    val portrait = thumbWidth > 0 && thumbHeight > thumbWidth * 6 / 5
                    val isShort = reel != null || contentType.contains("SHORT", ignoreCase = true) ||
                        url.contains("/shorts/") || portrait
                    if (!isShort) return@walk
                    val id = reel?.optString("videoId")?.ifBlank { null }
                        ?: command?.optJSONObject("watchEndpoint")?.optString("videoId")?.ifBlank { null }
                        ?: obj.optString("contentId").takeIf { it.length == 11 }
                        ?: return@walk
                    val meta = obj.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    val title = meta?.optJSONObject("title")?.optString("content")?.ifBlank { null } ?: "Short"
                    val parts = mutableListOf<String>()
                    meta?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
                        ?.optJSONArray("metadataRows")?.let { rows ->
                            for (r in 0 until rows.length()) {
                                val ps = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
                                for (p in 0 until ps.length()) {
                                    ps.optJSONObject(p)?.optJSONObject("text")
                                        ?.optString("content")?.ifBlank { null }?.let(parts::add)
                                }
                            }
                        }
                    val channel = parts.firstOrNull {
                        !it.contains("view") && !it.contains("ago") && !it.contains("watching")
                    }.orEmpty()
                    out.getOrPut(id) {
                        VideoItem(
                            id = id,
                            title = title,
                            channel = channel,
                            channelUrl = obj.collectStrings("browseId")
                                .firstOrNull { it.startsWith("UC") }
                                ?.let { "https://www.youtube.com/channel/$it" },
                            thumbnail = sources?.widestSourceUrl() ?: feedThumbnailUrl(id),
                            avatar = null,
                            durationSec = 0L,
                            views = parseViews(parts.firstOrNull { it.contains("view") }),
                            uploaded = null,
                        )
                    }
                }
                "videoRenderer" -> {
                    val id = obj.optString("videoId").ifBlank { return@walk }
                    val url = obj.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("commandMetadata")
                        ?.optJSONObject("webCommandMetadata")?.optString("url").orEmpty()
                    val thumbs = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val sample = thumbs?.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))
                    val tw = sample?.optInt("width") ?: 0
                    val th = sample?.optInt("height") ?: 0
                    val portrait = tw > 0 && th > tw * 6 / 5
                    val shortStyle = obj.collectStrings("style").any { it == "SHORTS" } ||
                        url.contains("/shorts/") || portrait
                    if (!shortStyle) return@walk
                    out.getOrPut(id) {
                        VideoItem(
                            id = id,
                            title = obj.text("title") ?: "Short",
                            channel = obj.text("ownerText")?.substringBefore(" • ").orEmpty(),
                            channelUrl = obj.collectStrings("browseId").firstOrNull { it.startsWith("UC") }
                                ?.let { "https://www.youtube.com/channel/$it" },
                            thumbnail = obj.bestThumbnailUrl() ?: feedThumbnailUrl(id),
                            avatar = null,
                            durationSec = obj.text("lengthText")?.let(::parseDuration) ?: 0L,
                            views = parseViews(obj.text("viewCountText")),
                            uploaded = null,
                        )
                    }
                }
                "reelPlayerOverlayRenderer", "reelWatchEndpoint" -> {
                    val id = obj.optString("videoId").ifBlank {
                        obj.optJSONObject("reelWatchEndpoint")?.optString("videoId").orEmpty()
                    }
                    if (id.isNotBlank()) out.getOrPut(id) {
                        VideoItem(
                            id = id,
                            title = obj.text("headline") ?: "Short",
                            channel = "",
                            channelUrl = null,
                            thumbnail = obj.bestThumbnailUrl() ?: feedThumbnailUrl(id),
                            avatar = null,
                            durationSec = 0L,
                            views = -1,
                            uploaded = null,
                        )
                    }
                }
            }
            if (key == "continuationItemRenderer" || key == "continuationItemViewModel") {
                obj.collectStrings("token").firstOrNull()?.let { next = it }
            }
        }
        return FeedPage(out.values.toList(), next)
    }

    internal fun collectMusicSongs(root: JSONObject): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        root.walk { key, obj ->
            if (key == "musicResponsiveListItemRenderer") {
                val vid = obj.optJSONObject("playlistItemData")?.optString("videoId")
                    ?: obj.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")?.optString("videoId")
                if (!vid.isNullOrEmpty()) {
                    val flexCols = obj.optJSONArray("flexColumns")
                    val col0 = flexCols?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    val col1 = flexCols?.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    val title = col0?.text("text") ?: "Track"
                    val artist = col1?.text("text") ?: "Unknown"
                    val thumb = highResMusicThumbnail(
                        obj.optJSONObject("thumbnail")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")?.thumbnails()?.lastOrNull()
                    )
                    out.add(
                        VideoItem(
                            id = vid,
                            title = title,
                            channel = artist,
                            channelUrl = null,
                            thumbnail = thumb,
                            avatar = null,
                            durationSec = 0L,
                            views = -1L,
                            uploaded = "YouTube Music",
                        )
                    )
                }
            } else if (key == "musicTwoRowItemRenderer") {
                val ep = obj.optJSONObject("navigationEndpoint")
                val vid = ep?.optJSONObject("watchEndpoint")?.optString("videoId")
                    ?.takeIf { it.isNotEmpty() }
                    ?: ep?.optJSONObject("browseEndpoint")?.optString("browseId")
                        ?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")
                if (!vid.isNullOrEmpty()) {
                    val title = obj.text("title") ?: "Track"
                    val subtitle = obj.text("subtitle") ?: ""
                    val thumb = highResMusicThumbnail(
                        obj.optJSONObject("thumbnailRenderer")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")?.thumbnails()?.lastOrNull()
                    )
                    out.add(
                        VideoItem(
                            id = vid,
                            title = title,
                            channel = subtitle,
                            channelUrl = null,
                            thumbnail = thumb,
                            avatar = null,
                            durationSec = 0L,
                            views = -1L,
                            uploaded = "YouTube Music",
                        )
                    )
                }
            } else if (key == "playlistPanelVideoRenderer") {
                val vid = obj.optString("videoId").takeIf { it.isNotEmpty() }
                if (!vid.isNullOrEmpty()) {
                    val title = obj.text("title") ?: "Track"
                    val artist = obj.text("longBylineText") ?: obj.text("shortBylineText") ?: ""
                    val thumb = highResMusicThumbnail(
                        obj.optJSONObject("thumbnail")?.thumbnails()?.lastOrNull()
                    )
                    out.add(
                        VideoItem(
                            id = vid,
                            title = title,
                            channel = artist,
                            channelUrl = null,
                            thumbnail = thumb,
                            avatar = null,
                            durationSec = 0L,
                            views = -1L,
                            uploaded = "YouTube Music",
                        )
                    )
                }
            }
        }
        return out
    }

    suspend fun resolveMusicItem(item: MusicShelfItem): VideoItem? = withContext(Dispatchers.IO) {
        if (!item.videoId.isNullOrEmpty()) {
            return@withContext item.toVideoItem()
        }
        val pid = item.playlistId
        if (!pid.isNullOrEmpty()) {
            runCatching {
                val nextResp = postMusic("next", JSONObject().put("playlistId", pid))
                val songs = collectMusicSongs(nextResp)
                if (songs.isNotEmpty()) return@withContext songs.first()
            }
            runCatching {
                val browseResp = postMusic("browse", JSONObject().put("browseId", if (pid.startsWith("VL")) pid else "VL$pid"))
                val songs = collectMusicSongs(browseResp)
                if (songs.isNotEmpty()) return@withContext songs.first()
            }
        }
        val bid = item.browseId
        if (!bid.isNullOrEmpty()) {
            runCatching {
                val browseResp = postMusic("browse", JSONObject().put("browseId", bid))
                val songs = collectMusicSongs(browseResp)
                if (songs.isNotEmpty()) return@withContext songs.first()
            }
        }

        runCatching {
            val q = "${item.title} ${item.subtitle}".trim()
            val results = YouTube.searchMusic(q)
            if (results.isNotEmpty()) return@withContext results.first()
        }
        null
    }

    



    suspend fun musicItemSongs(item: MusicShelfItem): List<VideoItem> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, VideoItem>()
        fun addAll(list: List<VideoItem>) {
            list.forEach { if (it.id.length == 11) out.putIfAbsent(it.id, it) }
        }

        val pid = item.playlistId
        val bid = item.browseId
        if (!pid.isNullOrEmpty()) {
            runCatching { addAll(collectMusicSongs(postMusic("next", JSONObject().put("playlistId", pid)))) }
            if (out.isEmpty()) {
                runCatching {
                    addAll(
                        collectMusicSongs(
                            postMusic("browse", JSONObject().put("browseId", if (pid.startsWith("VL")) pid else "VL$pid")),
                        ),
                    )
                }
            }
        }
        if (out.isEmpty() && !bid.isNullOrEmpty()) {
            runCatching { addAll(collectMusicSongs(postMusic("browse", JSONObject().put("browseId", bid)))) }

            if (out.isEmpty() && bid.startsWith("UC")) {
                runCatching {
                    val body = JSONObject().put("browseId", bid).put("params", "ggMGKgQIARAA")
                    addAll(collectMusicSongs(postMusic("browse", body)))
                }
            }
        }

        if (out.isEmpty() && !item.videoId.isNullOrEmpty()) {
            runCatching {
                val body = JSONObject()
                    .put("videoId", item.videoId)
                    .put("playlistId", "RDAMVM${item.videoId}")
                addAll(collectMusicSongs(postMusic("next", body)))
            }
        }
        if (out.isEmpty()) {
            runCatching {
                addAll(YouTube.searchMusic("${item.title} ${item.subtitle}".trim()).take(30))
            }
        }
        out.values.toList()
    }

    internal fun parseMusicShelves(root: JSONObject): List<MusicShelf> {
        val out = mutableListOf<MusicShelf>()
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?: return out
        val sectionList = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: return out

        for (i in 0 until sectionList.length()) {
            val section = sectionList.optJSONObject(i) ?: continue
            val carousel = section.optJSONObject("musicCarouselShelfRenderer")
            val plain = section.optJSONObject("musicShelfRenderer")
            val shelfObj = carousel ?: plain ?: continue

            val headerObj = shelfObj.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                ?: shelfObj.optJSONObject("title")
            val title = headerObj?.text("title") ?: shelfObj.text("title") ?: continue
            val strapline = headerObj?.text("strapline")

            val contentsArr = shelfObj.optJSONArray("contents") ?: continue
            val items = mutableListOf<MusicShelfItem>()
            for (j in 0 until contentsArr.length()) {
                val itemObj = contentsArr.optJSONObject(j) ?: continue
                val twoRow = itemObj.optJSONObject("musicTwoRowItemRenderer")
                val responsive = itemObj.optJSONObject("musicResponsiveListItemRenderer")

                if (twoRow != null) {
                    val itemTitle = twoRow.text("title") ?: continue
                    val itemSub = twoRow.text("subtitle") ?: ""
                    val ep = twoRow.optJSONObject("navigationEndpoint")
                    val playEp = twoRow.optJSONObject("thumbnailOverlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                    val watchEp = playEp?.optJSONObject("watchEndpoint") ?: ep?.optJSONObject("watchEndpoint")
                    val watchPlaylistEp = playEp?.optJSONObject("watchPlaylistEndpoint") ?: ep?.optJSONObject("watchPlaylistEndpoint")

                    val vid = watchEp?.optString("videoId")?.takeIf { it.isNotEmpty() }
                        ?: ep?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")
                    val playlistId = watchPlaylistEp?.optString("playlistId")?.takeIf { it.isNotEmpty() }
                        ?: watchEp?.optString("playlistId")?.takeIf { it.isNotEmpty() }
                        ?: ep?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.startsWith("VL") }?.removePrefix("VL")
                    val browseId = ep?.optJSONObject("browseEndpoint")?.optString("browseId")
                        ?.takeUnless { it.startsWith("MPED") }
                    val thumb = highResMusicThumbnail(
                        twoRow.optJSONObject("thumbnailRenderer")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")?.thumbnails()?.lastOrNull()
                    )
                    items.add(MusicShelfItem(itemTitle, itemSub, thumb, vid, browseId, playlistId))
                } else if (responsive != null) {
                    val playEp = responsive.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                    val vid = responsive.optJSONObject("playlistItemData")?.optString("videoId")
                        ?: playEp?.optJSONObject("watchEndpoint")?.optString("videoId")
                    val playlistId = responsive.optJSONObject("playlistItemData")?.optString("playlistId")
                        ?: playEp?.optJSONObject("watchEndpoint")?.optString("playlistId")
                        ?: playEp?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
                    val flexCols = responsive.optJSONArray("flexColumns")
                    val col0 = flexCols?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    val col1 = flexCols?.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    val itemTitle = col0?.text("text") ?: continue
                    val itemSub = col1?.text("text") ?: ""
                    val thumb = highResMusicThumbnail(
                        responsive.optJSONObject("thumbnail")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")?.thumbnails()?.lastOrNull()
                    )
                    items.add(MusicShelfItem(itemTitle, itemSub, thumb, vid, null, playlistId))
                }
            }
            if (items.isNotEmpty()) {
                out.add(MusicShelf(title, strapline, items))
            }
        }
        return out
    }



    suspend fun browse(browseId: String, continuation: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val body = if (continuation != null) JSONObject().put("continuation", continuation) else JSONObject().put("browseId", browseId)
        parseFeed(post("browse", body))
    }

    suspend fun home(next: String? = null) = browse("FEwhat_to_watch", next)

    





    suspend fun homeFeed(next: String? = null): FeedPage = withContext(Dispatchers.IO) {
        if (Account.signedIn) {
            val body = if (next != null) JSONObject().put("continuation", next)
                       else JSONObject().put("browseId", "FEwhat_to_watch")
            parseFeed(post("browse", body))
        } else {
            val trending = runCatching { YouTube.trending(next) }.getOrNull()
            if (trending != null && trending.videos.isNotEmpty()) {
                trending
            } else {
                val topics = listOf("Trending", "Popular videos", "New music", "Gaming", "Viral")
                val searchList = runCatching { YouTube.search(topics.random()) }.getOrDefault(emptyList())
                FeedPage(searchList, null)
            }
        }
    }

    suspend fun channelVideos(channelId: String, continuation: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val body = if (continuation != null) JSONObject().put("continuation", continuation)
        else JSONObject().put("browseId", channelId).put("params", CHANNEL_VIDEOS_TAB)
        val root = postAny("browse", body)
        val page = parseFeed(root)
        val header = if (continuation == null) parseChannelHeader(root) else null
        FeedPage(page.videos, page.next, header)
    }

    suspend fun channelLive(channelId: String, continuation: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val body = if (continuation != null) JSONObject().put("continuation", continuation)
        else JSONObject().put("browseId", channelId).put("params", CHANNEL_LIVE_TAB)
        val root = postAny("browse", body)
        val page = parseFeed(root)
        FeedPage(page.videos, page.next)
    }

    suspend fun channelCommunity(channelId: String, continuation: String? = null): CommunityPage = withContext(Dispatchers.IO) {
        val body = if (continuation != null) JSONObject().put("continuation", continuation)
        else JSONObject().put("browseId", channelId).put("params", CHANNEL_COMMUNITY_TAB)
        val root = postAny("browse", body)
        parseCommunity(root)
    }


    private const val CHANNEL_VIDEOS_TAB = "EgZ2aWRlb3PyBgQKAjoA"
    private const val CHANNEL_LIVE_TAB = "EgZzdHJlYW1z8gYECgJ6AA%3D%3D"
    private const val CHANNEL_COMMUNITY_TAB = "Egljb21tdW5pdHnyBgQKAkoA"
    suspend fun subscriptionsFeed(next: String? = null) = browse("FEsubscriptions", next)
    suspend fun history(next: String? = null) = browse("FEhistory", next)
    suspend fun playlist(id: String, next: String? = null) = browse("VL$id", next)


    suspend fun channelAvatarUrl(channelId: String): String? = withContext(Dispatchers.IO) {
        val root = postAny("browse", JSONObject().put("browseId", channelId))
        parseChannelAvatar(root.optJSONObject("header"))
    }

    internal fun parseChannelAvatar(header: JSONObject?): String? {
        if (header == null) return null
        var url: String? = null
        header.walk { key, obj ->
            if (url != null) return@walk
            if (key == "avatarViewModel" || key == "decoratedAvatarViewModel") {
                obj.optJSONObject("image")?.optJSONArray("sources")?.widestSourceUrl()?.let { url = it }
            }
        }
        return url?.let { normalizeChannelAvatarUrl(absoluteUrl(it)) }
    }

    suspend fun channels(): List<ChannelRef> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, ChannelRef>()
        post("browse", JSONObject().put("browseId", "FEchannels")).walk { key, obj ->
            if (key == "channelRenderer" || key == "gridChannelRenderer") {
                val id = obj.optString("channelId").ifBlank { return@walk }
                out.getOrPut(id) {
                    ChannelRef(id, obj.text("title") ?: "", obj.thumbnails()?.lastOrNull()?.let(::absoluteUrl))
                }
            }
        }
        out.values.toList()
    }

    suspend fun playlists(): List<PlaylistRef> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, PlaylistRef>()
        post("browse", JSONObject().put("browseId", "FEplaylist_aggregation")).walk { key, obj ->
            when (key) {
                "gridPlaylistRenderer", "playlistRenderer" -> {
                    val id = obj.optString("playlistId").ifBlank { return@walk }
                    out.getOrPut(id) {
                        PlaylistRef(id, obj.text("title") ?: "", obj.thumbnails()?.lastOrNull()?.let(::absoluteUrl), obj.text("videoCountText") ?: obj.text("videoCountShortText"))
                    }
                }
                "lockupViewModel" -> if (obj.optString("contentType") == "LOCKUP_CONTENT_TYPE_PLAYLIST") {
                    val id = obj.optString("contentId").ifBlank { return@walk }
                    val meta = obj.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    out.getOrPut(id) {
                        PlaylistRef(
                            id = id,
                            title = meta?.optJSONObject("title")?.optString("content").orEmpty(),
                            thumbnail = obj.firstImageSource(),
                            subtitle = obj.collectStrings("text").firstOrNull { it.contains("video", ignoreCase = true) },
                        )
                    }
                }
            }
        }
        out.values.filterNot { it.id == "WL" || it.id == "LL" }
    }


    suspend fun videoState(videoId: String): VideoAccountState = withContext(Dispatchers.IO) {
        val next = post("next", JSONObject().put("videoId", videoId))
        var like = LikeStatus.None
        var subscribed: Boolean? = null
        var channelId: String? = null
        next.walk { key, obj ->
            when (key) {
                "likeStatusEntity" -> like = when (obj.optString("likeStatus")) {
                    "LIKE" -> LikeStatus.Like
                    "DISLIKE" -> LikeStatus.Dislike
                    else -> like
                }
                "subscribeButtonRenderer" -> {
                    if (obj.has("subscribed")) subscribed = obj.optBoolean("subscribed")
                    obj.optString("channelId").ifBlank { null }?.let { channelId = it }
                }
                "subscriptionStateEntity" -> if (obj.has("subscribed")) subscribed = obj.optBoolean("subscribed")
                "videoOwnerRenderer" -> obj.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")?.takeIf { it.startsWith("UC") }?.let { if (channelId == null) channelId = it }
            }
        }
        VideoAccountState(like, subscribed, channelId)
    }


    suspend fun channelSubscribed(channelId: String): Boolean? = withContext(Dispatchers.IO) {
        var subscribed: Boolean? = null
        post("browse", JSONObject().put("browseId", channelId)).walk { key, obj ->
            if ((key == "subscribeButtonRenderer" || key == "subscriptionStateEntity") && obj.has("subscribed") && subscribed == null) {
                subscribed = obj.optBoolean("subscribed")
            }
        }
        subscribed
    }



    suspend fun setLike(videoId: String, status: LikeStatus) = withContext(Dispatchers.IO) {
        val endpoint = when (status) {
            LikeStatus.Like -> "like/like"
            LikeStatus.Dislike -> "like/dislike"
            LikeStatus.None -> "like/removelike"
        }
        post(endpoint, JSONObject().put("target", JSONObject().put("videoId", videoId)))
        Unit
    }

    suspend fun setSubscribed(channelId: String, subscribed: Boolean) = withContext(Dispatchers.IO) {
        post(
            if (subscribed) "subscription/subscribe" else "subscription/unsubscribe",
            JSONObject().put("channelIds", JSONArray().put(channelId)),
        )
        Unit
    }

    suspend fun setInWatchLater(videoId: String, add: Boolean) = withContext(Dispatchers.IO) {
        val action = if (add) JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
        else JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId)
        post("browse/edit_playlist", JSONObject().put("playlistId", "WL").put("actions", JSONArray().put(action)))
        Unit
    }


    class WatchSession(val videoId: String, val watchtimeUrl: String, val cpn: String)

    




    fun startWatch(videoId: String): WatchSession? {
        val tracking = post("player", JSONObject().put("videoId", videoId)).optJSONObject("playbackTracking") ?: return null
        val cpn = (1..16).map { CPN_ALPHABET[Random.nextInt(CPN_ALPHABET.length)] }.joinToString("")
        tracking.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")?.ifBlank { null }?.let { base ->
            ping(base, "ver=2&cpn=$cpn")
        }
        val watchtime = tracking.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")?.ifBlank { null } ?: return null
        return WatchSession(videoId, watchtime, cpn)
    }

    





    fun pingWatchtime(session: WatchSession, positionSec: Double, starts: List<Double>, ends: List<Double>, playing: Boolean, elapsedSec: Double, final: Boolean) {
        fun f(x: Double) = String.format(java.util.Locale.US, "%.3f", x)
        val rt = f(elapsedSec)
        val query = buildString {
            append("ver=2&cpn=").append(session.cpn)
            append("&cmt=").append(f(positionSec))
            append("&st=").append(starts.joinToString(",") { f(it) })
            append("&et=").append(ends.joinToString(",") { f(it) })
            append("&state=").append(if (playing) "playing" else "paused")
            append("&rt=").append(rt).append("&rtn=").append(rt).append("&rti=").append(elapsedSec.toInt())
            append("&lact=").append(Random.nextInt(800, 4000))
            append("&volume=100&muted=0&fs=0&hl=en_US&cr=US&c=WEB&cver=").append(Account.clientVersion())
            if (final) append("&final=1")
        }
        ping(session.watchtimeUrl, query)
    }

    private fun ping(base: String, query: String) {
        val request = Request.Builder().url(base + (if ('?' in base) "&" else "?") + query)
            .header("User-Agent", Net.USER_AGENT)
            .header("Cookie", Account.cookieHeader())
            .header("Referer", Account.ORIGIN + "/")
            .build()
        Net.client.newCall(request).execute().use { r ->
            Account.absorbSetCookies(r.headers("Set-Cookie"))
            if (!r.isSuccessful) throw IOException("stats ping ${r.code}")
        }
    }

    private const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"



    



    internal fun parseFeed(root: JSONObject): FeedPage {
        val videos = LinkedHashMap<String, VideoItem>()
        var next: String? = null
        root.walk { key, obj ->
            when (key) {


                "videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "playlistVideoRenderer" ->
                    if (!isMembersOnly(obj) && !isMovie(obj)) parseVideoRenderer(obj)?.let { videos.putIfAbsent(it.id, it) }
                "lockupViewModel" -> if (obj.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" && !isMembersOnly(obj) && !isMovie(obj)) {
                    parseLockup(obj)?.let { videos.putIfAbsent(it.id, it) }
                }


                "continuationItemRenderer", "continuationItemViewModel" ->
                    obj.collectStrings("token").firstOrNull()?.let { next = it }
            }
        }
        return FeedPage(videos.values.toList(), next)
    }


    internal fun isMembersOnly(o: JSONObject): Boolean =
        o.collectStrings("badgeStyle").any { it.contains("MEMBERS_ONLY") } ||
            o.collectStrings("style").any { it == "BADGE_STYLE_TYPE_MEMBERS_ONLY" }

    






    internal fun isMovie(o: JSONObject): Boolean {
        if (o.collectStrings("style").any { it == "BADGE_STYLE_TYPE_YPC" || it == "BADGE_STYLE_TYPE_MEDIA" }) return true
        var movie = false
        o.walk { key, badge ->
            if (key != "badgeViewModel") return@walk
            val text = badge.optString("badgeText")
            if (CONTENT_RATING.matches(text) ||
                (badge.optString("badgeStyle") == "BADGE_COMMERCE" && !text.equals("Fundraiser", ignoreCase = true))
            ) movie = true
        }
        return movie
    }

    private val CONTENT_RATING = Regex("^(G|PG|PG-13|R|NC-17|NR|TV-(Y|Y7|G|PG|14|MA))$")

    private fun parseVideoRenderer(o: JSONObject): VideoItem? {
        val id = o.optString("videoId").ifBlank { return null }
        val byline = o.optJSONObject("ownerText") ?: o.optJSONObject("shortBylineText") ?: o.optJSONObject("longBylineText")
        val channelRun = byline?.optJSONArray("runs")?.optJSONObject(0)
        val channelId = channelRun?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
        val duration = o.text("lengthText")
            ?: o.optString("lengthSeconds").toLongOrNull()?.let { formatDuration(it * 1000) }
            ?: o.optJSONArray("thumbnailOverlays")?.let { arr ->
                (0 until arr.length()).firstNotNullOfOrNull { arr.optJSONObject(it)?.optJSONObject("thumbnailOverlayTimeStatusRenderer")?.let { r -> r.text("text") } }
            }

        val infoRuns = o.optJSONObject("videoInfo")?.optJSONArray("runs")?.let { runs ->
            (0 until runs.length()).map { runs.optJSONObject(it).optString("text") }.filter { it.isNotBlank() && it.trim() != "•" }
        }.orEmpty()
        val isLive = o.collectStrings("style").any { it.contains("LIVE", ignoreCase = true) } ||
            o.collectStrings("badgeText").any { it.equals("LIVE", ignoreCase = true) } ||
            o.collectStrings("text").any { it.equals("LIVE", ignoreCase = true) } ||
            (o.text("viewCountText") ?: "").contains("watching", ignoreCase = true)
        val avatar = pickChannelAvatarUrl(o)
        return VideoItem(
            id = id,
            title = o.text("title") ?: return null,
            channel = channelRun?.optString("text").orEmpty(),
            channelUrl = channelId?.takeIf { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" },
            thumbnail = videoThumbnailUrl(o, id),
            avatar = avatar?.let { sharpenChannelAvatarUrl(absoluteUrl(it), 96) },
            durationSec = if (isLive) 0L else parseDuration(duration),
            views = parseViews(o.text("viewCountText") ?: o.text("shortViewCountText") ?: infoRuns.firstOrNull { it.contains("view") || it.contains("watching") }),
            uploaded = if (isLive) "Live" else (o.text("publishedTimeText") ?: infoRuns.firstOrNull { it.contains("ago") }),
            isLive = isLive,
        )
    }

    private fun parseLockup(o: JSONObject): VideoItem? {
        val id = o.optString("contentId").ifBlank { return null }
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = meta.optJSONObject("title")?.optString("content")?.ifBlank { null } ?: return null

        val parts = mutableListOf<String>()
        meta.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")?.let { rows ->
            for (r in 0 until rows.length()) {
                val ps = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
                for (p in 0 until ps.length()) ps.optJSONObject(p)?.optJSONObject("text")?.optString("content")?.ifBlank { null }?.let(parts::add)
            }
        }
        val isLive = o.collectStrings("badgeText").any { it.equals("LIVE", ignoreCase = true) } ||
            o.collectStrings("text").any { it.equals("LIVE", ignoreCase = true) } ||
            parts.any { it.contains("watching", ignoreCase = true) }
        val avatar = meta.bestChannelAvatarUrl()
        val duration = o.optJSONObject("contentImage")?.collectStrings("text")?.firstOrNull { DURATION.matches(it) }
        return VideoItem(
            id = id,
            title = title,
            channel = parts.firstOrNull { !it.contains("view") && !it.contains("ago") && !it.contains("watching") }.orEmpty(),
            channelUrl = o.collectStrings("browseId").firstOrNull { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" },
            thumbnail = videoThumbnailUrl(o, id),
            avatar = avatar?.let { sharpenChannelAvatarUrl(absoluteUrl(it), 96) },
            durationSec = if (isLive) 0L else parseDuration(duration),
            views = parseViews(parts.firstOrNull { it.contains("view") || it.contains("watching") }),
            uploaded = if (isLive) "Live" else parts.firstOrNull { it.contains("ago") },
            isLive = isLive,
        )
    }

    internal fun parseCommunity(root: JSONObject): CommunityPage {
        val posts = mutableListOf<CommunityPost>()
        var next: String? = null
        root.walk { key, obj ->
            when (key) {
                "backstagePostRenderer", "sharedPostRenderer", "postRenderer" -> {
                    val id = obj.optString("postId").ifBlank { obj.optString("id") }
                    val text = obj.text("contentText") ?: obj.optJSONObject("contentText")?.optJSONArray("runs")?.let { runs ->
                        (0 until runs.length()).joinToString("") { runs.optJSONObject(it).optString("text") }
                    }.orEmpty()
                    val published = obj.text("publishedTimeText")
                    val author = obj.text("authorText")
                    val avatar = pickChannelAvatarUrl(obj)
                    val image = obj.optJSONObject("backstageAttachment")?.optJSONObject("backstageImageRenderer")?.optJSONObject("image")?.thumbnails()?.lastOrNull()
                    val likes = obj.text("voteCount") ?: obj.text("likeCount")
                    if (id.isNotBlank() && (text.isNotBlank() || image != null)) {
                        posts.add(CommunityPost(id, author.orEmpty(), avatar, text, image, published, likes))
                    }
                }
                "continuationItemRenderer", "continuationItemViewModel" ->
                    obj.collectStrings("token").firstOrNull()?.let { next = it }
            }
        }
        return CommunityPage(posts, next)
    }

    private fun pickChannelAvatarUrl(o: JSONObject): String? {
        o.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")?.let { r ->
                r.thumbnails()?.lastOrNull()?.let { return it }
                r.optJSONObject("thumbnail")?.thumbnails()?.lastOrNull()?.let { return it }
            }
        o.optJSONObject("channelThumbnail")?.thumbnails()?.lastOrNull()?.let { return it }
        o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")?.bestChannelAvatarUrl()?.let { return it }
        o.bestChannelAvatarUrl()?.let { return it }
        var found: String? = null
        o.forEachObject { obj ->
            if (found != null) return@forEachObject
            obj.optJSONArray("sources")?.widestSourceUrl()?.takeIf { isChannelAvatarUrl(it) }?.let { found = it }
        }
        return found
    }

    private fun isChannelAvatarUrl(url: String): Boolean = when {
        "ytimg.com/vi/" in url -> false
        "/ytc/" in url -> true
        "googleusercontent.com" in url && "-c-k-c0x00ffffff-no-rj" in url -> true
        "ggpht.com" in url && "=s" in url && "=w" !in url -> true
        else -> false
    }

    private fun JSONObject.bestChannelAvatarUrl(): String? =
        optJSONObject("image")
            ?.optJSONObject("decoratedAvatarViewModel")
            ?.optJSONObject("avatar")
            ?.optJSONObject("avatarViewModel")
            ?.optJSONObject("image")
            ?.optJSONArray("sources")
            ?.widestSourceUrl()
            ?: optJSONObject("image")?.collectStrings("url")
                ?.filter { "ggpht.com" in it || "googleusercontent.com" in it }
                ?.maxByOrNull { channelAvatarEdgePx(it) }

    internal fun pickBestThumbnailUrl(renderer: JSONObject): String? {
        renderer.bestThumbnailUrl()?.let { return it }
        renderer.optJSONObject("contentImage")?.bestThumbnailUrl()?.let { return it }
        renderer.optJSONObject("contentImage")
            ?.optJSONObject("thumbnailViewModel")
            ?.optJSONObject("image")
            ?.optJSONArray("sources")
            ?.let { pickWidestSourceUrl(it) }
            ?.let { return it }
        return renderer.optJSONObject("contentImage")?.collectStrings("url")
            ?.filter { "ytimg.com/vi/" in it || "googleusercontent.com" in it || "ggpht.com" in it }
            ?.maxWithOrNull(compareBy({ ytimgThumbnailWidth(it) }, { it.length }))
    }

    private fun pickWidestSourceUrl(arr: JSONArray): String? = arr.widestSourceUrl()

    private fun videoThumbnailUrl(renderer: JSONObject, videoId: String): String =
        sharpenFeedThumbnail(pickBestThumbnailUrl(renderer)?.let(::absoluteUrl), videoId)

    private val DURATION = Regex("^\\d{1,2}(:\\d{2}){1,2}$")

    private fun parseDuration(text: String?): Long {
        val t = text?.trim() ?: return 0
        if (!DURATION.matches(t)) return 0
        return t.split(':').fold(0L) { acc, part -> acc * 60 + (part.toLongOrNull() ?: 0) }
    }


    internal fun parseViews(text: String?): Long {
        val t = text?.lowercase()?.replace(",", "")?.trim() ?: return -1
        if (t.startsWith("no ")) return 0
        val m = Regex("([\\d.]+)\\s*([kmb]?)").find(t) ?: return -1
        val n = m.groupValues[1].toDoubleOrNull() ?: return -1
        val mult = when (m.groupValues[2]) { "k" -> 1e3; "m" -> 1e6; "b" -> 1e9; else -> 1.0 }
        return (n * mult).toLong()
    }

    private fun absoluteUrl(url: String) = if (url.startsWith("//")) "https:$url" else url

    internal fun parseChannelHeader(root: JSONObject): ChannelHeaderMeta? {
        var handle: String? = null
        var subsText: String? = null
        var videosText: String? = null
        fun noteLine(line: String?) {
            val t = line?.trim()?.ifBlank { null } ?: return
            val lower = t.lowercase()
            if (subsText == null && "subscriber" in lower) subsText = t
            if (videosText == null) {
                VIDEO_COUNT_IN_LINE.find(lower)?.value?.let { videosText = it }
            }
        }
        fun scanMetadataParts(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                noteLine(arr.optJSONObject(i)?.optJSONObject("text")?.optString("content"))
            }
        }
        root.walk { _, obj ->
            noteLine(obj.text("subscriberCountText"))
            noteLine(obj.text("videoCountText"))
            noteLine(obj.optJSONObject("text")?.optString("content"))
            scanMetadataParts(obj.optJSONArray("metadataParts"))
            obj.optJSONArray("metadataRows")?.let { rows ->
                for (r in 0 until rows.length()) {
                    scanMetadataParts(rows.optJSONObject(r)?.optJSONArray("metadataParts"))
                }
            }
            obj.optString("vanityChannelUrl").takeIf { "@" in it }?.let { url ->
                handle = handle ?: url.substringAfter("@").substringBefore("?").substringBefore("/").ifBlank { null }
            }
            obj.optString("canonicalBaseUrl").takeIf { it.startsWith("/@") }?.let { path ->
                handle = handle ?: path.removePrefix("/@").substringBefore("/").ifBlank { null }
            }
        }
        if (subsText == null || videosText == null) {
            for (line in root.collectStrings("content") + root.collectStrings("simpleText")) {
                noteLine(line)
            }
        }
        val subscribers = parseViews(subsText)
        val videoCount = parseViews(videosText)
        val verified = root.optJSONObject("header")?.let { parseChannelVerified(it) } ?: false
        if (handle == null && subscribers < 0 && videoCount < 0 && !verified) return null
        return ChannelHeaderMeta(handle, subscribers.coerceAtLeast(0), videoCount.coerceAtLeast(-1), verified)
    }


    internal fun parseChannelVerified(headerRoot: JSONObject): Boolean {
        headerRoot.optJSONObject("pageHeaderRenderer")?.optJSONObject("content")
            ?.optJSONObject("pageHeaderViewModel")?.optJSONObject("title")
            ?.optJSONObject("dynamicTextViewModel")?.optJSONObject("rendererContext")
            ?.optJSONObject("accessibilityContext")?.optString("label")?.let { label ->
                if (VERIFIED_CHANNEL_LABEL.containsMatchIn(label)) return true
            }
        var found = false
        headerRoot.walk { key, obj ->
            if (found) return@walk
            if (key == "metadataBadgeRenderer" && isVerifiedChannelBadge(obj.optString("style"))) found = true
        }
        return found
    }

    private fun isVerifiedChannelBadge(style: String): Boolean =
        style == "BADGE_STYLE_TYPE_VERIFIED" || style == "BADGE_STYLE_TYPE_VERIFIED_ARTIST"

    private val VERIFIED_CHANNEL_LABEL = Regex("verified|official artist", RegexOption.IGNORE_CASE)
    private val VIDEO_COUNT_IN_LINE = Regex("""[\d,.]+[kmb]?\s+videos\b""", RegexOption.IGNORE_CASE)
}




internal fun JSONObject.walk(visit: (key: String, obj: JSONObject) -> Unit) {
    val keys = keys()
    while (keys.hasNext()) {
        val k = keys.next()
        when (val v = opt(k)) {
            is JSONObject -> { visit(k, v); v.walk(visit) }
            is JSONArray -> v.walkArray(visit)
        }
    }
}

private fun JSONArray.walkArray(visit: (key: String, obj: JSONObject) -> Unit) {
    for (i in 0 until length()) {
        when (val v = opt(i)) {
            is JSONObject -> v.walk(visit)
            is JSONArray -> v.walkArray(visit)
        }
    }
}


internal fun JSONObject.forEachObject(visit: (JSONObject) -> Unit) {
    visit(this)
    val keys = keys()
    while (keys.hasNext()) {
        when (val v = opt(keys.next())) {
            is JSONObject -> v.forEachObject(visit)
            is JSONArray -> v.forEachObjectInArray(visit)
        }
    }
}

private fun JSONArray.forEachObjectInArray(visit: (JSONObject) -> Unit) {
    for (i in 0 until length()) {
        when (val v = opt(i)) {
            is JSONObject -> v.forEachObject(visit)
            is JSONArray -> v.forEachObjectInArray(visit)
        }
    }
}


internal fun JSONObject.text(key: String): String? {
    val o = optJSONObject(key) ?: return optString(key).ifBlank { null }
    o.optString("simpleText").ifBlank { null }?.let { return it }
    o.optString("content").ifBlank { null }?.let { return it }
    val runs = o.optJSONArray("runs") ?: return null
    return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }.ifBlank { null }
}


internal fun JSONObject.thumbnails(): List<String>? {
    val arr = optJSONObject("thumbnail")?.optJSONArray("thumbnails") ?: optJSONArray("thumbnails") ?: return null
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("url")?.ifBlank { null } }
}

internal fun JSONArray.widestSourceUrl(): String? {
    var best: String? = null
    var bestW = -1
    for (i in 0 until length()) {
        val entry = optJSONObject(i) ?: continue
        val url = entry.optString("url").ifBlank { continue }
        val w = entry.optInt("width", channelAvatarEdgePx(url).takeIf { it > 0 } ?: ytimgThumbnailWidth(url))
        if (w > bestW) {
            bestW = w
            best = url
        }
    }
    return best ?: optJSONObject(length() - 1)?.optString("url")?.ifBlank { null }
}


internal fun JSONObject.bestThumbnailUrl(): String? {
    val arr = optJSONObject("thumbnail")?.optJSONArray("thumbnails") ?: optJSONArray("thumbnails") ?: return null
    var best: String? = null
    var bestW = -1
    for (i in 0 until arr.length()) {
        val entry = arr.optJSONObject(i) ?: continue
        val url = entry.optString("url").ifBlank { continue }
        val w = entry.optInt("width", 0)
        if (w > bestW) {
            bestW = w
            best = url
        }
    }
    return best ?: arr.optJSONObject(arr.length() - 1)?.optString("url")?.ifBlank { null }
}

private fun JSONObject.firstImageSource(): String? = collectStrings("url").firstOrNull { it.contains("ytimg") || it.contains("ggpht") }


internal fun JSONObject.collectStrings(field: String): List<String> {
    val out = mutableListOf<String>()
    fun visitObj(o: JSONObject) {
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            when (val v = o.opt(k)) {
                is String -> if (k == field && v.isNotBlank()) out += v
                is JSONObject -> visitObj(v)
                is JSONArray -> for (i in 0 until v.length()) (v.opt(i) as? JSONObject)?.let(::visitObj)
            }
        }
    }
    visitObj(this)
    return out
}
