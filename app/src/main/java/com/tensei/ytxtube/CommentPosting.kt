package com.tensei.ytxtube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException











object CommentPosting {
    private class VideoParams(
        var createComment: String? = null,
        val reply: MutableMap<String, String> = HashMap(),

        val like: MutableMap<String, Pair<String?, String?>> = HashMap(),
        var next: String? = null,
        var started: Boolean = false,
    )

    private val cache = HashMap<String, VideoParams>()
    private val lock = Mutex()
    private const val MAX_PAGES = 8

    fun forget(videoId: String) { cache.remove(videoId) }

    suspend fun comment(videoId: String, text: String) = withContext(Dispatchers.IO) {
        val params = lock.withLock {
            val vp = cache.getOrPut(videoId) { VideoParams() }
            if (!vp.started) loadFirstPage(videoId, vp)
            vp.createComment
        } ?: throw IOException("Comments are turned off or can't be posted on this video.")
        checkPosted(
            InnerTube.post("comment/create_comment", JSONObject().put("commentText", text).put("createCommentParams", params)),
        )
    }

    suspend fun reply(videoId: String, threadId: String, text: String) = withContext(Dispatchers.IO) {
        val params = lock.withLock {
            val vp = cache.getOrPut(videoId) { VideoParams() }
            if (!vp.started) loadFirstPage(videoId, vp)
            var pages = 1
            while (vp.reply[threadId] == null && vp.next != null && pages < MAX_PAGES) {
                loadPage(vp, vp.next!!)
                pages++
            }
            vp.reply[threadId]
        } ?: throw IOException("Couldn't find that comment to reply to. Try again.")
        checkPosted(
            InnerTube.post("comment/create_comment_reply", JSONObject().put("commentText", text).put("createReplyParams", params)),
        )
    }


    suspend fun setLiked(videoId: String, commentId: String, liked: Boolean) = withContext(Dispatchers.IO) {
        val action = lock.withLock {
            val vp = cache.getOrPut(videoId) { VideoParams() }
            if (!vp.started) loadFirstPage(videoId, vp)
            var pages = 1
            while (vp.like[commentId] == null && vp.next != null && pages < MAX_PAGES) {
                loadPage(vp, vp.next!!)
                pages++
            }
            val (likeAction, unlikeAction) = vp.like[commentId] ?: (null to null)
            if (liked) likeAction else unlikeAction
        } ?: throw IOException("Couldn't find that comment to like. Try again.")
        InnerTube.post("comment/perform_comment_action", JSONObject().put("actions", JSONArray().put(action)))
        Unit
    }

    private fun loadFirstPage(videoId: String, vp: VideoParams) {
        vp.started = true
        val next = InnerTube.post("next", JSONObject().put("videoId", videoId))
        var token: String? = null
        next.forEachObject { obj ->
            if (token == null && obj.optString("sectionIdentifier") == "comment-item-section") {
                token = obj.collectStrings("token").firstOrNull()
            }
        }
        token?.let { loadPage(vp, it) }
    }

    private fun loadPage(vp: VideoParams, token: String) {
        val parsed = parsePage(InnerTube.post("next", JSONObject().put("continuation", token)))
        if (vp.createComment == null) vp.createComment = parsed.createComment
        vp.reply.putAll(parsed.replyParams)
        vp.like.putAll(parsed.likeActions)
        vp.next = parsed.next
    }

    internal class ParsedPage(
        val createComment: String?,
        val replyParams: Map<String, String>,

        val threadsWithToolbar: Set<String>,
        val likeActions: Map<String, Pair<String?, String?>> = emptyMap(),
        val next: String?,
    )

    internal fun parsePage(page: JSONObject): ParsedPage {

        val entities = HashMap<String, JSONObject>()

        page.forEachObject { obj ->
            val key = obj.optString("entityKey")
            val payload = obj.optJSONObject("payload")
            if (key.isNotEmpty() && payload != null) entities[key] = payload
        }
        val replies = HashMap<String, String>()
        val toolbars = HashSet<String>()
        val likes = HashMap<String, Pair<String?, String?>>()
        page.walk { key, obj ->
            if (key == "commentViewModel" && obj.has("commentId")) {
                val id = obj.optString("commentId")
                val toolbar = entities[obj.optString("toolbarSurfaceKey")] ?: return@walk
                toolbars += id
                toolbar.collectStrings("createReplyParams").firstOrNull()?.let { replies[id] = it }

                val likeAction = toolbar.optJSONObject("likeCommand")?.collectStrings("action")?.firstOrNull()
                val unlikeAction = toolbar.optJSONObject("unlikeCommand")?.collectStrings("action")?.firstOrNull()
                if (likeAction != null || unlikeAction != null) likes[id] = likeAction to unlikeAction
            }
        }


        var next: String? = null
        page.walk { key, obj ->
            if (key == "reloadContinuationItemsCommand" || key == "appendContinuationItemsAction") {
                val items = obj.optJSONArray("continuationItems") ?: return@walk
                if (!items.hasThreads()) return@walk
                items.optJSONObject(items.length() - 1)?.optJSONObject("continuationItemRenderer")
                    ?.collectStrings("token")?.firstOrNull()?.let { next = it }
            }
        }
        return ParsedPage(
            createComment = page.collectStrings("createCommentParams").firstOrNull(),
            replyParams = replies, threadsWithToolbar = toolbars, likeActions = likes, next = next,
        )
    }

    private fun JSONArray.hasThreads() = (0 until length()).any { optJSONObject(it)?.has("commentThreadRenderer") == true }

    private fun checkPosted(response: JSONObject) {
        val text = response.toString()
        if ("STATUS_FAILED" in text || !(text.contains("createCommentAction") || text.contains("createCommentReplyAction") || text.contains("STATUS_SUCCEEDED"))) {
            Log.w("YTXTube", "comment post response: ${text.take(600)}")
            throw IOException("YouTube didn't accept the comment.")
        }
    }
}
