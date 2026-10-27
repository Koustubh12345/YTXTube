package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.C
import androidx.media3.common.Player






object ResumeStore {
    private const val MAX_ENTRIES = 500

    private const val EDGE_MS = 10_000L

    private var prefs: SharedPreferences? = null

    val positions = mutableStateMapOf<String, Pair<Long, Long>>()

    fun init(context: Context) {
        val p = context.getSharedPreferences("resume", Context.MODE_PRIVATE).also { prefs = it }
        p.all.forEach { (id, value) ->
            val parts = (value as? String)?.split(',') ?: return@forEach
            val pos = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
            val dur = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            positions[id] = pos to dur
        }
    }


    fun startPosition(id: String): Long = positions[id]?.first ?: 0L


    fun progress(id: String): Float? {
        val (pos, dur) = positions[id] ?: return null
        return if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else null
    }

    fun save(id: String, positionMs: Long, durationMs: Long) {
        if (id.isBlank() || durationMs <= 0 || durationMs == C.TIME_UNSET) return
        if (positionMs < EDGE_MS || positionMs > durationMs - EDGE_MS) {

            if (positions.remove(id) != null) prefs?.edit()?.remove(id)?.apply()
            return
        }
        positions[id] = positionMs to durationMs
        prefs?.edit()?.putString(id, "$positionMs,$durationMs")?.apply()
        if (positions.size > MAX_ENTRIES) trim()
    }

    private fun trim() {

        val extra = positions.keys.take(positions.size - MAX_ENTRIES)
        val edit = prefs?.edit()
        extra.forEach { positions.remove(it); edit?.remove(it) }
        edit?.apply()
    }
}





class ResumeSaver(private val player: Player) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private var lastId: String? = null
    private val tick = object : Runnable {
        override fun run() {
            saveNow()
            if (player.isPlaying) handler.postDelayed(this, 5_000)
        }
    }

    fun saveNow() {
        val id = player.currentMediaItem?.mediaId ?: return
        lastId = id
        ResumeStore.save(id, player.currentPosition, player.duration)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(tick)
        if (isPlaying) handler.postDelayed(tick, 5_000) else saveNow()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {

        val oldId = oldPosition.mediaItem?.mediaId
        if (oldId != null && oldId != newPosition.mediaItem?.mediaId) {
            val duration = player.duration.takeIf { lastId == oldId } ?: 0L
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) ResumeStore.save(oldId, Long.MAX_VALUE, Long.MAX_VALUE)
            else if (duration > 0) ResumeStore.save(oldId, oldPosition.positionMs, duration)
        } else saveNow()
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) player.currentMediaItem?.mediaId?.let { ResumeStore.save(it, Long.MAX_VALUE, Long.MAX_VALUE) }
    }
}
