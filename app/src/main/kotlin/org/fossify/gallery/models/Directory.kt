package org.fossify.gallery.models

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Directory(
    val bucketId: Long,
    val name: String,
    val coverUri: Uri,
    val mediaCount: Int,
    val latestDateMs: Long = 0L,
    val relativePath: String = ""
)
