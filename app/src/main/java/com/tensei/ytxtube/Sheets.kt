package com.tensei.ytxtube

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsSheet(video: VideoItem, player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(video.thumbnail, Modifier.width(112.dp).aspectRatio(16f / 9f), radius = 14)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.channel, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
            }
        }
        OptionRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") { player.playNext(video); onDismiss() }
        OptionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to queue") { player.addToQueue(video); onDismiss() }
        OptionRow(Icons.Rounded.Download, "Download") { player.downloadFor = video; onDismiss() }
        if (Account.signedIn) {
            OptionRow(Icons.Rounded.WatchLater, "Save to Watch later") { player.saveToWatchLater(video); onDismiss() }
        }
        OptionRow(Icons.Rounded.Share, "Share") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
                .putExtra(Intent.EXTRA_SUBJECT, video.title)
            context.startActivity(Intent.createChooser(send, null))
            onDismiss()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.onSurface)
        Spacer(Modifier.width(18.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}




@Composable
fun QueueCard(player: PlayerState, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val next = player.queue.firstOrNull() ?: return
    Row(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onOpen)
            .clip(RoundedCornerShape(24.dp))
            .background(c.secondaryContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(next.thumbnail, Modifier.width(88.dp).aspectRatio(16f / 9f), radius = 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Queue · ${player.queue.size}",
                color = c.onSecondaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
            )
            Text(next.title, color = c.onSecondaryContainer, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = c.onSecondaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, modifier = Modifier.weight(1f))
            if (player.queue.isNotEmpty()) {
                Text(
                    "Clear", color = c.primary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.pressable { player.clearQueue() }.padding(8.dp),
                )
            }
        }
        if (player.queue.isEmpty()) {
            Text(
                "Nothing queued. Press and hold a video to add it.",
                color = c.onSurfaceVariant, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxHeight(0.7f)) {
                itemsIndexed(player.queue, key = { i, v -> "$i-${v.id}" }) { index, video ->
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        VideoRow(video, onClick = { player.playFromQueue(index); onDismiss() }) {
                            Box(Modifier.size(40.dp).pressable { player.removeFromQueue(index) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, "Remove from queue", tint = c.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}



@Composable
fun SponsorBlockSettings() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SponsorBlock", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("Skip sponsor reads and other segments", color = c.onSurfaceVariant, fontSize = 13.sp)
            }
            Switch(
                checked = SponsorBlock.enabled,
                onCheckedChange = SponsorBlock::updateEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
            )
        }
        if (SponsorBlock.enabled) {
            Spacer(Modifier.height(10.dp))
            SponsorBlock.Category.entries.forEach { category -> CategoryRow(category) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Segments from SponsorBlock (sponsor.ajay.app), a community project. Data licensed CC BY-NC-SA 4.0.",
            color = c.onSurfaceVariant, fontSize = 11.5.sp, lineHeight = 16.sp,
        )
    }
}

@Composable
private fun CategoryRow(category: SponsorBlock.Category) {
    val c = MaterialTheme.colorScheme
    val current = SponsorBlock.actionFor(category)
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(category.color))
            Spacer(Modifier.width(8.dp))
            Text(category.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SponsorBlock.Action.entries.forEach { action ->
                val on = action == current
                Box(
                    Modifier
                        .pressable { SponsorBlock.setAction(category, action) }
                        .clip(RoundedCornerShape(if (on) 18.dp else 10.dp))
                        .background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(action.label, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}




@Composable
fun AppearanceSettings() {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dark = isDarkTheme()
    Column(Modifier.fillMaxWidth()) {
        Text("Appearance", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("system" to "System", "light" to "Light", "dark" to "Dark", "amoled" to "AMOLED").forEach { (key, label) ->
                val on = ThemeSettings.mode == key
                Box(
                    Modifier
                        .pressable { ThemeSettings.chooseMode(key) }
                        .clip(RoundedCornerShape(if (on) 18.dp else 10.dp))
                        .background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text(label, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(14.dp))
        val choices = buildList {
            if (ThemeSettings.wallpaperAvailable) add(null to "Wallpaper")
            YTX_THEMES.forEach { add(it.hex to it.name) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            choices.forEach { (hex, name) ->
                val selected = ThemeSettings.seed?.lowercase() == hex?.lowercase()
                val scheme = schemeFor(context, hex, dark)
                Column(
                    Modifier.width(78.dp).pressable { ThemeSettings.choose(hex) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(if (selected) CookieShape else CircleShape)
                            .background(scheme.surface)
                            .padding(5.dp)
                            .clip(if (selected) CookieShape else CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    0f to scheme.primary, 0.5f to scheme.primary,
                                    0.5f to scheme.primaryContainer, 1f to scheme.primaryContainer,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, "Selected", tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        name, fontSize = 11.5.sp, maxLines = 1, softWrap = false,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (selected) c.onSurface else c.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Subtitles / Closed Captions selection bottom sheet, matching FlashTube. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitlesSheet(
    player: PlayerState,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val videoId = player.current?.id
    var info by remember(videoId) { mutableStateOf(videoId?.let { StreamCache.get(it) }) }

    LaunchedEffect(videoId) {
        if (info == null && videoId != null) {
            info = withContext(Dispatchers.IO) { runCatching { StreamCache.getOrLoad(videoId) }.getOrNull() }
        }
    }

    data class SubtitleOption(
        val code: String,
        val label: String,
        val url: String?,
    )

    val subtitleTracks = remember(info, videoId) {
        val streamTracks = info?.subtitles.orEmpty().filter { it.isUrl && it.content.isNotBlank() }
        if (streamTracks.isNotEmpty()) {
            streamTracks.map { sub ->
                val code = sub.languageTag?.ifBlank { "en" } ?: "en"
                val name = sub.displayLanguageName?.ifBlank { code } ?: code
                val label = if (sub.isAutoGenerated) "$name (auto-generated)" else name
                SubtitleOption(code = code, label = label, url = sub.content)
            }
        } else {
            val fallbackTracks = videoId?.let { CaptionTracks.get(it) ?: runCatching { CaptionTracks.load(it) }.getOrNull() }.orEmpty()
            fallbackTracks.map { track ->
                SubtitleOption(code = track.languageTag, label = track.label, url = track.url)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Captions / Subtitles", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))

            // Option 1: Off (Turn off captions)
            val isOff = player.selectedSubtitleLanguage == null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable {
                        player.setSubtitleLanguage(null)
                        onDismiss()
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isOff) c.surfaceContainerHigh else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SubtitlesOff, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Off", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.onSurface, modifier = Modifier.weight(1f))
                if (isOff) {
                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(4.dp))

            if (subtitleTracks.isEmpty()) {
                Text(
                    "No captions available for this video.",
                    color = c.onSurfaceVariant,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    subtitleTracks.forEach { track ->
                        val selected = player.selectedSubtitleLanguage == track.code

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable {
                                    player.setSubtitleLanguage(track.code, track.url)
                                    onDismiss()
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) c.surfaceContainerHigh else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Subtitles,
                                contentDescription = null,
                                tint = if (selected) c.primary else c.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(track.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.onSurface, modifier = Modifier.weight(1f))
                            if (selected) {
                                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = c.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

