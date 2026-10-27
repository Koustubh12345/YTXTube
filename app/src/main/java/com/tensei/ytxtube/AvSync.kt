package com.tensei.ytxtube

import android.content.Context
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.Choreographer
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import kotlin.math.abs

















object AvSync {

    @Volatile
    var offsetUs = 0L
        private set


    @Volatile
    private var releaseNs = 0L


    fun onFrameReleased(releaseTimeNs: Long) {


        if (releaseTimeNs - System.nanoTime() > MIN_SCHEDULED_NS) releaseNs = releaseTimeNs
    }



    private val samples = LongArray(WINDOW)
    private var sampleCount = 0
    private var nextSample = 0
    private var lastSampledNs = 0L

    private var watching = false
    private var lastDrawnNs = 0L
    private var frameVsyncNs = 0L
    private var framePresentNs = 0L
    private var framePeriodNs = 0L
    private var usedVsyncNs = 0L

    @RequiresApi(33)
    private val vsync = object : Choreographer.VsyncCallback {
        override fun onVsync(data: Choreographer.FrameData) {
            val timeline = data.preferredFrameTimeline
            frameVsyncNs = data.frameTimeNanos
            framePresentNs = timeline.expectedPresentationTimeNanos
            framePeriodNs = data.frameTimelines.let {
                if (it.size >= 2) it[1].expectedPresentationTimeNanos - it[0].expectedPresentationTimeNanos else 0L
            }.takeIf { it > 0 } ?: DEFAULT_PERIOD_NS

            if (System.nanoTime() - lastDrawnNs < IDLE_NS) Choreographer.getInstance().postVsyncCallback(this)
            else watching = false
        }
    }


    fun onFrameDrawn() {
        if (Build.VERSION.SDK_INT < 33) return
        lastDrawnNs = System.nanoTime()
        if (!watching) {

            watching = true
            Choreographer.getInstance().postVsyncCallback(vsync)
            return
        }
        if (frameVsyncNs == usedVsyncNs) return
        usedVsyncNs = frameVsyncNs
        val released = releaseNs
        if (released == 0L || released == lastSampledNs) return
        lastSampledNs = released

        val intendedNs = released + framePeriodNs * 8 / 10
        val gapUs = (framePresentNs - intendedNs) / 1000
        if (gapUs !in -MAX_GAP_US..MAX_GAP_US) return
        samples[nextSample] = gapUs
        nextSample = (nextSample + 1) % WINDOW
        if (sampleCount < WINDOW) sampleCount++
        if (sampleCount < MIN_SAMPLES) return
        val median = samples.copyOf(sampleCount).apply { sort() }[sampleCount / 2]
        val next = median.coerceIn(-MAX_OFFSET_US, MAX_OFFSET_US)
        if (abs(next - offsetUs) >= HYSTERESIS_US) {
            Log.i(
                "FlashTube",
                "AV sync: frames show ${abs(median) / 1000} ms ${if (median < 0) "early" else "late"} " +
                    "(${1_000_000_000L / framePeriodNs} Hz); video clock shifted ${next / 1000} ms",
            )
            offsetUs = next
        }
    }

    private const val WINDOW = 61
    private const val MIN_SAMPLES = 15
    private const val MIN_SCHEDULED_NS = 1_000_000L
    private const val MAX_GAP_US = 150_000L
    private const val MAX_OFFSET_US = 100_000L
    private const val HYSTERESIS_US = 2_000L
    private const val IDLE_NS = 1_000_000_000L
    private const val DEFAULT_PERIOD_NS = 16_666_667L
}


@OptIn(UnstableApi::class)
class SyncedRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {

        out += SyncedVideoRenderer(
            MediaCodecVideoRenderer.Builder(context)
                .setCodecAdapterFactory(codecAdapterFactory)
                .setMediaCodecSelector(mediaCodecSelector)
                .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                .setEnableDecoderFallback(enableDecoderFallback)
                .setEventHandler(eventHandler)
                .setEventListener(eventListener)
                .setMaxDroppedFramesToNotify(50),
        )
    }
}


@OptIn(UnstableApi::class)
private class SyncedVideoRenderer(builder: Builder) : MediaCodecVideoRenderer(builder) {
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) =
        super.render(positionUs + AvSync.offsetUs, elapsedRealtimeUs)
}
