package com.tensei.ytxtube

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.WindowManager
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

/**
 * Best-effort HDR gate. YouTube exposes HDR renditions as 10-bit VP9 profile 2
 * (and increasingly 10-bit AV1). We only prefer those when the display and a
 * decoder both advertise HDR support for that exact codec family, so an HDR TV
 * with no VP9.2 decoder still gets a playable SDR stream instead of a black frame.
 */
object HdrSupport {

    @Volatile
    private var displayHdr = false

    @Volatile
    private var vp9Hdr = false

    @Volatile
    private var av1Hdr = false

    @Volatile
    private var hevcHdr = false

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        runCatching {
            val display = context.getSystemService(WindowManager::class.java)?.defaultDisplay
            displayHdr = display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
            if (displayHdr) detectHdrDecoders()
        }
    }

    val isSupported: Boolean get() = displayHdr && (vp9Hdr || av1Hdr || hevcHdr)

    /** True only for an HDR rendition this device can actually decode and display. */
    fun isHdr(stream: VideoStream): Boolean {
        if (!displayHdr) return false
        val codec = stream.codec?.lowercase(Locale.US).orEmpty()
        return when {
            codec.contains("vp9.2") || codec.contains("vp09.02") -> vp9Hdr
            codec.startsWith("av01") -> av1Hdr && av1BitDepth(codec) >= 10
            codec.startsWith("hev1") || codec.startsWith("hvc1") -> hevcHdr
            else -> false
        }
    }

    private fun av1BitDepth(codec: String): Int =
        codec.split('.').getOrNull(3)?.take(2)?.toIntOrNull() ?: 0

    private fun detectHdrDecoders() {
        val codecs = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        }.getOrDefault(emptyArray())
        for (codec in codecs) {
            if (codec.isEncoder) continue
            for (type in codec.supportedTypes) {
                val profiles = runCatching {
                    codec.getCapabilitiesForType(type).profileLevels.map { it.profile }.toSet()
                }.getOrDefault(emptySet())
                when (type) {
                    "video/x-vnd.on2.vp9" -> if (profiles.any { it in VP9_HDR_PROFILES }) vp9Hdr = true
                    "video/av01" -> if (profiles.any { it in AV1_HDR_PROFILES }) av1Hdr = true
                    "video/hevc" -> if (profiles.any { it in HEVC_HDR_PROFILES }) hevcHdr = true
                }
            }
        }
    }

    private val VP9_HDR_PROFILES = setOf(
        MediaCodecInfo.CodecProfileLevel.VP9Profile2,
        MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR,
        MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus,
    )

    private val AV1_HDR_PROFILES = buildSet {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10)
            add(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10)
            add(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus)
        } else {
            add(2) // AV1ProfileMain10 value
        }
    }

    private val HEVC_HDR_PROFILES = setOf(
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
    )
}
