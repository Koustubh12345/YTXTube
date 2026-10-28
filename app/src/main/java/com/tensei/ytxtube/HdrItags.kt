package com.tensei.ytxtube

import android.util.Log
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem

/**
 * YouTube serves HDR as 10-bit VP9 profile 2 (itags 337/336/335/334) and 10-bit AV1
 * (701/700/699/698). NewPipe's extractor drops any itag missing from its static
 * [ItagItem] table, so those renditions never reached this app even though the iOS
 * player response contains them. Adding the metadata at runtime makes the extractor
 * parse them like any other stream, including URL de-throttling and DASH ranges.
 */
object HdrItags {

    private const val TAG = "YTXTube"

    private data class Hdr(val id: Int, val height: Int, val fps: Int, val codec: String, val bitrate: Int)

    private val ENTRIES = listOf(
        Hdr(337, 2160, 60, "vp09.02.51.10", 24_850_000),
        Hdr(336, 1440, 60, "vp09.02.51.10", 12_400_000),
        Hdr(335, 1080, 60, "vp09.02.51.10", 6_900_000),
        Hdr(334, 720, 60, "vp09.02.51.10", 3_400_000),
        Hdr(701, 2160, 60, "av01.0.13M.10", 20_900_000),
        Hdr(700, 1440, 60, "av01.0.09M.10", 10_600_000),
        Hdr(699, 1080, 60, "av01.0.08M.10", 5_800_000),
        Hdr(698, 720, 60, "av01.0.05M.10", 2_900_000),
    )

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
            runCatching {
                val field = ItagItem::class.java.getDeclaredField("ITAG_LIST")
                field.isAccessible = true
                val current = field.get(null) as? Array<ItagItem> ?: return@runCatching
                if (current.any { item -> ENTRIES.any { it.id == item.id } }) return@runCatching
                val extra = ENTRIES.map { entry ->
                    ItagItem(
                        entry.id,
                        ItagItem.ItagType.VIDEO_ONLY,
                        MediaFormat.WEBM,
                        "${entry.height}p${entry.fps}",
                    ).apply {
                        codec = entry.codec
                        width = entry.height * 16 / 9
                        height = entry.height
                        fps = entry.fps
                        bitrate = entry.bitrate
                        quality = "hd${entry.height}"
                    }
                }
                field.set(null, current + extra)
                Log.i(TAG, "HDR itags registered: ${extra.joinToString { it.id.toString() }}")
            }.onFailure { Log.w(TAG, "couldn't register HDR itags", it) }
        }
    }
}
