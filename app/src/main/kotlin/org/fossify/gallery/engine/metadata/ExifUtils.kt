package org.fossify.gallery.engine.metadata

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.models.Medium
import java.io.InputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class MediaMetadata(
    val title: String,
    val formattedDate: String,
    val formattedSize: String,
    val resolution: String,
    val storagePath: String,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val flash: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Video specific
    val duration: String? = null,
    val videoCodec: String? = null,
    val bitrate: String? = null,
    val frameRate: String? = null
)

/**
 * Off-thread native metadata extraction engine for images (ExifInterface) and videos (MediaMetadataRetriever).
 */
object NativeMetadataEngine {

    suspend fun extractMetadata(context: Context, medium: Medium): MediaMetadata = withContext(Dispatchers.IO) {
        val dateStr = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
            .format(Date(medium.dateTakenMs))
        val sizeStr = formatFileSize(medium.sizeBytes)
        val resStr = "${medium.width} × ${medium.height}"

        if (medium.isVideo) {
            extractVideoMetadata(context, medium, dateStr, sizeStr, resStr)
        } else {
            extractImageMetadata(context, medium, dateStr, sizeStr, resStr)
        }
    }

    private fun extractImageMetadata(
        context: Context,
        medium: Medium,
        dateStr: String,
        sizeStr: String,
        resStr: String
    ): MediaMetadata {
        val hasMediaLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_MEDIA_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val queryUri = if (hasMediaLocation) {
            try {
                MediaStore.setRequireOriginal(medium.uri)
            } catch (_: Exception) {
                medium.uri
            }
        } else {
            medium.uri
        }

        var make: String? = null
        var model: String? = null
        var lens: String? = null
        var focal: String? = null
        var fNumber: String? = null
        var exposure: String? = null
        var isoVal: String? = null
        var flashStr: String? = null
        var lat: Double? = null
        var lng: Double? = null

        try {
            context.contentResolver.openInputStream(queryUri)?.use { stream ->
                val exif = ExifInterface(stream)
                make = exif.getAttribute(ExifInterface.TAG_MAKE)
                model = exif.getAttribute(ExifInterface.TAG_MODEL)
                lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)

                val focalLenDouble = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                if (focalLenDouble > 0) {
                    focal = "${DecimalFormat("#.#").format(focalLenDouble)} mm"
                }

                val fNumDouble = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                if (fNumDouble > 0) {
                    fNumber = "f/${DecimalFormat("#.#").format(fNumDouble)}"
                }

                val expTimeDouble = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                if (expTimeDouble > 0) {
                    exposure = if (expTimeDouble < 1.0) {
                        "1/${(1.0 / expTimeDouble).toInt()} s"
                    } else {
                        "${DecimalFormat("#.#").format(expTimeDouble)} s"
                    }
                }

                val isoInt = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                if (isoInt > 0) {
                    isoVal = "ISO $isoInt"
                }

                val flashInt = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
                if (flashInt != -1) {
                    flashStr = if ((flashInt and 1) != 0) "Flash fired" else "No flash"
                }

                val latLong = exif.latLong
                if (latLong != null && latLong.size >= 2) {
                    lat = latLong[0]
                    lng = latLong[1]
                }
            }
        } catch (_: Exception) {
        }

        return MediaMetadata(
            title = medium.displayName,
            formattedDate = dateStr,
            formattedSize = sizeStr,
            resolution = resStr,
            storagePath = medium.relativePath.ifEmpty { medium.uri.toString() },
            cameraMake = make,
            cameraModel = model,
            lensModel = lens,
            focalLength = focal,
            aperture = fNumber,
            exposureTime = exposure,
            iso = isoVal,
            flash = flashStr,
            latitude = lat,
            longitude = lng
        )
    }

    private fun extractVideoMetadata(
        context: Context,
        medium: Medium,
        dateStr: String,
        sizeStr: String,
        resStr: String
    ): MediaMetadata {
        var durationFormatted: String? = null
        var videoCodec: String? = null
        var bitrate: String? = null
        var frameRate: String? = null
        var lat: Double? = null
        var lng: Double? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, medium.uri)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: medium.durationMs
            val totalSec = durMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            durationFormatted = String.format("%02d:%02d", min, sec)

            videoCodec = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            if (br != null && br > 0) {
                bitrate = "${br / 1000} kbps"
            }

            frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let { "$it fps" }

            val locationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            if (locationStr != null) {
                // ISO-6709 string: e.g. +37.7749-122.4194/
                val coords = parseIso6709(locationStr)
                if (coords != null) {
                    lat = coords.first
                    lng = coords.second
                }
            }
        } catch (_: Exception) {
        } finally {
            retriever.release()
        }

        return MediaMetadata(
            title = medium.displayName,
            formattedDate = dateStr,
            formattedSize = sizeStr,
            resolution = resStr,
            storagePath = medium.relativePath.ifEmpty { medium.uri.toString() },
            duration = durationFormatted,
            videoCodec = videoCodec,
            bitrate = bitrate,
            frameRate = frameRate,
            latitude = lat,
            longitude = lng
        )
    }

    private fun parseIso6709(loc: String): Pair<Double, Double>? {
        return try {
            val clean = loc.trimEnd('/')
            val signIndex = clean.lastIndexOfAny(charArrayOf('+', '-'))
            if (signIndex > 0) {
                val lat = clean.substring(0, signIndex).toDouble()
                val lng = clean.substring(signIndex).toDouble()
                Pair(lat, lng)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
