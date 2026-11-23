package org.fossify.gallery.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.fossify.gallery.engine.metadata.MediaMetadata

@Composable
fun StorageInfoSection(
    metadata: MediaMetadata,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "File Info",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        // Date & Time
        DetailItem(
            icon = Icons.Default.Schedule,
            label = "Date Taken",
            value = metadata.formattedDate
        )

        // Dimensions & File Size
        DetailItem(
            icon = Icons.Default.Image,
            label = "Resolution & Size",
            value = "${metadata.resolution}  •  ${metadata.formattedSize}"
        )

        // Storage Path (Clickable to copy)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("File Path", metadata.storagePath))
                    Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                }
        ) {
            DetailItem(
                icon = Icons.Default.Folder,
                label = "Path (tap to copy)",
                value = metadata.storagePath
            )
        }
    }
}
