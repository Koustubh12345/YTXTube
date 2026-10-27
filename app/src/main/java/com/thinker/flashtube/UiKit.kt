package com.thinker.flashtube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin







class LobedShape(private val lobes: Int, private val depth: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = 120
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * 2f * PI.toFloat()
            val r = 1f - depth + depth * cos(lobes * t)
            val x = cx + cx * r * cos(t)
            val y = cy + cy * r * sin(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

val CookieShape = LobedShape(lobes = 9, depth = 0.075f)
val SunnyShape = LobedShape(lobes = 8, depth = 0.05f)



private val ScreenHeaderHorizontal = 16.dp


@Composable
fun ScreenHeader(
    title: String,
    brandMark: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = ScreenHeaderHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (brandMark) {
            BrandMark()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontFamily = RoundedHeavy,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}


@Composable
fun BackScreenHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = ScreenHeaderHorizontal, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Spacer(Modifier.width(10.dp))
        content()
    }
}

@Composable
fun BrandMark() {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(c.primary, c.primary.copy(alpha = 0.85f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.FastForward, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun CircleAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(38.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(c.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = c.onSurface, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
        fontFamily = RoundedHeavy,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        letterSpacing = (-0.6).sp,
    )
}








@Composable
fun BottomGestureGuard(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.mandatorySystemGestures)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    )
}





fun Modifier.overlayTouchBlock(): Modifier = composed {
    clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = {},
    )
}








fun Modifier.prefetchOnPress(videoId: String): Modifier = pointerInput(videoId) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        val scrolled = withTimeoutOrNull(60) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                if (!change.pressed) return@withTimeoutOrNull false
                if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        if (scrolled != true) StreamCache.prefetch(videoId)
    }
}


val LocalVideoMenu = staticCompositionLocalOf<(VideoItem) -> Unit> { {} }

@Composable
fun Thumbnail(url: String?, modifier: Modifier = Modifier, radius: Int = 22, videoId: String? = null) {
    val model = remember(url, videoId) {
        val id = videoId ?: url?.let(::ytVideoIdFromThumbUrl)
        if (id != null) sharpenFeedThumbnail(url, id) else url
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
fun DurationPill(seconds: Long, modifier: Modifier = Modifier, small: Boolean = false, isLive: Boolean = false) {
    if (isLive) {
        val fontSize = if (small) 10.sp else 11.sp
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFCC0000))
                .padding(horizontal = if (small) 5.dp else 6.dp, vertical = if (small) 1.5.dp else 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
            Text(
                "LIVE",
                color = Color.White,
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }
    if (seconds <= 0) return
    val fontSize = if (small) 10.5.sp else 11.5.sp
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = if (small) 5.dp else 6.dp, vertical = if (small) 1.dp else 2.dp),
    ) {
        Text(
            formatDuration(seconds * 1000),
            color = Color.White,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Bold,
        )
    }
}





@Composable
fun ChannelAvatar(
    url: String?,
    name: String,
    size: Int = 40,
    selected: Boolean = false,

    ringSlot: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val minPx = remember(size, density) {
        (size.dp.value * density.density * 2f).roundToInt().coerceIn(88, 240)
    }
    val model = remember(url, minPx) { url?.let { sharpenChannelAvatarUrl(normalizeChannelAvatarUrl(it), minPx) } }
    val outer = if (ringSlot) (size + 4).dp else size.dp
    Box(modifier = Modifier.size(outer), contentAlignment = Alignment.Center) {
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.dp, Color.White, CircleShape))
        }
    Box(
        modifier = Modifier.size(size.dp).clip(if (url != null) CircleShape else CookieShape).background(c.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().trimStart('@').take(1).uppercase(),
            color = c.onTertiaryContainer,
            fontWeight = FontWeight.Black,
            fontSize = (size * 0.4f).sp,
        )
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
        }
    }
    }
}


@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val menu = LocalVideoMenu.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .prefetchOnPress(video.id)
            .pressable(pressedScale = 0.98f, onLongClick = { menu(video) }, onClick = onClick)
            .padding(bottom = 16.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Thumbnail(
                video.thumbnail,
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                radius = 0,
                videoId = video.id,
            )
            DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(8.dp), isLive = video.isLive)
            ResumeBar(video.id, Modifier.align(Alignment.BottomStart))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val openChannel = LocalChannelOpener.current
            Box(Modifier.pressable(enabled = video.channelUrl != null) { video.channelUrl?.let(openChannel) }) {
                ChannelAvatar(video.feedAvatar(), video.channel, size = 36)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    metaLine(video.channel, video.views, video.uploaded, video.isLive),
                    color = c.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pressable(onClick = { menu(video) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}


@Composable
fun VideoRow(video: VideoItem, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val menu = LocalVideoMenu.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .prefetchOnPress(video.id)
            .pressable(pressedScale = 0.97f, onLongClick = { menu(video) }, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(140.dp)) {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 16)
            DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(6.dp), small = true, isLive = video.isLive)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val viewsLabel = if (video.isLive) {
                video.views.takeIf { it >= 0 }?.let { "${formatCount(it)} watching" } ?: "Live"
            } else {
                video.views.takeIf { it >= 0 }?.let { "${formatCount(it)} views" }
            }
            Text(
                listOfNotNull(video.channel.ifBlank { null }, viewsLabel).joinToString(" · "),
                color = c.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}


@Composable
fun ResumeBar(videoId: String, modifier: Modifier = Modifier) {
    val progress = ResumeStore.progress(videoId) ?: return
    val c = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.45f))) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(c.primary))
    }
}



@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(SunnyShape).background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = c.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, icon: ImageVector? = null) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .pressable(onClick = onClick)
            .height(44.dp)
            .clip(CircleShape)
            .background(c.primary)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}



fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatCount(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> trimmed(n / 1_000.0) + "K"
    n < 1_000_000_000 -> trimmed(n / 1_000_000.0) + "M"
    else -> trimmed(n / 1_000_000_000.0) + "B"
}


fun formatStatCount(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> statCompact(n / 1_000.0, "K")
    n < 1_000_000_000 -> statCompact(n / 1_000_000.0, "M")
    else -> statCompact(n / 1_000_000_000.0, "B")
}

@Composable
fun ChannelNameWithBadge(
    name: String,
    verified: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    maxLines: Int = 2,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    val c = MaterialTheme.colorScheme
    val badge = if (fontSize >= 22.sp) 18.dp else 16.dp
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            modifier = Modifier.weight(1f, fill = false),
            fontFamily = RoundedHeavy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            letterSpacing = letterSpacing,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (verified) {
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Rounded.Verified, "Verified", Modifier.size(badge), tint = c.onSurfaceVariant)
        }
    }
}

fun channelStatLine(subscribers: Long, videoCount: Long): String = listOfNotNull(
    subscribers.takeIf { it > 0 }?.let { "${formatStatCount(it)} subscribers" },
    videoCount.takeIf { it > 0 }?.let { "${formatStatCount(it)} videos" },
).joinToString(" · ")

private fun trimmed(v: Double): String =
    if (v >= 100 || v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v).removeSuffix(".0")

private fun statCompact(v: Double, suffix: String): String {
    val text = when {
        v >= 100 -> "%.0f".format(v)
        v >= 10 -> "%.1f".format(v)
        else -> "%.2f".format(v)
    }.trimEnd('0').trimEnd('.')
    return text + suffix
}

fun metaLine(channel: String, views: Long, uploaded: String?, isLive: Boolean = false): String =
    if (isLive) {
        listOfNotNull(
            channel.ifBlank { null },
            views.takeIf { it >= 0 }?.let { "${formatCount(it)} watching" } ?: "Live now",
        ).joinToString(" · ")
    } else {
        listOfNotNull(
            channel.ifBlank { null },
            views.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
            uploaded?.ifBlank { null },
        ).joinToString(" · ")
    }

@Composable
fun CommunityCard(post: CommunityPost, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(post.avatar, post.author, size = 36)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(post.author, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                post.published?.let {
                    Text(it, color = c.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        if (post.text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(post.text, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (!post.image.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Thumbnail(post.image, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 12)
        }
        if (post.likes != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ThumbUp, contentDescription = "Likes", tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(post.likes, color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = 0.3f)))
    }
}
