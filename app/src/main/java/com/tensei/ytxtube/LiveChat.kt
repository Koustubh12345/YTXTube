package com.tensei.ytxtube

import android.content.Context
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.UUID

@Immutable
data class LiveChatMessage(
    val id: String,
    val author: String,
    val text: String,
    val avatar: String? = null,
    val pending: Boolean = false,
)

internal data class LiveChatPage(
    val messages: List<LiveChatMessage>,
    val next: String?,
    val sendParams: String? = null,
)

internal data class LiveChatStart(
    val continuation: String,
    val channelId: String?,
    val sendParams: String?,
)

@Stable
class LiveChatState(private val scope: CoroutineScope) {
    var messages by mutableStateOf<List<LiveChatMessage>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var live by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var draft by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set
    var sendError by mutableStateOf<String?>(null)
        private set

    private var videoId: String? = null
    private var channelId: String? = null
    private var sendParams: String? = null
    private var job: Job? = null

    fun start(id: String, channelHint: String? = null) {
        if (videoId == id && job?.isActive == true) return
        stop()
        videoId = id
        channelId = channelHint
        sendParams = null
        messages = emptyList()
        loading = true
        live = false
        error = null
        sendError = null
        job = scope.launch {
            try {
                val start: LiveChatStart = withContext(Dispatchers.IO) { LiveChatFeed.start(id) }
                    ?: run {
                        error = "Live chat isn't available for this stream."
                        loading = false
                        return@launch
                    }
                if (channelId == null) channelId = start.channelId
                if (sendParams == null) sendParams = start.sendParams
                var cursor = start.continuation
                while (isActive) {
                    val page = withContext(Dispatchers.IO) { LiveChatFeed.page(cursor) }
                    if (page.messages.isNotEmpty()) {
                        val seen = messages.mapTo(HashSet()) { it.id }
                        messages = (messages + page.messages.filter { seen.add(it.id) }).takeLast(220)
                    }
                    if (sendParams == null) sendParams = page.sendParams
                    page.next?.takeIf { it.isNotBlank() }?.let { cursor = it }
                    live = true
                    loading = false
                    delay(2_500)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                error = YouTube.friendlyError(t)
                loading = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        loading = false
        live = false
        sending = false
    }

    fun send() {
        val id = videoId ?: return
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        if (!Account.signedIn) {
            sendError = "Sign in to chat."
            return
        }
        sending = true
        sendError = null
        scope.launch {
            try {
                val sent = withContext(Dispatchers.IO) {
                    LiveChatFeed.send(id, channelId, sendParams, text)
                }
                if (sent.isEmpty()) {
                    val profile = Account.profile
                    val local = LiveChatMessage(
                        id = "local-${UUID.randomUUID()}",
                        author = profile?.name ?: "You",
                        text = text,
                        avatar = profile?.avatar,
                    )
                    messages = (messages + local).takeLast(220)
                } else {
                    val seen = messages.mapTo(HashSet()) { it.id }
                    messages = (messages + sent.filter { seen.add(it.id) }).takeLast(220)
                }
                draft = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                sendError = YouTube.friendlyError(t)
            } finally {
                sending = false
            }
        }
    }
}

object LiveChatFeed {

    internal fun start(videoId: String): LiveChatStart? {
        val root = InnerTube.postAny("next", JSONObject().put("videoId", videoId))
        var token: String? = null
        var channelId: String? = null
        var ownerId: String? = null
        var sendParams: String? = null
        root.walk { key, obj ->
            when (key) {
                "liveChatRenderer" -> {
                    if (token == null) token = firstContinuation(obj.optJSONArray("continuations"))
                }

                "videoOwnerRenderer" -> {
                    if (ownerId == null) {
                        ownerId = obj.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                            ?.takeIf { it.startsWith("UC") }
                    }
                }

                "liveChatSendMessageCommand" -> {
                    if (sendParams == null) {
                        sendParams = obj.optString("params").ifBlank { null }
                    }
                }

                "browseEndpoint" -> {
                    if (channelId == null) {
                        channelId = obj.optString("browseId").takeIf { it.startsWith("UC") }
                    }
                }
            }
        }
        val continuation = token ?: return null
        return LiveChatStart(
            continuation = continuation,
            channelId = ownerId ?: channelId,
            sendParams = sendParams,
        )
    }

    internal fun page(cursor: String): LiveChatPage {
        val root = InnerTube.postAny("live_chat/get_live_chat", JSONObject().put("continuation", cursor))
        val out = ArrayList<LiveChatMessage>()
        var next: String? = null
        var sendParams: String? = null
        root.walk { key, obj ->
            when (key) {
                "liveChatTextMessageRenderer",
                "liveChatPaidMessageRenderer",
                "liveChatMembershipItemRenderer",
                -> parseMessage(obj)?.let(out::add)

                "liveChatContinuation" -> {
                    next = firstContinuation(obj.optJSONArray("continuations")) ?: next
                }

                "liveChatSendMessageCommand" -> {
                    if (sendParams == null) {
                        sendParams = obj.optString("params").ifBlank { null }
                    }
                }
            }
        }
        return LiveChatPage(out.distinctBy { it.id }, next, sendParams)
    }

    /**
     * Posts a chat message. The `params` blob is the protobuf `LiveMessageParams`:
     * params { ids { channel_id, video_id } }, number_0 = 1, number_1 = 4.
     * YouTube expects it double-base64 encoded with a percent-encoded inner layer.
     */
    internal fun send(
        videoId: String,
        channelId: String?,
        pageParams: String?,
        text: String,
    ): List<LiveChatMessage> {
        val params = pageParams ?: channelId?.let { liveMessageParams(it, videoId) }
            ?: throw IOException("Chat isn't ready yet. Try again in a moment.")
        val body = JSONObject()
            .put("params", params)
            .put("clientMessageId", UUID.randomUUID().toString())
            .put(
                "richMessage",
                JSONObject().put(
                    "textSegments",
                    JSONArray().put(JSONObject().put("text", text)),
                ),
            )
        val root = InnerTube.post("live_chat/send_message", body)
        val out = ArrayList<LiveChatMessage>()
        root.walk { key, obj ->
            if (key == "liveChatTextMessageRenderer" ||
                key == "liveChatPaidMessageRenderer" ||
                key == "liveChatMembershipItemRenderer"
            ) {
                parseMessage(obj)?.let(out::add)
            }
        }
        return out.distinctBy { it.id }
    }

    internal fun liveMessageParams(channelId: String, videoId: String): String {
        val ids = ByteArrayOutputStream().apply {
            write(protoString(1, channelId))
            write(protoString(2, videoId))
        }.toByteArray()
        val params = protoBytes(5, ids)
        val root = ByteArrayOutputStream().apply {
            write(protoBytes(1, params))
            write(protoVarintField(2, 1L))
            write(protoVarintField(3, 4L))
        }.toByteArray()
        val inner = Base64.encodeToString(root, Base64.NO_WRAP)
        return Base64.encodeToString(
            URLEncoder.encode(inner, "UTF-8").toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
    }

    private fun protoVarint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (true) {
            val bits = (v and 0x7f).toInt()
            v = v ushr 7
            if (v == 0L) {
                out.write(bits)
                return out.toByteArray()
            }
            out.write(bits or 0x80)
        }
    }

    private fun protoTag(field: Int, wire: Int): ByteArray =
        protoVarint(((field shl 3) or wire).toLong())

    private fun protoBytes(field: Int, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(protoTag(field, 2))
        out.write(protoVarint(bytes.size.toLong()))
        out.write(bytes)
        return out.toByteArray()
    }

    private fun protoVarintField(field: Int, value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(protoTag(field, 0))
        out.write(protoVarint(value))
        return out.toByteArray()
    }

    private fun protoString(field: Int, value: String): ByteArray =
        protoBytes(field, value.toByteArray(Charsets.UTF_8))

    private fun firstContinuation(continuations: JSONArray?): String? {
        if (continuations == null) return null
        for (i in 0 until continuations.length()) {
            val entry = continuations.optJSONObject(i) ?: continue
            continuationFrom(entry)?.let { return it }
        }
        return null
    }

    private fun continuationFrom(entry: JSONObject): String? {
        for (key in listOf(
            "reloadContinuationData",
            "realtimeChatRealtimeContinuationData",
            "invalidationContinuationData",
            "timedContinuationData",
        )) {
            entry.optJSONObject(key)?.optString("continuation")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun parseMessage(obj: JSONObject): LiveChatMessage? {
        val author = obj.text("authorName").orEmpty().trim()
        val text = obj.text("message").orEmpty().trim()
        if (author.isBlank() || text.isBlank()) return null
        val avatar = obj.optJSONObject("authorPhoto")?.thumbnails()?.lastOrNull()
        val id = obj.optString("id").ifBlank { obj.optString("messageId") }
            .ifBlank { "${author}_${text}_${obj.optString("timestampUsec")}" }
        return LiveChatMessage(id = id, author = author, text = text, avatar = avatar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatPanel(
    state: LiveChatState,
    onSignIn: () -> Unit,
    onClose: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) runCatching { list.animateScrollToItem(state.messages.lastIndex) }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .imePadding()
                .padding(bottom = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state.live) c.error else c.onSurfaceVariant),
                )
                Spacer(Modifier.width(8.dp))
                Text("Live chat", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(42.dp).pressable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        "Close live chat",
                        tint = c.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.error != null && state.messages.isEmpty() -> Text(
                        state.error.orEmpty(),
                        color = c.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    )

                    state.loading && state.messages.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(vertical = 34.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }

                    state.messages.isEmpty() -> Text(
                        "Waiting for messages…",
                        color = c.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    )

                    else -> LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            LiveChatRow(message)
                        }
                    }
                }
            }
            state.sendError?.let { message ->
                Text(
                    message,
                    color = c.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (Account.signedIn) {
                LiveChatComposer(state)
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.surfaceContainer)
                        .pressable { onSignIn() }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Sign in to chat",
                        color = c.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveChatComposer(state: LiveChatState) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val view = LocalView.current
    val focus = LocalFocusManager.current
    val canSend = state.draft.isNotBlank() && !state.sending

    fun send() {
        if (!canSend) return
        focus.clearFocus(force = true)
        context.getSystemService(Context.INPUT_METHOD_SERVICE)
            ?.let { it as? InputMethodManager }
            ?.hideSoftInputFromWindow(view.windowToken, 0)
        state.send()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.surfaceContainer)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (state.draft.isEmpty()) {
                Text("Chat…", color = c.onSurfaceVariant, fontSize = 14.sp)
            }
            BasicTextField(
                value = state.draft,
                onValueChange = { state.draft = it },
                textStyle = TextStyle(color = c.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(c.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(38.dp)
                .pressable(enabled = canSend) { send() }
                .clip(CircleShape)
                .background(if (canSend) c.primary else c.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (state.sending) {
                CircularProgressIndicator(
                    color = c.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    "Send message",
                    tint = if (canSend) c.onPrimary else c.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveChatRow(message: LiveChatMessage) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ChannelAvatar(message.avatar, message.author, size = 26)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                message.author,
                color = c.onSurfaceVariant,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(message.text, color = c.onSurface, fontSize = 13.5.sp, lineHeight = 18.sp)
        }
    }
}
