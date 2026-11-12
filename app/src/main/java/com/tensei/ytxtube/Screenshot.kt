package com.tensei.ytxtube

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves the video frame that is on screen as a JPEG in Pictures/YTXTube. Needs Android 10 or newer. */
object Screenshot {
    /** Returns a short message for the user. Call from the main thread. */
    suspend fun capture(context: Context, title: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "Screenshots need Android 10 or newer"
        val view = SharedVideoTexture.view?.get() ?: return "Nothing to capture yet"
        val bitmap: Bitmap = runCatching { view.bitmap }.getOrNull() ?: return "Could not capture this frame"
        return withContext(Dispatchers.IO) {
            runCatching {
                val safe = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(40).ifBlank { "video" }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$safe ${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YTXTube")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Saved to Pictures/YTXTube"
            }.getOrDefault("Could not save the screenshot")
        }
    }
}
