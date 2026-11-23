package org.fossify.gallery.ui.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium

/**
 * Lightweight, native MediaStore repository for Lumina (Android API 33+).
 *
 * Directly queries MediaStore.Files for images and videos, streams updates
 * via ContentObserver, and issues native scoped IntentSender requests.
 */
class LuminaMediaRepository(private val context: Context) {
    private val contentResolver: ContentResolver = context.contentResolver

    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.WIDTH,
        MediaStore.Files.FileColumns.HEIGHT,
        MediaStore.Files.FileColumns.ORIENTATION,
        MediaStore.Files.FileColumns.DURATION,
        MediaStore.Files.FileColumns.DATE_TAKEN,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.BUCKET_ID,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        MediaStore.Files.FileColumns.RELATIVE_PATH,
        MediaStore.Files.FileColumns.IS_FAVORITE,
        MediaStore.Files.FileColumns.MEDIA_TYPE
    )

    private val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
    private val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
    )
    private val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC, ${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC, ${MediaStore.Files.FileColumns._ID} DESC"

    val mediaFlow: Flow<List<Medium>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        // Initial emission
        trySend(Unit)

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
        .debounce(250)
        .flowOn(Dispatchers.Default)
        .let { triggerFlow ->
            flow {
                triggerFlow.collect {
                    emit(queryAllMedia())
                }
            }
        }
        .flowOn(Dispatchers.IO)

    suspend fun queryAllMedia(): List<Medium> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Medium>()
        val queryUri = MediaStore.Files.getContentUri("external")

        try {
            contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
                val orientCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.ORIENTATION)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val relPathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val favCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.IS_FAVORITE)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val mediaType = cursor.getInt(typeCol)
                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val contentBaseUri = if (isVideo) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val uri = ContentUris.withAppendedId(contentBaseUri, id)

                    val mimeType = cursor.getString(mimeCol) ?: (if (isVideo) "video/mp4" else "image/jpeg")
                    val isAnimated = mimeType.contains("gif", ignoreCase = true) ||
                            mimeType.contains("webp", ignoreCase = true) ||
                            mimeType.contains("apng", ignoreCase = true)

                    val dateTaken = cursor.getLong(dateTakenCol)
                    val dateModified = cursor.getLong(dateModCol) * 1000L
                    val finalDate = if (dateTaken > 0L) dateTaken else dateModified

                    result.add(
                        Medium(
                            id = id,
                            uri = uri,
                            displayName = cursor.getString(nameCol) ?: "Media_$id",
                            mimeType = mimeType,
                            width = cursor.getInt(widthCol),
                            height = cursor.getInt(heightCol),
                            orientation = cursor.getInt(orientCol),
                            durationMs = if (isVideo) cursor.getLong(durCol) else 0L,
                            dateTakenMs = finalDate,
                            dateModifiedMs = dateModified,
                            sizeBytes = cursor.getLong(sizeCol),
                            bucketId = cursor.getLong(bucketIdCol),
                            bucketName = cursor.getString(bucketNameCol) ?: "Internal",
                            relativePath = cursor.getString(relPathCol) ?: "",
                            isFavorite = cursor.getInt(favCol) == 1,
                            isVideo = isVideo,
                            isAnimated = isAnimated
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        result
    }

    fun extractDirectories(media: List<Medium>): List<Directory> {
        return media.groupBy { it.bucketId }
            .map { (bucketId, items) ->
                val latest = items.maxByOrNull { it.dateTakenMs } ?: items.first()
                Directory(
                    bucketId = bucketId,
                    name = latest.bucketName.ifEmpty { "Pictures" },
                    coverUri = latest.uri,
                    mediaCount = items.size,
                    latestDateMs = latest.dateTakenMs,
                    relativePath = latest.relativePath
                )
            }
            .sortedByDescending { it.latestDateMs }
    }

    fun createTrashRequest(uris: List<Uri>, isTrash: Boolean): IntentSenderRequest {
        val pendingIntent = MediaStore.createTrashRequest(contentResolver, uris, isTrash)
        return IntentSenderRequest.Builder(pendingIntent.intentSender).build()
    }

    fun createDeleteRequest(uris: List<Uri>): IntentSenderRequest {
        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
        return IntentSenderRequest.Builder(pendingIntent.intentSender).build()
    }

    fun createFavoriteRequest(uris: List<Uri>, isFavorite: Boolean): IntentSenderRequest {
        val pendingIntent = MediaStore.createFavoriteRequest(contentResolver, uris, isFavorite)
        return IntentSenderRequest.Builder(pendingIntent.intentSender).build()
    }
}
