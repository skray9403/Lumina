package org.fossify.gallery.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Iso
import androidx.compose.material.icons.filled.ShutterSpeed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.fossify.gallery.engine.metadata.MediaMetadata

@Composable
fun ExifMetadataSection(
    metadata: MediaMetadata,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Camera & Technical Details",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        // Camera make & model
        if (!metadata.cameraModel.isNullOrEmpty() || !metadata.cameraMake.isNullOrEmpty()) {
            val cameraName = listOfNotNull(metadata.cameraMake, metadata.cameraModel).joinToString(" ")
            DetailItem(
                icon = Icons.Default.CameraAlt,
                label = "Camera",
                value = cameraName
            )
        }

        // Lens
        metadata.lensModel?.let { lens ->
            DetailItem(
                icon = Icons.Default.CameraAlt,
                label = "Lens",
                value = lens
            )
        }

        // Exposure row (Aperture, Shutter speed, Focal length, ISO)
        val exposureParts = listOfNotNull(
            metadata.aperture,
            metadata.exposureTime,
            metadata.focalLength,
            metadata.iso
        )
        if (exposureParts.isNotEmpty()) {
            DetailItem(
                icon = Icons.Default.ShutterSpeed,
                label = "Exposure",
                value = exposureParts.joinToString("  •  ")
            )
        }

        // Flash
        metadata.flash?.let { flash ->
            DetailItem(
                icon = Icons.Default.FlashOn,
                label = "Flash",
                value = flash
            )
        }

        // Video codec & framerate
        if (!metadata.videoCodec.isNullOrEmpty() || !metadata.frameRate.isNullOrEmpty()) {
            val videoInfo = listOfNotNull(metadata.videoCodec, metadata.frameRate, metadata.bitrate).joinToString("  •  ")
            DetailItem(
                icon = Icons.Default.Videocam,
                label = "Video Format",
                value = videoInfo
            )
        }
    }
}

@Composable
fun DetailItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp)
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
