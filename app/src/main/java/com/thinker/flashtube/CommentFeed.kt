package com.thinker.flashtube

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap











object CommentFeed {
    suspend fun first(videoId: String): CommentsPage = withContext(Dispatchers.IO) {
        var token: String? = null
        InnerTube.postAny("next", JSONObject().put("videoId", videoId)).forEachObject { obj ->
            if (token == null && obj.optString("sectionIdentifier") == "comment-item-section") {
                token = obj.collectStrings("token").firstOrNull()
            }
        }

        val start = token ?: return@withContext CommentsPage(emptyList(), total = -1, disabled = true, next = null)
        parsePage(InnerTube.postAny("next", JSONObject().put("continuation", start)))
    }


    suspend fun more(token: String): CommentsPage = withContext(Dispatchers.IO) {
        parsePage(InnerTube.postAny("next", JSONObject().put("continuation", token)))
    }

    internal fun parsePage(page: JSONObject): CommentsPage {

        val entities = HashMap<String, JSONObject>()
        page.forEachObject { obj ->
            val key = obj.optString("entityKey")
            val payload = obj.optJSONObject("payload")
            if (key.isNotEmpty() && payload != null) entities[key] = payload
        }
        val comments = ArrayList<Comment>()
        var total = -1
        var next: String? = null
        page.walk { key, obj ->
            if (key != "reloadContinuationItemsCommand" && key != "appendContinuationItemsAction") return@walk
            val items = obj.optJSONArray("continuationItems") ?: return@walk
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                when {
                    item.has("commentsHeaderRenderer") -> item.getJSONObject("commentsHeaderRenderer").text("countText")
                        ?.let { InnerTube.parseViews(it) }?.takeIf { it >= 0 }?.let { total = it.toInt() }
                    item.has("commentThreadRenderer") -> {
                        val thread = item.getJSONObject("commentThreadRenderer")
                        val repliesToken = thread.optJSONObject("replies")?.collectStrings("token")?.firstOrNull()
                        thread.viewModel()?.let { vm -> parseComment(vm, entities, repliesToken)?.let(comments::add) }
                    }

                    item.has("commentViewModel") -> item.viewModel()?.let { vm -> parseComment(vm, entities, null)?.let(comments::add) }

                    item.has("continuationItemRenderer") -> item.collectStrings("token").firstOrNull()?.let { next = it }
                }
            }
        }

        val unique = comments.distinctBy { it.id }
        return CommentsPage(unique, total, disabled = false, next = next)
    }


    private fun JSONObject.viewModel(): JSONObject? {
        var found: JSONObject? = null
        walk { key, obj -> if (found == null && key == "commentViewModel" && obj.has("commentKey")) found = obj }
        return found
    }

    private fun parseComment(vm: JSONObject, entities: Map<String, JSONObject>, repliesToken: String?): Comment? {
        val entity = entities[vm.optString("commentKey")]?.optJSONObject("commentEntityPayload") ?: return null
        val props = entity.optJSONObject("properties") ?: return null
        val author = entity.optJSONObject("author")
        val toolbar = entity.optJSONObject("toolbar")
        val state = entities[vm.optString("toolbarStateKey")]?.optJSONObject("engagementToolbarStateEntityPayload")
        val replyCount = toolbar?.optString("replyCount")?.let { InnerTube.parseViews(it) }?.takeIf { it > 0 }?.toInt() ?: 0
        return Comment(
            id = props.optString("commentId").ifBlank { vm.optString("commentId") }.ifBlank { return null },
            author = author?.optString("displayName").orEmpty(),
            channelId = author?.optString("channelId")?.takeIf { it.startsWith("UC") },
            avatar = author?.optString("avatarThumbnailUrl")?.ifBlank { null }?.let { if (it.startsWith("//")) "https:$it" else it },
            text = props.optJSONObject("content")?.optString("content").orEmpty().trim(),
            likes = toolbar?.optString("likeCountNotliked")?.let { InnerTube.parseViews(it) }?.takeIf { it > 0 }?.toInt() ?: 0,
            ago = props.optString("publishedTime").ifBlank { null },
            pinned = vm.has("pinnedText") || props.has("pinnedText"),
            hearted = state?.optString("heartState") == "TOOLBAR_HEART_STATE_HEARTED",
            replyCount = replyCount,
            repliesToken = repliesToken?.takeIf { replyCount > 0 },
            liked = state?.optString("likeState") == "TOOLBAR_LIKE_STATE_LIKED",
        )
    }
}







object ChannelNames {

    val known = mutableStateMapOf<String, String>()

    private val asked = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(4)
    private val TITLE = Regex("<title>([^<]*)</title>")
    private const val HEAD_BYTES = 4096L


    fun resolve(scope: CoroutineScope, channelIds: Collection<String>) {
        channelIds.filter { asked.add(it) }.forEach { id ->
            scope.launch {
                val name = gate.withPermit { withContext(Dispatchers.IO) { runCatching { fetch(id) } } }
                name.onSuccess { if (it != null) known[id] = it }

                    .onFailure { asked.remove(id) }
            }
        }
    }

    private fun fetch(channelId: String): String? {
        val request = Request.Builder()
            .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            .header("User-Agent", Net.USER_AGENT)
            .build()
        Net.client.newCall(request).execute().use { r ->

            if (r.code == 404) return null
            if (!r.isSuccessful) throw IOException("feed ${r.code}")
            val source = r.body?.source() ?: return null

            source.request(HEAD_BYTES)
            return parseTitle(source.buffer.readByteString(minOf(source.buffer.size, HEAD_BYTES)).utf8())
        }
    }

    internal fun parseTitle(xml: String): String? = TITLE.find(xml)?.groupValues?.get(1)?.let(::unescape)?.trim()?.ifBlank { null }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
}


fun Comment.displayName(): String = name ?: channelId?.let { ChannelNames.known[it] } ?: author





object ChannelAvatars {
    val known = mutableStateMapOf<String, String>()
    private val asked = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(4)

    fun resolve(scope: CoroutineScope, videos: List<VideoItem>, seed: Map<String, String?> = emptyMap()) {
        seed.forEach { (id, url) -> url?.let { known[id] = normalizeChannelAvatarUrl(it) } }
        val need = videos.filter { it.avatar == null }.mapNotNull { it.channelId() }.toSet()
        need.filter { it !in known && asked.add(it) }.forEach { id ->
            scope.launch {
                gate.withPermit {
                    withContext(Dispatchers.IO) {
                        runCatching { InnerTube.channelAvatarUrl(id) }
                            .onSuccess { url -> if (url != null) known[id] = url }
                            .onFailure { asked.remove(id) }
                    }
                }
            }
        }
    }
}
