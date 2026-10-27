package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt








object PlaybackSpeed {
    const val MIN = 0.5f
    const val MAX = 3f
    const val STEP = 0.05f

    val LAST_STEP = ((MAX - MIN) / STEP).roundToInt()

    private const val KEY_REMEMBER = "speed_remember"
    private const val KEY_SPEED = "speed_saved"
    private var prefs: SharedPreferences? = null

    var remember by mutableStateOf(false)
        private set

    var saved by mutableFloatStateOf(1f)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        remember = prefs?.getBoolean(KEY_REMEMBER, false) == true
        saved = snap(prefs?.getFloat(KEY_SPEED, 1f) ?: 1f)
    }


    val forNewVideo: Float get() = if (remember) saved else 1f

    fun setRemember(on: Boolean, current: Float) {
        remember = on
        saved = snap(current)
        persist()
    }

    fun save(speed: Float) {
        saved = snap(speed)
        persist()
    }

    private fun persist() {
        prefs?.edit()?.putBoolean(KEY_REMEMBER, remember)?.putFloat(KEY_SPEED, saved)?.apply()
    }

    fun step(speed: Float) = ((speed - MIN) / STEP).roundToInt().coerceIn(0, LAST_STEP)

    fun atStep(step: Int) = ((MIN + step * STEP) * 100f).roundToInt() / 100f
    fun snap(speed: Float) = atStep(step(speed))
    fun isNormal(speed: Float) = abs(speed - 1f) < STEP / 2


    fun label(speed: Float): String =
        String.format(Locale.US, "%.2f", snap(speed)).trimEnd('0').trimEnd('.')
}






object AudioOnly {
    private const val KEY = "audio_only"
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY, false) == true
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY, on)?.apply()
    }

    fun toggle() = set(!enabled)
}
