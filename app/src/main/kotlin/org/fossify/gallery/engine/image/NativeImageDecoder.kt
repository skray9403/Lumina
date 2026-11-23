package org.fossify.gallery.engine.image

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface DecodedImageResult {
    data class Static(
        val bitmap: ImageBitmap,
        val displayWidth: Int,
        val displayHeight: Int,
        val fullWidth: Int,
        val fullHeight: Int
    ) : DecodedImageResult

    data class Animated(
        val drawable: AnimatedImageDrawable,
        val width: Int,
        val height: Int
    ) : DecodedImageResult
}

/**
 * Single-pass native hardware ImageDecoder pipeline (API 33+).
 * Downsamples static bitmaps to maxDimensionPx to stay safely within GPU texture limits
 * while preserving full natural dimensions for BitmapRegionDecoder deep zoom.
 */
object NativeImageDecoder {

    suspend fun decodeImage(
        contentResolver: ContentResolver,
        uri: Uri,
        maxDimensionPx: Int = 2048
    ): DecodedImageResult? = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(contentResolver, uri)
            var fullWidth = 0
            var fullHeight = 0

            val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                fullWidth = info.size.width
                fullHeight = info.size.height

                if (info.isAnimated) {
                    decoder.allocator = ImageDecoder.ALLOCATOR_DEFAULT
                } else {
                    decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
                    decoder.isMutableRequired = false

                    val maxSrc = max(info.size.width, info.size.height)
                    if (maxDimensionPx > 0 && maxSrc > maxDimensionPx) {
                        val scale = maxDimensionPx.toFloat() / maxSrc.toFloat()
                        val targetW = (info.size.width * scale).roundToInt().coerceAtLeast(1)
                        val targetH = (info.size.height * scale).roundToInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                }
            }

            when (drawable) {
                is AnimatedImageDrawable -> DecodedImageResult.Animated(
                    drawable = drawable,
                    width = drawable.intrinsicWidth,
                    height = drawable.intrinsicHeight
                )
                is BitmapDrawable -> {
                    val bmp = drawable.bitmap
                    DecodedImageResult.Static(
                        bitmap = bmp.asImageBitmap(),
                        displayWidth = bmp.width,
                        displayHeight = bmp.height,
                        fullWidth = if (fullWidth > 0) fullWidth else bmp.width,
                        fullHeight = if (fullHeight > 0) fullHeight else bmp.height
                    )
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Vsync-locked Compose Canvas renderer for AnimatedImageDrawable (GIF/WebP/HEIF).
 */
@Composable
fun NativeAnimatedCanvas(
    drawable: AnimatedImageDrawable,
    modifier: Modifier = Modifier
) {
    var frameNanos by remember(drawable) { mutableLongStateOf(0L) }

    LaunchedEffect(drawable) {
        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        drawable.start()
        try {
            while (isActive) {
                withFrameNanos { nanos -> frameNanos = nanos }
            }
        } finally {
            drawable.stop()
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Subscribe draw phase only to vsync tick
        if (frameNanos == Long.MIN_VALUE) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height
        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        if (drawableWidth > 0f && drawableHeight > 0f) {
            val scale = minOf(canvasWidth / drawableWidth, canvasHeight / drawableHeight)
            val scaledW = drawableWidth * scale
            val scaledH = drawableHeight * scale
            val left = ((canvasWidth - scaledW) / 2f).toInt()
            val top = ((canvasHeight - scaledH) / 2f).toInt()
            drawable.setBounds(left, top, (left + scaledW).toInt(), (top + scaledH).toInt())
            drawIntoCanvas { canvas ->
                drawable.draw(canvas.nativeCanvas)
            }
        }
    }
}
