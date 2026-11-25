package org.fossify.gallery.engine.image

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

object ImageLuminance {
    /**
     * Evaluates whether a software thumbnail is visually bright along the top/bottom chrome bands
     * and center. Always skips Config.HARDWARE bitmaps safely without allocating copies.
     */
    fun isBrightThumbnail(thumbnail: ImageBitmap?): Boolean {
        val bmp = thumbnail?.asAndroidBitmap() ?: return false
        if (bmp.isRecycled || bmp.config == Bitmap.Config.HARDWARE) return false
        val w = bmp.width
        val h = bmp.height
        if (w < 4 || h < 4) return false

        val xFracs = floatArrayOf(0.15f, 0.38f, 0.62f, 0.85f)
        val topLuma = sampleRow(bmp, w, (h * 0.08f).toInt().coerceIn(0, h - 1), xFracs)
        val midLuma = sampleRow(bmp, w, (h * 0.50f).toInt().coerceIn(0, h - 1), xFracs)
        val botLuma = sampleRow(bmp, w, (h * 0.92f).toInt().coerceIn(0, h - 1), xFracs)

        val weighted = topLuma * 0.35f + botLuma * 0.35f + midLuma * 0.30f
        return weighted >= 0.62f
    }

    private fun sampleRow(bmp: Bitmap, width: Int, y: Int, xFracs: FloatArray): Float {
        var sum = 0f
        for (f in xFracs) {
            val x = (width * f).toInt().coerceIn(0, width - 1)
            val px = bmp.getPixel(x, y)
            val r = Color.red(px) / 255f
            val g = Color.green(px) / 255f
            val b = Color.blue(px) / 255f
            sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
        return sum / xFracs.size
    }
}
