package org.fossify.gallery.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import org.fossify.gallery.engine.image.DecodedImageResult
import org.fossify.gallery.engine.image.NativeAnimatedCanvas
import org.fossify.gallery.engine.image.NativeImageDecoder
import org.fossify.gallery.engine.image.NativeTiledImageViewer
import org.fossify.gallery.engine.image.rememberThumbnail
import org.fossify.gallery.models.Medium

@Composable
fun PhotoPage(
    medium: Medium,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    onEdgeSwipeHandoff: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val previewThumbnail = rememberThumbnail(medium, sizePx = 360)
    var decodedResult by remember(medium.uri) { mutableStateOf<DecodedImageResult?>(null) }
    var isLoading by remember(medium.uri) { mutableStateOf(true) }

    LaunchedEffect(medium.uri) {
        isLoading = true
        decodedResult = NativeImageDecoder.decodeImage(context.contentResolver, medium.uri)
        isLoading = false
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val result = decodedResult) {
            is DecodedImageResult.Static -> {
                NativeTiledImageViewer(
                    baseBitmap = result.bitmap,
                    imageWidth = result.width,
                    imageHeight = result.height,
                    imageUri = medium.uri,
                    onSingleTap = onSingleTap,
                    onEdgeSwipeHandoff = onEdgeSwipeHandoff
                )
            }
            is DecodedImageResult.Animated -> {
                NativeAnimatedCanvas(
                    drawable = result.drawable,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(onSingleTap) {
                            detectTapGestures(onTap = { onSingleTap() })
                        }
                )
            }
            null -> {
                if (previewThumbnail != null) {
                    Image(
                        bitmap = previewThumbnail,
                        contentDescription = medium.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(onSingleTap) {
                                detectTapGestures(onTap = { onSingleTap() })
                            }
                    )
                } else if (isLoading) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}
