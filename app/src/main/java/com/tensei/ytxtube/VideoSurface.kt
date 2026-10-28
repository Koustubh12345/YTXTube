package com.tensei.ytxtube

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player


val LocalVideoSurface = androidx.compose.runtime.staticCompositionLocalOf<@Composable (Modifier) -> Unit> { { m -> VideoSurface(null, m) } }


val NoVideoSurface: @Composable (Modifier) -> Unit = {}











object SharedVideoTexture {
    var texture: SurfaceTexture? = null
        private set
    var surface: Surface? = null
        private set

    var version by mutableIntStateOf(0)
        private set

    fun adopt(view: TextureView, fresh: SurfaceTexture) {
        val kept = texture
        if (kept == null) {
            texture = fresh
            surface = Surface(fresh)
            version++
        } else if (kept !== fresh) {

            runCatching { view.setSurfaceTexture(kept) }
                .onFailure { Log.w("YTXTube", "couldn't re-attach the video texture", it) }
        }
    }
}





@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {

                SharedVideoTexture.texture?.let { runCatching { setSurfaceTexture(it) } }
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) = SharedVideoTexture.adopt(this@apply, st)
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = st !== SharedVideoTexture.texture
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = AvSync.onFrameDrawn()
                }
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {

                        val kept = SharedVideoTexture.texture ?: return
                        if (surfaceTexture !== kept) runCatching { setSurfaceTexture(kept) }
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        },
    )
    val version = SharedVideoTexture.version
    LaunchedEffect(player, version) {
        val surface = SharedVideoTexture.surface ?: return@LaunchedEffect
        player?.setVideoSurface(surface)
    }
}
