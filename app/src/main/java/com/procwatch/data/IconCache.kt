package com.procwatch.data

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Icons are decoded lazily, per visible row, and downscaled hard. Loading 300 full-size
 * adaptive icons up front is the fastest way to make a list like this feel broken.
 */
object IconCache {

    private const val PIXELS = 96
    private val cache = LruCache<String, ImageBitmap>(200)

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(PIXELS, PIXELS).asImageBitmap()
                cache.put(packageName, bitmap)
                bitmap
            }.getOrNull()
        }
    }
}
