package org.fossify.gallery.models

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Medium(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val orientation: Int = 0,
    val durationMs: Long = 0L,
    val dateTakenMs: Long = 0L,
    val dateModifiedMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val bucketId: Long = 0L,
    val bucketName: String = "",
    val relativePath: String = "",
    val isFavorite: Boolean = false,
    val isVideo: Boolean = false,
    val isAnimated: Boolean = false
)
