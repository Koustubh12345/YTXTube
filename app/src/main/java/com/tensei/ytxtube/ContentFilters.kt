package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hide videos from feeds and search by title keyword or channel name. Lists are kept on the
 * device only. Changes apply the next time a feed loads or refreshes.
 */
object ContentFilters {
    private const val PREFS = "appearance"
    private const val KEY_WORDS = "filter_keywords"
    private const val KEY_CHANNELS = "filter_channels"

    private var prefs: SharedPreferences? = null

    var keywords by mutableStateOf<List<String>>(emptyList())
        private set
    var channels by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        keywords = parse(p.getString(KEY_WORDS, "").orEmpty())
        channels = parse(p.getString(KEY_CHANNELS, "").orEmpty())
    }

    fun setKeywords(raw: String) {
        keywords = parse(raw)
        prefs?.edit()?.putString(KEY_WORDS, keywords.joinToString("\n"))?.apply()
    }

    fun setChannels(raw: String) {
        channels = parse(raw)
        prefs?.edit()?.putString(KEY_CHANNELS, channels.joinToString("\n"))?.apply()
    }

    fun blockChannel(name: String) {
        if (name.isBlank() || channels.any { it.equals(name, ignoreCase = true) }) return
        setChannels((channels + name).joinToString("\n"))
    }

    private fun parse(raw: String): List<String> =
        raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    fun allows(video: VideoItem): Boolean {
        if (!Tweaks.allows(video)) return false
        if (keywords.isNotEmpty()) {
            val title = video.title.lowercase()
            if (keywords.any { title.contains(it.lowercase()) }) return false
        }
        if (channels.isNotEmpty() && channels.any { it.equals(video.channel, ignoreCase = true) }) return false
        return true
    }

    fun filter(list: List<VideoItem>): List<VideoItem> =
        if (keywords.isEmpty() && channels.isEmpty() && !Tweaks.anyFeedFilter) list else list.filter(::allows)
}

/** A small editor for a comma separated list. */
@Composable
fun FilterListDialog(
    title: String,
    hint: String,
    initial: List<String>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
