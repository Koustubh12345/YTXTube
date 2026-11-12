package com.tensei.ytxtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val RYD_SITE = "https://returnyoutubedislike.com"

/**
 * Likes, dislikes and a ratio bar for the video that is playing.
 *
 * Draws nothing while the feature is off, while loading, or when the lookup fails, so it can be
 * dropped under the title without leaving a gap. It also carries the credit link the Return
 * YouTube Dislike API terms ask for.
 */
@Composable
fun VoteBar(videoId: String, modifier: Modifier = Modifier) {
    if (!ReturnDislike.enabled) return
    val votes by produceState<VoteCounts?>(initialValue = null, videoId, ReturnDislike.enabled) {
        value = ReturnDislike.votes(videoId)
    }
    val v = votes ?: return
    val c = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val ratio = v.likeRatio

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "${ReturnDislike.format(v.likes)} likes, ${ReturnDislike.format(v.dislikes)} dislikes"
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ThumbUp, null, tint = c.onSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                ReturnDislike.format(v.likes),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.width(18.dp))
            Icon(Icons.Rounded.ThumbDown, null, tint = c.onSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                ReturnDislike.format(v.dislikes),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Return YouTube Dislike",
                modifier = Modifier.clickable { uri.openUri(RYD_SITE) },
                color = c.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (ratio != null) {
            Spacer(Modifier.height(8.dp))
            // Keep a sliver of each colour visible so very lopsided videos still read as a bar.
            val like = ratio.coerceIn(0.03f, 0.97f)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(c.surfaceContainerHighest),
            ) {
                Box(Modifier.weight(like).height(4.dp).background(c.primary))
                Box(Modifier.weight(1f - like).height(4.dp).background(c.error))
            }
        }
    }
}

/** Settings row for the opt-in switch, written to match the other settings rows. */
@Composable
fun ReturnDislikeSetting(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Show dislike counts", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "Uses Return YouTube Dislike. Turning this on sends the ID of each video you watch to returnyoutubedislikeapi.com.",
                color = c.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Switch(checked = ReturnDislike.enabled, onCheckedChange = { ReturnDislike.set(it) })
    }
}
