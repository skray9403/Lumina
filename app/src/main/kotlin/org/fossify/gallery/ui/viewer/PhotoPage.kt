package org.fossify.gallery.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import org.fossify.gallery.engine.image.DecodedImageResult
import org.fossify.gallery.engine.image.NativeAnimatedCanvas
import org.fossify.gallery.engine.image.NativeImageDecoder
import org.fossify.gallery.engine.image.NativeTiledImageViewer
import org.fossify.gallery.engine.image.rememberThumbnail
import org.fossify.gallery.engine.vault.VaultCrypto
import org.fossify.gallery.engine.vault.VaultManager
import org.fossify.gallery.models.Medium
import kotlin.math.max

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhotoPage(
    medium: Medium,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    onEdgeSwipeHandoff: (Boolean) -> Unit = {},
    onVerticalDismissDrag: (Float) -> Unit = {},
    onVerticalDismissEnd: (Float) -> Unit = {},
    onSwipeUpDetails: () -> Unit = {},
    onBitmapReady: (Long, ImageBitmap) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val containerSize = LocalWindowInfo.current.containerSize
    val maxTargetPx = remember(containerSize) {
        max(containerSize.width, containerSize.height).coerceIn(1080, 2560)
    }

    val previewThumbnail = rememberThumbnail(medium)
    var decodedResult by remember(medium.uri) { mutableStateOf<DecodedImageResult?>(null) }
    var isLoading by remember(medium.uri) { mutableStateOf(true) }

    LaunchedEffect(medium.uri, maxTargetPx) {
        isLoading = true
        val decoded = if (medium.uri.scheme == VaultCrypto.VAULT_SCHEME) {
            val bytes = VaultManager.decryptMediaBytes(medium.id)
            if (bytes != null) {
                try {
                    NativeImageDecoder.decodeFromBytes(bytes, maxDimensionPx = maxTargetPx)
                } finally {
                    bytes.fill(0)
                }
            } else {
                null
            }
        } else {
            NativeImageDecoder.decodeImage(
                contentResolver = context.contentResolver,
                uri = medium.uri,
                maxDimensionPx = maxTargetPx
            )
        }
        decodedResult = decoded
        if (decoded is DecodedImageResult.Static) {
            onBitmapReady(medium.id, decoded.bitmap)
        }
        isLoading = false
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val result = decodedResult) {
            is DecodedImageResult.Static -> {
                NativeTiledImageViewer(
                    baseBitmap = result.bitmap,
                    imageWidth = result.displayWidth,
                    imageHeight = result.displayHeight,
                    orientationDegrees = medium.orientation,
                    imageUri = medium.uri,
                    onSingleTap = onSingleTap,
                    onEdgeSwipeHandoff = onEdgeSwipeHandoff,
                    onVerticalDismissDrag = onVerticalDismissDrag,
                    onVerticalDismissEnd = onVerticalDismissEnd,
                    onSwipeUpDetails = onSwipeUpDetails
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
                    LoadingIndicator(color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}
