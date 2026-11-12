package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The playback service and the UI live in the same process. This gives small playback tools
 * (sleep timer, skip silence) a way to reach the real ExoPlayer, which the UI otherwise only
 * sees through a MediaController. Always touch the player from the main thread.
 */
object PlayerBridge {
    /**
     * Set by PlaybackService on the main thread. Replacing the player also moves the network
     * recovery listener (see PlaybackRecovery), the stats listener (see PlayerStats) and the audio
     * effects (see AudioEffects) so they always follow the live player.
     */
    @Volatile
    var exo: ExoPlayer? = null
        set(value) {
            val old = field
            field = value
            if (old !== value) {
                PlaybackRecovery.detach(old)
                PlayerStats.detach(old)
                PlaybackRecovery.attach(value)
                PlayerStats.attach(value)
                // Audio effects hook onto the player's audio session; attach(null) releases them.
                AudioEffects.attach(value)
            }
        }
}

/** Trims silent stretches out of audio, handy for podcasts, lectures and talk-heavy videos. */
object SkipSilence {
    private const val PREFS = "playback_tools"
    private const val KEY = "skip_silence"

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean(KEY, false)
    }

    /** Flips the setting, applies it to the live player and returns the new value. */
    fun toggle(): Boolean {
        enabled = !enabled
        prefs?.edit()?.putBoolean(KEY, enabled)?.apply()
        applyTo(PlayerBridge.exo)
        return enabled
    }

    fun applyTo(player: ExoPlayer?) {
        player?.setSkipSilenceEnabled(enabled)
    }
}

/** Pauses playback after a chosen number of minutes. Tap the setting again to step to the next value. */
object SleepTimer {
    private val steps = listOf(0, 15, 30, 45, 60)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    /** Minutes the timer was set to, or 0 when it is off. */
    var minutes by mutableIntStateOf(0)
        private set

    /** Steps to the next preset (Off, 15, 30, 45, 60, then Off again) and returns it. */
    fun cycle(): Int {
        val next = steps[(steps.indexOf(minutes) + 1) % steps.size]
        set(next)
        return next
    }

    fun set(min: Int) {
        job?.cancel()
        minutes = min
        if (min <= 0) return
        job = scope.launch {
            delay(min * 60_000L)
            PlayerBridge.exo?.pause()
            minutes = 0
        }
    }
}
