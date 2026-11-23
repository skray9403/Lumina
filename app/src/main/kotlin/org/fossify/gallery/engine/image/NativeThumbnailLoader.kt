package org.fossify.gallery.engine.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.models.Medium

data class ThumbnailKey(val id: Long, val sizePx: Int, val dateModified: Long)

/**
 * Native, memory-bounded thumbnail loader for Lumina.
 *
 * Employs platform ContentResolver.loadThumbnail(uri, size, signal) directly on Dispatchers.IO,
 * bounded by a ~15% runtime maxMemory LruCache, wired to CancellationSignal on fast scroll fling.
 */
object NativeThumbnailLoader {
    private val maxMemoryBytes = Runtime.getRuntime().maxMemory()
    private val cacheSizeBytes = (maxMemoryBytes * 0.15).toInt().coerceAtLeast(1024 * 1024 * 16)

    private val cache = object : LruCache<ThumbnailKey, Bitmap>(cacheSizeBytes) {
        override fun sizeOf(key: ThumbnailKey, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    suspend fun loadThumbnail(
        contentResolver: ContentResolver,
        medium: Medium,
        targetSizePx: Int = 320
    ): ImageBitmap? {
        val key = ThumbnailKey(medium.id, targetSizePx, medium.dateModifiedMs)
        synchronized(cache) {
            val cached = cache.get(key)
            if (cached != null && !cached.isRecycled) {
                return cached.asImageBitmap()
            }
        }

        return withContext(Dispatchers.IO) {
            val signal = CancellationSignal()
            try {
                val size = Size(targetSizePx, targetSizePx)
                val bitmap = contentResolver.loadThumbnail(medium.uri, size, signal)
                if (bitmap != null) {
                    synchronized(cache) {
                        cache.put(key, bitmap)
                    }
                    bitmap.asImageBitmap()
                } else {
                    null
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    signal.cancel()
                }
                null
            }
        }
    }

    fun getCached(medium: Medium, targetSizePx: Int = 320): ImageBitmap? {
        val key = ThumbnailKey(medium.id, targetSizePx, medium.dateModifiedMs)
        synchronized(cache) {
            val cached = cache.get(key)
            if (cached != null && !cached.isRecycled) {
                return cached.asImageBitmap()
            }
        }
        return null
    }

    fun clearCache() {
        synchronized(cache) {
            cache.evictAll()
        }
    }
}

@Composable
fun rememberThumbnail(medium: Medium, sizePx: Int = 320): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(medium.id, medium.dateModifiedMs, sizePx) {
        mutableStateOf(NativeThumbnailLoader.getCached(medium, sizePx))
    }

    LaunchedEffect(medium.id, medium.dateModifiedMs, sizePx) {
        if (bitmap == null) {
            bitmap = NativeThumbnailLoader.loadThumbnail(context.contentResolver, medium, sizePx)
        }
    }

    return bitmap
}
