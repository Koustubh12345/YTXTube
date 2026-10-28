package com.tensei.ytxtube

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Android TV / Google TV boxes are always landscape, have no touchscreen and are driven by a
 * D-pad remote. Everything that assumes a phone (forced portrait, touch-only affordances) has to
 * branch on this.
 */
object TvMode {
    var isTv: Boolean = false
        private set

    fun init(context: Context) {
        val pm = context.packageManager
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        isTv = uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("android.hardware.type.television")
    }
}
