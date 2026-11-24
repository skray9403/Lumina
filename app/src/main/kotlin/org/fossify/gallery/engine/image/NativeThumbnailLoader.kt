package org.fossify.gallery.engine.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.os.OperationCanceledException
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.fossify.gallery.models.Medium
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

data class ThumbnailKey(val id: Long, val sizePx: Int, val dateModified: Long)

private data class CachedThumbnail(
    val imageBitmap: ImageBitmap,
    val byteCount: Int
)

object NativeThumbnailLoader {
    const val DEFAULT_THUMB_PX = 360

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(6))
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    private val inFlightPrefetch = ConcurrentHashMap.newKeySet<ThumbnailKey>()

    private val maxMemoryBytes = Runtime.getRuntime().maxMemory()
    private val cacheSizeBytes = (maxMemoryBytes * 0.15).toInt().coerceAtLeast(16 * 1024 * 1024)

    private val cache = object : LruCache<ThumbnailKey, CachedThumbnail>(cacheSizeBytes) {
        override fun sizeOf(key: ThumbnailKey, value: CachedThumbnail): Int = value.byteCount
    }

    fun getCached(medium: Medium, targetSizePx: Int = DEFAULT_THUMB_PX): ImageBitmap? {
        val key = ThumbnailKey(medium.id, targetSizePx, medium.dateModifiedMs)
        return cache.get(key)?.imageBitmap
    }

    suspend fun loadThumbnail(
        contentResolver: ContentResolver,
        medium: Medium,
        targetSizePx: Int = DEFAULT_THUMB_PX
    ): ImageBitmap? {
        val key = ThumbnailKey(medium.id, targetSizePx, medium.dateModifiedMs)
        cache.get(key)?.let { return it.imageBitmap }

        return suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation {
                signal.cancel()
            }

            ioScope.launch {
                try {
                    val bitmap: Bitmap? = contentResolver.loadThumbnail(
                        medium.uri,
                        Size(targetSizePx, targetSizePx),
                        signal
                    )
                    if (bitmap != null) {
                        val imageBitmap = bitmap.asImageBitmap()
                        cache.put(key, CachedThumbnail(imageBitmap, bitmap.allocationByteCount))
                        if (cont.isActive) cont.resume(imageBitmap)
                    } else {
                        if (cont.isActive) cont.resume(null)
                    }
                } catch (_: OperationCanceledException) {
                    if (cont.isActive) cont.resume(null)
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    /**
     * Warms upcoming rows ahead of the viewport on a dedicated 2-thread IO pool
     * without competing against currently visible tile loads.
     */
    fun prefetch(
        contentResolver: ContentResolver,
        items: List<Medium>,
        targetSizePx: Int = DEFAULT_THUMB_PX
    ) {
        for (medium in items) {
            val key = ThumbnailKey(medium.id, targetSizePx, medium.dateModifiedMs)
            if (cache.get(key) != null) continue
            if (!inFlightPrefetch.add(key)) continue

            prefetchScope.launch {
                try {
                    if (cache.get(key) == null) {
                        val bitmap = contentResolver.loadThumbnail(
                            medium.uri,
                            Size(targetSizePx, targetSizePx),
                            null
                        )
                        if (bitmap != null) {
                            cache.put(key, CachedThumbnail(bitmap.asImageBitmap(), bitmap.allocationByteCount))
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    inFlightPrefetch.remove(key)
                }
            }
        }
    }

    fun clearCache() {
        cache.evictAll()
    }
}

@Composable
fun rememberThumbnail(
    medium: Medium,
    sizePx: Int = NativeThumbnailLoader.DEFAULT_THUMB_PX
): ImageBitmap? {
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
