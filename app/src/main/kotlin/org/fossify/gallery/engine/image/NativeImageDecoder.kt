package org.fossify.gallery.engine.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DecodedImageResult {
    data class Static(val bitmap: ImageBitmap, val width: Int, val height: Int) : DecodedImageResult
    data class Animated(val drawable: AnimatedImageDrawable, val width: Int, val height: Int) : DecodedImageResult
}

/**
 * Native, hardware-accelerated image decoder using Android's ImageDecoder API (API 28+).
 * Supports Display P3, HEIF, AVIF, WebP, and animated formats without any third-party libraries.
 */
object NativeImageDecoder {

    suspend fun decodeImage(
        contentResolver: ContentResolver,
        uri: Uri,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): DecodedImageResult? = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(contentResolver, uri)

            // First check if this source produces an animated drawable
            val testDrawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_DEFAULT
                if (targetWidth > 0 && targetHeight > 0) {
                    decoder.setTargetSize(targetWidth, targetHeight)
                }
            }

            if (testDrawable is AnimatedImageDrawable) {
                return@withContext DecodedImageResult.Animated(
                    drawable = testDrawable,
                    width = testDrawable.intrinsicWidth,
                    height = testDrawable.intrinsicHeight
                )
            }

            // Decode static bitmap with hardware allocator
            val hardwareBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
                decoder.isMutableRequired = false
                if (targetWidth > 0 && targetHeight > 0) {
                    decoder.setTargetSize(targetWidth, targetHeight)
                }
            }

            DecodedImageResult.Static(
                bitmap = hardwareBitmap.asImageBitmap(),
                width = hardwareBitmap.width,
                height = hardwareBitmap.height
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Native Compose Canvas renderer for AnimatedImageDrawable (GIF, animated WebP, animated HEIF).
 * Renders directly into Compose Canvas without any view interop wrapper.
 */
@Composable
fun NativeAnimatedCanvas(
    drawable: AnimatedImageDrawable,
    modifier: Modifier = Modifier
) {
    var frameTick by remember { mutableIntStateOf(0) }

    DisposableEffect(drawable) {
        val callback = object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) {
                // Loop endlessly
                (drawable as? AnimatedImageDrawable)?.start()
            }
        }
        drawable.registerAnimationCallback(callback)
        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        drawable.start()

        onDispose {
            drawable.unregisterAnimationCallback(callback)
            drawable.stop()
        }
    }

    // Periodic invalidation pulse to sync frames with the running AnimatedImageDrawable
    LaunchedEffect(drawable) {
        while (drawable.isRunning) {
            frameTick++
            kotlinx.coroutines.delay(16) // ~60fps frame tick
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read frameTick to trigger redraw
        @Suppress("UNUSED_VARIABLE")
        val currentTick = frameTick

        val canvasWidth = size.width
        val canvasHeight = size.height
        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        if (drawableWidth > 0 && drawableHeight > 0) {
            val scale = minOf(canvasWidth / drawableWidth, canvasHeight / drawableHeight)
            val scaledWidth = drawableWidth * scale
            val scaledHeight = drawableHeight * scale
            val left = ((canvasWidth - scaledWidth) / 2f).toInt()
            val top = ((canvasHeight - scaledHeight) / 2f).toInt()
            val right = (left + scaledWidth).toInt()
            val bottom = (top + scaledHeight).toInt()

            drawable.setBounds(left, top, right, bottom)
            drawIntoCanvas { canvas ->
                drawable.draw(canvas.nativeCanvas)
            }
        }
    }
}
