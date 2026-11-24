package org.fossify.gallery.ui.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium

@OptIn(FlowPreview::class)
class LuminaMediaRepository(context: Context) {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

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
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.MediaColumns.IS_FAVORITE
    )

    private val observerTicks: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        try {
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
        } catch (_: Exception) {
        }

        awaitClose {
            try {
                contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {
            }
        }
    }

    /** Immediate initial query (0ms delay), 250ms debounce strictly on subsequent MediaStore changes. */
    val mediaFlow: Flow<List<Medium>> = flow {
        emit(queryAllMedia())
        emitAll(
            observerTicks
                .debounce(250)
                .map { queryAllMedia() }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun queryAllMedia(): List<Medium> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<Medium>()
        val queryUri = MediaStore.Files.getContentUri("external")
        val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC, ${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
                val orientCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.ORIENTATION)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val favCol = cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val mediaType = cursor.getInt(typeCol)
                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val contentBase = if (isVideo) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val uri = Uri.withAppendedPath(contentBase, id.toString())

                    val name = cursor.getString(nameCol) ?: ""
                    val mime = cursor.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*"
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val orientation = cursor.getInt(orientCol)
                    val duration = cursor.getLong(durationCol)
                    val dateTaken = cursor.getLong(takenCol)
                    val dateModified = cursor.getLong(modCol) * 1000L
                    val size = cursor.getLong(sizeCol)
                    val bucketId = cursor.getLong(bucketIdCol)
                    val bucketName = cursor.getString(bucketNameCol) ?: "Internal Storage"
                    val relativePath = cursor.getString(pathCol) ?: ""
                    val isFavorite = if (favCol != -1) cursor.getInt(favCol) == 1 else false
                    val isAnimated = mime.contains("gif", ignoreCase = true) ||
                        mime.contains("webp", ignoreCase = true) ||
                        mime.contains("apng", ignoreCase = true)

                    mediaList.add(
                        Medium(
                            id = id,
                            uri = uri,
                            displayName = name,
                            mimeType = mime,
                            width = width,
                            height = height,
                            orientation = orientation,
                            durationMs = duration,
                            dateTakenMs = if (dateTaken > 0) dateTaken else dateModified,
                            dateModifiedMs = dateModified,
                            sizeBytes = size,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            relativePath = relativePath,
                            isFavorite = isFavorite,
                            isVideo = isVideo,
                            isAnimated = isAnimated
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        mediaList
    }

    fun extractDirectories(media: List<Medium>): List<Directory> {
        val grouped = media.groupBy { it.bucketId }
        return grouped.mapNotNull { (bucketId, items) ->
            val first = items.firstOrNull() ?: return@mapNotNull null
            Directory(
                bucketId = bucketId,
                name = first.bucketName,
                coverUri = first.uri,
                mediaCount = items.size,
                latestDateMs = items.maxOfOrNull { it.dateTakenMs } ?: 0L,
                relativePath = first.relativePath,
                coverMediumId = first.id,
                coverDateModifiedMs = first.dateModifiedMs
            )
        }.sortedByDescending { it.latestDateMs }
    }

    suspend fun copyMediaToAlbum(
        items: List<Medium>,
        albumName: String,
        targetRelativePath: String? = null
    ): Int = withContext(Dispatchers.IO) {
        val sanitizedAlbum = albumName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (sanitizedAlbum.isEmpty()) return@withContext 0

        var copiedCount = 0
        for (medium in items) {
            try {
                val collection = if (medium.isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val defaultRoot = if (medium.isVideo) {
                    Environment.DIRECTORY_MOVIES
                } else {
                    Environment.DIRECTORY_PICTURES
                }
                val resolvedPath = targetRelativePath?.takeIf { it.isNotBlank() }
                    ?: "$defaultRoot/$sanitizedAlbum/"

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, medium.displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, medium.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, resolvedPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val destUri = contentResolver.insert(collection, values) ?: continue
                var success = false
                try {
                    contentResolver.openInputStream(medium.uri)?.use { input ->
                        contentResolver.openOutputStream(destUri)?.use { output ->
                            input.copyTo(output)
                            success = true
                        }
                    }
                } finally {
                    if (success) {
                        val finish = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        contentResolver.update(destUri, finish, null, null)
                        copiedCount++
                    } else {
                        contentResolver.delete(destUri, null, null)
                    }
                }
            } catch (_: Exception) {
            }
        }
        copiedCount
    }

    fun createDeleteRequest(uris: List<Uri>): IntentSenderRequest {
        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
        return IntentSenderRequest.Builder(pendingIntent.intentSender).build()
    }

    fun createTrashRequest(uris: List<Uri>, isTrash: Boolean): IntentSenderRequest {
        val pendingIntent = MediaStore.createTrashRequest(contentResolver, uris, isTrash)
        return IntentSenderRequest.Builder(pendingIntent.intentSender).build()
    }

    fun createFavoriteRequest(uris: List<Uri>, isFavorite: Boolean): IntentSenderRequest {
        val pendingIntent = MediaStore.createFavoriteRequest(contentResolver, uris, isFavorite)
        return IntentSenderRequest.Builder(pendingIntent.intentSender).build()
    }
}
