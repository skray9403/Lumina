package org.fossify.gallery.ui.details

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.engine.metadata.MediaMetadata
import java.util.Locale

@Composable
fun LocationSection(
    metadata: MediaMetadata,
    modifier: Modifier = Modifier
) {
    val lat = metadata.latitude ?: return
    val lng = metadata.longitude ?: return
    val context = LocalContext.current

    var addressText by remember(lat, lng) { mutableStateOf<String?>("${String.format(Locale.ROOT, "%.4f", lat)}, ${String.format(Locale.ROOT, "%.4f", lng)}") }

    LaunchedEffect(lat, lng) {
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        addresses.firstOrNull()?.let { addr ->
                            val line = addr.getAddressLine(0) ?: "${addr.locality}, ${addr.countryName}"
                            addressText = line
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    addresses?.firstOrNull()?.let { addr ->
                        val line = addr.getAddressLine(0) ?: "${addr.locality}, ${addr.countryName}"
                        addressText = line
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Location",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                    context.startActivity(Intent.createChooser(mapIntent, "Open location"))
                }
        ) {
            DetailItem(
                icon = Icons.Default.LocationOn,
                label = "Coordinates (tap to open in maps)",
                value = addressText ?: "${String.format(Locale.ROOT, "%.4f", lat)}, ${String.format(Locale.ROOT, "%.4f", lng)}"
            )
        }
    }
}
