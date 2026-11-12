package com.tensei.ytxtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A creator-defined chapter: it starts at [startMs] and runs until the next one begins. */
@Immutable
data class Chapter(val title: String, val startMs: Long, val previewUrl: String? = null)

/** The chapter playing at [positionMs]: the last one that has started. */
fun List<Chapter>.at(positionMs: Long): Chapter? = lastOrNull { it.startMs <= positionMs }

/** Shown on the watch page when the video has chapters: the current one, tap for the full list. */
@Composable
fun ChaptersCard(player: PlayerState, onOpen: () -> Unit) {
    val chapters = player.details?.chapters.orEmpty()
    if (chapters.size < 2) return
    val c = MaterialTheme.colorScheme
    val current = chapters.at(player.positionMs) ?: chapters.first()
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onOpen)
            .clip(RoundedCornerShape(24.dp))
            .background(c.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Chapters · ${chapters.size}",
                color = c.onSecondaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
            )
            Text(
                current.title,
                color = c.onSecondaryContainer, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, null, tint = c.onSecondaryContainer)
    }
}

/** Every chapter with its start time. Tap one to jump there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val chapters = player.details?.chapters.orEmpty()
    val current = chapters.at(player.positionMs)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Text(
            "Chapters",
            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
        )
        LazyColumn {
            items(chapters) { chapter ->
                val selected = chapter == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(pressedScale = 0.98f) {
                            player.seekTo(chapter.startMs)
                            onDismiss()
                        }
                        .background(if (selected) c.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (chapter.previewUrl != null) {
                        Box(Modifier.width(104.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(c.surfaceContainerHigh)) {
                            AsyncImage(
                                model = chapter.previewUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Text(
                                formatDuration(chapter.startMs),
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                    } else {
                        Text(
                            formatDuration(chapter.startMs),
                            color = if (selected) c.primary else c.onSurfaceVariant,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.width(64.dp),
                        )
                    }
                    Text(
                        chapter.title,
                        color = if (selected) c.primary else c.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
